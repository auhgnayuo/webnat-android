package io.github.auhgnayuo.webnat

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.json.JSONObject
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("io.github.auhgnayuo.webnat.test", appContext.packageName)
    }

    @Test
    fun method_nilConnection_returnsClosed() {
        val rpc = MethodWebnat()
        val done = CountDownLatch(1)
        rpc.method(
            method = "m",
            connection = null,
            callback = { _, error ->
                assertEquals(WebnatErrorCode.CLOSED, (error as? WebnatError)?.code)
                done.countDown()
            }
        )
        assertTrue(done.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun method_reply_deliversResult() {
        val rpc = MethodWebnat()
        val outgoing = mutableListOf<Message>()
        val connection = ConnectionImpl("peer-1", null, null) { msg ->
            outgoing.add(msg)
        }

        var callbackResult: Any? = null
        val done = CountDownLatch(1)
        rpc.method(
            method = "sum",
            param = mapOf("a" to 1),
            connection = connection,
            callback = { result, error ->
                assertNull(error)
                callbackResult = result
                done.countDown()
            }
        )

        val invoke = outgoing.lastOrNull()?.invoke
        assertNotNull(invoke)
        val reply = Message.reply(to = connection.id, id = invoke!!.id, result = 99)
        rpc.onConnectionReceive(connection, reply)

        assertTrue(done.await(1, TimeUnit.SECONDS))
        assertEquals(99, callbackResult)
    }

    @Test
    fun method_timeout_returnsTimeout() {
        val rpc = MethodWebnat()
        val connection = ConnectionImpl("peer-1", null, null) { _ -> }
        val done = CountDownLatch(1)

        rpc.method(
            method = "hang",
            timeout = 100L,
            connection = connection,
            callback = { _, error ->
                assertEquals(WebnatErrorCode.TIMEOUT, (error as? WebnatError)?.code)
                done.countDown()
            }
        )

        assertTrue(done.await(2, TimeUnit.SECONDS))
    }

    @Test
    fun message_roundTrip_open() {
        val message = Message.open("conn-a", mapOf("origin" to "https://example.com"))
        val parsed = Message.from(message.toJson())
        assertNotNull(parsed)
        assertEquals("conn-a", parsed?.from)
        assertNotNull(parsed?.open)
    }

    @Test
    fun message_roundTrip_broadcast() {
        val message = Message.broadcast("conn-a", "evt", mapOf("x" to 1))
        val parsed = Message.from(message.toJson())
        assertNotNull(parsed?.broadcast)
        assertEquals("evt", parsed?.broadcast?.name)
    }

    @Test
    fun message_roundTrip_invoke_reply_notify_abort() {
        val invoke = Message.invoke("peer", "i1", "m", mapOf("k" to "v"))
        assertNotNull(Message.from(invoke.toJson())?.invoke)

        val reply = Message.reply("peer", "i1", result = "ok")
        assertNotNull(Message.from(reply.toJson())?.reply)

        val notify = Message.notify("peer", "i1", param = 1)
        assertNotNull(Message.from(notify.toJson())?.notify)

        val abort = Message.abort("peer", "i1")
        assertNotNull(Message.from(abort.toJson())?.abort)
    }

    @Test
    fun message_from_rejects_wrong_magic() {
        val json = Message.open("conn-a").toJson()
        json.put("magic", "NOPE")
        assertNull(Message.from(json))
    }

    @Test
    fun message_from_rejects_missing_required_fields() {
        val json = JSONObject().apply { put("magic", Message.MAGIC) }
        assertNull(Message.from(json))
    }
}

