package app.termora.plugins.ftp

import app.termora.AuthenticationType
import app.termora.DynamicIcon
import app.termora.Icons
import app.termora.ProxyType
import app.termora.protocol.PathHandler
import app.termora.protocol.PathHandlerRequest
import app.termora.protocol.TransferProtocolProvider
import org.apache.commons.lang3.StringUtils
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.pool2.BasePooledObjectFactory
import org.apache.commons.pool2.PooledObject
import org.apache.commons.pool2.impl.DefaultPooledObject
import org.apache.commons.pool2.impl.GenericObjectPool
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.net.Proxy
import java.nio.charset.Charset
import java.time.Duration


class FTPProtocolProvider private constructor() : TransferProtocolProvider {


    companion object {
        private val log = LoggerFactory.getLogger(FTPProtocolProvider::class.java)

        val instance = FTPProtocolProvider()
        const val PROTOCOL = "FTP"
    }

    override fun getProtocol(): String {
        return PROTOCOL
    }

    override fun getIcon(width: Int, height: Int): DynamicIcon {
        return Icons.ftp
    }

    override fun createPathHandler(requester: PathHandlerRequest): PathHandler {
        val host = requester.host

        val config = GenericObjectPoolConfig<FTPClient>().apply {
            maxTotal = 12
            // 与 transfer 最大传输量匹配
            maxIdle = 6
            minIdle = 1
            testOnBorrow = false
            testWhileIdle = true
            // 检测空闲对象线程每次运行时检测的空闲对象的数量
            timeBetweenEvictionRuns = Duration.ofSeconds(30)
            // 连接空闲的最小时间，达到此值后空闲链接将会被移除，且保留 minIdle 个空闲连接数
            softMinEvictableIdleDuration = Duration.ofSeconds(30)
            // 连接的最小空闲时间，达到此值后该空闲连接可能会被移除（还需看是否已达最大空闲连接数）
            minEvictableIdleDuration = Duration.ofMinutes(3)
        }

        val ftpClientPool = GenericObjectPool(object : BasePooledObjectFactory<FTPClient>() {
            override fun create(): FTPClient {
                val client = FTPClient()
                client.charset = Charset.forName(host.options.encoding)
                client.controlEncoding = client.charset.name()
                client.connect(host.host, host.port)
                if (client.isConnected.not()) {
                    throw IllegalStateException("FTP client is not connected")
                }

                if (host.proxy.type == ProxyType.HTTP) {
                    client.proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(host.proxy.host, host.proxy.port))
                } else if (host.proxy.type == ProxyType.SOCKS5) {
                    client.proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(host.proxy.host, host.proxy.port))
                }

                val password = if (host.authentication.type == AuthenticationType.Password)
                    host.authentication.password else StringUtils.EMPTY
                if (client.login(host.username, password).not()) {
                    throw IllegalStateException("Incorrect account or password")
                }

                if (host.options.extras["passive"] == FTPHostOptionsPane.PassiveMode.Remote.name) {
                    client.enterRemotePassiveMode()
                } else {
                    client.enterLocalPassiveMode()
                }

                client.listHiddenFiles = true

                return client
            }

            override fun wrap(obj: FTPClient): PooledObject<FTPClient> {
                return DefaultPooledObject(obj)
            }

            override fun validateObject(p: PooledObject<FTPClient>): Boolean {
                val ftp = p.`object`
                return ftp.isConnected.not() && ftp.sendNoOp()
            }

            override fun destroyObject(p: PooledObject<FTPClient>) {
                try {
                    p.`object`.disconnect()
                } catch (e: Exception) {
                    if (log.isWarnEnabled) {
                        log.warn(e.message, e)
                    }
                }
            }

        }, config)

        val defaultPath = host.options.sftpDefaultDirectory
        val fs = FTPFileSystem(ftpClientPool)
        return PathHandler(fs, fs.getPath(defaultPath))
    }


}