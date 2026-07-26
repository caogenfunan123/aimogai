package com.gituploader.data

import com.gituploader.data.model.FileEntry
import com.gituploader.data.model.GitPlatform
import com.gituploader.data.model.RepoConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Git API 服务类，支持通过HTTPS令牌上传文件到GitHub/Gitee/GitLab
 */
class GitApiService {

    companion object {
        private const val TAG = "GitApiService"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * 检查仓库是否存在，并获取最新commit SHA
     */
    suspend fun getLatestCommitSha(config: RepoConfig): Result<String> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = config.getApiBaseUrl() ?: return@withContext Result.failure(
                IllegalArgumentException("不支持的Git平台或URL格式错误")
            )

            val request = when (config.getPlatform()) {
                GitPlatform.GITHUB, GitPlatform.GITLAB -> {
                    val url = "$baseUrl/git/refs/heads/${config.branch}"
                    Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer ${config.token}")
                        .addHeader("Accept", "application/vnd.github.v3+json")
                        .get()
                        .build()
                }
                GitPlatform.GITEE -> {
                    val url = "$baseUrl/branches/${config.branch}"
                    Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer ${config.token}")
                        .get()
                        .build()
                }
                GitPlatform.UNKNOWN -> {
                    return@withContext Result.failure(
                        IllegalArgumentException("不支持的Git平台")
                    )
                }
            }

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.failure(
                Exception("获取分支信息失败：响应为空")
            )

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("获取分支信息失败：${response.code} - $body")
                )
            }

            val json = JSONObject(body)
            val sha = when (config.getPlatform()) {
                GitPlatform.GITHUB, GitPlatform.GITLAB -> {
                    json.getJSONObject("object").getString("sha")
                }
                GitPlatform.GITEE -> {
                    json.getJSONObject("commit").getString("sha")
                }
                GitPlatform.UNKNOWN -> null
            }

            if (sha != null) {
                Result.success(sha)
            } else {
                Result.failure(Exception("无法解析commit SHA"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 使用Git Data API创建Tree并提交
     * 对于大量文件，采用批量tree方式一次性提交
     */
    suspend fun uploadFiles(
        config: RepoConfig,
        files: List<FileEntry>,
        onProgress: (Int, Int, String, Float) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val platform = config.getPlatform()
            val baseUrl = config.getApiBaseUrl() ?: return@withContext Result.failure(
                IllegalArgumentException("不支持的Git平台或URL格式错误")
            )

            // 步骤1: 获取最新commit SHA
            onProgress(0, files.size, "获取分支信息...", 0f)
            val latestShaResult = getLatestCommitSha(config)
            val latestSha = latestShaResult.getOrThrow()

            // 步骤2: 逐个创建blob
            val treeItems = mutableListOf<JSONObject>()
            for ((index, entry) in files.withIndex()) {
                onProgress(index + 1, files.size, entry.remotePath, (index + 1).toFloat() / files.size * 0.6f)

                val fileContent = entry.localFile.readBytes()
                val base64Content = Base64.getEncoder().encodeToString(fileContent)

                val blobResult = createBlob(config, base64Content)
                if (blobResult.isFailure) {
                    return@withContext Result.failure(
                        Exception("创建Blob失败: ${entry.remotePath} - ${blobResult.exceptionOrNull()?.message}")
                    )
                }

                val blobSha = blobResult.getOrThrow()
                val treeItem = JSONObject().apply {
                    put("path", entry.remotePath)
                    put("mode", "100644")
                    put("type", "blob")
                    put("sha", blobSha)
                }
                treeItems.add(treeItem)
            }

            // 步骤3: 创建Tree
            onProgress(files.size, files.size, "创建Tree...", 0.7f)
            val treeResult = createTree(config, treeItems, latestSha)
            val treeSha = treeResult.getOrThrow()

            // 步骤4: 创建Commit
            onProgress(files.size, files.size, "创建提交...", 0.85f)
            val commitResult = createCommit(config, treeSha, latestSha)
            val commitSha = commitResult.getOrThrow()

            // 步骤5: 更新分支引用
            onProgress(files.size, files.size, "更新分支...", 0.95f)
            val updateResult = updateBranchRef(config, commitSha)
            if (updateResult.isFailure) {
                return@withContext Result.failure(
                    Exception("更新分支失败: ${updateResult.exceptionOrNull()?.message}")
                )
            }

            onProgress(files.size, files.size, "上传完成!", 1f)
            Result.success(files.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 创建Blob对象
     */
    private fun createBlob(config: RepoConfig, base64Content: String): Result<String> {
        val baseUrl = config.getApiBaseUrl()!!
        val url = "$baseUrl/git/blobs"

        val jsonBody = JSONObject().apply {
            put("content", base64Content)
            put("encoding", "base64")
        }

        val request = buildRequest(config, url, jsonBody)
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return Result.failure(Exception("创建Blob响应为空"))

        if (!response.isSuccessful) {
            return Result.failure(Exception("创建Blob失败: ${response.code} - $body"))
        }

        val json = JSONObject(body)
        return Result.success(json.getString("sha"))
    }

    /**
     * 创建Tree对象
     */
    private fun createTree(config: RepoConfig, treeItems: List<JSONObject>, baseTree: String): Result<String> {
        val baseUrl = config.getApiBaseUrl()!!
        val url = "$baseUrl/git/trees"

        val jsonBody = JSONObject().apply {
            put("base_tree", baseTree)
            put("tree", treeItems)
        }

        val request = buildRequest(config, url, jsonBody)
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return Result.failure(Exception("创建Tree响应为空"))

        if (!response.isSuccessful) {
            return Result.failure(Exception("创建Tree失败: ${response.code} - $body"))
        }

        val json = JSONObject(body)
        return Result.success(json.getString("sha"))
    }

    /**
     * 创建Commit
     */
    private fun createCommit(config: RepoConfig, treeSha: String, parentSha: String): Result<String> {
        val baseUrl = config.getApiBaseUrl()!!
        val url = "$baseUrl/git/commits"

        val jsonBody = JSONObject().apply {
            put("message", config.commitMessage)
            put("tree", treeSha)
            put("parents", listOf(parentSha))
        }

        val request = buildRequest(config, url, jsonBody)
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return Result.failure(Exception("创建Commit响应为空"))

        if (!response.isSuccessful) {
            return Result.failure(Exception("创建Commit失败: ${response.code} - $body"))
        }

        val json = JSONObject(body)
        return Result.success(json.getString("sha"))
    }

    /**
     * 更新分支引用
     */
    private fun updateBranchRef(config: RepoConfig, commitSha: String): Result<Unit> {
        val baseUrl = config.getApiBaseUrl()!!
        val url = "$baseUrl/git/refs/heads/${config.branch}"

        val jsonBody = JSONObject().apply {
            put("sha", commitSha)
        }

        val request = buildRequest(config, url, jsonBody)
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val body = response.body?.string() ?: ""
            return Result.failure(Exception("更新分支失败: ${response.code} - $body"))
        }

        return Result.success(Unit)
    }

    /**
     * 构建通用的API请求
     */
    private fun buildRequest(config: RepoConfig, url: String, jsonBody: JSONObject): Request {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonBody.toString().toRequestBody(mediaType)

        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.token}")
            .addHeader("Accept", "application/vnd.github.v3+json")
            .post(body)
            .build()
    }
}
