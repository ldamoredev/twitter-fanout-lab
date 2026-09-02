package lab.fanout.platform.tx

import dev.botta.trantor.core.tx.Transaction

/**
 * `NullTransaction.afterCommit` no hace nada y `NullTransactionManager.activeTransaction`
 * es siempre null, aunque llames `beginTransaction`. Sin esto, `afterCommit = true` en un
 * event handler nunca espera: Trantor sólo trae el null o JDBC.
 */
class InMemoryTransaction(private val onClose: () -> Unit): Transaction {
    private val afterCommitActions = mutableListOf<() -> Unit>()
    private val afterRollbackActions = mutableListOf<() -> Unit>()
    override var isClosed = false
        private set

    override fun commit() {
        if (isClosed) return
        try {
            drain(afterCommitActions)
        } finally {
            setClosed()
        }
    }

    override fun rollback() {
        if (isClosed) return
        try {
            drain(afterRollbackActions)
        } finally {
            setClosed()
        }
    }

    /**
     * `DefaultJobDispatcher.dispatch` con `jobs.afterCommit=true` registra *otro* afterCommit
     * mientras corre el del event handler. `JdbcTransaction` itera con `forEach` y no drena
     * (o tira CME). Acá el índice sigue creciendo para que el job del outbox sí se encole.
     */
    private fun drain(actions: List<() -> Unit>) {
        var i = 0
        while (i < actions.size) {
            actions[i]()
            i++
        }
    }

    override fun afterComplete(action: () -> Unit) {
        afterCommit(action)
        afterRollback(action)
    }

    override fun afterCommit(action: () -> Unit) {
        if (isClosed) error("Cannot add callback to a closed transaction")
        afterCommitActions.add(action)
    }

    override fun afterRollback(action: () -> Unit) {
        if (isClosed) error("Cannot add callback to a closed transaction")
        afterRollbackActions.add(action)
    }

    override fun close() {
        if (isClosed) return
        rollback()
    }

    private fun setClosed() {
        isClosed = true
        onClose()
    }
}
