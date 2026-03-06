package io.github.auhgnayuo.webnat

/**
 * Connection 接口
 *
 * 表示 Native 端与 Web 端之间的单个连接。
 *
 * 核心职责：
 * 1. 管理连接的生命周期状态
 * 2. 提供消息发送能力
 * 3. 携带连接的元数据（attributes）
 *
 * 连接类型：
 * - 主框架连接：对应 Web 端页面的主框架
 * - iframe 连接：对应 Web 端页面中的 iframe
 *
 * 每个 WebView 可以有多个连接（主框架 + 多个 iframe）
 */
interface Connection {
    /** 连接的唯一标识符，由 Web 端生成 */
    val id: String
    
    /** 
     * 连接的元数据，在连接建立时由 Web 端传递，包含连接的附加信息
     * 例如：origin（来源）、isMainframe（是否为主框架）等
     */
    val attributes: Map<String, Any>?
    
    /**
     * 当前连接的界面 URL，在连接建立时当前页面的地址
     */
    val url: String?
    
    /** 连接是否已关闭 */
    val closed: Boolean
}

