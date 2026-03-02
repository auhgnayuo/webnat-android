package io.github.auhgnayuo.webnat

/**
 * ConnectionImpl - 连接实现类
 *
 * 实现 Connection 接口，提供连接的具体功能。
 */
internal class ConnectionImpl(
    /** 连接的唯一标识符 */
    override val id: String,
    /** 连接的元数据 */
    override val attributes: Map<String, Any>?,
    /** 消息发送函数，由创建 Connection 时注入，封装了实际的消息发送逻辑 */
    private val sendMessage: (Message) -> Unit
) : Connection {
    /** 连接是否已关闭 */
    override var closed: Boolean = false
    /**
     * 发送消息到 Web 端
     *
     * 如果连接已关闭，会抛出 WebnatError 错误，不会实际发送消息。
     *
     * @param message 要发送的消息
     * @throws WebnatError 如果连接已关闭，抛出 WebnatErrorCode.CLOSED 错误
     *
     * @example
     * ```kotlin
     * try {
     *   val message = Message.raw(Message.NATIVE_UUID, mapOf("data" to "test"))
     *   connection.send(message)
     *   Log.d("Connection", "Message sent successfully")
     * } catch (error: WebnatError) {
     *   if (error.code == WebnatErrorCode.CLOSED) {
     *     Log.e("Connection", "Connection is closed")
     *   }
     * }
     * ```
     */
    fun send(message: Message) {
        if (closed) {
            throw WebnatError.closed()
        }
        sendMessage(message)
    }
    
    /**
     * 尝试发送消息到 Web 端
     *
     * 与 send 方法不同，此方法不会抛出异常，而是静默失败。
     * 适用于在连接可能已关闭的情况下发送消息，如清理资源时。
     *
     * @param message 要发送的消息
     *
     * @example
     * ```kotlin
     * // 在清理资源时使用，不关心是否成功
     * val message = Message.close(connection.id, mapOf("reason" to "cleanup"))
     * connection.trySend(message)
     * ```
     */
    fun trySend(message: Message) {
        try {
            send(message)
        } catch (_: Exception) {
            // 静默忽略错误
        }
    }
}

