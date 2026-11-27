import { StrictMode, useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { createRoot } from 'react-dom/client';
import { api, clearSession, login, logout, refresh, subscribe } from './api';
import type { Profile } from './api';
import './style.css';

type Invitation = { id: string; senderId: string; recipientId: string; expiresAt: string };
type Relation = { id: string; teacherId: string; studentId: string; status: string };
type Page<T> = { items: T[]; nextCursor: string | null; hasMore: boolean };
function App() {
  const [user, setUser] = useState<Profile | null>(null);
  const [loading, setLoading] = useState(true);
  const [signup, setSignup] = useState(false);
  const [teacher, setTeacher] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [invitations, setInvitations] = useState<Invitation[]>([]);
  const [relations, setRelations] = useState<Relation[]>([]);
  const [inviteCursor, setInviteCursor] = useState<string | null>(null);
  const [relationCursor, setRelationCursor] = useState<string | null>(null);
  const [deleteOpen, setDeleteOpen] = useState(false);
  useEffect(() => subscribe(setUser), []);
  useEffect(() => { void refresh().catch((e: unknown) => setError(e instanceof Error ? e.message : '연결 오류')).finally(() => setLoading(false)); }, []);
  async function loadRelations() {
    const [i, r] = await Promise.all([api<Page<Invitation>>('/relationships/invitations'), api<Page<Relation>>('/relationships')]);
    setInvitations(i.items); setRelations(r.items); setInviteCursor(i.nextCursor); setRelationCursor(r.nextCursor);
  }
  useEffect(() => {
    setInvitations([]); setRelations([]); setInviteCursor(null); setRelationCursor(null); setDeleteOpen(false);
    if (user) void loadRelations().catch((e: unknown) => setError(e instanceof Error ? e.message : '연결 오류'));
  }, [user?.id]);
  async function action(work: () => Promise<void>) {
    setBusy(true); setError(''); setMessage('');
    try { await work(); } catch (e) { setError(e instanceof Error ? e.message : '요청을 처리할 수 없습니다.'); } finally { setBusy(false); }
  }
  function submitAuth(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = event.currentTarget; const data = new FormData(form);
    void action(async () => {
      const email = String(data.get('email')); const password = String(data.get('password'));
      if (signup) {
        if (new TextEncoder().encode(password).length > 72) throw new Error('비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.');
        await api('/auth/signup', 'POST', { email, password, name: String(data.get('name')), requestedRole: teacher ? 'TEACHER' : 'STUDENT', ...(teacher ? { teacherInvitationToken: String(data.get('invitation')) } : {}) });
        setMessage('가입되었습니다. 로그인해 주세요.'); setSignup(false); form.reset();
      } else { await login(email, password); form.reset(); }
    });
  }
  return <main>
    <header><a className="brand" href="/">TutorService</a><span>개인 맞춤형 학습도우미</span></header>
    <div className="layout">
      <aside><p className="eyebrow">배움의 시작을 함께</p><h1>나의 속도로,<br />한 걸음씩.</h1><p className="intro">학생과 선생님이 함께 배우는 공간입니다. 계정을 만들고 담당 선생님과 연결해 보세요.</p><div className="note"><strong>현재 이용할 수 있어요</strong><p>회원가입 · 로그인 · 내 정보 관리<br />선생님과 학생의 초대 및 연결</p></div></aside>
      <section className="card" aria-label={user ? '내 계정' : '회원 인증'}>
        {loading ? <p role="status">로그인 상태를 확인하고 있습니다.</p> : user ? <>
          <div className="card-heading"><div><span className="badge">{user.role === 'TEACHER' ? '선생님' : '학생'}</span><h2>{user.name} 님, 안녕하세요.</h2></div><button className="text-button" disabled={busy} onClick={() => void action(logout)}>로그아웃</button></div>
          <p className="muted">{user.email}</p><label htmlFor="my-id">내 회원 ID</label><input id="my-id" value={user.id} readOnly /><small>담당 선생님에게 이 ID를 알려주면 초대를 받을 수 있습니다.</small>
          <form onSubmit={(e) => { e.preventDefault(); const data = new FormData(e.currentTarget); void action(async () => { const profile = await api<Profile>('/users/me', 'PATCH', { name: String(data.get('name')) }); setUser(profile); setMessage('이름을 변경했습니다.'); }); }}>
            <label htmlFor="profile-name">이름</label><input id="profile-name" name="name" key={user.name} defaultValue={user.name} required maxLength={80} /><button disabled={busy}>이름 저장</button>
          </form>
          <div className="divider" /><h3>담당 관계</h3>
          {user.role === 'TEACHER' && <form onSubmit={(e) => { e.preventDefault(); const form = e.currentTarget; const data = new FormData(form); void action(async () => { await api('/relationships/invitations', 'POST', { recipientId: String(data.get('recipientId')) }); form.reset(); await loadRelations(); setMessage('학생에게 초대를 보냈습니다.'); }); }}><label htmlFor="recipient">학생 회원 ID</label><input id="recipient" name="recipientId" required placeholder="학생에게 전달받은 UUID" /><button disabled={busy}>학생 초대</button></form>}
          <ul className="items">{invitations.map((i) => <li key={i.id}><div><strong>{user.role === 'STUDENT' ? '도착한 선생님 초대' : '수락 대기 중인 초대'}</strong><small>{user.role === 'STUDENT' ? i.senderId : i.recipientId}</small><small>만료 {new Date(i.expiresAt).toLocaleString('ko-KR')}</small></div>{user.role === 'STUDENT' && <button disabled={busy} onClick={() => void action(async () => { await api('/relationships/invitations/' + i.id + '/accept', 'POST'); await loadRelations(); setMessage('선생님과 연결되었습니다.'); })}>수락</button>}</li>)}</ul>
          <ul className="items">{relations.map((r) => <li key={r.id}><div><strong>{user.role === 'STUDENT' ? '연결된 선생님' : '연결된 학생'}</strong><small>{user.role === 'STUDENT' ? r.teacherId : r.studentId}</small></div><button className="secondary" disabled={busy} onClick={() => void action(async () => { await api('/relationships/' + r.id, 'DELETE'); await loadRelations(); setMessage('관계를 해제했습니다.'); })}>연결 해제</button></li>)}</ul>
          {inviteCursor && <button disabled={busy} onClick={() => void action(async () => { const p = await api<Page<Invitation>>('/relationships/invitations?cursor=' + encodeURIComponent(inviteCursor)); setInvitations((items) => [...items, ...p.items]); setInviteCursor(p.nextCursor); })}>초대 더 보기</button>}
          {relationCursor && <button disabled={busy} onClick={() => void action(async () => { const p = await api<Page<Relation>>('/relationships?cursor=' + encodeURIComponent(relationCursor)); setRelations((items) => [...items, ...p.items]); setRelationCursor(p.nextCursor); })}>관계 더 보기</button>}
          {!invitations.length && !relations.length && <p className="muted">아직 연결된 관계나 대기 중인 초대가 없습니다.</p>}<button className="text-button" disabled={busy} onClick={() => void action(loadRelations)}>초대·관계 새로고침</button>
          <div className="divider" /><button className="text-button danger" onClick={() => setDeleteOpen(!deleteOpen)} disabled={busy}>회원 탈퇴</button>
          {deleteOpen && <form onSubmit={(e) => { e.preventDefault(); const data = new FormData(e.currentTarget); void action(async () => { await api('/users/me', 'DELETE', { password: String(data.get('password')) }); clearSession(); setMessage('회원 탈퇴가 완료되었습니다.'); }); }}><p className="muted">계정 정보와 담당 관계를 삭제하고 로그인을 종료합니다.</p><label htmlFor="delete-password">현재 비밀번호로 확인</label><input id="delete-password" name="password" type="password" autoComplete="current-password" required /><button className="danger-button" disabled={busy}>탈퇴 확인</button></form>}
        </> : <><h2>{signup ? '회원가입' : '다시 만나 반가워요'}</h2><p className="muted">{signup ? '배움을 함께할 계정을 만들어 주세요.' : '내 계정으로 학습 공간에 들어오세요.'}</p><form onSubmit={submitAuth} key={signup ? 'signup' : 'login'}>
          {signup && <><label htmlFor="name">이름</label><input id="name" name="name" autoComplete="name" required maxLength={80} /></>}
          <label htmlFor="email">이메일</label><input id="email" name="email" type="email" autoComplete="username" required maxLength={254} />
          <label htmlFor="password">비밀번호</label><input id="password" name="password" type="password" autoComplete={signup ? 'new-password' : 'current-password'} required minLength={signup ? 12 : undefined} maxLength={72} />
          {signup && <><small>12자 이상, UTF-8 72바이트 이하로 입력해 주세요.</small><label className="checkbox"><input type="checkbox" checked={teacher} onChange={(e) => setTeacher(e.target.checked)} />검증된 선생님으로 가입</label>{teacher && <><label htmlFor="invitation">선생님 가입 초대 코드</label><input id="invitation" name="invitation" required autoComplete="off" maxLength={64} /><small>운영자가 검증 후 전달한 초대 코드가 필요합니다.</small></>}</>}
          <button className="primary" disabled={busy}>{busy ? '처리 중…' : signup ? '계정 만들기' : '로그인'}</button>
        </form><p className="switch">{signup ? '이미 계정이 있나요?' : '처음 오셨나요?'} <button className="text-button" disabled={busy} onClick={() => { setSignup(!signup); setError(''); setMessage(''); }}>{signup ? '로그인' : '회원가입'}</button></p></>}
        {error && <p className="feedback error" role="alert">{error}</p>}{message && <p className="feedback" role="status">{message}</p>}
      </section>
    </div><footer>일반 채팅과 AI 학습 상담은 다음 단계에서 제공됩니다.</footer>
  </main>;
}
const root = document.getElementById('root');
if (!root) throw new Error('Root element is missing');
createRoot(root).render(<StrictMode><App /></StrictMode>);
