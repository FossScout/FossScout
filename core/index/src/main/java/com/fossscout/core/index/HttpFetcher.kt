package com.fossscout.core.index

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A streaming HTTP request. Conditional headers are optional. */
data class HttpRequest(
    val url: String,
    val ifNoneMatch: String? = null,
    val ifModifiedSince: String? = null,
    val allowInsecureHttp: Boolean = false,
    val accept: String = "application/json, application/java-archive, */*",
)

/**
 * The response body is an unbounded stream. Callers must close it and must not
 * load the whole index into memory.
 */
data class HttpResponse(
    val status: Int,
    val etag: String? = null,
    val lastModified: String? = null,
    val contentLength: Long? = null,
    val contentType: String? = null,
    val finalUrl: String,
    val body: InputStream? = null,
) {
    val isSuccess: Boolean get() = status in 200..299
}

class InsecureHttpException(url: String) :
    IllegalArgumentException("Plain HTTP is not allowed for $url without explicit opt-in")

interface HttpFetcher {
    suspend fun get(request: HttpRequest): HttpResponse
}

/**
 * Small streaming client built on [HttpURLConnection] so the app needs no extra
 * network dependency. Redirects are followed manually so an HTTPS repository
 * can never silently redirect to plain HTTP.
 */
class UrlConnectionHttpFetcher(
    private val connectTimeoutMillis: Int = 30_000,
    private val readTimeoutMillis: Int = 60_000,
) : HttpFetcher {
    override suspend fun get(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        var current = URI(request.url).toURL()
        var redirects = 0
        while (true) {
            requireAllowed(current.toURI(), request.allowInsecureHttp)
            val connection = (current.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
                setRequestProperty("Accept", request.accept)
                request.ifNoneMatch?.let { setRequestProperty("If-None-Match", it) }
                request.ifModifiedSince?.let {
                    setRequestProperty("If-Modified-Since", it)
                }
            }

            try {
                connection.connect()
                val status = connection.responseCode
                if (status in REDIRECT_STATUSES) {
                    val location = connection.getHeaderField("Location")
                        ?: throw IndexSyncException(
                            SyncFailureKind.NETWORK,
                            "Redirect response without a Location header",
                        )
                    if (++redirects > MAX_REDIRECTS) {
                        throw IndexSyncException(
                            SyncFailureKind.NETWORK,
                            "Too many HTTP redirects",
                        )
                    }
                    connection.disconnect()
                    current = current.toURI().resolve(location).toURL()
                    continue
                }

                val stream = if (status in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                return@withContext HttpResponse(
                    status = status,
                    etag = connection.getHeaderField("ETag"),
                    lastModified = connection.getHeaderField("Last-Modified"),
                    contentLength = connection.contentLengthLong.takeIf { it >= 0 },
                    contentType = connection.contentType,
                    finalUrl = current.toString(),
                    body = stream,
                )
            } catch (cancellation: CancellationException) {
                connection.disconnect()
                throw cancellation
            } catch (exception: IndexSyncException) {
                connection.disconnect()
                throw exception
            } catch (exception: Exception) {
                connection.disconnect()
                throw IndexSyncException(
                    kind = SyncFailureKind.NETWORK,
                    message = "Network request failed: ${exception.message.orEmpty()}",
                    cause = exception,
                )
            }
        }
        @Suppress("UNREACHABLE_CODE")
        throw IllegalStateException("Redirect loop ended unexpectedly")
    }

    private fun requireAllowed(uri: URI, allowInsecureHttp: Boolean) {
        val scheme = uri.scheme?.lowercase()
        when {
            scheme == "https" -> Unit
            scheme == "http" && allowInsecureHttp -> Unit
            scheme == "http" -> throw InsecureHttpException(uri.toString())
            else -> throw InsecureHttpException(uri.toString())
        }
    }

    companion object {
        private const val MAX_REDIRECTS = 5
        private val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)
    }
}
