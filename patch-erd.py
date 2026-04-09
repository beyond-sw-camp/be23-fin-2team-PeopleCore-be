import json, copy

with open(r"c:\Users\Playdata\Desktop\peoplecore-purple-snapshot.json", "r", encoding="utf-8") as f:
    data = json.load(f)

entities = data["entityData"]
eid = {e["_id"]: e for e in entities}
DID = "utcksJDhnCEsMDwS8"

def field(fid, name, pName, typ, **kw):
    return {
        "_id": fid, "name": name, "pName": pName, "domain": "",
        "type": typ, "defaultValue": kw.get("dv",""), "isAllowNull": kw.get("null", True),
        "comment": kw.get("cm",""), "relEntity": kw.get("re"), "relFieldId": kw.get("rf"),
        "relType": kw.get("rt"), "relGroupId": kw.get("rg")
    }

def entity(eid, x, y, name, pName, fields_list, pk_list):
    return {
        "_id": eid, "_diagramId": DID,
        "position": {"x": x, "y": y},
        "name": name, "pName": pName,
        "fields": fields_list,
        "keys": {"pks": pk_list, "fks": []},
        "color": "rgba(123, 0, 178, 0.5)"
    }

# === 1. 결재 문서 (ao7dWNubyrHtNye2u) - 5 fields ===
doc = eid["ao7dWNubyrHtNye2u"]
doc["fields"].extend([
    field("N1empDeptId001", "기안자 부서 ID", "emp_dept_id", "BIGINT"),
    field("N1docOpinion01", "기안 의견", "doc_opinion", "VARCHAR(255)"),
    field("N1version00001", "낙관적 락 버전", "version", "BIGINT", cm="@Version"),
    field("N1persFolder01", "개인 문서함 ID", "personal_folder_id", "BIGINT"),
    field("N1deptFolder01", "부서 문서함 ID", "dept_folder_id", "BIGINT"),
])

# === 2. 결재라인 (joChhDpSFKRKzGNL7) - 1 field ===
line = eid["joChhDpSFKRKzGNL7"]
line["fields"].insert(5, field("N2empDeptId001", "사원 부서 ID", "emp_dept_id", "BIGINT"))

# === 3. 결재 위임 (duMfTmh5tpSo7GuEk) - 5 fields ===
dele = eid["duMfTmh5tpSo7GuEk"]
# 원래 결재자 상세정보 - app_emp_id 다음에 삽입 (index 2 이후)
insert_at = 2
for i, f in enumerate(dele["fields"]):
    if f["pName"] == "app_emp_id":
        insert_at = i + 1
        break
new_dele_fields = [
    field("N3empDeptNm001", "원래 결재자 부서명", "emp_dept_name", "VARCHAR(255)", null=False),
    field("N3empGrade0001", "원래 결재자 직급", "emp_grade", "VARCHAR(255)", null=False),
    field("N3empTitle0001", "원래 결재자 직책", "emp_title", "VARCHAR(255)", null=False),
    field("N3empName00001", "원래 결재자 이름", "emp_name", "VARCHAR(255)", null=False),
]
for j, nf in enumerate(new_dele_fields):
    dele["fields"].insert(insert_at + j, nf)
dele["fields"].append(field("N3reason000001", "위임 사유", "reason", "VARCHAR(255)"))

# === 4. 결재 양식 폴더 (LzpeWSPh4qQ9jBPPp) - 2 fields ===
folder = eid["LzpeWSPh4qQ9jBPPp"]
folder["fields"].insert(2, field("N4parentId0001", "부모 폴더", "parent_id", "BIGINT",
    cm="self FK - 계층구조", re="LzpeWSPh4qQ9jBPPp", rf="X8yiBbggjCw6ToM8t", rt="ZERO_OR_ONE_OR_MANY"))
folder["fields"].insert(3, field("N4folderPath01", "MinIO 경로", "folder_path", "VARCHAR(255)", null=False))

# === 5. 결재 번호 규칙 (hbAjHY6e8Sp8YAbs8) - 2 fields ===
rule = eid["hbAjHY6e8Sp8YAbs8"]
# slot2_custom 다음에 삽입
insert_at = 0
for i, f in enumerate(rule["fields"]):
    if f["pName"] == "number_rule_slot2_custom":
        insert_at = i + 1
        break
rule["fields"].insert(insert_at, field("N5slot3Type001", "3번째 자리", "number_rule_slot3_type", "VARCHAR(255)", null=False))
rule["fields"].insert(insert_at+1, field("N5slot3Cust001", "3번째 직접 입력값", "number_rule_slot3_custom", "VARCHAR(255)"))

# === 6. 결재 라인 템플릿 (zxSCawPWPN2ZXzaC3) - 1 field ===
tmpl = eid["zxSCawPWPN2ZXzaC3"]
tmpl["fields"].append(field("N6isDefault001", "기본 결재선 여부", "is_default", "BOOLEAN", null=False, dv="FALSE"))

# === 7. 부서 문서함 설정 (btjGNDznTYz89CMZs) - 2 fields ===
dept = eid["btjGNDznTYz89CMZs"]
dept["fields"].insert(2, field("N7folderName01", "문서함 이름", "folder_name", "VARCHAR(100)", null=False))
dept["fields"].insert(3, field("N7sortOrder01", "정렬 순서", "sort_order", "INT", null=False, dv="0"))

# =============================================
# 신규 테이블 8개
# =============================================

# 8. 채번 카운터
entities.append(entity("NEW_SeqCounter01", 700, 1500, "approval_seq_counter", "채번 카운터", [
    field("N8companyId001", "회사 ID", "company_id", "UUID", null=False),
    field("N8seqRuleId01", "결재 번호 규칙 ID", "seq_rule_id", "BIGINT", null=False,
          re="hbAjHY6e8Sp8YAbs8", rf="SqXy2xZ3zPNK6Y4yA", rt="ZERO_OR_ONE_OR_MANY"),
    field("N8resetKey001", "리셋 주기별 키", "seq_reset_key", "VARCHAR(20)", null=False),
    field("N8seqCurrent1", "현재 일련번호", "seq_current", "INT", null=False, dv="0"),
    field("N8seqVersion1", "낙관적 락 버전", "seq_version", "INT", null=False, dv="1", cm="@Version"),
    field("N8createdAt01", "생성일시", "created_at", "DATETIME", null=False),
    field("N8updatedAt01", "수정일시", "updated_at", "DATETIME"),
], [field("N8seqCntId001", "채번 카운터 ID", "seq_counter_id", "BIGINT", null=False, dv="AUTO_INCREMENT")]))

# 9. 결재 상태 이력
entities.append(entity("NEW_StatusHist01", 500, 1800, "approval_status_history", "결재 상태 이력", [
    field("N9docId000001", "결재 문서 ID", "doc_id", "BIGINT", null=False,
          re="ao7dWNubyrHtNye2u", rf="MdBSRBfPcjJrncFKe", rt="ZERO_OR_ONE_OR_MANY"),
    field("N9companyId001", "회사 ID", "company_id", "UUID", null=False),
    field("N9prevStatus1", "이전 상태", "previous_status", "VARCHAR(255)"),
    field("N9chgStatus01", "변경된 상태", "changed_status", "VARCHAR(255)", null=False),
    field("N9changedBy01", "변경자 사원 ID", "changed_by", "BIGINT", null=False),
    field("N9chgByName01", "변경자 이름", "change_by_name", "VARCHAR(255)", null=False),
    field("N9chgByDept01", "변경자 부서명", "change_by_dept_name", "VARCHAR(255)", null=False),
    field("N9chgByGrade1", "변경자 직급", "change_by_grade", "VARCHAR(255)", null=False),
    field("N9chgReason01", "변경 사유", "change_reason", "VARCHAR(255)"),
    field("N9changedAt01", "변경 일시", "changed_at", "DATETIME", null=False),
    field("N9createdAt01", "생성일시", "created_at", "DATETIME", null=False),
    field("N9updatedAt01", "수정일시", "updated_at", "DATETIME"),
], [field("N9historyId01", "이력 ID", "history_id", "BIGINT", null=False, dv="AUTO_INCREMENT")]))

# 10. 결재 첨부파일
entities.append(entity("NEW_Attachment01", 2900, 2200, "approval_attachment", "결재 첨부파일", [
    field("NAadocId00001", "결재 문서 ID", "doc_id", "BIGINT", null=False,
          re="ao7dWNubyrHtNye2u", rf="MdBSRBfPcjJrncFKe", rt="ZERO_OR_ONE_OR_MANY"),
    field("NAaCompanyId1", "회사 ID", "company_id", "UUID", null=False),
    field("NAaFileName01", "원본 파일명", "file_name", "VARCHAR(255)", null=False),
    field("NAaFileSize01", "파일 크기", "file_size", "BIGINT", null=False, cm="bytes"),
    field("NAaObjName001", "MinIO 오브젝트명", "object_name", "VARCHAR(255)", null=False),
    field("NAaContType01", "파일 MIME 타입", "content_type", "VARCHAR(255)", null=False),
    field("NAaCreatedAt1", "생성일시", "created_at", "DATETIME", null=False),
    field("NAaUpdatedAt1", "수정일시", "updated_at", "DATETIME"),
], [field("NAaAttachId01", "첨부파일 ID", "attach_id", "BIGINT", null=False, dv="AUTO_INCREMENT")]))

# 11. 즐겨찾는 양식
entities.append(entity("NEW_FreqForm0001", 2600, 3300, "frequent_form", "즐겨찾는 양식", [
    field("NBcompanyId001", "회사 ID", "company_id", "UUID", null=False),
    field("NBempId000001", "사원 ID", "emp_id", "BIGINT", null=False),
    field("NBformId00001", "결재 양식 ID", "form_id", "BIGINT", null=False,
          re="24nPQsHuwatyosuP2", rf="PJKzAddZm4oFxBSty", rt="ZERO_OR_ONE_OR_MANY"),
    field("NBcreatedAt01", "생성일시", "created_at", "DATETIME", null=False),
    field("NBupdatedAt01", "수정일시", "updated_at", "DATETIME"),
], [field("NBfreqFormId1", "즐겨찾는 양식 ID", "frequent_form_id", "BIGINT", null=False, dv="AUTO_INCREMENT")]))

# 12. 개인 결재 문서함
entities.append(entity("NEW_PersFolder01", 500, 2600, "personal_approval_folder", "개인 결재 문서함", [
    field("NCcompanyId001", "회사 ID", "company_id", "UUID", null=False),
    field("NCempId000001", "사원 ID", "emp_id", "BIGINT", null=False),
    field("NCfolderName01", "폴더명", "folder_name", "VARCHAR(100)", null=False),
    field("NCsortOrder01", "정렬 순서", "sort_order", "INT", null=False, dv="0"),
    field("NCisActive0001", "활성 여부", "is_active", "BOOLEAN", null=False, dv="TRUE"),
    field("NCcreatedAt01", "생성일시", "created_at", "DATETIME", null=False),
    field("NCupdatedAt01", "수정일시", "updated_at", "DATETIME"),
], [field("NCpersFoldId1", "개인 문서함 ID", "personal_folder_id", "BIGINT", null=False, dv="AUTO_INCREMENT")]))

# 13. 부서 문서함 관리자
entities.append(entity("NEW_DeptMgr00001", 500, 2300, "dept_folder_manager", "부서 문서함 관리자", [
    field("NDdeptFoldId1", "부서 문서함 ID", "dept_app_folder_id", "BIGINT", null=False,
          re="btjGNDznTYz89CMZs", rf="XKrWQs7TkCDGzBuoj", rt="ZERO_OR_ONE_OR_MANY"),
    field("NDcompanyId001", "회사 ID", "company_id", "UUID", null=False),
    field("NDempId000001", "관리자 사원 ID", "emp_id", "BIGINT", null=False),
    field("NDempName0001", "관리자 이름", "emp_name", "VARCHAR(255)", null=False),
    field("NDdeptName001", "부서명", "dept_name", "VARCHAR(255)", null=False),
    field("NDcreatedAt01", "생성일시", "created_at", "DATETIME", null=False),
    field("NDupdatedAt01", "수정일시", "updated_at", "DATETIME"),
], [field("NDmanagerId01", "관리자 ID", "manager_id", "BIGINT", null=False, dv="AUTO_INCREMENT")]))

# 14. 개인 폴더 문서
entities.append(entity("NEW_PersFolDoc01", 200, 2900, "personal_folder_document", "개인 폴더 문서", [
    field("NEcompanyId001", "회사 ID", "company_id", "UUID", null=False),
    field("NEempId000001", "사원 ID", "emp_id", "BIGINT", null=False),
    field("NEdocId000001", "결재 문서 ID", "doc_id", "BIGINT", null=False,
          re="ao7dWNubyrHtNye2u", rf="MdBSRBfPcjJrncFKe", rt="ZERO_OR_ONE_OR_MANY"),
    field("NEpersFoldId1", "개인 폴더 ID", "personal_folder_id", "BIGINT", null=False,
          re="NEW_PersFolder01", rf="NCpersFoldId1", rt="ZERO_OR_ONE_OR_MANY"),
    field("NEcreatedAt01", "생성일시", "created_at", "DATETIME", null=False),
    field("NEupdatedAt01", "수정일시", "updated_at", "DATETIME"),
], [field("NEid000000001", "ID", "id", "BIGINT", null=False, dv="AUTO_INCREMENT")]))

# 15. 자동 분류 규칙
entities.append(entity("NEW_AutoRule0001", 200, 3200, "auto_classify_rule", "자동 분류 규칙", [
    field("NFcompanyId001", "회사 ID", "company_id", "UUID", null=False),
    field("NFempId000001", "사원 ID", "emp_id", "BIGINT", null=False),
    field("NFsourceBox01", "문서함 구분", "source_box", "VARCHAR(255)", null=False, cm="SENT/INBOX"),
    field("NFruleName001", "규칙 이름", "rule_name", "VARCHAR(255)", null=False),
    field("NFtitleCont01", "제목 포함 키워드", "title_contains", "VARCHAR(255)"),
    field("NFformName001", "양식명 조건", "form_name", "VARCHAR(255)"),
    field("NFdrafDept001", "기안자 부서 조건", "drafter_dept", "VARCHAR(255)"),
    field("NFdrafName001", "기안자 이름 조건", "drafter_name", "VARCHAR(255)"),
    field("NFtargetFold1", "대상 폴더 ID", "target_folder_id", "BIGINT", null=False,
          re="NEW_PersFolder01", rf="NCpersFoldId1", rt="ZERO_OR_ONE_OR_MANY"),
    field("NFisActive001", "활성 여부", "is_active", "BOOLEAN", null=False, dv="TRUE"),
    field("NFsortOrder01", "우선순위", "sort_order", "INT", null=False, dv="0"),
    field("NFcreatedAt01", "생성일시", "created_at", "DATETIME", null=False),
    field("NFupdatedAt01", "수정일시", "updated_at", "DATETIME"),
], [field("NFruleId00001", "규칙 ID", "rule_id", "BIGINT", null=False, dv="AUTO_INCREMENT")]))

out = r"c:\Users\Playdata\Desktop\peoplecore-purple-snapshot-modified.json"
with open(out, "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=2)

print(f"Done! Saved to {out}")
print(f"Total entities: {len(data['entityData'])}")
