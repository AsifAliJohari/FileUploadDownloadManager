package com.asifalijohari.fileuploaddownloadmanager

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var downloadManager: SecureDownloadManagerWithSecurity
    private lateinit var uploadManager: SecureUploadManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        downloadManager = SecureDownloadManagerWithSecurity(this)
        uploadManager = SecureUploadManager(this)

        val downloadButton = findViewById<Button>(R.id.downloadButton)
        val uploadButton = findViewById<Button>(R.id.uploadButton)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val progressText = findViewById<TextView>(R.id.progressText)

        downloadButton.setOnClickListener {
            MainScope().launch {
                progressBar.visibility = View.VISIBLE
                progressText.text = "Starting Download..."
                downloadManager.downloadFile(
                    url = "http://proof.ovh.net/files/10Mb.dat",
                  //  url = "https://nbg1-speed.hetzner.com/100MB.bin",
                    //fileName = "100MB.bin",
                    fileName = "10Mb.dat",
                    onProgress = { progress ->
                        progressBar.progress = progress
                        progressText.text = "Progress: $progress%"
                    },
                    onCompletion = {
                        progressBar.visibility = View.GONE
                        progressText.text = "Download Complete!"
                    },
                    onError = { error ->
                        progressBar.visibility = View.GONE
                        progressText.text = "Error: ${error.message}"
                    }
                )
            }
        }

        uploadButton.setOnClickListener {
            MainScope().launch {
                val file = File(this@MainActivity.filesDir, "10MB.zip")
                progressBar.visibility = View.VISIBLE
                progressText.text = "Starting Upload..."
                uploadManager.uploadFile(
                    file = file,
                    uploadUrl = "https://example.com/upload",
                    onProgress = { progress ->
                        progressBar.progress = progress
                        progressText.text = "Progress: $progress%"
                    },
                    onCompletion = {
                        progressBar.visibility = View.GONE
                        progressText.text = "Upload Complete!"
                    },
                    onError = { error ->
                        progressBar.visibility = View.GONE
                        progressText.text = "Error: ${error.message}"
                    }
                )
            }
        }
    }
}
