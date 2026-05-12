#!/bin/bash
# =====================================================================
# run_all_seed.sh — payroll 시드 end-to-end 자동화 wrapper
# ---------------------------------------------------------------------
# 사전 조건 (Pod 안에서):
#   - mysql 8 클라이언트, curl 설치 (mysql:8.0 이미지 + microdnf install curl)
#   - /tmp 에 아래 파일 복사:
#       seed_payroll_via_api.sh
#       06b_set_paid.sql
#       03_hr_salary_contracts.sql   (재시드 fallback 용)
#   - /tmp/pw.txt 에 RDS 비밀번호 한 줄 (또는 DB_PASS env 로 주입)
#   - 환경변수:
#       DB_HOST DB_USER DB_NAME HR_HOST COMPANY_NAME
#       ADMIN_EMP_NUM START_YM END_YM
#       (선택) DB_PORT, MYSQL_SSL_OPT
#
# 동작:
#   1) 회사 ID 조회
#   2) 기존 (껍데기) payroll_runs/details/emp_status/pay_stubs 정리
#   3) salary_contract_detail.pay_item_id 깨짐 여부 검사 → 필요시 utf8mb4 재시드
#   4) seed_payroll_via_api.sh 실행 (15개월 createPayroll API 호출)
#   5) 06b_set_paid.sql 실행 (PAID 전환 + pay_stubs 생성)
#   6) 최종 카운트 검증 출력
# =====================================================================

set -euo pipefail

: "${DB_HOST:?DB_HOST required}"
: "${DB_USER:?DB_USER required}"
: "${DB_NAME:?DB_NAME required}"
: "${HR_HOST:?HR_HOST required}"
: "${COMPANY_NAME:?COMPANY_NAME required}"
: "${ADMIN_EMP_NUM:?ADMIN_EMP_NUM required}"
: "${START_YM:?START_YM required}"
: "${END_YM:?END_YM required}"

DB_PORT="${DB_PORT:-3306}"
MYSQL_SSL_OPT="${MYSQL_SSL_OPT:---ssl-mode=REQUIRED}"

# 비밀번호 우선순위: 이미 DB_PASS export 됐으면 그대로, 아니면 /tmp/pw.txt
if [ -z "${DB_PASS:-}" ]; then
  if [ -r /tmp/pw.txt ]; then
    DB_PASS="$(tr -d '\r\n' < /tmp/pw.txt)"
  else
    echo "❌ DB_PASS 가 비어있고 /tmp/pw.txt 도 없음" >&2
    exit 1
  fi
fi
export DB_PASS DB_HOST DB_PORT DB_USER DB_NAME HR_HOST COMPANY_NAME ADMIN_EMP_NUM START_YM END_YM MYSQL_SSL_OPT

# mysql 공통 옵션 (utf8mb4 강제 → 한글 파라미터 매칭 보장)
M="$MYSQL_SSL_OPT --default-character-set=utf8mb4 -h$DB_HOST -P$DB_PORT -u$DB_USER -p$DB_PASS $DB_NAME"

echo "═══════════════════════════════════════════════════════════"
echo "  RDS     = $DB_HOST"
echo "  hr-svc  = $HR_HOST"
echo "  기간    = $START_YM ~ $END_YM"
echo "  company = $COMPANY_NAME / admin = $ADMIN_EMP_NUM"
echo "═══════════════════════════════════════════════════════════"

echo "── [1/5] 회사 ID 조회"
CID=$(mysql $M -N -B -e "SELECT BIN_TO_UUID(company_id) FROM company WHERE company_name='$COMPANY_NAME';")
if [ -z "$CID" ]; then
  echo "❌ company_id 조회 실패 ($COMPANY_NAME)" >&2
  exit 1
fi
echo "    company_id=$CID"

echo ""
echo "── [2/5] 기존 payroll 관련 데이터 일괄 정리"
mysql $M <<SQL
SET @cid := UUID_TO_BIN('$CID');
SET FOREIGN_KEY_CHECKS=0;
DELETE FROM pay_stubs                WHERE company_id=@cid;
DELETE FROM pay_transfers            WHERE company_id=@cid;
DELETE FROM pay_item_histories       WHERE company_id=@cid;
DELETE FROM payroll_approval_snapshot WHERE company_id=@cid;
DELETE FROM payroll_run_histories    WHERE company_id=@cid;
DELETE FROM payroll_details          WHERE company_id=@cid;
DELETE FROM payroll_emp_status       WHERE company_id=@cid;
DELETE FROM payroll_runs             WHERE company_id=@cid AND pay_year_month BETWEEN '$START_YM' AND '$END_YM';
SET FOREIGN_KEY_CHECKS=1;
SQL
echo "    ✓ 정리 완료"

echo ""
echo "── [3/5] salary_contract_detail.pay_item_id 검증"
BROKEN=$(mysql $M -N -B -e "SELECT COUNT(*) FROM salary_contract_detail WHERE pay_item_id IS NULL OR pay_item_id=0;")
if [ "$BROKEN" -gt 0 ]; then
  echo "    ✗ $BROKEN 건 pay_item_id 깨짐 → utf8mb4 로 재시드"
  if [ ! -r /tmp/03_hr_salary_contracts.sql ]; then
    echo "❌ /tmp/03_hr_salary_contracts.sql 없음 — 먼저 kubectl cp 필요" >&2
    exit 1
  fi
  sed -i 's/\r$//' /tmp/03_hr_salary_contracts.sql
  mysql $M -e "DELETE FROM salary_contract_detail; DELETE FROM salary_contract;"
  mysql $M < /tmp/03_hr_salary_contracts.sql > /dev/null
  # 재검증
  STILL=$(mysql $M -N -B -e "SELECT COUNT(*) FROM salary_contract_detail WHERE pay_item_id IS NULL OR pay_item_id=0;")
  if [ "$STILL" -gt 0 ]; then
    echo "❌ 재시드 후에도 $STILL 건 깨짐 — pay_items 한글 미존재 가능" >&2
    exit 1
  fi
  echo "    ✓ 재시드 완료"
else
  echo "    ✓ pay_item_id 정상"
fi

echo ""
echo "── [4/5] seed_payroll_via_api.sh (createPayroll × 15개월)"
if [ ! -r /tmp/seed_payroll_via_api.sh ]; then
  echo "❌ /tmp/seed_payroll_via_api.sh 없음" >&2
  exit 1
fi
sed -i 's/\r$//' /tmp/seed_payroll_via_api.sh
bash /tmp/seed_payroll_via_api.sh

echo ""
echo "── [5/5] 06b_set_paid.sql (PAID 전환 + pay_stubs 생성)"
if [ ! -r /tmp/06b_set_paid.sql ]; then
  echo "❌ /tmp/06b_set_paid.sql 없음" >&2
  exit 1
fi
sed -i 's/\r$//' /tmp/06b_set_paid.sql
mysql $M < /tmp/06b_set_paid.sql

echo ""
echo "═══════════════════════════════════════════════════════════"
echo "  최종 검증"
echo "═══════════════════════════════════════════════════════════"
mysql $M <<SQL
SET @cid := UUID_TO_BIN('$CID');
SELECT 'runs(PAID)' AS metric, COUNT(*) AS cnt
  FROM payroll_runs
 WHERE company_id=@cid AND payroll_status='PAID'
   AND pay_year_month BETWEEN '$START_YM' AND '$END_YM'
UNION ALL SELECT 'details',    COUNT(*) FROM payroll_details    WHERE company_id=@cid
UNION ALL SELECT 'emp_status', COUNT(*) FROM payroll_emp_status WHERE company_id=@cid
UNION ALL SELECT 'pay_stubs',  COUNT(*) FROM pay_stubs          WHERE company_id=@cid;

SELECT pay_year_month, total_employees, total_pay, total_deduction, total_net_pay
  FROM payroll_runs
 WHERE company_id=@cid AND pay_year_month BETWEEN '$START_YM' AND '$END_YM'
 ORDER BY pay_year_month;
SQL

echo ""
echo "✓ 전체 시드 완료"
