import { describe, expect, it } from 'vitest';
import { authorSeesAt, followerSeesAt, whoSees } from './lectura';

describe('authorSeesAt', () => {
  it('el autor ve su post en t=0, sin esperar al fan-out', () => {
    expect(authorSeesAt(0)).toBe(true);
    expect(authorSeesAt(1_000)).toBe(true);
  });
});

describe('followerSeesAt', () => {
  it('el seguidor no ve el post antes de que el fan-out termine', () => {
    expect(followerSeesAt(13, 14)).toBe(false);
  });

  it('justo cuando termina el fan-out el seguidor ya lo ve', () => {
    expect(followerSeesAt(14, 14)).toBe(true);
    expect(followerSeesAt(20, 14)).toBe(true);
  });
});

describe('whoSees', () => {
  it('antes del fan-out sólo el autor; después, los dos', () => {
    expect(whoSees(0, 14)).toBe('author');
    expect(whoSees(14, 14)).toBe('both');
  });
});
