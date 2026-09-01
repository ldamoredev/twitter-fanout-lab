import { describe, expect, it } from 'vitest';
import { formatBytes, slotBytes } from './format';
import {
  FULL_POST_BYTES,
  POST_ID_BYTES,
  PRECOMPUTED_TIMELINE_USERS,
  TIMELINE_WINDOW_POSTS,
} from './timeline-storage';

describe('formatBytes', () => {
  it('expresses 640e9 as 640 GB', () => {
    expect(formatBytes(640_000_000_000)).toBe('640 GB');
  });

  it('expresses 12.8e12 as 12,8 TB', () => {
    expect(formatBytes(12_800_000_000_000)).toBe('12,8 TB');
  });
});

describe('slotBytes', () => {
  it('matches the S1 timeline storage calculation', () => {
    expect(slotBytes(PRECOMPUTED_TIMELINE_USERS, TIMELINE_WINDOW_POSTS, POST_ID_BYTES)).toBe(
      640_000_000_000,
    );
    expect(slotBytes(PRECOMPUTED_TIMELINE_USERS, TIMELINE_WINDOW_POSTS, FULL_POST_BYTES)).toBe(
      12_800_000_000_000,
    );
  });
});
