package app.termora

import app.termora.Application.ohMyJson
import kotlin.test.*

class TunnelOptionsTest {
    @Test
    fun legacyTunnelsRemainEnabledWithoutStartingAtLaunch() {
        val tunnel = ohMyJson.decodeFromString<Tunneling>("""{"name":"old","sourcePort":8080}""")
        assertTrue(tunnel.enabled)
        assertFalse(tunnel.autoStart)
    }

    @Test
    fun disabledStartupTunnelSurvivesSerializationAndEditing() {
        val tunnel = Tunneling(name = "test", enabled = false, autoStart = true)
        val restored = ohMyJson.decodeFromString<Tunneling>(ohMyJson.encodeToString(tunnel)).copy(sourcePort = 8081)
        assertFalse(restored.enabled)
        assertTrue(restored.autoStart)
        assertEquals(8081, restored.sourcePort)
    }
}
