import { useState, type ReactNode } from 'react';
import { TryCard } from '../components/TryCard';
import { call, type CallResult } from '../lib/api';
import { FANOUT_CHUNK_FOLLOWERS, FANOUT_WORKERS, jobsFor, writesFor } from '../lib/fanout';
import { useFlash } from '../lib/useFlash';

type Stats = { jobsEnqueued: number; jobsProcessed: number; jobsPending: number };

export function FanoutPage() {
  const [alice] = useState(() => crypto.randomUUID());
  const [bob] = useState(() => crypto.randomUUID());
  const [followerId, setFollowerId] = useState<string>(alice);
  const [followeeId, setFolloweeId] = useState<string>(bob);
  const [authorId, setAuthorId] = useState<string>(bob);
  const [text, setText] = useState('el fan-out ya escribe');
  const [timelineUser, setTimelineUser] = useState<string>(alice);
  const [follow, setFollow] = useState<CallResult | null>(null);
  const [publish, setPublish] = useState<CallResult | null>(null);
  const [timeline, setTimeline] = useState<CallResult | null>(null);
  const [stats, setStats] = useState<Stats | null>(null);

  const refreshStats = () =>
    call('/metrics/fanout').then((result) => {
      setStats(result.ok ? (result.payload as Stats) : null);
    });

  return (
    <main className="page">
      <div className="lede">
        <h1>Fan-out on write</h1>
        <p className="question">Al publicar, ¿quién escribe los timelines de los seguidores?</p>
        <p>
          Nadie, en el request. Publicar guarda el post, despacha <strong>un</strong> job y contesta 201. La
          cola hace el resto: un job reparte, y los que escriben van de a {FANOUT_CHUNK_FOLLOWERS} seguidores
          con {FANOUT_WORKERS} workers en paralelo.
        </p>
      </div>

      <section className="section">
        <h2>La cadena</h2>
        <div className="protocol">
          <Step n="1" tag="request">
            <code>PublishPost</code> guarda el post y despacha <code>FanoutPost(postId, authorId)</code>. No
            lee seguidores: si lo hiciera, publicar costaría O(seguidores) antes del 201.
          </Step>
          <Step n="2" tag="job">
            <code>FanoutPost</code> lee la lista de seguidores y la parte en tandas de{' '}
            {FANOUT_CHUNK_FOLLOWERS}. Por cada tanda despacha un <code>WriteTimelineChunk</code>.
          </Step>
          <Step n="3" tag="job">
            <code>WriteTimelineChunk</code> hace <code>prepend</code> del <code>PostId</code> en cada
            timeline. Viajan 16 bytes por seguidor, no el post: eso es el cálculo de S1.
          </Step>
        </div>
        <p className="deep">
          El consumidor es un <code>JobProcessor</code> de Trantor, registrado como segundo{' '}
          <code>HostedService</code> del proceso después del servidor HTTP. La cola es del lab:{' '}
          <code>InMemoryMessageQueue</code>, porque lo único que publica Trantor es SQS.
        </p>
      </section>

      <JobsCalculator />

      <section className="section">
        <h2>La medición</h2>
        <div className="derived">
          <Row k="seguidores" v="1.000" />
          <Row k="jobs generados" v="11 (1 que reparte + 10 tandas)" />
          <Row k="publish respondió en" v="7 ms" />
          <Row k="fan-out completo en" v="14 ms" />
        </div>
        <p className="run__note" style={{ textAlign: 'left', marginTop: 10 }}>
          Sale de <code>./lab bench</code>, que corre <code>FanoutThroughputTest</code> contra el proceso
          real. Todo en memoria y en la misma JVM: es el piso, no una promesa de producción.
        </p>
      </section>

      <section className="section">
        <h2>Probalo</h2>
        <p className="lede">
          Orden: seguir → publicar → pedir el timeline. A diferencia de S1, ahora el timeline de Alice se
          llena solo.
        </p>
        <div className="try-row">
          <TryCard
            title="seguir a alguien"
            submitLabel="seguir a alguien"
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
            submitLabel="publicar y disparar el fan-out"
            result={publish}
            onSubmit={() => {
              void call('/posts', { method: 'POST', body: { authorId, text } })
                .then(setPublish)
                .then(refreshStats);
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
              void call(`/timelines/${timelineUser}`).then(setTimeline).then(refreshStats);
            }}
          >
            <Field label="userId" value={timelineUser} onChange={setTimelineUser} />
          </TryCard>
        </div>
      </section>

      <section className="section">
        <h2>La cola, ahora</h2>
        <div className="derived">
          <Row k="jobs generados" v={stats == null ? '—' : String(stats.jobsEnqueued)} />
          <Row k="jobs procesados" v={stats == null ? '—' : String(stats.jobsProcessed)} />
          <Row k="jobs pendientes" v={stats == null ? '—' : String(stats.jobsPending)} />
        </div>
        <button className="btn" type="button" onClick={() => void refreshStats()}>
          leer /metrics/fanout
        </button>
        <p className="run__note" style={{ textAlign: 'left', marginTop: 10 }}>
          Trantor no expone métricas de jobs — el <code>HttpServer</code> tiene <code>stats</code>, el{' '}
          <code>JobProcessor</code> no. Estos contadores son de la cola del lab.
        </p>
      </section>

      <p className="footnote">
        Lo que S2 <strong>no</strong> resuelve: una celebridad con 50 millones de seguidores son 500.001 jobs
        y 50 millones de escrituras por post. El umbral es S3.
      </p>
    </main>
  );
}

function JobsCalculator() {
  const [followers, setFollowers] = useState(1_000);
  const jobs = jobsFor(followers);
  const writes = writesFor(followers);

  return (
    <section className="section">
      <h2>Cuántos jobs cuesta un post</h2>
      <div className="calc-fields">
        <label className="field">
          <span className="field__label">seguidores del autor</span>
          <input
            className="field__input"
            type="number"
            min={0}
            step={1}
            value={Number.isFinite(followers) ? followers : ''}
            onChange={(event) => {
              const next = Number(event.target.value);
              if (!Number.isFinite(next) || next < 0) return;
              setFollowers(next);
            }}
          />
        </label>
      </div>
      <div className="versus" style={{ marginTop: 16 }}>
        <div className="side" data-kind="guarded">
          <div className="side__head">
            <span className="side__title">jobs</span>
            <span className="side__sub">lo que entra a la cola</span>
          </div>
          <div className="verdict">
            <FlashNumber text={jobs.toLocaleString('es-AR')} />
            <div className="verdict__label">{`1 + ${followers.toLocaleString('es-AR')} / ${FANOUT_CHUNK_FOLLOWERS}`}</div>
          </div>
        </div>
        <div className="versus__rule" aria-hidden="true" />
        <div className="side" data-kind="naive">
          <div className="side__head">
            <span className="side__title">escrituras</span>
            <span className="side__sub">timelines tocados</span>
          </div>
          <div className="verdict">
            <FlashNumber text={writes.toLocaleString('es-AR')} />
            <div className="verdict__label">una por seguidor</div>
          </div>
        </div>
      </div>
      <p className="deep" style={{ maxWidth: '68ch', color: 'var(--text-dim)', marginTop: 12 }}>
        La tanda es la perilla. Más chica: más jobs, más paralelismo, más overhead de cola. Más grande: menos
        jobs y un worker atado más tiempo a un solo post. Las escrituras no cambian — el fan-out on write las
        paga todas, siempre.
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

function Row({ k, v }: { k: string; v: string }) {
  return (
    <div className="derived__row">
      <span className="derived__k">{k}</span>
      <span className="derived__v">{v}</span>
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
