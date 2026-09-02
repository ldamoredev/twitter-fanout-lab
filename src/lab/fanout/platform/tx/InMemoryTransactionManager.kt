package lab.fanout.platform.tx

import dev.botta.trantor.core.tx.Transaction
import dev.botta.trantor.core.tx.TransactionManager

class InMemoryTransactionManager: TransactionManager {
    private val current = ThreadLocal<InMemoryTransaction?>()

    override val activeTransaction: Transaction? get() = current.get()

    override fun beginTransaction(): Transaction {
        if (current.get() != null) error("Ya hay una transacción activa")
        val tx = InMemoryTransaction { current.remove() }
        current.set(tx)
        return tx
    }
}
