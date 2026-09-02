/** Keep in sync with PostCache.kt. Literals so the bundle keeps digits. */
export const POST_CACHE_MAXIMUM_SIZE = Number('10000');

export type WhoSees = 'author' | 'both';

/**
 * El autor lee cache + sus posts recientes. No espera a que el fan-out escriba timelines.
 */
export function authorSeesAt(_elapsedMs: number): boolean {
  return true;
}

/**
 * El seguidor lee el timeline precomputado. Ve el post cuando el fan-out terminó de escribirlo.
 */
export function followerSeesAt(elapsedMs: number, fanoutLatencyMs: number): boolean {
  if (!Number.isFinite(elapsedMs) || !Number.isFinite(fanoutLatencyMs)) return false;
  return elapsedMs >= fanoutLatencyMs;
}

export function whoSees(elapsedMs: number, fanoutLatencyMs: number): WhoSees {
  return followerSeesAt(elapsedMs, fanoutLatencyMs) ? 'both' : 'author';
}
