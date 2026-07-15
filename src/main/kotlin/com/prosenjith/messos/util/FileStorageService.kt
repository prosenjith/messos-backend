package com.prosenjith.messos.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

interface FileStorageService {
    suspend fun store(bytes: ByteArray, extension: String, folder: String): String
    suspend fun delete(url: String)
}

class LocalFileStorageService(
    private val uploadDir: File,
    private val baseUrl: String
) : FileStorageService {

    init {
        uploadDir.mkdirs()
    }

    override suspend fun store(bytes: ByteArray, extension: String, folder: String): String = withContext(Dispatchers.IO) {
        val dir = File(uploadDir, folder)
        dir.mkdirs()
        val filename = "${UUID.randomUUID()}.$extension"
        File(dir, filename).writeBytes(bytes)
        "$baseUrl/$folder/$filename"
    }

    override suspend fun delete(url: String) = withContext(Dispatchers.IO) {
        val relativePath = url.removePrefix("$baseUrl/")
        File(uploadDir, relativePath).delete()
        Unit
    }
}
