package com.asifalijohari.fileuploaddownloadmanager

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory

class SecureUploadManager(private val context: Context) {

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeUploads = ConcurrentHashMap<String, MutableList<Job>>() // Track active uploads
    private val chunkStates = ConcurrentHashMap<String, MutableMap<Int, ChunkState>>() // Track chunk states

    data class ChunkState(var status: String, var uploadedBytes: Int)

    // Upload a file with custom headers and SSL
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    suspend fun uploadFile(
        file: File,
        uploadUrl: String,
        chunkSize: Int = 1024 * 1024, // Default 1 MB chunk size
        maxConcurrentChunks: Int = 4,
        customHeaders: Map<String, String> = emptyMap(),
        sslSocketFactory: SSLSocketFactory? = null,
        onProgress: (Int) -> Unit = {}, // Overall progress
        onCompletion: () -> Unit = {}, // Completion callback
        onError: (Throwable) -> Unit = {} // Error callback
    ) {
        val fileChunkStates = chunkStates.getOrPut(file.name) { initializeChunks(file.length(), chunkSize) }

        try {
            if (!isNetworkAvailable()) throw Exception("Network unavailable. Retry later.")

            val chunkJobs = fileChunkStates.filter { it.value.status != "completed" }.map { (chunkIndex, _) ->
                coroutineScope.async {
                    uploadChunk(file, uploadUrl, chunkIndex, chunkSize, customHeaders, sslSocketFactory) { progress ->
                        val overallProgress = calculateOverallProgress(fileChunkStates, file.length())
                        onProgress(overallProgress)
                    }
                }
            }

            activeUploads[file.name] = chunkJobs.toMutableList()

            coroutineScope {
                chunkJobs.chunked(maxConcurrentChunks).forEach { group ->
                    group.awaitAll()
                }
            }

            if (fileChunkStates.all { it.value.status == "completed" }) {
                onCompletion()
                chunkStates.remove(file.name) // Cleanup state after successful upload
            } else {
                throw Exception("Some chunks failed to upload.")
            }

        } catch (e: Exception) {
            onError(e)
        }
    }

    private suspend fun uploadChunk(
        file: File,
        uploadUrl: String,
        chunkIndex: Int,
        chunkSize: Int,
        customHeaders: Map<String, String>,
        sslSocketFactory: SSLSocketFactory?,
        onProgress: (Int) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            val startByte = chunkIndex * chunkSize
            val endByte = minOf(startByte + chunkSize - 1, file.length().toInt() - 1)
            var attempt = 0
            val maxRetries = 3

            while (attempt < maxRetries) {
                try {
                    val connection = createConnection(uploadUrl, customHeaders, sslSocketFactory)
                    connection.doOutput = true
                    connection.setRequestProperty(
                        "Content-Range",
                        "bytes $startByte-$endByte/${file.length()}"
                    )

                    val outputStream = connection.outputStream
                    val inputFile =
                        RandomAccessFile(file, "r")

                    val buffer = ByteArray(chunkSize)
                    var bytesRead: Int

                    inputFile.seek(startByte.toLong())
                    bytesRead = inputFile.read(buffer, 0, endByte - startByte + 1)
                    outputStream.write(buffer, 0, bytesRead)

                    inputFile.close()
                    outputStream.close()

                    if (connection.responseCode in 200..299) {
                        updateChunkState(chunkIndex, bytesRead, "completed")
                        return@withContext
                    } else {
                        throw Exception("Failed to upload chunk: ${connection.responseMessage}")
                    }

                } catch (e: Exception) {
                    attempt++
                    if (attempt >= maxRetries) {
                        updateChunkState(chunkIndex, 0, "failed")
                        throw Exception("Chunk $chunkIndex failed after $maxRetries attempts.")
                    }
                }
            }
        }
    }

    private fun createConnection(
        url: String,
        headers: Map<String, String>,
        sslSocketFactory: SSLSocketFactory?
    ): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        if (sslSocketFactory != null && connection is HttpsURLConnection) {
            connection.sslSocketFactory = sslSocketFactory
        }
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        return connection
    }

    private fun initializeChunks(fileLength: Long, chunkSize: Int): MutableMap<Int, ChunkState> {
        val totalChunks = (fileLength / chunkSize).toInt() + if (fileLength % chunkSize > 0) 1 else 0
        return mutableMapOf<Int, ChunkState>().apply {
            for (chunkIndex in 0 until totalChunks) {
                this[chunkIndex] = ChunkState("pending", 0)
            }
        }
    }

    private fun updateChunkState(chunkIndex: Int, uploadedBytes: Int, status: String) {
        chunkStates.forEach { (_, chunks) ->
            chunks[chunkIndex]?.apply {
                this.uploadedBytes = uploadedBytes
                this.status = status
            }
        }
    }

    private fun calculateOverallProgress(chunks: Map<Int, ChunkState>, fileSize: Long): Int {
        val uploadedBytes = chunks.values.sumOf { it.uploadedBytes }
        return (uploadedBytes * 100 / fileSize).toInt()
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun pauseUpload(fileName: String) {
        activeUploads[fileName]?.forEach { it.cancel() }
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    suspend fun resumeUpload(
        file: File,
        uploadUrl: String,
        chunkSize: Int,
        customHeaders: Map<String, String>,
        sslSocketFactory: SSLSocketFactory?,
        onProgress: (Int) -> Unit,
        onCompletion: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        uploadFile(
            file, uploadUrl, chunkSize, customHeaders = customHeaders, sslSocketFactory = sslSocketFactory,
            onProgress = onProgress, onCompletion = onCompletion, onError = onError
        )
    }

    fun cancelUpload(fileName: String) {
        pauseUpload(fileName)
        chunkStates.remove(fileName)
        activeUploads.remove(fileName)
    }
}
