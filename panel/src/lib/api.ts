export type CallResult = {
  ok: boolean;
  status: number;
  payload: unknown;
  raw: string;
  ms: number;
};

/**
 * S6: acá entra el round-robin entre réplicas. Hoy hay una sola, el origen es esta página.
 */
function replicaOrigin(): string {
  return '';
}

export async function call(path: string, options: { method?: string; body?: unknown } = {}): Promise<CallResult> {
  const started = performance.now();

  try {
    const response = await fetch(replicaOrigin() + path, {
      method: options.method ?? 'GET',
      headers: options.body ? { 'content-type': 'application/json' } : undefined,
      body: options.body ? JSON.stringify(options.body) : undefined,
    });

    const raw = await response.text();
    let payload: unknown = null;
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
