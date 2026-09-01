import { useState } from 'react';
import { Calculator } from '../components/Calculator';
import { TryCard } from '../components/TryCard';
import { call, type CallResult } from '../lib/api';

export function ModeloPage() {
  const [alice] = useState(() => crypto.randomUUID());
  const [bob] = useState(() => crypto.randomUUID());
  const [authorId, setAuthorId] = useState<string>(bob);
  const [text, setText] = useState('hola lab');
  const [followerId, setFollowerId] = useState<string>(alice);
  const [followeeId, setFolloweeId] = useState<string>(bob);
  const [timelineUser, setTimelineUser] = useState<string>(alice);
  const [publish, setPublish] = useState<CallResult | null>(null);
  const [follow, setFollow] = useState<CallResult | null>(null);
  const [timeline, setTimeline] = useState<CallResult | null>(null);

  return (
    <main className="page">
      <div className="lede">
        <h1>El modelo</h1>
        <p className="question">El timeline precomputado guarda IDs, no posts.</p>
        <p>
          Mover los números de abajo <strong>es</strong> el aprendizaje de S1. Los defaults son las
          constantes de <code>TimelineStorage.kt</code>: 50.000.000 usuarios, ventana de 800, 16 bytes por
          ID, 320 por post completo.
        </p>
      </div>

      <Calculator />

      <section className="section">
        <h2>Por qué IDs</h2>
        <div className="lede">
          <p>
            El fan-out copia <em>algo</em> al timeline de cada seguidor. Si ese algo es el post entero, 50
            millones de home timelines × 800 entradas son 12,8 TB. Si es un UUID de 16 bytes, 640 GB. Veinte
            veces menos. El cuerpo vive una sola vez; hidratarlo es otro slice.
          </p>
          <p className="deep">
            Publicar todavía <strong>no escribe</strong> el timeline de nadie. Los botones de abajo hablan
            con la API real: un post se guarda y se lee por id, un follow queda registrado, y{' '}
            <code>GET /timelines/{'{userId}'}</code> devuelve <code>{'{"postIds":[]}'}</code> hasta S2.
          </p>
        </div>
      </section>

      <section className="section">
        <h2>La API, ahora</h2>
        <div className="try-row">
          <TryCard
            title="publicar un post"
            submitLabel="publicar un post"
            result={publish}
            onSubmit={() => {
              void call('/posts', {
                method: 'POST',
                body: { authorId, text },
              }).then(setPublish);
            }}
          >
            <Field label="authorId" value={authorId} onChange={setAuthorId} />
            <Field label="text" value={text} onChange={setText} spellCheck />
          </TryCard>

          <TryCard
            title="seguir a alguien"
            submitLabel="seguir a alguien"
            result={follow}
            onSubmit={() => {
              void call('/follows', {
                method: 'POST',
                body: { followerId, followeeId },
              }).then(setFollow);
            }}
          >
            <Field label="followerId" value={followerId} onChange={setFollowerId} />
            <Field label="followeeId" value={followeeId} onChange={setFolloweeId} />
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
        Alice está precargada como follower y dueña del timeline; Bob como autor y followee. Seguí a Bob,
        publicá como Bob, pedí el timeline de Alice: sale vacío. El modelo ya existe; el fan-out no.
      </p>
    </main>
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
