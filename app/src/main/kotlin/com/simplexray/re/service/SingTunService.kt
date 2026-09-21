package com.simplexray.re.service

class SingTunService : GoTunService() {
    override val tag: String = "SingTunService"

    override fun startNative(
        tunFd: Int,
        socksHost: String,
        socksPort: Int,
        mtu: Int,
        username: String,
        password: String
    ): Boolean = SingTunStartService(tunFd, socksHost, socksPort, mtu, username, password)

    override fun stopNative(): Boolean = SingTunStopService()

    private external fun SingTunStartService(
        tunFd: Int,
        socksHost: String,
        socksPort: Int,
        mtu: Int,
        username: String,
        password: String
    ): Boolean

    private external fun SingTunStopService(): Boolean

    companion object {
        init {
            System.loadLibrary("singtun")
        }
    }
}
