package eu.kanade.tachiyomi.extension.all.mangaplus

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
class MpEnvelope(
    @ProtoNumber(1) val success: MpSuccess? = null,
    @ProtoNumber(2) val error: MpError? = null,
)

@Serializable
class MpSuccess(
    @ProtoNumber(8) val detail: MpTitleDetail? = null,
    @ProtoNumber(10) val viewer: MpViewer? = null,
    @ProtoNumber(25) val allTitles: MpAllTitles? = null,
    @ProtoNumber(35) val typedTitles: MpTypedTitles? = null,
    @ProtoNumber(37) val ranking: MpRanking? = null,
    @ProtoNumber(38) val home: MpHome? = null,
    @ProtoNumber(22) val updatedTitles: MpUpdatedTitleList? = null,
)

@Serializable
class MpError(
    @ProtoNumber(2) val english: MpMessage? = null,
    @ProtoNumber(3) val spanish: MpMessage? = null,
)

@Serializable
class MpMessage(
    @ProtoNumber(1) val subject: String = "",
    @ProtoNumber(2) val body: String = "",
)

@Serializable
class MpRanking(@ProtoNumber(3) val groups: List<MpRankGroup> = emptyList())

@Serializable
class MpRankGroup(@ProtoNumber(2) val titles: List<MpTitle> = emptyList())

@Serializable
class MpAllTitles(@ProtoNumber(1) val groups: List<MpTitleGroup> = emptyList())

@Serializable
class MpTitleGroup(
    @ProtoNumber(1) val category: String = "",
    @ProtoNumber(2) val titles: List<MpTitle> = emptyList(),
)

@Serializable
class MpTypedTitles(
    @ProtoNumber(2) val tags: List<MpTag> = emptyList(),
    @ProtoNumber(3) val titles: List<MpTypedTitle> = emptyList(),
)

@Serializable
class MpTypedTitle(
    @ProtoNumber(2) val title: MpTitle,
    @ProtoNumber(3) val genres: List<MpTag> = emptyList(),
)

@Serializable
class MpHome(@ProtoNumber(2) val groups: List<MpUpdateGroup> = emptyList())

@Serializable
class MpUpdateGroup(@ProtoNumber(2) val titleGroups: List<MpOriginalTitleGroup> = emptyList())

@Serializable
class MpOriginalTitleGroup(@ProtoNumber(3) val titles: List<MpUpdatedTitle> = emptyList())

@Serializable
class MpUpdatedTitle(@ProtoNumber(1) val title: MpTitle? = null)

@Serializable
class MpUpdatedTitleList(@ProtoNumber(1) val groups: List<MpUpdatedTitleGroup> = emptyList())

@Serializable
class MpUpdatedTitleGroup(@ProtoNumber(2) val titles: List<MpUpdatedTitle> = emptyList())

@Serializable
class MpTitle(
    @ProtoNumber(1) val id: Int,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val author: String? = null,
    @ProtoNumber(4) val cover: String = "",
    @ProtoNumber(7) val language: Int = 0,
)

@Serializable
class MpTitleDetail(
    @ProtoNumber(1) val title: MpTitle,
    @ProtoNumber(3) val description: String = "",
    @ProtoNumber(7) val schedule: String = "",
    @ProtoNumber(8) val statusText: String = "",
    @ProtoNumber(28) val chapterGroups: List<MpChapterGroup> = emptyList(),
    @ProtoNumber(31) val genres: List<MpTag> = emptyList(),
) {
    val chapters: List<MpChapter>
        get() = chapterGroups.flatMap { it.first + it.last }
}

@Serializable
class MpTag(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val slug: String = "",
)

@Serializable
class MpChapterGroup(
    @ProtoNumber(2) val first: List<MpChapter> = emptyList(),
    @ProtoNumber(4) val last: List<MpChapter> = emptyList(),
)

@Serializable
class MpChapter(
    @ProtoNumber(2) val id: Int,
    @ProtoNumber(3) val name: String,
    @ProtoNumber(4) val subtitle: String? = null,
    @ProtoNumber(6) val timestamp: Int = 0,
)

@Serializable
class MpViewer(
    @ProtoNumber(1) val pages: List<MpPageContainer> = emptyList(),
    @ProtoNumber(19) val token: String? = null,
)

@Serializable
class MpPageContainer(@ProtoNumber(1) val page: MpPage? = null)

@Serializable
class MpPage(
    @ProtoNumber(1) val url: String,
    @ProtoNumber(5) val key: String? = null,
)
