/** Keep in sync with CelebrityThreshold.kt / FanoutTuning.kt. Literals so the bundle keeps digits. */
export const CELEBRITY_THRESHOLD_FOLLOWERS = Number('10000');
export const CELEBRITY_MERGE_POSTS = Number('50');

/** Celebridad es estar por encima del umbral: justo en el número todavía hay fan-out. */
export function isCelebrity(followers: number, threshold = CELEBRITY_THRESHOLD_FOLLOWERS): boolean {
  if (!Number.isFinite(followers)) return false;
  return followers > threshold;
}

/** Escrituras que cuesta publicar: todas si hay fan-out, ninguna si el autor es celebridad. */
export function writesOnPublish(followers: number, threshold = CELEBRITY_THRESHOLD_FOLLOWERS): number {
  if (!Number.isFinite(followers) || followers < 1) return 0;
  return isCelebrity(followers, threshold) ? 0 : followers;
}

/** Consultas extra que cuesta cada lectura: una por celebridad seguida. */
export function readsOnTimeline(celebritiesFollowed: number): number {
  if (!Number.isFinite(celebritiesFollowed) || celebritiesFollowed < 1) return 0;
  return celebritiesFollowed;
}

/** Ids que hay que mergear al leer: el precomputado más lo que se trae de cada celebridad. */
export function mergedIds(celebritiesFollowed: number, perCelebrity = CELEBRITY_MERGE_POSTS): number {
  return readsOnTimeline(celebritiesFollowed) * perCelebrity;
}
