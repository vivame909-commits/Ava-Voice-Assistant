package ir.zabtyar

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        renderUi()
        requestNeededPermissions()
    }

    private fun renderUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 36, 28, 24)
            layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
        }

        val title = TextView(this).apply {
            text = "ضبط‌یار"
            textSize = 30f
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "ضبط آفلاین با فرمان صوتی"
            textSize = 17f
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 22)
        }
        status = TextView(this).apply {
            text = "برای شروع، مجوز میکروفن را تأیید کن."
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 24)
        }

        val start = Button(this).apply {
            text = "شروع آماده‌باش"
            setOnClickListener {
                if (!hasAudioPermission()) {
                    requestNeededPermissions()
                    status.text = "ابتدا مجوز میکروفن را تأیید کن."
                } else {
                    val intent = Intent(this@MainActivity, WakeRecorderService::class.java)
                        .setAction(WakeRecorderService.ACTION_START)
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                        status.text = "درخواست آماده‌باش ارسال شد. اعلان سرویس را بررسی کن."
                    } catch (e: Exception) {
                        status.text = "شروع سرویس ممکن نشد: ${e.localizedMessage ?: "خطای نامشخص"}"
                    }
                }
            }
        }

        val stop = Button(this).apply {
            text = "توقف سرویس"
            setOnClickListener {
                stopService(Intent(this@MainActivity, WakeRecorderService::class.java))
                status.text = "درخواست توقف سرویس ارسال شد."
            }
        }

        val hint = TextView(this).apply {
            text = "فرمان شروع: «ضبط‌یار، شروع کن»\nفرمان توقف: «ضبط‌یار، توقف»\n\nهنگام آماده‌باش، اعلان دائمی نمایش داده می‌شود. ابتدا سرویس را از همین صفحه آغاز کن؛ سپس می‌توانی صفحه را خاموش کنی."
            textSize = 15f
            setPadding(0, 24, 0, 0)
        }

        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        root.addView(subtitle, LinearLayout.LayoutParams(-1, -2))
        root.addView(status, LinearLayout.LayoutParams(-1, -2))
        root.addView(start, LinearLayout.LayoutParams(-1, -2))
        root.addView(stop, LinearLayout.LayoutParams(-1, -2))
        root.addView(hint, LinearLayout.LayoutParams(-1, -2))

        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
    }

    private fun hasAudioPermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestNeededPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = permissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), REQUEST_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            status.text = if (hasAudioPermission()) {
                "مجوز میکروفن تأیید شد. برای آماده‌باش دکمه را بزن."
            } else {
                "بدون مجوز میکروفن امکان ضبط وجود ندارد."
            }
        }
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 701
    }
}
