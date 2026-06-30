package com.musheer360.swiftslate.data.remote

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json

class ApiServiceFactory(
    private val json: Json
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("User-Agent", "SwiftSlate")
                .addHeader("Accept", "application/json")
                .build()
            chain.proceed(request)
        }
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
        .build()

    private val retrofits = mutableMapOf<String, Retrofit>()

    fun create(baseUrl: String): ApiService {
        val url = baseUrl.trimEnd('/')
        val retrofit = retrofits.getOrPut(url) {
            Retrofit.Builder()
                .baseUrl(if (url.endsWith("/")) url else "$url/")
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
        }
        return retrofit.create(ApiService::class.java)
    }
}
