package eu.kanade.tachiyomi.multisrc.aurora

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.text.SimpleDateFormat
import java.util.Locale

private const val SITE_BASE_URL = "https://leitordemangas.com"

fun normalizeSiteUrl(value: String?): String? {
    val url = value?.trim().orEmpty()
    if (url.isBlank()) return null
    return when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("http://") || url.startsWith("https://") -> url
        else -> "$SITE_BASE_URL/${url.trimStart('/')}"
    }
}

@Serializable
class SeriesDto(
    @JsonNames("updates") val series: List<MangaDto>,
) {
    fun toSMangaList(): List<SManga> = series.map { it.toSManga() }
}

@Serializable
class MangaDto(
    @JsonNames("name") val title: String,
    @JsonNames("image") val coverUrl: String? = null,
    @JsonNames("url") val slug: String,
    val type: String? = null,
    val description: String? = null,
    val author: Value? = null,
    val genre: List<String> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        title = this@MangaDto.title
        thumbnail_url = normalizeSiteUrl(coverUrl)
        description = this@MangaDto.description
        author = this@MangaDto.author?.name
        genre = this@MangaDto.genre.joinToString()

        val path = slug.substringAfter("//", slug).substringAfter("/").substringBefore("?").trim('/')
        val parts = path.split('/').filter { it.isNotBlank() }
        val detectedType = type?.takeIf { it.isNotBlank() }
            ?: parts.firstOrNull()?.takeIf { it in setOf("manga", "manhwa", "manhua") }
        val cleanSlug = if (detectedType != null && parts.firstOrNull() == detectedType) {
            parts.drop(1).joinToString("/")
        } else {
            parts.joinToString("/")
        }
        url = "/${detectedType ?: "manga"}/$cleanSlug"
    }
}

@Serializable
class Value(val name: String = "")

@Serializable
class ChapterListDto(
    @JsonNames("slug") val serieSlug: String,
    val chapters: List<ChapterDto>,
) {
    fun toSChapterList(mangaPath: String): List<SChapter> = chapters.map { it.toSChapter(mangaPath) }
}

@Serializable
class ChapterDto(
    val id: String,
    val number: String,
    @SerialName("releaseDate") val releaseDate: String? = null,
) {
    fun toSChapter(mangaPath: String) = SChapter.create().apply {
        val chapter = number.ifBlank { id }
        name = "Capítulo $chapter"
        chapter_number = chapter.toFloatOrNull() ?: -1f
        date_upload = parseDate(releaseDate)
        url = "${mangaPath.trimEnd('/')}/${chapter.trimStart('/')}"
    }
}

@Serializable
class PagesDto(val pages: List<ImageDto>)

@Serializable
class ImageDto(val url: String)

@Serializable
class KeyDto(val k: String)

private fun parseDate(value: String?): Long {
    if (value.isNullOrBlank()) return 0L
    return runCatching {
        SimpleDateFormat("dd 'de' MMM 'de' yyyy", Locale("pt", "BR")).parse(value)?.time ?: 0L
    }.getOrDefault(0L)
}
