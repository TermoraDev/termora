package app.termora.keymgr

import org.apache.sshd.common.util.io.resource.AbstractIoResource
import java.io.ByteArrayInputStream
import java.io.InputStream

class ByteArrayIoResource(bytes: ByteArray) : AbstractIoResource<ByteArray>(ByteArray::class.java, bytes) {
    override fun openInputStream(): InputStream {
        return ByteArrayInputStream(resourceValue)
    }
}