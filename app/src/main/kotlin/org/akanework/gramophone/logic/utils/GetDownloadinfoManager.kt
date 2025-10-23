package org.akanework.gramophone.logic.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.Instant

data class VideoDownloadInfo(
    val title: String,
    val backupUrl: String,
    val baseUrl: String,
    val isFlac: Boolean
)

class GetDownloadinfoManager(private val client: OkHttpClient = OkHttpClient()) {

    private var mixinKey: String? = null

    suspend fun getDownloadInfo(videoId: String): VideoDownloadInfo? = withContext(Dispatchers.IO) {
        val bvid = if (videoId.startsWith("AV", true)) translateAvidToBvid(videoId) else videoId
        val (cid, title) = getCidAndTitle(bvid) ?: return@withContext null
        val videoInfo = getVideoDownloadInfo(cid, bvid) ?: return@withContext null
        val (backupUrl, baseUrl, isFlac) = extractDownloadUrl(videoInfo)
        return@withContext VideoDownloadInfo(title, backupUrl, baseUrl, isFlac)
    }

    private suspend fun getCidAndTitle(bvid: String): Pair<Long, String>? {
        val params = wbiSign(mapOf("bvid" to bvid))
        val url = "https://api.bilibili.com/x/web-interface/wbi/view?" + toQuery(params)
        val body = getJson(url) ?: return null
        val data = body.optJSONObject("data") ?: return null
        val title = data.optString("title", "")
        val cid = data.optLong("cid", -1)
        if (cid == -1L) return null
        return Pair(cid, title)
    }

    private suspend fun getVideoDownloadInfo(cid: Long, bvid: String): JSONObject? {
        val params = wbiSign(mapOf("bvid" to bvid, "cid" to cid.toString(), "fnval" to "16"))
        val url = "https://api.bilibili.com/x/player/wbi/playurl?" + toQuery(params)
        val json = getJson(url) ?: return null
        val data = json.optJSONObject("data") ?: return null
        if (data.has("v_voucher")) {
            println("访问被风控")
            return null
        }
        return data.optJSONObject("dash")
    }

    private fun extractDownloadUrl(dash: JSONObject): Triple<String, String, Boolean> {
        val flac = dash.optJSONObject("flac")
        return if (flac == null || flac.optJSONObject("audio") == null) {
            val audio = dash.optJSONArray("audio")?.optJSONObject(0)
            val baseUrl = audio?.optString("baseUrl", "") ?: ""
            val backupUrl = audio?.optJSONArray("backupUrl")?.optString(0, "") ?: ""
            Triple(backupUrl, baseUrl, false)
        } else {
            val audio = flac.optJSONObject("audio")
            val baseUrl = audio?.optString("baseUrl", "") ?: ""
            val backupUrl = audio?.optJSONArray("backupUrl")?.optString(0, "") ?: ""
            Triple(backupUrl, baseUrl, true)
        }
    }

    private suspend fun translateAvidToBvid(aid: String): String {
        val avid = aid.removePrefix("AV").removePrefix("av")
        val url = "https://api.bilibili.com/x/web-interface/view?aid=$avid"
        val json = getJson(url) ?: return ""
        return json.optJSONObject("data")?.optString("bvid", "") ?: ""
    }

    private suspend fun getMixinKey(): String {
        val url = "https://api.bilibili.com/x/web-interface/nav"
        val json = getJson(url) ?: return ""
        val data = json.optJSONObject("data") ?: return ""
        val imgUrl = data.optJSONObject("wbi_img")?.optString("img_url") ?: ""
        val subUrl = data.optJSONObject("wbi_img")?.optString("sub_url") ?: ""
        val imgKey = imgUrl.substringAfterLast('/').substringBefore('.')
        val subKey = subUrl.substringAfterLast('/').substringBefore('.')
        return imgKey + subKey
    }

    private suspend fun wbiSign(params: Map<String, String>): Map<String, String> {
        if (mixinKey == null) {
            mixinKey = rebuildKey(getMixinKey())
        }
        val time = Instant.now().epochSecond.toString()
        val sorted = params.toSortedMap().toMutableMap()
        sorted["wts"] = time
        val query = toQuery(sorted)
        val sign = md5(query + mixinKey!!)
        sorted["w_rid"] = sign
        return sorted
    }

    private fun rebuildKey(raw: String): String {
        val map = listOf(
            46,47,18,2,53,8,23,32,15,50,10,31,58,3,45,35,27,43,5,49,
            33,9,42,19,29,28,14,39,12,38,41,13,37,48,7,16,24,55,40,
            61,26,17,0,1,60,51,30,4,22,25,54,21,56,59,6,63,57,62,11,
            36,20,34,44,52
        )
        return buildString { map.forEach { append(raw[it]) } }
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private suspend fun getJson(url: String): JSONObject? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android)")
            .header("Referer", "https://www.bilibili.com/")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            return@withContext JSONObject(resp.body?.string() ?: return@withContext null)
        }
    }

    private fun toQuery(map: Map<String, String>): String =
        map.entries.joinToString("&") {
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
        }
}
