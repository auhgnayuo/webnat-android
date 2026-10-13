# Webnat

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-purple.svg)](https://kotlinlang.org)
[![Platform](https://img.shields.io/badge/Platform-Android%205.0%2B-green.svg)](https://developer.android.com)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

[中文文档](./README_CN.md)

A lightweight WebView-Native bridge library for Android. Supports multiple communication modes based on Android WebView and JavascriptInterface.

## Features

- **Android Support** - Android 5.0+
- **iframe Support** - Automatic message forwarding between main frame and iframes
- **Three Communication Modes** - Bidirectional raw messages, broadcast messages, and method calls (RPC)
- **Timeout & Cancellation** - Built-in timeout control and active cancellation mechanism
- **Coroutine Support** - Full support for Kotlin Coroutines and Flow

## Installation

Add to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.auhgnayuo:webnat:1.4.0")
}
```

## Related Projects

Webnat requires a Web-side implementation and also supports other Native platforms:

| Platform | Repository |
|----------|------------|
| Web (JavaScript/TypeScript) | [webnat-web](https://github.com/auhgnayuo/webnat-web) |
| iOS / macOS (Swift) | [webnat-darwin](https://github.com/auhgnayuo/webnat-darwin) |
| HarmonyOS (ArkTS) | [webnat-ohos](https://github.com/auhgnayuo/webnat-ohos) |

## Usage

### 1. Initialization

```kotlin
import io.github.auhgnayuo.webnat.Webnat
import android.webkit.WebView

val webView = WebView(context)
val webnat = Webnat.of(webView)
```

### 2. Wait for Web-side Connection

Connections are initiated by the **Web side (JavaScript)**. The Native side automatically receives and manages connections.

```kotlin
val connections = webnat.getConnections()
println("Active connections: ${connections.size}")

val connection = connections["connection-id"]
connection?.let {
    println("Connection found: ${it.id}")
    println("Attributes: ${it.attributes}")
}
```

### 3. Send and Receive Messages

```kotlin
// Send raw message
webnat.raw(mapOf("message" to "Hello from Native!"), connection)

// Listen for raw messages
webnat.onRaw { connection, raw ->
    println("From ${connection.id}: $raw")
}

// Broadcast
webnat.broadcast("userLoggedIn", mapOf("userId" to 123), connection)

// Listen for broadcasts
webnat.onBroadcast("userLoggedIn") { param, connection ->
    println("Broadcast from ${connection.id}: $param")
}

// Stream broadcasts with Flow
lifecycleScope.launch {
    webnat.listenBroadcast("userLoggedIn").collect { (param, connection) ->
        println("Broadcast from ${connection.id}: $param")
    }
}

// Call Web-side method
lifecycleScope.launch {
    try {
        val result = webnat.method(
            method = "getUserInfo",
            param = mapOf("userId" to 123),
            timeout = 5000L,
            connection = connection
        )
        println("User info: $result")
    } catch (e: WebnatError) {
        println("Error: ${e.message}")
    }
}

// Register method for Web to call
webnat.onMethod("getUserInfo") { param, callback, notify, connection ->
    val userId = (param as? Map<*, *>)?.get("userId") as? Int ?: 0

    notify(mapOf("progress" to 50))

    lifecycleScope.launch {
        delay(1000)
        callback(mapOf("userId" to userId, "name" to "User"), null)
    }

    return@onMethod {
        // Cancellation logic
    }
}
```

### 4. Send failures and handler errors

- A failed send is visible to the caller that waits for it: `method` fails at once with `SERIALIZATION_FAILED` when the parameter cannot be serialized (for example `NaN` or `Infinity`), and with `CLOSED` when the `WebView` is gone or the connection is closed. `raw` and `broadcast` stay fire-and-forget.
- A method handler that fails (the handler throws, or calls `callback(null, error)`) is answered with an error reply `{ code, message }`, so the caller never waits for its timeout because of a handler bug. A result that cannot be serialized is answered with `SERIALIZATION_FAILED`.
- Android only: a script failure inside the page cannot be observed (`evaluateJavascript` has no error channel); such a call still ends by timeout, abort or CLOSE.

### 5. Page crash and teardown

When a page unloads normally, the Web SDK sends CLOSE and Webnat ends that connection. A crashed render process or a destroyed `WebView` never gets to send it, so forward the event to Webnat: call `Webnat.of(view).onRenderProcessGone()` from your `WebViewClient.onRenderProcessGone`, and once more before you destroy the `WebView`. Webnat then ends the page's connections itself: pending `method` calls fail with `CLOSED` and running method handlers are cancelled. Nothing is sent to the page, and Webnat stays usable, so a reloaded page opens new connections as usual. It can be called from any thread, and page scripts cannot call it.

```kotlin
webView.webViewClient = object : WebViewClient() {
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        Webnat.of(view).onRenderProcessGone()
        return true
    }
}
```

## License

This project is licensed under the MIT License.

## Testing

- Local JVM unit tests: `./gradlew :mylibrary:testDebugUnitTest`
- Android instrumented tests (includes Message/Method lifecycle cases): `./gradlew :mylibrary:connectedDebugAndroidTest`
