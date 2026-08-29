package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.SearchMode
import com.example.ui.SearchViewModel
import com.example.ui.components.AiOverviewCard
import com.example.ui.components.CustomSitesView
import com.example.ui.components.GoogleWebResultView
import com.example.ui.components.HistoryFavoritesSheet
import com.example.ui.components.SearchHeader
import com.example.ui.components.WikipediaResultView
import com.example.ui.theme.AiGlowEnd
import com.example.ui.theme.AiGlowMid
import com.example.ui.theme.AiGlowStart
import java.util.Locale

@Composable
fun SearchScreen(
    viewModel: SearchViewModel = viewModel()
) {
    val context = LocalContext.current
    val query by viewModel.query.collectAsState()
    val selectedMode by viewModel.selectedMode.collectAsState()
    val siteFilters by viewModel.siteFilters.collectAsState()
    val customDomainInput by viewModel.customDomainInput.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val selectedWikiArticle by viewModel.selectedWikiArticle.collectAsState()
    val wikiAiSummary by viewModel.wikiAiSummary.collectAsState()
    val isExplainingWiki by viewModel.isExplainingWiki.collectAsState()
    val isTtsSpeaking by viewModel.isTtsSpeaking.collectAsState()
    val showHistorySheet by viewModel.showHistorySheet.collectAsState()
    val historyList by viewModel.historyList.collectAsState()
    val favoriteList by viewModel.favoriteList.collectAsState()

    // Voice recognition launcher
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                viewModel.onQueryChanged(spoken)
                viewModel.performSearch(spoken)
            }
        }
    }

    val triggerVoiceInput: () -> Unit = {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("uk", "UA").toString())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Скажіть пошуковий запит для AI...")
            }
            voiceLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Голосовий пошук недоступний на цьому пристрої", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Top Search & Mode Bar
            SearchHeader(
                query = query,
                onQueryChanged = viewModel::onQueryChanged,
                onSearch = { viewModel.performSearch() },
                selectedMode = selectedMode,
                onModeSelected = viewModel::onModeSelected,
                onVoiceClick = triggerVoiceInput,
                onOpenHistory = { viewModel.setShowHistorySheet(true) },
                historyCount = historyList.size,
                quickSuggestions = viewModel.quickTopicSuggestions,
                onSuggestionClick = { suggestion ->
                    viewModel.onQueryChanged(suggestion)
                    viewModel.performSearch(suggestion)
                }
            )

            // Main Content Area with Scroll
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Loading State with Glowing Pulse
                    if (searchState.isLoading) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(listOf(AiGlowStart, AiGlowMid, AiGlowEnd))
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(32.dp),
                                        color = Color.White,
                                        strokeWidth = 3.dp
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "AI досліджує тему «${searchState.query}»...",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = when (selectedMode) {
                                        SearchMode.AI_OVERVIEW -> "Синтезуємо дані з Вікіпедії, вебу та бази знань Gemini"
                                        SearchMode.WIKIPEDIA -> "Отримуємо оригінальні статті та формуємо вижимку"
                                        SearchMode.GOOGLE_SEARCH -> "Індексуємо веб-джерела та оптимізуємо запит"
                                        SearchMode.CUSTOM_SITES -> "Аналізуємо інформацію за обраними доменами"
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }

                    // Error Message Callout
                    if (searchState.errorMessage != null && !searchState.isLoading) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = searchState.errorMessage ?: "",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    )
                                }
                                Button(
                                    onClick = { viewModel.performSearch() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Повторити",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Mode Specific Results View
                    if (!searchState.isLoading) {
                        when (selectedMode) {
                            SearchMode.AI_OVERVIEW -> {
                                // Primary AI Overview Card
                                if (searchState.aiOverview != null) {
                                    AiOverviewCard(
                                        content = searchState.aiOverview!!,
                                        isSpeaking = isTtsSpeaking,
                                        onSpeakToggle = viewModel::speakText,
                                        onRelatedQueryClick = { related ->
                                            viewModel.onQueryChanged(related)
                                            viewModel.performSearch(related)
                                        }
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                // Supplemental Wikipedia & Google Web section previews
                                if (searchState.wikiArticles.isNotEmpty()) {
                                    WikipediaResultView(
                                        articles = searchState.wikiArticles.take(3),
                                        selectedArticle = selectedWikiArticle,
                                        onSelectArticle = viewModel::selectWikiArticle,
                                        onExplainWithAi = viewModel::explainCurrentWikiArticleWithAi,
                                        aiExplanation = wikiAiSummary,
                                        isExplaining = isExplainingWiki,
                                        onOpenUrl = { url -> viewModel.openUrlInBrowser(context, url) }
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                if (searchState.webSources.isNotEmpty()) {
                                    GoogleWebResultView(
                                        query = searchState.query,
                                        webSources = searchState.webSources,
                                        googleSearchUrl = searchState.googleSearchUrl,
                                        onOpenUrl = { url -> viewModel.openUrlInBrowser(context, url) }
                                    )
                                }
                            }

                            SearchMode.WIKIPEDIA -> {
                                WikipediaResultView(
                                    articles = searchState.wikiArticles,
                                    selectedArticle = selectedWikiArticle,
                                    onSelectArticle = viewModel::selectWikiArticle,
                                    onExplainWithAi = viewModel::explainCurrentWikiArticleWithAi,
                                    aiExplanation = wikiAiSummary,
                                    isExplaining = isExplainingWiki,
                                    onOpenUrl = { url -> viewModel.openUrlInBrowser(context, url) }
                                )

                                if (searchState.aiOverview != null) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    AiOverviewCard(
                                        content = searchState.aiOverview!!,
                                        isSpeaking = isTtsSpeaking,
                                        onSpeakToggle = viewModel::speakText,
                                        onRelatedQueryClick = { related ->
                                            viewModel.onQueryChanged(related)
                                            viewModel.performSearch(related)
                                        }
                                    )
                                }
                            }

                            SearchMode.GOOGLE_SEARCH -> {
                                GoogleWebResultView(
                                    query = searchState.query,
                                    webSources = searchState.webSources,
                                    googleSearchUrl = searchState.googleSearchUrl,
                                    onOpenUrl = { url -> viewModel.openUrlInBrowser(context, url) }
                                )

                                if (searchState.aiOverview != null) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    AiOverviewCard(
                                        content = searchState.aiOverview!!,
                                        isSpeaking = isTtsSpeaking,
                                        onSpeakToggle = viewModel::speakText,
                                        onRelatedQueryClick = { related ->
                                            viewModel.onQueryChanged(related)
                                            viewModel.performSearch(related)
                                        }
                                    )
                                }
                            }

                            SearchMode.CUSTOM_SITES -> {
                                CustomSitesView(
                                    siteFilters = siteFilters,
                                    onToggleSite = { siteId ->
                                        viewModel.toggleSiteFilter(siteId)
                                        viewModel.performSearch()
                                    },
                                    customDomainInput = customDomainInput,
                                    onCustomDomainChanged = viewModel::onCustomDomainInputChanged,
                                    onAddCustomDomain = {
                                        viewModel.addCustomDomain()
                                        viewModel.performSearch()
                                    },
                                    onRemoveCustomSite = viewModel::removeCustomSite,
                                    googleSearchUrl = searchState.googleSearchUrl,
                                    onOpenUrl = { url -> viewModel.openUrlInBrowser(context, url) }
                                )

                                if (searchState.aiOverview != null) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    AiOverviewCard(
                                        content = searchState.aiOverview!!,
                                        isSpeaking = isTtsSpeaking,
                                        onSpeakToggle = viewModel::speakText,
                                        onRelatedQueryClick = { related ->
                                            viewModel.onQueryChanged(related)
                                            viewModel.performSearch(related)
                                        }
                                    )
                                }

                                if (searchState.webSources.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    GoogleWebResultView(
                                        query = searchState.query,
                                        webSources = searchState.webSources,
                                        googleSearchUrl = searchState.googleSearchUrl,
                                        onOpenUrl = { url -> viewModel.openUrlInBrowser(context, url) }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }

        // History & Bookmarks Bottom Sheet
        if (showHistorySheet) {
            HistoryFavoritesSheet(
                historyList = historyList,
                favoriteList = favoriteList,
                onSelectQuery = { selectedQuery, modeName ->
                    val mode = try {
                        SearchMode.valueOf(modeName)
                    } catch (e: Exception) {
                        SearchMode.AI_OVERVIEW
                    }
                    viewModel.onModeSelected(mode)
                    viewModel.onQueryChanged(selectedQuery)
                    viewModel.performSearch(selectedQuery)
                },
                onToggleFavorite = viewModel::toggleFavorite,
                onDeleteItem = viewModel::deleteHistoryItem,
                onClearAll = viewModel::clearAllHistory,
                onDismiss = { viewModel.setShowHistorySheet(false) }
            )
        }
    }
}
