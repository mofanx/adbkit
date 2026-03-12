package com.adbkit.app.ui.screens

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adbkit.app.ui.strings.LocalStrings
import com.adbkit.app.ui.viewmodel.RemoteControlViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteControlScreen(
    onMenuClick: () -> Unit,
    onRemoteConnectedChanged: (Boolean) -> Unit = {},
    viewModel: RemoteControlViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isConnected) {
        onRemoteConnectedChanged(uiState.isConnected)
    }

    if (uiState.isConnected) {
        ScreenMirrorView(viewModel = viewModel, onMenuClick = onMenuClick)
    } else {
        SettingsView(viewModel = viewModel, onMenuClick = onMenuClick)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenMirrorView(
    viewModel: RemoteControlViewModel,
    onMenuClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val strings = LocalStrings.current
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    var dragEnd by remember { mutableStateOf(Offset.Zero) }
    var showControls by remember { mutableStateOf(true) }
    var surfaceReady by remember { mutableStateOf(false) }
    var currentSurface: Surface? by remember { mutableStateOf(null) }
    var navBarExpanded by remember { mutableStateOf(false) }
    var navBarOffset by remember { mutableStateOf<Offset?>(null) }

    // Start stream when both connected and surface ready
    LaunchedEffect(uiState.isConnected, surfaceReady, currentSurface) {
        if (uiState.isConnected && surfaceReady && currentSurface != null) {
            viewModel.startH264Stream(currentSurface!!)
        }
    }

    // Stop stream when leaving this composable
    DisposableEffect(Unit) {
        onDispose { viewModel.stopStream() }
    }

    Scaffold(
        topBar = {
            if (showControls) {
                TopAppBar(
                    title = {
                        val modeLabel = when (uiState.streamMode) {
                            "h264" -> "H.264"
                            "mjpeg" -> "Screenshot"
                            else -> ""
                        }
                        val resInfo = if (uiState.videoWidth > 0 && uiState.videoHeight > 0)
                            " ${uiState.videoWidth}x${uiState.videoHeight}" else ""
                        Text(
                            "${strings.remoteControl} - ${uiState.fps}fps $modeLabel$resInfo",
                            style = MaterialTheme.typography.titleSmall
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onMenuClick) {
                            Icon(Icons.Filled.Menu, contentDescription = strings.menu)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showControls = false }) {
                            Icon(Icons.Filled.Fullscreen, contentDescription = strings.fullscreen)
                        }
                        IconButton(onClick = { viewModel.startRemoteControl() }) {
                            Icon(Icons.Filled.LinkOff, contentDescription = strings.disconnect)
                        }
                    }
                )
            }
        },
        bottomBar = {}
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
                .onSizeChanged { viewSize = it },
            contentAlignment = Alignment.Center
        ) {
            // SurfaceView always present - used for H.264 mode, transparent when unused
            // Keeping it always in composition avoids surfaceDestroyed killing the fallback stream
            AndroidView(
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                surfaceReady = true
                                currentSurface = holder.surface
                            }
                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                surfaceReady = false
                                currentSurface = null
                            }
                        })
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { offset ->
                                if (viewSize.width > 0 && viewSize.height > 0) {
                                    val xRatio = (offset.x / viewSize.width).coerceIn(0f, 1f)
                                    val yRatio = (offset.y / viewSize.height).coerceIn(0f, 1f)
                                    viewModel.sendTapAt(xRatio, yRatio)
                                }
                            },
                            onDoubleTap = {
                                showControls = !showControls
                            },
                            onLongPress = { offset ->
                                // Forward long press to remote device
                                if (viewSize.width > 0 && viewSize.height > 0) {
                                    val xRatio = (offset.x / viewSize.width).coerceIn(0f, 1f)
                                    val yRatio = (offset.y / viewSize.height).coerceIn(0f, 1f)
                                    viewModel.sendLongPressAt(xRatio, yRatio)
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                dragStart = offset
                                dragEnd = offset
                            },
                            onDragEnd = {
                                if (dragStart != Offset.Zero && dragEnd != Offset.Zero && dragStart != dragEnd) {
                                    if (viewSize.width > 0 && viewSize.height > 0) {
                                        val x1 = (dragStart.x / viewSize.width).coerceIn(0f, 1f)
                                        val y1 = (dragStart.y / viewSize.height).coerceIn(0f, 1f)
                                        val x2 = (dragEnd.x / viewSize.width).coerceIn(0f, 1f)
                                        val y2 = (dragEnd.y / viewSize.height).coerceIn(0f, 1f)
                                        viewModel.sendSwipeAt(x1, y1, x2, y2, 300)
                                    }
                                }
                                dragStart = Offset.Zero
                                dragEnd = Offset.Zero
                            },
                            onDrag = { change, _ ->
                                dragEnd = change.position
                            }
                        )
                    }
            )

            // Fallback screencap mode: overlay bitmap on top of SurfaceView
            if (uiState.streamMode == "mjpeg") {
                val bitmap = uiState.screenBitmap
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = strings.remoteControl,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        if (viewSize.width > 0 && viewSize.height > 0) {
                                            val bw = uiState.bitmapWidth.takeIf { it > 0 } ?: viewSize.width
                                            val bh = uiState.bitmapHeight.takeIf { it > 0 } ?: viewSize.height
                                            val scale = minOf(
                                                viewSize.width.toFloat() / bw,
                                                viewSize.height.toFloat() / bh
                                            )
                                            val imgW = bw * scale
                                            val imgH = bh * scale
                                            val offsetX = (viewSize.width - imgW) / 2f
                                            val offsetY = (viewSize.height - imgH) / 2f
                                            val xRatio = ((offset.x - offsetX) / imgW).coerceIn(0f, 1f)
                                            val yRatio = ((offset.y - offsetY) / imgH).coerceIn(0f, 1f)
                                            viewModel.sendTapAt(xRatio, yRatio)
                                        }
                                    },
                                    onDoubleTap = {
                                        showControls = !showControls
                                    },
                                    onLongPress = { offset ->
                                        if (viewSize.width > 0 && viewSize.height > 0) {
                                            val bw = uiState.bitmapWidth.takeIf { it > 0 } ?: viewSize.width
                                            val bh = uiState.bitmapHeight.takeIf { it > 0 } ?: viewSize.height
                                            val scale = minOf(
                                                viewSize.width.toFloat() / bw,
                                                viewSize.height.toFloat() / bh
                                            )
                                            val imgW = bw * scale
                                            val imgH = bh * scale
                                            val offsetX = (viewSize.width - imgW) / 2f
                                            val offsetY = (viewSize.height - imgH) / 2f
                                            val xRatio = ((offset.x - offsetX) / imgW).coerceIn(0f, 1f)
                                            val yRatio = ((offset.y - offsetY) / imgH).coerceIn(0f, 1f)
                                            viewModel.sendLongPressAt(xRatio, yRatio)
                                        }
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        dragStart = offset
                                        dragEnd = offset
                                    },
                                    onDragEnd = {
                                        if (dragStart != Offset.Zero && dragEnd != Offset.Zero && dragStart != dragEnd) {
                                            if (viewSize.width > 0 && viewSize.height > 0) {
                                                val bw = uiState.bitmapWidth.takeIf { it > 0 } ?: viewSize.width
                                                val bh = uiState.bitmapHeight.takeIf { it > 0 } ?: viewSize.height
                                                val scale = minOf(
                                                    viewSize.width.toFloat() / bw,
                                                    viewSize.height.toFloat() / bh
                                                )
                                                val imgW = bw * scale
                                                val imgH = bh * scale
                                                val ox = (viewSize.width - imgW) / 2f
                                                val oy = (viewSize.height - imgH) / 2f
                                                val x1 = ((dragStart.x - ox) / imgW).coerceIn(0f, 1f)
                                                val y1 = ((dragStart.y - oy) / imgH).coerceIn(0f, 1f)
                                                val x2 = ((dragEnd.x - ox) / imgW).coerceIn(0f, 1f)
                                                val y2 = ((dragEnd.y - oy) / imgH).coerceIn(0f, 1f)
                                                viewModel.sendSwipeAt(x1, y1, x2, y2, 300)
                                            }
                                        }
                                        dragStart = Offset.Zero
                                        dragEnd = Offset.Zero
                                    },
                                    onDrag = { change, _ ->
                                        dragEnd = change.position
                                    }
                                )
                            },
                        contentScale = ContentScale.Fit
                    )
                }
            }

            // Loading indicator when no stream
            if (uiState.streamMode == "none" && uiState.isConnected) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(strings.gettingScreen, color = Color.White)
                }
            }

            // FPS overlay
            if (!showControls) {
                val overlayMode = when (uiState.streamMode) {
                    "h264" -> "H.264"
                    "mjpeg" -> "Screenshot"
                    else -> ""
                }
                Text(
                    text = "${uiState.fps}fps $overlayMode | ${strings.fpsOverlay(uiState.fps).substringAfter("|").trim()}",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // Floating draggable navigation bar (always visible, including fullscreen)
            val density = LocalDensity.current
            val defaultOffset = remember(viewSize) {
                if (viewSize.width > 0) {
                    val navWidthPx = with(density) { 52.dp.toPx() }
                    Offset((viewSize.width - navWidthPx - 8f).coerceAtLeast(8f), 8f)
                } else Offset(8f, 8f)
            }
            FloatingNavBar(
                expanded = navBarExpanded,
                offset = navBarOffset ?: defaultOffset,
                containerSize = viewSize,
                onToggleExpand = { navBarExpanded = !navBarExpanded },
                onOffsetChange = { navBarOffset = it },
                onBack = { viewModel.sendKey(4) },
                onHome = { viewModel.sendKey(3) },
                onRecent = { viewModel.sendKey(187) },
                onVolumeDown = { viewModel.sendKey(25) },
                onVolumeUp = { viewModel.sendKey(24) },
                onPower = { viewModel.sendKey(26) },
                onExit = { viewModel.startRemoteControl() },
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
    }
}

@Composable
private fun FloatingNavBar(
    expanded: Boolean,
    offset: Offset,
    containerSize: IntSize,
    onToggleExpand: () -> Unit,
    onOffsetChange: (Offset) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onRecent: () -> Unit,
    onVolumeDown: () -> Unit,
    onVolumeUp: () -> Unit,
    onPower: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dragOffset by remember { mutableStateOf(offset) }

    LaunchedEffect(offset) {
        dragOffset = offset
    }

    Surface(
        modifier = modifier
            .offset { IntOffset(dragOffset.x.toInt(), dragOffset.y.toInt()) }
            .pointerInput(containerSize) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val maxX = (containerSize.width - 52.dp.toPx()).coerceAtLeast(0f)
                        val maxY = (containerSize.height - 52.dp.toPx()).coerceAtLeast(0f)
                        dragOffset = Offset(
                            (dragOffset.x + dragAmount.x).coerceIn(0f, maxX),
                            (dragOffset.y + dragAmount.y).coerceIn(0f, maxY)
                        )
                    },
                    onDragEnd = {
                        onOffsetChange(dragOffset)
                    }
                )
            },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(4.dp)
        ) {
            // Toggle button
            IconButton(onClick = onToggleExpand, modifier = Modifier.size(44.dp)) {
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            if (expanded) {
                // Navigation buttons
                IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = onHome, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Filled.Home, contentDescription = "Home", modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = onRecent, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = "Recent", modifier = Modifier.size(22.dp))
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Volume & Power
                IconButton(onClick = onVolumeUp, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Vol+", modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = onVolumeDown, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.AutoMirrored.Filled.VolumeDown, contentDescription = "Vol-", modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = onPower, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Filled.PowerSettingsNew, contentDescription = "Power", modifier = Modifier.size(22.dp))
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Exit remote control
                IconButton(onClick = onExit, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Filled.ExitToApp, contentDescription = "Exit", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsView(
    viewModel: RemoteControlViewModel,
    onMenuClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val strings = LocalStrings.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.screenRemoteControl) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = strings.menu)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Settings card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingRow(
                        label = strings.resolution,
                        value = "${uiState.maxSize}p",
                        options = listOf("480p", "720p", "1080p", "1440p", "1920p"),
                        onValueChange = { viewModel.setMaxSize(it.replace("p", "")) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    SettingRow(
                        label = strings.bitrateControl,
                        value = uiState.bitrate,
                        options = listOf("2Mbps", "4Mbps", "8Mbps", "16Mbps", "32Mbps"),
                        onValueChange = { viewModel.setBitrate(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    SettingRow(
                        label = strings.maxFps,
                        value = "${uiState.maxFps}fps",
                        options = listOf("10fps", "15fps", "20fps", "30fps", "60fps"),
                        onValueChange = { viewModel.setMaxFps(it.replace("fps", "")) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    ToggleRow(
                        label = strings.screenOffControl,
                        checked = uiState.screenOff,
                        onCheckedChange = { viewModel.setScreenOff(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    ToggleRow(
                        label = strings.compatMode,
                        checked = uiState.compatMode,
                        onCheckedChange = { viewModel.setCompatMode(it) }
                    )
                }
            }

            // Connect button
            Button(
                onClick = { viewModel.startRemoteControl() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                enabled = !uiState.isConnecting
            ) {
                if (uiState.isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(strings.connect, style = MaterialTheme.typography.bodyLarge)
            }

            // Status
            if (uiState.statusMessage.isNotEmpty()) {
                Text(
                    text = uiState.statusMessage,
                    color = if (uiState.isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun SettingRow(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onValueChange(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}
