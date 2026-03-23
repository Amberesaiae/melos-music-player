@file:Suppress("kotlin:S6290","kotlin:S6701")

package com.amberesaiae.melos.core.player

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.TransferListener
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.EOFException
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit

/**
 * Custom DataSource implementation for streaming remote audio.
 * Supports HTTP range requests for seeking and integrates with OkHttp.
 */
class StreamingDataSource(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val cache: DataSource? = null
) : DataSource {
    companion object {
        private const val TAG = "StreamingDataSource"
        private val SUPPORTED_PROTOCOLS = setOf("http", "https")
    }

    private var responseCall: Call? = null
    private var responseStream: java.io.InputStream? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = -1
    private var bytesRead: Long = 0
    private var position: Long = 0
    private var listener: TransferListener? = null

    override fun addListener(listener: TransferListener) {
        this.listener = listener
    }

    override fun open(dataSpec: DataSource.DataSpec): Long {
        uri = dataSpec.uri
        position = dataSpec.position
        bytesRemaining = if (dataSpec.length != -1L) dataSpec.length else -1
        bytesRead = 0

        val uriString = uri.toString()
        if (!isHttpProtocol(uriString)) {
            throw StreamingDataSourceException("Unsupported protocol: ${uri.scheme}")
        }

        try {
            val request = buildRequest(dataSpec)
            responseCall = okHttpClient.newCall(request)
            val response = responseCall!!.execute()

            if (!response.isSuccessful) {
                val statusCode = response.code
                if (statusCode == HttpURLConnection.HTTP_NOT_FOUND ||
                    statusCode == HttpURLConnection.HTTP_FORBIDDEN ||
                    statusCode == HttpURLConnection.HTTP_UNAUTHORIZED
                ) {
                    throw StreamingDataSourceException("HTTP error: $statusCode", statusCode)
                }
            }

            val responseBody = response.body
            if (responseBody == null) {
                throw StreamingDataSourceException("Null response body", response.code)
            }

            responseStream = responseBody.byteStream()

            if (bytesRemaining == -1L) {
                bytesRemaining = responseBody.contentLength()
            }

            listener?.onTransferStart(dataSpec)

        } catch (e: IOException) {
            throw StreamingDataSourceException(e.message ?: "Network error", 0, e)
        }

        return bytesRemaining
    }

    private fun isHttpProtocol(uriString: String): Boolean {
        val scheme = uri?.scheme ?: return false
        return SUPPORTED_PROTOCOLS.contains(scheme.lowercase())
    }

    private fun buildRequest(dataSpec: DataSource.DataSpec): Request {
        val requestBuilder = Request.Builder()
            .url(uri.toString())

        if (dataSpec.position != 0L || dataSpec.length != -1L) {
            val from = dataSpec.position
            val to = if (dataSpec.length != -1L) {
                dataSpec.position + dataSpec.length - 1
            } else ""
            requestBuilder.addHeader("Range", "bytes=$from-$to")
        }

        requestBuilder.cacheControl(CacheControl.Builder().noStore().build())
        requestBuilder.addHeader("User-Agent", "MelosPlayer/1.0")

        for ((key, value) in dataSpec.httpRequestHeaders) {
            requestBuilder.addHeader(key, value)
        }

        return requestBuilder.build()
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        try {
            if (bytesRemaining == 0L) {
                return -1
            }

            val readStream = responseStream
            if (readStream == null) {
                throw StreamingDataSourceException("Response stream is null", 0)
            }

            var bytesToRead = readLength
            if (bytesRemaining != -1L) {
                val remaining = bytesRemaining
                if (remaining < bytesToRead) {
                    bytesToRead = remaining.toInt()
                }
            }

            var bytesReadCount = 0
            while (bytesReadCount < bytesToRead) {
                val read = readStream.read(buffer, offset + bytesReadCount, bytesToRead - bytesReadCount)
                if (read == -1) {
                    break
                }
                bytesReadCount += read
            }

            if (bytesReadCount == -1) {
                return -1
            }

            bytesRead += bytesReadCount
            bytesRemaining -= bytesReadCount

            listener?.onBytesTransferred(bytesReadCount)

            return bytesReadCount

        } catch (e: IOException) {
            if (e is EOFException) {
                return -1
            }
            throw StreamingDataSourceException(e.message ?: "Read error", 0, e)
        }
    }

    override fun close() {
        try {
            responseStream?.close()
        } catch (e: IOException) {
            // Ignore
        } finally {
            responseStream = null
            responseCall?.cancel()
            responseCall = null
            listener?.onTransferEnd()
        }
    }

    override fun getUri(): Uri? = uri

    class StreamingEventListener : EventListener() {
        var lastConnectTimeMs: Long = -1
        var lastDnsTimeMs: Long = -1
        var lastConnectEndTimeMs: Long = -1

        override fun connectStart(call: Call, inetSocketAddress: java.net.InetSocketAddress, proxy: java.net.Proxy) {
            lastConnectTimeMs = System.currentTimeMillis()
        }

        override fun connectEnd(call: Call, inetSocketAddress: java.net.InetSocketAddress, proxy: java.net.Proxy) {
            lastConnectEndTimeMs = System.currentTimeMillis()
        }

        override fun dnsEnd(call: Call, domainName: String, inetSocketAddressList: List<java.net.InetSocketAddress>) {
            lastDnsTimeMs = System.currentTimeMillis()
        }
    }
}

class StreamingDataSourceException(
    message: String?,
    val statusCode: Int = 0,
    cause: Throwable? = null
) : IOException(message, cause)
