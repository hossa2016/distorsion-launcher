package com.hossa.distorsion

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Spanish: Locale = Locale.forLanguageTag("es")

@Composable
fun LauncherScreen(resumeTick: Int, homePressed: Int) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var favKeys by remember { mutableStateOf(Prefs.favs(ctx)) }
    var drawerOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(resumeTick) {
        val loaded = withContext(Dispatchers.Default) { AppRepo.load(ctx) }
        apps = loaded
    }
    LaunchedEffect(homePressed) { drawerOpen = false }
    BackHandler(enabled = drawerOpen) { drawerOpen = false }

    val dock = remember(apps) { Actions.dockApps(ctx, apps) }

    // Primera vez: elegir favoritas automáticamente
    LaunchedEffect(apps) {
        if (favKeys == null && apps.isNotEmpty()) {
            val defaults = Actions.defaultFavs(apps, dock)
            Prefs.setFavs(ctx, defaults)
            favKeys = defaults
        }
    }

    val favs = remember(apps, favKeys) {
        (favKeys ?: emptyList()).mapNotNull { k -> apps.firstOrNull { it.key == k } }
    }
    val isDefault = remember(resumeTick) { Actions.isDefaultLauncher(ctx) }

    fun toggleFav(app: AppInfo) {
        val cur = favKeys ?: emptyList()
        val next = if (cur.contains(app.key)) cur - app.key else cur + app.key
        Prefs.setFavs(ctx, next)
        favKeys = next
    }

    Box(Modifier.fillMaxSize().background(Ink)) {
        Image(
            painter = painterResource(R.drawable.wallpaper),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        HomeContent(
            favs = favs,
            dock = dock,
            isDefault = isDefault,
            onOpenDrawer = { drawerOpen = true },
            onLaunch = { Actions.launch(ctx, it) },
            onRemoveFav = { toggleFav(it) }
        )

        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInVertically(initialOffsetY = { it / 3 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 3 }) + fadeOut()
        ) {
            AppDrawer(
                apps = apps,
                favKeys = favKeys ?: emptyList(),
                onToggleFav = { toggleFav(it) },
                onLaunch = {
                    Actions.launch(ctx, it)
                    drawerOpen = false
                },
                onClose = { drawerOpen = false }
            )
        }
    }
}

@Composable
private fun HomeContent(
    favs: List<AppInfo>,
    dock: List<AppInfo>,
    isDefault: Boolean,
    onOpenDrawer: () -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onRemoveFav: (AppInfo) -> Unit
) {
    val ctx = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -120f) onOpenDrawer() },
                    onVerticalDrag = { change, dy ->
                        total += dy
                        change.consume()
                    }
                )
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(56.dp))
        Clock()

        if (!isDefault) {
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Graphite.copy(alpha = 0.85f))
                    .border(1.dp, Crimson.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .clickable { Actions.homeSettings(ctx) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Crimson))
                Spacer(Modifier.width(12.dp))
                Text(
                    "Toca aquí para usar Distorsión como pantalla de inicio",
                    color = Bone, fontFamily = Inter, fontSize = 14.sp
                )
            }
        }

        Spacer(Modifier.weight(1f))

        if (favs.isEmpty()) {
            Text(
                "Desliza hacia arriba y mantén presionada una app para añadirla aquí",
                color = Muted, fontFamily = Inter, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            )
        }
        favs.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                row.forEach { app ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        AppTile(
                            app = app,
                            menu = listOf(
                                "Quitar de inicio" to { onRemoveFav(app) },
                                "Info de la app" to { Actions.appInfo(ctx, app) }
                            ),
                            onClick = { onLaunch(app) }
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        // Indicador para abrir el cajón
        Box(
            Modifier
                .fillMaxWidth()
                .clickable { onOpenDrawer() }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(width = 36.dp, height = 4.dp).clip(CircleShape).background(Bone.copy(alpha = 0.35f)))
        }

        if (dock.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(32.dp))
                    .padding(vertical = 12.dp)
            ) {
                dock.forEach { app ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        AppTile(
                            app = app,
                            showLabel = false,
                            menu = listOf("Info de la app" to { Actions.appInfo(ctx, app) }),
                            onClick = { onLaunch(app) }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun Clock() {
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000)
        }
    }
    val pattern = if (DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm"
    val time = now.format(DateTimeFormatter.ofPattern(pattern))
    val date = now.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Spanish))

    Column(
        Modifier
            .fillMaxWidth()
            .clickable { Actions.openAlarms(ctx) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            time,
            color = Bone,
            fontFamily = InterDisplayThin,
            fontWeight = FontWeight.Thin,
            fontSize = 104.sp,
            letterSpacing = (-3).sp
        )
        Text(
            date,
            color = Bone.copy(alpha = 0.7f),
            fontFamily = Inter,
            fontSize = 16.sp,
            letterSpacing = 0.5.sp
        )
        Spacer(Modifier.height(10.dp))
        Box(Modifier.size(width = 28.dp, height = 2.dp).background(Crimson))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(
    app: AppInfo,
    showLabel: Boolean = true,
    menu: List<Pair<String, () -> Unit>>,
    onClick: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuOpen = true
                }
            )
            .padding(6.dp)
    ) {
        Box {
            IconTile(app)
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                menu.forEach { (label, action) ->
                    DropdownMenuItem(
                        text = { Text(label, fontFamily = Inter, color = Bone) },
                        onClick = {
                            menuOpen = false
                            action()
                        }
                    )
                }
            }
        }
        if (showLabel) {
            Text(
                app.label,
                color = Bone.copy(alpha = 0.85f),
                fontFamily = Inter,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp).width(72.dp),
                style = TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            )
        }
    }
}

private val grayscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
private val boneTint = ColorFilter.tint(Bone)

@Composable
private fun IconTile(app: AppInfo, size: Dp = 58.dp) {
    val shape = RoundedCornerShape(size * 0.28f)
    Box(
        Modifier
            .size(size)
            .shadow(10.dp, shape)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF27272C), Color(0xFF151518), Color(0xFF0D0D0F))))
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape),
        contentAlignment = Alignment.Center
    ) {
        val (filter, iconSize) = when (app.kind) {
            IconKind.MONO -> Pair(boneTint, size)
            IconKind.FG -> Pair(grayscale, size)
            IconKind.FULL -> Pair(grayscale, size * 0.62f)
        }
        Image(
            bitmap = app.icon,
            contentDescription = app.label,
            colorFilter = filter,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun AppDrawer(
    apps: List<AppInfo>,
    favKeys: List<String>,
    onToggleFav: (AppInfo) -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        val q = query.trim()
        if (q.isEmpty()) apps else apps.filter { it.label.contains(q, ignoreCase = true) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.copy(alpha = 0.94f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp)
    ) {
        // Asa: tocar o deslizar hacia abajo para cerrar
        Box(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dy ->
                        if (dy > 12f) {
                            change.consume()
                            onClose()
                        }
                    }
                }
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(width = 40.dp, height = 4.dp).clip(CircleShape).background(Muted))
        }

        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = Bone, fontSize = 16.sp, fontFamily = Inter),
            cursorBrush = SolidColor(Crimson),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { filtered.firstOrNull()?.let(onLaunch) }),
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Graphite)
                        .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Crimson))
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text("Buscar apps", color = Muted, fontFamily = Inter, fontSize = 16.sp)
                        }
                        inner()
                    }
                }
            }
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filtered, key = { it.key }) { app ->
                Box(contentAlignment = Alignment.Center) {
                    val isFav = favKeys.contains(app.key)
                    AppTile(
                        app = app,
                        menu = listOf(
                            (if (isFav) "Quitar de inicio" else "Añadir a inicio") to { onToggleFav(app) },
                            "Info de la app" to { Actions.appInfo(ctx, app) }
                        ),
                        onClick = { onLaunch(app) }
                    )
                }
            }
        }
    }
}
