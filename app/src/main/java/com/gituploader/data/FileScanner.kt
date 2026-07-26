package com.gituploader.data

import com.gituploader.data.model.FileEntry
import java.io.File

/**
 * 文件扫描工具，递归遍历文件夹收集所有文件
 */
object FileScanner {

    /**
     * 扫描文件夹，收集所有文件并计算相对路径
     * @param rootDir 用户选择的根文件夹
     * @return 文件列表（包含本地文件对象和仓库中的相对路径）
     */
    fun scanDirectory(rootDir: File): List<FileEntry> {
        val entries = mutableListOf<FileEntry>()
        scanRecursive(rootDir, rootDir, entries)
        return entries
    }

    /**
     * 递归扫描
     */
    private fun scanRecursive(currentDir: File, rootDir: File, entries: MutableList<FileEntry>) {
        val files = currentDir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                scanRecursive(file, rootDir, entries)
            } else {
                // 计算相对路径（使用正斜杠，符合Git路径规范）
                val relativePath = file.relativeTo(rootDir).path.replace("\\", "/")
                entries.add(FileEntry(file, relativePath))
            }
        }
    }

    /**
     * 统计文件总大小
     */
    fun calculateTotalSize(entries: List<FileEntry>): Long {
        return entries.sumOf { it.localFile.length() }
    }

    /**
     * 格式化文件大小
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }
}
