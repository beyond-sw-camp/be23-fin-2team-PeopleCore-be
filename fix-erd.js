const fs = require('fs');

function generateId() {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  let result = '';
  for (let i = 0; i < 17; i++) {
    result += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return result;
}

function makeField(korName, pName, type, isAllowNull, comment, defaultValue) {
  return {
    _id: generateId(),
    name: korName,
    pName: pName,
    domain: "",
    type: type,
    defaultValue: defaultValue || "",
    isAllowNull: isAllowNull,
    comment: comment || "",
    relEntity: null,
    relFieldId: null,
    relType: null,
    relGroupId: null
  };
}

const additions = {
  'xsRopkQR6Bea4Fqfu': {
    insertBeforeFieldNames: ['Field2', 'Field3'],
    fields: [
      makeField('우편번호', 'emp_zip_code', 'VARCHAR(255)', true, ''),
      makeField('상세주소', 'emp_address_detail', 'VARCHAR(255)', true, ''),
      makeField('메일함용량', 'emp_mailbox_size', 'VARCHAR(255)', false, '', '5GB'),
      makeField('소프트삭제일', 'delete_at', 'DATE', true, ''),
      makeField('계약종료일', 'contract_end_date', 'DATE', true, ''),
    ]
  },
  'z2nMkDWTg2d5sZL3H': {
    fields: [
      makeField('직급 정렬순서', 'grade_order', 'INT', false, ''),
    ]
  },
  'khHrZfin54TLWGPqa': {
    fields: [
      makeField('연봉계약 ID', 'contract_id', 'BIGINT', false, 'FK → salary_contract'),
    ]
  },
  'ikpxMppWXHy3437sR': {
    fields: [
      makeField('규칙 설명', 'create_rule_desc', 'VARCHAR(255)', true, ''),
    ]
  },
  'nRvFbLfQefe5RBEkJ': {
    fields: [
      makeField('사원이름', 'emp_name', 'VARCHAR(50)', false, ''),
    ]
  },
  'N4LhYGKS3xuknBmCr': {
    fields: [
      makeField('사원이름', 'emp_name', 'VARCHAR(255)', true, ''),
      makeField('퇴직사유', 'resign_reason', 'VARCHAR(255)', true, ''),
      makeField('퇴직 예정일', 'resign_date', 'DATE', true, ''),
    ]
  },
};

const inputPath = process.argv[2];
if (!inputPath) {
  console.error('Usage: node fix-erd.js <input.json>');
  process.exit(1);
}

const raw = fs.readFileSync(inputPath, 'utf-8');
const data = JSON.parse(raw);

const entities = data.entityData;
if (!entities) {
  console.error('entityData not found');
  process.exit(1);
}

for (const entity of entities) {
  const config = additions[entity._id];
  if (!config) continue;

  const fieldsArr = entity.fields;

  if (config.insertBeforeFieldNames) {
    let insertIdx = -1;
    for (let i = 0; i < fieldsArr.length; i++) {
      if (config.insertBeforeFieldNames.includes(fieldsArr[i].name) ||
          config.insertBeforeFieldNames.includes(fieldsArr[i].pName)) {
        insertIdx = i;
        break;
      }
    }
    if (insertIdx >= 0) {
      fieldsArr.splice(insertIdx, 0, ...config.fields);
    } else {
      fieldsArr.push(...config.fields);
    }
  } else {
    fieldsArr.push(...config.fields);
  }

  console.error(`Patched: ${entity.name} (+${config.fields.length} fields)`);
}

const outputPath = inputPath.replace('.json', '-fixed.json');
fs.writeFileSync(outputPath, JSON.stringify(data, null, 2), 'utf-8');
console.error(`Output: ${outputPath}`);
