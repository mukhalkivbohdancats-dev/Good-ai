package com.example.data.api

import com.example.BuildConfig
import com.example.data.model.AIOverviewContent
import com.example.data.model.SearchMode
import com.example.data.model.WebSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val modelName = "gemini-3.5-flash"
    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent"

    suspend fun generateAiOverview(
        query: String,
        mode: SearchMode,
        selectedDomains: List<String> = emptyList(),
        customInstruction: String? = null
    ): AIOverviewContent = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext fallbackOverview(query, mode, selectedDomains)
        }

        val domainContext = if (selectedDomains.isNotEmpty()) {
            "Користувач обрав фокус на наступних сайтах/доменах: ${selectedDomains.joinToString(", ")}. Проаналізуй запит з урахуванням контенту, специфіки та експертизи саме цих ресурсів."
        } else ""

        val systemPrompt = """
            Ти — інтелектуальний пошуковий AI-асистент "Smart AI Search" нового покоління.
            Твоє завдання — надати вичерпну, структуровану, точну і зрозумілу українську відповідь на пошуковий запит користувача.
            
            Вимоги:
            1. Відповідай українською мовою (якщо користувач явно не запитує іншою).
            2. Структуруй відповідь у JSON форматі з такими полями:
               - "quickAnswer": короткий, чіткий і точний підсумок у 2-3 реченнях.
               - "keyPoints": масив із 4-6 ключових фактів, тез або кроків.
               - "detailedAnalysis": детальний, глибинний аналіз із підзаголовками, поясненнями та контекстом.
               - "sourceInsights": масив із 2-4 порад або аналізу надійності джерел (Вікіпедія, профільні сайти, наукові статті).
               - "relatedQueries": масив із 3-5 пов'язаних або уточнюючих пошукових запитів.
            $domainContext
            ${customInstruction ?: ""}
            
            Повертай ТІЛЬКИ валідний JSON без markdown огортань ```json або пояснень.
        """.trimIndent()

        val userPrompt = "Пошуковий запит: $query\nРежим пошуку: ${mode.titleUk}"

        val requestJson = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
            })
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))
            ))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.4)
                put("topP", 0.95)
                put("responseMimeType", "application/json")
            })
        }

        val url = "$baseUrl?key=$apiKey"
        val body = requestJson.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        try {
            val response = client.newCall(request).execute()
            val rawResponse = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext fallbackOverview(query, mode, selectedDomains, "Помилка API: ${response.code}")
            }

            val responseObj = JSONObject(rawResponse)
            val candidates = responseObj.optJSONArray("candidates")
            val candidate = candidates?.optJSONObject(0)
            val text = candidate?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text") ?: ""

            parseOverviewJson(text, query)
        } catch (e: Exception) {
            e.printStackTrace()
            fallbackOverview(query, mode, selectedDomains, e.localizedMessage)
        }
    }

    suspend fun generateWikipediaAiSummary(title: String, rawExtract: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "💡 **Короткий зміст (AI):** $rawExtract\n\n📌 *Порада:* Налаштуйте дійсний GEMINI_API_KEY для розширеного аналізу."
        }

        val prompt = """
            Проаналізуй статтю Вікіпедії про "$title":
            Контекст статті:
            $rawExtract
            
            Зроби вижимку:
            1. 🎯 Що це таке простою мовою (2 речення).
            2. 🔑 3 найважливіших факти.
            3. 💡 Чому це важливо або де застосовується.
            Пиши живою українською мовою з емодзі-пунктами.
        """.trimIndent()

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            ))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.3)
            })
        }

        val url = "$baseUrl?key=$apiKey"
        val body = requestJson.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder().url(url).post(body).build()

        try {
            val response = client.newCall(request).execute()
            val raw = response.body?.string() ?: ""
            val json = JSONObject(raw)
            val text = json.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")

            text ?: rawExtract
        } catch (e: Exception) {
            rawExtract
        }
    }

    suspend fun generateWebSources(query: String, domains: List<String>): List<WebSource> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext generateFallbackWebSources(query, domains)
        }

        val prompt = """
            Для пошукового запиту "$query" згенеруй список із 4-6 найбільш авторитетних та релевантних веб-джерел, які користувач може дослідити в інтернеті.
            Якщо вказані домени (${domains.joinToString(", ")}), обов'язково включи сторінки чи розділи цих сайтів.
            
            Поверни JSON масив об'єктів з полями:
            - "title": назва статті / сторінки
            - "url": пряме або пошукове посилання (наприклад, https://uk.wikipedia.org/wiki/..., https://github.com/search?q=..., https://www.google.com/search?q=...)
            - "domain": назва домену (наприклад, wikipedia.org, github.com, bbc.com)
            - "snippet": що саме користувач знайде на цьому ресурсі з цієї теми
            - "relevanceReason": чому це джерело авторитетне з цього питання
            
            Повертай тільки чистий JSON масив!
        """.trimIndent()

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            ))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.3)
                put("responseMimeType", "application/json")
            })
        }

        val url = "$baseUrl?key=$apiKey"
        val body = requestJson.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder().url(url).post(body).build()

        try {
            val response = client.newCall(request).execute()
            val raw = response.body?.string() ?: ""
            val json = JSONObject(raw)
            val text = json.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text") ?: ""

            parseWebSourcesJson(text, query, domains)
        } catch (e: Exception) {
            generateFallbackWebSources(query, domains)
        }
    }

    private fun parseOverviewJson(text: String, query: String): AIOverviewContent {
        return try {
            val clean = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val json = JSONObject(clean)
            val quick = json.optString("quickAnswer", "Короткий огляд для «$query» сформовано.")

            val keyPointsList = mutableListOf<String>()
            val keyPointsArr = json.optJSONArray("keyPoints")
            if (keyPointsArr != null) {
                for (i in 0 until keyPointsArr.length()) {
                    keyPointsList.add(keyPointsArr.getString(i))
                }
            }

            val detailed = json.optString("detailedAnalysis", "")

            val sourceInsightsList = mutableListOf<String>()
            val sourceArr = json.optJSONArray("sourceInsights")
            if (sourceArr != null) {
                for (i in 0 until sourceArr.length()) {
                    sourceInsightsList.add(sourceArr.getString(i))
                }
            }

            val relatedList = mutableListOf<String>()
            val relatedArr = json.optJSONArray("relatedQueries")
            if (relatedArr != null) {
                for (i in 0 until relatedArr.length()) {
                    relatedList.add(relatedArr.getString(i))
                }
            }

            AIOverviewContent(
                quickAnswer = quick,
                keyPoints = keyPointsList,
                detailedAnalysis = detailed,
                sourceInsights = sourceInsightsList,
                relatedQueries = relatedList
            )
        } catch (e: Exception) {
            AIOverviewContent(
                quickAnswer = text.take(200),
                detailedAnalysis = text,
                keyPoints = listOf("Аналіз запиту: $query"),
                sourceInsights = listOf("Джерело: Gemini 3.5 Flash")
            )
        }
    }

    private fun parseWebSourcesJson(text: String, query: String, domains: List<String>): List<WebSource> {
        return try {
            val clean = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val arr = JSONArray(clean)
            val list = mutableListOf<WebSource>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    WebSource(
                        title = obj.optString("title", "Веб-джерело"),
                        url = obj.optString("url", "https://www.google.com/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}"),
                        domain = obj.optString("domain", "web"),
                        snippet = obj.optString("snippet", ""),
                        relevanceReason = obj.optString("relevanceReason", "Релевантне джерело")
                    )
                )
            }
            if (list.isEmpty()) generateFallbackWebSources(query, domains) else list
        } catch (e: Exception) {
            generateFallbackWebSources(query, domains)
        }
    }

    private fun generateFallbackWebSources(query: String, domains: List<String>): List<WebSource> {
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        val sources = mutableListOf<WebSource>()

        sources.add(
            WebSource(
                title = "Вікіпедія: $query",
                url = "https://uk.wikipedia.org/wiki/Special:Search?search=$enc",
                domain = "uk.wikipedia.org",
                snippet = "Енциклопедичні статті, визначення та підтверджені наукові факти.",
                relevanceReason = "Найбільша вільна енциклопедія"
            )
        )

        sources.add(
            WebSource(
                title = "Пошук Google для «$query»",
                url = "https://www.google.com/search?q=$enc",
                domain = "google.com",
                snippet = "Повний індекс світового інтернету з останніми новинами та публікаціями.",
                relevanceReason = "Головна пошукова система"
            )
        )

        if (domains.contains("github.com") || domains.isEmpty()) {
            sources.add(
                WebSource(
                    title = "GitHub репозиторії: $query",
                    url = "https://github.com/search?q=$enc",
                    domain = "github.com",
                    snippet = "Відкритий вихідний код, проекти та технічні реалізації.",
                    relevanceReason = "Провідна платформа для розробників"
                )
            )
        }

        if (domains.contains("reddit.com") || domains.isEmpty()) {
            sources.add(
                WebSource(
                    title = "Reddit обговорення: $query",
                    url = "https://www.reddit.com/search/?q=$enc",
                    domain = "reddit.com",
                    snippet = "Живий досвід користувачів, дискусії та реальні відгуки спільноти.",
                    relevanceReason = "Глобальна дискусійна платформа"
                )
            )
        }

        return sources
    }

    private fun fallbackOverview(
        query: String,
        mode: SearchMode,
        selectedDomains: List<String>,
        errorMessage: String? = null
    ): AIOverviewContent {
        return AIOverviewContent(
            quickAnswer = "Пошуковий AI-огляд за темою «$query». Режим: ${mode.titleUk}.",
            keyPoints = listOf(
                "Запит «$query» аналізується через інтелектуальні джерела.",
                if (selectedDomains.isNotEmpty()) "Фокусні сайти: ${selectedDomains.joinToString(", ")}" else "Охоплює Вікіпедію, веб-ресурси та структурований AI-синтез.",
                "Для отримання повних відповідей увімкніть Gemini API ключ у налаштуваннях.",
                "Ви можете переглянути прямі результати Вікіпедії або відкрити запит у Google."
            ),
            detailedAnalysis = "Smart AI Search поєднує прямі дані енциклопедії Вікіпедія, індексацію пошуку Google та аналітичні можливості генеративного штучного інтелекту. Ви можете перемикати режими вгорі, щоб отримати конкретний тип інформації.${if (errorMessage != null) "\n\n⚠️ Інформація про з'єднання: $errorMessage" else ""}",
            sourceInsights = listOf(
                "Вікіпедія: швидкі факти та визначення",
                "Google: актуальні статті та офіційні веб-сайти",
                "AI: синтез, порівняння та структуризація"
            ),
            relatedQueries = listOf(
                "$query історія та походження",
                "$query як це працює",
                "$query приклади та використання",
                "$query останні новини"
            )
        )
    }
}
