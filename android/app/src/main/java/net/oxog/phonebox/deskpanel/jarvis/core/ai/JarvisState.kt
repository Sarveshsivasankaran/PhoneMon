package net.oxog.phonebox.deskpanel.jarvis.core.ai

enum class JarvisState(val displayName: String, val glowColorHex: Long) {
    OFFLINE("OFFLINE", 0xFF546E7A),
    STANDBY("STANDBY", 0xFF00F2FE),
    LISTENING("LISTENING", 0xFF4FACFE),
    THINKING("THINKING", 0xFF7F00FF),
    VISION("VISION", 0xFF00E5FF),
    EXECUTING("EXECUTING", 0xFFFF9100),
    SPEAKING("SPEAKING", 0xFF00E676),
    ERROR("ERROR", 0xFFFF1744);

    fun isBusy(): Boolean = this == THINKING || this == VISION || this == EXECUTING
}

