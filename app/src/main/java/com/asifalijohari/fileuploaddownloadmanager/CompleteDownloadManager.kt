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
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory

class SecureDownloadManagerWithSecurity(private val context: Context) {

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeDownloads = ConcurrentHashMap<String, MutableList<Job>>()
    private val chunkStates = ConcurrentHashMap<String, MutableMap<Int, ChunkState>>()

    data class ChunkState(var status: String, var downloadedBytes: Int)

    // Certificate Pinning Configuration
    private fun createPinnedSSLSocketFactory(): SSLSocketFactory {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)

        // Load trusted certificates (custom certificate pinning)
        val certificateInputStream = context.assets.open("trusted_cert.pem")
        val certificateFactory = CertificateFactory.getInstance("X.509")
        val certificate = certificateFactory.generateCertificate(certificateInputStream)
        keyStore.setCertificateEntry("pinned_cert", certificate)

        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(keyStore)

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, tmf.trustManagers, SecureRandom())
        return sslContext.socketFactory
    }

    // Download a file with encryption and SSL pinning
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    suspend fun downloadFile(
        url: String,
        fileName: String,
        chunkSize: Int = 1024 * 1024,
        sslSocketFactory: SSLSocketFactory? = null, // SSL is now optional
        maxConcurrentChunks: Int = 4,
        customHeaders: Map<String, String> = emptyMap(),
        onProgress: (Int) -> Unit = {},
        onCompletion: () -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        val encryptedFile = withContext(Dispatchers.IO) {
            RandomAccessFile(
                context.getExternalFilesDir(null)!!.absolutePath + "/$fileName.enc",
                "rw"
            )
        }
        try {
            if (!isNetworkAvailable()) throw Exception("Network unavailable. Retry later.")

            //val sslSocketFactory = createPinnedSSLSocketFactory()
            val connection = createConnection(url, customHeaders, sslSocketFactory)
            val fileSize = connection.contentLength
            connection.disconnect()

            val totalChunks = (fileSize / chunkSize) + if (fileSize % chunkSize > 0) 1 else 0
            val fileChunkStates = chunkStates.getOrPut(fileName) { initializeChunks(totalChunks) }

            val chunkJobs = fileChunkStates.filter { it.value.status != "completed" }.map { (chunkIndex, _) ->
                coroutineScope.async {
                    downloadChunk(url, chunkIndex, chunkSize, encryptedFile, fileSize, customHeaders, sslSocketFactory) { progress ->
                        val overallProgress = calculateOverallProgress(fileChunkStates, fileSize)
                        onProgress(overallProgress)
                    }
                }
            }

            activeDownloads[fileName] = chunkJobs.toMutableList()

            coroutineScope {
                chunkJobs.chunked(maxConcurrentChunks).forEach { group ->
                    group.awaitAll()
                }
            }

            if (fileChunkStates.all { it.value.status == "completed" }) {
                decryptFile(encryptedFile, fileName) // Decrypt and save file after download
                onCompletion()
                chunkStates.remove(fileName)
            } else {
                throw Exception("Some chunks failed to download.")
            }

        } catch (e: Exception) {
            onError(e)
        } finally {
            withContext(Dispatchers.IO) {
                encryptedFile.close()
            }
        }
    }

    private suspend fun downloadChunk(
        url: String,
        chunkIndex: Int,
        chunkSize: Int,
        encryptedFile: RandomAccessFile,
        fileSize: Int,
        customHeaders: Map<String, String>,
        sslSocketFactory: SSLSocketFactory?,
        onProgress: (Int) -> Unit
    ) {
        val startByte = chunkIndex * chunkSize
        val endByte = minOf(startByte + chunkSize - 1, fileSize - 1)
        var attempt = 0
        val maxRetries = 3

        while (attempt < maxRetries) {
            try {
                val connection = createConnection(url, customHeaders, sslSocketFactory)
                connection.setRequestProperty("Range", "bytes=$startByte-$endByte")

                val inputStream = connection.inputStream
                val buffer = ByteArray(chunkSize)
                var downloadedBytes = 0
                var bytesRead: Int

                encryptedFile.seek(startByte.toLong())
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    val encryptedData = encryptChunk(buffer, bytesRead)
                    encryptedFile.write(encryptedData, 0, encryptedData.size)
                    downloadedBytes += bytesRead
                    updateChunkState(chunkIndex, downloadedBytes, "downloading")
                    val chunkProgress = (downloadedBytes * 100) / (endByte - startByte + 1)
                    onProgress(chunkProgress)
                }

                updateChunkState(chunkIndex, downloadedBytes, "completed")
                return

            } catch (e: Exception) {
                attempt++
                if (attempt >= maxRetries) {
                    updateChunkState(chunkIndex, 0, "failed")
                    throw Exception("Chunk $chunkIndex failed after $maxRetries attempts.")
                }
            }
        }
    }

    // Encrypt chunks for secure storage
    private fun encryptChunk(data: ByteArray, bytesRead: Int): ByteArray {
        val key = SecretKeySpec("MySecurePassword".toByteArray(), "AES")
        val cipher = Cipher.getInstance("AES")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.doFinal(data.copyOf(bytesRead))
    }

    // Decrypt file after download
    private fun decryptFile(encryptedFile: RandomAccessFile, fileName: String) {
        val decryptedFile = RandomAccessFile(context.getExternalFilesDir(null)!!.absolutePath + "/$fileName", "rw")
        val key = SecretKeySpec("MySecurePassword".toByteArray(), "AES")
        val cipher = Cipher.getInstance("AES")
        cipher.init(Cipher.DECRYPT_MODE, key)

        val buffer = ByteArray(1024)
        var bytesRead: Int
        encryptedFile.seek(0)

        while (encryptedFile.read(buffer).also { bytesRead = it } != -1) {
            decryptedFile.write(cipher.doFinal(buffer.copyOf(bytesRead)))
        }

        decryptedFile.close()
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
        connection.connect()
        return connection
    }

    private fun initializeChunks(totalChunks: Int): MutableMap<Int, ChunkState> {
        return mutableMapOf<Int, ChunkState>().apply {
            for (chunkIndex in 0 until totalChunks) {
                this[chunkIndex] = ChunkState("pending", 0)
            }
        }
    }

    private fun updateChunkState(chunkIndex: Int, downloadedBytes: Int, status: String) {
        chunkStates.forEach { (_, chunks) ->
            chunks[chunkIndex]?.apply {
                this.downloadedBytes = downloadedBytes
                this.status = status
            }
        }
    }

    private fun calculateOverallProgress(chunks: Map<Int, ChunkState>, fileSize: Int): Int {
        val downloadedBytes = chunks.values.sumOf { it.downloadedBytes }
        return (downloadedBytes * 100) / fileSize
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun pauseDownload(fileName: String) {
        activeDownloads[fileName]?.forEach { it.cancel() }
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    suspend fun resumeDownload(
        url: String,
        fileName: String,
        chunkSize: Int,
        customHeaders: Map<String, String>,
        onProgress: (Int) -> Unit,
        onCompletion: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        downloadFile(
            url, fileName, chunkSize, customHeaders = customHeaders,
            onProgress = onProgress, onCompletion = onCompletion, onError = onError
        )
    }

    fun cancelDownload(fileName: String) {
        pauseDownload(fileName)
        chunkStates.remove(fileName)
        activeDownloads.remove(fileName)
    }
}
