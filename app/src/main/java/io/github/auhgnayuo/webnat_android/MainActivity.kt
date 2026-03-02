package io.github.auhgnayuo.webnat_android

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import io.github.auhgnayuo.webnat.Webnat
import androidx.core.view.isVisible

class MainActivity : ComponentActivity() {
    private val viewModel: WebnatViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(true)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WebnatExampleApp(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun WebnatExampleApp(viewModel: WebnatViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Web") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("原生") }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            // 保持两个 tab 都在 Composition 中，避免 WebView 重新创建
            // 使用 zIndex 控制显示顺序，而不是条件渲染
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (selectedTab == 0) 1f else 0f)
            ) {
                WebTab(viewModel = viewModel)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (selectedTab == 1) 1f else 0f)
            ) {
                NativeTab(viewModel = viewModel)
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebTab(viewModel: WebnatViewModel) {
    // 获取 webnat_web example 的 URL
    // 开发环境：使用本地服务器
    // 生产环境：需要部署 webnat_web 的 example 到服务器
    val webURL = "http://172.16.71.254:5173/"  // Vue 应用（已修复）

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Red)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    // 启用 WebView 调试（必须在最开始）
                    WebView.setWebContentsDebuggingEnabled(true)

                    // 设置背景为白色（确保可见）
                    setBackgroundColor(android.graphics.Color.RED)
                    
                    // 启用硬件加速
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    
                    // 确保 WebView 可见性
                    visibility = android.view.View.VISIBLE
                    alpha = 1.0f
                    
                    // 添加调试信息
                    Log.d("WebnatExample", "WebView created - size: ${width}x${height}, visible: $isVisible")
                    
                    // 配置 WebView 设置（在加载任何内容之前）
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        
                        // 启用渲染相关设置
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        setSupportZoom(true)
                        builtInZoomControls = false
                        displayZoomControls = false
                        
                        // 强制启用所有渲染特性
                        javaScriptCanOpenWindowsAutomatically = true
                        mediaPlaybackRequiresUserGesture = false
                        
                        // 设置默认文本编码
                        defaultTextEncodingName = "utf-8"
                        
                        // Android 调试设置
                        @Suppress("DEPRECATION")
                        allowFileAccessFromFileURLs = true
                        @Suppress("DEPRECATION")
                        allowUniversalAccessFromFileURLs = true
                        
                        // 启用混合内容
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }
                    
                    // 初始化并设置 Webnat（必须在加载 URL 之前）
                    viewModel.setup(this)

                    // 配置 WebViewClient
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            Log.d("WebnatExample", "Page starting: $url")
                        }
                        
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            Log.d("WebnatExample", "✅ Page loaded: $url")
                            
                            // 验证页面状态和 Native 接口
                            view?.evaluateJavascript(
                                """
                                (function() {
                                    var info = {
                                        hasInterface: typeof window.__native_webnat__ !== 'undefined',
                                        bodyHeight: document.body ? document.body.scrollHeight : 0,
                                        elementCount: document.querySelectorAll('*').length,
                                        bodyBgColor: window.getComputedStyle(document.body).backgroundColor,
                                        bodyDisplay: window.getComputedStyle(document.body).display,
                                        htmlHeight: document.documentElement.scrollHeight,
                                        appHeight: document.getElementById('app') ? document.getElementById('app').scrollHeight : 0,
                                        appDisplay: document.getElementById('app') ? window.getComputedStyle(document.getElementById('app')).display : 'unknown',
                                        viewportHeight: window.innerHeight,
                                        viewportWidth: window.innerWidth
                                    };
                                    
                                    if (info.hasInterface) {
                                        console.log('✅ Native interface available');
                                    } else {
                                        console.error('❌ Native interface NOT available');
                                    }
                                    
                                    console.log('📊 Page info:', JSON.stringify(info, null, 2));
                                    return JSON.stringify(info);
                                })()
                                """.trimIndent()
                            ) { result ->
                                Log.d("WebnatExample", "📊 Page info: $result")
                                view.let {
                                    Log.d("WebnatExample", "📱 WebView state - visible: ${it.isVisible}, size: ${it.width}x${it.height}, alpha: ${it.alpha}")
                                }
                            }
                        }
                        
                        @Deprecated("Deprecated in Java")
                        override fun onReceivedError(
                            view: WebView?,
                            errorCode: Int,
                            description: String?,
                            failingUrl: String?
                        ) {
                            super.onReceivedError(view, errorCode, description, failingUrl)
                            Log.e("WebnatExample", "WebView error: $description ($errorCode) at $failingUrl")
                        }
                    }
                    
                    // 配置 WebChromeClient 用于调试
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                            val level = when (consoleMessage.messageLevel()) {
                                ConsoleMessage.MessageLevel.ERROR -> "ERROR"
                                ConsoleMessage.MessageLevel.WARNING -> "WARN"
                                else -> "INFO"
                            }
                            Log.d(
                                "WebView Console",
                                "[$level] ${consoleMessage.message()} -- From line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}"
                            )
                            return true
                        }
                    }
                    
                    // 添加布局监听器，检查 WebView 是否正确渲染
                    viewTreeObserver.addOnGlobalLayoutListener {
                        post {
                            Log.d("WebnatExample", "WebView layout - size: ${width}x${height}, scrollHeight: $contentHeight, visible: ${isVisible}")
                        }
                    }
                    
                    Log.d("WebnatExample", "Loading URL: $webURL")
                    
                    // 加载 URL（确保在所有配置完成后）
                    loadUrl(webURL)
                }
            },
            update = { webView ->
                // 当配置变化时，确保 WebView 状态
                webView.visibility = android.view.View.VISIBLE
                Log.d("WebnatExample", "WebView update - size: ${webView.width}x${webView.height}")
            }
        )
    }
}

@Composable
fun NativeTab(viewModel: WebnatViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 控制面板
        ControlPanel(viewModel = viewModel)
        
        // 日志显示区域
        LogListView(viewModel = viewModel)
    }
}

@Composable
fun ControlPanel(viewModel: WebnatViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Native 控制面板",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "连接数: ${viewModel.connectionCount.intValue}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.sendRaw() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("发送", fontSize = 12.sp)
                    Text("Raw", fontSize = 10.sp)
                }
            }
            
            Button(
                onClick = { viewModel.sendBroadcast() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2196F3)
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("发送", fontSize = 12.sp)
                    Text("Broadcast", fontSize = 10.sp)
                }
            }
            
            Button(
                onClick = { viewModel.sendMethod() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF9800)
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("发送", fontSize = 12.sp)
                    Text("Method", fontSize = 10.sp)
                }
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            TextButton(
                onClick = { viewModel.clearLogs() }
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text("清空日志")
            }
        }
    }
}

@Composable
fun LogListView(viewModel: WebnatViewModel) {
    val listState = rememberLazyListState()
    
    // 自动滚动到底部
    LaunchedEffect(viewModel.logs.size) {
        if (viewModel.logs.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.logs.size - 1)
        }
    }
    
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(viewModel.logs, key = { it.id }) { log ->
            LogRowView(log = log)
        }
    }
}

@Composable
fun LogRowView(log: LogEntry) {
    val (backgroundColor, borderColor, typeColor) = when (log.type) {
        LogEntry.LogType.SENT -> Triple(
            Color(0xFFE8F5E9),
            Color(0xFF4CAF50),
            Color(0xFF4CAF50)
        )
        LogEntry.LogType.RECEIVED -> Triple(
            Color(0xFFE3F2FD),
            Color(0xFF2196F3),
            Color(0xFF2196F3)
        )
        LogEntry.LogType.ERROR -> Triple(
            Color(0xFFFFEBEE),
            Color(0xFFF44336),
            Color(0xFFF44336)
        )
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(6.dp))
            .border(2.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = log.time,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.Gray,
            modifier = Modifier.alignByBaseline()
        )
        
        Text(
            text = log.type.label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .background(typeColor, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
        
        Text(
            text = "${log.category.label}: ${log.message}",
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.Black,
            modifier = Modifier.weight(1f)
        )
    }
}

