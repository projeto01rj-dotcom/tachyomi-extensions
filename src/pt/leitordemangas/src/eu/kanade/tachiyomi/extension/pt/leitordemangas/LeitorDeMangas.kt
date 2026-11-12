package eu.kanade.tachiyomi.extension.pt.leitordemangas

import eu.kanade.tachiyomi.multisrc.aurora.Aurora
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.IOException

/**
 * Leitor público do site Leitor de Mangas.
 * Não cria sessão de usuário nem exige assinatura; usa somente o fluxo de leitura
 * disponibilizado pelo próprio site para visitantes.
 */
@Source
abstract class LeitorDeMangas : Aurora() {

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = super.chapterListParse(response)
        if (chapters.isNotEmpty()) return chapters

        return chapterListFromPublicSitemap(response.request.url.encodedPath.trimEnd('/'))
    }

    /**
     * Aurora já tenta os dados públicos da página e seus links HTML. Se ambos
     * vierem vazios, usa os sitemaps públicos por categoria como último fallback.
     */
    private fun chapterListFromPublicSitemap(mangaPath: String): List<SChapter> {
        val category = mangaPath.trim('/').substringBefore('/')
        if (category !in SUPPORTED_SITEMAP_CATEGORIES) return emptyList()

        val siteHost = baseUrl.toHttpUrl().host
        val indexUrl = "$baseUrl/sitemap.xml"
        val indexXml = client.newCall(GET(indexUrl, headers)).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Falha ao carregar o índice de sitemaps (${response.code})")
            response.body?.string().orEmpty()
        }

        val sitemapPattern = Regex("sitemap_${Regex.escape(category)}_\\d+\\.xml")
        val categorySitemaps = Jsoup.parse(indexXml, indexUrl, Parser.xmlParser())
            .select("sitemap > loc")
            .mapNotNull { it.text().trim().toHttpUrlOrNull() }
            .filter { url ->
                url.host == siteHost && sitemapPattern.matches(url.pathSegments.lastOrNull().orEmpty())
            }

        val chapters = mutableListOf<SChapter>()
        for (sitemapUrl in categorySitemaps) {
            val sitemapXml = client.newCall(GET(sitemapUrl.toString(), headers)).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Falha ao carregar sitemap de capítulos (${response.code})")
                response.body?.string().orEmpty()
            }

            val entries = Jsoup.parse(sitemapXml, sitemapUrl.toString(), Parser.xmlParser())
                .select("url > loc")
            for (entry in entries) {
                val chapterUrl = entry.text().trim().toHttpUrlOrNull() ?: continue
                if (chapterUrl.host != siteHost) continue

                val chapterPath = chapterUrl.encodedPath.trimEnd('/')
                val chapterPrefix = "$mangaPath/"
                if (!chapterPath.startsWith(chapterPrefix)) continue

                val number = chapterPath.removePrefix(chapterPrefix)
                if (number.isBlank() || '/' in number) continue

                chapters += SChapter.create().apply {
                    name = "Capítulo $number"
                    url = chapterPath
                    chapter_number = number.replace(',', '.').toFloatOrNull() ?: -1f
                }
            }
        }

        return chapters.distinctBy(SChapter::url)
    }

    private companion object {
        val SUPPORTED_SITEMAP_CATEGORIES = setOf("manga", "manhwa", "manhua", "one-shot")
    }
}
