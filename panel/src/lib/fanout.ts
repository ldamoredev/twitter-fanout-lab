/** Keep in sync with FanoutTuning.kt. Literals so the served bundle keeps the digits. */
export const FANOUT_CHUNK_FOLLOWERS = Number('100');
export const FANOUT_WORKERS = Number('8');

/** Un job que reparte + uno por cada tanda de seguidores. Sin seguidores no hay trabajo. */
export function jobsFor(followers: number, chunk = FANOUT_CHUNK_FOLLOWERS): number {
  if (!Number.isFinite(followers) || followers < 1) return 1;
  return 1 + Math.ceil(followers / chunk);
}

/** Una escritura de timeline por seguidor: el costo real del fan-out on write. */
export function writesFor(followers: number): number {
  return Number.isFinite(followers) && followers > 0 ? followers : 0;
}
