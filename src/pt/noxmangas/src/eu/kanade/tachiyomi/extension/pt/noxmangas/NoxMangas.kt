package eu.kanade.tachiyomi.extension.pt.noxmangas

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.net.URLEncoder

@Source
abstract class NoxMangas : HttpSource() {

    override val supportsLatest = true

    private val apiUrl = baseUrl
    private val apiHost = apiUrl.toHttpUrl().host

    override val client = network.client.newBuilder()
        .addInterceptor { chain ->
            val req = chain.request()

            if (req.url.host == apiHost) {
                var response = chain.proceed(req)
                if (response.code == 401) {
                    response.close()

                    val newReq = req.newBuilder().apply {
                        headers(getApiHeaders(req.url.encodedPath, refresh = true))
                    }.build()
                    response = chain.proceed(newReq)
                }

                // Keep the signed API as the primary path. If the site blocks
                // only the API, retry the corresponding public HTML page.
                if (response.code == 403) {
                    val fallbackUrl = req.header(HTML_FALLBACK_HEADER)
                    if (fallbackUrl != null) {
                        response.close()
                        return@addInterceptor chain.proceed(
                            req.newBuilder()
                                .removeHeader(HTML_FALLBACK_HEADER)
                                .url(fallbackUrl)
                                .header("Accept", "text/html,application/xhtml+xml")
                                .build(),
                        )
                    }
                }
                return@addInterceptor response
            }
            chain.proceed(req)
        }
        .build()

    private var cachedSlot: String = ""
    private var cachedToken: String = ""
    private var cachedSignature: String = ""

    private val signerJsUrl = "$apiUrl/_nix/signer.js"
    private val signerJsRegex = Regex("const z=\\[(.*?)\\],")

    private fun refreshAuthValues(endpoint: String) {
        val response = client.newCall(GET(signerJsUrl)).execute()
        val body = response.body?.string() ?: error("Failed to fetch signer.js")
        response.close()

        val match = signerJsRegex.find(body)
        val zArr = match!!.groupValues[1].split(",").map { it.trim().removeSurrounding("\"") }

        fun reverse(s: String) = s.reversed()
        fun rJoin(arr: List<String>) = arr.joinToString("") { reverse(it) }

        val slot = reverse(zArr[0])
        val token = rJoin(zArr.slice(4 until zArr.size))
        val k = rJoin(zArr.slice(1..3))

        val payload = "GET|$endpoint|$SITE_ID|$slot|$token|$k"
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(payload.toByteArray())
        val sig = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(hash)

        cachedSlot = slot
        cachedToken = token
        cachedSignature = sig
    }

    private fun getApiHeaders(endpoint: String, refresh: Boolean = false): Headers {
        if (refresh || cachedSlot.isEmpty()) refreshAuthValues(endpoint)

        // The API validates the request against real browser fingerprint headers
        // (sec-ch-ua / sec-fetch-*) in addition to the X-Web-* signature.
        return headersBuilder()
            .add("Accept", "*/*")
            .add("Accept-Language", "pt-BR,pt;q=0.9")
            .add("Cache-Control", "no-cache")
            .add("Origin", baseUrl)
            .add("Referer", "$baseUrl/")
            .add("sec-ch-ua", "\"Not/A)Brand\";v=\"8\", \"Chromium\";v=\"126\"")
            .add("sec-ch-ua-mobile", "?0")
            .add("sec-ch-ua-platform", "\"Windows\"")
            .add("sec-fetch-dest", "empty")
            .add("sec-fetch-mode", "cors")
            .add("sec-fetch-site", "same-origin")
            .add("X-Web-Token", cachedToken)
            .add("X-Web-Signature", cachedSignature)
            .add("X-Web-Slot", cachedSlot)
            .add("X-Site-ID", SITE_ID)
            .build()
    }

    private fun apiRequest(endpoint: String, path: String, htmlFallback: String? = null): Request {
        return try {
            GET("$apiUrl/api/v1$path", getApiHeaders(endpoint).newBuilder().apply {
                htmlFallback?.let { add(HTML_FALLBACK_HEADER, it) }
            }.build())
        } catch (_: Exception) {
            // signer.js can itself be blocked with 403, before OkHttp gets an
            // opportunity to run the API interceptor. Use only the public page
            // supplied by this source as the fallback; never bypass a challenge.
            if (htmlFallback == null) throw IllegalStateException("NoxMangas API authentication failed", _)
            GET(htmlFallback, headersBuilder()
                .add("Accept", "text/html,application/xhtml+xml")
                .add("Referer", "$baseUrl/")
                .build())
        }
    }

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request {
        val endpoint = "/api/v1/comics"
        val url = "/comics?page=$page&per_page=24&sort=popular"
        return apiRequest(endpoint, url, "$baseUrl/popular?page=$page")
    }

    override fun popularMangaParse(response: Response): MangasPage {
        if (response.isHtml()) return response.parseHtmlMangas()
        val dto = response.parseAs<PaginatedComicsDto>()
        return MangasPage(dto.comics.map { it.toSManga() }, dto.hasNextPage)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        val endpoint = "/api/v1/comics"
        val url = "/comics?page=$page&per_page=24&sort=latest"
        return apiRequest(endpoint, url, "$baseUrl/new?page=$page")
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val endpoint = "/api/v1/comics/search"
            val url = "/comics/search?q=$query&page=$page"
            return apiRequest(endpoint, url, "$baseUrl/search?query=${URLEncoder.encode(query, "UTF-8")}&page=$page")
        }

        val endpoint = "/api/v1/comics"
        val url = "/comics?page=$page&per_page=24".toHttpUrl().newBuilder().apply {
            val sortFilter = filters.firstInstanceOrNull<SortFilter>()
            if (sortFilter != null) {
                val sortMode = when (sortFilter.state?.index) {
                    0 -> "latest"
                    1 -> "popular"
                    2 -> "rating"
                    3 -> "name"
                    4 -> "chapters"
                    5 -> "oldest"
                    else -> "latest"
                }
                addQueryParameter("sort", sortMode)
            } else {
                addQueryParameter("sort", "latest")
            }

            filters.firstInstanceOrNull<TypeFilter>()?.toUriPart()?.takeIf { it.isNotEmpty() }?.let { addQueryParameter("type", it) }
            filters.firstInstanceOrNull<StatusFilter>()?.toUriPart()?.takeIf { it.isNotEmpty() }?.let { addQueryParameter("status", it) }
            filters.firstInstanceOrNull<DemographicFilter>()?.toUriPart()?.takeIf { it.isNotEmpty() }?.let { addQueryParameter("demographic", it) }
            filters.firstInstanceOrNull<YearFilter>()?.state?.takeIf { it.isNotEmpty() }?.let { addQueryParameter("year", it) }
        }.build().toString()

        return apiRequest(endpoint, url, "$baseUrl/search")
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Details ==============================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url
        val endpoint = "/api/v1/comics/slug/$slug"
        val url = "/comics/slug/$slug"
        return apiRequest(endpoint, url, "$baseUrl/manga/$slug")
    }

    override fun mangaDetailsParse(response: Response): SManga {
        if (response.isHtml()) return response.parseHtmlMangaDetails()
        val root = response.parseAs<JsonElement>()
        val objectRoot = root.jsonObject
        val payload = objectRoot["comic"] ?: objectRoot["data"] ?: root
        return payload.parseAs<ComicDto>().toSManga()
    }

    // ============================= Chapters ==============================

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url.substringBeforeLast("#")

    override fun chapterListRequest(manga: SManga): Request {
        val slug = manga.url
        return chapterListRequestPaginated(slug, 1)
    }

    private fun chapterListRequestPaginated(slug: String, page: Int): Request {
        val endpoint = "/api/v1/comics/slug/$slug/chapters"
        val url = "/comics/slug/$slug/chapters?page=$page&per_page=100&sort=newest"
        return apiRequest(endpoint, url, "$baseUrl/manga/$slug")
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        if (response.isHtml()) return response.parseHtmlChapters()
        var res = response
        var dto = res.parseAs<PaginatedChaptersDto>()
        val chapters = mutableListOf<SChapter>()

        val mangaSlug = res.request.url.encodedPath
            .substringAfter("/slug/")
            .substringBefore("/")

        chapters.addAll(dto.chapters.map { it.toSChapter(mangaSlug) })

        var page = 1
        while (dto.hasNextPage) {
            page++
            val nextReq = chapterListRequestPaginated(mangaSlug, page)
            res = client.newCall(nextReq).execute()
            dto = res.parseAs<PaginatedChaptersDto>()
            chapters.addAll(dto.chapters.map { it.toSChapter(mangaSlug) })
        }

        return chapters
    }

    // =============================== Pages ===============================

    override fun pageListRequest(chapter: SChapter): Request {
        val id = chapter.url.substringAfterLast("#")
        val endpoint = "/api/v1/chapters/$id"
        val url = "/chapters/$id?skip_view=true"
        val htmlFallback = chapter.url.substringBefore("#").let { "$baseUrl$it" }
        return apiRequest(endpoint, url, htmlFallback)
    }

    override fun pageListParse(response: Response): List<Page> = if (response.isHtml()) {
        response.parseHtmlPages()
    } else {
        response.parseAs<PagesDto>().toPageList()
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    override fun getFilterList() = FilterList(
        SortFilter(),
        TypeFilter(),
        StatusFilter(),
        DemographicFilter(),
        YearFilter(),
    )

    companion object {
        private const val SITE_ID = "00000000-0000-0000-0000-000000000003"
        private const val HTML_FALLBACK_HEADER = "X-Nox-HTML-Fallback"
    }
}

private fun Response.isHtml(): Boolean = header("Content-Type")?.contains("html", ignoreCase = true) == true

private fun Response.parseHtmlMangas(): MangasPage {
    val document = Jsoup.parse(body!!.string(), request.url.toString())
    val mangas = document.select("a[href^=/manga/]")
        .mapNotNull { element ->
            val url = element.attr("href").substringBefore("?").trimEnd('/')
            if (url == "/manga" || url.isEmpty()) return@mapNotNull null
            val title = element.selectFirst("img[alt]")?.attr("alt")?.takeIf { it.isNotBlank() }
                ?: element.selectFirst("h2, h3, h4")?.text()
                ?: element.text()
            if (title.isBlank()) return@mapNotNull null
            SManga.create().apply {
                this.url = url
                this.title = title.trim()
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
            }
        }
        .distinctBy(SManga::url)
    return MangasPage(mangas, false)
}

private fun Response.parseHtmlMangaDetails(): SManga {
    val document = Jsoup.parse(body!!.string(), request.url.toString())
    return SManga.create().apply {
        url = request.url.pathSegments.lastOrNull().orEmpty()
        title = document.selectFirst("h1")?.text().orEmpty()
        description = document.selectFirst("[class*=synopsis], [class*=description], main p")?.text()
        thumbnail_url = document.selectFirst("main img[src], img[src]")?.attr("abs:src")
    }
}

private fun Response.parseHtmlChapters(): List<SChapter> {
    val document = Jsoup.parse(body!!.string(), request.url.toString())
    return document.select("a[href^=/read/]")
        .mapNotNull { element ->
            val href = element.attr("href").substringBefore("?")
            if (href.count { it == '/' } < 3) return@mapNotNull null
            val number = Regex("(?:capitulo|capítulo)[- ]([0-9]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)
                .find(href)?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: -1f
            SChapter.create().apply {
                url = href
                name = element.text().trim().ifEmpty { "Capítulo" }
                chapter_number = number
            }
        }
        .distinctBy(SChapter::url)
}

private fun Response.parseHtmlPages(): List<Page> {
    val document = Jsoup.parse(body!!.string(), request.url.toString())
    return document.select("img[src]")
        .mapNotNull { it.attr("abs:src").takeIf(String::isNotBlank) }
        .distinct()
        .mapIndexed { index, url -> Page(index, imageUrl = url) }
}
