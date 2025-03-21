package com.asifalijohari.fileuploaddownloadmanager

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import java.io.File

class SecureDownloadManagerWithSecurityTest {

    private lateinit var context: Context
    private lateinit var downloadManager: SecureDownloadManagerWithSecurity
    private lateinit var testDirectory: File

    @Before
    fun setup() {
        // Mock the Android Context
        context = mock(Context::class.java)
        //`when`(context.filesDir).thenReturn(File("/tmp")) // Set temporary directory for tests

        // Create a temporary directory for testing
        testDirectory = File(System.getProperty("java.io.tmpdir"), "test_downloads")
        testDirectory.mkdirs() // Ensure the directory exists

        // Mock getFilesDir() to return the test directory
        `when`(context.filesDir).thenReturn(testDirectory)
        // Mock getExternalFilesDir(null) to return the test directory (This is the fix!)
        `when`(context.getExternalFilesDir(null)).thenReturn(testDirectory)


        downloadManager = SecureDownloadManagerWithSecurity(context)
    }
    @Test
    fun testThinkBroadbandDownload() = runBlocking {
        // Use ThinkBroadband's public test file (5MB file)
        val url = "https://ipv4.download.thinkbroadband.com/5MB.zip"
        val fileName = "5MB.zip"

        downloadManager.downloadFile(
            url = url,
            fileName = fileName,
            onProgress = { progress ->
                println("Download Progress: $progress%")
                assertTrue(progress in 0..100)
            },
            onCompletion = {
                println("Download completed successfully!")
                val downloadedFile = File(testDirectory, fileName)
//                val downloadedFile = File(context.filesDir, fileName)
                assertTrue("The file should exist after download", downloadedFile.exists())
                assertTrue("The file size should match the expected size", downloadedFile.length() > 0)
            },
            onError = { error ->
                fail("Download failed with error: ${error.message}")
            }
        )
    }

    @Test
    fun testNormalDownload() = runBlocking {
        val url = "https://example.com/testfile.zip"
        val fileName = "testfile.zip"

        downloadManager.downloadFile(
            url = url,
            fileName = fileName,
            onProgress = { progress ->
                println("Progress: $progress%")
                assertTrue(progress in 0..100)
            },
            onCompletion = {
                println("Download completed!")
                val file = File(context.filesDir, fileName)
                assertTrue(file.exists())
            },
            onError = { error ->
                fail("Download failed with error: ${error.message}")
            }
        )
    }

    @Test
    fun testPauseAndResumeDownload() = runBlocking {
        val url = "https://example.com/testfile.zip"
        val fileName = "testfile.zip"

        // Start downloading
        downloadManager.downloadFile(
            url = url,
            fileName = fileName,
            onProgress = { progress ->
                if (progress > 50) {
                    downloadManager.pauseDownload(fileName)
                }
            },
            onCompletion = {
                fail("Download should be paused before completion")
            },
            onError = { error ->
                fail("Error during download: ${error.message}")
            }
        )

        // Resume download
        downloadManager.resumeDownload(
            url = url,
            fileName = fileName,
            chunkSize = 1024 * 1024,
            onProgress = { progress ->
                println("Resumed Progress: $progress%")
            },
            onCompletion = {
                println("Download completed after resume!")
                val file = File(context.filesDir, fileName)
                assertTrue(file.exists())
            },
            onError = { error ->
                fail("Error during resume: ${error.message}")
            },
            customHeaders = TODO()
        )
    }

    @Test
    fun testCertificatePinning() = runBlocking {
        val url = "https://secure.example.com/testfile.zip"
        val fileName = "securefile.zip"

        try {
            downloadManager.downloadFile(
                url = url,
                fileName = fileName,
                onProgress = { progress ->
                    println("Certificate Pinning Progress: $progress%")
                },
                onCompletion = {
                    println("Download with certificate pinning completed!")
                },
                onError = { error ->
                    fail("Certificate validation failed: ${error.message}")
                }
            )
        } catch (e: Exception) {
            println("Download blocked due to invalid certificate.")
            assertTrue(e.message!!.contains("SSLHandshakeException"))
        }
    }

    @Test
    fun testChunkRetry() = runBlocking {
        val url = "https://example.com/unstablefile.zip"
        val fileName = "unstablefile.zip"

        downloadManager.downloadFile(
            url = url,
            fileName = fileName,
            onProgress = { progress ->
                println("Retrying Chunk... Progress: $progress%")
            },
            onCompletion = {
                println("Download completed with retries!")
            },
            onError = { error ->
                fail("Chunk retries failed: ${error.message}")
            }
        )
    }

    @Test
    fun testCustomHeaders() = runBlocking {
        val url = "https://example.com/protectedfile.zip"
        val fileName = "protectedfile.zip"
        val headers = mapOf("Authorization" to "Bearer myToken")

        downloadManager.downloadFile(
            url = url,
            fileName = fileName,
            customHeaders = headers,
            onProgress = { progress ->
                println("Custom Header Progress: $progress%")
            },
            onCompletion = {
                println("Download with custom headers completed!")
            },
            onError = { error ->
                fail("Error with custom headers: ${error.message}")
            }
        )
    }
}
