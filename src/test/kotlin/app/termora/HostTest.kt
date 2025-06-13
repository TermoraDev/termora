package app.termora

import app.termora.Application.ohMyJson
import kotlin.test.Test

class HostTest {
    @Test
    fun test() {
        val host = ohMyJson.decodeFromString<Host>(
            """
            {
              "name": "test",
              "protocol": SSHProtocolProvider.PROTOCOL,
              "test": ""
            }
        """.trimIndent()
        )

        println(ohMyJson.encodeToString(host))
    }
}