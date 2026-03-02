package io.github.auhgnayuo.webnat_android

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 日志条目数据类
 */
data class LogEntry(
    val id: String = UUID.randomUUID().toString(),
    val time: String,
    val type: LogType,
    val category: LogCategory,
    val message: String
) {
    enum class LogType(val label: String) {
        SENT("发送"),
        RECEIVED("接收"),
        ERROR("错误")
    }
    
    enum class LogCategory(val label: String) {
        RAW("Raw"),
        BROADCAST("Broadcast"),
        METHOD("Method"),
    }
    
    companion object {
        private val timeFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        
        fun create(type: LogType, category: LogCategory, message: String): LogEntry {
            val time = timeFormatter.format(Date())
            return LogEntry(
                time = time,
                type = type,
                category = category,
                message = message
            )
        }
    }
}

