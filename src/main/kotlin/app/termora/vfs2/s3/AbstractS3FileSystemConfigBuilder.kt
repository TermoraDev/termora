package app.termora.vfs2.s3

import org.apache.commons.lang3.StringUtils
import org.apache.commons.vfs2.FileSystemConfigBuilder
import org.apache.commons.vfs2.FileSystemOptions

abstract class AbstractS3FileSystemConfigBuilder : FileSystemConfigBuilder() {
    fun getEndpoint(options: FileSystemOptions): String {
        return getParam(options, "endpoint")
    }

    fun setEndpoint(options: FileSystemOptions, endpoint: String) {
        setParam(options, "endpoint", endpoint)
    }

    fun setAccessKey(options: FileSystemOptions, accessId: String) {
        setParam(options, "accessId", accessId)
    }

    fun getAccessKey(options: FileSystemOptions): String {
        return getParam(options, "accessId")
    }

    fun setSecretKey(options: FileSystemOptions, secretKey: String) {
        setParam(options, "secretKey", secretKey)
    }

    fun getSecretKey(options: FileSystemOptions): String {
        return getParam(options, "secretKey")
    }

    fun setRegion(options: FileSystemOptions, region: String) {
        setParam(options, "region", region)
    }

    fun getRegion(options: FileSystemOptions): String {
        return getParam(options, "region")
    }

    fun setDelimiter(options: FileSystemOptions, delimiter: String) {
        setParam(options, "delimiter", delimiter)
    }

    fun getDelimiter(options: FileSystemOptions): String {
        return StringUtils.defaultIfBlank(getParam(options, "delimiter"), "/")
    }

}