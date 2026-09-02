import { describe, expect, it } from 'vitest';
import { FANOUT_CHUNK_FOLLOWERS, jobsFor, writesFor } from './fanout';

describe('jobsFor', () => {
  it('cuenta el job que reparte más uno por tanda', () => {
    expect(jobsFor(1_000)).toBe(11);
    expect(jobsFor(FANOUT_CHUNK_FOLLOWERS)).toBe(2);
    expect(jobsFor(FANOUT_CHUNK_FOLLOWERS + 1)).toBe(3);
  });

  it('sin seguidores sigue habiendo un job: el que descubre que no hay a quién escribirle', () => {
    expect(jobsFor(0)).toBe(1);
  });
});

describe('writesFor', () => {
  it('es una escritura por seguidor', () => {
    expect(writesFor(1_000)).toBe(1_000);
    expect(writesFor(0)).toBe(0);
  });
});
