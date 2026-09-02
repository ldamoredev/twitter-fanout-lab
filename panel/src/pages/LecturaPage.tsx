import { useState, type ReactNode } from 'react';
import { TryCard } from '../components/TryCard';
import { call, type CallResult } from '../lib/api';
import { whoSees } from '../lib/lectura';
import { useFlash } from '../lib/useFlash';

export function LecturaPage() {
  const [autor] = useState(() => crypto.randomUUID());
  const [seguidor] = useState(() => crypto.randomUUID());
  const [authorId, setAuthorId] = useState<string>(autor);
  const [text, setText] = useState('el autor lo ve ya');
  const [followerId, setFollowerId] = useState<string>(seguidor);
  const [followeeId, setFolloweeId] = useState<string>(autor);
  const [timelineUser, setTimelineUser] = useState<string>(autor);
  const [follow, setFollow] = useState<CallResult | null>(null);
  const [publish, setPublish] = useState<CallResult | null>(null);
  const [timeline, setTimeline] = useState<CallResult | null>(null);

  return (
    <main className="page">
      <div className="lede">
        <h1>Hidratación y read-your-writes</h1>
        <p className="question">¿El autor ve su post al toque, o espera al fan-out como todos?</p>
        <p>
          Dos caminos de lectura, no un bug. El autor hidrata desde cache y mergea sus propios posts: lo
          suyo está en el feed cuando el 201 ya salió. El seguidor lee el timeline precomputado, que el
          fan-out llena después. Consistencia distinta por camino.
        </p>
      </div>

      <section className="section">
        <h2>La regla</h2>
        <div className="protocol">
          <Step n="1" tag="write">
            <code>PublishPost</code> entra a <code>EventDispatcher.defer</code>, publica{' '}
            <code>PostPublished</code> y recién ahí persiste y cachea. Sin <code>defer</code>, el handler
            correría con el store vacío.
          </Step>
          <Step n="2" tag="event">
            Al salir del bloque, el evento dispara <code>FanoutPost</code>. <code>defer</code> sólo
            bufferiza eventos: un <code>jobs.dispatch</code> adentro del bloque no se atrasa.
          </Step>
          <Step n="3" tag="read">
            <code>GetTimeline</code> mergea el precomputado, el pull de celebridades y los posts del
            lector, y hidrata cada id desde <code>InMemoryCache</code>. El feed ahora trae texto.
          </Step>
        </div>
        <p className="deep">
          El cache no viene inyectado: <code>CacheModule</code> registra la factory, el lab construye{' '}
          <code>PostCache</code>. Guarda <code>PostSnapshot</code>, no el <code>Post</code> vivo — el
          comentario de Trantor es explícito.
        </p>
      </section>

      <ConsistencyCalculator />

      <section className="section">
        <h2>Probalo</h2>
        <p className="lede">
          Publicá como el autor y pedí <em>su</em> timeline: el texto está, aunque nadie le haya escrito
          el timeline. El del seguidor espera al fan-out (y a que lo siga).
        </p>
        <div className="try-row">
          <TryCard
            title="seguir al autor"
            submitLabel="seguir al autor"
            result={follow}
            onSubmit={() => {
              void call('/follows', { method: 'POST', body: { followerId, followeeId } }).then(setFollow);
            }}
          >
            <Field label="followerId" value={followerId} onChange={setFollowerId} />
            <Field label="followeeId" value={followeeId} onChange={setFolloweeId} />
          </TryCard>

          <TryCard
            title="publicar un post"
            submitLabel="publicar"
            result={publish}
            onSubmit={() => {
              void call('/posts', { method: 'POST', body: { authorId, text } }).then(setPublish);
            }}
          >
            <Field label="authorId" value={authorId} onChange={setAuthorId} />
            <Field label="text" value={text} onChange={setText} spellCheck />
          </TryCard>

          <TryCard
            title="pedir un timeline"
            submitLabel="pedir un timeline"
            result={timeline}
            onSubmit={() => {
              void call(`/timelines/${timelineUser}`).then(setTimeline);
            }}
          >
            <Field label="userId" value={timelineUser} onChange={setTimelineUser} />
          </TryCard>
        </div>
        <p className="run__note" style={{ textAlign: 'left', marginTop: 10 }}>
          El userId del medio es el autor; el de la derecha, el seguidor. Cambiá el de la derecha{' '}
          <code>{seguidor}</code> para ver el otro camino.
        </p>
      </section>

      <p className="footnote">
        Lo que S4 <strong>no</strong> resuelve: si el proceso se cae entre persistir y disparar el
        evento, el fan-out se pierde. Garantizar que el evento salga con la escritura es el outbox:{' '}
        <a href="/outbox.html">outbox</a>.
      </p>
    </main>
  );
}

function ConsistencyCalculator() {
  const [elapsedMs, setElapsedMs] = useState(0);
  const [fanoutMs, setFanoutMs] = useState(14);
  const who = whoSees(elapsedMs, fanoutMs);
  const authorLabel = 'sí';
  const followerLabel = who === 'both' ? 'sí' : 'todavía no';

  return (
    <section className="section">
      <h2>Quién ve el post, y cuándo</h2>
      <div className="calc-fields">
        <label className="field">
          <span className="field__label">ms desde el 201</span>
          <input
            className="field__input"
            type="number"
            min={0}
            step={1}
            value={Number.isFinite(elapsedMs) ? elapsedMs : ''}
            onChange={(event) => {
              const next = Number(event.target.value);
              if (!Number.isFinite(next) || next < 0) return;
              setElapsedMs(next);
            }}
          />
        </label>
        <label className="field">
          <span className="field__label">latencia del fan-out (ms)</span>
          <input
            className="field__input"
            type="number"
            min={0}
            step={1}
            value={Number.isFinite(fanoutMs) ? fanoutMs : ''}
            onChange={(event) => {
              const next = Number(event.target.value);
              if (!Number.isFinite(next) || next < 0) return;
              setFanoutMs(next);
            }}
          />
        </label>
      </div>
      <div className="versus" style={{ marginTop: 16 }}>
        <div className="side" data-kind="guarded">
          <div className="side__head">
            <span className="side__title">autor</span>
            <span className="side__sub">cache + posts propios</span>
          </div>
          <div className="verdict">
            <FlashNumber text={authorLabel} />
            <div className="verdict__label">read-your-writes</div>
          </div>
        </div>
        <div className="versus__rule" aria-hidden="true" />
        <div className="side" data-kind="naive">
          <div className="side__head">
            <span className="side__title">seguidor</span>
            <span className="side__sub">timeline precomputado</span>
          </div>
          <div className="verdict">
            <FlashNumber text={followerLabel} />
            <div className="verdict__label">eventual, espera al fan-out</div>
          </div>
        </div>
      </div>
      <p className="deep" style={{ maxWidth: '68ch', color: 'var(--text-dim)', marginTop: 12 }}>
        Los 14 ms son el piso que midió S2 in-memory. En producción el hueco es más grande, y por eso
        los dos caminos no pueden compartir la misma promesa de consistencia.
      </p>
    </section>
  );
}

function Step({ n, tag, children }: { n: string; tag: string; children: ReactNode }) {
  return (
    <div className="protocol__step">
      <span className="protocol__n">{n}</span>
      <span className="protocol__tag">{tag}</span>
      <div>{children}</div>
    </div>
  );
}

function FlashNumber({ text }: { text: string }) {
  const ref = useFlash<HTMLDivElement>(text, { skipFirst: true });
  return (
    <div className="verdict__number" ref={ref}>
      {text}
    </div>
  );
}

function Field({
  label,
  value,
  onChange,
  spellCheck = false,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  spellCheck?: boolean;
}) {
  return (
    <label className="field">
      <span className="field__label">{label}</span>
      <input
        className="field__input"
        type="text"
        spellCheck={spellCheck}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}
