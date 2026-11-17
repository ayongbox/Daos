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

export interface ApiClient {
  get<T>(path: string): Promise<T>;
  post<T>(path: string, body?: unknown): Promise<T>;
  put<T>(path: string, body?: unknown): Promise<T>;
  delete<T>(path: string): Promise<T>;
}

export function createApiClient(baseUrl: string, getToken: () => string | null): ApiClient {
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

    if (!response.ok) {
      const payload = (await response.json().catch(() => ({ code: 'unknown', message: response.statusText }))) as ApiErrorBody;
      throw new ApiError(response.status, payload);
    }
    if (response.status === 204) return undefined as T;
    return (await response.json()) as T;
  }

  return {
    get: (path) => request('GET', path),
    post: (path, body) => request('POST', path, body),
    put: (path, body) => request('PUT', path, body),
    delete: (path) => request('DELETE', path)
  };
}
