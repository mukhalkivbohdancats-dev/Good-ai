package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiService
import com.example.data.api.WikipediaService
import com.example.data.db.AppDatabase
import com.example.data.db.SearchHistoryEntity
import com.example.data.model.SearchMode
import com.example.data.model.SearchResultState
import com.example.data.model.SiteFilter
import com.example.data.model.WikipediaArticle
import com.example.data.repository.SearchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class SearchViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    private val repository: SearchRepository
    private var tts: TextToSpeech? = null
    private val _isTtsSpeaking = MutableStateFlow(false)
    val isTtsSpeaking: StateFlow<Boolean> = _isTtsSpeaking.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selectedMode = MutableStateFlow(SearchMode.AI_OVERVIEW)
    val selectedMode: StateFlow<SearchMode> = _selectedMode.asStateFlow()

    private val defaultPresetSites = listOf(
        SiteFilter("wiki", "Вікіпедія", "uk.wikipedia.org", "📚", true),
        SiteFilter("google", "Google Джерела", "google.com", "🔍", true),
        SiteFilter("reddit", "Reddit", "reddit.com", "💬", false),
        SiteFilter("github", "GitHub", "github.com", "💻", false),
        SiteFilter("stackoverflow", "StackOverflow", "stackoverflow.com", "🛠️", false),
        SiteFilter("medium", "Medium", "medium.com", "📝", false),
        SiteFilter("bbc", "BBC News", "bbc.com", "📰", false),
        SiteFilter("nauka", "Наука UA", "nauka.ua", "🔬", false),
        SiteFilter("hackernews", "Hacker News", "news.ycombinator.com", "⚡", false)
    )

    private val _siteFilters = MutableStateFlow<List<SiteFilter>>(defaultPresetSites)
    val siteFilters: StateFlow<List<SiteFilter>> = _siteFilters.asStateFlow()

    private val _customDomainInput = MutableStateFlow("")
    val customDomainInput: StateFlow<String> = _customDomainInput.asStateFlow()

    private val _searchState = MutableStateFlow(SearchResultState())
    val searchState: StateFlow<SearchResultState> = _searchState.asStateFlow()

    private val _selectedWikiArticle = MutableStateFlow<WikipediaArticle?>(null)
    val selectedWikiArticle: StateFlow<WikipediaArticle?> = _selectedWikiArticle.asStateFlow()

    private val _wikiAiSummary = MutableStateFlow<String?>(null)
    val wikiAiSummary: StateFlow<String?> = _wikiAiSummary.asStateFlow()

    private val _isExplainingWiki = MutableStateFlow(false)
    val isExplainingWiki: StateFlow<Boolean> = _isExplainingWiki.asStateFlow()

    private val _showHistorySheet = MutableStateFlow(false)
    val showHistorySheet: StateFlow<Boolean> = _showHistorySheet.asStateFlow()

    val historyList: StateFlow<List<SearchHistoryEntity>>
    val favoriteList: StateFlow<List<SearchHistoryEntity>>

    val quickTopicSuggestions = listOf(
        "Квантові комп'ютери як працюють",
        "Штучний інтелект Gemini 3.5",
        "Історія створення Вікіпедії",
        "Космічний телескоп Джеймс Вебб",
        "Як вивчити програмування з нуля",
        "Блокчейн та смарт-контракти",
        "Відновлювана енергетика майбутнього"
    )

    init {
        val db = AppDatabase.getDatabase(application)
        repository = SearchRepository(
            geminiService = GeminiService(),
            wikipediaService = WikipediaService(),
            historyDao = db.searchHistoryDao()
        )

        historyList = repository.historyFlow.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        favoriteList = repository.favoritesFlow.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        try {
            tts = TextToSpeech(application, this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Initial default search to display smart AI research overview immediately
        performSearch("Штучний інтелект та майбутнє технологій")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("uk", "UA"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
        }
    }

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
    }

    fun onModeSelected(mode: SearchMode) {
        _selectedMode.value = mode
        if (_query.value.isNotBlank()) {
            performSearch(_query.value)
        }
    }

    fun toggleSiteFilter(siteId: String) {
        _siteFilters.value = _siteFilters.value.map {
            if (it.id == siteId) it.copy(isSelected = !it.isSelected) else it
        }
    }

    fun onCustomDomainInputChanged(domain: String) {
        _customDomainInput.value = domain
    }

    fun addCustomDomain() {
        val raw = _customDomainInput.value.trim().lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .removeSuffix("/")

        if (raw.isNotBlank() && !_siteFilters.value.any { it.domain.equals(raw, ignoreCase = true) }) {
            val newSite = SiteFilter(
                id = "custom_${System.currentTimeMillis()}",
                name = raw,
                domain = raw,
                iconEmoji = "🌐",
                isSelected = true
            )
            _siteFilters.value = _siteFilters.value + newSite
            _customDomainInput.value = ""
        }
    }

    fun removeCustomSite(siteId: String) {
        _siteFilters.value = _siteFilters.value.filterNot { it.id == siteId }
    }

    fun performSearch(searchQuery: String? = null) {
        val queryToSearch = (searchQuery ?: _query.value).trim()
        if (queryToSearch.isBlank()) return

        _query.value = queryToSearch
        stopTts()

        val selectedDomains = _siteFilters.value
            .filter { it.isSelected }
            .map { it.domain }

        viewModelScope.launch {
            _searchState.value = _searchState.value.copy(
                query = queryToSearch,
                mode = _selectedMode.value,
                isLoading = true,
                errorMessage = null
            )
            _selectedWikiArticle.value = null
            _wikiAiSummary.value = null

            val result = repository.executeSearch(
                query = queryToSearch,
                mode = _selectedMode.value,
                selectedDomains = selectedDomains
            )

            _searchState.value = result.copy(isLoading = false)
            if (result.wikiArticles.isNotEmpty()) {
                _selectedWikiArticle.value = result.wikiArticles.first()
            }
        }
    }

    fun selectWikiArticle(article: WikipediaArticle) {
        _selectedWikiArticle.value = article
        _wikiAiSummary.value = null
    }

    fun explainCurrentWikiArticleWithAi() {
        val article = _selectedWikiArticle.value ?: return
        viewModelScope.launch {
            _isExplainingWiki.value = true
            val aiSummary = repository.explainWikipediaArticleWithAi(article)
            _wikiAiSummary.value = aiSummary
            _isExplainingWiki.value = false
        }
    }

    fun speakText(text: String) {
        if (_isTtsSpeaking.value) {
            stopTts()
            return
        }
        val cleanText = text.replace(Regex("[#*_`\\[\\]]"), "")
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "AI_SPEECH_ID")
        _isTtsSpeaking.value = true
    }

    fun stopTts() {
        tts?.stop()
        _isTtsSpeaking.value = false
    }

    fun openUrlInBrowser(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun shareText(context: Context, text: String, title: String = "Поділитися пошуковим результатом") {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, text)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(intent, title).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleFavorite(item: SearchHistoryEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(item.id, !item.isFavorite)
        }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            repository.deleteHistory(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
        }
    }

    fun setShowHistorySheet(show: Boolean) {
        _showHistorySheet.value = show
    }

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
    }
}
