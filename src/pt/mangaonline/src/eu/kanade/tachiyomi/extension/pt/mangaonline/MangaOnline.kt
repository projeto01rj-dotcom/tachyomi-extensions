package eu.kanade.tachiyomi.extension.pt.mangaonline

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class MangaOnline : Madara() {
    override val dateFormat = SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("pt"))

    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override fun popularMangaRequest(page: Int): Request {
        val path = if (page == 1) "/manga/" else "/manga/page/$page/"
        return GET("$baseUrl$path?ordem=popular", headers)
    }

    override fun popularMangaSelector() = "article.home-manga-card"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        element.selectFirst("h3 a")?.let {
            setUrlWithoutDomain(it.attr("abs:href"))
            title = it.text()
        }
        element.selectFirst("img")?.let {
            thumbnail_url = processThumbnail(imageFromElement(it), true)
        }
    }

    override fun popularMangaNextPageSelector() = "div.manga-archive-pagination a.next"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (page == 1) {
            "$baseUrl/".toHttpUrl()
        } else {
            "$baseUrl/page/$page/".toHttpUrl()
        }.newBuilder()
            .addQueryParameter("s", query)
            .build()

        return GET(url, headers)
    }

    override fun searchMangaSelector() = ".search-results-grid .manga-card"

    override val searchMangaUrlSelector = "a.manga-card-link"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        element.selectFirst(searchMangaUrlSelector)?.let {
            setUrlWithoutDomain(it.attr("abs:href"))
        }
        element.selectFirst("h3.manga-card-title")?.let {
            title = it.text()
        }
        element.selectFirst("img")?.let {
            thumbnail_url = processThumbnail(imageFromElement(it), true)
        }
    }

    override fun searchMangaNextPageSelector(): String? = null

    override fun latestUpdatesRequest(page: Int): Request {
        val path = if (page == 1) "/manga/" else "/manga/page/$page/"
        return GET("$baseUrl$path?ordem=recentes", headers)
    }

    override val mangaDetailsSelectorTitle = "h1.manga-title"
    override val mangaDetailsSelectorDescription = "div.synopsis-content"
    override val mangaDetailsSelectorThumbnail = "div.manga-cover img"
    override val mangaDetailsSelectorStatus = "div.manga-meta-item"
    override val mangaDetailsSelectorGenre = ".manga-tags a, .manga-tags span"

    override fun chapterListSelector() = "li.chapter-item"
    override val chapterUrlSelector = "a.chapter-link"
    override fun chapterDateSelector() = "span.chapter-date"
    override val chapterUrlSuffix = ""
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
    override val pageListParseSelector = ".chapter-images img.chapter-image"
}
