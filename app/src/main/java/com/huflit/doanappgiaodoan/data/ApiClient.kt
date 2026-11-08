package com.huflit.doanappgiaodoan.data

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    // Dùng "adb reverse tcp:5090 tcp:5090" để nối cổng 5090 của máy ảo/điện thoại về máy tính,
    // nên app chỉ cần gọi 127.0.0.1 (chạy lại lệnh adb reverse mỗi lần khởi động máy ảo).
    // Cách khác: máy ảo dùng "http://10.0.2.2:5090/", điện thoại thật dùng IP Wi-Fi của máy tính.
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
