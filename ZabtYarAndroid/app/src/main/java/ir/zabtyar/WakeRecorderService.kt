
package ir.zabtyar

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.util.Log
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class WakeRecorderService : Service() {

    companion object {
        const val ACTION_START = "ir.zabtyar.ACTION_START"
        private const val CHANNEL_ID = "ava_voice_service"
        private const val NOTIFICATION_ID = 701
        private const val SAMPLE_RATE = 16000
        private const val TAG = "WakeRecorderService"
    }

    private val running = AtomicBoolean(false)
    private val active = AtomicBoolean(false)

    @Volatile
    private var recorder: AudioRecord? = null

    @Volatile
    private var model: Model? = null

    @Volatile
    private var recognizer: Recognizer? = null

    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (intent?.action != ACTION_START && running.get()) {
            return START_NOT_STICKY
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()

        try {
            val notification = buildNotification("در حال آماده‌سازی مدل آفلاین...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not start foreground service", e)
            stopSelf()
            return START_NOT_STICKY
        }

        if (running.compareAndSet(false, true)) {
            worker = Thread({ listenLoop() }, "AvaOfflineListener").also {
                it.start()
            }
        }

        return START_NOT_STICKY
    }

    private fun listenLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)

        var audio: AudioRecord? = null
        var localModel: Model? = null
        var localRecognizer: Recognizer? = null

        try {
            val modelDirectory = installModelFromAssets()
            localModel = Model(modelDirectory.absolutePath)
            model = localModel

            localRecognizer = Recognizer(localModel, SAMPLE_RATE.toFloat())
            recognizer = localRecognizer

            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            if (minBuffer <= 0) {
                throw IllegalStateException("اندازه بافر میکروفن معتبر نیست")
            }

            audio = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer * 2, SAMPLE_RATE)
            )
            recorder = audio

            if (audio.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("میکروفن راه‌اندازی نشد")
            }

            audio.startRecording()
            updateNotification("آوا آماده‌باش است؛ شنود آفلاین فعال است.")

            val buffer = ByteArray(4096)

            while (running.get() && !Thread.currentThread().isInterrupted) {
                val count = audio.read(buffer, 0, buffer.size)

                if (count > 0 && localRecognizer.acceptWaveForm(buffer, count)) {
                    val result = localRecognizer.result
                    val text = Regex("\"text\"\\s*:\\s*\"([^\"]*)\"")
                        .find(result)
                        ?.groupValues
                        ?.getOrNull(1)
                        .orEmpty()

                    if (text.isNotBlank()) {
                        handleRecognizedText(text)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Offline listening failed", e)
            updateNotification("خطا در شنود: ${e.localizedMessage ?: "نامشخص"}")
        } finally {
            running.set(false)

            try {
                audio?.stop()
            } catch (_: Exception) {
            }
            audio?.release()
            recorder = null

            localRecognizer?.close()
            recognizer = null

            localModel?.close()
            model = null

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun handleRecognizedText(text: String) {
        Log.i(TAG, "Recognized: $text")

        val normalized = text
            .lowercase(Locale.ROOT)
            .replace("ي", "ی")
            .replace("ك", "ک")
            .trim()

        when {
            normalized.contains("ضبط‌یار") &&
                normalized.contains("شروع کن") -> {
                active.set(true)
                updateNotification("فرمان شروع دریافت شد.")
            }

            normalized.contains("ضبط‌یار") &&
                normalized.contains("توقف") -> {
                active.set(false)
                updateNotification("فرمان توقف دریافت شد؛ آماده‌باش ادامه دارد.")
            }
        }
    }

    private fun installModelFromAssets(): File {
        val destination = File(filesDir, "vosk-model")

        if (File(destination, "am/final.mdl").isFile &&
            File(destination, "conf/model.conf").isFile
        ) {
            return destination
        }

        if (destination.exists()) {
            destination.deleteRecursively()
        }
        destination.mkdirs()

        copyAssetDirectory("model", destination)

        if (!File(destination, "am/final.mdl").isFile ||
            !File(destination, "conf/model.conf").isFile
        ) {
            destination.deleteRecursively()
            throw IllegalStateException(
                "مدل فارسی در assets/model کامل نیست."
            )
        }

        return destination
    }

    private fun copyAssetDirectory(assetPath: String, destination: File) {
        val entries = assets.list(assetPath)
            ?: throw IllegalStateException("پوشه مدل پیدا نشد: $assetPath")

        destination.mkdirs()

        for (entry in entries) {
            val childAsset = "$assetPath/$entry"
            val childFile = File(destination, entry)
            val children = assets.list(childAsset)

            if (!children.isNullOrEmpty()) {
                copyAssetDirectory(childAsset, childFile)
            } else {
                assets.open(childAsset).use { input ->
                    FileOutputStream(childFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "آماده‌باش آوا",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("آوا (ضبط‌یار)")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(message: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(message))
    }

    override fun onDestroy() {
        running.set(false)

        try {
            recorder?.stop()
        } catch (_: Exception) {
        }

        worker?.interrupt()
        super.onDestroy()
    }
}
