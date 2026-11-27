import { StrictMode, useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './style.css';

function App() {
  const [connected, setConnected] = useState<boolean | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    fetch('/api/v1/system/status', { signal: controller.signal })
      .then(async (response) => {
        if (!response.ok) throw new Error('Service unavailable');
        const status: { status?: string } = await response.json();
        setConnected(status.status === 'UP');
      })
      .catch((error: unknown) => {
        if (!(error instanceof DOMException && error.name === 'AbortError')) {
          setConnected(false);
        }
      });
    return () => controller.abort();
  }, []);

  return (
    <main>
      <p className="eyebrow">개인 맞춤형 학습도우미</p>
      <h1>함께 배우는<br />학습 공간</h1>
      <p className="intro">선생님과의 대화와 AI 학습 상담을 위한 서비스를 준비하고 있습니다.</p>
      <section aria-label="서비스 연결 상태" role="status">
        <span className={`dot ${connected === false ? 'error' : ''}`} />
        <div>
          <strong>{connected === null ? '연결을 확인하고 있습니다' : connected ? '서비스에 연결되었습니다' : '서비스에 연결할 수 없습니다'}</strong>
          <p>{connected === false ? '잠시 후 새로고침해 주세요.' : '학습을 돕는 기능을 순서대로 준비하고 있습니다.'}</p>
        </div>
      </section>
    </main>
  );
}

const root = document.getElementById('root');
if (!root) throw new Error('Root element is missing');
createRoot(root).render(<StrictMode><App /></StrictMode>);
