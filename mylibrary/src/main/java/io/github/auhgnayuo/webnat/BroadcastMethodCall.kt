package io.github.auhgnayuo.webnat

import android.os.Handler
import android.os.Looper

/**
 * BroadcastMethodCall - 广播式方法调用的竞赛协调器
 *
 * 当 [Webnat.method] 未指定 `connection` 时，会把同一次方法调用**广播**给当前所有连接
 * （主框架 + 所有 iframe），并在这些子调用之间做「谁先返回用谁的」的竞赛裁决。
 *
 * 典型使用场景是**透传 iframe**：正常情况下同一个方法只会有一个 frame 注册处理器，
 * 其余 frame 会立即回 `unimplemented`。协调器据此挑出真正实现该方法的 frame 作为结果来源。
 *
 * ## 竞赛规则
 *
 * 每个连接各自发起一次独立的单连接 RPC（各有独立的调用 ID，互不干扰），协调器据其响应裁决：
 *
 * - **胜者锁定**：第一个发来「有意义信号」的连接成为胜者，随后立即 `abort` 其余连接，
 *   之后只透传胜者的 notify 与最终结果。
 *   「有意义信号」= `notify`、成功 `reply`、或**真实业务错误**（错误码不是 `unimplemented`/`closed`）。
 * - **淘汰而非获胜**：`unimplemented(-1010)` 与 `closed(-1004)` 都不会「抢跑」成为胜者，
 *   只是把该连接移出候选池；当所有连接都被淘汰且从未出现胜者时才最终失败：
 *   若淘汰过程中出现过 `closed` 则返回 `closed`，否则返回 `unimplemented`。
 * - **已锁定的胜者中途失败**（如其连接关闭）：直接把该错误返回给调用方。
 * - **超时 / 取消**：作用于整组——`abort` 全部子调用，返回 `timeout` / `cancelled`。
 *   子调用本身不设超时，超时统一由协调器组级管理。
 *
 * 线程安全：所有状态变更都在 [lock] 内进行（可重入），可安全应对子调用同步回调造成的重入。
 */
internal class BroadcastMethodCall(
    private val methodName: String,
    private val onNotification: MethodOnNotification?,
    private val callback: MethodCallback?,
    private val onFinished: () -> Unit,
) {
    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 是否已终结（终态到达后所有信号都被忽略） */
    private var settled = false

    /** 胜者连接 ID；null 表示尚未锁定胜者 */
    private var winnerId: String? = null

    /** 已被淘汰（回了 unimplemented/closed）的连接 ID 集合 */
    private val eliminated = HashSet<String>()

    /** 淘汰过程中是否出现过 closed（决定候选池耗尽时返回哪种错误码） */
    private var sawClosed = false

    /** 参与竞赛的连接总数（快照自发起时） */
    private var total = 0

    /** 各子调用的取消句柄，key 为连接 ID；完成或被取消后从表中移除 */
    private val cancels = HashMap<String, MethodCancellation>()

    /** 组级超时任务 */
    private var timeoutRunnable: Runnable? = null

    /**
     * 启动广播调用
     *
     * @param connections 参与竞赛的连接快照（发起时的所有连接）
     * @param methodWebnat 复用其单连接 RPC 能力
     * @param param 方法参数
     * @param timeout 组级超时（毫秒）；null 或 <= 0 时不注册超时
     * @return 取消函数，调用后 abort 全部子调用并以 cancelled 结束
     */
    fun start(
        connections: List<Connection>,
        methodWebnat: MethodWebnat,
        param: Any?,
        timeout: Long?,
    ): MethodCancellation {
        synchronized(lock) {
            total = connections.size

            // 没有任何连接：与旧的单连接行为一致，立即返回 closed
            if (connections.isEmpty()) {
                finish(null, WebnatError.closed())
                return {}
            }

            // 组级超时（子调用不再各自设置超时）
            if (timeout != null && timeout > 0) {
                val r = Runnable { finish(null, WebnatError.timeout()) }
                timeoutRunnable = r
                mainHandler.postDelayed(r, timeout)
            }

            // 对每个连接发起独立的单连接子调用
            for (connection in connections) {
                // 若在发起过程中已终结（如某连接同步 closed，或真实错误抢跑），停止继续发起
                if (settled) break
                val connId = connection.id
                val cancel = methodWebnat.method(
                    methodName,
                    param,
                    null,
                    { p -> handleNotify(connId, p) },
                    { result, error -> handleComplete(connId, result, error) },
                    connection
                )
                if (settled) {
                    // 该子调用在发起时已同步完成并触发终结，取消句柄已无意义（幂等 no-op）
                    cancel()
                } else {
                    cancels[connId] = cancel
                }
            }
        }
        return { finish(null, WebnatError.cancelled()) }
    }

    /** 处理某连接的途中通知 */
    private fun handleNotify(connId: String, param: Any?) {
        synchronized(lock) {
            if (settled) return
            val w = winnerId
            if (w != null) {
                // 已有胜者：只透传胜者的通知，落败者一律忽略
                if (w == connId) onNotification?.invoke(param)
                return
            }
            // 尚无胜者：该连接凭「首个有意义信号」锁定为胜者
            winnerId = connId
            cancelLosers(connId)
            onNotification?.invoke(param)
        }
    }

    /** 处理某连接的最终完成（成功或错误） */
    private fun handleComplete(connId: String, result: Any?, error: Exception?) {
        synchronized(lock) {
            if (settled) return
            // 该子调用已完成，从取消表中移除
            cancels.remove(connId)

            val w = winnerId
            if (w != null) {
                // 已锁定的胜者产出终态：直接作为最终结果；落败者迟到消息忽略
                if (w == connId) finish(result, error)
                return
            }

            // 尚无胜者
            if (error != null && isEliminating(error)) {
                eliminated.add(connId)
                if ((error as? WebnatError)?.code == WebnatErrorCode.CLOSED) sawClosed = true
                // 全部连接都被淘汰且从未出现胜者：兜底失败
                if (eliminated.size >= total) {
                    finish(null, if (sawClosed) WebnatError.closed() else WebnatError.unimplemented(methodName))
                }
                return
            }

            // 成功或真实业务错误：该连接抢跑获胜并直接终结
            winnerId = connId
            cancelLosers(connId)
            finish(result, error)
        }
    }

    /** 判断错误是否属于「淘汰类」（不抢跑、不获胜，只移出候选池） */
    private fun isEliminating(error: Exception): Boolean {
        val code = (error as? WebnatError)?.code ?: return false
        return code == WebnatErrorCode.UNIMPLEMENTED || code == WebnatErrorCode.CLOSED
    }

    /** 取消除 [except] 外的所有子调用（给落败 frame 发 abort） */
    private fun cancelLosers(except: String) {
        val losers = cancels.filterKeys { it != except }
        // 先从表中移除再逐个取消：取消可能同步回调 handleComplete，避免重入时重复处理
        losers.keys.forEach { cancels.remove(it) }
        losers.values.forEach { it.invoke() }
    }

    /** 终结整组调用：幂等，仅触发一次用户回调与收尾钩子 */
    private fun finish(result: Any?, error: Exception?) {
        synchronized(lock) {
            if (settled) return
            settled = true
            timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            timeoutRunnable = null
            // 取消所有剩余子调用（胜者的已完成，取消为 no-op；落败者收到 abort）
            val remaining = HashMap(cancels)
            cancels.clear()
            remaining.values.forEach { it.invoke() }
            callback?.invoke(result, error)
            onFinished()
        }
    }
}
