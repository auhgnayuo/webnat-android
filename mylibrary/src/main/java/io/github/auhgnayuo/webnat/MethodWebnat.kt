package io.github.auhgnayuo.webnat

import android.os.Handler
import android.os.Looper
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 方法调用完成回调类型定义
 *
 * 用于接收方法调用执行结果的回调类型。
 * - result: 方法执行结果，可以是任意可序列化的对象，成功时传入，失败时为 `null`
 * - error: 错误信息，失败时传入，成功时为 `null`
 */
typealias MethodCallback = (result: Any?, error: Exception?) -> Unit

/**
 * 方法调用取消函数类型定义
 *
 * 调用此函数可以取消正在执行的方法调用。
 */
typealias MethodCancellation = () -> Unit

/**
 * 方法调用通知类型定义
 *
 * 用于接收方法执行过程中的通知（如进度、中间结果等）。
 * - param: 方法执行途中的通知内容，可以是任意可序列化的对象，可选
 */
typealias MethodOnNotification = (param: Any?) -> Unit

/**
 * 方法监听器类型定义
 *
 * 用于处理来自 Web 端的方法调用请求。
 * - param: 方法调用时传入的参数，可以是任意可序列化的对象，可选
 * - callback: 方法执行完成后的回调，用于返回结果或错误
 * - notify: 通知回调，用于在方法执行过程中发送进度或状态更新
 * - connection: 调用来源的连接对象
 * - returns: 取消函数，用于在方法执行过程中取消操作
 */
typealias MethodListener = (param: Any?, callback: MethodCallback, notify: MethodOnNotification, connection: Connection) -> MethodCancellation

/**
 * MethodWebnat - 方法调用消息传递器
 *
 * 实现请求-响应模式的远程方法调用（RPC）机制。
 *
 * 适用场景：
 * - 需要获取返回值的方法调用
 * - 异步操作（如文件读取、网络请求等）
 * - 需要超时控制的场景
 * - 需要主动取消的长时间操作
 *
 * 特点：
 * - 支持双向方法调用（Native 调用 Web 和 Web 调用 Native）
 * - 基于回调的异步调用
 * - 支持超时控制
 * - 支持主动取消正在执行的方法
 * - 自动错误传递
 * - 每个调用有唯一 ID，支持并发多个调用
 *
 * 消息格式：使用 Message 类，包含 invoke、reply、notify、abort 字段
 * - 调用请求：`{ from: string, to: string, invoke: { id: string, method: string, param?: Any } }`
 * - 调用结果：`{ from: string, to: string, reply: { id: string, result?: Any, error?: Any } }`
 * - 调用通知：`{ from: string, to: string, notify: { id: string, param?: Any } }`
 * - 取消请求：`{ from: string, to: string, abort: { id: string } }`
 */
internal class MethodWebnat {
    
    /** 主线程 Handler，用于在主线程执行操作 */
    private val mainHandler = Handler(Looper.getMainLooper())
    
    /**
     * 方法监听器映射表
     * key: 方法名称（String）
     * value: 方法处理器 Listener
     * 为每个已注册的方法名称维护一个对应的处理器，用于处理 Web 端的方法调用请求
     */
    private val listeners = ConcurrentHashMap<String, Listener>()
    
    /**
     * 方法调用途中的通知（比如进度）
     * key: 调用 ID
     * value: 用于响应通知的回调函数（如进度、步骤变更等）
     */
    private val onNotifications = ConcurrentHashMap<String, MethodOnNotification>()
    
    /**
     * 方法调用完成回调映射表
     * key: 调用 ID（字符串，为本地发起或接收调用的唯一标识）
     * value: 完成回调函数，收到结果或错误时调用
     */
    private val onCompletes = ConcurrentHashMap<String, MethodCallback>()
    
    /**
     * 连接关闭时的清理回调映射表
     * key: 连接 ID
     * value: 该连接上所有待完成调用的清理回调，嵌套 Map key 为调用 ID，value 为清理回调
     */
    private val onCloses = ConcurrentHashMap<String, ConcurrentHashMap<String, () -> Unit>>()
    
    /**
     * 方法调用取消函数映射表
     * key: 调用 ID（这里是被调用方接收到的 ID）
     * value: 取消函数，收到取消请求时执行
     */
    private val aborts = ConcurrentHashMap<String, () -> Unit>()
    
    /**
     * 注册方法处理器
     *
     * 注册方法监听器以响应来自 Web 端的方法调用请求。
     *
     * **重要**：每个方法名称只能有一个处理器，重复注册会覆盖之前的处理器。
     *
     * @param name 方法名称，用于标识要处理的方法
     * @param listener 方法处理器，当收到对应方法的调用请求时会被调用
     */
    fun on(name: String, listener: MethodListener) {
        listeners[name] = Listener(listener)
    }
    
    /**
     * 移除方法处理器
     *
     * 从监听器映射表中移除指定名称和处理器的组合。
     *
     * @param name 方法名称
     * @param listener 方法处理器
     */
    fun off(name: String, listener: MethodListener) {
        val l = listeners[name]
        if (l != null && l.value === listener) {
            listeners.remove(name)
        }
    }
    
    /**
     * 调用 Web 端方法
     *
     * 发起远程方法调用，支持超时和取消。这是一个异步操作，结果通过回调返回。
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
     * @param timeout 超时时间（毫秒），如果为 `null` 则永不超时。超时后会自动取消调用并返回超时错误
     * @param onNotification 接收到途中的通知时回调，用于接收方法执行过程中的进度或状态更新
     * @param callback 完成回调，接收方法执行结果或错误
     * @param connection 目标连接，如果为 `null` 则报错
     * @return 取消函数，调用此函数可以主动取消正在执行的方法调用
     */
    fun method(
        method: String,
        param: Any? = null,
        timeout: Long? = null,
        onNotification: MethodOnNotification? = null,
        callback: MethodCallback? = null,
        connection: Connection? = null
    ): MethodCancellation {
        if (connection == null) {
            callback?.invoke(null, WebnatError.closed())
            return {}
        }
        
        // 生成唯一调用 ID
        val id = UUID.randomUUID().toString()
        
        // 用于取消超时定时的 Runnable
        val timeoutRunnable = Runnable {
            val onComplete = onCompletes[id]
            if (onComplete != null) {
                // 触发超时回调
                onComplete(null, WebnatError.timeout())
                // 发送取消请求
                val message = Message.abort(connection.id, id)
                (connection as? ConnectionImpl)?.trySend(message)
            }
        }
        
        // 设置超时定时器
        if (timeout != null && timeout > 0) {
            mainHandler.postDelayed(timeoutRunnable, timeout)
        }
        
        val cancelTimeout = {
            mainHandler.removeCallbacks(timeoutRunnable)
        }
        
        // 注册连接关闭回调
        val cancelClose = run {
            if (!onCloses.containsKey(connection.id)) {
                onCloses[connection.id] = ConcurrentHashMap()
            }
            onCloses[connection.id]!![id] = {
                // 连接关闭时，返回错误
                onCompletes[id]?.invoke(null, WebnatError.closed())
            }
            
            {
                onCloses[connection.id]?.remove(id)
                if (onCloses[connection.id]?.isEmpty() == true) {
                    onCloses.remove(connection.id)
                }
            }
        }
        
        // 注册通知回调
        val cancelOnNotification = run {
            if (onNotification != null) {
                onNotifications[id] = onNotification
            }
            
            {
                onNotifications.remove(id)
            }
        }
        
        // 注册完成回调（收到结果或错误时）
        onCompletes[id] = { result, error ->
            // 清理资源
            onCompletes.remove(id)
            cancelOnNotification()
            cancelClose()
            cancelTimeout()
            // 触发用户回调
            callback?.invoke(result, error)
        }
        
        // 发送调用请求
        val message = Message.invoke(connection.id, id, method, param)
        
        try {
            (connection as? ConnectionImpl)?.send(message)
        } catch (error: Exception) {
            // 如果发送失败，返回错误
            onCompletes[id]?.invoke(null, error)
        }
        
        // 返回取消函数，支持主动取消本次 method 操作
        return {
            val onComplete = onCompletes[id]
            if (onComplete != null) {
                // 发送取消请求
                val abortMessage = Message.abort(connection.id, id)
                (connection as? ConnectionImpl)?.trySend(abortMessage)
                // 触发取消回调
                onComplete(null, WebnatError.cancelled())
            }
        }
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
    ): Any? = suspendCancellableCoroutine { continuation ->
        // 调用回调版本的 method 方法
        val cancel = this.method(
            method = method,
            param = param,
            timeout = timeout,
            onNotification = onNotification,
            callback = { result, error ->
                if (error != null) {
                    // 有错误，通过异常恢复
                    continuation.resumeWithException(error)
                } else {
                    // 成功，返回结果
                    continuation.resume(result)
                }
            },
            connection = connection
        )
        
        // 当协程被取消时，主动调用取消逻辑
        continuation.invokeOnCancellation {
            cancel.invoke()
        }
    }
    
    /**
     * 连接打开时的回调
     *
     * 将新连接添加到连接列表中，后续可向其发送方法调用请求。
     *
     * @param connection 新打开的连接对象（Connection 实例）
     */
    @Suppress("unused")
    fun onConnectionOpen(connection: Connection) {
        // 方法调用传递器不需要维护连接列表
    }
    
    /**
     * 连接关闭时的回调
     *
     * 触发该连接上所有待完成调用的清理回调（返回连接关闭错误），并从连接列表中移除。
     *
     * @param connection 已关闭的连接对象（Connection 实例）
     */
    fun onConnectionClose(connection: Connection) {
        // 触发该连接上所有待完成调用的清理回调（如超时、主动取消、断开等，统一清理）
        val closes = onCloses[connection.id]
        // 执行所有该连接上的清理回调
        closes?.values?.forEach { onClose ->
            onClose()
        }
    }
    
    /**
     * 接收到消息时的回调
     *
     * 处理四种类型的方法消息：
     * 1. `reply`: 方法调用结果（收到我们发起的调用响应）
     * 2. `invoke`: 方法调用请求（Web 端请求 Native 方法）
     * 3. `abort`: 取消方法调用
     * 4. `notify`: 方法途中的通知（如进度/信息）
     *
     * @param connection 消息来源连接
     * @param message 接收到的消息（已解析的 `Message` 对象）
     */
    fun onConnectionReceive(connection: Connection, message: Message) {
        // 处理 reply 消息
        message.reply?.let { reply ->
            val error = reply.error
            if (error != null) {
                // 有错误，触发完成回调并传递错误
                onCompletes[reply.id]?.invoke(null, WebnatError.from(error))
            } else {
                // 成功，触发完成回调并传递结果
                onCompletes[reply.id]?.invoke(reply.result, null)
            }
            return
        }
        
        // 处理 notify 消息
        message.notify?.let { notify ->
            onNotifications[notify.id]?.invoke(notify.param)
            return
        }
        
        // 处理 abort 消息
        message.abort?.let { abort ->
            aborts[abort.id]?.invoke()
            return
        }
        
        // 处理 invoke 消息
        message.invoke?.let { invoke ->
            val id = invoke.id
            val method = invoke.method
            val param = invoke.param
            
            // 检查方法是否已注册
            val listener = listeners[method]
            if (listener == null) {
                // 方法未实现，返回错误
                val error = WebnatError.unimplemented(method)
                val replyMessage = Message.reply(connection.id, id, error = error.toJson())
                (connection as? ConnectionImpl)?.trySend(replyMessage)
                return
            }
            
            var isCompleted = false
            
            // 清理资源（取消所有与本次调用绑定的回调和状态）
            val clean = {
                // 清理资源
                aborts.remove(id)
                onCloses[connection.id]?.remove(id)
                if (onCloses[connection.id]?.isEmpty() == true) {
                    onCloses.remove(connection.id)
                }
            }
            
            // 向调用方主动推送途中的通知（如进度等）
            val notify: MethodOnNotification = { notifyParam ->
                if (!isCompleted) {
                    val notifyMessage = Message.notify(connection.id, id, notifyParam)
                    (connection as? ConnectionImpl)?.trySend(notifyMessage)
                }
            }
            
            // 完成回调：向调用方发送最终的执行结果或错误
            val complete: MethodCallback = { result, error ->
                if (!isCompleted) {
                    isCompleted = true
                    clean()
                    val replyMessage = if (error != null) {
                        // 有错误，发送错误响应
                        val errorJson = (error as? WebnatError)?.toJson() ?: WebnatError.from(error)?.toJson()
                        Message.reply(connection.id, id, error = errorJson)
                    } else {
                        // 成功，发送结果响应
                        Message.reply(connection.id, id, result = result)
                    }
                    (connection as? ConnectionImpl)?.trySend(replyMessage)
                }
            }
            
            @Suppress("UNCHECKED_CAST")
            val methodListener = listener.value as? MethodListener
            if (methodListener != null) {
                // 调用方法监听器，返回取消此次调用的 cancel 函数
                val abort = methodListener(param, complete, notify, connection)
                
                // 注册取消函数（收到取消请求时会触发此函数，进行清理和调用用户的 abort 逻辑）
                aborts[id] = {
                    if (!isCompleted) {
                        isCompleted = true
                        clean()
                        abort()
                    }
                }
                
                // 注册连接关闭回调（连接关闭时即视为调用被终止，需要清理与主动取消）
                if (!onCloses.containsKey(connection.id)) {
                    onCloses[connection.id] = ConcurrentHashMap()
                }
                onCloses[connection.id]!![id] = {
                    if (!isCompleted) {
                        isCompleted = true
                        clean()
                        abort()
                    }
                }
            }
        }
    }
}

