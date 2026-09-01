import { useEffect, useRef } from 'react';

export function useFlash<T extends HTMLElement>(
  text: string,
  options: { skip?: boolean; skipFirst?: boolean } = {},
) {
  const ref = useRef<T>(null);
  const primed = useRef(false);

  useEffect(() => {
    const el = ref.current;
    if (!el || options.skip) return;
    if (options.skipFirst && !primed.current) {
      primed.current = true;
      return;
    }
    primed.current = true;
    el.classList.remove('is-updating');
    void el.offsetWidth;
    el.classList.add('is-updating');
  }, [options.skip, options.skipFirst, text]);

  return ref;
}
