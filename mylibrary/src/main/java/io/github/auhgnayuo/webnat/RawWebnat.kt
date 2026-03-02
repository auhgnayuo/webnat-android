package io.github.auhgnayuo.webnat

/**
 * RawWebnat - 原始消息传递器
 *
 * 提供最基础的消息发送和接收功能，不做任何额外处理。
 *
 * **适用场景**：
 * - 简单的数据传输
 * - 不需要消息分类的场景
 * - 自定义消息格式的场景
 *
 * **消息格式**：
 * - 使用 `Message` 类，包含 `raw` 字段
 * - Message 格式：`{ from: string, to: string, raw: { param?: Any } }`
 *
 * **特点**：
 * - 支持多个监听器同时监听
 * - 所有监听器都会收到相同的消息
 * - 消息体可以是任意可序列化的对象
 *
 * - Note: 这是内部类，不应直接使用，应通过 `Webnat` 类的 API 访问
 */
internal class RawWebnat {
    
    /**
     * 注册的监听器列表
     *
     * 保存所有已注册的原始消息监听器，当收到原始消息时会依次调用所有监听器。
     */
    private val listeners = mutableListOf<Listener>()
    
    /**
     * 注册原始消息监听器
     *
     * 可以注册多个监听器，所有监听器都会收到相同的消息。
     * 如果同一个监听器引用已存在，则先移除旧的再添加新的，避免重复。
     *
     * @param listener 消息监听回调，当收到原始消息时会被调用
     */
    fun on(listener: (Connection, Any?) -> Unit) {
        // 移除已存在的相同监听器（如果有的话）
        listeners.removeAll { it.value === listener }
        
        // 添加新的监听器
        listeners.add(Listener(listener))
    }
    
    /**
     * 移除原始消息监听器
     *
     * 使用引用相等性（===）来匹配监听器，只有完全相同的引用才会被移除。
     *
     * @param listener 要移除的监听器（必须与注册时的引用完全相同）
     */
    fun off(listener: (Connection, Any?) -> Unit) {
        listeners.removeAll { it.value === listener }
    }
    
    /**
     * 发送原始消息到指定连接
     *
     * 使用 `Message` 类构造消息并发送到 Web 端。
     *
     * @param param 消息体，可以是任意可序列化的对象（String、Int、Map、List 等）
     * @param connection 目标连接，如果连接已关闭则不会发送
     */
    fun raw(param: Any?, connection: Connection) {
        val message = Message.raw(connection.id, param)
        (connection as? ConnectionImpl)?.send(message)
    }
    
    /**
     * 连接打开时的回调
     *
     * 将新连接添加到连接列表中，后续可向其发送消息。
     *
     * @param connection 新打开的连接对象（Connection 实例）
     */
    @Suppress("unused")
    fun onConnectionOpen(connection: Connection) {
        // 原始消息传递器不需要维护连接列表
    }
    
    /**
     * 连接关闭时的回调
     *
     * 从连接列表中移除已关闭的连接，避免后续继续向其发送消息。
     *
     * @param connection 已关闭的连接对象（Connection 实例）
     */
    @Suppress("unused")
    fun onConnectionClose(connection: Connection) {
        // 原始消息传递器不需要维护连接列表
    }
    
    /**
     * 接收到消息时的回调
     *
     * 当收到原始消息时，分发给所有注册的监听器。
     *
     * @param connection 消息来源连接
     * @param message 接收到的消息（已解析的 `Message` 对象）
     */
    fun onConnectionReceive(connection: Connection, message: Message) {
        // 检查是否为 raw 消息
        val raw = message.raw ?: return
        
        // 分发给所有监听器
        val listenersCopy = listeners.toList()
        for (listener in listenersCopy) {
            @Suppress("UNCHECKED_CAST")
            val callback = listener.value as? ((Connection, Any?) -> Unit)
            callback?.invoke(connection, raw.param)
        }
    }
}

