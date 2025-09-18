package chat.model

import kotlinx.serialization.Serializable
@Serializable
data class ChatMessage(
    val id: String,
    val from: String,
    val text: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val imageBase64: String? = null
)

@Serializable
sealed class Envelope {
    @Serializable data class Hello(val name: String): Envelope()
    @Serializable data class Msg(val message: ChatMessage): Envelope()
    @Serializable data class Bye(val reason: String? = null): Envelope()
}
