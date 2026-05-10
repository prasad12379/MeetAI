package com.example.meetai

// ════════════════════════════════════════════════════════════════════════════
//  Transcriptactivity.kt  —  MeetAI  •  Compose rewrite
// ════════════════════════════════════════════════════════════════════════════

import android.content.Intent
import android.graphics.Canvas as ACanvas
import android.graphics.Color as AColor
import android.graphics.Paint as APaint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

// ── Palette ──────────────────────────────────────────────────────────────────
private val TInk        = Color(0xFF050810)
private val TInkMid     = Color(0xFF090E1C)
private val TInkLight   = Color(0xFF0F1626)
private val TInkLighter = Color(0xFF161E30)
private val TCyan       = Color(0xFF00D4FF)
private val TGold       = Color(0xFFFFBF3C)
private val TViolet     = Color(0xFF8B5CF6)
private val TGreen      = Color(0xFF00E5A0)
private val TRed        = Color(0xFFFF4757)
private val TTextPri    = Color(0xFFF0F6FF)
private val TTextSub    = Color(0xFF6B7FA3)
private val TTextMuted  = Color(0xFF2E3B55)
private val TCardBorder = Color(0xFF141C2E)

private val TBgGrad    = Brush.verticalGradient(listOf(Color(0xFF030610), Color(0xFF07091A), Color(0xFF030610)))
private val TCyanGrad  = Brush.linearGradient(listOf(TCyan, Color(0xFF0EA5E9)))
private val TGoldGrad  = Brush.linearGradient(listOf(TGold, Color(0xFFF97316)))
private val TGreenGrad = Brush.linearGradient(listOf(TGreen, Color(0xFF0EA5E9)))
private val TRedGrad   = Brush.linearGradient(listOf(TRed, Color(0xFFFF6B35)))
private val TVioletGrad = Brush.linearGradient(listOf(TViolet, Color(0xFFEC4899)))

// ── State machine ─────────────────────────────────────────────────────────────
internal enum class OrbState { IDLE, TRANSCRIBING, SUMMARIZING, DONE_TRANSCRIPT, DONE_SUMMARY, ERROR }

// ════════════════════════════════════════════════════════════════════════════
//  ACTIVITY
// ════════════════════════════════════════════════════════════════════════════
class Transcriptactivity : AppCompatActivity() {

    private val BASE_URL = "https://newmeetaibackend.onrender.com"

    private var orbState       by mutableStateOf(OrbState.IDLE)
    private var statusText     by mutableStateOf("Initialising...")
    private var transcriptText by mutableStateOf("")
    private var summaryText    by mutableStateOf("")
    private var lineCount      by mutableStateOf(0)
    private var showSummaryBtn by mutableStateOf(false)
    private var showDlSummary  by mutableStateOf(false)
    private var showSendBtn    by mutableStateOf(false)
    private var errorMsg       by mutableStateOf("")

    private var transcriptFileName = "transcript.txt"
    private var audioFilePath      = ""
    private var roomCode           = ""
    private var duration           = ""
    private var summaryPdfPath     = ""
    private var adminEmail         = ""
    private var memberEmails       = listOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor     = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars     = false
            isAppearanceLightNavigationBars = false
        }

        audioFilePath      = intent.getStringExtra("audio_file_path")        ?: ""
        transcriptFileName = intent.getStringExtra("transcript_filename")    ?: "transcript.txt"
        roomCode           = intent.getStringExtra("room_code")              ?: ""
        duration           = intent.getStringExtra("duration")               ?: ""
        adminEmail         = intent.getStringExtra("admin_email")            ?: ""
        memberEmails       = intent.getStringArrayListExtra("member_emails") ?: arrayListOf()

        setContent {
            TranscriptScreen(
                orbState             = orbState,
                statusText           = statusText,
                transcriptText       = transcriptText,
                summaryText          = summaryText,
                lineCount            = lineCount,
                roomCode             = roomCode,
                duration             = duration,
                showSummaryBtn       = showSummaryBtn,
                showDlSummary        = showDlSummary,
                showSendBtn          = showSendBtn,
                errorMsg             = errorMsg,
                onClose              = { finish() },
                onGetSummary         = { callSummarizeApi() },
                onDownloadTranscript = { downloadTranscript() },
                onShareTranscript    = { shareFile() },
                onDownloadSummary    = { downloadSummaryPdf() },
                onSendToMembers      = { showSendConfirmDialog() }
            )
        }

        if (audioFilePath.isNotEmpty()) callTranscribeApi(audioFilePath)
        else { errorMsg = "No audio file found."; orbState = OrbState.ERROR }
    }

    // ── Transcribe ────────────────────────────────────────────────────────────
    private fun callTranscribeApi(filePath: String) {
        orbState   = OrbState.TRANSCRIBING
        statusText = "Listening to your meeting..."

        val audioFile = File(filePath)
        if (!audioFile.exists()) { setError("Audio file not found."); return }

        val client = makeClient(120)
        val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", audioFile.name, audioFile.asRequestBody("audio/mpeg".toMediaTypeOrNull()))
            .build()

        client.newCall(Request.Builder().url("$BASE_URL/transcribe").post(requestBody).build())
            .enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread { setError("Network error: ${e.message}") }
                }
                override fun onResponse(call: Call, response: Response) {
                    val body = response.body?.string() ?: ""
                    runOnUiThread {
                        if (response.isSuccessful) onTranscriptReady(body)
                        else setError("Server error ${response.code}")
                    }
                }
            })
    }

    private fun onTranscriptReady(text: String) {
        transcriptText = text
        lineCount      = text.lines().count { it.trim().isNotEmpty() }
        orbState       = OrbState.DONE_TRANSCRIPT
        statusText     = "Transcript complete"
        showSummaryBtn = true
        try { File(audioFilePath).delete() } catch (_: Exception) {}
    }

    // ── Summarize ─────────────────────────────────────────────────────────────
    private fun callSummarizeApi() {
        if (transcriptText.isEmpty()) { toast("No transcript to summarize"); return }
        orbState       = OrbState.SUMMARIZING
        statusText     = "AI is reading your meeting..."
        showSummaryBtn = false
        summaryText    = ""
        showDlSummary  = false
        showSendBtn    = false

        val tmp = File(cacheDir, "transcript_temp.txt").apply { writeText(transcriptText) }
        val client = makeClient(120)
        val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", tmp.name, tmp.asRequestBody("text/plain".toMediaTypeOrNull()))
            .build()

        client.newCall(Request.Builder().url("$BASE_URL/summarize").post(requestBody).build())
            .enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread { setError("Summary failed: ${e.message}"); showSummaryBtn = true }
                }
                override fun onResponse(call: Call, response: Response) {
                    val body = response.body?.string() ?: ""
                    runOnUiThread {
                        tmp.delete()
                        if (response.isSuccessful) {
                            try {
                                onSummaryReady(JSONObject(body).getString("summary"))
                            } catch (_: Exception) { setError("Parse error"); showSummaryBtn = true }
                        } else { setError("Server error ${response.code}"); showSummaryBtn = true }
                    }
                }
            })
    }

    private fun onSummaryReady(text: String) {
        summaryText = text
        orbState    = OrbState.DONE_SUMMARY
        statusText  = "Summary ready"
        try {
            summaryPdfPath = generateSummaryPdf(text)
            showDlSummary  = true
            showSendBtn    = memberEmails.isNotEmpty()
        } catch (e: Exception) { toast("PDF generation failed: ${e.message}") }
    }

    // ── PDF  (markdown-aware renderer, fixed drawText) ────────────────────────
    private fun generateSummaryPdf(summaryText: String): String {
        val doc = PdfDocument()
        val PW  = 595f; val PH = 842f
        val MG  = 48f;  val MR = 48f
        val UW  = PW - MG - MR
        val BOT = PH - 70f

        // Colours
        val cBg      = AColor.parseColor("#050810")
        val cCard    = AColor.parseColor("#090E1C")
        val cCyan    = AColor.parseColor("#00D4FF")
        val cViolet  = AColor.parseColor("#8B5CF6")
        val cGold    = AColor.parseColor("#FFBF3C")
        val cText    = AColor.parseColor("#E8EEF8")
        val cSubText = AColor.parseColor("#6B7FA3")
        val cMuted   = AColor.parseColor("#2E3B55")
        val cDivider = AColor.parseColor("#141C2E")
        val cAccent  = AColor.parseColor("#00D4FF")

        // ── Paint helpers ────────────────────────────────────────────────────
        // NOTE: always pass android.graphics.Paint (APaint) to ACanvas.drawText()
        fun mkPaint(color: Int, size: Float, bold: Boolean = false, italic: Boolean = false) =
            APaint(APaint.ANTI_ALIAS_FLAG).apply {
                this.color     = color
                textSize       = size
                isFakeBoldText = bold
                textSkewX      = if (italic) -0.25f else 0f
                style          = APaint.Style.FILL
            }

        fun mkStrokePaint(color: Int, width: Float) =
            APaint(APaint.ANTI_ALIAS_FLAG).apply {
                this.color  = color
                strokeWidth = width
                style       = APaint.Style.STROKE
            }

        fun mkFillPaint(color: Int) =
            APaint(APaint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style      = APaint.Style.FILL
            }

        // ── Page state ───────────────────────────────────────────────────────
        var pageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PW.toInt(), PH.toInt(), pageNum).create()
        var curPage  = doc.startPage(pageInfo)
        var cc: ACanvas = curPage.canvas
        var y = 0f

        fun fillBg() {
            cc.drawRect(0f, 0f, PW, PH, mkFillPaint(cBg))
            // Top accent line
            cc.drawRect(0f, 0f, PW, 3f, mkFillPaint(cCyan))
        }

        fun drawFooter(canvas: ACanvas, pn: Int) {
            val footY = PH - 24f
            canvas.drawLine(MG, PH - 44f, PW - MR, PH - 44f, mkStrokePaint(cDivider, 0.8f))
            canvas.drawText("Generated by MeetAI", MG, footY, mkPaint(cMuted, 9f))
            val pStr = "Page $pn"
            val pW   = mkPaint(cMuted, 9f).measureText(pStr)
            canvas.drawText(pStr, PW - MR - pW, footY, mkPaint(cMuted, 9f))
        }

        fun newPage() {
            drawFooter(cc, pageNum)
            doc.finishPage(curPage)
            pageNum++
            pageInfo = PdfDocument.PageInfo.Builder(PW.toInt(), PH.toInt(), pageNum).create()
            curPage  = doc.startPage(pageInfo)
            cc       = curPage.canvas
            fillBg()
            y = MG + 8f
        }

        fun ensureSpace(need: Float) { if (y + need > BOT) newPage() }

        // ── Word-wrap draw ───────────────────────────────────────────────────
        fun drawWrapped(text: String, p: APaint, left: Float = MG, width: Float = UW, lh: Float = p.textSize * 1.6f) {
            val words = text.split(" ")
            var line  = ""
            for (w in words) {
                val candidate = if (line.isEmpty()) w else "$line $w"
                if (p.measureText(candidate) <= width) {
                    line = candidate
                } else {
                    ensureSpace(lh)
                    cc.drawText(line, left, y, p)
                    y += lh; line = w
                }
            }
            if (line.isNotEmpty()) {
                ensureSpace(lh)
                cc.drawText(line, left, y, p)
                y += lh
            }
        }

        fun cleanInline(s: String): String =
            s.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
                .replace(Regex("\\*(.+?)\\*"),        "$1")
                .replace(Regex("__(.+?)__"),           "$1")
                .replace(Regex("_(.+?)_"),             "$1")
                .replace(Regex("`(.+?)`"),             "$1")
                .trim()

        fun isBold(s: String) = s.contains(Regex("\\*\\*.+\\*\\*|__.+__"))

        // ════════════════════════════════════════════════════════════════════
        //  COVER / HEADER
        // ════════════════════════════════════════════════════════════════════
        fillBg()

        // Logo
        cc.drawText("MEETAI", MG, 36f, mkPaint(cCyan, 11f, bold = true))
        val dateStr = SimpleDateFormat("dd MMM yyyy  •  HH:mm", Locale.getDefault()).format(Date())
        val datePaint = mkPaint(cMuted, 9f)
        cc.drawText(dateStr, PW - MR - datePaint.measureText(dateStr), 36f, datePaint)

        y = 68f
        cc.drawText("Meeting Summary", MG, y, mkPaint(cText, 26f, bold = true))
        y += 8f
        cc.drawLine(MG, y, MG + 180f, y, mkStrokePaint(cCyan, 1.5f))
        y += 20f

        val metaPaint = mkPaint(cSubText, 10f)
        cc.drawText("Room  $roomCode", MG, y, metaPaint)
        cc.drawText("Duration  $duration", MG + 160f, y, metaPaint)
        y += 22f
        cc.drawLine(MG, y, PW - MR, y, mkStrokePaint(cDivider, 0.8f))
        y += 26f

        // ════════════════════════════════════════════════════════════════════
        //  PARSE + RENDER MARKDOWN
        // ════════════════════════════════════════════════════════════════════
        for (raw in summaryText.lines()) {
            val line = raw.trimEnd()
            when {
                line.startsWith("# ") -> {
                    val text = cleanInline(line.removePrefix("# "))
                    ensureSpace(54f)
                    y += 10f
                    cc.drawRect(MG, y - 17f, MG + 3f, y + 5f, mkFillPaint(cViolet))
                    cc.drawText(text, MG + 12f, y, mkPaint(cText, 16f, bold = true))
                    y += 16f
                    cc.drawLine(MG, y, PW - MR, y, mkStrokePaint(cDivider, 0.8f))
                    y += 14f
                }
                line.startsWith("## ") -> {
                    val text = cleanInline(line.removePrefix("## "))
                    ensureSpace(44f)
                    y += 8f
                    cc.drawRect(MG, y - 14f, MG + 2f, y + 3f, mkFillPaint(cCyan))
                    cc.drawText(text, MG + 10f, y, mkPaint(cCyan, 13f, bold = true))
                    y += 16f
                }
                line.startsWith("### ") -> {
                    val text = cleanInline(line.removePrefix("### "))
                    ensureSpace(36f)
                    y += 6f
                    cc.drawText(text, MG, y, mkPaint(cGold, 12f, bold = true))
                    y += 16f
                }
                line.matches(Regex("^\\*\\*.+\\*\\*$")) -> {
                    val text = cleanInline(line)
                    ensureSpace(40f)
                    y += 8f
                    cc.drawRect(MG, y - 13f, MG + 2f, y + 3f, mkFillPaint(cCyan))
                    cc.drawText(text, MG + 10f, y, mkPaint(cCyan, 12f, bold = true))
                    y += 16f
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    val text  = cleanInline(line.drop(2))
                    val bodyP = if (isBold(line.drop(2))) mkPaint(cText, 11.5f, bold = true)
                    else mkPaint(cText, 11.5f)
                    ensureSpace(22f)
                    cc.drawCircle(MG + 6f, y - 3.5f, 2.5f, mkFillPaint(cAccent))
                    drawWrapped(text, bodyP, left = MG + 16f, width = UW - 16f)
                    y += 2f
                }
                line.startsWith("  - ") || line.startsWith("  * ") -> {
                    val text = cleanInline(line.trim().drop(2))
                    ensureSpace(18f)
                    cc.drawCircle(MG + 20f, y - 3.5f, 1.8f, mkFillPaint(cSubText.toInt()))
                    drawWrapped(text, mkPaint(cSubText, 11f), left = MG + 30f, width = UW - 30f)
                    y += 1f
                }
                line.matches(Regex("^\\d+\\. .+")) -> {
                    val num  = line.substringBefore(". ")
                    val text = cleanInline(line.substringAfter(". "))
                    ensureSpace(22f)
                    cc.drawText("$num.", MG, y, mkPaint(cCyan, 11f, bold = true))
                    drawWrapped(text, mkPaint(cText, 11.5f), left = MG + 22f, width = UW - 22f)
                    y += 2f
                }
                line.matches(Regex("^[-*]{3,}$")) -> {
                    ensureSpace(18f)
                    y += 6f
                    cc.drawLine(MG, y, PW - MR, y, mkStrokePaint(cDivider, 0.8f))
                    y += 14f
                }
                line.isBlank() -> { y += 8f }
                else -> {
                    val p = if (isBold(line)) mkPaint(cText, 11.5f, bold = true) else mkPaint(cText, 11.5f)
                    drawWrapped(cleanInline(line), p)
                    y += 3f
                }
            }
        }

        // Footer on last page, then close
        drawFooter(cc, pageNum)
        doc.finishPage(curPage)

        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        val f     = File(cacheDir, "Summary_${roomCode}_$stamp.pdf")
        FileOutputStream(f).use { doc.writeTo(it) }
        doc.close()
        return f.absolutePath
    }

    // ── Actions ───────────────────────────────────────────────────────────────
    private fun downloadSummaryPdf() {
        if (summaryPdfPath.isEmpty()) return
        try {
            val src = File(summaryPdfPath)
            val dst = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).also { it.mkdirs() },
                src.name
            )
            src.copyTo(dst, overwrite = true)
            toast("PDF saved to Downloads/${src.name}")
        } catch (e: Exception) {
            try {
                val uri = FileProvider.getUriForFile(this, "${packageName}.provider", File(summaryPdfPath))
                startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/pdf")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "Open PDF"
                ))
            } catch (ex: Exception) { toast("Download failed: ${ex.message}") }
        }
    }

    private fun downloadTranscript() {
        try {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).also { it.mkdirs() }
            FileWriter(File(dir, transcriptFileName)).use { it.write(transcriptText) }
            toast("Saved to Downloads/$transcriptFileName")
        } catch (e: Exception) {
            try {
                val f = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: filesDir, transcriptFileName)
                FileWriter(f).use { it.write(transcriptText) }
                toast("Saved to app Documents")
            } catch (ex: Exception) { toast("Save failed: ${ex.message}") }
        }
    }

    private fun shareFile() {
        try {
            val f = File(cacheDir, transcriptFileName).apply { FileWriter(this).use { it.write(transcriptText) } }
            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", f)
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "MeetAI Transcript – $transcriptFileName")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Share Transcript"
            ))
        } catch (e: Exception) {
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, transcriptText)
                    putExtra(Intent.EXTRA_SUBJECT, "MeetAI Transcript")
                }, "Share Transcript"
            ))
        }
    }

    private fun showSendConfirmDialog() {
        val list = memberEmails.joinToString("\n• ", "• ")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Send Summary PDF")
            .setMessage("Email app will open with PDF attached to ${memberEmails.size} member(s):\n\n$list")
            .setPositiveButton("Open Email App") { _, _ -> sendPdfViaEmail() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun sendPdfViaEmail() {
        if (summaryPdfPath.isEmpty() || memberEmails.isEmpty()) return
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", File(summaryPdfPath))
            val ei = Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_EMAIL, memberEmails.toTypedArray())
                putExtra(Intent.EXTRA_SUBJECT, "MeetAI — Meeting Summary [$roomCode]")
                putExtra(Intent.EXTRA_TEXT,
                    "Hi,\n\nPlease find attached the AI-generated summary.\n\nRoom: $roomCode\nDuration: $duration\n\nBest,\n$adminEmail")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            packageManager.queryIntentActivities(
                Intent.createChooser(ei, "Send"),
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
            ).forEach { grantUriPermission(it.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            startActivity(Intent.createChooser(ei, "Send Summary via Email"))
        } catch (e: Exception) { toast("Failed to open email: ${e.message}") }
    }

    private fun setError(msg: String) {
        errorMsg   = msg
        orbState   = OrbState.ERROR
        statusText = "Something went wrong"
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun makeClient(readSec: Long) = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(readSec, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS).build()

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        startActivity(
            Intent(this, Newmeetingactivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        )
        finish()
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  SCREEN
// ════════════════════════════════════════════════════════════════════════════
@Composable
internal fun TranscriptScreen(
    orbState:             OrbState,
    statusText:           String,
    transcriptText:       String,
    summaryText:          String,
    lineCount:            Int,
    roomCode:             String,
    duration:             String,
    showSummaryBtn:       Boolean,
    showDlSummary:        Boolean,
    showSendBtn:          Boolean,
    errorMsg:             String,
    onClose:              () -> Unit,
    onGetSummary:         () -> Unit,
    onDownloadTranscript: () -> Unit,
    onShareTranscript:    () -> Unit,
    onDownloadSummary:    () -> Unit,
    onSendToMembers:      () -> Unit
) {
    Box(Modifier.fillMaxSize().background(TBgGrad)) {

        StarfieldBg()

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(8.dp))

            // ── Top bar ───────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Back / close button
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(TInkLight)
                        .border(1.dp, TCardBorder, RoundedCornerShape(11.dp))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Canvas(Modifier.size(13.dp)) {
                        val p = androidx.compose.ui.graphics.Paint().apply {
                            color       = TTextSub
                            style       = PaintingStyle.Stroke
                            strokeWidth = 2f
                            strokeCap   = StrokeCap.Round
                        }
                        drawContext.canvas.apply {
                            drawLine(Offset(0f, 0f), Offset(size.width, size.height), p)
                            drawLine(Offset(size.width, 0f), Offset(0f, size.height), p)
                        }
                    }
                }

                // App badge
                Row(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(TInkLight)
                        .border(1.dp, TCardBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(Modifier.size(6.dp).background(
                        Brush.radialGradient(listOf(TCyan, TCyan.copy(0.1f))), CircleShape
                    ))
                    Text("MeetAI", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = TTextPri, letterSpacing = 0.4.sp)
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── AI ORB ────────────────────────────────────────────────────────
            AIVoiceOrb(state = orbState)

            Spacer(Modifier.height(20.dp))

            // Status
            AnimatedContent(
                targetState = statusText,
                transitionSpec = {
                    fadeIn(tween(400)) + slideInVertically(tween(400)) { 12 } togetherWith fadeOut(tween(300))
                },
                label = "status"
            ) { txt ->
                Text(
                    txt,
                    fontSize      = 15.sp,
                    fontWeight    = FontWeight.SemiBold,
                    color         = when (orbState) {
                        OrbState.ERROR           -> TRed
                        OrbState.DONE_SUMMARY    -> TGold
                        OrbState.DONE_TRANSCRIPT -> TGreen
                        else                     -> TCyan
                    },
                    textAlign     = TextAlign.Center,
                    letterSpacing = 0.2.sp
                )
            }

            Spacer(Modifier.height(10.dp))

            // Meta chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetaChip("Room  $roomCode", TCyan)
                MetaChip("Duration  $duration", TGold)
            }

            Spacer(Modifier.height(24.dp))

            // ── Waveform (loading only) ───────────────────────────────────────
            AnimatedVisibility(
                visible = orbState == OrbState.TRANSCRIBING || orbState == OrbState.SUMMARIZING,
                enter   = fadeIn(tween(400)),
                exit    = fadeOut(tween(300))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    WaveformBars(color = if (orbState == OrbState.SUMMARIZING) TViolet else TCyan)
                    Spacer(Modifier.height(12.dp))
                }
            }

            // ── Transcript card ───────────────────────────────────────────────
            AnimatedVisibility(
                visible = transcriptText.isNotEmpty(),
                enter   = fadeIn(tween(600)) + slideInVertically(tween(600)) { 60 },
                exit    = fadeOut(tween(300))
            ) {
                ContentCard(
                    title   = "Transcript",
                    badge   = if (lineCount > 0) "$lineCount lines" else null,
                    tint    = TCyan,
                    content = transcriptText,
                    actions = if (showSummaryBtn || orbState == OrbState.DONE_TRANSCRIPT || orbState == OrbState.DONE_SUMMARY) {
                        listOf(
                            ActionBtn("Download", TCyanGrad)   { onDownloadTranscript() },
                            ActionBtn("Share",    TBorderGrad) { onShareTranscript() }
                        )
                    } else emptyList()
                )
            }

            Spacer(Modifier.height(14.dp))

            // ── Generate summary CTA ──────────────────────────────────────────
            AnimatedVisibility(
                visible = showSummaryBtn,
                enter   = fadeIn(tween(500)) + scaleIn(tween(500, easing = EaseOutBack)),
                exit    = fadeOut(tween(300)) + scaleOut(tween(300))
            ) {
                AIOrbButton(
                    label    = "Generate AI Summary",
                    gradient = TVioletGrad,
                    onClick  = onGetSummary
                )
            }

            Spacer(Modifier.height(14.dp))

            // ── Summary card ──────────────────────────────────────────────────
            AnimatedVisibility(
                visible = summaryText.isNotEmpty(),
                enter   = fadeIn(tween(700)) + slideInVertically(tween(700)) { 80 },
                exit    = fadeOut(tween(300))
            ) {
                ContentCard(
                    title   = "AI Summary",
                    badge   = "Ready",
                    tint    = TViolet,
                    content = summaryText,
                    actions = buildList {
                        if (showDlSummary) add(ActionBtn("Download PDF", TGoldGrad)       { onDownloadSummary() })
                        if (showSendBtn)   add(ActionBtn("Send to Members", TGreenGrad)   { onSendToMembers() })
                    }
                )
            }

            // ── Error ─────────────────────────────────────────────────────────
            AnimatedVisibility(visible = orbState == OrbState.ERROR) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(TRed.copy(0.08f))
                        .border(1.dp, TRed.copy(0.3f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text(errorMsg, fontSize = 13.sp, color = TRed.copy(0.85f), lineHeight = 20.sp)
                }
            }

            Spacer(Modifier.height(60.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

private val TBorderGrad = Brush.linearGradient(listOf(Color(0xFF1A2235), Color(0xFF1A2235)))

// ════════════════════════════════════════════════════════════════════════════
//  AI VOICE ORB
// ════════════════════════════════════════════════════════════════════════════
@Composable
internal fun AIVoiceOrb(state: OrbState) {
    val inf = rememberInfiniteTransition(label = "orb")

    val rotation by inf.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "rot"
    )
    val rotationRev by inf.animateFloat(
        360f, 0f,
        infiniteRepeatable(tween(5500, easing = LinearEasing)),
        label = "rotR"
    )
    val breathScale by inf.animateFloat(
        0.93f, 1.07f,
        infiniteRepeatable(
            tween(
                if (state == OrbState.IDLE || state == OrbState.DONE_TRANSCRIPT || state == OrbState.DONE_SUMMARY) 2200 else 550,
                easing = EaseInOutSine
            ),
            RepeatMode.Reverse
        ),
        label = "breath"
    )
    val pulseAlpha by inf.animateFloat(
        0f, 0.45f,
        infiniteRepeatable(tween(1600, easing = EaseOutSine), RepeatMode.Reverse),
        label = "pulse"
    )
    val pulseScale by inf.animateFloat(
        0.88f, 1.65f,
        infiniteRepeatable(tween(1600, easing = EaseOutSine), RepeatMode.Reverse),
        label = "pulseS"
    )
    val pulse2Scale by inf.animateFloat(
        1.05f, 1.9f,
        infiniteRepeatable(tween(2000, delayMillis = 500, easing = EaseOutSine), RepeatMode.Reverse),
        label = "p2s"
    )
    val pulse2Alpha by inf.animateFloat(
        0f, 0.22f,
        infiniteRepeatable(tween(2000, delayMillis = 500, easing = EaseOutSine), RepeatMode.Reverse),
        label = "p2a"
    )
    val wavePhase by inf.animateFloat(
        0f, 2 * PI.toFloat(),
        infiniteRepeatable(
            tween(if (state == OrbState.TRANSCRIBING || state == OrbState.SUMMARIZING) 750 else 2400, easing = LinearEasing)
        ),
        label = "wave"
    )

    val orbColor by animateColorAsState(
        when (state) {
            OrbState.IDLE            -> TCyan
            OrbState.TRANSCRIBING    -> TCyan
            OrbState.SUMMARIZING     -> TViolet
            OrbState.DONE_TRANSCRIPT -> TGreen
            OrbState.DONE_SUMMARY    -> TGold
            OrbState.ERROR           -> TRed
        },
        tween(900), label = "orbColor"
    )

    val orbSize = 164.dp

    Box(Modifier.size(orbSize), contentAlignment = Alignment.Center) {

        // Outer pulse ring
        Box(
            Modifier
                .size(orbSize)
                .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale; alpha = pulseAlpha }
                .background(Brush.radialGradient(listOf(orbColor.copy(0.28f), Color.Transparent)), CircleShape)
        )

        // Second pulse ring
        Box(
            Modifier
                .size(orbSize)
                .graphicsLayer { scaleX = pulse2Scale; scaleY = pulse2Scale; alpha = pulse2Alpha }
                .background(Brush.radialGradient(listOf(orbColor.copy(0.15f), Color.Transparent)), CircleShape)
        )

        // Canvas-drawn orb
        androidx.compose.foundation.Canvas(
            Modifier
                .size(orbSize)
                .graphicsLayer { scaleX = breathScale; scaleY = breathScale }
        ) {
            val cx = size.width / 2
            val cy = size.height / 2
            val r  = size.minDimension / 2

            // Outer halo
            drawCircle(
                brush  = Brush.radialGradient(
                    listOf(orbColor.copy(0.22f), Color.Transparent),
                    center = Offset(cx, cy), radius = r
                ),
                radius = r,
                center = Offset(cx, cy)
            )

            // Rotating outer arc
            rotate(rotation, Offset(cx, cy)) {
                drawArc(
                    brush      = Brush.sweepGradient(listOf(orbColor, orbColor.copy(0f)), Offset(cx, cy)),
                    startAngle = 0f,
                    sweepAngle = 230f,
                    useCenter  = false,
                    topLeft    = Offset(cx - r * 0.9f, cy - r * 0.9f),
                    size       = Size(r * 1.8f, r * 1.8f),
                    style      = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Rotating inner arc (reverse)
            rotate(rotationRev, Offset(cx, cy)) {
                drawArc(
                    brush      = Brush.sweepGradient(listOf(TGold.copy(0.55f), TGold.copy(0f)), Offset(cx, cy)),
                    startAngle = 0f,
                    sweepAngle = 150f,
                    useCenter  = false,
                    topLeft    = Offset(cx - r * 0.72f, cy - r * 0.72f),
                    size       = Size(r * 1.44f, r * 1.44f),
                    style      = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Core filled circle
            drawCircle(
                brush  = Brush.radialGradient(
                    listOf(orbColor.copy(0.85f), orbColor.copy(0.25f), TInkMid),
                    center = Offset(cx, cy), radius = r * 0.55f
                ),
                radius = r * 0.55f,
                center = Offset(cx, cy)
            )

            // Morphing wave ring (active states)
            if (state == OrbState.TRANSCRIBING || state == OrbState.SUMMARIZING) {
                val waveR   = r * 0.63f
                val waveAmp = r * 0.09f
                val steps   = 120
                val path    = Path()
                for (i in 0..steps) {
                    val angle = (i.toFloat() / steps) * 2 * PI.toFloat()
                    val wave  = waveAmp * sin(angle * 5 + wavePhase)
                    val rr    = waveR + wave
                    val px    = cx + rr * cos(angle)
                    val py    = cy + rr * sin(angle)
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                drawPath(path, orbColor.copy(0.45f), style = Stroke(width = 1.4f))
            }

            // Specular highlight
            drawCircle(
                brush  = Brush.radialGradient(
                    listOf(Color.White.copy(0.9f), orbColor.copy(0.35f), Color.Transparent),
                    center = Offset(cx - r * 0.1f, cy - r * 0.12f),
                    radius = r * 0.22f
                ),
                radius = r * 0.22f,
                center = Offset(cx - r * 0.1f, cy - r * 0.12f)
            )
        }

        // State glyph
        Text(
            text = when (state) {
                OrbState.IDLE, OrbState.TRANSCRIBING, OrbState.SUMMARIZING -> "AI"
                OrbState.DONE_TRANSCRIPT -> "✓"
                OrbState.DONE_SUMMARY    -> "✦"
                OrbState.ERROR           -> "!"
            },
            fontSize      = 21.sp,
            fontWeight    = FontWeight.Black,
            color         = Color.White,
            letterSpacing = (-0.5).sp
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  WAVEFORM BARS
// ════════════════════════════════════════════════════════════════════════════
@Composable
internal fun WaveformBars(color: Color) {
    val inf      = rememberInfiniteTransition(label = "wave")
    val barCount = 30

    Row(
        Modifier.height(48.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        (0 until barCount).forEach { i ->
            val phase  = i.toFloat() / barCount
            val speed  = 380 + (i % 5) * 90
            val delay  = (phase * 600).toInt()
            val minH   = 0.07f + (sin(phase * PI.toFloat() * 2) * 0.04f).absoluteValue
            val maxH   = 0.3f + (sin(phase * PI.toFloat()) * 0.7f).absoluteValue.coerceIn(0.2f, 0.95f)

            val heightFrac by inf.animateFloat(
                minH, maxH,
                infiniteRepeatable(tween(speed, delayMillis = delay, easing = EaseInOutSine), RepeatMode.Reverse),
                label = "bar$i"
            )

            Box(
                Modifier
                    .width(3.5.dp)
                    .fillMaxHeight(heightFrac)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Brush.verticalGradient(listOf(color.copy(0.15f), color, color.copy(0.2f))))
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  STARFIELD
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun StarfieldBg() {
    val stars = remember {
        List(55) {
            Triple(
                (0..100).random() / 100f,
                (0..100).random() / 100f,
                (2..9).random() / 10f
            )
        }
    }
    val inf = rememberInfiniteTransition(label = "stars")

    Box(Modifier.fillMaxSize()) {
        stars.forEachIndexed { i, (x, y, alpha) ->
            val twinkle by inf.animateFloat(
                alpha * 0.25f, alpha,
                infiniteRepeatable(tween(900 + i * 75, easing = EaseInOutSine), RepeatMode.Reverse),
                label = "star$i"
            )
            Box(Modifier.fillMaxSize().drawBehind {
                drawCircle(Color.White.copy(twinkle), radius = 1.1f, center = Offset(size.width * x, size.height * y))
            })
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  META CHIP
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun MetaChip(text: String, color: Color) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(color.copy(0.05f))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(listOf(color.copy(0.45f), color.copy(0.08f))),
                shape = RoundedCornerShape(50.dp)
            )
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(5.dp).background(
            Brush.radialGradient(listOf(color, color.copy(0.15f))), CircleShape
        ))
        Text(text, fontSize = 10.sp, color = TTextSub, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp)
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  CONTENT CARD
// ════════════════════════════════════════════════════════════════════════════
private data class ActionBtn(val label: String, val gradient: Brush, val action: () -> Unit)

@Composable
private fun ContentCard(
    title:   String,
    badge:   String?,
    tint:    Color,
    content: String,
    actions: List<ActionBtn>
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(TInkMid)
            .border(1.dp, tint.copy(0.18f), RoundedCornerShape(20.dp))
    ) {
        // Top accent line
        Box(Modifier.fillMaxWidth().height(1.5.dp).background(
            Brush.linearGradient(listOf(tint, tint.copy(0f)))
        ))

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            // Header row
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(Modifier.size(6.dp).background(tint, CircleShape))
                    Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = TTextPri, letterSpacing = 0.3.sp)
                }
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    if (badge != null) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(tint.copy(0.1f))
                                .border(1.dp, tint.copy(0.25f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(badge, fontSize = 9.sp, color = tint, fontWeight = FontWeight.Bold)
                        }
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(TInkLighter)
                            .clickable { expanded = !expanded }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            if (expanded) "Show less" else "Show more",
                            fontSize = 9.sp, color = TTextMuted, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Content
            if (expanded) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp, max = 300.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(TInk.copy(0.7f))
                        .border(1.dp, tint.copy(0.07f), RoundedCornerShape(10.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp)
                ) {
                    Text(content, fontSize = 12.sp, color = TTextSub, lineHeight = 19.sp)
                }
            } else {
                Text(
                    content,
                    fontSize  = 12.sp,
                    color     = TTextSub,
                    lineHeight = 19.sp,
                    maxLines  = 5,
                    overflow  = TextOverflow.Ellipsis
                )
            }

            // Action buttons
            if (actions.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    actions.forEach { btn ->
                        TActionButton(
                            label    = btn.label,
                            gradient = btn.gradient,
                            modifier = Modifier.weight(1f),
                            onClick  = btn.action
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TActionButton(label: String, gradient: Brush, modifier: Modifier, onClick: () -> Unit) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        spring(Spring.DampingRatioMediumBouncy),
        label = "tab"
    )

    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(11.dp))
            .background(gradient)
            .clickable(interactionSource, null) { onClick() }
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            color = Color.Black, letterSpacing = 0.2.sp)
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  AI ORB ACTION BUTTON
// ════════════════════════════════════════════════════════════════════════════
@Composable
private fun AIOrbButton(label: String, gradient: Brush, onClick: () -> Unit) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        spring(Spring.DampingRatioMediumBouncy),
        label = "aob"
    )

    val inf = rememberInfiniteTransition(label = "btnGlow")
    val glow by inf.animateFloat(
        0.28f, 0.65f,
        infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "bg"
    )

    Box(
        Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                drawRoundRect(
                    color        = TViolet.copy(glow * 0.28f),
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    size         = size.copy(size.width + 10.dp.toPx(), size.height + 10.dp.toPx()),
                    topLeft      = Offset(-5.dp.toPx(), -5.dp.toPx())
                )
            }
            .clip(RoundedCornerShape(16.dp))
            .background(gradient)
            .clickable(interactionSource, null) { onClick() }
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Spark icon
            androidx.compose.foundation.Canvas(Modifier.size(16.dp)) {
                drawCircle(
                    Color.White.copy(0.85f),
                    radius = 3.dp.toPx(),
                    center = Offset(size.width / 2, size.height / 2)
                )
                val p = androidx.compose.ui.graphics.Paint().apply {
                    color       = Color.White
                    style       = PaintingStyle.Stroke
                    strokeWidth = 1.5f
                    strokeCap   = StrokeCap.Round
                }
                drawContext.canvas.apply {
                    drawLine(Offset(size.width / 2, 0f), Offset(size.width / 2, 5f), p)
                    drawLine(Offset(size.width / 2, size.height - 5f), Offset(size.width / 2, size.height), p)
                    drawLine(Offset(0f, size.height / 2), Offset(5f, size.height / 2), p)
                    drawLine(Offset(size.width - 5f, size.height / 2), Offset(size.width, size.height / 2), p)
                }
            }
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Black,
                color = Color.White, letterSpacing = 0.3.sp)
        }
    }
}