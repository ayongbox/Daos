import { readFileSync } from 'node:fs';

const frontend = new URL('../..', import.meta.url).pathname;
const javaEnum = `${frontend}/../daos-core/src/main/java/io/github/ayongbox/daos/core/metadata/common/FieldLogicalType.java`;
const tsSchema = `${frontend}/packages/metadata/src/schema-v1.ts`;

const javaSource = readFileSync(javaEnum, 'utf8');
const tsSource = readFileSync(tsSchema, 'utf8');

const javaCodes = new Set([...javaSource.matchAll(/\("([a-z0-9_]+)",/g)].map((m) => m[1]));

const unionMatch = tsSource.match(/export type FieldLogicalType =([\s\S]*?);/);
if (!unionMatch) {
  console.error('未在 schema-v1.ts 中找到 FieldLogicalType 联合类型');
  process.exit(1);
}
const tsCodes = new Set([...unionMatch[1].matchAll(/'([a-z0-9_]+)'/g)].map((m) => m[1]));

const missingInTs = [...javaCodes].filter((code) => !tsCodes.has(code));
const missingInJava = [...tsCodes].filter((code) => !javaCodes.has(code));

if (missingInTs.length > 0 || missingInJava.length > 0) {
  console.error('schema v1 逻辑类型不一致：');
  if (missingInTs.length > 0) console.error('  TS 缺失: ' + missingInTs.join(', '));
  if (missingInJava.length > 0) console.error('  Java 缺失: ' + missingInJava.join(', '));
  process.exit(1);
}

console.log(`schema v1 逻辑类型一致，共 ${javaCodes.size} 项`);
