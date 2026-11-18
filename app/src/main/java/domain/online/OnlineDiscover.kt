package domain.online

enum class DiscoverSectionType(
    val title: String,
    val subtitle: String
) {
    TRENDING_MUSIC(
        title = "Trending Music",
        subtitle = "Live music charts & trending streams"
    ),
    POPULAR_SONGS(
        title = "Popular Songs",
        subtitle = "Top searched tracks right now"
    ),
    NEW_RELEASES(
        title = "New Releases",
        subtitle = "Fresh albums, singles & drops"
    ),
    MUSIC_RECOMMENDATIONS(
        title = "Music Recommendations",
        subtitle = "Curated mixes & listening picks"
    ),
    POPULAR_ARTISTS(
        title = "Popular Artists",
        subtitle = "Featured creators & music channels"
    ),
    ONLINE_PLAYLISTS(
        title = "Online Playlists",
        subtitle = "Curated streaming mixes & playlists"
    )
}

enum class DiscoverMusicLanguage(
    val id: String,
    val label: String,
    val searchKeyword: String
) {
    ALL("all", "All", ""),
    HINDI("hindi", "Hindi", "Hindi Bollywood"),
    ODIA("odia", "Odia", "Odia"),
    TAMIL("tamil", "Tamil", "Tamil"),
    TELUGU("telugu", "Telugu", "Telugu"),
    MALAYALAM("malayalam", "Malayalam", "Malayalam"),
    KANNADA("kannada", "Kannada", "Kannada"),
    BENGALI("bengali", "Bengali", "Bengali"),
    PUNJABI("punjabi", "Punjabi", "Punjabi"),
    MARATHI("marathi", "Marathi", "Marathi"),
    BOLLYWOOD("bollywood", "Bollywood", "Bollywood film soundtrack"),
    INDIAN_INDIE("indian_indie", "Indian Indie", "Indian indie independent"),
    REGIONAL_OST("regional_ost", "Regional OST", "Indian regional film soundtrack");

    companion object {
        val DEFAULT = ALL

        fun fromId(id: String?): DiscoverMusicLanguage {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
        }
    }
}

data class OnlineCountryOption(
    val code: String,
    val name: String
)

data class OnlineLanguageOption(
    val code: String,
    val name: String
)

object OnlineRegionCatalog {
    const val DEFAULT_COUNTRY_CODE = "IN"
    const val DEFAULT_LANGUAGE_CODE = "en"

    val supportedCountries: List<OnlineCountryOption> = listOf(
        OnlineCountryOption("IN", "India"),
        OnlineCountryOption("US", "United States"),
        OnlineCountryOption("GB", "United Kingdom"),
        OnlineCountryOption("CA", "Canada"),
        OnlineCountryOption("AU", "Australia"),
        OnlineCountryOption("JP", "Japan"),
        OnlineCountryOption("KR", "South Korea"),
        OnlineCountryOption("DE", "Germany"),
        OnlineCountryOption("FR", "France"),
        OnlineCountryOption("BR", "Brazil")
    )

    val supportedLanguages: List<OnlineLanguageOption> = listOf(
        OnlineLanguageOption("en", "English"),
        OnlineLanguageOption("hi", "Hindi"),
        OnlineLanguageOption("or", "Odia"),
        OnlineLanguageOption("ta", "Tamil"),
        OnlineLanguageOption("te", "Telugu"),
        OnlineLanguageOption("ml", "Malayalam"),
        OnlineLanguageOption("kn", "Kannada"),
        OnlineLanguageOption("bn", "Bengali"),
        OnlineLanguageOption("pa", "Punjabi"),
        OnlineLanguageOption("mr", "Marathi")
    )
}

sealed interface DiscoverSectionState {
    data object Loading : DiscoverSectionState
    data class Success(
        val items: List<OnlineTrack>,
        val sourceLabel: String = "",
        val nextPageToken: String? = null,
        val hasMore: Boolean = false,
        val isLoadingMore: Boolean = false
    ) : DiscoverSectionState

    data class Empty(val message: String) : DiscoverSectionState
    data class Error(
        val kind: OnlineErrorKind,
        val message: String,
        val isRetryable: Boolean = true
    ) : DiscoverSectionState
}

fun defaultDiscoverSectionsState(): Map<DiscoverSectionType, DiscoverSectionState> =
    DiscoverSectionType.entries.associateWith { DiscoverSectionState.Loading }
