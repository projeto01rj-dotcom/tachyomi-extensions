package eu.kanade.tachiyomi.extension.all.megacompartilhamento

import android.util.Base64
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Source
abstract class MegaCompartilhamento : HttpSource() {
    override val supportsLatest = true
    private val json = Json { ignoreUnknownKeys = true }
    private val shareUrl = DEFAULT_LINK
    private val apiHost = "https://g.api.mega.co.nz"

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val rawKey = request.header(KEY_HEADER)
            val response = chain.proceed(request)
            if (rawKey == null || response.request.url.host != "g.api.mega.co.nz") return@addInterceptor response
            val api = runCatching {
                (json.parseToJsonElement(response.body!!.string()) as JsonArray).first().jsonObject
            }.getOrNull() ?: return@addInterceptor response
            response.close()
            val downloadUrl = api["g"]?.jsonPrimitive?.content ?: return@addInterceptor response
            val download = network.client.newCall(Request.Builder().url(downloadUrl).get().build()).execute()
            val encrypted = download.body!!.bytes()
            download.close()
            response.newBuilder().body(decryptCtr(encrypted, decode(rawKey)).toResponseBody("image/*".toMediaType())).build()
        }
        .build()

    override fun popularMangaRequest(page: Int): Request = megaRequest(listRequest())
    override fun popularMangaParse(response: Response): MangasPage = parseRoot(response)
    override fun latestUpdatesRequest(page: Int): Request = megaRequest(listRequest())
    override fun latestUpdatesParse(response: Response): MangasPage = parseRoot(response)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = megaRequest(listRequest())
    override fun searchMangaParse(response: Response): MangasPage = parseRoot(response)

    private fun parseRoot(response: Response): MangasPage {
        val nodes = parseNodes(response)
        val folders = nodes.filter { it.type == 1 }
        val nodeIds = nodes.mapTo(mutableSetOf()) { it.id }
        // The public link points to the file-cloud home. The actual source
        // root is the child folder named "Mangás", whose parent (home) may
        // also be present in the API response. Do not rely only on a missing
        // parent to identify the root, or the catalog becomes empty.
        val mangaRoot = folders.firstOrNull { it.name.normalizedName() == "mangas" }
            ?: folders.firstOrNull { it.parent !in nodeIds }
        val mangas = mangaRoot?.let { root ->
            folders.filter { it.parent == root.id }.map { it.toManga() }
        }.orEmpty().distinctBy(SManga::url)
        return MangasPage(mangas, false)
    }

    override fun mangaDetailsRequest(manga: SManga): Request = megaRequest(listRequest(), manga.url.substringAfter(':'))
    override fun mangaDetailsParse(response: Response): SManga {
        val id = response.request.url.queryParameter("manga") ?: return SManga.create()
        return parseNodes(response).firstOrNull { it.id == id }?.toManga() ?: SManga.create()
    }

    override fun chapterListRequest(manga: SManga): Request = megaRequest(listRequest(), manga.url.substringAfter(':'))
    override fun chapterListParse(response: Response): List<SChapter> {
        val id = response.request.url.queryParameter("manga") ?: return emptyList()
        val nodes = parseNodes(response)
        return nodes.filter { it.parent == id && it.type == 1 }
            .sortedByDescending { chapterNumber(it.name) }
            .map { node ->
                SChapter.create().apply {
                    url = "chapter:${node.id}"
                    name = node.name
                    chapter_number = chapterNumber(node.name)
                }
            }
    }

    override fun pageListRequest(chapter: SChapter): Request = megaRequest(listRequest(), chapter.url.substringAfter(':'))
    override fun pageListParse(response: Response): List<Page> {
        val id = response.request.url.queryParameter("manga") ?: return emptyList()
        val nodes = parseNodes(response)
        return nodes.filter { it.parent == id && it.type == 0 }
            .sortedBy { it.name.lowercase().padStart(12, '0') }
            .mapIndexed { index, node -> Page(index, imageUrl = "mega://${node.id}!${encode(node.key)}") }
    }

    override fun imageRequest(page: Page): Request {
        val parts = page.imageUrl!!.removePrefix("mega://").split('!', limit = 2)
        return megaRequest(downloadRequest(parts[0]), host = apiHost).newBuilder()
            .header(KEY_HEADER, parts[1])
            .build()
    }

    override fun imageUrlParse(response: Response): String = response.request.url.toString()

    private fun listRequest(): JsonObject = buildJsonObject {
        put("a", JsonPrimitive("f"))
        put("c", JsonPrimitive(1))
        put("ca", JsonPrimitive(1))
        put("r", JsonPrimitive(1))
    }

    private fun downloadRequest(id: String): JsonObject = buildJsonObject {
        put("a", JsonPrimitive("g"))
        put("g", JsonPrimitive(1))
        put("n", JsonPrimitive(id))
    }

    private fun megaRequest(payload: JsonObject, parent: String? = null, host: String = apiHost): Request {
        val body = buildJsonArray { add(payload) }.toString().toRequestBody("application/json".toMediaType())
        val publicFolder = payload["a"]?.jsonPrimitive?.content == "f"
        val url = "$host/cs?id=${System.currentTimeMillis()}" +
            (if (publicFolder) "&n=${shareParts().first}" else "") +
            (parent?.let { "&manga=$it" } ?: "")
        return POST(url, headers, body)
    }

    private fun parseNodes(response: Response): List<Node> {
        val payload = runCatching { json.parseToJsonElement(response.body!!.string()) }.getOrNull()
        val root = (payload as? JsonArray)?.firstOrNull() as? JsonObject ?: return emptyList()
        val (shareHandle, shareKey) = shareParts()
        val folderKeys = mutableMapOf<String, ByteArray>()
        val nodeArray = root["f"] as? JsonArray ?: return emptyList()
        return nodeArray.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj["h"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val type = obj["t"]?.jsonPrimitive?.int ?: return@mapNotNull null
            val parent = obj["p"]?.jsonPrimitive?.content ?: ""
            val keyParts = obj["k"]?.jsonPrimitive?.content
                ?.split('/')
                ?.mapNotNull { part -> part.split(':', limit = 2).takeIf { it.size == 2 } }
                ?: return@mapNotNull null
            // Nodes in a public shared folder carry a key encrypted with the
            // shared-folder handle (not necessarily with their immediate
            // parent). Prefer that entry for the whole tree; retain the
            // parent-key fallback for legacy/shared-node responses.
            val sharedKeyPart = keyParts.firstOrNull { it[0] == shareHandle }
            val encryptedNodeKey = sharedKeyPart?.get(1)
                ?: keyParts.firstOrNull { it[0] == parent }?.get(1)
                ?: keyParts.lastOrNull()?.get(1)
                ?: return@mapNotNull null
            val decryptingKey = if (sharedKeyPart != null) {
                shareKey
            } else {
                folderKeys[parent] ?: shareKey
            }
            val rawKey = decryptKey(decode(encryptedNodeKey), decryptingKey)
            val attributes = obj["a"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val name = decryptAttributes(decode(attributes), if (type == 0) xorKey(rawKey) else rawKey) ?: return@mapNotNull null
            if (type == 1) folderKeys[id] = rawKey
            Node(id, parent, type, name, rawKey)
        }
    }

    private fun shareParts(): Pair<String, ByteArray> {
        val fragment = shareUrl.substringAfter('#', "")
        val id = Regex("/folder/([A-Za-z0-9_-]+)").find(shareUrl)?.groupValues?.get(1)
            ?: fragment.substringAfter("F!").substringBefore('!')
        val keyText = if ('!' in fragment) fragment.substringAfterLast('!') else fragment
        return id to decode(keyText)
    }

    private fun shareKey(): ByteArray = shareParts().second

    private fun decryptKey(data: ByteArray, key: ByteArray): ByteArray = data.asList().chunked(16).flatMap { aesCbc(it.toByteArray(), key, Cipher.DECRYPT_MODE).asList() }.toByteArray()
    private fun decryptAttributes(data: ByteArray, key: ByteArray): String? = runCatching {
        val plain = aesCbc(data, key, Cipher.DECRYPT_MODE).toString(Charsets.UTF_8).trimEnd('\u0000')
        if (!plain.startsWith("MEGA{")) null else Json.parseToJsonElement(plain.substring(4)).jsonObject["n"]?.jsonPrimitive?.content
    }.getOrNull()

    private fun aesCbc(data: ByteArray, key: ByteArray, mode: Int): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(mode, SecretKeySpec(key.copyOf(16), "AES"), IvParameterSpec(ByteArray(16)))
        return cipher.doFinal(data)
    }

    private fun decryptCtr(data: ByteArray, rawKey: ByteArray): ByteArray {
        val iv = ByteArray(16)
        if (rawKey.size >= 24) rawKey.copyInto(iv, destinationOffset = 0, startIndex = 16, endIndex = 24)
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(xorKey(rawKey), "AES"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    private fun xorKey(key: ByteArray): ByteArray {
        if (key.size < 32) return key
        return ByteArray(16) { key[it].toInt().xor(key[it + 16].toInt()).toByte() }
    }

    private fun decode(value: String): ByteArray = Base64.decode(value.replace('-', '+').replace('_', '/').padEnd((value.length + 3) / 4 * 4, '='), Base64.DEFAULT)
    private fun encode(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP or Base64.URL_SAFE)
    private fun chapterNumber(name: String): Float = Regex("(?:chapter|capitulo|capítulo)\\s*([0-9]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE).find(name)?.groupValues?.get(1)?.toFloatOrNull() ?: -1f
    private fun naturalKey(value: String): List<String> = Regex("\\d+|\\D+").findAll(value.lowercase()).map { it.value }.toList()

    private fun String.normalizedName(): String = java.text.Normalizer
        .normalize(this, java.text.Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .lowercase()
        .trim()

    private data class Node(val id: String, val parent: String, val type: Int, val name: String, val key: ByteArray) {
        fun toManga() = SManga.create().apply {
            url = "manga:$id"
            title = name
        }
    }

    companion object {
        private const val DEFAULT_LINK = "https://mega.nz/folder/cFdglQSR#hDL6wCQQhEzDtJxykk55Vg"
        private const val KEY_HEADER = "X-Mega-File-Key"
    }
}
