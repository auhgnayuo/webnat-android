package io.github.auhgnayuo.webnat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 失败语义测试（模块层：MethodWebnat + ConnectionImpl）。
 * JVM 单测里 android.* 调用返回默认值（见 build.gradle.kts 的 isReturnDefaultValues），所以这里不经过 WebView / Looper。
 */
class FailureSemanticsTest {
    private fun invoke(from: String, id: String, method: String) =
        Message(from = from, to = Message.NATIVE_UUID, invoke = Invoke(id, method))

    private fun recordingConnection(out: MutableList<Message>, failOn: (Message) -> Exception? = { null }) =
        ConnectionImpl("conn-1", emptyMap(), null) { message ->
            failOn(message)?.let { throw it }
            out.add(message)
        }

    // T1 / T2：handler 同步抛出
    @Test
    fun handlerThatThrowsIsAnsweredWithAnErrorReply() {
        val mw = MethodWebnat()
        mw.on("throws") { _, _, _, _ -> throw IllegalStateException("boom") }
        mw.on("throws-webnat-error") { _, _, _, _ -> throw WebnatError(123, "denied") }
        val out = mutableListOf<Message>()
        val conn = recordingConnection(out)

        mw.onConnectionReceive(conn, invoke("conn-1", "rid-1", "throws"))
        mw.onConnectionReceive(conn, invoke("conn-1", "rid-2", "throws-webnat-error"))

        assertEquals(2, out.size)
        val first = WebnatError.from(out[0].reply?.error)
        assertEquals("rid-1", out[0].reply?.id)
        assertEquals(WebnatErrorCode.UNKNOWN, first?.code)
        assertEquals("boom", first?.message)
        val second = WebnatError.from(out[1].reply?.error)
        assertEquals(123, second?.code)
        assertEquals("denied", second?.message)
    }

    // T4 / T5：发送失败对等待方可见
    @Test
    fun methodFailsAtOnceWithTheErrorOfTheSend() {
        val mw = MethodWebnat()
        val conn = recordingConnection(mutableListOf(), failOn = { WebnatError.serializationFailed("NaN") })
        var seen: Exception? = null
        mw.method("remote", callback = { _, error -> seen = error }, connection = conn)
        assertEquals(WebnatErrorCode.SERIALIZATION_FAILED, (seen as? WebnatError)?.code)
    }

    @Test
    fun sendSurfacesAFailingTransportAndTrySendSwallowsIt() {
        val conn = recordingConnection(mutableListOf(), failOn = { WebnatError.closed() })
        try {
            conn.send(Message.raw("conn-1", 1))
            throw AssertionError("expected a WebnatError")
        } catch (e: WebnatError) {
            assertEquals(WebnatErrorCode.CLOSED, e.code)
        }
        conn.trySend(Message.raw("conn-1", 1))

        val closed = recordingConnection(mutableListOf())
        closed.closed = true
        try {
            closed.send(Message.raw("conn-1", 1))
            throw AssertionError("expected a WebnatError")
        } catch (e: WebnatError) {
            assertEquals(WebnatErrorCode.CLOSED, e.code)
        }
        closed.trySend(Message.raw("conn-1", 1))
    }

    // T6：结果无法序列化 -> 回复 -1012 错误
    @Test
    fun unserializableResultIsAnsweredWithSerializationFailed() {
        val mw = MethodWebnat()
        mw.on("big") { _, callback, _, _ -> callback(Double.NaN, null); {} }
        val out = mutableListOf<Message>()
        val conn = recordingConnection(out, failOn = { m ->
            if (m.reply?.result != null) WebnatError.serializationFailed("NaN") else null
        })

        mw.onConnectionReceive(conn, invoke("conn-1", "rid-3", "big"))

        assertEquals(1, out.size)
        assertEquals("rid-3", out[0].reply?.id)
        assertNull(out[0].reply?.result)
        assertEquals(WebnatErrorCode.SERIALIZATION_FAILED, WebnatError.from(out[0].reply?.error)?.code)
    }

    @Test
    fun aSendFailureThatIsNotAboutSerializationGetsNoFallbackReply() {
        val mw = MethodWebnat()
        mw.on("gone") { _, callback, _, _ -> callback(1, null); {} }
        val attempts = mutableListOf<Message>()
        val conn = ConnectionImpl("conn-1", emptyMap(), null) { message ->
            attempts.add(message)
            throw WebnatError.closed()
        }

        mw.onConnectionReceive(conn, invoke("conn-1", "rid-4", "gone"))

        assertEquals(1, attempts.size)
    }

    // 真实的 JSON 序列化：NaN / Infinity 让 JSONObject.put 抛出，是 Android 上 -1012 的来源
    @Test
    fun messageToJsonThrowsForNaNSoTheSendClosureCanReportSerializationFailed() {
        val message = Message.raw("conn-1", mapOf("x" to Double.NaN))
        try {
            message.toJson()
            throw AssertionError("expected the JSON layer to reject NaN")
        } catch (e: org.json.JSONException) {
            assertNotNull(e)
        }
    }

    // T7 / T8：连接结束 -> 待完成调用以 CLOSED 失败，运行中的 handler 被取消且不回复
    @Test
    fun closingAConnectionRejectsPendingCallsAndCancelsRunningHandlers() {
        val mw = MethodWebnat()
        var cancelled = false
        mw.on("long") { _, _, _, _ -> { cancelled = true } }
        val out = mutableListOf<Message>()
        val conn = recordingConnection(out)
        var pendingError: Exception? = null
        mw.method("never", callback = { _, error -> pendingError = error }, connection = conn)
        mw.onConnectionReceive(conn, invoke("conn-1", "rid-5", "long"))
        val sent = out.size

        mw.onConnectionClose(conn)

        assertEquals(WebnatErrorCode.CLOSED, (pendingError as? WebnatError)?.code)
        assertTrue(cancelled)
        assertEquals(sent, out.size)
        mw.onConnectionClose(conn)
    }

    // T10：同步完成的 handler 不留下取消 / 关闭钩子
    @Test
    fun aHandlerThatCompletesSynchronouslyLeavesNoHooksBehind() {
        val mw = MethodWebnat()
        mw.on("quick") { _, callback, _, _ -> callback("done", null); {} }
        mw.on("throws") { _, _, _, _ -> throw RuntimeException("x") }
        val conn = recordingConnection(mutableListOf())

        mw.onConnectionReceive(conn, invoke("conn-1", "rid-6", "quick"))
        mw.onConnectionReceive(conn, invoke("conn-1", "rid-7", "throws"))

        assertFalse(mw.hasPendingHooks())
    }
}
