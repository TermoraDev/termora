package app.termora

import app.termora.Application.ohMyJson
import kotlin.test.*

class TunnelOptionsTest {
    @Test
    fun legacyTunnelsDoNotStartAtLaunch() {
        val tunnel = ohMyJson.decodeFromString<Tunneling>("""{"name":"old","sourcePort":8080}""")
        assertFalse(tunnel.autoStart)
    }

    @Test
    fun legacyDisabledFlagIsIgnoredAndStartupPreferenceSurvivesEditing() {
        val tunnel = ohMyJson.decodeFromString<Tunneling>("""{"name":"test","enabled":false,"autoStart":true}""")
        val restored = ohMyJson.decodeFromString<Tunneling>(ohMyJson.encodeToString(tunnel)).copy(sourcePort = 8081)
        assertTrue(restored.autoStart)
        assertEquals(8081, restored.sourcePort)
    }
}
