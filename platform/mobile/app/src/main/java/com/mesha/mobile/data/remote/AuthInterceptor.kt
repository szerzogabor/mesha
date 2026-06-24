package com.mesha.mobile.data.remote

import com.mesha.mobile.data.local.SecureTokenStore
import okhttp3.Interceptor
import okhttp3.Response
import java.util.UUID
import javax.inject.Inject

/**
 * Attaches the bearer token to every request and a per-request correlation id (the
 * backend honors `X-Correlation-ID` for trace stitching). Release endpoints are public
 * but sending the header when present is harmless.
 */
class AuthInterceptor @Inject constructor(
    private val tokenStore: SecureTokenStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
            .header("X-Correlation-ID", UUID.randomUUID().toString())

        tokenStore.getToken()?.let { token ->
            builder.header("Authorization", "Bearer $token")
        }
        return chain.proceed(builder.build())
    }
}
