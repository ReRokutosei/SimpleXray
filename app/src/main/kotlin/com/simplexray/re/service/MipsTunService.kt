package com.simplexray.re.service

class MipsTunService : GoTunService() {
    override val tag: String = "MipsTunService"

    override fun startNative(
        tunFd: Int,
        socksHost: String,
        socksPort: Int,
        mtu: Int,
        username: String,
        password: String
    ): Boolean = MipsTunStartService(tunFd, socksHost, socksPort, mtu, username, password)

    override fun stopNative(): Boolean = MipsTunStopService()

    private external fun MipsTunStartService(
        tunFd: Int,
        socksHost: String,
        socksPort: Int,
        mtu: Int,
        username: String,
        password: String
    ): Boolean

    private external fun MipsTunStopService(): Boolean

    companion object {
        init {
            System.loadLibrary("mipstun")
        }
    }
}
