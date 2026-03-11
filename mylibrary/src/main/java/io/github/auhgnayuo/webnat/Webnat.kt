package io.github.auhgnayuo.webnat

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/**
 * Webnat - Native 与 Web 之间的双向通信框架
 *
 * 提供三种通信模式：
 * 1. **Raw（原始消息）**：最基础的消息传递，不做任何额外处理
 * 2. **Broadcast（广播）**：发布-订阅模式，支持事件通知
 * 3. **Method（方法调用）**：RPC 模式，支持远程方法调用和返回值
 *
 * ## 使用示例
 *
 * ```kotlin
 * // 1. 获取 Webnat 实例
 * val webnat = Webnat.of(webView)
 *
 * // 2. 注册方法处理器
 * webnat.onMethod("getUserInfo") { param, callback, notify, connection ->
 *   callback(mapOf("name" to "John"), null)
 *   return@onMethod {}
 * }
 *
 * // 3. 调用 Web 端方法
 * webnat.method("showAlert", mapOf("message" to "Hello")) { result, error ->
 *   println("Result: $result")
 * }
 * ```
 */
class Webnat private constructor(webView: WebView) {
    private val webViewRef = WeakReference(webView)
    
    companion object {
        /**
         * 创建或获取 Webnat 实例
         *
         * 工厂方法，为指定的 WebView 创建或返回已存在的 Webnat 实例。
         * 使用单例模式，确保每个 WebView 只有一个 Webnat 实例。
         *
         * 工作流程：
         * 1. 检查 WebView 是否已有 Webnat 实例（通过 tag）
         * 2. 如果存在，直接返回
         * 3. 如果不存在，创建新实例并注册 JavaScript 接口
         * 4. 将实例保存到 WebView 的 tag 中
         *
         * JavaScript 接口：
         * - 注册 `__native_webnat__` 对象到 WebView
         * - Web 端可以通过 `window.__native_webnat__.postMessage(JSON字符串)` 向 Native 端发送消息
         *
         * @param webView WebView 实例
         * @return Webnat 实例
         */
        private const val TAG_KEY = "io.github.auhgnayuo.webnat.Webnat"
        
        @Suppress("unused")
        fun of(webView: WebView): Webnat {
            val tag = webView.getTag(TAG_KEY.hashCode()) as? Webnat
            if (tag != null) {
                return tag
            }
            
            val webnat = Webnat(webView)
            webView.setTag(TAG_KEY.hashCode(), webnat)
            @SuppressLint("SetJavaScriptEnabled")
            webView.settings.javaScriptEnabled = true
            
            val prefix = "Webnat"
            val webnatUserAgent = "$prefix/$WebnatVersion"
            val existingUserAgent = webView.settings.userAgentString ?: ""
            val components = existingUserAgent.split(" ")
                .filter { it.isNotEmpty() && !it.startsWith(prefix) }
            webView.settings.userAgentString = (components + webnatUserAgent).joinToString(" ")
            
            webView.addJavascriptInterface(webnat, "__native_webnat__")
            
            return webnat
        }
    }
    
    /**
     * 连接字典
     *
     * key: 连接 ID（由 Web 端生成）
     * value: Connection 实例
     */
    private val connections = ConcurrentHashMap<String, Connection>()
    
    /** 原始消息传递器 */
    private val rawWebnat = RawWebnat()
    
    /** 广播消息传递器 */
    private val broadcastWebnat = BroadcastWebnat()
    
    /** 方法调用消息传递器 */
    private val methodWebnat = MethodWebnat()
    
    /** JavaScript 保活管理器 */
    private val javaScriptAliveKeeper = JavaScriptAliveKeeper(200) {
        // 心跳回调：向第一个可用连接发送空消息以保持 JS 活跃
        val connection = connections.values.firstOrNull()
        if (connection != null) {
            rawWebnat.raw(null, connection)
        }
    }
    
    /**
     * 获取所有连接
     *
     * @return 连接字典，key 为连接 ID，value 为 Connection 实例
     */
    @Suppress("unused")
    fun getConnections(): Map<String, Connection> {
        return connections.toMap()
    }
    
    // MARK: - 原始消息 API
    
    /**
     * 注册原始消息监听器
     *
     * 可以注册多个监听器，所有监听器都会收到相同的消息。
     * 如果同一个监听器引用已存在，则先移除旧的再添加新的。
     *
     * @param listener 消息监听器回调
     */
    @Suppress("unused")
    fun onRaw(listener: (Connection, Any?) -> Unit) {
        rawWebnat.on(listener)
    }
    
    /**
     * 移除原始消息监听器
     *
     * 使用引用相等性来匹配监听器，只有完全相同的引用才会被移除。
     *
     * @param listener 要移除的监听器（必须与注册时的函数引用完全相同）
     */
    @Suppress("unused")
    fun offRaw(listener: (Connection, Any?) -> Unit) {
        rawWebnat.off(listener)
    }
    
    /**
     * 发送原始消息到指定连接
     *
     * 将消息包装为 Message 格式后发送到 Web 端。
     *
     * @param raw 消息体，可以是任意可序列化的对象（String、Number、Map、List 等）
     * @param connection 目标连接，如果连接已关闭则不会发送
     */
    @Suppress("unused")
    fun raw(raw: Any?, connection: Connection) {
        rawWebnat.raw(raw, connection)
    }
    
    // MARK: - 广播消息 API
    
    /**
     * 订阅广播消息
     *
     * 注册指定事件名称的监听器，当收到对应事件的广播时触发回调。
     * 可以注册多个监听器监听同一事件，所有监听器都会收到通知。
     *
     * @param name 广播事件名称，用于标识不同的事件类型
     * @param listener 接收到广播时的回调函数
     */
    @Suppress("unused")
    fun onBroadcast(name: String, listener: (Any?, Connection) -> Unit) {
        broadcastWebnat.on(name, listener)
    }
    
    /**
     * 取消订阅广播消息
     *
     * 移除指定事件名称下的特定监听器，使用引用相等性进行匹配。
     *
     * @param name 广播事件名称
     * @param listener 要移除的监听器（必须与注册时的函数引用完全相同）
     */
    @Suppress("unused")
    fun offBroadcast(name: String, listener: (Any?, Connection) -> Unit) {
        broadcastWebnat.off(name, listener)
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
    fun listenBroadcast(name: String): Flow<Pair<Any?, Connection>> {
        return broadcastWebnat.listen(name)
    }

    /**
     * 广播消息
     *
     * 向指定连接或所有连接发送广播消息。如果 `connection` 为 `null`，则向所有当前活跃的连接广播。
     *
     * @param name 广播事件名称，用于标识事件类型
     * @param param 广播参数，可以是任意可序列化的对象，可选
     * @param connection 目标连接，如果为 `null` 则广播到所有连接
     */
    @Suppress("unused")
    fun broadcast(name: String, param: Any? = null, connection: Connection? = null) {
        val c = connection ?: connections.values.firstOrNull()
        broadcastWebnat.broadcast(name, param, c)
    }
    
    // MARK: - 方法调用 API
    
    /**
     * 注册方法处理器
     *
     * 注册方法监听器以响应来自 Web 端的方法调用请求。
     *
     * **重要**：每个方法名称只能有一个处理器，重复注册会覆盖之前的处理器。
     *
     * @param name 方法名称，用于标识要处理的方法
     * @param listener 方法处理器回调（同步或异步函数）
     */
    @Suppress("unused")
    fun onMethod(name: String, listener: MethodListener) {
        methodWebnat.on(name, listener)
    }
    
    /**
     * 移除方法处理器
     *
     * 移除指定方法名称的处理器。使用引用相等性进行匹配。
     *
     * @param name 方法名称
     * @param listener 要移除的方法处理器（必须与注册时的函数引用完全相同）
     */
    @Suppress("unused")
    fun offMethod(name: String, listener: MethodListener) {
        methodWebnat.off(name, listener)
    }
    
    /**
     * 调用 Web 端方法
     *
     * 执行远程方法调用，支持超时和取消。这是一个异步操作，结果通过回调返回。
     *
     * @param method 要调用的方法名称
     * @param param 方法参数，可以是任意可序列化的对象，可选
     * @param timeout 超时时间（毫秒），超时后会自动取消调用并抛出超时错误
     * @param onNotification 收到通知时的回调函数，用于接收方法执行过程中的进度或状态更新
     * @param callback 完成回调，接收方法执行结果或错误
     * @param connection 目标连接，如果为 null 则选择第一个可用连接
     * @return 取消函数，调用此函数可以主动取消正在执行的方法调用
     */
    @Suppress("unused")
    fun method(
        method: String,
        param: Any? = null,
        timeout: Long? = null,
        onNotification: MethodOnNotification? = null,
        callback: MethodCallback? = null,
        connection: Connection? = null
    ): MethodCancellation {
        val c = connection ?: connections.values.firstOrNull()
        // 在方法调用期间增加引用，确保 JS 保持活跃
        javaScriptAliveKeeper.increaseReference()
        return methodWebnat.method(method, param, timeout, onNotification, { result, error ->
            callback?.invoke(result, error)
            // 方法调用完成后减少引用
            javaScriptAliveKeeper.decreaseReference()
        }, c)
    }

    /**
     * 调用 Web 端方法（协程版本）
     *
     * 发起远程方法调用，支持超时和取消。这是一个挂起函数，使用 Kotlin Coroutines。
     *
     * **调用流程**：
     * 1. 生成唯一调用 ID
     * 2. 设置超时定时器（如有传入）
     * 3. 注册完成回调和通知回调
     * 4. 发送调用请求消息
     * 5. 等待结果、超时或被取消
     * 6. 清理回调和定时任务
     *
     * @param method 要调用的方法名称
     * @param param 方法参数，可以是任意可序列化的对象，可选
     * @param timeout 超时时间（毫秒），如果为 `null` 则永不超时。超时后会自动取消调用并抛出超时错误
     * @param onNotification 接收到途中的通知时回调，用于接收方法执行过程中的进度或状态更新
     * @param connection 目标连接，如果为 `null` 则抛出连接关闭错误
     * @return 方法执行结果，可以是任意可序列化的对象，可选
     * @throws WebnatError 方法执行错误，包括：
     *   - 超时错误（`WebnatErrorCode.TIMEOUT`）
     *   - 取消错误（`WebnatErrorCode.CANCELLED`）
     *   - 连接关闭错误（`WebnatErrorCode.CLOSED`）
     *   - 其他执行错误 WebnatError
     * @throws CancellationException 协程被取消时抛出
     */
    @Suppress("unused")
    suspend fun method(
        method: String,
        param: Any? = null,
        timeout: Long? = null,
        onNotification: MethodOnNotification? = null,
        connection: Connection? = null
    ): Any? {
        return methodWebnat.method(method, param, timeout, onNotification, connection)
    }
    /**
     * 通知所有代理：连接已打开
     *
     * 当新连接建立时，通知所有注册的 Webnat 有新连接可用。
     *
     * @param connection 新打开的连接
     */
    private fun onConnectionOpen(connection: Connection) {
        connections[connection.id] = connection
        rawWebnat.onConnectionOpen(connection)
        broadcastWebnat.onConnectionOpen(connection)
        methodWebnat.onConnectionOpen(connection)
    }
    
    /**
     * 通知所有代理：连接已关闭
     *
     * 当连接关闭时，通知所有注册的 Webnat 清理该连接相关的资源。
     *
     * @param connection 已关闭的连接
     */
    private fun onConnectionClose(connection: Connection) {
        connections.remove(connection.id)
        rawWebnat.onConnectionClose(connection)
        broadcastWebnat.onConnectionClose(connection)
        methodWebnat.onConnectionClose(connection)
    }
    
    /**
     * 通知所有代理：接收到消息
     *
     * 当从 Web 端接收到消息时，分发消息到所有注册的 Webnat。
     *
     * @param connection 消息来源连接
     * @param message 接收到的消息
     */
    private fun onConnectionReceive(connection: Connection, message: Message) {
        rawWebnat.onConnectionReceive(connection, message)
        broadcastWebnat.onConnectionReceive(connection, message)
        methodWebnat.onConnectionReceive(connection, message)
    }
    
    /**
     * 接收来自 Web 端的消息
     *
     * 此方法由 Web 端通过 JavaScript 接口调用（通过 `__native_webnat__.postMessage`），
     * 处理来自 Web 端的消息。
     *
     * 消息格式：JSON 字符串，解析后应为 Message 对象
     *
     * 消息类型：
     * - `open`: 连接打开消息，建立新连接
     * - `close`: 连接关闭消息，关闭连接
     * - 其他: 普通消息，转发给各个 Webnat
     *
     * @param messageText JSON 格式的消息字符串
     */
    @JavascriptInterface
    @Suppress("unused")
    fun postMessage(messageText: String) {
        // 推迟下次心跳时间
        javaScriptAliveKeeper.delay()
        val webView = webViewRef.get() ?: return
        // postMessage 由 JS 线程调用，WebView 仅可在主线程访问，故整段逻辑投递到主线程执行
        webView.post {
            try {
                // 解析 JSON 字符串
                val json = JSONObject(messageText)
                val message = Message.from(json) ?: return@post
                val from = message.from

                // 处理连接打开消息
                if (message.open != null) {
                    if (connections.containsKey(from)) return@post

                    val attributes = message.open.param as? Map<*, *>
                    @Suppress("UNCHECKED_CAST")
                    val attributesMap = attributes?.mapKeys { it.key.toString() } as? Map<String, Any>

                    // 主线程中获取当前 URL，保证 WebView.url 访问安全
                    val currentUrl = webView.url
                    val connection = ConnectionImpl(from, attributesMap, currentUrl) { messageToSend ->
                        try {
                            javaScriptAliveKeeper.delay()
                            val jsonArray = JSONArray()
                            jsonArray.put(messageToSend.toJson())
                            val js = "window.__web_webnat__.receive(...${jsonArray.toString()})"
                            // 发送时也必须在主线程执行 evaluateJavascript
                            webViewRef.get()?.post {
                                webViewRef.get()?.evaluateJavascript(js, null)
                            }
                        } catch (_: Exception) { }
                    }
                    onConnectionOpen(connection)
                    return@post
                }

                if (message.close != null) {
                    val connection = connections.remove(from) ?: return@post
                    (connection as? ConnectionImpl)?.closed = true
                    onConnectionClose(connection)
                    return@post
                }

                val connection = connections[from] ?: return@post
                onConnectionReceive(connection, message)
            } catch (_: Exception) { }
        }
    }
    
    // MARK: - 公共辅助方法
    
    /**
     * 获取当前活跃连接数
     *
     * @return 当前活跃连接的数量
     */
    fun getConnectionCount(): Int {
        return connections.size
    }
    
    /**
     * 获取第一个可用连接
     *
     * @return 第一个可用的连接，如果没有连接则返回 `null`
     */
    fun getFirstConnection(): Connection? {
        return connections.values.firstOrNull()
    }
}

