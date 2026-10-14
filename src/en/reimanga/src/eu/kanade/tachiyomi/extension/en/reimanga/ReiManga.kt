package eu.kanade.tachiyomi.extension.en.reimanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.util.Locale

@Source
abstract class ReiManga : HttpSource() {
    override val supportsLatest = true

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage = parseMangaPage(response)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/latest-update?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaPage(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET("$baseUrl/search?keyword=${java.net.URLEncoder.encode(query, "UTF-8")}&page=$page", headers)

    override fun searchMangaParse(response: Response): MangasPage = parseMangaPage(response)

    private fun parseMangaPage(response: Response): MangasPage {
        val document = response.asJsoup()
        val manga = document.select("a[href^=/manga/]")
            .mapNotNull { element ->
                val href = element.attr("abs:href").substringBefore('?').trimEnd('/')
                if (href == baseUrl || href.endsWith("/manga")) return@mapNotNull null
                val title = element.selectFirst("h1, h2, h3, h4, .title")?.text()
                    ?: element.selectFirst("img[alt]")?.attr("alt")
                    ?: element.text()
                if (title.isBlank()) return@mapNotNull null
                SManga.create().apply {
                    url = href.removePrefix(baseUrl)
                    this.title = title.trim()
                    thumbnail_url = element.selectFirst("img")?.attr("abs:src")
                }
            }
            .distinctBy(SManga::url)
        val hasNext = document.selectFirst("a[rel=next], a.next, .pagination a") != null
        return MangasPage(manga, hasNext)
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            url = response.request.url.encodedPath
            title = document.selectFirst("h1")?.text()?.trim().orEmpty()
            description = document.selectFirst(".description, .synopsis, [class*=synopsis], main p")?.text()?.trim()
            thumbnail_url = document.selectFirst("img")?.attr("abs:src")
            genre = document.select(".genres a, .genre a, [class*=genre] a").joinToString { it.text() }
        }
    }

    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("a[href*=/chapter/], a[href*=/read/], a[href*=/chapters/]")
            .mapNotNull { element ->
                val url = element.attr("abs:href").substringBefore('?')
                val text = element.text().trim()
                val number = Regex("(?:chapter|capitulo|capítulo)\\s*([0-9]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)
                    .find("$text $url")?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: -1f
                if (url.isBlank()) return@mapNotNull null
                SChapter.create().apply {
                    this.url = url.removePrefix(baseUrl)
                    name = text.ifBlank { "Chapter ${if (number >= 0) number else ""}" }.trim()
                    chapter_number = number
                    date_upload = 0L
                }
            }
            .distinctBy(SChapter::url)
            .sortedByDescending(SChapter::chapter_number)
    }

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> = response.asJsoup()
        .select("img.chapter-image, .chapter-images img, .reading-content img, main img")
        .mapNotNull { it.attr("abs:src").takeIf(String::isNotBlank) }
        .distinct()
        .mapIndexed { index, url -> Page(index, imageUrl = url) }

    override fun imageUrlParse(response: Response): String = response.request.url.toString()
}
