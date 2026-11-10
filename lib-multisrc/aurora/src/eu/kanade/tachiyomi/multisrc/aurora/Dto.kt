package eu.kanade.tachiyomi.multisrc.aurora

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class SeriesDto(
    @JsonNames("updates") val series: List<MangaDto> = emptyList(),
) {
    fun toSMangaList(): List<SManga> = series.map { it.toSManga() }
}

@Serializable
class MangaDto(
    @JsonNames("name") val title: String = "",
    @JsonNames("image") val coverUrl: String? = null,
    @JsonNames("url") val slug: String = "",
    val type: String? = null,
    val description: String? = null,
    val author: Value? = null,
    val genre: List<String> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        title = this@MangaDto.title
        thumbnail_url = coverUrl
        description = this@MangaDto.description
        author = this@MangaDto.author?.name
        genre = this@MangaDto.genre.joinToString()
        val cleanSlug = slug.substringAfterLast("/").trim('/').ifBlank { slug }
        url = "/${type ?: "manga"}/$cleanSlug"
    }
}

@Serializable
class Value(val name: String = "")

@Serializable
class ChapterListDto(
    @JsonNames("slug") val serieSlug: String = "",
    val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSChapterList(mangaPath: String): List<SChapter> = chapters.map { it.toSChapter(mangaPath, serieSlug) }
}

@Serializable
class ChapterDto(
    val id: String = "",
    val number: String = "",
    @SerialName("releaseDate") val releaseDate: String? = null,
) {
    fun toSChapter(mangaPath: String, serieSlug: String) = SChapter.create().apply {
        name = "Capítulo ${number.ifBlank { id }}"
        chapter_number = number.toFloatOrNull() ?: -1f
        date_upload = parseDate(releaseDate)
        url = "${mangaPath.trimEnd('/')}/${id.trimStart('/')}"
    }
}

@Serializable
class PagesDto(val pages: List<ImageDto> = emptyList())

@Serializable
class ImageDto(val url: String = "")

@Serializable
class KeyDto(val k: String = "")

private fun parseDate(value: String?): Long {
    if (value.isNullOrBlank()) return 0L
    return runCatching {
        SimpleDateFormat("dd 'de' MMM 'de' yyyy", Locale("pt", "BR")).parse(value)?.time ?: 0L
    }.getOrDefault(0L)
}
