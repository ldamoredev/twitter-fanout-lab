import { useState, type ReactNode } from 'react';
import { TryCard } from '../components/TryCard';
import { call, type CallResult } from '../lib/api';
import { jobsFor } from '../lib/fanout';
import {
  outboxEnqueues,
  queueJobsFor,
  retriesAfterThrow,
  sameDedupProcessedTwice,
  type ThrowingKind,
  type TxOutcome,
} from '../lib/outbox';
import { useFlash } from '../lib/useFlash';

export function OutboxPage() {
  const [autor] = useState(() => crypto.randomUUID());
  const [authorId, setAuthorId] = useState<string>(autor);
  const [text, setText] = useState('el outbox espera al commit');
  const [publish, setPublish] = useState<CallResult | null>(null);
  const [stats, setStats] = useState<CallResult | null>(null);

  const refreshStats = () => call('/metrics/fanout').then(setStats);

  return (
    <main className="page">
      <div className="lede">
        <h1>El outbox transaccional</h1>
        <p className="question">¿Si la transacción hace rollback, el fan-out sale igual?</p>
        <p>
          No, si el handler espera al commit. <code>FanoutOnPostPublished</code> es un{' '}
          <code>QueuedEventHandler</code> con <code>afterCommit = true</code>: el efecto no corre
          adentro del request. Se encola como <code>ProcessEventHandlerJob</code> cuando la tx
          commitea, y recién ahí despacha <code>FanoutPost</code>.
        </p>
      </div>

      <section className="section">
        <h2>La regla</h2>
        <div className="protocol">
          <Step n="1" tag="tx">
            <code>PublishPost</code> envuelve el <code>defer</code> en <code>transactional</code>.
            Sin un <code>TransactionManager</code> de verdad, <code>afterCommit</code> no espera:
            Trantor trae <code>NullTransactionManager</code> y <code>activeTransaction</code> es
            siempre null.
          </Step>
          <Step n="2" tag="event">
            Al commitear se encola <code>ProcessEventHandlerJob</code>. Un{' '}
            <code>events.on {'{ }'}</code> anónimo no sirve: no tiene <code>handlerType</code> y el
            job no puede rehidratarlo.
          </Step>
          <Step n="3" tag="job">
            El worker corre el handler, que despacha <code>FanoutPost</code>. Un job extra por
            publish, encima de la cadena de S2.
          </Step>
        </div>
        <p className="deep">
          Tres experimentos, no una feature. El outbox garantiza el <em>cuándo</em> se encola. No
          garantiza retry ni dedup: eso lo mide cada prueba de abajo.
        </p>
      </section>

      <Experiments />

      <section className="section">
        <h2>Probalo</h2>
        <p className="lede">
          Publicá y mirá <code>/metrics/fanout</code>. Sin seguidores son 2 jobs: el outbox y el{' '}
          <code>FanoutPost</code> que descubre que no hay a quién escribirle. El rollback no se
          puede fabricar por HTTP: lo prueba <code>OutboxExperimentsTest</code>.
        </p>
        <div className="try-row">
          <TryCard
            title="publicar un post"
            submitLabel="publicar"
            result={publish}
            onSubmit={() => {
              void call('/posts', { method: 'POST', body: { authorId, text } }).then((result) => {
                setPublish(result);
                void refreshStats();
              });
            }}
          >
            <Field label="authorId" value={authorId} onChange={setAuthorId} />
            <Field label="text" value={text} onChange={setText} spellCheck />
          </TryCard>

          <TryCard
            title="métricas de la cola"
            submitLabel="leer métricas"
            result={stats}
            onSubmit={() => {
              void refreshStats();
            }}
          >
            <p className="run__note" style={{ textAlign: 'left', margin: 0 }}>
              <code>GET /metrics/fanout</code>. Cada publish suma el job del outbox.
            </p>
          </TryCard>
        </div>
      </section>

      <p className="footnote">
        Lo que S5 <strong>no</strong> resuelve: la cola sigue en el proceso. Tres pods no
        comparten memoria. Rabbit (o SQS) se evalúa después, cuando K4 pregunte si el mismo job
        corre dos veces.
      </p>
    </main>
  );
}

function Experiments() {
  const [outcome, setOutcome] = useState<TxOutcome>('commit');
  const [kind, setKind] = useState<ThrowingKind>('queued-handler');
  const [followers, setFollowers] = useState(1_000);
  const enqueued = outboxEnqueues(outcome);
  const retries = retriesAfterThrow(kind);
  const jobs = queueJobsFor(followers);

  return (
    <section className="section">
      <h2>Los tres experimentos</h2>

      <h3 className="answer__h">1. Rollback</h3>
      <div className="calc-fields">
        <label className="field">
          <span className="field__label">desenlace de la tx</span>
          <select
            className="field__input"
            value={outcome}
            onChange={(event) => setOutcome(event.target.value as TxOutcome)}
          >
            <option value="commit">commit</option>
            <option value="rollback">rollback</option>
          </select>
        </label>
      </div>
      <Versus
        leftTitle="afterCommit"
        leftSub="el handler encolado"
        left={enqueued ? 'se encola' : 'no corre'}
        leftKind={enqueued ? 'guarded' : 'naive'}
        leftLabel={outcome === 'commit' ? 'commit disparó el callback' : 'rollback no llama afterCommit'}
        rightTitle="el fan-out"
        rightSub="FanoutPost"
        right={enqueued ? 'después, en el worker' : 'nunca'}
        rightKind={enqueued ? 'guarded' : 'naive'}
        rightLabel="el efecto no vive en el request"
      />

      <h3 className="answer__h">2. Dedup</h3>
      <Versus
        leftTitle="la interfaz"
        leftSub="EnqueueOptions.deduplicationId"
        left="existe"
        leftKind="guarded"
        leftLabel="QueuedEventConfig no lo expone"
        rightTitle="la cola del lab"
        rightSub="InMemoryMessageQueue"
        right={sameDedupProcessedTwice() ? 'lo ignora' : 'deduplica'}
        rightKind="naive"
        rightLabel="el mismo id se procesa dos veces"
      />

      <h3 className="answer__h">3. Retry — el importante</h3>
      <div className="calc-fields">
        <label className="field">
          <span className="field__label">quién tira</span>
          <select
            className="field__input"
            value={kind}
            onChange={(event) => setKind(event.target.value as ThrowingKind)}
          >
            <option value="queued-handler">event handler encolado</option>
            <option value="job">Job común</option>
          </select>
        </label>
      </div>
      <Versus
        leftTitle="al tirar"
        leftSub={kind === 'queued-handler' ? 'invokeEventHandler' : 'JobProcessor.executeJob'}
        left={kind === 'queued-handler' ? 'traga el error' : 'propaga'}
        leftKind={retries ? 'guarded' : 'naive'}
        leftLabel={kind === 'queued-handler' ? 'log ERROR y listo' : 'onMessage tira'}
        rightTitle="el mensaje"
        rightSub="visibility timeout"
        right={retries ? 'se reentrega' : 'se borra'}
        rightKind={retries ? 'guarded' : 'naive'}
        rightLabel={retries ? 'no delete → retry' : 'delete → no hay retry'}
      />

      <h3 className="answer__h">Cuántos jobs ve la cola</h3>
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
      <Versus
        leftTitle="cadena S2"
        leftSub="FanoutPost + chunks"
        left={jobsFor(followers).toLocaleString('es-AR')}
        leftKind="guarded"
        leftLabel="lo que midió el bench antes"
        rightTitle="la cola ahora"
        rightSub="+ ProcessEventHandlerJob"
        right={jobs.toLocaleString('es-AR')}
        rightKind="naive"
        rightLabel="un job extra por publish"
      />
      <p className="deep" style={{ maxWidth: '68ch', color: 'var(--text-dim)', marginTop: 12 }}>
        1.000 seguidores eran 11 jobs. Ahora son 12. El extra no escribe timelines: recorre el
        handler encolado.
      </p>
    </section>
  );
}

function Versus({
  leftTitle,
  leftSub,
  left,
  leftKind,
  leftLabel,
  rightTitle,
  rightSub,
  right,
  rightKind,
  rightLabel,
}: {
  leftTitle: string;
  leftSub: string;
  left: string;
  leftKind: 'guarded' | 'naive';
  leftLabel: string;
  rightTitle: string;
  rightSub: string;
  right: string;
  rightKind: 'guarded' | 'naive';
  rightLabel: string;
}) {
  return (
    <div className="versus" style={{ marginTop: 16, marginBottom: 28 }}>
      <div className="side" data-kind={leftKind}>
        <div className="side__head">
          <span className="side__title">{leftTitle}</span>
          <span className="side__sub">{leftSub}</span>
        </div>
        <div className="verdict">
          <FlashNumber text={left} />
          <div className="verdict__label">{leftLabel}</div>
        </div>
      </div>
      <div className="versus__rule" aria-hidden="true" />
      <div className="side" data-kind={rightKind}>
        <div className="side__head">
          <span className="side__title">{rightTitle}</span>
          <span className="side__sub">{rightSub}</span>
        </div>
        <div className="verdict">
          <FlashNumber text={right} />
          <div className="verdict__label">{rightLabel}</div>
        </div>
      </div>
    </div>
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
