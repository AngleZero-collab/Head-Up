package com.google.mediapipe.examples.poselandmarker

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

data class RegisterRequest(
    val email: String,
    val password: String,
)

data class UserResponse(
    val id: String,
    val email: String,
)

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("expires_in") val expiresIn: Int,
    @SerializedName("user_id") val userId: String,
)

data class SyncPostureRecordRequest(
    @SerializedName("user_id") val userId: String,
    @SerializedName("daily_slouch_count") val dailySlouchCount: Int,
    @SerializedName("ai_intercept_rate") val aiInterceptRate: Float,
    @SerializedName("record_date") val recordDate: String,
)

data class SyncResponse(
    val inserted: Int,
)

interface HeadUpApiService {
    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<UserResponse>

    @FormUrlEncoded
    @POST("api/v1/auth/login")
    suspend fun login(
        @Field("username") email: String,
        @Field("password") password: String,
    ): TokenResponse

    @POST("api/v1/records/sync")
    suspend fun syncRecords(
        @Header("Authorization") authorization: String,
        @Body records: List<SyncPostureRecordRequest>,
    ): Response<SyncResponse>
}

object HeadUpApiClient {
    val service: HeadUpApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl(BuildConfig.HEADUP_API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(HeadUpApiService::class.java)
    }
}
