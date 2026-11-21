const CODE_ALPHABET = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';

/** 生成设计器内部标识：时间戳 base32 前缀 + 随机后缀，同毫秒内并发创建不冲突。 */
export function newId(): string {
  let time = Date.now();
  let out = '';
  for (let i = 0; i < 10; i += 1) {
    out = CODE_ALPHABET.charAt(time % 32) + out;
    time = Math.floor(time / 32);
  }
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  for (const byte of bytes) {
    out += CODE_ALPHABET.charAt(byte % 32);
  }
  return out;
}

/** 当前时间 ISO 字符串，统一各设计器的 createdAt/updatedAt 写法。 */
export function now(): string {
  return new Date().toISOString();
}

/** 在既有编码集合内追加 _2/_3… 后缀直到唯一；编码长度限制 32，必要时截断基名。 */
export function uniqueCode(base: string, existing: string[]): string {
  const CODE_MAX_LENGTH = 32;
  if (!existing.includes(base)) return base;
  for (let index = 2; index < 100; index += 1) {
    const suffix = `_${index}`;
    const trimmedBase = base.slice(0, CODE_MAX_LENGTH - suffix.length);
    const candidate = `${trimmedBase}${suffix}`;
    if (!existing.includes(candidate)) return candidate;
  }
  return base.slice(0, 28);
}
