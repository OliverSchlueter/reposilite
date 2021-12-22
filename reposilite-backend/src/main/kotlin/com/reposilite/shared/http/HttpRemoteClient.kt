package com.reposilite.shared.http

import com.google.api.client.http.GenericUrl
import com.google.api.client.http.HttpMethods
import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpResponse
import com.google.api.client.http.javanet.NetHttpTransport
import com.reposilite.journalist.Channel
import com.reposilite.journalist.Journalist
import com.reposilite.shared.fs.DocumentInfo
import com.reposilite.shared.fs.FileDetails
import com.reposilite.shared.fs.UNKNOWN_LENGTH
import com.reposilite.shared.fs.getExtension
import com.reposilite.shared.fs.getSimpleNameFromUri
import com.reposilite.web.http.ErrorResponse
import com.reposilite.web.http.errorResponse
import io.javalin.http.ContentType
import io.javalin.http.HttpCode.BAD_REQUEST
import io.javalin.http.HttpCode.NOT_ACCEPTABLE
import panda.std.Result
import panda.std.asSuccess
import java.io.InputStream
import java.net.Proxy

interface RemoteClientProvider {

    fun createClient(journalist: Journalist, proxy: Proxy?): RemoteClient

}

object HttpRemoteClientProvider : RemoteClientProvider {

    override fun createClient(journalist: Journalist, proxy: Proxy?): RemoteClient =
        HttpRemoteClient(journalist, proxy)

}

class HttpRemoteClient(private val journalist: Journalist, proxy: Proxy?) : RemoteClient {

    private val requestFactory = NetHttpTransport.Builder()
        .setProxy(proxy)
        .build()
        .createRequestFactory()

    override fun head(uri: String, credentials: String?, connectTimeout: Int, readTimeout: Int): Result<FileDetails, ErrorResponse> =
        createRequest(HttpMethods.HEAD, uri, credentials, connectTimeout, readTimeout)
            .execute {
                val headers = it.headers

                // Nexus can send misleading for client content-length of chunked responses
                // ~ https://github.com/dzikoysk/reposilite/issues/549
                val contentLength =
                    if ("gzip" == headers.contentEncoding)
                        UNKNOWN_LENGTH // remove content-length header
                    else
                        headers.contentLength

                val contentType = headers.contentType
                    ?.let { ContentType.getContentType(it) }
                    ?: ContentType.getContentTypeByExtension(uri.getExtension())
                    ?: ContentType.APPLICATION_OCTET_STREAM

                DocumentInfo(
                    uri.getSimpleNameFromUri(),
                    contentType,
                    contentLength
                ).asSuccess()
            }

    override fun get(uri: String, credentials: String?, connectTimeout: Int, readTimeout: Int): Result<InputStream, ErrorResponse> =
        createRequest(HttpMethods.GET, uri, credentials, connectTimeout, readTimeout)
            .execute { it.content.asSuccess() }

    private fun createRequest(method: String, uri: String, credentials: String?, connectTimeout: Int, readTimeout: Int): HttpRequest {
        val request = requestFactory.buildRequest(method, GenericUrl(uri), null)
        request.throwExceptionOnExecuteError = false
        request.connectTimeout = connectTimeout * 1000
        request.readTimeout = readTimeout * 1000
        request.authenticateWith(credentials)
        return request
    }

    private fun <R> HttpRequest.execute(block: (HttpResponse) -> Result<R, ErrorResponse>): Result<R, ErrorResponse> =
        try {
            val response = this.execute()
            when {
                response.contentType == ContentType.HTML -> errorResponse(NOT_ACCEPTABLE, "Illegal file type (${response.contentType})")
                response.isSuccessStatusCode.not() -> errorResponse(NOT_ACCEPTABLE, "Unsuccessful request (${response.statusCode})")
                else -> block(response)
            }
        } catch (exception: Exception) {
            createErrorResponse(this.url.toString(), exception)
        }

    private fun HttpRequest.authenticateWith(credentials: String?): HttpRequest = also {
        if (credentials != null) {
            val (username, password) = credentials.split(":", limit = 2)
            it.headers.setBasicAuthentication(username, password)
        }
    }

    private fun <V> createErrorResponse(uri: String, exception: Exception): Result<V, ErrorResponse> {
        journalist.logger.debug("Cannot get $uri")
        journalist.logger.exception(Channel.DEBUG, exception)
        return errorResponse(BAD_REQUEST, "An error of type ${exception.javaClass} happened: ${exception.message}")
    }

}