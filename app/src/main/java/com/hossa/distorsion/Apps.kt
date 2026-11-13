package com.hossa.distorsion

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Cómo se dibuja el ícono dentro de la baldosa. */
enum class IconKind {
    MONO,  // capa monocromática oficial (Android 13+): se tiñe color hueso
    FG,    // capa frontal del ícono adaptativo: en escala de grises
    FULL   // ícono antiguo completo: en escala de grises y más pequeño
}

data class AppInfo(
    val label: String,
    val pkg: String,
    val activity: String,
    val icon: ImageBitmap,
    val kind: IconKind
) {
    val key: String get() = "$pkg/$activity"
}

object AppRepo {
    private const val ICON_PX = 192
    private val cache = HashMap<String, Pair<ImageBitmap, IconKind>>()

    fun load(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(intent, 0)
        return list
            .filter { it.activityInfo.packageName != ctx.packageName }
            .map { ri ->
                val pkg = ri.activityInfo.packageName
                val act = ri.activityInfo.name
                val key = "$pkg/$act"
                val (bmp, kind) = synchronized(cache) { cache[key] } ?: run {
                    val rendered = try {
                        render(ri.loadIcon(pm))
                    } catch (e: Exception) {
                        Pair(Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888), IconKind.FULL)
                    }
                    val value = Pair(rendered.first.asImageBitmap(), rendered.second)
                    synchronized(cache) { cache[key] = value }
                    value
                }
                AppInfo(ri.loadLabel(pm).toString(), pkg, act, bmp, kind)
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun render(d: Drawable): Pair<Bitmap, IconKind> {
        if (d is AdaptiveIconDrawable) {
            if (Build.VERSION.SDK_INT >= 33) {
                val mono = d.monochrome
                if (mono != null) return Pair(draw(mono), IconKind.MONO)
            }
            val fg = d.foreground
            if (fg != null) return Pair(draw(fg), IconKind.FG)
        }
        return Pair(draw(d), IconKind.FULL)
    }

    private fun draw(d: Drawable): Bitmap {
        val b = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val old = Rect(d.bounds)
        d.setBounds(0, 0, ICON_PX, ICON_PX)
        d.draw(c)
        d.bounds = old
        return b
    }
}

object Prefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("distorsion", Context.MODE_PRIVATE)

    fun favs(ctx: Context): List<String>? =
        sp(ctx).getString("favs", null)?.split("|")?.filter { it.isNotBlank() }

    fun setFavs(ctx: Context, keys: List<String>) {
        sp(ctx).edit().putString("favs", keys.joinToString("|")).apply()
    }
}

object Actions {
    fun launch(ctx: Context, app: AppInfo) {
        try {
            val i = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(app.pkg, app.activity)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            ctx.startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(ctx, "No se pudo abrir ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    fun appInfo(ctx: Context, app: AppInfo) {
        try {
            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.pkg, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (e: Exception) {
        }
    }

    fun homeSettings(ctx: Context) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            try {
                ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
            }
        }
    }

    fun openAlarms(ctx: Context) {
        try {
            ctx.startActivity(
                Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
        }
    }

    fun isDefaultLauncher(ctx: Context): Boolean {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val r = ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
        return r?.activityInfo?.packageName == ctx.packageName
    }

    private fun resolvePkg(ctx: Context, intent: Intent): String? =
        try {
            ctx.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
        } catch (e: Exception) {
            null
        }

    /** Teléfono, Mensajes, Navegador y Cámara para el dock. */
    fun dockApps(ctx: Context, apps: List<AppInfo>): List<AppInfo> {
        val slots = listOf(
            Pair(Intent(Intent.ACTION_DIAL), listOf("com.google.android.dialer", "com.android.contacts", "com.android.dialer")),
            Pair(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")), listOf("com.google.android.apps.messaging", "com.android.mms")),
            Pair(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")), listOf("com.android.chrome", "com.mi.globalbrowser", "org.mozilla.firefox")),
            Pair(Intent(MediaStore.ACTION_IMAGE_CAPTURE), listOf("com.android.camera", "com.google.android.GoogleCamera"))
        )
        val out = ArrayList<AppInfo>()
        for ((intent, fallbacks) in slots) {
            val candidates = listOfNotNull(resolvePkg(ctx, intent)) + fallbacks
            val found = candidates.firstNotNullOfOrNull { p -> apps.firstOrNull { it.pkg == p } }
            if (found != null && out.none { it.key == found.key }) out.add(found)
        }
        return out
    }

    private val defaultFavPkgs = listOf(
        "com.whatsapp", "com.instagram.android", "com.spotify.music", "com.google.android.youtube",
        "com.miui.gallery", "com.google.android.apps.photos", "com.google.android.gm",
        "com.google.android.apps.maps", "org.telegram.messenger", "com.facebook.katana",
        "com.android.deskclock", "com.miui.weather2", "com.android.settings"
    )

    fun defaultFavs(apps: List<AppInfo>, dock: List<AppInfo>): List<String> =
        defaultFavPkgs
            .mapNotNull { p -> apps.firstOrNull { it.pkg == p } }
            .filter { a -> dock.none { it.key == a.key } }
            .take(8)
            .map { it.key }
}
