package com.lk21tv.app

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object AppUpdateManager {

    private const val CONFIG_URL =
        "https://raw.githubusercontent.com/syaqirselamanya/lk21-tv-app/main/config.json"
    private const val PREFS_NAME = "citeraloka_prefs"
    private const val KEY_BASE_URL = "base_url"

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun checkUpdate(
        activity: Activity,
        isManual: Boolean = false,
        onDomainUpdated: ((String) -> Unit)? = null
    ) {
        executor.execute {
            try {
                val conn = (URL(CONFIG_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "CiteraLoka-OTA")
                    setRequestProperty("Cache-Control", "no-cache")
                }

                if (conn.responseCode != 200) {
                    if (isManual) {
                        mainHandler.post {
                            Toast.makeText(activity, "Gagal menyemak pelayan kemas kini.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return@execute
                }

                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(jsonStr)

                // 1. Penyelarasan Domain Dinamik di Latar Belakang (Remote Domain Sync)
                val remoteDomain = obj.optString("activeDomain", "").trimEnd('/')
                if (remoteDomain.startsWith("http")) {
                    val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val currentDomain = prefs.getString(KEY_BASE_URL, "") ?: ""
                    if (currentDomain.isNotBlank() && currentDomain != remoteDomain) {
                        prefs.edit().putString(KEY_BASE_URL, remoteDomain).apply()
                        mainHandler.post {
                            onDomainUpdated?.invoke(remoteDomain)
                        }
                    }
                }

                // 2. Semakan Versi Aplikasi
                val remoteVersionCode = obj.optInt("versionCode", 0)
                val remoteVersionName = obj.optString("versionName", "Terkini")
                val cleanRemoteVer = remoteVersionName.replace(Regex("[^a-zA-Z0-9._-]"), "")
                val defaultApkName = if (cleanRemoteVer.isNotBlank()) "citeraloka${cleanRemoteVer}.apk" else "CiteraLoka.apk"
                val apkUrl = obj.optString(
                    "apkUrl",
                    "https://github.com/syaqirselamanya/lk21-tv-app/releases/download/latest/$defaultApkName"
                )
                val changelog = obj.optString("changelog", "Kemas kini prestasi dan kestabilan penstriman.")

                val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    activity.packageManager.getPackageInfo(activity.packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    activity.packageManager.getPackageInfo(activity.packageName, 0)
                }

                val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode
                }

                mainHandler.post {
                    if (remoteVersionCode > currentVersionCode) {
                        showUpdateDialog(activity, remoteVersionName, apkUrl, changelog)
                    } else if (isManual) {
                        Toast.makeText(
                            activity,
                            "CiteraLoka v${packageInfo.versionName} adalah versi terkini.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                if (isManual) {
                    mainHandler.post {
                        Toast.makeText(activity, "Ralat semakan: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showUpdateDialog(
        activity: Activity,
        newVersionName: String,
        apkUrl: String,
        changelog: String
    ) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = createRoundedBg("#071F29", "#00D4B2", 2, 10)
            setPadding(dp(activity, 24), dp(activity, 20), dp(activity, 24), dp(activity, 20))
            layoutParams = ViewGroup.LayoutParams(dp(activity, 520), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val cleanVer = newVersionName.replace(Regex("[^a-zA-Z0-9._-]"), "")
        val displayApkName = if (cleanVer.isNotBlank()) "citeraloka${cleanVer}.apk" else "CiteraLoka.apk"

        val title = TextView(activity).apply {
            text = "KEMAS KINI CITERALOKA ($displayApkName)"
            setTextColor(Color.parseColor("#F0FDFA"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.04f
        }

        val desc = TextView(activity).apply {
            text = changelog
            setTextColor(Color.parseColor("#99F6E4"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(activity, 8), 0, dp(activity, 16))
        }

        val btnRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val dismissBtn = createBoxButton(activity, "KEMUDIAN", isPrimary = false) {
            dialog.dismiss()
        }

        val updateBtn = createBoxButton(activity, "KEMAS KINI SEKARANG", isPrimary = true) {
            dialog.dismiss()
            startDownloadAndInstall(activity, apkUrl, newVersionName)
        }

        btnRow.addView(dismissBtn)
        btnRow.addView(updateBtn)

        root.addView(title)
        root.addView(desc)
        root.addView(btnRow)

        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()

        updateBtn.requestFocus()
    }

    private fun startDownloadAndInstall(activity: Activity, apkDownloadUrl: String, versionName: String = "") {
        val cleanVer = versionName.replace(Regex("[^a-zA-Z0-9._-]"), "")
        val apkFileName = if (cleanVer.isNotBlank()) "citeraloka${cleanVer}.apk" else "CiteraLoka.apk"

        val progressDialog = Dialog(activity)
        progressDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        progressDialog.setCancelable(false)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = createRoundedBg("#071F29", "#00D4B2", 2, 10)
            setPadding(dp(activity, 28), dp(activity, 24), dp(activity, 28), dp(activity, 24))
            layoutParams = ViewGroup.LayoutParams(dp(activity, 460), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val title = TextView(activity).apply {
            text = "MEMUAT TURUN $apkFileName..."
            setTextColor(Color.parseColor("#F0FDFA"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.04f
            setPadding(0, 0, 0, dp(activity, 14))
        }

        val progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            progress = 0
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(activity, 8)
            )
        }

        val percentText = TextView(activity).apply {
            text = "0%"
            setTextColor(Color.parseColor("#00D4B2"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(activity, 10), 0, 0)
        }

        root.addView(title)
        root.addView(progressBar)
        root.addView(percentText)

        progressDialog.setContentView(root)
        progressDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        progressDialog.show()

        executor.execute {
            try {
                val conn = (URL(apkDownloadUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "CiteraLoka-OTA")
                }

                // Mengendalikan redirect jika pautan GitHub menghala ke CDN
                var redirectConn = conn
                var status = conn.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
                    val newUrl = conn.getHeaderField("Location")
                    redirectConn = (URL(newUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 30000
                        setRequestProperty("User-Agent", "CiteraLoka-OTA")
                    }
                }

                val totalSize = redirectConn.contentLength
                // Padam sebarang fail APK lama dalam cache sebelum simpan versi baharu
                activity.cacheDir.listFiles()?.forEach { cached ->
                    if (cached.name.endsWith(".apk", ignoreCase = true)) {
                        cached.delete()
                    }
                }
                val apkFile = File(activity.cacheDir, apkFileName)
                if (apkFile.exists()) apkFile.delete()

                redirectConn.inputStream.use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var downloaded = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            if (totalSize > 0) {
                                val progress = ((downloaded * 100L) / totalSize).toInt()
                                mainHandler.post {
                                    progressBar.progress = progress
                                    percentText.text = "$progress%"
                                }
                            }
                        }
                    }
                }

                mainHandler.post {
                    progressDialog.dismiss()
                    triggerApkInstall(activity, apkFile)
                }
            } catch (e: Exception) {
                mainHandler.post {
                    progressDialog.dismiss()
                    Toast.makeText(activity, "Gagal memuat turun: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun triggerApkInstall(context: Context, apkFile: File) {
        try {
            val contentUri = Uri.parse("content://${context.packageName}.provider/${apkFile.name}")
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Ralat pemasangan: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun createBoxButton(
        context: Context,
        label: String,
        isPrimary: Boolean,
        onClick: () -> Unit
    ): Button {
        val normalBg = if (isPrimary) "#0D9488" else "#0B2B38"
        val normalBorder = if (isPrimary) "#00D4B2" else "#134556"
        return Button(context).apply {
            text = label
            isAllCaps = true
            setTextColor(Color.parseColor("#F0FDFA"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.05f
            isFocusable = true
            isFocusableInTouchMode = false
            background = createRoundedBg(normalBg, normalBorder, 1, 6)
            setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dp(context, 8)
            }
            setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    (v as Button).setTextColor(Color.parseColor("#031117"))
                    v.background = createRoundedBg("#00D4B2", "#FFFFFF", 2, 6)
                    v.scaleX = 1.05f
                    v.scaleY = 1.05f
                } else {
                    (v as Button).setTextColor(Color.parseColor("#F0FDFA"))
                    v.background = createRoundedBg(normalBg, normalBorder, 1, 6)
                    v.scaleX = 1.0f
                    v.scaleY = 1.0f
                }
            }
            setOnClickListener { onClick() }
        }
    }

    private fun createRoundedBg(fillHex: String, strokeHex: String, strokeWidthDp: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(fillHex))
            cornerRadius = (radiusDp * 2).toFloat()
            if (strokeWidthDp > 0) {
                setStroke(strokeWidthDp * 2, Color.parseColor(strokeHex))
            }
        }
    }

    private fun dp(context: Context, value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
