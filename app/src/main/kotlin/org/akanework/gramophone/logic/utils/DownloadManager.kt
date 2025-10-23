package org.akanework.gramophone.logic.utils

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.File

/**
 * DownloadFragment:
 *   The function of this manager is downloading the context of url
 * @author keiionn
 */

class Downloader private constructor(private val context: Context) {

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: Downloader? = null

        fun getInstance(context: Context): Downloader {
            return instance ?: synchronized(this) {
                instance ?: Downloader(context.applicationContext).also { instance = it }
            }
        }
    }

    private val downloadManager: DownloadManager by lazy {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }

    private var currentDownloadId: Long = -1
    private var currentFileName: String = ""
    private var downloadListener: DownloadListener? = null

    // 下载状态监听接口
    interface DownloadListener {
        fun onDownloadStart(fileName: String)
        fun onDownloadProgress(progress: Int)
        fun onDownloadComplete(filePath: String)
        fun onDownloadError(errorMessage: String)
    }

    // 下载完成广播接收器
    private val downloadCompleteReceiver = object : BroadcastReceiver() {
        @SuppressLint("Range")
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == currentDownloadId) {
                val query = DownloadManager.Query().setFilterById(id)
                val cursor = downloadManager.query(query)

                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            val uriString = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
                            val filePath = Uri.parse(uriString).path ?: ""
                            downloadListener?.onDownloadComplete(filePath)

                            // 扫描媒体文件，使其出现在系统媒体库中
                            scanMediaFile(File(filePath))
                        }
                        DownloadManager.STATUS_FAILED -> {
                            val reason = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON))
                            val errorMsg = when (reason) {
                                DownloadManager.ERROR_UNKNOWN -> "未知错误"
                                DownloadManager.ERROR_FILE_ERROR -> "文件错误"
                                DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "HTTP错误"
                                DownloadManager.ERROR_HTTP_DATA_ERROR -> "HTTP数据错误"
                                DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "重定向过多"
                                DownloadManager.ERROR_INSUFFICIENT_SPACE -> "存储空间不足"
                                DownloadManager.ERROR_DEVICE_NOT_FOUND -> "设备未找到"
                                else -> "下载失败 (错误代码: $reason)"
                            }
                            downloadListener?.onDownloadError(errorMsg)
                        }
                    }
                }
                cursor.close()
            }
        }
    }

    /**
     * 从视频数据下载音频
     * @param videoData 包含base_url和title的JSON对象
     * @param listener 下载监听器
     */
    fun downloadFromVideoData(videoData: JSONObject, listener: DownloadListener? = null) {
        this.downloadListener = listener

        try {
            // 提取下载URL和标题
            val downloadUrl = videoData.getString("base_url")
            val videoTitle = videoData.getString("title")

            // 生成安全的文件名
            currentFileName = generateSafeFileName(videoTitle)

            // 开始下载
            startDownload(downloadUrl, currentFileName)

        } catch (e: Exception) {
            listener?.onDownloadError("数据解析错误: ${e.message}")
        }
    }

    /**
     * 直接使用URL和标题下载
     * @param baseUrl 下载URL
     * @param title 视频标题（用于生成文件名）
     * @param listener 下载监听器
     */
    fun downloadFromBaseUrl(baseUrl: String, title: String, listener: DownloadListener? = null) {
        this.downloadListener = listener
        currentFileName = generateSafeFileName(title)
        startDownload(baseUrl, currentFileName)
    }

    /**
     * 开始下载
     */
    private fun startDownload(downloadUrl: String, fileName: String) {
        try {
            // 创建下载目录（如果不存在）
            val downloadDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "MyMusicDownloads"
            )
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }

            // 确定文件扩展名
            val fileExtension =  "mp3"
            val fullFileName = if (fileName.endsWith(".$fileExtension")) {
                fileName
            } else {
                "$fileName.$fileExtension"
            }

            // 创建下载请求
            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                // 设置网络类型
                setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                setAllowedOverRoaming(false)

                // 设置通知栏
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setTitle("下载音频")
                setDescription("正在下载: ${fileName.substringBeforeLast(".")}")

                // 设置下载路径
                val file = File(downloadDir, fullFileName)
                setDestinationUri(Uri.fromFile(file))

                // 设置请求头（模拟浏览器访问）
                addRequestHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                addRequestHeader("Referer", "https://www.bilibili.com/")
            }

            // 执行下载
            currentDownloadId = downloadManager.enqueue(request)

            // 注册下载完成广播
            ContextCompat.registerReceiver(
                context,
                downloadCompleteReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

            // 通知开始下载
            downloadListener?.onDownloadStart(fullFileName)

            // 开始进度跟踪
            startProgressTracking()

        } catch (e: Exception) {
            downloadListener?.onDownloadError("下载初始化失败: ${e.message}")
        }
    }

    /**
     * 开始进度跟踪
     */
    @SuppressLint("Range")
    private fun startProgressTracking() {
        Thread {
            var previousProgress = -1
            var isDownloading = true

            while (isDownloading && currentDownloadId != -1L) {
                try {
                    val cursor = downloadManager.query(DownloadManager.Query().setFilterById(currentDownloadId))
                    if (cursor.moveToFirst()) {
                        when (cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                isDownloading = false
                            }
                            DownloadManager.STATUS_FAILED -> {
                                isDownloading = false
                            }
                            else -> {
                                // 计算进度
                                val totalSize = cursor.getLong(
                                    cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                                )
                                val downloadedSize = cursor.getLong(
                                    cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                                )

                                if (totalSize > 0) {
                                    val progress = (downloadedSize * 100 / totalSize).toInt()
                                    if (progress != previousProgress) {
                                        previousProgress = progress
                                        downloadListener?.onDownloadProgress(progress)
                                    }
                                }
                            }
                        }
                    }
                    cursor.close()

                    Thread.sleep(1000) // 每秒更新一次进度
                } catch (e: Exception) {
                    // 忽略查询异常，继续尝试
                }
            }
        }.start()
    }

    /**
     * 生成安全的文件名
     */
    private fun generateSafeFileName(title: String): String {
        // 移除或替换文件名中的非法字符
        return title.replace("[\\\\/:*?\"<>|]".toRegex(), "_") + ".mp3"
    }

    /**
     * 从URL获取文件扩展名
     */
    private fun getFileExtensionFromUrl(url: String): String? {
        return try {
            val extension = MimeTypeMap.getFileExtensionFromUrl(url)
            if (extension.isNullOrEmpty()) {
                // 尝试从URL路径解析
                Uri.parse(url).lastPathSegment?.substringAfterLast('.')
            } else {
                extension
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 扫描媒体文件，使其出现在系统媒体库中
     */
    private fun scanMediaFile(file: File) {
        try {
            val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            intent.data = Uri.fromFile(file)
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            // 忽略扫描异常
        }
    }

    /**
     * 取消当前下载
     */
    fun cancelCurrentDownload() {
        if (currentDownloadId != -1L) {
            downloadManager.remove(currentDownloadId)
            currentDownloadId = -1
            try {
                context.unregisterReceiver(downloadCompleteReceiver)
            } catch (e: Exception) {
                // 忽略未注册的异常
            }
        }
    }

    /**
     * 获取当前下载的文件名
     */
    fun getCurrentFileNameWithoutExtension(): String = currentFileName

    /**
     * 检查是否有正在进行的下载
     */
    fun isDownloading(): Boolean = currentDownloadId != -1L

    /**
     * 清理资源
     */
    fun destroy() {
        cancelCurrentDownload()
    }
}