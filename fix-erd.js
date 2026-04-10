const fs = require('fs');

function generateId() {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  let result = '';
  for (let i = 0; i < 17; i++) {
    result += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return result;
}

function f(korName, pName, type, isAllowNull, comment, defaultValue, relEntity, relFieldId, relType, relGroupId) {
  return {
    _id: generateId(),
    name: korName,
    pName: pName,
    domain: "",
    type: type,
    defaultValue: defaultValue || "",
    isAllowNull: isAllowNull,
    comment: comment || "",
    relEntity: relEntity || null,
    relFieldId: relFieldId || null,
    relType: relType || null,
    relGroupId: relGroupId || null
  };
}

const inputPath = process.argv[2];
if (!inputPath) { console.error('Usage: node fix-erd.js <input.json>'); process.exit(1); }

const data = JSON.parse(fs.readFileSync(inputPath, 'utf-8'));
const entities = data.entityData;

function findEntity(id) { return entities.find(e => e._id === id); }
function findFieldIdx(ent, pName) { return ent.fields.findIndex(f => f.pName === pName || f.name === pName); }
function hasField(ent, pName) { return ent.fields.some(f => f.pName === pName); }

// 1. employee - add missing fields
const emp = findEntity('xsRopkQR6Bea4Fqfu');
if (emp) {
  const before = findFieldIdx(emp, 'Field2');
  const newFields = [];
  if (!hasField(emp, 'emp_zip_code')) newFields.push(f('우편번호', 'emp_zip_code', 'VARCHAR(255)', true, ''));
  if (!hasField(emp, 'emp_address_detail')) newFields.push(f('상세주소', 'emp_address_detail', 'VARCHAR(255)', true, ''));
  if (!hasField(emp, 'emp_mailbox_size')) newFields.push(f('메일함용량', 'emp_mailbox_size', 'VARCHAR(255)', false, '', '5GB'));
  if (!hasField(emp, 'delete_at')) newFields.push(f('소프트삭제일', 'delete_at', 'DATE', true, ''));
  if (!hasField(emp, 'contract_end_date')) newFields.push(f('계약종료일', 'contract_end_date', 'DATE', true, ''));
  if (before >= 0) emp.fields.splice(before, 0, ...newFields);
  else emp.fields.push(...newFields);
  console.error('Patched employee: +' + newFields.length);
}

// 2. grade - add grade_order
const grade = findEntity('z2nMkDWTg2d5sZL3H');
if (grade && !hasField(grade, 'grade_order')) {
  grade.fields.push(f('직급 정렬순서', 'grade_order', 'INT', false, ''));
  console.error('Patched grade: +1');
}

// 3. emp_schedule - add contract_id
const empSche = findEntity('khHrZfin54TLWGPqa');
if (empSche && !hasField(empSche, 'contract_id')) {
  empSche.fields.push(f('연봉계약 ID', 'contract_id', 'BIGINT', false, 'FK → salary_contract'));
  console.error('Patched emp_schedule: +1');
}

// 4. vacation_create_rule - add create_rule_desc
const vacRule = findEntity('ikpxMppWXHy3437sR');
if (vacRule && !hasField(vacRule, 'create_rule_desc')) {
  vacRule.fields.push(f('규칙 설명', 'create_rule_desc', 'VARCHAR(255)', true, ''));
  console.error('Patched vacation_create_rule: +1');
}

// 5. face_registration - add emp_name
const faceReg = findEntity('nRvFbLfQefe5RBEkJ');
if (faceReg && !hasField(faceReg, 'emp_name')) {
  const afterEmpId = findFieldIdx(faceReg, 'employee_id');
  const nf = f('사원이름', 'emp_name', 'VARCHAR(50)', false, '');
  if (afterEmpId >= 0) faceReg.fields.splice(afterEmpId + 1, 0, nf);
  else faceReg.fields.push(nf);
  console.error('Patched face_registration: +1');
}

// 6. resign - add emp_name, resign_reason, resign_date
const resign = findEntity('N4LhYGKS3xuknBmCr');
if (resign) {
  const newFields = [];
  if (!hasField(resign, 'emp_name')) newFields.push(f('사원이름', 'emp_name', 'VARCHAR(255)', true, ''));
  if (!hasField(resign, 'resign_reason')) newFields.push(f('퇴직사유', 'resign_reason', 'VARCHAR(255)', true, ''));
  if (!hasField(resign, 'resign_date')) newFields.push(f('퇴직 예정일', 'resign_date', 'DATE', true, ''));
  // insert before processed_at
  const beforeIdx = findFieldIdx(resign, 'processed_at');
  if (beforeIdx >= 0) resign.fields.splice(beforeIdx, 0, ...newFields);
  else resign.fields.push(...newFields);
  console.error('Patched resign: +' + newFields.length);
}

const outputPath = inputPath.replace('.json', '-fixed.json');
fs.writeFileSync(outputPath, JSON.stringify(data, null, 2), 'utf-8');
console.error('Output written to: ' + outputPath);
