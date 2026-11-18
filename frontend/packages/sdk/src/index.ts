export interface ApiErrorBody {
  code: string;
  message: string;
  traceId?: string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly traceId?: string;

  constructor(status: number, body: ApiErrorBody) {
    super(body.message);
    this.status = status;
    this.code = body.code;
    this.traceId = body.traceId;
  }
}

interface Envelope<T> {
  code: number;
  msg?: string;
  data: T;
}

function isEnvelope(value: unknown): value is Envelope<unknown> {
  return typeof value === 'object' && value !== null && 'code' in value && 'data' in value;
}

export interface ApiClient {
  get<T>(path: string): Promise<T>;
  post<T>(path: string, body?: unknown): Promise<T>;
  put<T>(path: string, body?: unknown): Promise<T>;
  delete<T>(path: string): Promise<T>;
}

export interface ApiClientOptions {
  /** 携带令牌的请求返回 401 时回调，用于清理登录态并回到登录页。 */
  onUnauthorized?: () => void;
}

export function createApiClient(
  baseUrl: string,
  getToken: () => string | null,
  options: ApiClientOptions = {}
): ApiClient {
  async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
    const headers: Record<string, string> = { Accept: 'application/json' };
    const token = getToken();
    if (token) headers.Authorization = `Bearer ${token}`;
    if (body !== undefined) headers['Content-Type'] = 'application/json';

    const response = await fetch(`${baseUrl}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body)
    });

    if (response.status === 401 && token) {
      options.onUnauthorized?.();
    }

    if (!response.ok) {
      const payload = (await response.json().catch(() => ({ code: 'unknown', msg: response.statusText }))) as {
        code?: number | string;
        msg?: string;
        message?: string;
      };
      throw new ApiError(response.status, {
        code: String(payload.code ?? 'unknown'),
        message: payload.msg ?? payload.message ?? response.statusText
      });
    }
    if (response.status === 204) return undefined as T;

    const payload = (await response.json()) as T | Envelope<T>;
    if (isEnvelope(payload)) {
      if (payload.code !== 0) {
        throw new ApiError(200, { code: String(payload.code), message: payload.msg ?? '请求失败' });
      }
      return payload.data;
    }
    return payload as T;
  }

  return {
    get: (path) => request('GET', path),
    post: (path, body) => request('POST', path, body),
    put: (path, body) => request('PUT', path, body),
    delete: (path) => request('DELETE', path)
  };
}
