package eu.kanade.tachiyomi.extension.all.mangaplus

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.decodeHex
import keiyoushi.utils.parseAsProto
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.UUID

@Source
abstract class MangaPlus : HttpSource() {
    override val supportsLatest = true

    private val apiLanguage: String
        get() = when (lang) {
            "en" -> "eng"
            "es" -> "esp"
            "fr" -> "fra"
            "id" -> "ind"
            "pt-BR" -> "ptb"
            "ru" -> "rus"
            "th" -> "tha"
            "vi" -> "vie"
            "de" -> "deu"
            else -> "eng"
        }

    private val languageCode: Int
        get() = when (lang) {
            "en" -> 0
            "es" -> 1
            "fr" -> 2
            "id" -> 3
            "pt-BR" -> 4
            "ru" -> 5
            "th" -> 6
            "vi" -> 9
            "de" -> 7
            else -> 0
        }

    private val apiHost = "jumpg-webapi.tokyo-cdn.com"
    private val sessionToken = UUID.randomUUID().toString()

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val prepared = if (request.url.host == apiHost) {
                request.newBuilder().header("SESSION-TOKEN", sessionToken).build()
            } else {
                request
            }
            val response = chain.proceed(prepared)
            decryptPage(response)
        }
        .rateLimit(1) { it.host == apiHost }
        .build()

    override fun popularMangaRequest(page: Int): Request = apiGet("title_list/allV2")

    override fun popularMangaParse(response: Response): MangasPage {
        val titles = response.parseAsProto<MpEnvelope>().success?.allTitles?.groups.orEmpty()
            .flatMap { it.titles }
            .filterByLanguage()
        return MangasPage(titles.map { it.toManga() }, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = apiGet("title_list/updated")

    override fun latestUpdatesParse(response: Response): MangasPage {
        val success = response.parseAsProto<MpEnvelope>().success
        val updates = success?.updatedTitles?.groups.orEmpty()
            .flatMap { it.titles }
            .mapNotNull { it.title }
            .filterByLanguage()
        if (updates.isNotEmpty()) {
            return MangasPage(updates.map { it.toManga() }.distinctBy(SManga::url), false)
        }

        val fallback = client.newCall(apiGet("web/web_homeV4")).execute().use {
            it.parseAsProto<MpEnvelope>().success?.home?.groups.orEmpty()
                .flatMap { group -> group.titleGroups }
                .flatMap { group -> group.titles }
                .mapNotNull { it.title }
                .filterByLanguage()
        }
        return MangasPage(fallback.map { it.toManga() }.distinctBy(SManga::url), false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = apiGet("title_list/allV2", query.trim())

    override fun searchMangaParse(response: Response): MangasPage {
        val query = response.request.url.queryParameter("q").orEmpty()
        val titles = response.parseAsProto<MpEnvelope>().success?.allTitles?.groups.orEmpty()
            .flatMap { it.titles }.filterByLanguage().filter {
                query.isBlank() || it.name.contains(query, true) || it.author.orEmpty().contains(query, true)
            }
        return MangasPage(titles.map { it.toManga() }, false)
    }

    override fun mangaDetailsRequest(manga: SManga): Request = apiGet("title_detailV3?title_id=${manga.id()}")

    override fun mangaDetailsParse(response: Response): SManga {
        val detail = response.parseAsProto<MpEnvelope>().success?.detail ?: return SManga.create()
        return detail.toManga()
    }

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val detail = response.parseAsProto<MpEnvelope>().success?.detail ?: return emptyList()
        return detail.chapters.asSequence().filter { it.subtitle != null }.map { chapter ->
            SChapter.create().apply {
                url = "chapter:${chapter.id}"
                name = listOf(chapter.name, chapter.subtitle).filterNotNull().joinToString(" - ")
                chapter_number = chapter.name.substringAfter('#').toFloatOrNull() ?: -1f
                date_upload = chapter.timestamp * 1000L
                scanlator = "MANGA Plus"
            }
        }.sortedByDescending { it.chapter_number }.toList()
    }

    override fun pageListRequest(chapter: SChapter): Request = apiGet(
        "manga_viewer_v3?chapter_id=${chapter.url.substringAfter(':')}&split=yes&img_quality=super_high",
    )

    override fun pageListParse(response: Response): List<Page> {
        val viewer = response.parseAsProto<MpEnvelope>().success?.viewer ?: return emptyList()
        val token = viewer.token.orEmpty()
        return viewer.pages.mapNotNull { it.page }.mapIndexed { index, page ->
            val key = page.key?.let { "#$it" }.orEmpty()
            Page(index, url = token, imageUrl = page.url + key)
        }
    }

    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headers.newBuilder().set("Plus-Vw-Token", page.url).build())

    override fun imageUrlParse(response: Response): String = response.request.url.toString()

    private fun apiGet(path: String, query: String? = null): Request {
        val url = "$API_URL/$path".toHttpUrl().newBuilder()
            .addQueryParameter("lang", apiLanguage)
            .addQueryParameter("clang", apiLanguage)
        if (!query.isNullOrBlank()) url.addQueryParameter("q", query)
        return GET(url.build(), headers)
    }

    private fun decryptPage(response: Response): Response {
        val keyText = response.request.url.fragment ?: return response
        val key = runCatching { keyText.decodeHex() }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return response
        val encrypted = response.body?.bytes() ?: return response
        val decrypted = ByteArray(encrypted.size) { index ->
            (encrypted[index].toInt() xor (key[index % key.size].toInt() and 0xFF)).toByte()
        }
        val mediaType = response.header("Content-Type") ?: "image/jpeg"
        return response.newBuilder().body(decrypted.toResponseBody(mediaType.toMediaType())).build()
    }

    private fun MpTitle.toManga(): SManga = SManga.create().apply {
        title = name
        author = this@toManga.author?.replace(" / ", ", ")
        artist = author
        thumbnail_url = cover
        url = "title:$id"
    }

    private fun MpTitleDetail.toManga(): SManga = title.toManga().apply {
        description = listOf(this@toManga.description, this@toManga.schedule).filter(String::isNotBlank).joinToString("\n\n")
        genre = genres.map { it.name }.filter(String::isNotBlank).joinToString(", ")
        status = when {
            statusText.contains("complete", true) -> SManga.COMPLETED
            statusText.contains("hiatus", true) -> SManga.ON_HIATUS
            else -> SManga.ONGOING
        }
    }

    private fun List<MpTitle>.filterByLanguage(): List<MpTitle> = filter { it.id != 0 && it.language == languageCode }.distinctBy { it.id }
    private fun SManga.id(): String = url.substringAfter(':')
}

private const val API_URL = "https://jumpg-webapi.tokyo-cdn.com/api"
