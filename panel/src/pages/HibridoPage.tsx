import { useState, type ReactNode } from 'react';
import { TryCard } from '../components/TryCard';
import { call, type CallResult } from '../lib/api';
import {
  CELEBRITY_MERGE_POSTS,
  CELEBRITY_THRESHOLD_FOLLOWERS,
  isCelebrity,
  mergedIds,
  writesOnPublish,
} from '../lib/hybrid';
import { useFlash } from '../lib/useFlash';

export function HibridoPage() {
  const [lector] = useState(() => crypto.randomUUID());
  const [celebridad] = useState(() => crypto.randomUUID());
  const [followerId, setFollowerId] = useState<string>(lector);
  const [followeeId, setFolloweeId] = useState<string>(celebridad);
  const [authorId, setAuthorId] = useState<string>(celebridad);
  const [text, setText] = useState('esto se entrega al leer');
  const [timelineUser, setTimelineUser] = useState<string>(lector);
  const [follow, setFollow] = useState<CallResult | null>(null);
  const [publish, setPublish] = useState<CallResult | null>(null);
  const [timeline, setTimeline] = useState<CallResult | null>(null);

  return (
    <main className="page">
      <div className="lede">
        <h1>El híbrido</h1>
        <p className="question">¿Qué pasa cuando el que publica tiene 50 millones de seguidores?</p>
        <p>
          Que el fan-out no se hace. Por encima de {CELEBRITY_THRESHOLD_FOLLOWERS.toLocaleString('es-AR')}{' '}
          seguidores publicar no escribe ningún timeline: el post se entrega al leer, mergeando el timeline
          precomputado con lo último de las celebridades que seguís. Los dos caminos de S2 y S1 conviven, y
          cuál te toca depende de a quién sigas.
        </p>
      </div>

      <section className="section">
        <h2>La regla</h2>
        <div className="protocol">
          <Step n="1" tag="write">
            <code>FanoutPost</code> cuenta los seguidores del autor <em>antes</em> de listarlos. Si pasa el
            umbral, no despacha nada y el job termina. Contar primero es la diferencia entre un{' '}
            <code>count</code> y traer 50 millones de ids.
          </Step>
          <Step n="2" tag="read">
            <code>GetTimeline</code> mira a quién seguís, se queda con los que pasan el umbral y les pide
            sus últimos {CELEBRITY_MERGE_POSTS} posts.
          </Step>
          <Step n="3" tag="read">
            Mergea todo y ordena por <code>PostId</code>. Los <code>Id</code> de Trantor son UUIDv7, así
            que el timestamp está en los bits altos y ordenar ids <em>es</em> ordenar por fecha. Hidratar
            el texto es el paso siguiente: <a href="/lectura.html">lectura</a>.
          </Step>
        </div>
        <p className="deep">
          El umbral es <code>CelebrityThreshold</code>, un servicio del contenedor y no una constante
          suelta: <code>TRANTOR__FANOUT__CELEBRITY_THRESHOLD_FOLLOWERS</code> lo mueve sin recompilar, y
          los tests lo bajan a 50.
        </p>
      </section>

      <ThresholdCalculator />

      <section className="section">
        <h2>De dónde sale el número</h2>
        <div className="derived">
          <Row k="un post en el umbral" v="101 jobs · ~140 ms de propagación" />
          <Row k="un post de 1 millón" v="10.001 jobs · el burst que la cola no absorbe" />
          <Row k="cada celebridad seguida" v={`+1 consulta en todas tus lecturas`} />
          <Row k="umbral demasiado bajo" v="todos son celebridades = fan-out on read" />
        </div>
        <p className="run__note" style={{ textAlign: 'left', marginTop: 10 }}>
          El umbral es el punto donde el burst de escritura empieza a doler más que la consulta extra en
          cada lectura. El número correcto no sale de la teoría: sale de medir a qué ritmo drena la cola.
        </p>
      </section>

      <section className="section">
        <h2>Probalo</h2>
        <p className="lede">
          Con el umbral en {CELEBRITY_THRESHOLD_FOLLOWERS.toLocaleString('es-AR')} no vas a poder fabricar
          una celebridad a mano acá: seguir, publicar y leer recorre el camino con fan-out. El camino sin
          fan-out lo prueba <code>HybridHttpTest</code>, que baja el umbral por configuración.
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
      </section>

      <p className="footnote">
        Lo que el híbrido <strong>no</strong> resolvía: el timeline devolvía ids pelados. Hidratar el
        texto, y que el autor vea su propio post antes que el resto, está en{' '}
        <a href="/lectura.html">lectura</a>.
      </p>
    </main>
  );
}

function ThresholdCalculator() {
  const [followers, setFollowers] = useState(1_000);
  const [celebritiesFollowed, setCelebritiesFollowed] = useState(3);
  const celebrity = isCelebrity(followers);
  const writes = writesOnPublish(followers);
  const merged = mergedIds(celebritiesFollowed);

  return (
    <section className="section">
      <h2>Qué camino te toca</h2>
      <div className="calc-fields">
        <NumberField label="seguidores del autor" value={followers} onChange={setFollowers} />
        <NumberField
          label="celebridades que seguís"
          value={celebritiesFollowed}
          onChange={setCelebritiesFollowed}
        />
      </div>
      <div className="versus" style={{ marginTop: 16 }}>
        <div className="side" data-kind={celebrity ? 'naive' : 'guarded'}>
          <div className="side__head">
            <span className="side__title">al publicar</span>
            <span className="side__sub">{celebrity ? 'sin fan-out' : 'con fan-out'}</span>
          </div>
          <div className="verdict">
            <FlashNumber text={writes.toLocaleString('es-AR')} />
            <div className="verdict__label">timelines escritos</div>
          </div>
        </div>
        <div className="versus__rule" aria-hidden="true" />
        <div className="side" data-kind="guarded">
          <div className="side__head">
            <span className="side__title">al leer</span>
            <span className="side__sub">lo que paga cada request</span>
          </div>
          <div className="verdict">
            <FlashNumber text={merged.toLocaleString('es-AR')} />
            <div className="verdict__label">{`${celebritiesFollowed} × ${CELEBRITY_MERGE_POSTS} ids a mergear`}</div>
          </div>
        </div>
      </div>
      <p className="deep" style={{ maxWidth: '68ch', color: 'var(--text-dim)', marginTop: 12 }}>
        El costo no desaparece: se mueve. Subir el umbral hace que más gente pague escrituras al publicar;
        bajarlo hace que más gente pague consultas al leer. Y las lecturas son el camino caliente.
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

function NumberField({
  label,
  value,
  onChange,
}: {
  label: string;
  value: number;
  onChange: (value: number) => void;
}) {
  return (
    <label className="field">
      <span className="field__label">{label}</span>
      <input
        className="field__input"
        type="number"
        min={0}
        step={1}
        value={Number.isFinite(value) ? value : ''}
        onChange={(event) => {
          const next = Number(event.target.value);
          if (!Number.isFinite(next) || next < 0) return;
          onChange(next);
        }}
      />
    </label>
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
