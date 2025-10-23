package org.akanework.gramophone.logic.utils

import android.util.Log
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * DownloadFragment:
 *   The function of this manager is connect to server
 * @author keiionn
 */

class WebsocketManager private constructor() {

    companion object {
        @Volatile
        private var INSTANCE: WebsocketManager? = null

        fun getInstance(): WebsocketManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WebsocketManager().also { INSTANCE = it }
            }
        }
    }

    // 创建 OkHttpClient 并设置超时
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    interface WebSocketCallback {
        fun onConnected()
        fun onMessageReceived(message: String)
        fun onDisconnected()
        fun onError(error: String)
    }

    fun connect(serverUrl: String, callback: WebSocketCallback) {
        val request = Request.Builder()
            .url("wss://$serverUrl/ws")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocket", "已连接到服务器")
                callback.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("WebSocket", "收到消息: $text")
                callback.onMessageReceived(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Log.d("WebSocket", "收到二进制消息")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocket", "连接关闭中: $code - $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocket", "连接已关闭")
                callback.onDisconnected()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocket", "连接失败: ${t.message}")
                callback.onError(t.message ?: "未知错误")
            }
        })
    }

    fun sendVideoRequest(videoId: String) {
        val message = JSONObject().apply {
            put("video_id", videoId)
        }.toString()

        webSocket?.send(message)
        Log.d("WebSocket", "已发送视频请求: $videoId")
    }

    fun disconnect() {
        webSocket?.close(1000, "正常关闭")
        webSocket = null
    }

    fun isConnected(): Boolean {
        return webSocket != null
    }
}