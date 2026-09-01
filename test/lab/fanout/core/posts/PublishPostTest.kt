package lab.fanout.core.posts

import dev.botta.cqbus.CQBus
import dev.botta.trantor.domain.errors.ArgumentCannotBeEmptyError
import lab.fanout.core.identity.UserId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class PublishPostTest {
    @Test
    fun `un post publicado se guarda y se lee por id`() {
        val posts = InMemoryPosts()
        val bus = CQBus()
        bus.registerHandler { PublishPost.Handler(posts) }
        bus.registerHandler { GetPost.Handler(posts) }
        val authorId = UserId()

        val published = bus.execute(PublishPost(authorId, "hola lab"))
        val loaded = bus.execute(GetPost(published.postId))

        assertThat(loaded.id).isEqualTo(published.postId)
        assertThat(loaded.authorId).isEqualTo(authorId)
        assertThat(loaded.text).isEqualTo("hola lab")
    }

    @Test
    fun `un post con texto vacio no puede existir`() {
        assertThatThrownBy { Post(authorId = UserId(), text = "  ") }
            .isInstanceOf(ArgumentCannotBeEmptyError::class.java)
    }

    @Test
    fun `un post con mas caracteres que el maximo no puede existir`() {
        val tooLong = "x".repeat(MAX_POST_TEXT_CHARS + 1)
        assertThatThrownBy { Post(authorId = UserId(), text = tooLong) }
            .isInstanceOf(PostTextTooLong::class.java)
    }
}
