package com.gituploader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.gituploader.data.GitApiService
import com.gituploader.data.TokenManager
import com.gituploader.data.model.FileEntry
import com.gituploader.data.model.RepoConfig
import com.gituploader.data.model.UploadState
import com.gituploader.ui.screens.MainScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun GitUploaderAppContent(tokenManager: TokenManager) {
    val context = LocalContext.current
    var uploadState by remember { mutableStateOf<UploadState>(UploadState.Idle) }
    val apiService = remember { GitApiService() }

    // 请求存储权限
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            Toast.makeText(context, "需要存储权限才能选择文件夹", Toast.LENGTH_SHORT).show()
        }
    }

    // 检查并请求权限
    LaunchedEffect(Unit) {
        val needsPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            !android.os.Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        }

        if (needsPermission && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && needsPermission) {
            try {
                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = android.net.Uri.parse("package:${context.packageName}")
                context.startActivity(intent)
            } catch (e: Exception) {
                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                context.startActivity(intent)
            }
        }
    }

    MainScreen(
        tokenManager = tokenManager,
        uploadState = uploadState,
        onUpload = { repoUrl, token, branch, commitMsg, files ->
            // 保存配置
            CoroutineScope(Dispatchers.IO).launch {
                tokenManager.saveToken(token)
                tokenManager.saveRepoUrl(repoUrl)
                tokenManager.saveBranch(branch)
                tokenManager.saveCommitMessage(commitMsg)
            }

            val config = RepoConfig(
                repoUrl = repoUrl,
                token = token,
                branch = branch,
                commitMessage = commitMsg
            )

            // 开始上传
            CoroutineScope(Dispatchers.Main).launch {
                uploadState = UploadState.Preparing(files.size)

                val result = apiService.uploadFiles(
                    config = config,
                    files = files,
                    onProgress = { current, total, fileName, progress ->
                        uploadState = UploadState.Uploading(current, total, fileName, progress)
                    }
                )

                uploadState = if (result.isSuccess) {
                    UploadState.Success(files.size, files.size)
                } else {
                    UploadState.Error(
                        message = result.exceptionOrNull()?.message ?: "未知错误"
                    )
                }
            }
        }
    )
}
