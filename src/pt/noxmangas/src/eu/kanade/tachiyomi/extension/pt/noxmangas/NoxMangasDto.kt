package eu.kanade.tachiyomi.extension.pt.noxmangas

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
class PaginatedComicsDto(
    @JsonNames("results") val comics: List<ComicDto> = emptyList(),
    private val page: Int = 1,
    @SerialName("total_pages") private val totalPages: Int = 1,
    private val total: Int = 0,
) {
    val hasNextPage: Boolean
        get() = page < totalPages || (page * 24) < total
}

@Serializable
class ComicDetailDto(
    @SerialName("comic") private val comic: ComicDto? = null,
    @SerialName("data") private val data: ComicDto? = null,
    private val slug: String? = null,
    private val title: String? = null,
    private val synopsis: String? = null,
    private val cover: String? = null,
    private val status: String? = null,
    private val genres: List<GenreDto> = emptyList(),
) {
    fun toSManga(): SManga = (comic ?: data ?: ComicDto(
        slug = slug.orEmpty(),
        title = title.orEmpty(),
        synopsis = synopsis,
        cover = cover,
        status = status,
        genres = genres,
    )).toSManga()
}

@Serializable
class ComicDto(
    private val slug: String,
    private val title: String,
    private val synopsis: String? = null,
    private val cover: String? = null,
    private val status: String? = null,
    private val genres: List<GenreDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@ComicDto.title
        thumbnail_url = cover
        status = parseStatus(this@ComicDto.status)
        description = synopsis
        genre = genres.joinToString { it.name }
    }

    private fun parseStatus(status: String?): Int = when (status?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        "cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }
}

@Serializable
class GenreDto(val name: String)

@Serializable
class PaginatedChaptersDto(
    val chapters: List<ChapterDto> = emptyList(),
    private val page: Int = 1,
    @SerialName("total_pages") private val totalPages: Int = 1,
) {
    val hasNextPage: Boolean
        get() = page < totalPages
}

@Serializable
class ChapterDto(
    private val id: String,
    private val number: Float? = null,
    private val title: String? = null,
    private val slug: String,
    @SerialName("published_at") private val publishedAt: String? = null,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = "/read/$mangaSlug/$slug#$id"

        val chapterName = buildString {
            if (number != null) {
                append("Capítulo ")
                append(number.toString().removeSuffix(".0"))
            } else {
                append("Capítulo")
            }
            if (!title.isNullOrEmpty()) {
                append(" - ")
                append(title)
            }
        }
        name = chapterName.trim()
        date_upload = parseDate(publishedAt)
    }
}

@Serializable
class PagesDto(
    private val pages: List<PageDto> = emptyList(),
) {
    fun toPageList(): List<Page> = pages.mapIndexed { index, page -> page.toPage(index) }
}

@Serializable
class PageDto(
    @SerialName("image_url") private val imageUrl: String,
) {
    fun toPage(index: Int) = Page(index, imageUrl = imageUrl)
}

private val parseDateRegex = Regex("(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?)")
private val parseOffsetRegex = Regex("([+-])(\\d{2}):?(\\d{2})$")

private val dateFormat by lazy {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}

private fun parseDate(raw: String?): Long {
    if (raw.isNullOrEmpty()) return 0L
    val match = parseDateRegex.find(raw)?.groupValues?.getOrNull(1) ?: return 0L

    var offsetMin = 0
    parseOffsetRegex.find(raw)?.let { g ->
        val sign = if (g.groupValues[1] == "-") -1 else 1
        offsetMin = sign * (g.groupValues[2].toInt() * 60 + g.groupValues[3].toInt())
    }

    val cleanBase = if (match.contains(".")) match else "$match.000"
    return runCatching { dateFormat.parse(cleanBase).time - offsetMin * 60_000L }
        .getOrDefault(0L)
}
