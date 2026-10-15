package eu.kanade.tachiyomi.extension.en.reimanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.Response

@Source
abstract class ReiManga : HttpSource() {
    override val supportsLatest = true

    private val json = Json { ignoreUnknownKeys = true }
    private val apiBase get() = "$baseUrl/api"
    private val pageLimit = 20

    override fun popularMangaRequest(page: Int): Request = GET("$apiBase/manga/most-read?limit=$pageLimit&period=month&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage = parseMangaList(response)

    override fun latestUpdatesRequest(page: Int): Request = GET("$apiBase/manga/latest-updates?limit=$pageLimit&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaList(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$apiBase/manga/search/advanced?title=${java.net.URLEncoder.encode(query, "UTF-8")}&limit=$pageLimit&page=$page", headers)

    override fun searchMangaParse(response: Response): MangasPage = parseMangaList(response)

    private fun parseMangaList(response: Response): MangasPage {
        val root = json.parseToJsonElement(response.body!!.string())
        val entries = when (root) {
            is JsonArray -> root
            is JsonObject -> root["data"]?.jsonArray ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        val mangas = entries.mapNotNull { it.jsonObject.toManga() }.distinctBy(SManga::url)
        return MangasPage(mangas, entries.size >= pageLimit)
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiBase/manga/${manga.idFromUrl()}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val manga = json.parseToJsonElement(response.body!!.string()).jsonObject["manga"]?.jsonObject
            ?: return SManga.create()
        return (manga.toManga() ?: SManga.create()).apply {
            description = manga["description"]?.jsonPrimitive?.content
            genre = manga["genres"]?.jsonArray.orEmpty().mapNotNull {
                it.jsonObject["name"]?.jsonPrimitive?.content
            }.joinToString()
        }
    }

    override fun chapterListRequest(manga: SManga): Request = GET("$apiBase/chapters/manga/${manga.idFromUrl()}", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val entries = json.parseToJsonElement(response.body!!.string()).jsonObject["data"]?.jsonArray.orEmpty()
        return entries.mapNotNull { element ->
            val chapter = element.jsonObject
            val id = chapter["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val name = chapter["name"]?.jsonPrimitive?.content ?: "Chapter"
            SChapter.create().apply {
                url = "chapter:$id"
                this.name = name
                chapter_number = chapter["chapter_number"]?.jsonPrimitive?.content?.toFloatOrNull() ?: -1f
                date_upload = chapter["created_at"]?.jsonPrimitive?.content?.let(::parseDate) ?: 0L
            }
        }.sortedByDescending { it.chapter_number }
    }

    override fun pageListRequest(chapter: SChapter): Request = GET("$apiBase/chapters/${chapter.url.substringAfter(':')}/images", headers)

    override fun pageListParse(response: Response): List<Page> {
        val entries = json.parseToJsonElement(response.body!!.string()).jsonObject["images"]?.jsonArray.orEmpty()
        return entries.mapNotNull { element ->
            element.jsonObject["image_url"]?.jsonPrimitive?.content
        }.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    override fun imageUrlParse(response: Response): String = response.request.url.toString()

    private fun JsonObject.toManga(): SManga? {
        val id = this["id"]?.jsonPrimitive?.content ?: return null
        val title = this["title"]?.jsonPrimitive?.content?.takeIf(String::isNotBlank) ?: return null
        val cover = this["cover_url"]?.jsonPrimitive?.content?.takeIf(String::isNotBlank)
            ?: "$baseUrl/covers/$id/thumbnail.webp"
        return SManga.create().apply {
            url = "manga:$id"
            this.title = title
            thumbnail_url = cover
        }
    }

    private fun SManga.idFromUrl(): String = url.substringAfter(':').substringBefore(':')

    private fun parseDate(value: String): Long = runCatching {
        java.time.Instant.parse(value).toEpochMilli()
    }.getOrDefault(0L)
}
