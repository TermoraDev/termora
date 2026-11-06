package app.termora

import app.termora.Application.ohMyJson
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SSHReconnectOptionsTest {
    @Test
    fun legacyHostsDoNotAutomaticallyReconnect() {
        val host = ohMyJson.decodeFromString<Host>("""{"name":"legacy","protocol":"SSH"}""")
        assertFalse(host.options.extras["autoReconnect"].toBoolean())
    }

    @Test
    fun reconnectPreferenceSurvivesHostSerialization() {
        val host = Host(name = "ssh", protocol = "SSH", options = Options.Default.copy(
            extras = mapOf("autoReconnect" to "true")
        ))
        val restored = ohMyJson.decodeFromString<Host>(ohMyJson.encodeToString(host))
        assertTrue(restored.options.extras["autoReconnect"].toBoolean())
    }
}
