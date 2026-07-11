package com.corlang.app.update

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Metadata for the latest published build (releases/version.json in the public repo). */
@Serializable
data class ReleaseInfo(
    val versionCode: Int,
    val versionName: String,
    val notes: String = "",
    val apkUrl: String
)

/** Coarse UI state for the update flow. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: ReleaseInfo) : UpdateState
    data class Downloading(val percent: Int) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Self-updater: checks a JSON manifest in the public GitHub repo, and if a newer build exists,
 * downloads its APK and hands it to the system installer — one tap, no browsing to GitHub.
 */
class Updater(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    /** The installed app's versionCode, read from the package (no BuildConfig needed). */
    fun installedVersionCode(): Int {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt()
        else @Suppress("DEPRECATION") info.versionCode
    }

    fun installedVersionName(): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"

    /** Fetches the release manifest; null on any network/parse error. */
    suspend fun fetchLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(VERSION_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000; readTimeout = 8000
                setRequestProperty("Cache-Control", "no-cache")
            }
            conn.inputStream.bufferedReader().use { it.readText() }
        }.getOrNull()?.let { runCatching { json.decodeFromString<ReleaseInfo>(it) }.getOrNull() }
    }

    /** True if [info] is newer than what's installed. */
    fun isNewer(info: ReleaseInfo): Boolean = info.versionCode > installedVersionCode()

    /** Downloads the APK to cache, reporting 0..100 progress; returns the file or null. */
    suspend fun downloadApk(info: ReleaseInfo, onProgress: (Int) -> Unit): File? =
        withContext(Dispatchers.IO) {
            runCatching {
                val out = File(context.cacheDir, "corlang-update.apk")
                if (out.exists()) out.delete()
                val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000; readTimeout = 20000
                }
                val length = conn.contentLength.takeIf { it > 0 }
                conn.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var read: Int
                        var total = 0L
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            total += read
                            if (length != null) onProgress(((total * 100) / length).toInt().coerceIn(0, 100))
                        }
                    }
                }
                out
            }.getOrNull()
        }

    /** Launches the system package installer for the downloaded APK. */
    fun installApk(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    companion object {
        // Public raw URLs — anonymous access works once the repo is public.
        private const val BASE = "https://raw.githubusercontent.com/Al3grus/Corlang/main/releases"
        const val VERSION_URL = "$BASE/version.json"
    }
}
