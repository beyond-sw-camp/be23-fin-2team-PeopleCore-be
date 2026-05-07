#!/bin/bash
# =====================================================================
# C 방안 — hr-service API 호출로 16개월 급여 정확히 산정
# ---------------------------------------------------------------------
# 흐름:
#   1) /pay/admin/payroll/create  × 16개월
#      → PayrollService.createPayroll() 가 사원별 detail (변동수당+공제)
#        모두 백엔드 정확한 식으로 INSERT. status = CALCULATING.
#   2) /pay/admin/payroll/{runId}/employees/{empId}/apply-overtime
#      × overtime_request 가 있는 모든 (run, emp) 조합
#      → 변동수당(연장/야간/휴일) 정확히 적용
#   3) (별도) 06b_set_paid.sql 로 status PAID 일괄 변경 + pay_stubs 생성
#
# 선행 조건:
#   - 02~05 SQL 시드 완료 (사원, 계약, 출퇴근, 야근까지)
#   - hr-service 가 ${HR_HOST} (기본 localhost:8080) 에서 기동 중
#
# 환경변수:
#   DB_USER       (기본 root)
#   DB_PASS       (필수)
#   DB_NAME       (기본 peoplecore)
#   HR_HOST       (기본 http://localhost:8080)
#   COMPANY_NAME  (기본 peoplecore)
#   ADMIN_EMP_NUM (기본 EMP-2025-001 — HR_SUPER_ADMIN)
#   START_YM      (기본 2025-01)
#   END_YM        (기본 2026-04)
#
# 사용 예:
#   DB_PASS=mypass bash scripts/dummy/seed_payroll_via_api.sh
# =====================================================================

set -euo pipefail

DB_USER=${DB_USER:-root}
DB_PASS=${DB_PASS:?need DB_PASS env var}
DB_NAME=${DB_NAME:-peoplecore}
HR_HOST=${HR_HOST:-http://localhost:8080}
COMPANY_NAME=${COMPANY_NAME:-peoplecore}
ADMIN_EMP_NUM=${ADMIN_EMP_NUM:-EMP-2025-001}
START_YM=${START_YM:-2025-01}
END_YM=${END_YM:-2026-04}
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

mysql_q() {
  mysql -u"$DB_USER" -p"$DB_PASS" -N -B -e "$1" 2>/dev/null
}

# ---- 1. 회사 ID + 처리자 emp_id 조회 ----
COMPANY_ID=$(mysql_q "SELECT BIN_TO_UUID(company_id) FROM $DB_NAME.company WHERE company_name='$COMPANY_NAME'")
ADMIN_EMP_ID=$(mysql_q "SELECT emp_id FROM $DB_NAME.employee WHERE emp_num='$ADMIN_EMP_NUM'")

if [[ -z "$COMPANY_ID" || -z "$ADMIN_EMP_ID" ]]; then
  echo "❌ ERROR: company_id 또는 admin_emp_id 조회 실패" >&2
  echo "   company='$COMPANY_NAME', admin='$ADMIN_EMP_NUM'" >&2
  exit 1
fi

echo "═══════════════════════════════════════════════════════════"
echo "  hr-service     = $HR_HOST"
echo "  company_id     = $COMPANY_ID"
echo "  admin_emp_id   = $ADMIN_EMP_ID  ($ADMIN_EMP_NUM)"
echo "  기간           = $START_YM ~ $END_YM"
echo "═══════════════════════════════════════════════════════════"

# 게이트웨이 우회: hr-service 가 받는 3개 헤더 직접 주입
HDR=(-H "X-User-Company: $COMPANY_ID"
     -H "X-User-Id: $ADMIN_EMP_ID"
     -H "X-User-Role: HR_SUPER_ADMIN"
     -H "Content-Type: application/json")

# ---- 2. 월 시퀀스 생성 ----
gen_months() {
  local cur="$1-01" end="$2-01"
  while [[ "$cur" < "$end" || "$cur" == "$end" ]]; do
    echo "${cur:0:7}"
    # date 명령어는 macOS/Linux 호환을 위해 mysql 사용
    cur=$(mysql_q "SELECT DATE_ADD('$cur', INTERVAL 1 MONTH)")
  done
}
MONTHS=($(gen_months "$START_YM" "$END_YM"))
TOTAL_MONTHS=${#MONTHS[@]}
echo ""
echo "[Step 1/2] payroll_runs 생성 ($TOTAL_MONTHS 개월)"

OK_CNT=0; FAIL_CNT=0
for ym in "${MONTHS[@]}"; do
  printf "  %s ... " "$ym"
  HTTP=$(curl -s -o /tmp/.payroll_resp -w "%{http_code}" -X POST \
         "$HR_HOST/pay/admin/payroll/create?payYearMonth=$ym" "${HDR[@]}")
  if [[ "$HTTP" =~ ^(200|201)$ ]]; then
    echo "✓"
    OK_CNT=$((OK_CNT+1))
  else
    echo "✗ HTTP=$HTTP  $(cat /tmp/.payroll_resp 2>/dev/null | head -c 200)"
    FAIL_CNT=$((FAIL_CNT+1))
  fi
done
echo "  → 성공 $OK_CNT / 실패 $FAIL_CNT"

# ---- 3. 변동수당 적용 ----
echo ""
echo "[Step 2/2] 변동수당(연장/야간/휴일) applyOvertime 호출"

TMPFILE=$(mktemp)
mysql_q "
SELECT DISTINCT pr.payroll_run_id, ot.emp_id
  FROM $DB_NAME.overtime_request ot
  JOIN $DB_NAME.payroll_runs pr
    ON pr.pay_year_month = DATE_FORMAT(ot.ot_date, '%Y-%m')
   AND pr.company_id = UUID_TO_BIN('$COMPANY_ID')
 WHERE ot.company_id = UUID_TO_BIN('$COMPANY_ID')
   AND ot.ot_status = 'APPROVED'
   AND pr.pay_year_month BETWEEN '$START_YM' AND '$END_YM'" > "$TMPFILE"

TOTAL=$(wc -l < "$TMPFILE" | tr -d ' ')
if [[ "$TOTAL" -eq 0 ]]; then
  echo "  대상 (run, emp) 조합 0건 — overtime_request 시드 확인"
else
  echo "  대상: $TOTAL (run, emp) 조합"
  i=0; OT_OK=0; OT_FAIL=0
  while read -r RUN_ID EMP_ID; do
    i=$((i+1))
    HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
      "$HR_HOST/pay/admin/payroll/$RUN_ID/employees/$EMP_ID/apply-overtime" "${HDR[@]}")
    if [[ "$HTTP" =~ ^(200|201|204)$ ]]; then OT_OK=$((OT_OK+1)); else OT_FAIL=$((OT_FAIL+1)); fi
    # 진행률 표시 (50건마다)
    if (( i % 50 == 0 )); then
      printf "\r  진행: %d/%d  (✓%d ✗%d)" "$i" "$TOTAL" "$OT_OK" "$OT_FAIL"
    fi
  done < "$TMPFILE"
  printf "\r  진행: %d/%d  (✓%d ✗%d)\n" "$i" "$TOTAL" "$OT_OK" "$OT_FAIL"
fi
rm -f "$TMPFILE" /tmp/.payroll_resp

# ---- 4. 마무리 안내 ----
echo ""
echo "═══════════════════════════════════════════════════════════"
echo "  ✓ API 시드 완료. 현재 status = CALCULATING"
echo ""
echo "  다음 단계: payroll_runs/emp_status 를 PAID 로 변경 + pay_stubs 생성"
echo "  → mysql -u$DB_USER -p $DB_NAME < $SCRIPT_DIR/06b_set_paid.sql"
echo "═══════════════════════════════════════════════════════════"
