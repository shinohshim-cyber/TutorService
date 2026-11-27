export type Profile = { id: string; email: string; name: string; role: 'STUDENT' | 'TEACHER' };
type Tokens = { accessToken: string; expiresIn: number; user: Profile };
let access = '';
let pending: Promise<Profile | null> | null = null;
let current: Profile | null = null;
let csrfPending: Promise<string> | null = null;
const listeners = new Set<(user: Profile | null) => void>();
const channel = typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel('tutor-auth') : null;
function update(data: Tokens | null, broadcast = true) {
  access = data?.accessToken ?? ''; current = data?.user ?? null;
  listeners.forEach((listener) => listener(current));
  if (broadcast) channel?.postMessage(data);
}
channel?.addEventListener('message', (event: MessageEvent<Tokens | null>) => update(event.data, false));
export function subscribe(listener: (user: Profile | null) => void) {
  listeners.add(listener); listener(current); return () => { listeners.delete(listener); };
}
function csrf(): Promise<string> {
  if (csrfPending) return csrfPending;
  const run = async () => {
    const response = await fetch('/api/v1/auth/csrf', { credentials: 'same-origin', cache: 'no-store' });
    if (!response.ok) throw new Error('서비스에 연결할 수 없습니다.');
    const body: { token: string } = await response.json(); return body.token;
  };
  csrfPending = (navigator.locks ? navigator.locks.request('tutor-csrf', run) : run()).finally(() => { csrfPending = null; });
  return csrfPending;
}
async function raw(path: string, method = 'GET', body?: unknown): Promise<Response> {
  const headers: Record<string, string> = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (method !== 'GET') headers['X-XSRF-TOKEN'] = await csrf();
  if (access && !path.startsWith('/auth/')) headers.Authorization = `Bearer ${access}`;
  return fetch(`/api/v1${path}`, { method, headers, credentials: 'same-origin', cache: 'no-store', body: body === undefined ? undefined : JSON.stringify(body) });
}
export function refresh(): Promise<Profile | null> {
  if (pending) return pending;
  const run = async () => {
    const response = await raw('/auth/refresh', 'POST');
    if (response.status === 401) { update(null); return null; }
    if (!response.ok) throw new Error('로그인 상태를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.');
    const data: Tokens = await response.json(); update(data); return data.user;
  };
  pending = (navigator.locks ? navigator.locks.request('tutor-refresh', run) : run()).finally(() => { pending = null; });
  return pending;
}
export async function api<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  let response = await raw(path, method, body);
  if (response.status === 401 && !path.startsWith('/auth/')) {
    if (await refresh()) response = await raw(path, method, body);
  }
  if (!response.ok) {
    const messages: Record<number, string> = { 400: '입력 형식과 비밀번호 조건을 확인해 주세요.', 401: '로그인 정보가 올바르지 않거나 세션이 만료되었습니다.', 403: '권한 또는 초대 정보를 확인해 주세요.', 404: '접근할 수 있는 항목이 없습니다.', 409: '이미 처리되었거나 현재 상태에서 진행할 수 없습니다.', 429: '요청이 많습니다. 잠시 후 다시 시도해 주세요.', 503: '서비스 연결이 원활하지 않습니다. 잠시 후 다시 시도해 주세요.' };
    throw new Error(messages[response.status] ?? '요청을 처리할 수 없습니다.');
  }
  return response.status === 204 ? undefined as T : response.json() as Promise<T>;
}
export async function login(email: string, password: string) { const data = await api<Tokens>('/auth/login', 'POST', { email, password }); update(data); }
export async function logout() { await api<void>('/auth/logout', 'POST'); update(null); }
export function clearSession() { update(null); }
