import { describe, expect, it } from 'vitest';
import { jobsFor } from './fanout';
import { outboxEnqueues, queueJobsFor, retriesAfterThrow, sameDedupProcessedTwice } from './outbox';

describe('outboxEnqueues', () => {
  it('el commit encola el handler y el rollback no', () => {
    expect(outboxEnqueues('commit')).toBe(true);
    expect(outboxEnqueues('rollback')).toBe(false);
  });
});

describe('sameDedupProcessedTwice', () => {
  it('el mismo deduplicationId se procesa dos veces', () => {
    expect(sameDedupProcessedTwice()).toBe(true);
  });
});

describe('retriesAfterThrow', () => {
  it('el handler encolado no reintenta; el Job común sí', () => {
    expect(retriesAfterThrow('queued-handler')).toBe(false);
    expect(retriesAfterThrow('job')).toBe(true);
  });
});

describe('queueJobsFor', () => {
  it('suma el job del outbox a la cadena del fan-out', () => {
    expect(queueJobsFor(1_000)).toBe(1 + jobsFor(1_000));
    expect(queueJobsFor(0)).toBe(2);
  });
});
