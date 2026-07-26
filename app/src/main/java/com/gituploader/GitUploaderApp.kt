package com.gituploader

import android.app.Application
import com.gituploader.data.TokenManager

class GitUploaderApp : Application() {
    val tokenManager: TokenManager by lazy {
        TokenManager(this)
    }
}
