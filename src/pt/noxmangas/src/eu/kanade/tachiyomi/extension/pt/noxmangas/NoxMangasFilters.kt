package eu.kanade.tachiyomi.extension.pt.noxmangas

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class TypeFilter :
    UriPartFilter(
        "Tipo",
        arrayOf(
            "Qualquer" to "",
            "Manga" to "manga",
            "Manhwa" to "manhwa",
            "Manhua" to "manhua",
            "Webtoon" to "webtoon",
            "Pornhwa" to "pornhwa",
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Status",
        arrayOf(
            "Qualquer" to "",
            "Em andamento" to "ongoing",
            "Completo" to "completed",
            "Hiato" to "hiatus",
            "Cancelado" to "cancelled",
        ),
    )

class DemographicFilter :
    UriPartFilter(
        "Demografia",
        arrayOf(
            "Qualquer" to "",
            "Shounen" to "shounen",
            "Shoujo" to "shoujo",
            "Seinen" to "seinen",
            "Josei" to "josei",
        ),
    )

class SortFilter :
    Filter.Sort(
        "Ordenar por",
        arrayOf(
            "Lançamentos recentes",
            "Mais populares",
            "Melhor avaliados",
            "Alfabética",
            "Mais capítulos",
            "Mais antigos",
        ),
        Selection(0, false),
    )

class YearFilter : Filter.Text("Ano")