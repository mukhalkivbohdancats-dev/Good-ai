package com.example.data.api

import com.example.data.model.WikipediaArticle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class WikipediaService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun searchArticles(query: String, lang: String = "uk"): List<WikipediaArticle> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
        val url = "https://$lang.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encodedQuery&format=json&utf8=1&srlimit=8"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "SmartAISearchApp/1.0 (Android; Kotlin)")
            .build()

        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)
            val searchArray = json.optJSONObject("query")?.optJSONArray("search") ?: return@withContext emptyList()

            val articles = mutableListOf<WikipediaArticle>()
            for (i in 0 until searchArray.length()) {
                val item = searchArray.getJSONObject(i)
                val title = item.optString("title")
                val pageId = item.optLong("pageid")
                val snippetHtml = item.optString("snippet")
                val cleanSnippet = snippetHtml.replace(Regex("<.*?>"), "")

                // Fetch high quality summary with thumbnail if possible
                val articleDetail = fetchSummary(title, lang)
                if (articleDetail != null) {
                    articles.add(articleDetail.copy(pageId = pageId))
                } else {
                    articles.add(
                        WikipediaArticle(
                            title = title,
                            pageId = pageId,
                            extract = cleanSnippet,
                            description = "Вікіпедія ($lang)",
                            thumbnailUrl = null,
                            pageUrl = "https://$lang.wikipedia.org/wiki/${URLEncoder.encode(title, "UTF-8")}",
                            language = lang
                        )
                    )
                }
            }

            // If Ukrainian gave 0 results and lang was 'uk', attempt English fallback
            if (articles.isEmpty() && lang == "uk") {
                return@withContext searchArticles(query, "en")
            }

            articles
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun fetchSummary(title: String, lang: String = "uk"): WikipediaArticle? = withContext(Dispatchers.IO) {
        val encodedTitle = URLEncoder.encode(title.replace(" ", "_"), "UTF-8")
        val url = "https://$lang.wikipedia.org/api/rest_v1/page/summary/$encodedTitle"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "SmartAISearchApp/1.0 (Android; Kotlin)")
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)

            val displayTitle = json.optString("title", title)
            val extract = json.optString("extract", "")
            val description = json.optString("description", "")
            val thumbnailObj = json.optJSONObject("thumbnail")
            val thumbnailUrl = thumbnailObj?.optString("source")
            val contentUrls = json.optJSONObject("content_urls")?.optJSONObject("desktop")
            val pageUrl = contentUrls?.optString("page", "https://$lang.wikipedia.org/wiki/$encodedTitle") ?: "https://$lang.wikipedia.org/wiki/$encodedTitle"

            WikipediaArticle(
                title = displayTitle,
                extract = extract,
                description = description,
                thumbnailUrl = thumbnailUrl,
                pageUrl = pageUrl,
                language = lang
            )
        } catch (e: Exception) {
            null
        }
    }
}
