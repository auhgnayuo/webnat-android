package io.github.auhgnayuo.webnat_android

import android.util.Log
import android.webkit.WebView
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.auhgnayuo.webnat.Webnat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Webnat 示例 ViewModel
 */
class WebnatViewModel : ViewModel() {
    val logs = mutableStateListOf<LogEntry>()
    val connectionCount = mutableIntStateOf(0)
    
    private var webnat: Webnat? = null
    
    // UTF-8 边界字符测试数据
    private val utf8BoundaryChars = mapOf(
        "ascii" to "Hello World! 123",
        "latin1" to "ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏ",
        "cjk" to "你好世界！中文测试",
        "emoji" to "😀😁😂🤣😃😄😅",
        "mixed" to "Hello 你好 😀 世界 World! 测试 Test 123",
        "boundaries" to "\u0000\u007F\u0080\u07FF\u0800\uFFFF",
        "special" to "\n\r\t\\/\"'`{}[]()<>",
        "empty" to "",
        "long" to "A".repeat(1000) + "你".repeat(100) + "😀".repeat(50),
        "array" to listOf("Hello World! 123", "你好世界！中文测试", "😀😁😂🤣😃😄😅"),
        "nested" to mapOf(
            "level1" to mapOf(
                "level2" to mapOf(
                    "level3" to "Hello 你好 😀 世界 World! 测试 Test 123"
                )
            )
        )
    )
    
    fun setup(webView: WebView) {
        Log.d("WebnatViewModel", "Setting up Webnat...")
        webnat = Webnat.of(webView)
        Log.d("WebnatViewModel", "Webnat initialized")
        
        // 注册消息监听器
        registerListeners()
        Log.d("WebnatViewModel", "Listeners registered")
        
        // 注册方法处理函数
        registerMethodHandlers()
        Log.d("WebnatViewModel", "Method handlers registered")
        
        // 定期检查连接数
        viewModelScope.launch {
            while (true) {
                updateConnectionCount()
                delay(500)
            }
        }
    }
    
    private fun updateConnectionCount() {
        connectionCount.intValue = webnat?.getConnectionCount() ?: 0
    }
    
    private fun registerListeners() {
        val webnat = webnat ?: return
        
        // 注册 raw 消息监听
        webnat.onRaw { connection, raw ->
            val message = formatMessage(raw)
            addLog(LogEntry.LogType.RECEIVED, LogEntry.LogCategory.RAW, "收到 Raw 消息: $message")
        }
        
        // 注册 broadcast 消息监听
        webnat.onBroadcast("test-broadcast") { param, connection ->
            val message = formatMessage(param)
            addLog(LogEntry.LogType.RECEIVED, LogEntry.LogCategory.BROADCAST, "收到 Broadcast 消息: $message")
        }
    }
    
    private fun registerMethodHandlers() {
        val webnat = webnat ?: return
        
        // 注册 test-method 方法处理函数
        webnat.onMethod("test-method") { param, callback, notify, connection ->
            val message = formatMessage(param)
            addLog(LogEntry.LogType.RECEIVED, LogEntry.LogCategory.METHOD, "收到 Method 调用: $message")
            
            // 发送通知
            notify(mapOf("progress" to 50, "message" to "处理中..."))
            
            // 模拟异步处理
            val job = viewModelScope.launch {
                delay(3000)
                val result = mapOf(
                    "success" to true,
                    "result" to "Method 调用成功",
                    "receivedParam" to (param ?: "null")
                )
                callback(result, null)
            }
            
            // 返回取消函数
            return@onMethod {
                job.cancel()
            }
        }
    }
    
    private fun formatMessage(message: Any?): String {
        if (message == null) {
            return "null"
        }
        
        return when (message) {
            is Map<*, *> -> JSONObject(message).toString(2)
            is List<*> -> JSONArray(message).toString(2)
            is String -> message
            else -> message.toString()
        }
    }
    
    fun addLog(type: LogEntry.LogType, category: LogEntry.LogCategory, message: String) {
        val entry = LogEntry.create(type, category, message)
        logs.add(entry)
        
        // 限制日志数量，避免内存问题
        if (logs.size > 1000) {
            repeat(100) { logs.removeAt(0) }
        }
    }
    
    fun sendRaw() {
        val webnat = webnat ?: run {
            addLog(LogEntry.LogType.ERROR, LogEntry.LogCategory.RAW, "Webnat 未初始化")
            return
        }
        
        val connection = webnat.getFirstConnection() ?: run {
            addLog(LogEntry.LogType.ERROR, LogEntry.LogCategory.RAW, "没有可用的连接")
            return
        }
        
        val message = formatMessage(utf8BoundaryChars)
        addLog(LogEntry.LogType.SENT, LogEntry.LogCategory.RAW, "发送 Raw 消息: $message")
        
        webnat.raw(utf8BoundaryChars, connection)
    }
    
    fun sendBroadcast() {
        val webnat = webnat ?: run {
            addLog(LogEntry.LogType.ERROR, LogEntry.LogCategory.BROADCAST, "Webnat 未初始化")
            return
        }
        
        val connection = webnat.getFirstConnection() ?: run {
            addLog(LogEntry.LogType.ERROR, LogEntry.LogCategory.BROADCAST, "没有可用的连接")
            return
        }
        
        val message = formatMessage(utf8BoundaryChars)
        addLog(LogEntry.LogType.SENT, LogEntry.LogCategory.BROADCAST, "发送 Broadcast 消息: $message")
        
        webnat.broadcast("test-broadcast", utf8BoundaryChars, connection)
    }
    
    fun sendMethod() {
        val webnat = webnat ?: run {
            addLog(LogEntry.LogType.ERROR, LogEntry.LogCategory.METHOD, "Webnat 未初始化")
            return
        }
        
        val connection = webnat.getFirstConnection() ?: run {
            addLog(LogEntry.LogType.ERROR, LogEntry.LogCategory.METHOD, "没有可用的连接")
            return
        }
        
        val message = formatMessage(utf8BoundaryChars)
        addLog(LogEntry.LogType.SENT, LogEntry.LogCategory.METHOD, "发送 Method 调用: $message")
        
        // 使用协程版本的 method
        viewModelScope.launch {
            try {
                val result = webnat.method(
                    method = "test-method",
                    param = utf8BoundaryChars,
                    timeout = 5000L,
                    onNotification = { notification ->
                        val notifMessage = formatMessage(notification)
                        addLog(LogEntry.LogType.RECEIVED, LogEntry.LogCategory.METHOD, "收到 Method 通知: $notifMessage")
                    },
                    connection = connection
                )
                val resultMessage = formatMessage(result)
                addLog(LogEntry.LogType.RECEIVED, LogEntry.LogCategory.METHOD, "Method 调用成功: $resultMessage")
            } catch (e: Exception) {
                addLog(LogEntry.LogType.ERROR, LogEntry.LogCategory.METHOD, "Method 调用失败: ${e.message}")
            }
        }
    }
    
    fun clearLogs() {
        logs.clear()
    }
}

