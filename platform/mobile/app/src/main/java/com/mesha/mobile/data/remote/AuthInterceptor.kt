package com.mesha.mobile.data.remote

import com.clerk.api.Clerk
import com.clerk.api.network.model.error.ClerkErrorResponse
import com.clerk.api.network.model.token.TokenResource
import com.clerk.api.network.serialization.ClerkResult
import com.clerk.api.session.fetchToken
import com.mesha.mobile.ClerkBootstrap
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.util.UUID
import javax.inject.Inject

/**
 * Attaches the bearer token to every request and a per-request correlation id (the
 * backend honors `X-Correlation-ID` for trace stitching). Release endpoints are public
 * but sending the header when present is harmless.
 *
 * The token is fetched fresh from the Clerk session on each request rather than read
 * from storage: Clerk JWTs are short-lived (~1 min) and the SDK transparently caches /
 * refreshes them, so this is cheap when valid and correct when expired.
 */
class AuthInterceptor @Inject constructor() : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
            .header("X-Correlation-ID", UUID.randomUUID().toString())

        if (ClerkBootstrap.isReady) Clerk.session?.let { session ->
            val result: ClerkResult<TokenResource, ClerkErrorResponse> =
                runBlocking { session.fetchToken() }
            when (result) {
                is ClerkResult.Success<TokenResource> -> builder.header("Authorization", "Bearer ${result.value.jwt}")
                is ClerkResult.Failure<ClerkErrorResponse> -> Unit
            }
        }
        return chain.proceed(builder.build())
    }
}
