# Webnat

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-purple.svg)](https://kotlinlang.org)
[![Platform](https://img.shields.io/badge/Platform-Android%205.0%2B-green.svg)](https://developer.android.com)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Webnat 是一个用于 Native (Android) 与 Web 之间通信的 Kotlin 库。支持多种通信模式，基于 Android WebView 和 JavascriptInterface。

## 特性

- **多平台支持** - 支持 Android 5.0+
- **iframe 支持** - 自动处理主框架和 iframe 之间的消息转发
- **三种通信模式** - 支持双向的原始消息、广播消息和方法调用（RPC）
- **超时和取消** - 内置超时控制和主动取消机制
- **协程支持** - 完全支持 Kotlin Coroutines 和 Flow

## 安装

在你的 `build.gradle.kts` 文件中添加：

```kotlin
dependencies {
    implementation("io.github.auhgnayuo:webnat:1.0.0")
}
```

## 相关项目

Webnat 需要配合 Web 端实现使用，同时也支持其他 Native 平台：

| 平台 | 仓库 |
|------|------|
| Web (JavaScript/TypeScript) | [webnat-web](https://github.com/auhgnayuo/webnat-web) |
| iOS / macOS (Swift) | [webnat-os](https://github.com/auhgnayuo/webnat-os) |
| HarmonyOS (ArkTS) | [webnat-ohos](https://github.com/auhgnayuo/webnat-ohos) |

## 基本使用

### 1. 初始化

```kotlin
import io.github.auhgnayuo.webnat.Webnat
import android.webkit.WebView

val webView = WebView(context)
val webnat = Webnat.of(webView)
```

### 2. 等待 Web 端建立连接

连接是由 **Web 端（JavaScript）主动发起**的，Native 端会自动接收和管理连接。

```kotlin
// Web 端（JavaScript）会发送 "open" 消息来建立连接
// Native 端自动创建 Connection 实例并存储在 webnat.connections 中

// 访问所有活跃连接
val connections = webnat.getConnections()
println("当前有 ${connections.size} 个连接")

// 通过 ID 获取特定连接
val connection = connections["connection-id"]
connection?.let {
    println("找到连接: ${it.id}")
    println("连接属性: ${it.attributes}")
}
```

### 3. 发送和接收消息

```kotlin
// 发送原始消息
webnat.raw(mapOf("message" to "Hello from Native!"), connection)

// 监听原始消息
webnat.onRaw { connection, raw ->
    println("From ${connection.id}: $raw")
}

// 广播消息
webnat.broadcast("userLoggedIn", mapOf("userId" to 123), connection)

// 监听广播消息
webnat.onBroadcast("userLoggedIn") { param, connection ->
    println("Broadcast from ${connection.id}: $param")
}

// 流式监听广播消息
lifecycleScope.launch {
    webnat.listenBroadcast("userLoggedIn").collect { (param, connection) ->
        println("Broadcast from ${connection.id}: $param")
    }
}

// 调用 Web 端方法
webnat.method(
    method = "getUserInfo",
    param = mapOf("userId" to 123),
    timeout = 5000L,
    connection = connection,
    callback = { result, error ->
        if (error != null) {
            println("Error: $error")
        } else {
            println("User info: $result")
        }
    }
)

// 异步方式调用 Web 端方法
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

// 注册方法供 Web 调用
webnat.onMethod("getUserInfo") { param, callback, notify, connection ->
    val userId = (param as? Map<*, *>)?.get("userId") as? Int ?: 0

    // 可以发送途中的通知（如进度更新）
    notify(mapOf("progress" to 50))

    // 模拟异步操作
    lifecycleScope.launch {
        delay(1000)
        callback(mapOf("userId" to userId, "name" to "User"), null)
    }

    return@onMethod {
        // 取消操作的逻辑
    }
}
```

## 协议

本项目采用 MIT 协议开源。
