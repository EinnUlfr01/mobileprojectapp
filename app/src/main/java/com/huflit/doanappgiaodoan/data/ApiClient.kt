package com.huflit.doanappgiaodoan.data

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    // App gọi 127.0.0.1:5090; cổng này được nối về API trên máy tính bằng "adb reverse tcp:5090 tcp:5090".
    // Mỗi lần bấm Run trong Android Studio, Gradle tự chạy lệnh này (task adbReverse trong app/build.gradle.kts).
    const val BASE_URL = "http://127.0.0.1:5090/"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val token = Session.token
            val req = if (token.isNullOrEmpty()) chain.request()
            else chain.request().newBuilder().header("Authorization", "Bearer $token").build()
            chain.proceed(req)
        }
        .build()

    val api: ApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(http)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)
}
