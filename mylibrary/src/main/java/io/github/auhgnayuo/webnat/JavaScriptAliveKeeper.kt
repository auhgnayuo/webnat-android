package io.github.auhgnayuo.webnat

import android.os.Handler
import android.os.Looper

/**
 * JavaScriptAliveKeeper - JavaScript 保活管理器
 *
 * 用于保持与 JavaScript 的心跳活跃状态，防止系统挂起 JS 执行。
 *
 * **工作原理**：
 * - 该类会周期性地调用提供的心跳回调，以维持 JS 侧的活跃性
 * - 支持引用计数，只有在至少有一个引用时才启动心跳定时器
 * - 心跳定时器仅在有引用时激活，无引用时自动停止
 *
 * **主要场景**：
 * - WebView 失活（比如不在窗口上，系统会尽可能挂起 JS 执行）
 * - Native 侧调用 JS 侧的异步延迟方法，还未处理完，这个时候 JS 挂起，就不会再执行
 * - 通过 `evaluateJavascript`，可以让 JS 短暂运行，保持执行环境活跃
 *
 * **生命周期管理**：
 * - `increaseReference()` 增加引用计数
 * - `decreaseReference()` 减少引用计数
 * - 当引用计数为 0 时，自动停止心跳定时器
 *
 * - Note: 这是内部类，不应直接使用
 */
internal class JavaScriptAliveKeeper(
    /** 心跳定时器的触发间隔（毫秒），用于定时检查是否应该触发心跳 */
    private val timerInterval: Long,
    /** 心跳回调函数，每次心跳时都会调用 */
    private val heartbeat: () -> Unit
) {
    /** 主线程 Handler，用于在主线程执行操作 */
    private val mainHandler = Handler(Looper.getMainLooper())
    
    /** 心跳间隔（毫秒），实际触发心跳的间隔，必须大于 0 */
    var heartbeatInterval: Long = 1000
        set(value) {
            if (value <= 0) {
                // 不允许非正数，保持原值不变并打印警告
                android.util.Log.w("JavaScriptAliveKeeper", "Invalid heartbeatInterval, using previous value: $field")
                return
            }
            val oldValue = field
            field = value
            // 更新下次心跳计划时间，保持间隔不变
            nextHeartbeatTime?.let {
                nextHeartbeatTime = it + (value - oldValue)
            }
        }
    
    /** 当前引用计数（有多少对象/连接需要保持心跳） */
    private var referenceCount = 0
    
    /** 下次心跳计划的时间点（时间戳，毫秒） */
    private var nextHeartbeatTime: Long? = null
    
    /** 管理心跳的 Runnable */
    private val timerRunnable = object : Runnable {
        override fun run() {
            val next = nextHeartbeatTime
            if (next != null) {
                val now = System.currentTimeMillis()
                // 已到计划心跳时间，触发心跳并重新计划下次心跳
                if (now >= next) {
                    heartbeat()
                    nextHeartbeatTime = now + heartbeatInterval
                }
            }
            // 继续下一次检查
            mainHandler.postDelayed(this, timerInterval)
        }
    }
    
    /** 定时器是否正在运行 */
    private var isTimerRunning = false
    
    /**
     * 增加引用计数
     *
     * 当第一个引用到来时自动启动心跳定时器
     */
    fun increaseReference() {
        referenceCount++
        if (referenceCount == 1) {
            startTimer()
        }
    }
    
    /**
     * 减少引用计数
     *
     * 当引用计数减为 0 时自动停止心跳定时器
     */
    fun decreaseReference() {
        referenceCount = maxOf(referenceCount - 1, 0)
        if (referenceCount == 0) {
            stopTimer()
        }
    }
    
    /**
     * 推迟下次心跳时间
     *
     * 立即把下次心跳推迟到当前时间后的一个心跳间隔
     */
    fun delay() {
        nextHeartbeatTime = System.currentTimeMillis() + heartbeatInterval
    }
    
    /**
     * 启动心跳定时器
     *
     * 若定时器已存在则先停止。定时器每隔 `timerInterval` 检查是否到下次心跳时间。
     * 
     * 工作流程：
     * 1. 停止现有定时器（如果存在）
     * 2. 设置下次心跳时间
     * 3. 创建新的定时器，周期性检查是否需要触发心跳
     */
    private fun startTimer() {
        stopTimer()
        nextHeartbeatTime = System.currentTimeMillis() + heartbeatInterval
        mainHandler.postDelayed(timerRunnable, timerInterval)
        isTimerRunning = true
    }
    
    /**
     * 停止心跳定时器
     *
     * 释放定时器资源，防止内存泄漏
     */
    private fun stopTimer() {
        if (isTimerRunning) {
            mainHandler.removeCallbacks(timerRunnable)
            isTimerRunning = false
        }
    }
}

