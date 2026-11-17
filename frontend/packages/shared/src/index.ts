const ULID_PATTERN = /^[0-9A-HJKMNP-TV-Z]{26}$/;

export function isUlid(value: string): boolean {
  return ULID_PATTERN.test(value);
}

export function assertNever(value: never): never {
  throw new Error(`未处理的分支: ${JSON.stringify(value)}`);
}

export function formatDateTime(value: string | Date, timezone = 'Asia/Shanghai'): string {
  const date = typeof value === 'string' ? new Date(value) : value;
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: timezone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  }).format(date);
}
