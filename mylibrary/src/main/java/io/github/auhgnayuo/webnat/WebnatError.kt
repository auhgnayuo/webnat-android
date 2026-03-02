package io.github.auhgnayuo.webnat

import org.json.JSONObject

/**
 * Webnat 错误码定义
 * 
 * 定义 Webnat 内置错误码。
 * 负数表示系统级、通用错误码，保证与业务自定义 code 不冲突。
 */
object WebnatErrorCode {
    /**
     * 未知错误
     * 
     * 无法识别或分类的错误
     */
    const val UNKNOWN = -1
    
    /**
     * 请求已取消
     * 
     * 当用户主动取消某个操作（如 RPC 方法调用）时触发
     */
    const val CANCELLED = -999
    
    /**
     * 请求超时
     * 
     * 当操作或调用超时（如 RPC 方法调用超过指定超时时间）时触发
     */
    const val TIMEOUT = -1001
    
    /**
     * 连接已关闭
     * 
     * 当尝试向已关闭的连接发送消息时触发
     */
    const val CLOSED = -1004
    
    /**
     * 方法未实现
     * 
     * 当调用的方法在对端（Web/Native）未注册处理器时触发
     */
    const val UNIMPLEMENTED = -1010
    
    /**
     * 消息反序列化失败
     * 
     * 当接收到的消息无法解析为可用对象时触发
     */
    const val DESERIALIZATION_FAILED = -1011
    
    /**
     * 消息序列化失败
     * 
     * 当消息对象无法转换为 JSON 格式时触发
     */
    const val SERIALIZATION_FAILED = -1012
}

/**
 * Webnat 标准错误类
 * 
 * 表示 Webnat 协议内的标准错误对象，包含错误码和错误消息。
 * 继承自 Exception 类，以便可以在 throw 语句中使用。
 *
 * @property code 错误代码
 * @property message 错误消息
 *
 * @example
 * ```kotlin
 * try {
 *   // 某些操作
 *   throw WebnatError.timeout()
 * } catch (error: WebnatError) {
 *   Log.e("WebnatError", "Error ${error.code}: ${error.message}")
 * }
 * ```
 */
class WebnatError(
    /** 错误代码 */
    val code: Int,
    /** 错误消息 */
    override val message: String
) : Exception(message) {
    
    companion object {
        /**
         * 从任意对象反序列化为 WebnatError
         *
         * 此方法用于将从 JavaScript 端接收到的错误对象转换为 WebnatError
         *
         * 支持的输入格式：
         * 1. 对象格式：{ "code": number, "message": string } 或其它系统常见字段命名（区分大小写，不同风格均支持）
         * 2. 其他类型：将对象描述作为错误消息
         * 
         * @param error 任意异常对象
         * @return WebnatError 实例，如果 error 为 null 则返回 null
         */
        fun from(error: Any?): WebnatError? {
            if (error == null) {
                return null
            }
            
            if (error is WebnatError) {
                return error
            }
            
            if (error is JSONObject) {
                val code = when {
                    error.has("code") -> error.optInt("code", WebnatErrorCode.UNKNOWN)
                    error.has("errcode") -> error.optInt("errcode", WebnatErrorCode.UNKNOWN)
                    error.has("errCode") -> error.optInt("errCode", WebnatErrorCode.UNKNOWN)
                    error.has("errorcode") -> error.optInt("errorcode", WebnatErrorCode.UNKNOWN)
                    error.has("errorCode") -> error.optInt("errorCode", WebnatErrorCode.UNKNOWN)
                    else -> WebnatErrorCode.UNKNOWN
                }
                
                val message = when {
                    error.has("message") -> error.optString("message", "Unknown Error")
                    error.has("msg") -> error.optString("msg", "Unknown Error")
                    error.has("errmsg") -> error.optString("errmsg", "Unknown Error")
                    error.has("errMsg") -> error.optString("errMsg", "Unknown Error")
                    error.has("errormsg") -> error.optString("errormsg", "Unknown Error")
                    error.has("errorMsg") -> error.optString("errorMsg", "Unknown Error")
                    error.has("errormessage") -> error.optString("errormessage", "Unknown Error")
                    error.has("errorMessage") -> error.optString("errorMessage", "Unknown Error")
                    else -> "Unknown Error"
                }
                
                return WebnatError(code, message)
            }
            
            return WebnatError(WebnatErrorCode.UNKNOWN, error.toString())
        }
        
        /**
         * 构造未知错误
         * 
         * @param obj 可选，额外描述
         * @return WebnatError 实例
         */
        @Suppress("unused")
        fun unknown(obj: Any? = null): WebnatError {
            val message = if (obj != null) "Unknown Error: $obj" else "Unknown Error"
            return WebnatError(WebnatErrorCode.UNKNOWN, message)
        }
        
        /**
         * 构造操作取消错误
         * 
         * @return WebnatError 实例
         */
        fun cancelled(): WebnatError {
            return WebnatError(WebnatErrorCode.CANCELLED, "Operation Cancelled")
        }
        
        /**
         * 构造超时错误
         * 
         * @return WebnatError 实例
         */
        fun timeout(): WebnatError {
            return WebnatError(WebnatErrorCode.TIMEOUT, "Operation Timeout")
        }
        
        /**
         * 构造连接关闭错误
         * 
         * @return WebnatError 实例
         */
        fun closed(): WebnatError {
            return WebnatError(WebnatErrorCode.CLOSED, "Connection Closed")
        }
        
        /**
         * 构造未实现错误
         * 
         * @param obj 可选，未实现的方法描述
         * @return WebnatError 实例
         */
        fun unimplemented(obj: Any? = null): WebnatError {
            val message = if (obj != null) "Unimplemented Method: $obj" else "Unimplemented Method"
            return WebnatError(WebnatErrorCode.UNIMPLEMENTED, message)
        }
        
        /**
         * 构造反序列化失败错误
         * 
         * @param obj 可选，额外描述
         * @return WebnatError 实例
         */
        @Suppress("unused")
        fun deserializationFailed(obj: Any? = null): WebnatError {
            val message = if (obj != null) "Message Deserialization Failed: $obj" else "Message Deserialization Failed"
            return WebnatError(WebnatErrorCode.DESERIALIZATION_FAILED, message)
        }
        
        /**
         * 构造序列化失败错误
         * 
         * @param obj 可选，额外描述
         * @return WebnatError 实例
         */
        @Suppress("unused")
        fun serializationFailed(obj: Any? = null): WebnatError {
            val message = if (obj != null) "Message Serialization Failed: $obj" else "Message Serialization Failed"
            return WebnatError(WebnatErrorCode.SERIALIZATION_FAILED, message)
        }
    }
    
    /**
     * 转为 JSON 格式（便于返回 JS 或日志）
     *
     * 将 WebnatError 转换为 JSONObject 格式，包含 `code` 和 `message` 字段。
     *
     * @return 包含错误码和错误消息的 JSONObject
     *
     * @example
     * ```kotlin
     * val error = WebnatError.timeout()
     * val json = error.toJson()
     * // { "code": -1001, "message": "Operation Timeout" }
     * ```
     */
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("code", code)
            put("message", message)
        }
    }
}

