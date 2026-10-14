package ir.cheghadr.core.data.provider

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import ir.cheghadr.core.data.network.ProviderHttpException
import ir.cheghadr.core.model.ProviderId

/**
 * Small Ktor helpers shared by all providers. Timeouts are configured once on
 * the shared client (see di/DataModules.kt); this helper centralizes
 * status-code and transport error handling.
 */
internal suspend fun HttpClient.getText(
    providerId: ProviderId,
    url: String,
    block: HttpRequestBuilder.() -> Unit = {},
): String {
    val response = try {
        get(url) { block() }
    } catch (e: Exception) {
        throw ProviderHttpException(providerId, -1, "network: ${e.javaClass.simpleName}: ${e.message}")
    }
    if (response.status != HttpStatusCode.OK) {
        throw ProviderHttpException(providerId, response.status.value, "HTTP ${response.status.value}")
    }
    return response.bodyAsText()
}

internal fun HttpRequestBuilder.bearerHeader(key: String?) {
    if (!key.isNullOrBlank()) header("Authorization", "Bearer $key")
}

internal fun HttpRequestBuilder.apiKeyHeader(name: String, key: String?) {
    if (!key.isNullOrBlank()) header(name, key)
}
