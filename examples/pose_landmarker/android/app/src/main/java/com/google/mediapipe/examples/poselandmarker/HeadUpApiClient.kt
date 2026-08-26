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
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class RegisterRequest(
    val email: String,
    val password: String,
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("subscription_tier") val subscriptionTier: String = "individual",
    @SerializedName("family_name") val familyName: String? = null,
)

data class GuestLoginRequest(
    @SerializedName("device_id") val deviceId: String,
)

data class UserResponse(
    val id: String,
    val email: String,
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("subscription_tier") val subscriptionTier: String = "individual",
    val role: String = "user",
    @SerializedName("family_id") val familyId: String? = null,
)

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("expires_in") val expiresIn: Int,
    @SerializedName("user_id") val userId: String,
    @SerializedName("subscription_tier") val subscriptionTier: String = "individual",
    val role: String = "user",
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("family_id") val familyId: String? = null,
)

data class FamilyResponse(
    val id: String,
    val name: String,
    @SerializedName("invite_code") val inviteCode: String,
    @SerializedName("owner_user_id") val ownerUserId: String? = null,
)

data class FamilyMemberResponse(
    val id: String,
    val email: String,
    @SerializedName("display_name") val displayName: String? = null,
    val role: String,
    @SerializedName("subscription_tier") val subscriptionTier: String,
    @SerializedName("is_manager") val isManager: Boolean,
)

data class FamilyAccountResponse(
    @SerializedName("current_user") val currentUser: UserResponse,
    val plan: String,
    val role: String,
    @SerializedName("is_family_manager") val isFamilyManager: Boolean,
    @SerializedName("can_view_family_dashboard") val canViewFamilyDashboard: Boolean,
    val family: FamilyResponse? = null,
    val members: List<FamilyMemberResponse> = emptyList(),
)

data class FamilyCreateRequest(
    val name: String,
)

data class FamilyJoinRequest(
    @SerializedName("invite_code") val inviteCode: String,
    @SerializedName("display_name") val displayName: String? = null,
)

data class FamilyLeaderboardEntryResponse(
    val rank: Int,
    @SerializedName("user_id") val userId: String,
    @SerializedName("display_name") val displayName: String,
    val role: String,
    @SerializedName("good_posture_score") val goodPostureScore: Int,
    @SerializedName("slouch_count") val slouchCount: Int,
    @SerializedName("ai_intercept_rate") val aiInterceptRate: Float,
    @SerializedName("pet_exp") val petExp: Int,
    @SerializedName("report_days") val reportDays: Int,
    @SerializedName("latest_record_date") val latestRecordDate: String? = null,
)

data class FamilyLeaderboardResponse(
    val plan: String,
    val family: FamilyResponse? = null,
    val leaderboard: List<FamilyLeaderboardEntryResponse> = emptyList(),
)

data class FamilyMemberDashboardResponse(
    @SerializedName("user_id") val userId: String,
    @SerializedName("display_name") val displayName: String,
    val email: String,
    val role: String,
    @SerializedName("slouch_count") val slouchCount: Int,
    @SerializedName("ai_intercept_rate") val aiInterceptRate: Float,
    @SerializedName("pet_exp") val petExp: Int,
    @SerializedName("report_days") val reportDays: Int,
    @SerializedName("latest_record_date") val latestRecordDate: String? = null,
)

data class FamilyDashboardResponse(
    val plan: String,
    val family: FamilyResponse? = null,
    @SerializedName("member_count") val memberCount: Int,
    @SerializedName("total_slouch_count") val totalSlouchCount: Int,
    @SerializedName("average_ai_intercept_rate") val averageAiInterceptRate: Float,
    @SerializedName("total_pet_exp") val totalPetExp: Int,
    val members: List<FamilyMemberDashboardResponse> = emptyList(),
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

    @GET("api/v1/family/me")
    suspend fun familyAccount(): FamilyAccountResponse

    @POST("api/v1/family/create")
    suspend fun createFamily(@Body request: FamilyCreateRequest): FamilyAccountResponse

    @POST("api/v1/family/join")
    suspend fun joinFamily(@Body request: FamilyJoinRequest): FamilyAccountResponse

    @GET("api/v1/family/leaderboard")
    suspend fun familyLeaderboard(
        @Query("days") days: Int = 7,
    ): FamilyLeaderboardResponse

    @GET("api/v1/family/dashboard")
    suspend fun familyDashboard(
        @Query("days") days: Int = 7,
    ): FamilyDashboardResponse

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
