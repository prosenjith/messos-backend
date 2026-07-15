package com.prosenjith.messos.util

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.deleteObject
import aws.sdk.kotlin.services.s3.putObject
import aws.smithy.kotlin.runtime.content.ByteStream
import java.util.UUID

class S3FileStorageService(
    private val bucket: String,
    private val region: String
) : FileStorageService {

    private val client = S3Client { this.region = this@S3FileStorageService.region }

    override suspend fun store(bytes: ByteArray, extension: String, folder: String): String {
        val key = "$folder/${UUID.randomUUID()}.$extension"
        client.putObject {
            this.bucket = this@S3FileStorageService.bucket
            this.key = key
            body = ByteStream.fromBytes(bytes)
            contentType = contentTypeFor(extension)
        }
        return "https://$bucket.s3.$region.amazonaws.com/$key"
    }

    override suspend fun delete(url: String) {
        val key = url.substringAfter(".amazonaws.com/")
        client.deleteObject {
            this.bucket = this@S3FileStorageService.bucket
            this.key = key
        }
    }

    private fun contentTypeFor(extension: String) = when (extension) {
        "jpg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "application/octet-stream"
    }
}
