package eu.kanade.tachiyomi.extension.pt.leituramanga

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.IOException
import kotlin.time.Instant
import kotlin.time.Duration.Companion.seconds

@Source
abstract class LeituraManga : KeiSource() {
    private val apiUrl = "https://api.leituramanga.net"

    private val cdnUrl = "https://cdn.leituramanga.net"

    private val apiCooldownPreferences by lazy { getPreferences() }

    private companion object {
        const val API_COOLDOWN_UNTIL = "api_cooldown_until"
    }

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor { chain ->
            val request = chain.request()
            val isApiRequest = request.url.host == "api.leituramanga.net"
            if (isApiRequest) {
                val remainingMillis = apiCooldownPreferences.getLong(API_COOLDOWN_UNTIL, 0L) - System.currentTimeMillis()
                if (remainingMillis > 0L) {
                    val remainingSeconds = (remainingMillis + 999L) / 1000L
                    throw IOException("Limite do site atingido. Aguarde $remainingSeconds segundos.")
                }
            }

            val response = chain.proceed(request)
            when (response.code) {
                403 -> {
                    response.close()
                    throw IOException("Abra primeiramente algum mangá ou qualquer capítulo na WebiView")
                }
                429 -> {
                    val retryAfter = response.header("Retry-After") ?: "alguns"
                    val retryAfterSeconds = retryAfter.toLongOrNull()
                    if (isApiRequest && retryAfterSeconds != null) {
                        val blockedUntil = System.currentTimeMillis() + retryAfterSeconds.coerceAtLeast(1L) * 1000L
                        val previousBlockedUntil = apiCooldownPreferences.getLong(API_COOLDOWN_UNTIL, 0L)
                        if (blockedUntil > previousBlockedUntil) {
                            apiCooldownPreferences.edit().putLong(API_COOLDOWN_UNTIL, blockedUntil).apply()
                        }
                    }
                    response.close()
                    throw IOException("Limite do site atingido. Aguarde $retryAfter segundos.")
                }
            }
            response
        }
        rateLimit(1, 2.seconds)
    }

    // ================= Popular ==================

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList("$apiUrl/api/manga/?sort=view&limit=24&page=$page")

    // ================= Latest ==================

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList("$apiUrl/api/manga/?sort=time&limit=24&page=$page")

    private suspend fun parseMangaList(url: String): MangasPage {
        val result = client.get(url, headersBuilder().build()).parseAs<MangaResponseDto<MangaListDto>>()
        val mangas = result.data.data.map { it.toSManga(cdnUrl) }
        val hasNext = result.data.pagination.let { it.page < it.totalPage }
        return MangasPage(mangas, hasNext)
    }

    // ================= Search ==================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/api/manga/".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "24")
            .addQueryParameter("page", page.toString())

        if (query.isNotEmpty()) {
            url.addQueryParameter("keyword", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    if (filter.selectedValue.isNotEmpty()) {
                        url.addQueryParameter("genre", filter.selectedValue)
                    }
                }

                is StatusFilter -> {
                    if (filter.selectedValue.isNotEmpty()) {
                        url.addQueryParameter("status", filter.selectedValue)
                    }
                }

                is SortFilter -> {
                    url.addQueryParameter("sort", filter.selectedValue)
                }

                else -> {}
            }
        }

        return parseMangaList(url.build().toString())
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        GenreFilter(),
        StatusFilter(),
        SortFilter(),
    )

    // ================= Details ==================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), headersBuilder().build()).asJsoup()

        manga.apply {
            title = document.selectFirst("h1")!!.text()
            description = document.selectFirst("h2:contains(Sinopse) +div p")?.text()
            author = document.selectFirst("h2:contains(Informações) +div p:contains(Autor)")?.text()?.substringAfter(":")
            genre = document.select("h2 + div > a[href*=genre]").joinToString { it.text() }

            status = when (document.selectFirst("h2:contains(Informações) +div p:contains(Status)")?.text()?.substringAfter(":")?.trim()?.lowercase()) {
                "Em breve", "Em andamento" -> SManga.ONGOING
                "Completo" -> SManga.COMPLETED
                "Cancelado" -> SManga.CANCELLED
                "Em pausa" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }

            setUrlWithoutDomain(document.location())
        }

        if (!fetchChapters) return SMangaUpdate(manga, chapters)

        val slug = document.location().toHttpUrl().pathSegments.last { it.isNotEmpty() }
        val sitemapUrl = "$baseUrl/sitemaps/manga/$slug.xml"
        val sitemapXml = client.get(sitemapUrl, headersBuilder().build()).use { it.body!!.string() }
        val sitemap = Jsoup.parse(sitemapXml, sitemapUrl, Parser.xmlParser())
        val siteHost = baseUrl.toHttpUrl().host
        val chaptersFromSitemap = sitemap.select("urlset > url").mapNotNull { entry ->
            val chapterUrl = entry.selectFirst("loc")?.text()?.trim()?.toHttpUrlOrNull()
                ?: return@mapNotNull null
            val path = chapterUrl.pathSegments
            if (chapterUrl.host != siteHost || path.getOrNull(0) != "manga" ||
                path.getOrNull(1) != slug || path.getOrNull(2) != "chapter"
            ) {
                return@mapNotNull null
            }

            val number = path.getOrNull(3) ?: return@mapNotNull null
            SChapter.create().apply {
                name = "Capítulo $number"
                url = "/manga/$slug/chapter/$number"
                chapter_number = number.toFloatOrNull() ?: -1f
                date_upload = entry.selectFirst("lastmod")?.text()?.let { Instant.tryParse(it) } ?: 0L
            }
        }.distinctBy(SChapter::url)

        if (chaptersFromSitemap.isEmpty()) throw IOException("Nenhum capítulo encontrado no sitemap do site")
        return SMangaUpdate(manga, chaptersFromSitemap)
    }

    // ================= Pages ==================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val dto = client.get(getChapterUrl(chapter), headersBuilder().build()).asJsoup().extractNextJs<ChapterPageDto> {
            val chapter = (it as? JsonObject)?.get("chapter") as? JsonObject
            chapter?.containsKey("images") == true
        } ?: throw IOException("Páginas não encontradas")

        return dto.chapter.images.mapIndexed { index, image ->
            Page(index, imageUrl = image.absUrl(cdnUrl))
        }
    }
}
