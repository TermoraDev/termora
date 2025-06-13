package app.termora.account

import kotlin.test.Test

class ServerManagerTest {
    @Test
    fun test() {
        ServerManager.getInstance().login(
            Server(
                name = "test",
                server = "http://127.0.0.1:8080"
            ), "admin", "admin"
        )
    }
}