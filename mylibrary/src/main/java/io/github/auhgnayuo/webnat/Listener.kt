package io.github.auhgnayuo.webnat

/**
 * Listener - 用于包装监听器（如方法监听、消息监听等）
 *
 * 说明：
 * - 通过 Any 类型存放监听器闭包或对象
 * - 支持引用判等（同一实例引用才认为相等）
 * - 用于便捷管理和注销监听器
 */
internal class Listener(
    /** 被包装的监听对象，可以是任意类型（如监听器 lambda、对象等） */
    val value: Any
) {
    /**
     * 引用判等，用于区分唯一性
     * 
     * @param other 另一个对象
     * @return 是否为同一实例（引用相等）
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Listener) return false
        return value === other.value
    }
    
    /**
     * 根据引用计算哈希码
     * 
     * @return 哈希码
     */
    override fun hashCode(): Int {
        return System.identityHashCode(value)
    }
}

