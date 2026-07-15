package com.prosenjith.messos.services

import com.prosenjith.messos.util.FileStorageService
import com.prosenjith.messos.util.ValidationException
import io.ktor.http.content.*
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray

private val ALLOWED_CONTENT_TYPES = mapOf(
    "image/jpeg" to "jpg",
    "image/png" to "png",
    "image/webp" to "webp"
)
private const val MAX_BYTES = 5 * 1024 * 1024  // 5 MB

class UploadService(private val fileStorageService: FileStorageService) {

    suspend fun parseAndStore(multipart: MultiPartData, folder: String): String {
        var url: String? = null
        multipart.forEachPart { part ->
            if (part is PartData.FileItem && url == null) {
                val contentType = part.contentType?.toString()
                    ?: throw ValidationException("Missing content type for uploaded file")
                val extension = ALLOWED_CONTENT_TYPES[contentType]
                    ?: throw ValidationException("Unsupported file type. Allowed: jpeg, png, webp")
                val bytes = part.provider().readRemaining().readByteArray()
                if (bytes.size > MAX_BYTES) {
                    throw ValidationException("File too large. Maximum allowed size is 5 MB")
                }
                url = fileStorageService.store(bytes, extension, folder)
            }
            part.release()
        }
        return url ?: throw ValidationException("No file found in request")
    }
}
