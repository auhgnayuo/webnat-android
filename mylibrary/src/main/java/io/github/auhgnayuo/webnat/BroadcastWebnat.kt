package io.github.auhgnayuo.webnat

import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * BroadcastWebnat - 广播消息传递器
 *
 * 实现发布-订阅（pub/sub）模式的消息传递机制。
 *
 * **适用场景**：
 * - 事件通知（如状态变更、数据更新等）
 * - 一对多的消息分发
 * - 不需要返回值的通知场景
 *
 * **特点**：
 * - 按事件名称分类管理监听器
 * - 支持多个订阅者同时监听同一事件
 * - 可以向指定连接或所有连接广播
 * - 广播时不关心是否有订阅者的存在
 *
 * **消息格式**：
 * - 使用 `Message` 类，包含 `broadcast` 字段
 * - Message 格式：`{ from: string, to: string, broadcast: { name: string, param?: Any } }`
 *
 * - Note: 这是内部类，不应直接使用，应通过 `Webnat` 类的 API 访问
 */
internal class BroadcastWebnat {
    
    /**
     * 广播事件监听器映射表
     *
     * key: 广播事件名称
     * value: 绑定在该事件名称上的监听器列表
     */
    private val listeners = mutableMapOf<String, MutableList<Listener>>()
    
    /**
     * 注册（订阅）广播消息
     *
     * 注册指定事件名称的监听器，当收到对应事件的广播时触发回调。
     * 若同一监听器对象已被注册，则会先移除后再添加，避免重复订阅。
     *
     * @param name 广播事件名称（字符串标识），用于标识不同的事件类型
     * @param listener 接收到广播时的回调函数，当对应事件被广播时会被调用
     */
    fun on(name: String, listener: (Any?, Connection) -> Unit) {
        // 如果该事件名称还没有监听器，创建新的监听器列表
        if (!listeners.containsKey(name)) {
            listeners[name] = mutableListOf()
        }
        
        // 移除已存在的相同监听器（如果有的话，使用引用判等）
        listeners[name]?.removeAll { it.value === listener }
        
        // 添加新的监听器
        listeners[name]?.add(Listener(listener))
    }
    
    /**
     * 取消订阅广播消息
     *
     * 将指定事件名称下的特定监听器移除，使用引用相等性（===）进行匹配。
     *
     * @param name 广播事件名称
     * @param listener 要移除的监听器（必须与注册时的引用完全相同）
     */
    fun off(name: String, listener: (Any?, Connection) -> Unit) {
        // 移除已存在的相同监听器（如果有的话）
        listeners[name]?.removeAll { it.value === listener }
    }
    
    /**
     * 订阅广播 Flow（Kotlin Coroutines）
     *
     * 通过 Flow 方式订阅广播事件。
     * 当对应事件被广播时，新的 `Pair<Any?, Connection>` 会发送到流中。
     * 流取消时，会自动注销相关监听器，避免内存泄漏。
     *
     * @param name 广播事件名称
     * @return 监听广播事件的 Flow
     */
    @Suppress("unused")
    fun listen(name: String): Flow<Pair<Any?, Connection>> = callbackFlow {
        // 如果该事件名称还没有监听器，创建新的监听器列表
        if (!listeners.containsKey(name)) {
            listeners[name] = mutableListOf()
        }
        
        // 将 ProducerScope 包装为 Listener 后注册
        val listener = Listener(this)
        listeners[name]?.add(listener)
        
        // 当流被取消或终止时，自动注销监听器
        awaitClose {
            listeners[name]?.remove(listener)
        }
    }
    
    /**
     * 广播消息推送
     *
     * 将消息广播给指定连接或所有连接。
     * 若 `connection` 为 `null`，则向所有当前活跃连接广播。
     * 使用 `Message` 类构造消息并发送。
     *
     * @param name 广播事件名称，用于标识事件类型
     * @param param 广播参数，可以是任意可序列化的对象，可选。若无参数，则消息不携带 `param` 字段
     * @param connection 目标连接（单个），若为 `null` 则不发送
     */
    fun broadcast(name: String, param: Any? = null, connection: Connection?) {
        // 定义广播操作——使用 Message 类构造消息后通过 Connection.send 发送
        connection ?: return
        
        val message = Message.broadcast(connection.id, name, param)
        (connection as? ConnectionImpl)?.send(message)
    }
    
    /**
     * 连接打开（建立）时的回调
     *
     * 会将新连接添加到内部活跃连接列表，后续可向其广播。
     *
     * @param connection 新打开的连接对象（Connection 实例）
     */
    @Suppress("unused")
    fun onConnectionOpen(connection: Connection) {
        // 广播消息传递器不需要维护连接列表
    }
    
    /**
     * 连接关闭时的回调
     *
     * 会将已关闭的连接对象从活跃连接列表中移除，避免后续继续广播到其上。
     *
     * @param connection 已关闭的连接对象（Connection 实例）
     */
    @Suppress("unused")
    fun onConnectionClose(connection: Connection) {
        // 广播消息传递器不需要维护连接列表
    }
    
    /**
     * 接收到广播消息时的回调
     *
     * 用于分发 Web 或 Native 侧收到的广播消息。
     * 按事件名称查找并依次回调所有已订阅的监听器（支持回调函数和 Flow）。
     *
     * @param connection 消息来源连接
     * @param message 收到的消息（已解析的 `Message` 对象）
     */
    fun onConnectionReceive(connection: Connection, message: Message) {
        // 检查是否为 broadcast 消息
        val broadcast = message.broadcast ?: return
        
        // 根据事件名称分发触发所有相关的监听器（callback 或 Flow）
        val listenersCopy = listeners[broadcast.name]?.toList() ?: return
        for (listener in listenersCopy) {
            when (val value = listener.value) {
                is Function2<*, *, *> -> {
                    // 处理回调函数类型
                    @Suppress("UNCHECKED_CAST")
                    val callback = value as? ((Any?, Connection) -> Unit)
                    callback?.invoke(broadcast.param, connection)
                }
                is ProducerScope<*> -> {
                    // 处理 Flow 类型
                    @Suppress("UNCHECKED_CAST")
                    val producer = value as? ProducerScope<Pair<Any?, Connection>>
                    producer?.trySend(Pair(broadcast.param, connection))
                }
            }
        }
    }
}

