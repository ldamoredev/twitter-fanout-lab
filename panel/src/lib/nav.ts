export type NavItem = { href: string; label: string } | { href: null; label: string };

export const NAV: readonly NavItem[] = [
  { href: '/', label: 'portada' },
  { href: '/modelo.html', label: 'modelo' },
  { href: '/fanout.html', label: 'fan-out' },
  { href: '/hibrido.html', label: 'híbrido' },
  { href: null, label: 'lectura' },
  { href: null, label: 'outbox' },
  { href: null, label: 'infra' },
];
