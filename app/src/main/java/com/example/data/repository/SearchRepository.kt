package com.example.data.repository

import com.example.data.api.GeminiService
import com.example.data.api.WikipediaService
import com.example.data.db.SearchHistoryDao
import com.example.data.db.SearchHistoryEntity
import com.example.data.model.AIOverviewContent
import com.example.data.model.SearchMode
import com.example.data.model.SearchResultState
import com.example.data.model.WikipediaArticle
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import java.net.URLEncoder

class SearchRepository(
    private val geminiService: GeminiService,
    private val wikipediaService: WikipediaService,
    private val historyDao: SearchHistoryDao
) {
    val historyFlow: Flow<List<SearchHistoryEntity>> = historyDao.getAllHistory()
    val favoritesFlow: Flow<List<SearchHistoryEntity>> = historyDao.getFavorites()

    suspend fun executeSearch(
        query: String,
        mode: SearchMode,
        selectedDomains: List<String>,
        customInstruction: String? = null
    ): SearchResultState = coroutineScope {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            return@coroutineScope SearchResultState(errorMessage = "Будь ласка, введіть пошуковий запит")
        }

        val googleSearchQuery = if (selectedDomains.isNotEmpty() && mode == SearchMode.CUSTOM_SITES) {
            val siteFilters = selectedDomains.joinToString(" OR ") { "site:$it" }
            "$trimmedQuery ($siteFilters)"
        } else {
            trimmedQuery
        }
        val encodedGoogleQuery = URLEncoder.encode(googleSearchQuery, "UTF-8")
        val googleSearchUrl = "https://www.google.com/search?q=$encodedGoogleQuery"

        try {
            when (mode) {
                SearchMode.AI_OVERVIEW -> {
                    val aiOverviewDeferred = async {
                        geminiService.generateAiOverview(trimmedQuery, mode, selectedDomains, customInstruction)
                    }
                    val wikiDeferred = async {
                        wikipediaService.searchArticles(trimmedQuery, "uk")
                    }
                    val webDeferred = async {
                        geminiService.generateWebSources(trimmedQuery, selectedDomains)
                    }

                    val aiOverview = aiOverviewDeferred.await()
                    val wikiArticles = wikiDeferred.await()
                    val webSources = webDeferred.await()

                    // Save to history
                    saveToHistory(trimmedQuery, mode, aiOverview.quickAnswer, selectedDomains)

                    SearchResultState(
                        query = trimmedQuery,
                        mode = mode,
                        aiOverview = aiOverview,
                        wikiArticles = wikiArticles,
                        webSources = webSources,
                        googleSearchUrl = googleSearchUrl,
                        activeSites = selectedDomains
                    )
                }

                SearchMode.WIKIPEDIA -> {
                    val wikiDeferred = async {
                        wikipediaService.searchArticles(trimmedQuery, "uk")
                    }
                    val aiDeferred = async {
                        geminiService.generateAiOverview(
                            trimmedQuery,
                            mode,
                            listOf("wikipedia.org"),
                            "Фокусуйся на енциклопедичних фактах та контексті статті Вікіпедії"
                        )
                    }

                    val wikiArticles = wikiDeferred.await()
                    val aiOverview = aiDeferred.await()

                    saveToHistory(
                        trimmedQuery,
                        mode,
                        if (wikiArticles.isNotEmpty()) wikiArticles.first().extract.take(150) else aiOverview.quickAnswer,
                        listOf("wikipedia.org")
                    )

                    SearchResultState(
                        query = trimmedQuery,
                        mode = mode,
                        wikiArticles = wikiArticles,
                        selectedWikiArticle = wikiArticles.firstOrNull(),
                        aiOverview = aiOverview,
                        googleSearchUrl = "https://uk.wikipedia.org/wiki/Special:Search?search=${URLEncoder.encode(trimmedQuery, "UTF-8")}",
                        activeSites = listOf("wikipedia.org")
                    )
                }

                SearchMode.GOOGLE_SEARCH -> {
                    val webSourcesDeferred = async {
                        geminiService.generateWebSources(trimmedQuery, emptyList())
                    }
                    val aiOverviewDeferred = async {
                        geminiService.generateAiOverview(
                            trimmedQuery,
                            mode,
                            emptyList(),
                            "Зроби аналіз топ-джерел та запропонуй найкращі пошукові стратегії Google"
                        )
                    }

                    val webSources = webSourcesDeferred.await()
                    val aiOverview = aiOverviewDeferred.await()

                    saveToHistory(trimmedQuery, mode, aiOverview.quickAnswer, emptyList())

                    SearchResultState(
                        query = trimmedQuery,
                        mode = mode,
                        aiOverview = aiOverview,
                        webSources = webSources,
                        googleSearchUrl = googleSearchUrl,
                        activeSites = emptyList()
                    )
                }

                SearchMode.CUSTOM_SITES -> {
                    val aiOverviewDeferred = async {
                        geminiService.generateAiOverview(
                            trimmedQuery,
                            mode,
                            selectedDomains,
                            "Проаналізуй тему суворо крізь призму зазначених сайтів: ${selectedDomains.joinToString(", ")}"
                        )
                    }
                    val webSourcesDeferred = async {
                        geminiService.generateWebSources(trimmedQuery, selectedDomains)
                    }

                    val aiOverview = aiOverviewDeferred.await()
                    val webSources = webSourcesDeferred.await()

                    saveToHistory(trimmedQuery, mode, aiOverview.quickAnswer, selectedDomains)

                    SearchResultState(
                        query = trimmedQuery,
                        mode = mode,
                        aiOverview = aiOverview,
                        webSources = webSources,
                        googleSearchUrl = googleSearchUrl,
                        activeSites = selectedDomains
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            SearchResultState(
                query = trimmedQuery,
                mode = mode,
                errorMessage = "Помилка завантаження: ${e.localizedMessage}",
                googleSearchUrl = googleSearchUrl,
                activeSites = selectedDomains
            )
        }
    }

    suspend fun explainWikipediaArticleWithAi(article: WikipediaArticle): String {
        return geminiService.generateWikipediaAiSummary(article.title, article.extract)
    }

    private suspend fun saveToHistory(
        query: String,
        mode: SearchMode,
        summary: String,
        sites: List<String>
    ) {
        try {
            historyDao.insert(
                SearchHistoryEntity(
                    query = query,
                    modeName = mode.name,
                    summarySnippet = summary.take(200),
                    timestamp = System.currentTimeMillis(),
                    selectedSites = sites.joinToString(",")
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) {
        historyDao.updateFavorite(id, isFavorite)
    }

    suspend fun deleteHistory(id: Long) {
        historyDao.deleteById(id)
    }

    suspend fun clearAllHistory() {
        historyDao.clearHistory()
    }
}
