package com.gituploader.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 使用DataStore持久化存储令牌和仓库地址
 */
class TokenManager(private val context: Context) {

    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "git_uploader_prefs")

        val KEY_TOKEN = stringPreferencesKey("saved_token")
        val KEY_REPO_URL = stringPreferencesKey("saved_repo_url")
        val KEY_BRANCH = stringPreferencesKey("saved_branch")
        val KEY_COMMIT_MSG = stringPreferencesKey("saved_commit_msg")
    }

    /**
     * 获取已保存的令牌
     */
    val savedToken: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_TOKEN]
    }

    /**
     * 获取已保存的仓库地址
     */
    val savedRepoUrl: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_REPO_URL]
    }

    /**
     * 获取已保存的分支
     */
    val savedBranch: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_BRANCH]
    }

    /**
     * 获取已保存的提交信息
     */
    val savedCommitMessage: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_COMMIT_MSG]
    }

    /**
     * 保存令牌
     */
    suspend fun saveToken(token: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TOKEN] = token
        }
    }

    /**
     * 保存仓库地址
     */
    suspend fun saveRepoUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_REPO_URL] = url
        }
    }

    /**
     * 保存分支名
     */
    suspend fun saveBranch(branch: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BRANCH] = branch
        }
    }

    /**
     * 保存提交信息
     */
    suspend fun saveCommitMessage(message: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_COMMIT_MSG] = message
        }
    }

    /**
     * 清除所有已保存的数据
     */
    suspend fun clearAll() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
