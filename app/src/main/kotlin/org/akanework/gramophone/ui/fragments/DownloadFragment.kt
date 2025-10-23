package org.akanework.gramophone.ui.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.utils.WebsocketManager
import org.akanework.gramophone.logic.utils.Downloader
import org.json.JSONObject

/**
 * DownloadFragment:
 *   The function of this fragment is downloading mp3 file
 * @author keiionn
 */
class DownloadFragment : BaseFragment(null) {

    private lateinit var editTextDownloadId: TextInputEditText
    private lateinit var editTextFileName: TextInputEditText
    private lateinit var buttonSubmitId: MaterialButton
    private lateinit var buttonStartDownload: MaterialButton
    private lateinit var buttonSave: MaterialButton
    private lateinit var resultTextView: android.widget.TextView

    private val websocketManager = WebsocketManager.getInstance()
    private lateinit var downloader: Downloader

    private var currentVideoData: JSONObject? = null
    private var isRequestInProgress = false
    private var currentVideoTitle: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 加载布局文件
        val view = inflater.inflate(R.layout.fragment_download, container, false)

        // 初始化下载器
        downloader = Downloader.getInstance(requireContext())

        // 初始化视图
        initViews(view)

        // 设置点击监听器
        setupClickListeners()

        return view
    }

    private fun initViews(view: View) {
        editTextDownloadId = view.findViewById(R.id.editTextDownloadId)
        editTextFileName = view.findViewById(R.id.editTextFileName)
        resultTextView = view.findViewById(R.id.resultTextView)
        buttonSubmitId = view.findViewById(R.id.buttonSubmitId)
        buttonStartDownload = view.findViewById(R.id.buttonStartDownload)
        buttonSave = view.findViewById(R.id.buttonSave)

        // 初始状态设置
        updateButtonStates()
    }

    private fun setupClickListeners() {
        // 提交BV号按钮
        buttonSubmitId.setOnClickListener {
            val bvNumber = editTextDownloadId.text.toString().trim()
            if (bvNumber.isNotEmpty()) {
                // 开始连接服务器并获取信息
                startVideoInfoRequest(bvNumber)
            } else {
                showMessage("请输入BV号")
            }
        }

        // 开始下载按钮
        buttonStartDownload.setOnClickListener {
            startDownload()
        }

        // 保存按钮 - 修改为复制当前文件名到输入栏
        buttonSave.setOnClickListener {
            copyCurrentFileNameToInput()
        }
    }

    /**
     * 复制当前文件名到输入栏
     */
    private fun copyCurrentFileNameToInput() {
        try {
            // 获取当前文件名（不含扩展名）
            val currentFileName = downloader.getCurrentFileNameWithoutExtension()

            if (currentFileName.isNotEmpty()) {
                // 将当前文件名设置到输入栏
                editTextFileName.setText(currentFileName)

                // 将光标移动到文本末尾
                editTextFileName.setSelection(editTextFileName.text?.length ?: 0)

                // 显示提示
                showMessage("当前文件名已复制到输入栏")

                // 可选：自动聚焦到文件名输入框
                editTextFileName.requestFocus()
            } else {
                // 如果没有正在进行的下载，检查是否有从服务器获取的视频标题
                if (currentVideoTitle.isNotEmpty()) {
                    val safeFileName = currentVideoTitle.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
                    editTextFileName.setText(safeFileName)
                    editTextFileName.setSelection(editTextFileName.text?.length ?: 0)
                    editTextFileName.requestFocus()
                    showMessage("视频标题已复制到输入栏")
                } else {
                    showMessage("没有可用的文件名")
                }
            }
        } catch (e: Exception) {
            showMessage("复制文件名失败: ${e.message}")
        }
    }

    @SuppressLint("SetTextI18n")
    private fun startVideoInfoRequest(bvNumber: String) {
        if (isRequestInProgress) {
            showMessage("已有请求在处理中，请稍候")
            return
        }

        isRequestInProgress = true
        updateButtonStates()

        // 显示处理中状态
        resultTextView.text = "正在连接服务器并获取视频信息...\nBV号: $bvNumber"

        // 连接服务器
        connectToServer(bvNumber)
    }

    private fun connectToServer(bvNumber: String) {
        val serverUrl = getString(R.string.server_url)

        websocketManager.connect(serverUrl, object : WebsocketManager.WebSocketCallback {
            @SuppressLint("SetTextI18n")
            override fun onConnected() {
                requireActivity().runOnUiThread {
                    resultTextView.text = "已成功连接到服务器\n\n正在请求视频信息..."
                    // 连接成功后立即发送视频请求
                    websocketManager.sendVideoRequest(bvNumber)
                }
            }

            override fun onMessageReceived(message: String) {
                requireActivity().runOnUiThread {
                    handleServerMessage(message, bvNumber)
                }
            }

            override fun onDisconnected() {
                requireActivity().runOnUiThread {
                    // 如果是正常完成请求后的断开，不显示错误
                    if (!isRequestInProgress) {
                        resultTextView.append("\n\n连接已正常关闭")
                    } else {
                        resultTextView.text = "连接意外断开，请重试"
                        resetRequestState()
                    }
                }
            }

            @SuppressLint("SetTextI18n")
            override fun onError(error: String) {
                requireActivity().runOnUiThread {
                    resultTextView.text = "连接错误: $error\n\n请检查服务器状态后重试"
                    showMessage("连接失败: $error")
                    resetRequestState()
                }
            }
        })
    }

    @SuppressLint("SetTextI18n")
    private fun handleServerMessage(message: String, bvNumber: String) {
        try {
            val jsonResponse = JSONObject(message)

            when {
                jsonResponse.has("error") -> {
                    val error = jsonResponse.getString("error")
                    resultTextView.text = "服务器返回错误:\n$error"
                    showMessage("获取视频信息失败: $error")
                    // 请求完成，断开连接
                    completeRequest()
                }

                jsonResponse.has("base_url") && jsonResponse.has("title") -> {
                    // 成功获取到视频数据
                    currentVideoData = jsonResponse
                    val title = jsonResponse.getString("title")
                    val baseUrl = jsonResponse.getString("base_url")

                    // 保存视频标题用于生成默认文件名
                    currentVideoTitle = title

                    resultTextView.text = """
                        成功获取视频信息:
                        
                        标题: $title
                        BV号: $bvNumber
                        下载URL: ${baseUrl}...
                        
                        点击"开始下载"按钮开始下载
                    """.trimIndent()

                    // 自动设置默认文件名（基于视频标题）
                    setDefaultFileName(title)

                    showMessage("视频信息获取成功")
                    // 请求完成，断开连接
                    completeRequest()
                }

                jsonResponse.has("status") -> {
                    // 处理其他状态消息
                    val status = jsonResponse.getString("status")
                    resultTextView.append("\n\n服务器状态: $status")

                    // 如果是完成状态，断开连接
                    if (status == "completed" || status == "finished") {
                        completeRequest()
                    }
                }

                else -> {
                    resultTextView.append("\n\n收到未知消息: $message")
                }
            }
        } catch (e: Exception) {
            resultTextView.text = "解析服务器消息失败:\n${e.message}\n\n原始消息: $message"
            completeRequest()
        }
    }

    private fun setDefaultFileName(title: String) {
        // 生成安全的文件名（移除非法字符）
        val safeFileName = title.replace("[\\\\/:*?\"<>|]".toRegex(), "_")

        // 确保文件扩展名为.mp3
        val finalFileName = if (safeFileName.endsWith(".mp3")) {
            safeFileName
        } else {
            "$safeFileName.mp3"
        }

        // 设置到文件名输入框
        editTextFileName.setText(finalFileName)
    }

    private fun completeRequest() {
        // 断开WebSocket连接
        websocketManager.disconnect()

        // 重置请求状态
        isRequestInProgress = false
        updateButtonStates()
    }

    private fun resetRequestState() {
        isRequestInProgress = false
        updateButtonStates()
    }

    private fun startDownload() {
        var fileName = editTextFileName.text.toString().trim()

        if (fileName.isEmpty()) {
            // 如果用户没有输入文件名，使用默认的视频标题
            if (currentVideoTitle.isNotEmpty()) {
                setDefaultFileName(currentVideoTitle)
                fileName = editTextFileName.text.toString().trim()
            } else {
                showMessage("请输入文件名或先获取视频信息")
                return
            }
        }

        if (currentVideoData == null) {
            showMessage("请先获取视频信息")
            return
        }

        // 确保文件名以.mp3结尾
        fileName = ensureMp3Extension(fileName)
        editTextFileName.setText(fileName)

        // 执行下载逻辑
        performDownload(fileName)
    }

    private fun ensureMp3Extension(fileName: String): String {
        return if (fileName.endsWith(".mp3")) {
            fileName
        } else {
            "$fileName.mp3"
        }
    }

    @SuppressLint("SetTextI18n")
    private fun performDownload(fileName: String) {
        resultTextView.text = "开始下载...\n文件名: $fileName\n\n下载准备中..."

        val videoData = currentVideoData ?: run {
            showMessage("视频数据不存在")
            return
        }

        // 使用Downloader开始下载
        downloader.downloadFromVideoData(videoData, object : Downloader.DownloadListener {
            override fun onDownloadStart(fileName: String) {
                requireActivity().runOnUiThread {
                    resultTextView.text = "下载已开始:\n文件名: $fileName\n\n下载进度: 0%"
                    showMessage("下载开始")
                }
            }

            override fun onDownloadProgress(progress: Int) {
                requireActivity().runOnUiThread {
                    val currentText = resultTextView.text.toString()
                    if (currentText.contains("下载进度:")) {
                        resultTextView.text = currentText.substringBefore("下载进度:") + "下载进度: $progress%"
                    } else {
                        resultTextView.append("\n下载进度: $progress%")
                    }
                }
            }

            override fun onDownloadComplete(filePath: String) {
                requireActivity().runOnUiThread {
                    resultTextView.text = "下载完成!\n\n文件保存位置:\n$filePath\n\n可以开始新的下载任务"
                    showMessage("下载完成")

                    // 重置当前视频数据，允许新的下载
                    currentVideoData = null
                    currentVideoTitle = ""
                    updateButtonStates()
                }
            }

            override fun onDownloadError(errorMessage: String) {
                requireActivity().runOnUiThread {
                    resultTextView.text = "下载失败:\n$errorMessage\n\n请检查网络连接后重试"
                    showMessage("下载失败: $errorMessage")
                }
            }
        })
    }

    private fun updateButtonStates() {
        // 提交按钮：只有在没有请求进行时才可用
        buttonSubmitId.isEnabled = !isRequestInProgress

        // 开始下载按钮：只有在有视频数据且没有请求进行时才可用
        buttonStartDownload.isEnabled = currentVideoData != null && !isRequestInProgress

        // 更新提交按钮文本
        buttonSubmitId.text = if (isRequestInProgress) "处理中..." else "提交BV号"
    }

    private fun showMessage(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 清理资源
        websocketManager.disconnect()
        downloader.destroy()
    }

    override fun onPause() {
        super.onPause()
        // 暂停时断开WebSocket连接
        websocketManager.disconnect()
        // 重置请求状态
        isRequestInProgress = false
    }
}