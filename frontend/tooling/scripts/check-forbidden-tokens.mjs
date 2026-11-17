import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

const root = new URL('../..', import.meta.url).pathname;
const scanRoots = ['apps', 'packages'];
const extensions = new Set(['.ts', '.tsx', '.css', '.scss', '.less']);
const forbidden = [
  { token: '#1677ff', reason: '禁止使用 AntD 默认蓝，统一使用 #3370FF' },
  { token: 'border-radius: 2px', reason: '圆角只允许 6 / 8 / 10' },
  { token: 'borderRadius: 2', reason: '圆角只允许 6 / 8 / 10' },
  { token: 'border-radius:2px', reason: '圆角只允许 6 / 8 / 10' }
];

function walk(dir, files) {
  for (const entry of readdirSync(dir)) {
    if (entry === 'node_modules' || entry === 'dist') continue;
    const full = join(dir, entry);
    const stat = statSync(full);
    if (stat.isDirectory()) {
      walk(full, files);
    } else {
      const dot = entry.lastIndexOf('.');
      if (dot >= 0 && extensions.has(entry.slice(dot))) files.push(full);
    }
  }
}

const files = [];
for (const dir of scanRoots) walk(join(root, dir), files);

const violations = [];
for (const file of files) {
  const content = readFileSync(file, 'utf8');
  const lines = content.split('\n');
  lines.forEach((line, index) => {
    for (const item of forbidden) {
      if (line.includes(item.token)) {
        violations.push(`${file.slice(root.length)}:${index + 1} ${item.token} -> ${item.reason}`);
      }
    }
  });
}

if (violations.length > 0) {
  console.error('UI 令牌扫描失败：');
  for (const violation of violations) console.error('  ' + violation);
  process.exit(1);
}
console.log(`UI 令牌扫描通过，检查 ${files.length} 个文件`);
