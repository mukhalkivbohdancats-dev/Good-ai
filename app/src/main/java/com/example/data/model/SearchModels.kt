package com.example.data.model

enum class SearchMode(
    val titleUk: String,
    val titleEn: String,
    val iconName: String,
    val description: String
) {
    AI_OVERVIEW(
        titleUk = "AI Огляд",
        titleEn = "AI Overview",
        iconName = "AutoAwesome",
        description = "Глибокий розумний синтез, структурована відповідь та аналіз від Gemini AI"
    ),
    WIKIPEDIA(
        titleUk = "Вікіпедія",
        titleEn = "Wikipedia",
        iconName = "MenuBook",
        description = "Офіційні статті Вікіпедії з живою вибіркою, ілюстраціями та AI-поясненням"
    ),
    GOOGLE_SEARCH(
        titleUk = "Google Пошук",
        titleEn = "Google Search",
        iconName = "Search",
        description = "Веб-джерела, перевірка фактів, оптимізовані пошукові запити та посилання"
    ),
    CUSTOM_SITES(
        titleUk = "Сайти на вибір",
        titleEn = "Specific Sites",
        iconName = "FilterAlt",
        description = "Фокусування AI на обраних доменах (Reddit, GitHub, Вікіпедія або власний сайт)"
    )
}

data class SiteFilter(
    val id: String,
    val name: String,
    val domain: String,
    val iconEmoji: String,
    val isSelected: Boolean = false
)

data class WikipediaArticle(
    val title: String,
    val pageId: Long = 0,
    val extract: String = "",
    val description: String = "",
    val thumbnailUrl: String? = null,
    val pageUrl: String = "",
    val language: String = "uk",
    val aiSummary: String? = null
)

data class WebSource(
    val title: String,
    val url: String,
    val domain: String,
    val snippet: String,
    val relevanceReason: String = ""
)

data class AIOverviewContent(
    val quickAnswer: String,
    val keyPoints: List<String> = emptyList(),
    val detailedAnalysis: String = "",
    val sourceInsights: List<String> = emptyList(),
    val relatedQueries: List<String> = emptyList(),
    val siteTargetedNotes: Map<String, String> = emptyMap()
)

data class SearchResultState(
    val query: String = "",
    val mode: SearchMode = SearchMode.AI_OVERVIEW,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val aiOverview: AIOverviewContent? = null,
    val wikiArticles: List<WikipediaArticle> = emptyList(),
    val selectedWikiArticle: WikipediaArticle? = null,
    val webSources: List<WebSource> = emptyList(),
    val googleSearchUrl: String = "",
    val activeSites: List<String> = emptyList(),
    val aiRawResponse: String? = null
)
