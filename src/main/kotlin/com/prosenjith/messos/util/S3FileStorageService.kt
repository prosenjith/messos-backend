package com.prosenjith.messos.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import java.util.UUID

class S3FileStorageService(
    private val bucket: String,
    private val region: String
) : FileStorageService {

    private val client: S3Client = S3Client.builder()
        .region(Region.of(region))
        .credentialsProvider(DefaultCredentialsProvider.builder().build())
        .httpClient(UrlConnectionHttpClient.create())
        .build()

    override suspend fun store(bytes: ByteArray, extension: String, folder: String): String = withContext(Dispatchers.IO) {
        val key = "$folder/${UUID.randomUUID()}.$extension"
        val request = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(contentTypeFor(extension))
            .contentLength(bytes.size.toLong())
            .build()
        client.putObject(request, RequestBody.fromBytes(bytes))
        "https://$bucket.s3.$region.amazonaws.com/$key"
    }

    override suspend fun delete(url: String) = withContext(Dispatchers.IO) {
        val key = url.substringAfter(".amazonaws.com/")
        val request = DeleteObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build()
        client.deleteObject(request)
        Unit
    }

    private fun contentTypeFor(extension: String) = when (extension) {
        "jpg"  -> "image/jpeg"
        "png"  -> "image/png"
        "webp" -> "image/webp"
        else   -> "application/octet-stream"
    }
}
