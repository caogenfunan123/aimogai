package com.gituploader.ui.screens

import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gituploader.data.FileScanner
import com.gituploader.data.TokenManager
import com.gituploader.data.model.FileEntry
import com.gituploader.data.model.UploadState
import com.gituploader.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    tokenManager: TokenManager,
    onUpload: (repoUrl: String, token: String, branch: String, commitMsg: String, files: List<FileEntry>) -> Unit,
    uploadState: UploadState
) {
    val context = LocalContext.current

    // 从DataStore加载保存的值
    val savedToken by tokenManager.savedToken.collectAsStateWithLifecycle(initialValue = null)
    val savedRepoUrl by tokenManager.savedRepoUrl.collectAsStateWithLifecycle(initialValue = null)
    val savedBranch by tokenManager.savedBranch.collectAsStateWithLifecycle(initialValue = null)
    val savedCommitMsg by tokenManager.savedCommitMessage.collectAsStateWithLifecycle(initialValue = null)

    var repoUrl by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("main") }
    var commitMessage by remember { mutableStateOf("批量上传文件") }
    var showToken by remember { mutableStateOf(false) }

    // 选中的文件夹
    var selectedFolder by remember { mutableStateOf<File?>(null) }
    var fileEntries by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // 同步从DataStore加载的值
    LaunchedEffect(savedToken) {
        if (savedToken != null && token.isEmpty()) token = savedToken!!
    }
    LaunchedEffect(savedRepoUrl) {
        if (savedRepoUrl != null && repoUrl.isEmpty()) repoUrl = savedRepoUrl!!
    }
    LaunchedEffect(savedBranch) {
        if (savedBranch != null) branch = savedBranch!!
    }
    LaunchedEffect(savedCommitMsg) {
        if (savedCommitMsg != null) commitMessage = savedCommitMsg!!
    }

    // 文件夹选择器
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            // SAF方式: 尝试从uri获取路径
            val path = it.path ?: it.lastPathSegment ?: ""
            // 对于Android 11+，使用SAF URI需要特殊处理
            // 尝试使用传统路径
            val folder = if (path.contains(":")) {
                val segments = path.split(":")
                if (segments.size >= 2) {
                    File(Environment.getExternalStorageDirectory(), segments.last())
                } else {
                    File(Environment.getExternalStorageDirectory().path)
                }
            } else {
                File(path)
            }

            if (folder.exists() && folder.isDirectory) {
                selectedFolder = folder
                fileEntries = FileScanner.scanDirectory(folder)
                Toast.makeText(
                    context,
                    "已选择 ${fileEntries.size} 个文件",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                // 如果传统路径不可用，尝试通过MediaStore
                // 使用SAF方式直接从URI内容读取
                selectedFolder = folder
                // 作为降级方案，只显示提示
                Toast.makeText(
                    context,
                    "文件夹已选择（请使用Android 10以下设备获取完整路径支持）",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val isUploading = uploadState is UploadState.Uploading

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Git 批量上传",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ===== 仓库配置区域 =====
            item {
                SectionHeader("仓库配置")
            }

            // 仓库地址
            item {
                OutlinedTextField(
                    value = repoUrl,
                    onValueChange = { repoUrl = it },
                    label = { Text("仓库地址") },
                    placeholder = { Text("https://github.com/user/repo.git") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = DarkPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    ),
                    singleLine = true,
                    enabled = !isUploading
                )
            }

            // 令牌输入
            item {
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("HTTPS访问令牌") },
                    placeholder = { Text("ghp_xxxxx 或 gitee令牌") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = DarkPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    ),
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(
                                if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "切换令牌可见性"
                            )
                        }
                    },
                    singleLine = true,
                    enabled = !isUploading
                )
            }

            // 保存令牌按钮
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                tokenManager.saveToken(token)
                                tokenManager.saveRepoUrl(repoUrl)
                                tokenManager.saveBranch(branch)
                                tokenManager.saveCommitMessage(commitMessage)
                            }
                            Toast.makeText(context, "令牌和配置已保存", Toast.LENGTH_SHORT).show()
                        },
                        enabled = token.isNotEmpty() && !isUploading,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("保存令牌")
                    }
                }
            }

            // 分支和提交信息
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = branch,
                        onValueChange = { branch = it },
                        label = { Text("分支") },
                        placeholder = { Text("main") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        enabled = !isUploading
                    )
                    OutlinedTextField(
                        value = commitMessage,
                        onValueChange = { commitMessage = it },
                        label = { Text("提交信息") },
                        placeholder = { Text("批量上传文件") },
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        enabled = !isUploading
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // ===== 文件夹选择区域 =====
            item {
                SectionHeader("选择文件夹")
            }

            // 文件夹选择按钮
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isUploading) {
                            folderPickerLauncher.launch(null)
                        },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = DarkSurface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = DarkPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            if (selectedFolder != null) {
                                Text(
                                    selectedFolder!!.name,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 16.sp
                                )
                                Text(
                                    "共 ${fileEntries.size} 个文件 - ${FileScanner.formatFileSize(FileScanner.calculateTotalSize(fileEntries))}",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            } else {
                                Text(
                                    "点击选择文件夹",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }

            // 文件列表预览
            if (fileEntries.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "文件列表 (${fileEntries.size})",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        if (fileEntries.size > 10) {
                            Text(
                                "显示前10个...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                items(fileEntries.take(10)) { entry ->
                    FileItemRow(entry)
                }

                if (fileEntries.size > 10) {
                    item {
                        Text(
                            "还有 ${fileEntries.size - 10} 个文件未显示...",
                            modifier = Modifier.padding(vertical = 4.dp),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // ===== 上传按钮 =====
            item {
                Button(
                    onClick = {
                        if (repoUrl.isEmpty() || token.isEmpty()) {
                            Toast.makeText(context, "请填写仓库地址和令牌", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (fileEntries.isEmpty()) {
                            Toast.makeText(context, "请选择包含文件的文件夹", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        onUpload(repoUrl, token, branch, commitMessage, fileEntries)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = !isUploading && repoUrl.isNotEmpty() && token.isNotEmpty() && fileEntries.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkPrimary
                    )
                ) {
                    Icon(
                        if (isUploading) Icons.Default.CloudUpload else Icons.Default.Upload,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isUploading) "上传中..." else "开始上传",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ===== 上传进度 =====
            when (uploadState) {
                is UploadState.Preparing -> {
                    item {
                        ProgressCard(
                            title = "准备上传...",
                            subtitle = "正在扫描 ${uploadState.totalFiles} 个文件",
                            progress = 0f
                        )
                    }
                }
                is UploadState.Uploading -> {
                    item {
                        ProgressCard(
                            title = "上传中",
                            subtitle = "${uploadState.current}/${uploadState.total} - ${uploadState.currentFileName}",
                            progress = uploadState.progress
                        )
                    }
                }
                is UploadState.Success -> {
                    item {
                        SuccessCard(
                            uploaded = uploadState.uploadedCount,
                            total = uploadState.totalFiles
                        )
                    }
                }
                is UploadState.Error -> {
                    item {
                        ErrorCard(
                            message = uploadState.message,
                            partialSuccess = uploadState.partialSuccess
                        )
                    }
                }
                UploadState.Idle -> {}
            }

            // 底部间距
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun FileItemRow(entry: FileEntry) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkSurface.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = DarkSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                entry.remotePath,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                FileScanner.formatFileSize(entry.localFile.length()),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun ProgressCard(
    title: String,
    subtitle: String,
    progress: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkSurface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.Medium)
                Text(
                    "${(progress * 100).toInt()}%",
                    color = DarkPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = DarkPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SuccessCard(uploaded: Int, total: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkSuccess.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = DarkSuccess,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    "上传成功!",
                    fontWeight = FontWeight.Bold,
                    color = DarkSuccess
                )
                Text(
                    "已成功上传 $uploaded/$total 个文件",
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, partialSuccess: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkError.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = DarkError,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    "上传失败",
                    fontWeight = FontWeight.Bold,
                    color = DarkError
                )
                Text(
                    message,
                    fontSize = 13.sp,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
                if (partialSuccess > 0) {
                    Text(
                        "已部分上传 $partialSuccess 个文件",
                        fontSize = 12.sp,
                        color = DarkWarning
                    )
                }
            }
        }
    }
}
