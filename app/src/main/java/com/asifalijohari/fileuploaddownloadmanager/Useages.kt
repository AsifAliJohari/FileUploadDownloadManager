//package com.asifalijohari.fileuploaddownloadmanager
//
//import javax.net.ssl.SSLContext
//import javax.net.ssl.X509TrustManager
//
//val downloadManager = SecureDownloadManagerWithSecurity(this)
//
//val fileUrl = "https://example.com/largefile.zip"
//val fileName = "largefile.zip"
//
//// Start download
//CoroutineScope(Dispatchers.Main).launch {
//    downloadManager.downloadFile(
//        url = fileUrl,
//        fileName = fileName,
//        onProgress = { progress ->
//            println("Download progress: $progress%")
//        },
//        onCompletion = {
//            println("Download completed successfully!")
//        },
//        onError = { error ->
//            println("Download failed: ${error.message}")
//        }
//    )
//}
//
//// Pause download
//downloadManager.pauseDownload(fileName)
//
//// Resume download
//CoroutineScope(Dispatchers.Main).launch {
//    downloadManager.resumeDownload(fileUrl, fileName, 1024 * 1024,
//        onProgress = { progress ->
//            println("Resumed progress: $progress%")
//        },
//        onCompletion = {
//            println("Download resumed and completed!")
//        },
//        onError = { error ->
//            println("Error resuming download: ${error.message}")
//        }
//    )
//}
//
//// Retry failed chunks
//CoroutineScope(Dispatchers.Main).launch {
//    downloadManager.retryFailedChunks(
//        url = fileUrl,
//        fileName = fileName,
//        chunkSize = 1024 * 1024, // 1 MB chunks
//        onProgress = { progress ->
//            println("Retry progress: $progress%")
//        }
//    )
//}
//
////val downloadManager = SecureDownloadManager(this)
//
//val fileUrl = "https://example.com/securefile.zip"
//val fileName = "securefile.zip"
//val customHeaders = mapOf("Authorization" to "Bearer myToken", "User-Agent" to "MyApp/1.0")
//
//// SSL configuration (optional)
//val sslContext = SSLContext.getInstance("TLS")
//sslContext.init(null, arrayOf(object : X509TrustManager {
//    override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
//    override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
//    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
//}), SecureRandom())
//val sslSocketFactory = sslContext.socketFactory
//
//CoroutineScope(Dispatchers.Main).launch {
//    downloadManager.downloadFile(
//        url = fileUrl,
//        fileName = fileName,
//        customHeaders = customHeaders,
//        sslSocketFactory = sslSocketFactory,
//        onProgress = { progress ->
//            println("Download progress: $progress%")
//        },
//        onCompletion = {
//            println("Download completed successfully!")
//        },
//        onError = { error ->
//            println("Download failed: ${error.message}")
//        }
//    )
//}
