package com.vibeos.tvlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

data class TvAppInfo(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap?,
    val banner: ImageBitmap?,
    val isLeanback: Boolean,
    val accentColor: Color
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VibeOsAppleTvLauncher()
        }
    }
}

@Composable
fun VibeOsAppleTvLauncher() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vibeos_prefs", Context.MODE_PRIVATE) }

    var allApps by remember { mutableStateOf<List<TvAppInfo>>(emptyList()) }
    var pinnedPackages by remember {
        mutableStateOf(
            prefs.getStringSet(
                "pinned_dock",
                setOf(
                    "com.google.android.youtube.tv",
                    "com.netflix.ninja",
                    "com.amazon.avod.thirdpartyclient",
                    "in.startv.hotstar",
                    "com.plexapp.android",
                    "com.android.tv.settings"
                )
            ) ?: emptySet()
        )
    }
    var focusedApp by remember { mutableStateOf<TvAppInfo?>(null) }
    var selectedMenuApp by remember { mutableStateOf<TvAppInfo?>(null) }
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }

    // Disable Back button from exiting the Home Launcher
    BackHandler(enabled = true) {
        if (selectedMenuApp != null) {
            selectedMenuApp = null
        }
    }

    // Live Clock Ticker
    LaunchedEffect(Unit) {
        val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val dateFmt = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
        while (true) {
            val now = Date()
            currentTime = timeFmt.format(now)
            currentDate = dateFmt.format(now)
            delay(10_000L)
        }
    }

    // Load installed TV + sideloaded apps
    LaunchedEffect(Unit) {
        allApps = loadInstalledTvApps(context)
        if (allApps.isNotEmpty() && focusedApp == null) {
            focusedApp = allApps.first()
        }
    }

    // Dynamic Apple TV Top-Shelf Ambient Mesh Colors
    val targetAccent = focusedApp?.accentColor ?: Color(0xFF2563EB)
    val animatedAccent by animateColorAsState(
        targetValue = targetAccent,
        animationSpec = tween(durationMillis = 650),
        label = "ambientAccent"
    )

    val dockApps = remember(allApps, pinnedPackages) {
        val matched = allApps.filter { it.packageName in pinnedPackages }
        if (matched.isNotEmpty()) matched.take(6) else allApps.take(6)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        animatedAccent.copy(alpha = 0.42f),
                        Color(0xFF0B0F19),
                        Color(0xFF05070B)
                    ),
                    radius = 1350f
                )
            )
            .padding(horizontal = 48.dp, vertical = 28.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Top Status Bar & Dynamic Hero Showcase (tvOS "Top Shelf")
            TopHeroShelf(
                focusedApp = focusedApp,
                currentTime = currentTime,
                currentDate = currentDate,
                onOpenSettings = { openSystemSettings(context) }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 2. Floating Apple TV Glassmorphic Dock
            GlassmorphicDock(
                dockApps = dockApps,
                onFocusApp = { focusedApp = it },
                onClickApp = { launchApp(context, it.packageName) },
                onLongClickApp = { selectedMenuApp = it }
            )

            Spacer(modifier = Modifier.height(22.dp))

            // 3. All Installed Apps & Sideloaded Apps Grid (Zero Ads, Zero Bloat)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Apps & Channels  •  Hold OK on any app to Pin/Manage",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${allApps.size} Installed",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(allApps, key = { it.packageName }) { app ->
                    AppGridTile(
                        app = app,
                        isPinned = app.packageName in pinnedPackages,
                        onFocus = { focusedApp = app },
                        onClick = { launchApp(context, app.packageName) },
                        onLongClick = { selectedMenuApp = app }
                    )
                }
            }
        }

        // 4. Long-Press D-Pad Quick Action Modal (Pin/Unpin to Dock, App Info, Uninstall)
        selectedMenuApp?.let { menuApp ->
            AppActionOverlay(
                app = menuApp,
                isPinned = menuApp.packageName in pinnedPackages,
                onTogglePin = {
                    val updated = pinnedPackages.toMutableSet()
                    if (menuApp.packageName in updated) {
                        updated.remove(menuApp.packageName)
                    } else {
                        updated.add(menuApp.packageName)
                    }
                    pinnedPackages = updated
                    prefs.edit().putStringSet("pinned_dock", updated).apply()
                    selectedMenuApp = null
                },
                onOpenAppInfo = {
                    openAppDetails(context, menuApp.packageName)
                    selectedMenuApp = null
                },
                onUninstall = {
                    uninstallPackage(context, menuApp.packageName)
                    selectedMenuApp = null
                },
                onDismiss = { selectedMenuApp = null }
            )
        }
    }
}

@Composable
fun TopHeroShelf(
    focusedApp: TvAppInfo?,
    currentTime: String,
    currentDate: String,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(118.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        // Left: Dynamic Focused App Showcase
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (focusedApp?.isLeanback == false) "SIDELOADED APP" else "VIBEOS TV  •  AD-FREE",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = focusedApp?.label ?: "VibeOS Home",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = focusedApp?.packageName ?: "Press OK to launch • Hold OK for options",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp,
                maxLines = 1
            )
        }

        // Right: Glassmorphic Clock & Quick Settings Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            var settingsFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .onFocusChanged { settingsFocused = it.isFocused }
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (settingsFocused) Color.White.copy(alpha = 0.25f)
                        else Color.White.copy(alpha = 0.08f)
                    )
                    .border(
                        width = if (settingsFocused) 2.dp else 1.dp,
                        color = if (settingsFocused) Color.White else Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(50)
                    )
                    .combinedClickable(onClick = onOpenSettings)
                    .focusable()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "⚙ TV Settings",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = currentTime,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = currentDate,
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun GlassmorphicDock(
    dockApps: List<TvAppInfo>,
    onFocusApp: (TvAppInfo) -> Unit,
    onClickApp: (TvAppInfo) -> Unit,
    onLongClickApp: (TvAppInfo) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.09f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(28.dp))
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            items(dockApps, key = { "dock_${it.packageName}" }) { app ->
                DockAppTile(
                    app = app,
                    onFocus = { onFocusApp(app) },
                    onClick = { onClickApp(app) },
                    onLongClick = { onLongClickApp(app) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DockAppTile(
    app: TvAppInfo,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.12f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "dockScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(128.dp)
            .scale(scale)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .focusable()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = 124.dp, height = 72.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(app.accentColor.copy(alpha = if (isFocused) 0.85f else 0.45f))
                .border(
                    width = if (isFocused) 2.5.dp else 1.dp,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(18.dp)
                )
        ) {
            if (app.banner != null) {
                Image(
                    bitmap = app.banner,
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (app.icon != null) {
                Image(
                    bitmap = app.icon,
                    contentDescription = app.label,
                    modifier = Modifier.size(44.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = app.label,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.75f),
            fontSize = 12.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppGridTile(
    app: TvAppInfo,
    isPinned: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.09f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "gridScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .focusable()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    if (isFocused) app.accentColor.copy(alpha = 0.72f)
                    else Color.White.copy(alpha = 0.08f)
                )
                .border(
                    width = if (isFocused) 2.5.dp else 1.dp,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(18.dp)
                )
        ) {
            if (app.banner != null) {
                Image(
                    bitmap = app.banner,
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (app.icon != null) {
                Image(
                    bitmap = app.icon,
                    contentDescription = app.label,
                    modifier = Modifier.size(46.dp)
                )
            }

            if (isPinned) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF38BDF8))
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = app.label,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.72f),
            fontSize = 12.sp,
            fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppActionOverlay(
    app: TvAppInfo,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
    ) {
        Surface(
            color = Color(0xFF141B2D),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .width(380.dp)
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = app.label,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = app.packageName,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                DialogActionButton(
                    label = if (isPinned) "Remove from Top Dock" else "Pin to Top Dock",
                    onClick = onTogglePin
                )
                DialogActionButton(
                    label = "App Info & Force Stop (Android Settings)",
                    onClick = onOpenAppInfo
                )
                DialogActionButton(
                    label = "Uninstall App",
                    onClick = onUninstall
                )
                DialogActionButton(
                    label = "Cancel",
                    onClick = onDismiss
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialogActionButton(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.1f))
            .combinedClickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            color = if (focused) Color.Black else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

fun loadInstalledTvApps(context: Context): List<TvAppInfo> {
    val pm = context.packageManager
    val selfPkg = context.packageName
    val discovered = linkedMapOf<String, Boolean>()

    // 1. Query native Android TV Leanback apps
    val leanbackIntent = Intent(Intent.ACTION_MAIN, null).apply {
        addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
    }
    pm.queryIntentActivities(leanbackIntent, 0).forEach { ri ->
        val pkg = ri.activityInfo.packageName
        if (pkg != selfPkg) discovered[pkg] = true
    }

    // 2. Query sideloaded mobile/tablet apps (so user doesn't need Sideload Launcher!)
    val standardIntent = Intent(Intent.ACTION_MAIN, null).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    pm.queryIntentActivities(standardIntent, 0).forEach { ri ->
        val pkg = ri.activityInfo.packageName
        if (pkg != selfPkg && pkg !in discovered) {
            discovered[pkg] = false
        }
    }

    val palette = listOf(
        Color(0xFF2563EB),
        Color(0xFF7C3AED),
        Color(0xFFDB2777),
        Color(0xFF059669),
        Color(0xFFD97706),
        Color(0xFF0891B2),
        Color(0xFF4F46E5),
        Color(0xFFE11D48)
    )

    return discovered.entries.mapIndexedNotNull { idx, (pkg, isLeanback) ->
        try {
            val appInfo = pm.getApplicationInfo(pkg, 0)
            val label = pm.getApplicationLabel(appInfo).toString()
            val iconDrawable = pm.getApplicationIcon(appInfo)
            val bannerDrawable = pm.getApplicationBanner(appInfo)
            TvAppInfo(
                label = label,
                packageName = pkg,
                icon = drawableToImageBitmap(iconDrawable, 128, 128),
                banner = bannerDrawable?.let { drawableToImageBitmap(it, 320, 180) },
                isLeanback = isLeanback,
                accentColor = palette[idx % palette.size]
            )
        } catch (_: Exception) {
            null
        }
    }.sortedWith(compareByDescending<TvAppInfo> { it.isLeanback }.thenBy { it.label.lowercase() })
}

fun drawableToImageBitmap(drawable: Drawable, width: Int, height: Int): ImageBitmap {
    val bmp = Bitmap.createBitmap(
        drawable.intrinsicWidth.coerceAtLeast(width),
        drawable.intrinsicHeight.coerceAtLeast(height),
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bmp)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bmp.asImageBitmap()
}

fun launchApp(context: Context, packageName: String) {
    val pm = context.packageManager
    val intent = pm.getLeanbackLaunchIntentForPackage(packageName)
        ?: pm.getLaunchIntentForPackage(packageName)
    intent?.let {
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(it)
    }
}

fun openSystemSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}

fun openAppDetails(context: Context, packageName: String) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
}

fun uninstallPackage(context: Context, packageName: String) {
    context.startActivity(
        Intent(Intent.ACTION_DELETE).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
}
