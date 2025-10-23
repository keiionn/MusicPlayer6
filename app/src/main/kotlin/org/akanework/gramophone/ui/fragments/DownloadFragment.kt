package org.akanework.gramophone.ui.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.utils.Downloader
import org.akanework.gramophone.logic.utils.GetDownloadinfoManager
import org.json.JSONObject

class DownloadFragment : Fragment() {

    private lateinit var editTextDownloadId: TextInputEditText
    private lateinit var editTextFileName: TextInputEditText
    private lateinit var buttonGetDownloadinfo: MaterialButton
    private lateinit var buttonStartDownload: MaterialButton
    private lateinit var resultTextView: android.widget.TextView

    private val getDownloadInfoManager = GetDownloadinfoManager()
    private lateinit var downloader: Downloader
    private var currentVideoData: JSONObject? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_download, container, false)

        editTextDownloadId = view.findViewById(R.id.editTextDownloadId)
        editTextFileName = view.findViewById(R.id.editTextFileName)
        buttonGetDownloadinfo = view.findViewById(R.id.buttonGetDownloadinfo)
        buttonStartDownload = view.findViewById(R.id.buttonStartDownload)
        resultTextView = view.findViewById(R.id.resultTextView)

        downloader = Downloader.getInstance(requireContext())

        setupClickListeners()

        return view
    }

    private fun setupClickListeners() {
        // 获取下载信息按钮
        buttonGetDownloadinfo.setOnClickListener {
            val bvNumber = editTextDownloadId.text.toString().trim()
            if (bvNumber.isEmpty()) {
                showMessage("请输入BV号")
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch {
                resultTextView.text = "正在获取视频信息，请稍候..."
                val info = getDownloadInfoManager.getDownloadInfo(bvNumber)
                if (info == null) {
                    resultTextView.text = "获取失败，请重试"
                    showMessage("无法获取视频信息")
                    return@launch
                }

                // 保存视频信息
                currentVideoData = JSONObject().apply {
                    put("title", info.title)
                    put("base_url", info.baseUrl)
                    put("backup_url", info.backupUrl)
                    put("is_flac", info.isFlac)
                }

                // 设置默认文件名到输入框，安全化并加 .mp3
                val defaultFileName = sanitizeFileName(info.title)
                editTextFileName.setText(defaultFileName)

                resultTextView.text = "成功获取视频信息：\n标题: ${info.title}\n下载地址: ${info.baseUrl.take(80)}".trimIndent()

                showMessage("视频信息已加载")
            }
        }

        // 开始下载按钮
        buttonStartDownload.setOnClickListener {
            var fileName = editTextFileName.text.toString().trim()
            if (fileName.isEmpty()) {
                showMessage("请输入文件名")
                return@setOnClickListener
            }

            // 安全化文件名并确保 .mp3
            fileName = sanitizeFileName(fileName)

            if (currentVideoData == null) {
                showMessage("请先获取下载信息")
                return@setOnClickListener
            }

            // 执行下载
            downloader.downloadFromVideoData(currentVideoData!!, object : Downloader.DownloadListener {
                override fun onDownloadStart(fileName: String) {
                    requireActivity().runOnUiThread {
                        resultTextView.text = "下载开始: $fileName\n进度: 0%"
                        showMessage("下载开始")
                    }
                }

                override fun onDownloadProgress(progress: Int) {
                    requireActivity().runOnUiThread {
                        resultTextView.text = "下载进度: $progress%"
                    }
                }

                override fun onDownloadComplete(filePath: String) {
                    requireActivity().runOnUiThread {
                        resultTextView.text = "下载完成!\n文件保存位置:\n$filePath"
                        showMessage("下载完成")
                        currentVideoData = null
                    }
                }

                override fun onDownloadError(errorMessage: String) {
                    requireActivity().runOnUiThread {
                        resultTextView.text = "下载失败: $errorMessage"
                        showMessage("下载失败: $errorMessage")
                    }
                }
            },fileName)
        }
    }

    /**
     * 移除非法字符并确保以 .mp3 结尾
     */
    private fun sanitizeFileName(name: String): String {
        val safeName = name.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
        return if (safeName.lowercase().endsWith(".mp3")) safeName else "$safeName.mp3"
    }

    private fun showMessage(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        downloader.destroy()
    }
}
