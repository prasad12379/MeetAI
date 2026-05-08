package com.example.meetai

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.FileProvider
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import android.net.Uri

class Transcriptactivity : AppCompatActivity() {

    private val BASE_URL = "https://newmeetaibackend.onrender.com"

    private lateinit var tvRoomCode: TextView
    private lateinit var tvDuration: TextView
    private lateinit var tvLineCount: TextView
    private lateinit var tvTranscriptBody: TextView
    private lateinit var btnDownload: CardView
    private lateinit var btnShare: CardView
    private lateinit var btnClose: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var btnGetSummary: CardView
    private lateinit var btnDownloadSummary: CardView
    private lateinit var btnSendToMembers: CardView
    private lateinit var tvSummaryBody: TextView

    private var transcriptText     = ""
    private var transcriptFileName = "transcript.txt"
    private var audioFilePath      = ""
    private var roomCode           = ""
    private var duration           = ""
    private var summaryPdfPath     = ""
    private var adminEmail         = ""
    private var memberEmails       = listOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transcriptactivity)

        audioFilePath      = intent.getStringExtra("audio_file_path")        ?: ""
        transcriptFileName = intent.getStringExtra("transcript_filename")    ?: "transcript.txt"
        roomCode           = intent.getStringExtra("room_code")              ?: ""
        duration           = intent.getStringExtra("duration")               ?: ""
        adminEmail         = intent.getStringExtra("admin_email")            ?: ""
        memberEmails       = intent.getStringArrayListExtra("member_emails") ?: arrayListOf()

        bindViews()

        tvRoomCode.text       = roomCode
        tvDuration.text       = duration
        tvLineCount.text      = "Transcribing..."
        tvTranscriptBody.text = ""

        btnDownload.visibility        = View.GONE
        btnShare.visibility           = View.GONE
        btnGetSummary.visibility      = View.GONE
        btnDownloadSummary.visibility = View.GONE
        btnSendToMembers.visibility   = View.GONE

        btnClose.setOnClickListener { finish() }
        btnGetSummary.setOnClickListener { callSummarizeApi() }

        if (audioFilePath.isNotEmpty()) {
            callTranscribeApi(audioFilePath)
        } else {
            showError("No audio file found.")
        }
    }

    private fun bindViews() {
        tvRoomCode         = findViewById(R.id.tvTranscriptRoom)
        tvDuration         = findViewById(R.id.tvTranscriptDuration)
        tvLineCount        = findViewById(R.id.tvTranscriptLineCount)
        tvTranscriptBody   = findViewById(R.id.tvTranscriptBody)
        btnDownload        = findViewById(R.id.btnDownload)
        btnShare           = findViewById(R.id.btnShareTranscript)
        btnClose           = findViewById(R.id.btnCloseTranscript)
        progressBar        = findViewById(R.id.progressBar)
        tvStatus           = findViewById(R.id.tvStatus)
        btnGetSummary      = findViewById(R.id.btnGetSummary)
        btnDownloadSummary = findViewById(R.id.btnDownloadSummary)
        btnSendToMembers   = findViewById(R.id.btnSendToMembers)
        tvSummaryBody      = findViewById(R.id.tvSummaryBody)
    }

    // ── Transcribe API ────────────────────────────────────────────────
    private fun callTranscribeApi(filePath: String) {
        showLoading(true)
        tvStatus.text = "Uploading audio and transcribing..."

        val audioFile = File(filePath)
        if (!audioFile.exists()) {
            showError("Audio file not found: $filePath")
            return
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file", audioFile.name,
                audioFile.asRequestBody("audio/mpeg".toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/transcribe")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    showLoading(false)
                    showError("Network error: ${e.message}")
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                runOnUiThread {
                    showLoading(false)
                    if (response.isSuccessful) {
                        transcriptText = body
                        displayTranscript(transcriptText)
                    } else {
                        showError("Server error ${response.code}: $body")
                    }
                }
            }
        })
    }

    // ── Summarize API ─────────────────────────────────────────────────
    private fun callSummarizeApi() {
        if (transcriptText.isEmpty()) {
            Toast.makeText(this, "No transcript to summarize", Toast.LENGTH_SHORT).show()
            return
        }

        btnGetSummary.visibility      = View.GONE
        btnDownloadSummary.visibility = View.GONE
        btnSendToMembers.visibility   = View.GONE
        tvSummaryBody.text            = ""
        showLoading(true)
        tvStatus.text = "Generating summary..."

        val tempTxtFile = File(cacheDir, "transcript_temp.txt")
        tempTxtFile.writeText(transcriptText)

        val client = OkHttpClient.Builder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file", tempTxtFile.name,
                tempTxtFile.asRequestBody("text/plain".toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/summarize")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    showLoading(false)
                    btnGetSummary.visibility = View.VISIBLE
                    showError("Summary failed: ${e.message}")
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                runOnUiThread {
                    showLoading(false)
                    if (response.isSuccessful) {
                        try {
                            val summary = JSONObject(body).getString("summary")
                            displaySummary(summary)
                        } catch (e: Exception) {
                            showError("Failed to parse summary: ${e.message}")
                            btnGetSummary.visibility = View.VISIBLE
                        }
                    } else {
                        showError("Server error ${response.code}: $body")
                        btnGetSummary.visibility = View.VISIBLE
                    }
                }
                tempTxtFile.delete()
            }
        })
    }

    // ── Send PDF via device email app ─────────────────────────────────
    private fun sendPdfViaEmail() {
        if (summaryPdfPath.isEmpty()) {
            Toast.makeText(this, "No PDF to send", Toast.LENGTH_SHORT).show()
            return
        }
        if (memberEmails.isEmpty()) {
            Toast.makeText(this, "No members to send to", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val pdfFile = File(summaryPdfPath)
            if (!pdfFile.exists()) {
                Toast.makeText(this, "PDF file not found", Toast.LENGTH_SHORT).show()
                return
            }

            val pdfUri = FileProvider.getUriForFile(
                this,
                "${packageName}.provider",
                pdfFile
            )

            val subject = "MeetAI — Meeting Summary [$roomCode]"
            val body = """
Hi,

Please find attached the AI-generated summary for our recent MeetAI meeting.

Meeting Details:
  • Room Code : $roomCode
  • Duration  : $duration
  • Admin     : $adminEmail

The full meeting summary is attached as a PDF.

Best regards,
$adminEmail
        """.trimIndent()

            // Use ACTION_SEND (not MULTIPLE) with "*/*" to maximize email app compatibility
            val emailIntent = Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_EMAIL, memberEmails.toTypedArray())
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                putExtra(Intent.EXTRA_STREAM, pdfUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(emailIntent, "Send Summary via Email")

            // Grant URI permission to all apps in the chooser
            val resInfoList = packageManager.queryIntentActivities(
                chooser, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
            )
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                grantUriPermission(
                    packageName,
                    pdfUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            startActivity(chooser)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to open email: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ── Display Transcript ────────────────────────────────────────────
    private fun displayTranscript(text: String) {
        tvTranscriptBody.text = text
        val lineCount         = text.lines().count { it.trim().isNotEmpty() }
        tvLineCount.text      = "$lineCount lines"
        tvStatus.text         = "Transcript ready ✓"

        btnDownload.visibility   = View.VISIBLE
        btnShare.visibility      = View.VISIBLE
        btnGetSummary.visibility = View.VISIBLE

        btnDownload.setOnClickListener { downloadTranscript() }
        btnShare.setOnClickListener    { shareFile() }

        try { File(audioFilePath).delete() } catch (e: Exception) { e.printStackTrace() }
    }

    // ── Display Summary ───────────────────────────────────────────────
    private fun displaySummary(summaryText: String) {
        tvSummaryBody.text       = summaryText
        tvSummaryBody.visibility = View.VISIBLE
        tvStatus.text            = "Summary ready ✓"
        btnGetSummary.visibility = View.GONE

        try {
            summaryPdfPath = generateSummaryPdf(summaryText)

            btnDownloadSummary.visibility = View.VISIBLE
            btnDownloadSummary.setOnClickListener { downloadSummaryPdf() }

            // Show send button only if members exist
            if (memberEmails.isNotEmpty()) {
                btnSendToMembers.visibility = View.VISIBLE
                btnSendToMembers.setOnClickListener {
                    showSendConfirmDialog()
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this,
                "PDF generation failed: ${e.message}",
                Toast.LENGTH_SHORT).show()
        }
    }

    // ── Confirm dialog before sending ────────────────────────────────
    private fun showSendConfirmDialog() {
        val emailList = memberEmails.joinToString("\n• ", prefix = "• ")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Send Summary PDF")
            .setMessage(
                "Your email app will open with the PDF attached " +
                        "and all ${memberEmails.size} member(s) added as recipients:\n\n$emailList"
            )
            .setPositiveButton("Open Email App") { _, _ -> sendPdfViaEmail() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Generate PDF ──────────────────────────────────────────────────
    private fun generateSummaryPdf(summaryText: String): String {
        val pdfDocument = PdfDocument()
        val pageWidth   = 595
        val pageHeight  = 842
        val margin      = 50f
        val usableWidth = pageWidth - (margin * 2)

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page     = pdfDocument.startPage(pageInfo)
        val canvas   = page.canvas

        val bgPaint = Paint().apply { color = Color.parseColor("#0D0D1A") }
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)

        val titlePaint = Paint().apply {
            color          = Color.parseColor("#2ACFD9")
            textSize       = 26f
            isFakeBoldText = true
            isAntiAlias    = true
        }
        canvas.drawText("MeetAI — Meeting Summary", margin, 80f, titlePaint)

        val linePaint = Paint().apply {
            color       = Color.parseColor("#2ACFD9")
            strokeWidth = 1.5f
        }
        canvas.drawLine(margin, 95f, pageWidth - margin, 95f, linePaint)

        val metaPaint = Paint().apply {
            color       = Color.parseColor("#888888")
            textSize    = 11f
            isAntiAlias = true
        }
        val dateStr = SimpleDateFormat(
            "yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        canvas.drawText(
            "Room: $roomCode   |   Duration: $duration   |   Generated: $dateStr",
            margin, 118f, metaPaint
        )

        val labelPaint = Paint().apply {
            color          = Color.parseColor("#A855F7")
            textSize       = 13f
            isFakeBoldText = true
            isAntiAlias    = true
        }
        canvas.drawText("SUMMARY", margin, 150f, labelPaint)

        val bodyPaint = Paint().apply {
            color       = Color.parseColor("#CCFFFFFF")
            textSize    = 13f
            isAntiAlias = true
        }

        var yPos          = 175f
        val lineHeight    = 20f
        val words         = summaryText.split(" ")
        var currentLine   = ""
        var currentPage   = page
        var currentCanvas = canvas

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (bodyPaint.measureText(testLine) <= usableWidth) {
                currentLine = testLine
            } else {
                currentCanvas.drawText(currentLine, margin, yPos, bodyPaint)
                yPos += lineHeight
                currentLine = word

                if (yPos > pageHeight - margin) {
                    pdfDocument.finishPage(currentPage)
                    val newInfo = PdfDocument.PageInfo.Builder(
                        pageWidth, pageHeight,
                        pdfDocument.pages.size + 1
                    ).create()
                    currentPage   = pdfDocument.startPage(newInfo)
                    currentCanvas = currentPage.canvas
                    currentCanvas.drawRect(
                        0f, 0f,
                        pageWidth.toFloat(), pageHeight.toFloat(), bgPaint
                    )
                    yPos = margin + lineHeight
                }
            }
        }
        if (currentLine.isNotEmpty()) {
            currentCanvas.drawText(currentLine, margin, yPos, bodyPaint)
        }

        val footerPaint = Paint().apply {
            color       = Color.parseColor("#444444")
            textSize    = 10f
            isAntiAlias = true
        }
        currentCanvas.drawLine(
            margin, pageHeight - 50f,
            pageWidth - margin, pageHeight - 50f, footerPaint
        )
        currentCanvas.drawText(
            "Generated by MeetAI  •  $dateStr",
            margin, pageHeight - 30f, footerPaint
        )

        pdfDocument.finishPage(currentPage)

        val dateStamp   = SimpleDateFormat(
            "yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        val pdfFileName = "Summary_${roomCode}_$dateStamp.pdf"
        val pdfFile     = File(cacheDir, pdfFileName)
        FileOutputStream(pdfFile).use { pdfDocument.writeTo(it) }
        pdfDocument.close()

        return pdfFile.absolutePath
    }

    // ── Download PDF ──────────────────────────────────────────────────
    private fun downloadSummaryPdf() {
        if (summaryPdfPath.isEmpty()) return
        try {
            val sourceFile   = File(summaryPdfPath)
            val downloadsDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val destFile = File(downloadsDir, sourceFile.name)
            sourceFile.copyTo(destFile, overwrite = true)
            Toast.makeText(this,
                "PDF saved to Downloads/${sourceFile.name}",
                Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            try {
                val uri = FileProvider.getUriForFile(
                    this, "${packageName}.provider", File(summaryPdfPath))
                val shareIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Open PDF"))
            } catch (ex: Exception) {
                Toast.makeText(this,
                    "Download failed: ${ex.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Download Transcript ───────────────────────────────────────────
    private fun downloadTranscript(): File? {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = File(downloadsDir, transcriptFileName)
            FileWriter(file).use { it.write(transcriptText) }
            Toast.makeText(this,
                "Saved to Downloads/$transcriptFileName",
                Toast.LENGTH_LONG).show()
            file
        } catch (e: Exception) {
            try {
                val docsDir = getExternalFilesDir(
                    Environment.DIRECTORY_DOCUMENTS) ?: filesDir
                val file = File(docsDir, transcriptFileName)
                FileWriter(file).use { it.write(transcriptText) }
                Toast.makeText(this,
                    "Saved to app Documents", Toast.LENGTH_LONG).show()
                file
            } catch (ex: Exception) {
                Toast.makeText(this,
                    "Save failed: ${ex.message}", Toast.LENGTH_SHORT).show()
                null
            }
        }
    }

    // ── Share Transcript ──────────────────────────────────────────────
    private fun shareFile() {
        try {
            val cacheFile = File(cacheDir, transcriptFileName)
            FileWriter(cacheFile).use { it.write(transcriptText) }
            val uri = FileProvider.getUriForFile(
                this, "${packageName}.provider", cacheFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT,
                    "MeetAI Transcript – $transcriptFileName")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Transcript"))
        } catch (e: Exception) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, transcriptText)
                putExtra(Intent.EXTRA_SUBJECT, "MeetAI Transcript")
            }
            startActivity(Intent.createChooser(shareIntent, "Share Transcript"))
        }
    }

    private fun showLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        tvStatus.text         = "❌ $message"
        tvLineCount.text      = "Error"
        tvTranscriptBody.text = message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onBackPressed() {
        val intent = Intent(this, Newmeetingactivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }
}