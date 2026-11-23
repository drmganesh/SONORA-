package data.video

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object VideoFeedbackType {
    const val NOT_INTERESTED_VIDEO = "NOT_INTERESTED_VIDEO"
    const val DONT_RECOMMEND_CHANNEL = "DONT_RECOMMEND_CHANNEL"
    const val HIDE_VIDEO = "HIDE_VIDEO"
    const val USER_INTEREST = "USER_INTEREST"
    const val LIKED_VIDEO = "LIKED_VIDEO"
    const val DISLIKED_VIDEO = "DISLIKED_VIDEO"
}

/**
 * Locally persisted recommendation preference & dismissal record for SONORA's
 * transparent on-device Online Video recommendation engine.
 */
@Entity(
    tableName = "video_recommendation_feedback",
    indices = [Index(value = ["feedbackType"])]
)
data class VideoRecommendationFeedbackEntity(
    @PrimaryKey
    val feedbackKey: String,
    val feedbackType: String,
    val targetValue: String,
    val createdAt: Long = System.currentTimeMillis()
)
