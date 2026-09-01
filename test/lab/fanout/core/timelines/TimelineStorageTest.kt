package lab.fanout.core.timelines

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TimelineStorageTest {
    @Test
    fun `cincuenta millones de timelines de ids pesan 640 GB y con el post completo 12 punto 8 TB`() {
        val idBytes = PRECOMPUTED_TIMELINE_USERS * TIMELINE_WINDOW_POSTS * POST_ID_BYTES
        val fullPostBytes = PRECOMPUTED_TIMELINE_USERS * TIMELINE_WINDOW_POSTS * FULL_POST_BYTES

        assertThat(idBytes).isEqualTo(640_000_000_000L)
        assertThat(fullPostBytes).isEqualTo(12_800_000_000_000L)
    }
}
