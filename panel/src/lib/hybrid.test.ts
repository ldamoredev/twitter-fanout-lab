import { describe, expect, it } from 'vitest';
import {
  CELEBRITY_MERGE_POSTS,
  CELEBRITY_THRESHOLD_FOLLOWERS,
  isCelebrity,
  mergedIds,
  writesOnPublish,
} from './hybrid';

describe('isCelebrity', () => {
  it('con el umbral en 50, 49 no es celebridad y 51 sí', () => {
    expect(isCelebrity(49, 50)).toBe(false);
    expect(isCelebrity(51, 50)).toBe(true);
  });

  it('justo en el umbral todavía hay fan-out', () => {
    expect(isCelebrity(50, 50)).toBe(false);
    expect(isCelebrity(CELEBRITY_THRESHOLD_FOLLOWERS)).toBe(false);
  });
});

describe('writesOnPublish', () => {
  it('el fan-out paga una escritura por seguidor', () => {
    expect(writesOnPublish(1_000)).toBe(1_000);
  });

  it('una celebridad publica sin escribir un solo timeline', () => {
    expect(writesOnPublish(CELEBRITY_THRESHOLD_FOLLOWERS + 1)).toBe(0);
    expect(writesOnPublish(50_000_000)).toBe(0);
  });
});

describe('mergedIds', () => {
  it('cada celebridad seguida agrega su ventana de posts a cada lectura', () => {
    expect(mergedIds(3)).toBe(3 * CELEBRITY_MERGE_POSTS);
    expect(mergedIds(0)).toBe(0);
  });
});
