package com.frontegg.android.services

import com.frontegg.android.models.AuthResponse
import com.frontegg.android.models.User
import com.frontegg.android.utils.ApiConstants
import com.frontegg.android.utils.CredentialKeys
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import okhttp3.Call
import okhttp3.Headers
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

open class Api(
    private var baseUrl: String,
    private var clientId: String,
    private var credentialManager: CredentialManager
) {
    private var httpClient: OkHttpClient

    init {
        this.httpClient = OkHttpClient()
    }


    private fun prepareHeaders(additionalHeaders: Map<String, String> = mapOf()): Headers {

        val headers: MutableMap<String, String> = mutableMapOf(
            Pair("Content-Type", "application/json"),
            Pair("Accept", "application/json"),
            Pair("Origin", this.baseUrl)
        )

        additionalHeaders.forEach {
            headers[it.key] = it.value
        }

        val accessToken = this.credentialManager.getOrNull(CredentialKeys.ACCESS_TOKEN)
        if (accessToken != null) {
            headers["Authorization"] = "Bearer $accessToken"
        }
        return headers.toHeaders()
    }

    private fun buildPostRequest(
        path: String,
        body: JsonObject,
        additionalHeaders: Map<String, String> = mapOf()
    ): Call {
        val url = "${this.baseUrl}/$path".toHttpUrl()
        val requestBuilder = Request.Builder()
        val bodyRequest =
            body.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
        val headers = this.prepareHeaders(additionalHeaders);

        requestBuilder.method("POST", bodyRequest)
        requestBuilder.headers(headers);
        requestBuilder.url(url)

        val request = requestBuilder.build()
        return this.httpClient.newCall(request)
    }

    private fun buildGetRequest(path: String): Call {
        val url = "$baseUrl/$path".toHttpUrl()
        val requestBuilder = Request.Builder()
        val headers = prepareHeaders();

        requestBuilder.method("GET", null)
        requestBuilder.headers(headers);
        requestBuilder.url(url)

        val request = requestBuilder.build()
        return this.httpClient.newCall(request)
    }

    @Throws(IllegalArgumentException::class, IOException::class)
    public fun me(): User? {
        val meCall = buildGetRequest(ApiConstants.me)
        val meResponse = meCall.execute()
        val tenantsCall = buildGetRequest(ApiConstants.tenants)
        val tenantsResponse = tenantsCall.execute()

        if (meResponse.isSuccessful && tenantsResponse.isSuccessful) {
            // Parsing JSON strings into JsonObject
            val gson = Gson()
            val mapType = object : TypeToken<MutableMap<String, Any>>() {}.type

            val meJsonStr = meResponse.body!!.string()
            val tenantsJsonStr = tenantsResponse.body!!.string()

            val meJson: MutableMap<String, Any> = gson.fromJson(meJsonStr, mapType)
            val tenantsJson: MutableMap<String, Any> = gson.fromJson(tenantsJsonStr, mapType)

            meJson["tenants"] = tenantsJson["tenants"] as Any
            meJson["activeTenant"] = tenantsJson["activeTenant"] as Any

            val merged = Gson().toJson(meJson)
            return Gson().fromJson(merged, User::class.java)
        }

        return null
    }

    @Throws(IllegalArgumentException::class, IOException::class)
    public fun refreshToken(refreshToken: String): AuthResponse? {


        val body = JsonObject()
        body.addProperty("grant_type", "refresh_token")
        body.addProperty("refresh_token", refreshToken)

        val call = buildPostRequest(ApiConstants.refreshToken, body)
        val response = call.execute()
        if (response.isSuccessful) {
            return Gson().fromJson(response.body!!.string(), AuthResponse::class.java)
        }
        return null
    }


    @Throws(IllegalArgumentException::class, IOException::class)
    public fun exchangeToken(
        code: String,
        redirectUrl: String,
        codeVerifier: String
    ): AuthResponse? {

        val body = JsonObject()
        body.addProperty("code", code)
        body.addProperty("redirect_uri", redirectUrl)
        body.addProperty("code_verifier", codeVerifier)
        body.addProperty("grant_type", "authorization_code")


        val call = buildPostRequest(ApiConstants.exchangeToken, body)
        val response = call.execute()
        if (response.isSuccessful) {
            return Gson().fromJson(response.body!!.string(), AuthResponse::class.java)
        }
        return null
    }

    fun logout() {
        val refreshToken = this.credentialManager.getOrNull(CredentialKeys.ACCESS_TOKEN)

        if (refreshToken != null) {
            val call = buildPostRequest(
                ApiConstants.logout, JsonObject(), mapOf(
                    Pair("fe_refresh_$clientId", refreshToken)
                )
            )
            call.execute()
        }
    }
}