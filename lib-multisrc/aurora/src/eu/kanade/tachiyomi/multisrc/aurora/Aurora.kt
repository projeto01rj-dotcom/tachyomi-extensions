package eu.kanade.tachiyomi.multisrc.aurora

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Source
abstract class Aurora : HttpSource() {
    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .addHeader("Accept-Language", "pt-BR,pt;q=0.9")
                    .addHeader("Referer", "$baseUrl/")
                    .addHeader("Sec-Fetch-Dest", "document")
                    .addHeader("Sec-Fetch-Mode", "navigate")
                    .addHeader("Sec-Fetch-Site", "none")
                    .addHeader("Sec-Fetch-User", "?1")
                    .addHeader("Alt-Used", baseUrl.substringAfterLast("/"))
                    .build(),
            )
        }
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Accept-Language", "pt-BR,pt;q=0.9")

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/catalogo", headers)

    override fun popularMangaParse(response: Response): MangasPage = parseSeries(response)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/novidades", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = parseSeries(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/catalogo", headers.newBuilder().add("X-Mihon-Query", query).build())

    override fun searchMangaParse(response: Response): MangasPage {
        val query = response.request.header("X-Mihon-Query").orEmpty()
        val result = parseSeries(response)
        return if (query.isBlank()) {
            result
        } else {
            MangasPage(
                result.mangas.filter { it.title.contains(query, ignoreCase = true) },
                false,
            )
        }
    }

    private fun parseSeries(response: Response): MangasPage {
        val document = Jsoup.parse(response.body!!.string(), response.request.url.toString())
        val nextJs = document.extractNextJs<SeriesDto>()?.toSMangaList().orEmpty()
        val fallback = document.select("a[href^=/manga/], a[href^=/manhwa/], a[href^=/manhua/]")
            .mapNotNull { element ->
                val href = element.attr("href").substringBefore("?").trimEnd('/')
                val title = element.selectFirst("img[alt]")?.attr("alt")?.takeIf { it.isNotBlank() }
                    ?: element.selectFirst("h2, h3, h4")?.text()
                    ?: element.text()
                if (href.isBlank() || title.isBlank()) {
                    null
                } else {
                    SManga.create().apply {
                        url = href
                        this.title = title
                        thumbnail_url = element.selectFirst("img")?.let {
                            normalizeSiteUrl(
                                it.attr("abs:src").ifBlank {
                                    it.attr("data-src").ifBlank { it.attr("data-lazy-src") }
                                },
                            )
                        }
                    }
                }
            }
            .distinctBy { it.url }
        return MangasPage(
            (nextJs + fallback)
                .filter { it.url.isNotBlank() && it.url != "/manga/" }
                .distinctBy { it.url },
            false,
        )
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request = GET(getMangaUrl(manga), headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = Jsoup.parse(response.body!!.string(), response.request.url.toString())
        val manga = document.extractNextJs<MangaDto>()?.toSManga() ?: SManga.create()
        if (manga.title.isBlank()) manga.title = document.selectFirst("h1")?.text().orEmpty()
        if (manga.description.isNullOrBlank()) {
            manga.description = document.selectFirst(".description-summary, .summary_content, meta[name=description]")
                ?.let { it.attr("content").ifBlank { it.text() } }
        }
        if (manga.thumbnail_url.isNullOrBlank()) {
            manga.thumbnail_url = normalizeSiteUrl(
                document.selectFirst(".summary_image img, .tab-summary img, meta[property='og:image']")
                    ?.let { it.attr("abs:src").ifBlank { it.attr("content") } },
            )
        }
        if (manga.author.isNullOrBlank()) {
            manga.author = document.selectFirst(".author-content a, .author-content")?.text()?.trim()
        }
        if (manga.genre.isNullOrBlank()) {
            manga.genre = document.select(".genres-content a, .genres-content").joinToString { it.text().trim() }
        }
        return manga
    }

    override fun chapterListRequest(manga: SManga): Request = GET(getMangaUrl(manga), headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = Jsoup.parse(response.body!!.string(), response.request.url.toString())
        val mangaPath = response.request.url.encodedPath.trimEnd('/')
        val nextJs = document.extractNextJs<ChapterListDto>()?.toSChapterList(mangaPath)
        if (!nextJs.isNullOrEmpty()) return nextJs
        return document.select(".wp-manga-chapters-holder a, a[href*=/capitulo], a[href*=/ler/]").mapNotNull { element ->
            val href = element.attr("href").substringBefore("?").trimEnd('/')
            val name = element.text().trim().ifBlank { href.substringAfterLast('/').replace('-', ' ') }
            if (href.isBlank() || name.isBlank()) {
                null
            } else {
                SChapter.create().apply {
                    url = href
                    this.name = name
                    chapter_number = Regex("\\d+(?:[.,]\\d+)?").find(name)?.value?.replace(',', '.')?.toFloatOrNull() ?: -1f
                }
            }
        }.distinctBy { it.url }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    override fun pageListRequest(chapter: SChapter): Request = GET(
        getChapterUrl(chapter),
        headers.newBuilder()
            .add("Referer", getChapterUrl(chapter))
            .add("Accept", "text/x-component")
            .add("rsc", "1")
            .add("next-url", chapter.url.substringBeforeLast('/'))
            .add("Sec-Fetch-Dest", "empty")
            .add("Sec-Fetch-Mode", "cors")
            .add("Sec-Fetch-Site", "same-origin")
            .add("Cookie", "mnx_adulto=1; mnx_gate_${chapter.chapter_number}=1")
            .build(),
    )

    override fun pageListParse(response: Response): List<Page> {
        val payloads = response.extractNextJs<PagesDto>()?.pages.orEmpty()
        if (payloads.isNotEmpty()) {
            return payloads.mapIndexed { index, payload ->
                Page(index, imageUrl = decrypt(payload.url, getKey(payload.url)))
            }
        }
        val document = Jsoup.parse(response.body!!.string(), response.request.url.toString())
        return document.select("img[src], img[data-src]").mapIndexedNotNull { index, image ->
            val url = normalizeSiteUrl(
                image.attr("abs:src").ifBlank {
                    image.attr("abs:data-src").ifBlank { image.attr("data-lazy-src") }
                },
            )
            url?.takeIf { it.isNotBlank() }?.let { Page(index, imageUrl = it) }
        }
    }

    private var cachedKey: String? = null

    private fun getKey(payload: String): String {
        cachedKey?.let { return it }
        val bytes = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val version = bytes.first().toInt()
        val value = buffer.getInt(1)
        val response = client.newCall(GET("$baseUrl/api/atfield/key?v=$version&e=$value", headers)).execute()
        return response.parseAs<KeyDto>().k.also { cachedKey = it }
    }

    private fun decrypt(payload: String, key: String): String {
        val bytes = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP)
        val baseData = bytes.copyOfRange(5, 13)
        val cipherText = bytes.copyOfRange(13, bytes.size)
        val chapterKey = Base64.decode(key, Base64.DEFAULT)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(chapterKey, "HmacSHA256"))
        val keystream = ByteArray(cipherText.size)
        var offset = 0
        var counter = 0
        while (offset < cipherText.size) {
            val hash = mac.doFinal(baseData + byteArrayOf((counter and 0xFF).toByte()))
            val length = minOf(hash.size, cipherText.size - offset)
            hash.copyInto(keystream, offset, 0, length)
            offset += length
            counter++
        }
        return cipherText.mapIndexed { index, value ->
            (value.toInt() xor (keystream[index].toInt() and 0xFF)).toByte()
        }.toByteArray().toString(Charsets.UTF_8)
    }
}
