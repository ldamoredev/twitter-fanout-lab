/* Utilidades compartidas del panel.
 *
 * Sin framework y sin build step: el proyecto se explica leyendo el código, y una capa
 * de herramientas sería una capa más que explicar. */

export const uuid = () => crypto.randomUUID();
export const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
export const $ = (sel, root = document) => root.querySelector(sel);
export const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

/**
 * S6: acá entra el round-robin entre réplicas. Hoy hay una sola, el origen es esta página.
 */
function replicaOrigin() {
  return '';
}

export async function call(path, options = {}) {
  const started = performance.now();

  try {
    const response = await fetch(replicaOrigin() + path, {
      method: options.method ?? 'GET',
      headers: options.body
        ? { 'content-type': 'application/json', ...options.headers }
        : options.headers,
      body: options.body ? JSON.stringify(options.body) : undefined,
    });

    const raw = await response.text();
    let payload = null;
    if (raw) {
      try {
        payload = JSON.parse(raw);
      } catch {
        payload = raw;
      }
    }

    return {
      ok: response.ok,
      status: response.status,
      payload,
      raw,
      ms: Math.round(performance.now() - started),
    };
  } catch (error) {
    return {
      ok: false,
      status: 0,
      payload: { message: String(error) },
      raw: String(error),
      ms: Math.round(performance.now() - started),
    };
  }
}

const NAV = [
  { href: '/', label: 'portada', on: true },
  { href: '/modelo.html', label: 'modelo', on: true },
  { href: null, label: 'fan-out', on: false },
  { href: null, label: 'híbrido', on: false },
  { href: null, label: 'lectura', on: false },
  { href: null, label: 'outbox', on: false },
  { href: null, label: 'infra', on: false },
];

export function mountTop({ current }) {
  const host = $('#top');
  if (!host) return;

  host.innerHTML = `
    <a class="top__brand" href="/">
      <b>twitter-fanout-lab</b>
      <span>el costo se paga en la escritura o en la lectura</span>
    </a>
    <nav class="top__nav" aria-label="secciones">
      ${NAV.map((item) => {
        if (!item.on) {
          return `<span aria-disabled="true">${item.label}</span>`;
        }
        const currentAttr = item.href === current ? ' aria-current="page"' : '';
        return `<a href="${item.href}"${currentAttr}>${item.label}</a>`;
      }).join('')}
    </nav>
    <div class="top__right">
      <div class="dial" role="group" aria-label="profundidad de lectura">
        <button type="button" data-depth="breve" aria-pressed="false">breve</button>
        <button type="button" data-depth="detallado" aria-pressed="true">detallado</button>
      </div>
    </div>`;

  mountDepthDial();
}

/**
 * Dial de profundidad: oculta los párrafos .deep para releer rápido, o los muestra
 * para explicar despacio. El body arranca en data-depth="detallado".
 */
function mountDepthDial() {
  const saved = localStorage.getItem('lab-depth') ?? 'detallado';
  apply(saved);

  $$('.dial button').forEach((button) => {
    button.addEventListener('click', () => apply(button.dataset.depth));
  });

  function apply(depth) {
    document.body.dataset.depth = depth;
    localStorage.setItem('lab-depth', depth);
    $$('.dial button').forEach((b) =>
      b.setAttribute('aria-pressed', String(b.dataset.depth === depth)),
    );
  }
}
