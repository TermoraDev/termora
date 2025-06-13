package app.termora.account

import app.termora.Application.ohMyJson
import kotlin.test.Test

class TeamTest {
    @Test
    fun test() {
        val team = ohMyJson.decodeFromString<Team>(
            ohMyJson.encodeToString(
                Team(
                    id = "test",
                    name = "test",
                    secretKey = byteArrayOf(1, 123, 123, 123, 123, 123, 123, 123, 123),
                    role = TeamRole.Member
                )
            )
        )
        println(team)
    }
}