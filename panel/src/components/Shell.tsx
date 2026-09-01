import { useEffect, useState, type ReactNode } from 'react';
import { NAV } from '../lib/nav';

type Depth = 'breve' | 'detallado';

function readDepth(): Depth {
  const saved = localStorage.getItem('lab-depth');
  return saved === 'breve' || saved === 'detallado' ? saved : 'detallado';
}

export function Shell({ current, children }: { current: string; children: ReactNode }) {
  const [depth, setDepth] = useState<Depth>(readDepth);

  useEffect(() => {
    document.body.dataset.depth = depth;
    localStorage.setItem('lab-depth', depth);
  }, [depth]);

  useEffect(() => {
    if (!matchMedia('(prefers-reduced-motion: reduce)').matches) {
      document.documentElement.classList.add('motion-ok');
    }
  }, []);

  return (
    <>
      <header className="top">
        <a className="top__brand" href="/">
          <span className="top__mark" aria-hidden="true" />
          <span className="top__brand-text">
            <b>twitter-fanout-lab</b>
            <span>el costo se paga en la escritura o en la lectura</span>
          </span>
        </a>
        <nav className="top__nav" aria-label="secciones">
          {NAV.map((item) =>
            item.href == null ? (
              <span key={item.label} aria-disabled="true">
                {item.label}
              </span>
            ) : (
              <a
                key={item.href}
                href={item.href}
                aria-current={item.href === current ? 'page' : undefined}
              >
                {item.label}
              </a>
            ),
          )}
        </nav>
        <div className="top__right">
          <div className="dial" role="group" aria-label="profundidad de lectura">
            <button
              type="button"
              aria-pressed={depth === 'breve'}
              onClick={() => setDepth('breve')}
            >
              breve
            </button>
            <button
              type="button"
              aria-pressed={depth === 'detallado'}
              onClick={() => setDepth('detallado')}
            >
              detallado
            </button>
          </div>
        </div>
      </header>
      {children}
    </>
  );
}
