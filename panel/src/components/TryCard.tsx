import { type ReactNode } from 'react';
import type { CallResult } from '../lib/api';
import { useFlash } from '../lib/useFlash';

export function TryCard({
  title,
  result,
  submitLabel,
  onSubmit,
  children,
}: {
  title: string;
  result: CallResult | null;
  submitLabel: string;
  onSubmit: () => void;
  children: ReactNode;
}) {
  const tone = result == null ? undefined : result.ok ? 'ok' : 'bad';
  const status = result == null ? '' : `${result.status} · ${result.ms} ms`;
  const body = result == null ? 'hacé clic — acá va el JSON y el tiempo' : result.raw || '(sin cuerpo)';

  return (
    <div className="try-card" data-tone={tone}>
      <div className="try-card__head">
        <span className="side__title">{title}</span>
        <FlashSpan className="try-card__ms" text={status} />
      </div>
      {children}
      <button className="btn btn--primary" type="button" onClick={onSubmit}>
        {submitLabel}
      </button>
      <FlashPre className="code" text={body} />
    </div>
  );
}

function FlashSpan({ text, className }: { text: string; className: string }) {
  const ref = useFlash<HTMLSpanElement>(text, { skip: text === '' });
  return (
    <span ref={ref} className={className}>
      {text}
    </span>
  );
}

function FlashPre({ text, className }: { text: string; className: string }) {
  const ref = useFlash<HTMLPreElement>(text);
  return (
    <pre ref={ref} className={className}>
      {text}
    </pre>
  );
}
