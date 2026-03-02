package io.github.auhgnayuo.webnat

import org.json.JSONObject

/**
 * 连接打开消息类
 * 
 * 用于建立连接时的初始化消息，可以携带初始化参数。
 */
data class Open(
    /** 可选的初始化参数，可以是任意可序列化的对象 */
    val param: Any? = null
)

/**
 * 连接关闭消息类
 * 
 * 用于关闭连接时的消息，可以携带关闭原因等参数。
 */
data class Close(
    /** 可选的关闭参数（如关闭原因等），可以是任意可序列化的对象 */
    val param: Any? = null
)

/**
 * 原始消息类
 * 
 * 用于发送任意原始数据，不经过任何特殊处理。
 */
data class Raw(
    /** 原始消息的参数数据，可以是任意可序列化的对象 */
    val param: Any? = null
)

/**
 * 广播消息类
 * 
 * 用于向所有订阅者发送事件通知，支持事件名称和参数。
 */
data class Broadcast(
    /** 广播事件名称，用于标识不同的事件类型 */
    val name: String,
    /** 广播事件的参数数据，可以是任意可序列化的对象，可选 */
    val param: Any? = null
)

/**
 * 方法调用消息类
 * 
 * 用于远程方法调用（RPC），包含调用 ID、方法名和参数。
 */
data class Invoke(
    /** 调用 ID，用于匹配请求和响应，唯一标识一次方法调用 */
    val id: String,
    /** 要调用的方法名称 */
    val method: String,
    /** 方法调用的参数，可以是任意可序列化的对象，可选 */
    val param: Any? = null
)

/**
 * 方法调用响应消息类
 * 
 * 用于返回方法调用的结果或错误，包含调用 ID、结果或错误信息。
 */
data class Reply(
    /** 调用 ID，用于匹配对应的请求 */
    val id: String,
    /** 方法调用的成功结果，可以是任意可序列化的对象（与 `error` 互斥） */
    val result: Any? = null,
    /** 方法调用的错误信息，可以是任意可序列化的对象（与 `result` 互斥） */
    val error: Any? = null
)

/**
 * 通知消息类
 * 
 * 用于在方法调用执行过程中向调用方发送进度通知或中间结果。
 */
data class Notify(
    /** 调用 ID，用于匹配对应的请求 */
    val id: String,
    /** 通知的参数数据，可以是任意可序列化的对象（如进度信息、中间结果等），可选 */
    val param: Any? = null
)

/**
 * 中止消息类
 * 
 * 用于取消正在执行的方法调用。
 */
data class Abort(
    /** 调用 ID，用于匹配要取消的请求 */
    val id: String
)

/**
 * 消息类
 * 
 * 所有消息的统一格式，包含发送方、接收方和具体的消息类型。
 * 消息类型是互斥的，一条消息只能包含一种类型的消息体。
 *
 * 支持的消息类型：
 * - `open`: 连接打开消息
 * - `close`: 连接关闭消息
 * - `raw`: 原始消息
 * - `broadcast`: 广播消息
 * - `invoke`: 方法调用消息
 * - `reply`: 方法调用响应消息
 * - `notify`: 通知消息
 * - `abort`: 中止消息
 */
data class Message(
    /** 消息魔数，用于验证消息格式 */
    val magic: String = MAGIC,
    /** 消息发送方的标识（连接 ID 或 `NATIVE_UUID`） */
    val from: String,
    /** 消息接收方的标识（连接 ID 或 `NATIVE_UUID`） */
    val to: String,
    /** 连接打开消息（与其他消息类型互斥） */
    val open: Open? = null,
    /** 连接关闭消息（与其他消息类型互斥） */
    val close: Close? = null,
    /** 原始消息（与其他消息类型互斥） */
    val raw: Raw? = null,
    /** 广播消息（与其他消息类型互斥） */
    val broadcast: Broadcast? = null,
    /** 方法调用消息（与其他消息类型互斥） */
    val invoke: Invoke? = null,
    /** 方法调用响应消息（与其他消息类型互斥） */
    val reply: Reply? = null,
    /** 通知消息（与其他消息类型互斥） */
    val notify: Notify? = null,
    /** 中止消息（与其他消息类型互斥） */
    val abort: Abort? = null
) {
    companion object {
        /**
         * Native 端的 UUID 标识符常量
         *
         * 用于标识消息来自 Native 端，值为 `"00000000-0000-0000-0000-000000000000"`。
         */
        const val NATIVE_UUID = "00000000-0000-0000-0000-000000000000"
        
        /**
         * 消息魔数常量
         *
         * 用于验证消息格式，值为 `"WEBNAT"`。
         */
        const val MAGIC = "WEBNAT"
        
        // ==================== 连接管理 ====================
        
        /**
         * 创建连接打开消息
         * 
         * @param from 发送方标识
         * @param param 可选的初始化参数
         * @return Message 实例
         */
        @Suppress("unused")
        fun open(from: String, param: Any? = null) = Message(
            from = from,
            to = NATIVE_UUID,
            open = Open(param)
        )
        
        /**
         * 创建连接关闭消息
         * 
         * @param from 发送方标识
         * @param param 可选的关闭参数
         * @return Message 实例
         */
        @Suppress("unused")
        fun close(from: String, param: Any? = null) = Message(
            from = from,
            to = NATIVE_UUID,
            close = Close(param)
        )
        
        // ==================== 基础消息 ====================
        /**
         * 创建 Native 发送的原始消息
         *
         * @param to 接收方标识（连接 ID）
         * @param param 原始消息的参数数据
         * @return Message 实例
         */
        fun raw(to: String, param: Any? = null) = Message(
            from = NATIVE_UUID,
            to = to,
            raw = Raw(param)
        )

        /**
         * 创建 Native 发送的广播消息
         *
         * @param to 接收方标识（连接 ID）
         * @param name 广播事件名称
         * @param param 广播事件的参数数据
         * @return Message 实例
         */
        fun broadcast(to: String, name: String, param: Any? = null) = Message(
            from = NATIVE_UUID,
            to = to,
            broadcast = Broadcast(name, param)
        )
        
        /**
         * 创建 Native 发送的方法调用消息
         * 
         * @param to 接收方标识（连接 ID）
         * @param id 调用 ID
         * @param method 方法名称
         * @param param 方法参数
         * @return Message 实例
         */
        fun invoke(to: String, id: String, method: String, param: Any? = null) = Message(
            from = NATIVE_UUID,
            to = to,
            invoke = Invoke(id, method, param)
        )
        
        /**
         * 创建 Native 发送的方法调用响应消息
         * 
         * @param to 接收方标识（连接 ID）
         * @param id 调用 ID
         * @param result 成功结果（与 error 互斥）
         * @param error 错误信息（与 result 互斥）
         * @return Message 实例
         */
        fun reply(to: String, id: String, result: Any? = null, error: Any? = null) = Message(
            from = NATIVE_UUID,
            to = to,
            reply = Reply(id, result, error)
        )
        
        /**
         * 创建 Native 发送的通知消息
         * 
         * @param to 接收方标识（连接 ID）
         * @param id 调用 ID
         * @param param 通知的参数数据
         * @return Message 实例
         */
        fun notify(to: String, id: String, param: Any? = null) = Message(
            from = NATIVE_UUID,
            to = to,
            notify = Notify(id, param)
        )
        
        /**
         * 创建 Native 发送的中止消息
         * 
         * @param to 接收方标识（连接 ID）
         * @param id 调用 ID
         * @return Message 实例
         */
        fun abort(to: String, id: String) = Message(
            from = NATIVE_UUID,
            to = to,
            abort = Abort(id)
        )
        
        // ==================== 序列化 ====================
        
        /**
         * 从 JSONObject 创建消息实例
         *
         * 从 JSONObject 格式（通常来自 JSON 反序列化）创建 `Message` 对象。
         * 如果 JSONObject 格式无效（缺少必需字段、魔数不匹配等），则返回 `null`。
         *
         * @param json JSONObject 格式的消息，通常来自 JSON 反序列化
         * @return `Message` 实例，如果 JSONObject 格式无效则返回 `null`
         */
        fun from(json: JSONObject): Message? {
            try {
                val magic = json.optString("magic", "")
                if (magic != MAGIC) {
                    return null
                }
                
                val from = json.optString("from", "") 
                val to = json.optString("to", "")
                if (from.isEmpty() || to.isEmpty()) {
                    return null
                }
                
                var open: Open? = null
                if (json.has("open")) {
                    val openJson = json.getJSONObject("open")
                    open = Open(openJson.opt("param"))
                }
                
                var close: Close? = null
                if (json.has("close")) {
                    val closeJson = json.getJSONObject("close")
                    close = Close(closeJson.opt("param"))
                }
                
                var raw: Raw? = null
                if (json.has("raw")) {
                    val rawJson = json.getJSONObject("raw")
                    raw = Raw(rawJson.opt("param"))
                }
                
                var broadcast: Broadcast? = null
                if (json.has("broadcast")) {
                    val broadcastJson = json.getJSONObject("broadcast")
                    val name = broadcastJson.optString("name", "")
                    if (name.isNotEmpty()) {
                        broadcast = Broadcast(name, broadcastJson.opt("param"))
                    }
                }
                
                var invoke: Invoke? = null
                if (json.has("invoke")) {
                    val invokeJson = json.getJSONObject("invoke")
                    val id = invokeJson.optString("id", "")
                    val method = invokeJson.optString("method", "")
                    if (id.isNotEmpty() && method.isNotEmpty()) {
                        invoke = Invoke(id, method, invokeJson.opt("param"))
                    }
                }
                
                var reply: Reply? = null
                if (json.has("reply")) {
                    val replyJson = json.getJSONObject("reply")
                    val id = replyJson.optString("id", "")
                    if (id.isNotEmpty()) {
                        reply = Reply(
                            id,
                            replyJson.opt("result"),
                            replyJson.opt("error")
                        )
                    }
                }
                
                var notify: Notify? = null
                if (json.has("notify")) {
                    val notifyJson = json.getJSONObject("notify")
                    val id = notifyJson.optString("id", "")
                    if (id.isNotEmpty()) {
                        notify = Notify(id, notifyJson.opt("param"))
                    }
                }
                
                var abort: Abort? = null
                if (json.has("abort")) {
                    val abortJson = json.getJSONObject("abort")
                    val id = abortJson.optString("id", "")
                    if (id.isNotEmpty()) {
                        abort = Abort(id)
                    }
                }
                
                return Message(
                    magic = magic,
                    from = from,
                    to = to,
                    open = open,
                    close = close,
                    raw = raw,
                    broadcast = broadcast,
                    invoke = invoke,
                    reply = reply,
                    notify = notify,
                    abort = abort
                )
            } catch (_: Exception) {
                return null
            }
        }
    }
    
    /**
     * 将消息转换为 JSONObject 格式（用于发送）
     *
     * 将 `Message` 对象转换为 JSONObject 格式，便于序列化为 JSON 并发送到 Web 端。
     *
     * @return JSONObject 格式的消息，可以直接用于 JSON 序列化
     */
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("magic", magic)
        json.put("from", from)
        json.put("to", to)
        
        open?.let {
            val openJson = JSONObject()
            it.param?.let { param -> openJson.put("param", param) }
            json.put("open", openJson)
        }
        
        close?.let {
            val closeJson = JSONObject()
            it.param?.let { param -> closeJson.put("param", param) }
            json.put("close", closeJson)
        }
        
        raw?.let {
            val rawJson = JSONObject()
            it.param?.let { param -> rawJson.put("param", param) }
            json.put("raw", rawJson)
        }
        
        broadcast?.let {
            val broadcastJson = JSONObject()
            broadcastJson.put("name", it.name)
            it.param?.let { param -> broadcastJson.put("param", param) }
            json.put("broadcast", broadcastJson)
        }
        
        invoke?.let {
            val invokeJson = JSONObject()
            invokeJson.put("id", it.id)
            invokeJson.put("method", it.method)
            it.param?.let { param -> invokeJson.put("param", param) }
            json.put("invoke", invokeJson)
        }
        
        reply?.let {
            val replyJson = JSONObject()
            replyJson.put("id", it.id)
            it.result?.let { result -> replyJson.put("result", result) }
            it.error?.let { error -> replyJson.put("error", error) }
            json.put("reply", replyJson)
        }
        
        notify?.let {
            val notifyJson = JSONObject()
            notifyJson.put("id", it.id)
            it.param?.let { param -> notifyJson.put("param", param) }
            json.put("notify", notifyJson)
        }
        
        abort?.let {
            val abortJson = JSONObject()
            abortJson.put("id", it.id)
            json.put("abort", abortJson)
        }
        
        return json
    }
}

