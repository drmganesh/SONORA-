package data.video

import domain.video.OnlineVideoChannel
import domain.video.OnlineVideoItem
import domain.video.VideoHomeSectionType
import domain.video.VideoSectionState
import java.util.Locale

data class VideoRecommendationSignals(
    val watchHistory: List<OnlineVideoItem> = emptyList(),
    val subscribedChannels: List<OnlineVideoChannel> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val feedbackEntries: List<VideoRecommendationFeedbackEntity> = emptyList(),
    val countryCode: String = "IN",
    val regionalLanguageLabel: String = "All India"
)

data class PersonalizedVideoShelf(
    val id: String,
    val title: String,
    val subtitle: String,
    val items: List<OnlineVideoItem>,
    val isCarousel: Boolean = true
)

/**
 * SONORA's transparent on-device video recommendation and ranking engine.
 *
 * Clearly derives personalization solely from SONORA's own local watch history,
 * completion progress, followed channels, local likes/dislikes, search history,
 * user-selected interests, and local dismissal rules (Not Interested / Don't Recommend Channel / Hide).
 * Never claims access to YouTube's private recommendation algorithm.
 */
object VideoRecommendationEngine {

    val DEFAULT_INTEREST_TOPICS = listOf(
        "Music", "Technology", "Gaming", "Cinema", "Education",
        "Comedy", "Cricket", "Science", "Podcasts", "Travel", "Devotional", "Cooking"
    )

    fun extractDismissedVideoIds(feedback: List<VideoRecommendationFeedbackEntity>): Set<String> =
        feedback.asSequence()
            .filter {
                it.feedbackType == VideoFeedbackType.NOT_INTERESTED_VIDEO ||
                    it.feedbackType == VideoFeedbackType.HIDE_VIDEO ||
                    it.feedbackType == VideoFeedbackType.DISLIKED_VIDEO
            }
            .map { it.targetValue.trim() }
            .filter { it.isNotBlank() }
            .toSet()

    fun extractBlockedChannelKeys(feedback: List<VideoRecommendationFeedbackEntity>): Set<String> =
        feedback.asSequence()
            .filter { it.feedbackType == VideoFeedbackType.DONT_RECOMMEND_CHANNEL }
            .map { it.targetValue.trim().lowercase(Locale.US) }
            .filter { it.isNotBlank() }
            .toSet()

    fun extractUserInterests(feedback: List<VideoRecommendationFeedbackEntity>): List<String> =
        feedback.asSequence()
            .filter { it.feedbackType == VideoFeedbackType.USER_INTEREST }
            .map { it.targetValue.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()

    fun filterDismissed(
        videos: List<OnlineVideoItem>,
        feedback: List<VideoRecommendationFeedbackEntity>
    ): List<OnlineVideoItem> {
        if (videos.isEmpty() || feedback.isEmpty()) return videos
        val dismissedIds = extractDismissedVideoIds(feedback)
        val blockedChannels = extractBlockedChannelKeys(feedback)
        if (dismissedIds.isEmpty() && blockedChannels.isEmpty()) return videos
        return videos.filter { video ->
            video.videoId !in dismissedIds &&
                video.channelName.trim().lowercase(Locale.US) !in blockedChannels &&
                (video.channelUrl?.trim()?.lowercase(Locale.US) !in blockedChannels)
        }
    }

    /**
     * Ranks candidate videos transparently using local watch history, completion ratio,
     * followed channels, search tokens, liked videos, and selected interests.
     */
    fun rankVideos(
        candidates: List<OnlineVideoItem>,
        signals: VideoRecommendationSignals,
        excludeVideoIds: Set<String> = emptySet()
    ): List<OnlineVideoItem> {
        val filtered = filterDismissed(candidates, signals.feedbackEntries)
            .filter { it.videoId !in excludeVideoIds }
            .distinctBy { it.videoId }
        if (filtered.isEmpty()) return emptyList()

        val followedNames = signals.subscribedChannels
            .map { it.name.trim().lowercase(Locale.US) }
            .filter { it.isNotBlank() }
            .toSet()
        val likedVideoIds = signals.feedbackEntries
            .filter { it.feedbackType == VideoFeedbackType.LIKED_VIDEO }
            .map { it.targetValue }
            .toSet()
        val interests = extractUserInterests(signals.feedbackEntries)
            .map { it.lowercase(Locale.US) }

        val channelAffinity = HashMap<String, Double>()
        val keywordAffinity = HashMap<String, Double>()
        val alreadyFinishedIds = HashSet<String>()

        signals.watchHistory.forEachIndexed { idx, watched ->
            val recencyWeight = (1.0 - (idx * 0.03)).coerceAtLeast(0.2)
            val completionRatio = if (watched.durationSeconds > 0L) {
                (watched.lastPositionMs.toDouble() / (watched.durationSeconds * 1000.0)).coerceIn(0.0, 1.0)
            } else {
                0.5
            }
            if (completionRatio >= 0.88) {
                alreadyFinishedIds.add(watched.videoId)
            }
            val engagement = (0.6 + completionRatio * 1.4) * recencyWeight +
                if (watched.videoId in likedVideoIds) 1.5 else 0.0
            val chKey = watched.channelName.trim().lowercase(Locale.US)
            if (chKey.isNotBlank()) {
                channelAffinity[chKey] = (channelAffinity[chKey] ?: 0.0) + engagement * 3.0
            }
            tokenize(watched.title).forEach { token ->
                keywordAffinity[token] = (keywordAffinity[token] ?: 0.0) + engagement
            }
        }

        signals.searchHistory.take(12).forEachIndexed { idx, query ->
            val weight = (1.5 - idx * 0.1).coerceAtLeast(0.4)
            tokenize(query).forEach { token ->
                keywordAffinity[token] = (keywordAffinity[token] ?: 0.0) + weight * 1.6
            }
        }

        interests.forEach { interest ->
            keywordAffinity[interest] = (keywordAffinity[interest] ?: 0.0) + 3.0
        }

        val hasPersonalSignals = channelAffinity.isNotEmpty() || keywordAffinity.isNotEmpty() || followedNames.isNotEmpty()

        return filtered.sortedByDescending { item ->
            var score = 0.0
            val chKey = item.channelName.trim().lowercase(Locale.US)
            if (chKey in followedNames) score += 8.0
            score += (channelAffinity[chKey] ?: 0.0).coerceAtMost(12.0)

            val titleTokens = tokenize(item.title)
            titleTokens.forEach { token ->
                score += (keywordAffinity[token] ?: 0.0).coerceAtMost(4.0)
            }

            // Slight freshness / popularity boost when signals are sparse (trending fallback)
            val views = item.viewCount ?: 0L
            if (views > 0L) {
                score += kotlin.math.ln(views.toDouble() + 1.0) * (if (hasPersonalSignals) 0.25 else 0.75)
            }
            if (item.videoId in alreadyFinishedIds) {
                score -= 4.0 // Deprioritize already-finished videos in recommendation feeds
            }
            score
        }
    }

    /**
     * Builds deduplicated YouTube-style personalized video shelves for SONORA's Online Video Home.
     */
    fun buildPersonalizedHomeShelves(
        rawSections: Map<VideoHomeSectionType, VideoSectionState>,
        signals: VideoRecommendationSignals
    ): List<PersonalizedVideoShelf> {
        fun successItems(type: VideoHomeSectionType): List<OnlineVideoItem> =
            filterDismissed((rawSections[type] as? VideoSectionState.Success)?.items.orEmpty(), signals.feedbackEntries)

        val allPool = rawSections.values
            .filterIsInstance<VideoSectionState.Success>()
            .flatMap { it.items }
            .let { filterDismissed(it, signals.feedbackEntries) }
            .distinctBy { it.videoId }

        val shelves = mutableListOf<PersonalizedVideoShelf>()
        val usedAcrossShelves = HashSet<String>()

        // 1. Continue Watching (in-progress local watch history)
        val continueWatching = filterDismissed(signals.watchHistory, signals.feedbackEntries)
            .filter { item ->
                val durMs = item.durationMs
                item.lastPositionMs >= 5_000L && (durMs <= 0L || item.lastPositionMs < (durMs * 0.92).toLong())
            }
            .take(12)
        if (continueWatching.isNotEmpty()) {
            shelves.add(
                PersonalizedVideoShelf(
                    id = "continue_watching",
                    title = "Continue Watching",
                    subtitle = "Pick up where you left off in ${domain.branding.AppBranding.APP_NAME}",
                    items = continueWatching,
                    isCarousel = true
                )
            )
            continueWatching.forEach { usedAcrossShelves.add(it.videoId) }
        }

        // 2. Recommended for You (transparently ranked by SONORA's local engine; falls back to trending)
        val recommendedRanked = rankVideos(allPool, signals, excludeVideoIds = emptySet()).take(14)
        if (recommendedRanked.isNotEmpty()) {
            val subtitle = when {
                signals.watchHistory.isNotEmpty() -> "Personalized by ${domain.branding.AppBranding.APP_NAME} from your watch history & interests"
                signals.subscribedChannels.isNotEmpty() -> "Based on channels you follow in ${domain.branding.AppBranding.APP_NAME}"
                else -> "Popular in ${if (signals.countryCode == "IN") "India" else signals.countryCode} • Watch videos to personalize"
            }
            shelves.add(
                PersonalizedVideoShelf(
                    id = "recommended_for_you",
                    title = "Recommended for You",
                    subtitle = subtitle,
                    items = recommendedRanked,
                    isCarousel = false
                )
            )
            recommendedRanked.take(6).forEach { usedAcrossShelves.add(it.videoId) }
        }

        // 3. Trending in India / Popular in Your Region
        val trendingItems = (successItems(VideoHomeSectionType.TRENDING) + successItems(VideoHomeSectionType.POPULAR_INDIAN))
            .distinctBy { it.videoId }
            .filter { it.videoId !in usedAcrossShelves }
            .ifEmpty { successItems(VideoHomeSectionType.TRENDING) }
            .take(14)
        if (trendingItems.isNotEmpty()) {
            val isIndia = signals.countryCode.equals("IN", ignoreCase = true)
            shelves.add(
                PersonalizedVideoShelf(
                    id = if (isIndia) "trending_in_india" else "popular_in_region",
                    title = if (isIndia) "Trending in India" else "Popular in Your Region",
                    subtitle = "Live trending videos right now",
                    items = trendingItems,
                    isCarousel = true
                )
            )
            trendingItems.take(6).forEach { usedAcrossShelves.add(it.videoId) }
        }

        // 4. Similar to What You Watch
        val latestWatched = signals.watchHistory.firstOrNull()
        if (latestWatched != null) {
            val seedTokens = tokenize(latestWatched.title)
            val similar = allPool
                .filter { it.videoId != latestWatched.videoId && it.videoId !in usedAcrossShelves }
                .filter { candidate ->
                    candidate.channelName.equals(latestWatched.channelName, ignoreCase = true) ||
                        tokenize(candidate.title).any { it in seedTokens }
                }
                .take(12)
            if (similar.isNotEmpty()) {
                shelves.add(
                    PersonalizedVideoShelf(
                        id = "similar_to_watched",
                        title = "Similar to What You Watch",
                        subtitle = "Inspired by ${latestWatched.channelName}",
                        items = similar,
                        isCarousel = true
                    )
                )
                similar.forEach { usedAcrossShelves.add(it.videoId) }
            }
        }

        // 5. Recently Uploaded
        val recentlyUploaded = successItems(VideoHomeSectionType.RECENTLY_UPLOADED)
            .filter { it.videoId !in usedAcrossShelves }
            .ifEmpty { successItems(VideoHomeSectionType.RECENTLY_UPLOADED) }
            .take(12)
        if (recentlyUploaded.isNotEmpty()) {
            shelves.add(
                PersonalizedVideoShelf(
                    id = "recently_uploaded",
                    title = "Recently Uploaded",
                    subtitle = "Fresh uploads across ${signals.regionalLanguageLabel}",
                    items = recentlyUploaded,
                    isCarousel = true
                )
            )
            recentlyUploaded.take(6).forEach { usedAcrossShelves.add(it.videoId) }
        }

        // 6. New Creators (channels the user hasn't watched or followed yet)
        val knownChannels = (signals.watchHistory.map { it.channelName } +
            signals.subscribedChannels.map { it.name })
            .map { it.trim().lowercase(Locale.US) }
            .toSet()
        val newCreators = allPool
            .filter { it.videoId !in usedAcrossShelves }
            .filter { it.channelName.trim().lowercase(Locale.US) !in knownChannels }
            .distinctBy { it.channelName.trim().lowercase(Locale.US) }
            .take(12)
        if (newCreators.isNotEmpty()) {
            shelves.add(
                PersonalizedVideoShelf(
                    id = "new_creators",
                    title = "New Creators",
                    subtitle = "Fresh voices outside your usual channels",
                    items = newCreators,
                    isCarousel = true
                )
            )
        }

        return shelves
    }

    /**
     * Splits related videos below the player into "More From This Channel" and ranked "Related Videos",
     * applying dismissal filters and deduplicating against the active video.
     */
    fun partitionPlayerRecommendations(
        activeVideo: OnlineVideoItem,
        rawRelated: List<OnlineVideoItem>,
        signals: VideoRecommendationSignals
    ): Pair<List<OnlineVideoItem>, List<OnlineVideoItem>> {
        val cleaned = filterDismissed(rawRelated, signals.feedbackEntries)
            .filter { it.videoId != activeVideo.videoId }
            .distinctBy { it.videoId }
        val moreFromChannel = cleaned.filter {
            it.channelName.equals(activeVideo.channelName, ignoreCase = true) ||
                (!activeVideo.channelUrl.isNullOrBlank() && it.channelUrl.equals(activeVideo.channelUrl, ignoreCase = true))
        }
        val moreIds = moreFromChannel.map { it.videoId }.toSet()
        val otherRelated = rankVideos(
            candidates = cleaned.filter { it.videoId !in moreIds },
            signals = signals,
            excludeVideoIds = setOf(activeVideo.videoId)
        )
        return moreFromChannel to otherRelated
    }

    private fun tokenize(text: String): Set<String> {
        if (text.isBlank()) return emptySet()
        val stopWords = setOf(
            "the", "and", "for", "with", "official", "video", "full", "song", "hd", "4k",
            "new", "best", "top", "2024", "2025", "2026", "feat", "ft", "from", "this", "that"
        )
        return text.lowercase(Locale.US)
            .split(Regex("[^a-z0-9\\u0900-\\u0D7F]+"))
            .asSequence()
            .filter { it.length >= 3 && it !in stopWords }
            .toSet()
    }
}
