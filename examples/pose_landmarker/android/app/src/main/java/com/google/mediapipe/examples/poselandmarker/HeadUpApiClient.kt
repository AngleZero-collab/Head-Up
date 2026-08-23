package com.google.mediapipe.examples.poselandmarker

import android.content.Context
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

data class RegisterRequest(
    val email: String,
    val password: String,
)

data class GuestLoginRequest(
    @SerializedName("device_id") val deviceId: String,
)

data class UserResponse(
    val id: String,
    val email: String,
    @SerializedName("subscription_tier") val subscriptionTier: String = "free",
    val role: String = "user",
)

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("expires_in") val expiresIn: Int,
    @SerializedName("user_id") val userId: String,
    @SerializedName("subscription_tier") val subscriptionTier: String = "free",
    val role: String = "user",
)

data class DailyReportSyncRequest(
    @SerializedName("record_date") val recordDate: String,
    @SerializedName("slouch_count") val slouchCount: Int,
    @SerializedName("ai_intercept_rate") val aiInterceptRate: Float,
    @SerializedName("pet_exp") val petExp: Int,
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

    @POST("api/v1/auth/guest")
    suspend fun guest(@Body request: GuestLoginRequest): TokenResponse

    @POST("api/v1/reports/sync")
    suspend fun syncDailyReports(
        @Body reports: List<DailyReportSyncRequest>,
    ): Response<SyncResponse>
}

object HeadUpApiClient {
    @Volatile
    private var authenticatedService: HeadUpApiService? = null

    val service: HeadUpApiService by lazy {
        createService()
    }

    fun authenticatedService(context: Context): HeadUpApiService =
        authenticatedService ?: synchronized(this) {
            authenticatedService ?: createService {
                HeadUpAuthStore.accessToken(context.applicationContext)
            }.also { authenticatedService = it }
        }

    private fun createService(tokenProvider: (() -> String?)? = null): HeadUpApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val token = tokenProvider?.invoke()
                val request = if (token.isNullOrBlank()) {
                    chain.request()
                } else {
                    chain.request()
                        .newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build()
                }
                chain.proceed(request)
            }
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.HEADUP_API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(HeadUpApiService::class.java)
    }
}
