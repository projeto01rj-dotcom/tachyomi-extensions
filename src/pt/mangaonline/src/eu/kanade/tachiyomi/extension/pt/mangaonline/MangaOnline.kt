package eu.kanade.tachiyomi.extension.pt.mangaonline

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class MangaOnline : Madara() {
    override val dateFormat = SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("pt"))
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override val chapterUrlSuffix = ""
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

    // Manga Online replaced the standard Madara <li.wp-manga-chapter>
    // markup with article cards in its current theme.
    override fun chapterListSelector() = "article[data-mo-chapter]"
    override fun chapterDateSelector() = "time"

    override val pageListParseSelector = "#mo-reading-content img.wp-manga-chapter-img, #mo-reading-content img"
}
