package app.termora.cli

import app.termora.cli.guard.DangerousCommandFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DangerousCommandFilterTest {
    private val filter = DangerousCommandFilter()

    @Test
    fun `放行普通命令`() {
        assertNull(filter.match("ls -la /var/log"))
        assertNull(filter.match("df -h"))
        assertNull(filter.match("systemctl status nginx"))
    }

    @Test
    fun `拦截 rm -rf 根目录`() {
        assertEquals("rm-rf-root", filter.match("rm -rf /"))
        assertEquals("rm-rf-root", filter.match("rm -fr /"))
        assertEquals("rm-rf-root", filter.match("rm -Rf /"))
        assertEquals("rm-rf-root", filter.match("rm -rfv /"))
        assertEquals("rm-rf-root", filter.match("rm -fr ~"))
        assertEquals("rm-rf-root", filter.match("sudo rm  -rf   /*"))
    }

    @Test
    fun `拦截 mkfs 与 dd 写设备`() {
        assertEquals("mkfs", filter.match("mkfs.ext4 /dev/sdb1"))
        assertEquals("dd-to-device", filter.match("dd if=/dev/zero of=/dev/sda"))
    }

    @Test
    fun `拦截关机重启与 fork bomb`() {
        assertEquals("shutdown", filter.match("shutdown -h now"))
        assertEquals("shutdown", filter.match("reboot"))
        assertEquals("shutdown", filter.match("sudo reboot"))
        assertEquals("fork-bomb", filter.match(":(){ :|:& };:"))
    }

    @Test
    fun `关机重启关键字作为子串时放行`() {
        assertNull(filter.match("cat halt.txt"))
        assertNull(filter.match("echo reboot now"))
        assertNull(filter.match("grep poweroff /var/log/x"))
    }

    @Test
    fun `拦截重定向到设备与 chmod chown 根目录`() {
        assertEquals("redirect-to-device", filter.match("echo x > /dev/sda"))
        assertEquals("chmod-root", filter.match("chmod -R 777 /"))
        assertEquals("chown-root", filter.match("chown -R user /"))
    }
}
