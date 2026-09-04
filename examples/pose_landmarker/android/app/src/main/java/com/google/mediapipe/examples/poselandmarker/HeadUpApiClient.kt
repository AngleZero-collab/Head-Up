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
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
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
    @SerializedName("invite_code") val inviteCode: String? = null,
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

data class FamilyRenameRequest(
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
    @SerializedName("slouch_count") val slouchCount: Int? = null,
    @SerializedName("ai_intercept_rate") val aiInterceptRate: Float? = null,
    @SerializedName("pet_exp") val petExp: Int? = null,
    @SerializedName("report_days") val reportDays: Int? = null,
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

data class SchoolResponse(
    val id: String,
    @SerializedName("official_school_code") val officialSchoolCode: String,
    @SerializedName("localized_name") val localizedName: String,
    @SerializedName("education_stage") val educationStage: String,
    val region: String,
    val district: String,
    val source: String,
    val verified: Boolean,
)

data class EducationProfileRequest(
    @SerializedName("country_code") val countryCode: String,
    @SerializedName("school_id") val schoolId: String?,
    @SerializedName("grade_code") val gradeCode: String?,
    @SerializedName("education_stage") val educationStage: String?,
    @SerializedName("public_alias") val publicAlias: String,
    @SerializedName("leaderboard_opt_in") val leaderboardOptIn: Boolean,
    @SerializedName("parent_consent_status") val parentConsentStatus: String,
)

data class EducationProfileResponse(
    @SerializedName("user_id") val userId: String,
    @SerializedName("country_code") val countryCode: String,
    @SerializedName("school_id") val schoolId: String?,
    @SerializedName("school_name") val schoolName: String?,
    @SerializedName("grade_code") val gradeCode: String?,
    @SerializedName("education_stage") val educationStage: String?,
    @SerializedName("public_alias") val publicAlias: String,
    @SerializedName("leaderboard_opt_in") val leaderboardOptIn: Boolean,
    @SerializedName("parent_consent_status") val parentConsentStatus: String,
)

data class PostureAggregateUpload(
    @SerializedName("aggregate_id") val aggregateId: String,
    @SerializedName("record_date") val recordDate: String,
    val mode: String,
    @SerializedName("green_seconds") val greenSeconds: Long,
    @SerializedName("yellow_seconds") val yellowSeconds: Long,
    @SerializedName("red_seconds") val redSeconds: Long,
    @SerializedName("unknown_seconds") val unknownSeconds: Long,
    @SerializedName("raw_points") val rawPoints: Int,
    @SerializedName("challenge_points") val challengePoints: Int,
    @SerializedName("longest_green_streak_seconds") val longestGreenStreakSeconds: Long,
    @SerializedName("green_streak_count") val greenStreakCount: Int,
    @SerializedName("green_streak_total_seconds") val greenStreakTotalSeconds: Long,
    @SerializedName("reminder_count") val reminderCount: Int,
    @SerializedName("successful_corrections") val successfulCorrections: Int,
    @SerializedName("recovery_seconds_total") val recoverySecondsTotal: Long,
    @SerializedName("scoring_version") val scoringVersion: Int,
    @SerializedName("idempotency_key") val idempotencyKey: String,
)

data class PostureAggregateBatchRequest(val aggregates: List<PostureAggregateUpload>)

data class LeaderboardEntryResponse(
    val rank: Int,
    @SerializedName("public_alias") val publicAlias: String,
    @SerializedName("posture_score") val postureScore: Double,
    @SerializedName("challenge_points") val challengePoints: Int,
    @SerializedName("valid_days") val validDays: Int,
    @SerializedName("valid_minutes") val validMinutes: Long,
    @SerializedName("participant_count") val participantCount: Int? = null,
    @SerializedName("is_current_user") val isCurrentUser: Boolean = false,
)

data class CampusLeaderboardResponse(
    @SerializedName("entity_type") val entityType: String,
    @SerializedName("scope_type") val scopeType: String,
    val period: String,
    @SerializedName("minimum_days") val minimumDays: Int,
    @SerializedName("minimum_minutes") val minimumMinutes: Int,
    val entries: List<LeaderboardEntryResponse>,
    @SerializedName("current_user_window") val currentUserWindow: List<LeaderboardEntryResponse> = emptyList(),
    @SerializedName("current_user_qualified") val currentUserQualified: Boolean,
    @SerializedName("generated_at_ms") val generatedAtMs: Long,
)

data class GuardInsightResponse(
    @SerializedName("observation_bad_ratio") val observationBadRatio: Double?,
    @SerializedName("guarding_bad_ratio") val guardingBadRatio: Double?,
    @SerializedName("absolute_change_percentage_points") val absoluteChangePercentagePoints: Double?,
    @SerializedName("relative_improvement_percent") val relativeImprovementPercent: Double?,
    @SerializedName("reminder_correction_rate") val reminderCorrectionRate: Double?,
    @SerializedName("average_recovery_seconds") val averageRecoverySeconds: Double?,
    @SerializedName("minimum_minutes_per_mode") val minimumMinutesPerMode: Int,
    @SerializedName("is_comparable") val isComparable: Boolean,
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

    @PATCH("api/v1/family/settings")
    suspend fun renameFamily(@Body request: FamilyRenameRequest): FamilyAccountResponse

    @DELETE("api/v1/family/members/{userId}")
    suspend fun removeFamilyMember(@Path("userId") userId: String): FamilyAccountResponse

    @POST("api/v1/family/leave")
    suspend fun leaveFamily(): FamilyAccountResponse

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

    @GET("api/v1/schools")
    suspend fun schools(
        @Query("country_code") countryCode: String = "TW",
        @Query("stage") stage: String? = null,
        @Query("q") query: String = "",
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0,
    ): List<SchoolResponse>

    @GET("api/v1/profile/education")
    suspend fun educationProfile(): EducationProfileResponse

    @PUT("api/v1/profile/education")
    suspend fun updateEducationProfile(@Body request: EducationProfileRequest): EducationProfileResponse

    @POST("api/v1/posture-aggregates/batch")
    suspend fun syncPostureAggregates(@Body request: PostureAggregateBatchRequest): Response<SyncResponse>

    @GET("api/v1/leaderboards")
    suspend fun campusLeaderboard(
        @Query("entity_type") entityType: String,
        @Query("scope_type") scopeType: String,
        @Query("period") period: String,
        @Query("school_id") schoolId: String? = null,
        @Query("grade_code") gradeCode: String? = null,
        @Query("education_stage") educationStage: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): CampusLeaderboardResponse

    @GET("api/v1/insights/comparison")
    suspend fun guardInsights(
        @Query("from_date") fromDate: String,
        @Query("to_date") toDate: String,
    ): GuardInsightResponse
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
