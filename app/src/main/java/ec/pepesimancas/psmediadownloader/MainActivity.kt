package ec.pepesimancas.psmediadownloader

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.widget.*
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : Activity() {
    companion object {
        private const val BG = 0xff0a1424.toInt()
        private const val GOLD = 0xffd5af58.toInt()
        private const val LIGHT = 0xfff0f3f8.toInt()
    }

    private lateinit var link: EditText
    private lateinit var format: Spinner
    private lateinit var quality: Spinner
    private lateinit var details: TextView
    private lateinit var status: TextView
    private lateinit var percent: TextView
    private lateinit var progress: ProgressBar
    private lateinit var downloadButton: Button

    private val worker = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    @Volatile private var processId: String? = null
    @Volatile private var initialized = false
    @Volatile private var cancelled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG

        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(BG)
        }
        scroll.addView(layout)
        setContentView(scroll)

        addText(layout, "PS MEDIA DOWNLOADER PRO", 22, GOLD, true).gravity = Gravity.CENTER
        addText(layout, "PEPE SIMANCAS CAPA · Android Beta", 13, LIGHT, true).gravity = Gravity.CENTER
        addText(layout, "ENLACE", 13, LIGHT, true)
        link = EditText(this).apply {
            hint = "Pega un enlace compatible"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(0xffa5b6c9.toInt())
            setPadding(dp(12), 0, dp(12), 0)
            setBackgroundColor(0xff1a3047.toInt())
        }
        layout.addView(link, LinearLayout.LayoutParams(-1, dp(52)))
        addButton(layout, "ANALIZAR", 0xff31597d.toInt()) { analyze() }
        details = addText(layout, "Sin analizar", 14, LIGHT, false)
        addText(layout, "FORMATO", 13, LIGHT, true)
        format = addSpinner(layout, arrayOf("Video MP4", "Audio MP3"))
        addText(layout, "CALIDAD DE VIDEO", 13, LIGHT, true)
        quality = addSpinner(layout, arrayOf("Mejor MP4 directo", "1080p", "720p", "480p", "360p"))
        downloadButton = addButton(layout, "DESCARGAR AHORA", GOLD) { startDownload() }
        addButton(layout, "DETENER DESCARGA", 0xff6b3546.toInt()) {
            cancelled = true
            processId?.let { YoutubeDL.getInstance().destroyProcessById(it) }
            message("Deteniendo descarga…")
        }
        percent = addText(layout, "0%", 27, GOLD, true).apply { gravity = Gravity.CENTER }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        layout.addView(progress, LinearLayout.LayoutParams(-1, dp(18)))
        status = addText(layout, "Preparando motor…", 14, LIGHT, false)
        addText(layout, "Archivos: Descargas/PS Media Downloader", 12, 0xffb9c6d5.toInt(), false)
        addText(layout, "Utiliza únicamente contenidos propios o autorizados.", 11, 0xffabb8c8.toInt(), false)

        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { link.setText(it) }
        }
        if (Build.VERSION.SDK_INT <= 28 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 123)
        }
        worker.execute {
            try {
                YoutubeDL.getInstance().init(applicationContext)
                FFmpeg.getInstance().init(applicationContext)
                initialized = true
                message("Motor preparado. Pega un enlace.")
            } catch (ex: Exception) {
                message("Error al preparar motor: ${ex.message}")
            }
        }
    }

    private fun url(): String? {
        val value = link.text.toString().trim()
        if (!value.startsWith("https://") && !value.startsWith("http://")) {
            message("Introduce un enlace válido.")
            return null
        }
        return value
    }

    private fun analyze() {
        val target = url() ?: return
        if (!initialized) { message("El motor todavía se está preparando."); return }
        if (!busy.compareAndSet(false, true)) { message("Hay otra operación activa."); return }
        message("Analizando enlace…")
        worker.execute {
            try {
                val info = YoutubeDL.getInstance().getInfo(target)
                runOnUiThread { details.text = info.title ?: "Video identificado" }
                message("Análisis completado: selecciona formato y calidad.")
            } catch (ex: Exception) {
                message("Error de análisis: ${ex.message}")
            } finally {
                busy.set(false)
            }
        }
    }

    private fun startDownload() {
        val target = url() ?: return
        if (!initialized) { message("El motor todavía se está preparando."); return }
        if (Build.VERSION.SDK_INT <= 28 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            message("Concede permiso de almacenamiento.")
            return
        }
        if (!busy.compareAndSet(false, true)) { message("Hay otra operación activa."); return }
        val audio = format.selectedItemPosition == 1
        val selectedQuality = quality.selectedItemPosition
        cancelled = false
        downloadButton.isEnabled = false
        progress.progress = 0
        percent.text = "0%"
        message("Preparando descarga…")

        worker.execute {
            val base = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (base == null) {
                message("Almacenamiento no disponible.")
                finishTask()
                return@execute
            }
            val temp = File(base, "job_${System.currentTimeMillis()}").apply { mkdirs() }
            val id = "ps_${System.currentTimeMillis()}"
            processId = id
            try {
                val request = YoutubeDLRequest(target)
                request.addOption("--no-playlist")
                request.addOption("--newline")
                request.addOption("--socket-timeout", "15")
                request.addOption("--retries", "3")
                request.addOption("-o", File(temp, "%(title).100B [%(id)s].%(ext)s").absolutePath)
                if (audio) {
                    request.addOption("-f", "bestaudio/best")
                    request.addOption("-x")
                    request.addOption("--audio-format", "mp3")
                } else {
                    val maxHeight = when (selectedQuality) {
                        1 -> 1080; 2 -> 720; 3 -> 480; 4 -> 360; else -> null
                    }
                    val filter = maxHeight?.let { "[height<=${it}]" } ?: ""
                    request.addOption(
                        "-f",
                        "best[ext=mp4]${filter}/bestvideo[ext=mp4]${filter}+bestaudio[ext=m4a]/best${filter}/best"
                    )
                    request.addOption("--merge-output-format", "mp4")
                }
                YoutubeDL.getInstance().execute(request, id) { value, eta, line ->
                    runOnUiThread {
                        progress.progress = value.toInt().coerceIn(0, 100)
                        percent.text = String.format(Locale.US, "%.1f%%", value)
                        status.text = if (eta >= 0) {
                            "Descargando · tiempo restante: ${eta} s"
                        } else {
                            line.take(160)
                        }
                    }
                }
                if (cancelled) {
                    message("Descarga cancelada.")
                    return@execute
                }
                val outputs = temp.listFiles { file ->
                    file.isFile && file.length() > 0L &&
                        !file.name.endsWith(".part") && !file.name.endsWith(".ytdl")
                } ?: emptyArray()
                if (outputs.isEmpty()) throw IOException("No se encontró archivo final.")
                outputs.forEach { file -> saveFile(file, if (audio) "audio/mpeg" else "video/mp4") }
                message("DESCARGA COMPLETADA · ${outputs.size} archivo(s) guardado(s).")
                runOnUiThread { progress.progress = 100; percent.text = "100%" }
            } catch (ex: Exception) {
                message(if (cancelled) "Descarga cancelada." else "ERROR: ${ex.message}")
            } finally {
                processId = null
                finishTask()
            }
        }
    }

    private fun finishTask() {
        busy.set(false)
        runOnUiThread { downloadButton.isEnabled = true }
    }

    private fun saveFile(file: File, mime: String) {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/PS Media Downloader")
            }
            val destination = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("No se pudo crear el archivo en Descargas.")
            try {
                FileInputStream(file).use { input ->
                    val output = contentResolver.openOutputStream(destination)
                        ?: throw IOException("No se pudo abrir el archivo.")
                    output.use { input.copyTo(it) }
                }
            } catch (ex: Exception) {
                contentResolver.delete(destination, null, null)
                throw ex
            }
        } else {
            val folder = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "PS Media Downloader"
            ).apply { mkdirs() }
            var destination = File(folder, file.name)
            var counter = 1
            while (destination.exists()) {
                destination = File(folder, "${counter++}_${file.name}")
            }
            FileInputStream(file).use { input ->
                FileOutputStream(destination).use { input.copyTo(it) }
            }
        }
        file.delete()
    }

    private fun message(value: String) = runOnUiThread { status.text = value }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun addText(parent: LinearLayout, label: String, size: Int, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            text = label
            textSize = size.toFloat()
            setTextColor(color)
            setPadding(0, dp(11), 0, dp(9))
            if (bold) setTypeface(null, Typeface.BOLD)
            parent.addView(this)
        }

    private fun addSpinner(parent: LinearLayout, values: Array<String>): Spinner =
        Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, values)
            setBackgroundColor(0xffebf0f5.toInt())
            parent.addView(this, LinearLayout.LayoutParams(-1, dp(52)))
        }

    private fun addButton(parent: LinearLayout, label: String, color: Int, onTap: () -> Unit): Button =
        Button(this).apply {
            text = label
            setTextColor(if (color == GOLD) BG else Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(color)
            parent.addView(this, LinearLayout.LayoutParams(-1, dp(54)))
            setOnClickListener { onTap() }
        }

    override fun onDestroy() {
        processId?.let { YoutubeDL.getInstance().destroyProcessById(it) }
        worker.shutdownNow()
        super.onDestroy()
    }
}
