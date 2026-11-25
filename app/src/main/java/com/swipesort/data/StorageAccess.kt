package com.swipesort.data

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Everything about storage permissions in one place.
 *
 * ── The "All files access" flow ─────────────────────────────────────────────────────────────
 * Android 11+ (API 30+): full access to shared storage is the special permission
 *   MANAGE_EXTERNAL_STORAGE. It is NOT a runtime pop-up: the app opens a Settings page
 *   (ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION) where the user flips "Allow access to manage
 *   all files". When they come back, we re-check Environment.isExternalStorageManager().
 * Android 13+ (API 33+): we additionally ask for READ_MEDIA_IMAGES / VIDEO / AUDIO with the normal
 *   pop-up, so media access keeps working even if "All files access" is later revoked.
 * Android 10 (API 29): READ/WRITE_EXTERNAL_STORAGE + requestLegacyExternalStorage="true" in the
 *   manifest gives the old full-file access.
 * Android 8–9 (API 26–28): READ/WRITE_EXTERNAL_STORAGE runtime permissions are full access.
 */
object StorageAccess {

    /** True when SwipeSort can see, move and delete every file the user can. */
    fun hasFullAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else granted(context, Manifest.permission.READ_EXTERNAL_STORAGE) &&
            granted(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    /** Whether full access needs the special Settings page (vs. only runtime pop-ups). */
    val usesAllFilesAccessSettings: Boolean get() = Build.VERSION.SDK_INT >= 30

    /** The normal pop-up permissions to ask for on this Android version. */
    fun runtimePermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 33 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
        )
        Build.VERSION.SDK_INT >= 30 -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    fun missingRuntimePermissions(context: Context): Array<String> =
        runtimePermissions().filterNot { granted(context, it) }.toTypedArray()

    /**
     * Opens the "All files access" switch for SwipeSort. Tries the page for this app first, then the
     * general list, then the app-info page. Returns false if none could be opened.
     * (We don't need a result: the UI re-checks [hasFullAccess] when the user comes back.)
     */
    fun openAllFilesAccessSettings(context: Context): Boolean =
        tryStart(context, Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))) ||
            tryStart(context, Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) ||
            tryStart(context, appSettingsIntent(context))

    /** App info page, for when a runtime permission was denied with "Don't ask again". */
    fun appSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    fun tryStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: ActivityNotFoundException) { false }

    private fun granted(context: Context, p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
}
