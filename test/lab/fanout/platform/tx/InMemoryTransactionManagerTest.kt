package lab.fanout.platform.tx

import dev.botta.trantor.core.tx.transactional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class InMemoryTransactionManagerTest {
    @Test
    fun `afterCommit corre solo si la transaccion commitea`() {
        val tm = InMemoryTransactionManager()
        val seen = mutableListOf<String>()

        tm.transactional {
            tm.activeTransaction!!.afterCommit { seen += "commit" }
            tm.activeTransaction!!.afterRollback { seen += "rollback" }
        }

        assertThat(seen).containsExactly("commit")
    }

    @Test
    fun `afterCommit no corre si la transaccion hace rollback`() {
        val tm = InMemoryTransactionManager()
        val seen = mutableListOf<String>()

        try {
            tm.transactional {
                tm.activeTransaction!!.afterCommit { seen += "commit" }
                tm.activeTransaction!!.afterRollback { seen += "rollback" }
                error("rollback a propósito")
            }
        } catch (e: IllegalStateException) {
            assertThat(e.message).isEqualTo("rollback a propósito")
        }

        assertThat(seen).containsExactly("rollback")
    }

    @Test
    fun `un afterCommit que registra otro afterCommit tambien corre`() {
        val tm = InMemoryTransactionManager()
        val seen = mutableListOf<String>()

        tm.transactional {
            tm.activeTransaction!!.afterCommit {
                seen += "primero"
                tm.activeTransaction!!.afterCommit { seen += "anidado" }
            }
        }

        assertThat(seen).containsExactly("primero", "anidado")
    }
}
