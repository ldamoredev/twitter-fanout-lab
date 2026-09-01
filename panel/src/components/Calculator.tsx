import { useState } from 'react';
import { formatBytes, slotBytes } from '../lib/format';
import {
  FULL_POST_BYTES,
  POST_ID_BYTES,
  PRECOMPUTED_TIMELINE_USERS,
  TIMELINE_WINDOW_POSTS,
} from '../lib/timeline-storage';
import { useFlash } from '../lib/useFlash';

export function Calculator() {
  const [users, setUsers] = useState(PRECOMPUTED_TIMELINE_USERS);
  const [windowSize, setWindowSize] = useState(TIMELINE_WINDOW_POSTS);
  const [idBytes, setIdBytes] = useState(POST_ID_BYTES);
  const [postBytes, setPostBytes] = useState(FULL_POST_BYTES);

  const ids = slotBytes(users, windowSize, idBytes);
  const full = slotBytes(users, windowSize, postBytes);
  const ratio = ids === 0 ? 0 : full / ids;

  return (
    <section className="section" id="calculadora">
      <h2>Cuánto pesa el timeline</h2>
      <div className="calc-fields">
        <NumberField label="usuarios" value={users} onChange={setUsers} />
        <NumberField label="ventana de posts" value={windowSize} onChange={setWindowSize} />
        <NumberField label="bytes por ID" value={idBytes} onChange={setIdBytes} />
        <NumberField label="bytes por post completo" value={postBytes} onChange={setPostBytes} />
      </div>
      <div className="versus" style={{ marginTop: 16 }}>
        <div className="side" data-kind="guarded">
          <div className="side__head">
            <span className="side__title">sólo IDs</span>
            <span className="side__sub">lo que guarda el timeline</span>
          </div>
          <div className="verdict">
            <FlashNumber text={formatBytes(ids)} />
            <div className="verdict__label">{`${ids.toLocaleString('es-AR')} bytes`}</div>
          </div>
        </div>
        <div className="versus__rule" aria-hidden="true" />
        <div className="side" data-kind="naive">
          <div className="side__head">
            <span className="side__title">post completo</span>
            <span className="side__sub">si cada slot llevara el post</span>
          </div>
          <div className="verdict">
            <FlashNumber text={formatBytes(full)} />
            <div className="verdict__label">{`${full.toLocaleString('es-AR')} bytes`}</div>
          </div>
        </div>
      </div>
      <p className="run__note" style={{ textAlign: 'left', marginTop: 10 }}>
        {Number.isFinite(ratio)
          ? `el post completo pesa ${ratio.toLocaleString('es-AR', { maximumFractionDigits: 1 })}× los IDs`
          : ''}
      </p>
      <p className="deep" style={{ maxWidth: '68ch', color: 'var(--text-dim)', marginTop: 12 }}>
        320 bytes es un registro estimado: 16 (<code>PostId</code>) + 16 (<code>UserId</code>) + 280 (
        <code>MAX_POST_TEXT_CHARS</code>) + 8 (<code>createdAt</code> en millis). El test del slice fija 640
        GB contra 12,8 TB con estos defaults.
      </p>
    </section>
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
        min={1}
        step={1}
        value={Number.isFinite(value) ? value : ''}
        onChange={(event) => {
          const next = Number(event.target.value);
          if (!Number.isFinite(next) || next < 1) return;
          onChange(next);
        }}
      />
    </label>
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
