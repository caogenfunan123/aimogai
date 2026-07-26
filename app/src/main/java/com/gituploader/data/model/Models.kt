package com.gituploader.data.model

import java.io.File

/**
 * 表示一个待上传的文件条目，包含其在仓库中的相对路径
 */
data class FileEntry(
    val localFile: File,
    val remotePath: String  // 相对于仓库根目录的路径，如 "src/main/java/App.kt"
)

/**
 * 上传状态
 */
sealed class UploadState {
    object Idle : UploadState()
    data class Preparing(val totalFiles: Int) : UploadState()
    data class Uploading(
        val current: Int,
        val total: Int,
        val currentFileName: String,
        val progress: Float  // 0.0 ~ 1.0
    ) : UploadState()
    data class Success(val uploadedCount: Int, val totalFiles: Int) : UploadState()
    data class Error(val message: String, val partialSuccess: Int = 0) : UploadState()
}

/**
 * Git仓库配置信息
 */
data class RepoConfig(
    val repoUrl: String,
    val token: String,
    val branch: String = "main",
    val commitMessage: String = "批量上传文件"
) {
    /**
     * 解析仓库URL，提取owner和repo名
     * 支持格式：
     * - https://github.com/user/repo.git
     * - https://gitee.com/user/repo.git
     * - https://github.com/user/repo
     */
    fun parseRepoInfo(): Pair<String, String>? {
        // 去掉.git后缀和末尾斜杠
        val cleanUrl = repoUrl.removeSuffix(".git").trimEnd('/')
        val parts = cleanUrl.split("/")
        if (parts.size < 2) return null
        return Pair(parts[parts.size - 2], parts[parts.size - 1])
    }

    /**
     * 获取API基础URL
     */
    fun getApiBaseUrl(): String? {
        val (owner, repo) = parseRepoInfo() ?: return null
        return when {
            repoUrl.contains("github.com") ->
                "https://api.github.com/repos/$owner/$repo"
            repoUrl.contains("gitee.com") ->
                "https://gitee.com/api/v5/repos/$owner/$repo"
            repoUrl.contains("gitlab.com") ->
                "https://gitlab.com/api/v4/projects/$owner%2F$repo"
            else -> null
        }
    }

    /**
     * 判断是哪个Git平台
     */
    fun getPlatform(): GitPlatform {
        return when {
            repoUrl.contains("github.com") -> GitPlatform.GITHUB
            repoUrl.contains("gitee.com") -> GitPlatform.GITEE
            repoUrl.contains("gitlab.com") -> GitPlatform.GITLAB
            else -> GitPlatform.UNKNOWN
        }
    }
}

enum class GitPlatform {
    GITHUB, GITEE, GITLAB, UNKNOWN
}
