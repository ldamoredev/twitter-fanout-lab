export function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes < 0) return '—';
  if (bytes >= 1e12) {
    return `${(bytes / 1e12).toLocaleString('es-AR', {
      minimumFractionDigits: 1,
      maximumFractionDigits: 1,
    })} TB`;
  }
  if (bytes >= 1e9) {
    return `${(bytes / 1e9).toLocaleString('es-AR', { maximumFractionDigits: 0 })} GB`;
  }
  if (bytes >= 1e6) {
    return `${(bytes / 1e6).toLocaleString('es-AR', { maximumFractionDigits: 1 })} MB`;
  }
  return `${bytes.toLocaleString('es-AR')} B`;
}

export function slotBytes(users: number, windowSize: number, bytesPerSlot: number): number {
  return users * windowSize * bytesPerSlot;
}
