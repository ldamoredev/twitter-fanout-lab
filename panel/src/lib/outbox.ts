import { jobsFor } from './fanout';

export type TxOutcome = 'commit' | 'rollback';
export type ThrowingKind = 'queued-handler' | 'job';

/** El handler encolado sólo se encola si la tx commitea. Rollback no dispara afterCommit. */
export function outboxEnqueues(outcome: TxOutcome): boolean {
  return outcome === 'commit';
}

/**
 * `EnqueueOptions.deduplicationId` existe; `QueuedEventConfig` no lo expone, y la cola
 * del lab lo ignora. El mismo id se procesa dos veces.
 */
export function sameDedupProcessedTwice(): boolean {
  return true;
}

/**
 * `invokeEventHandler` traga el Throwable: el job se borra. Un `Job` común que tira
 * no se borra y el visibility timeout lo reentrega.
 */
export function retriesAfterThrow(kind: ThrowingKind): boolean {
  return kind === 'job';
}

/** Un `ProcessEventHandlerJob` extra por cada publish, encima de la cadena de S2. */
export function queueJobsFor(followers: number): number {
  return 1 + jobsFor(followers);
}
