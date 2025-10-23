package org.akanework.gramophone.logic.utils

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.annotation.RequiresApi
import androidx.media3.common.MediaItem
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.*
import org.akanework.gramophone.ui.MainActivity

// SongDeletionHelper.kt
object SongDeletionHelper {

    fun confirmAndDelete(
        context: Context,
        launcher: ActivityResultLauncher<IntentSenderRequest>?,
        item: MediaItem,
        onResult: (Boolean) -> Unit
    ) {
        val title = item.mediaMetadata.title ?: "未知歌曲"
        MaterialAlertDialogBuilder(context)
            .setTitle("删除歌曲")
            .setMessage("确定要删除「$title」吗？此操作将永久删除文件。")
            .setPositiveButton("删除") { _, _ ->
                if (context is MainActivity) {
                    tryDelete(context, item, onResult, wasAuthorized = false)
                } else {
                    onResult(false)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    fun tryDelete(
        activity: MainActivity,
        item: MediaItem,
        onResult: (Boolean) -> Unit,
        wasAuthorized: Boolean = false
    ) {
        val resolver = activity.contentResolver
        val uri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            item.mediaId.toLong()
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                // 直接创建系统级删除请求 — 只弹一次 SAF
                val intentSender = MediaStore.createDeleteRequest(resolver, listOf(uri)).intentSender
                activity.pendingDeletion = Pair(item, onResult)
                activity.deleteIntentSender.launch(IntentSenderRequest.Builder(intentSender).build())
            } catch (e: Exception) {
                Toast.makeText(activity, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
                onResult(false)
            }
        } else {
            // Android 10（API 29）及以下兼容路径
            try {
                val rows = resolver.delete(uri, null, null)
                onResult(rows > 0)
            } catch (e: Exception) {
                Toast.makeText(activity, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
                onResult(false)
            }
        }
    }
}



