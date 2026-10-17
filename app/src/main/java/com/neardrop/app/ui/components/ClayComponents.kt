package com.neardrop.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.PathEffect
import com.neardrop.app.core.*
import com.neardrop.app.data.TransferRecord
import com.neardrop.app.ui.theme.Clay
import com.neardrop.app.ui.theme.clay

@Composable
fun ClayCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (onClick != null && isPressed) 0.985f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "cardScale"
    )
    val m = modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clay(color)
        .let {
            if (onClick != null) it.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            else it
        }
    Column(m.padding(18.dp), content = content)
}

@Composable
fun ClayButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    color: Color = Clay.Lavender
) {
    val isDark = isDark()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "buttonScale"
    )

    val (buttonBg, buttonText, iconTint) = if (isDark) {
        when (color) {
            Clay.Lavender -> Triple(Color(0xFF5B45C9), Color.White, Color.White)
            Clay.Mint -> Triple(Clay.DarkMint, Clay.DarkMintText, Clay.DarkMintAccent)
            Clay.Sky -> Triple(Clay.DarkSky, Clay.DarkSkyText, Clay.DarkSkyAccent)
            Clay.Peach -> Triple(Clay.DarkPeach, Clay.DarkPeachText, Clay.DarkPeachAccent)
            else -> Triple(Color(0xFF2C2740), Color.White, Color.White)
        }
    } else {
        Triple(color, Clay.Charcoal, Clay.Charcoal)
    }

    Row(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .alpha(if (enabled) 1f else 0.45f)
            .clay(buttonBg, RoundedCornerShape(18.dp), if (isPressed) 2.dp else 4.dp)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick)
            .heightIn(min = 46.dp)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = buttonText, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ClayIconButton(
    icon: ImageVector,
    desc: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Clay.Sky
) {
    val isDark = isDark()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "iconScale"
    )

    val (bg, iconTint) = if (isDark) {
        when (color) {
            Clay.Sky -> Pair(Color(0xFF242036), Color(0xFFC4D5FF))
            Clay.Peach -> Pair(Clay.DarkPeach, Clay.DarkPeachText)
            Clay.Mint -> Pair(Clay.DarkMint, Clay.DarkMintText)
            else -> Pair(Color(0xFF242036), Color(0xFFEDE8FF))
        }
    } else {
        Pair(color, Clay.Charcoal)
    }

    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .size(44.dp)
            .clay(bg, CircleShape, if (isPressed) 2.dp else 4.dp)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, tint = iconTint, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun <T> ClaySegmentedControl(
    items: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isDark()
    val containerBg = if (isDark) Color(0xFF12101B) else Color(0xFFECE7DE)
    val activePillBg = if (isDark) Color(0xFF5B45C9) else Color.White
    val activeTextColor = if (isDark) Color.White else Clay.Charcoal
    val inactiveTextColor = if (isDark) Color(0xFF8E88A6) else Clay.Charcoal.copy(alpha = 0.65f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(containerBg)
            .border(
                1.dp,
                if (isDark) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.05f),
                RoundedCornerShape(16.dp)
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .then(
                        if (isSelected) {
                            Modifier.clay(activePillBg, RoundedCornerShape(12.dp), 2.dp)
                        } else {
                            Modifier.clickable { onSelect(value) }
                        }
                    )
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) activeTextColor else inactiveTextColor,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun ClayChip(text: String, selected: Boolean = false, onClick: (() -> Unit)? = null, color: Color = Clay.Mint) {
    val isDark = isDark()
    val activeBg = if (isDark) {
        when (color) {
            Clay.Mint -> Clay.DarkMint
            Clay.Lavender -> Color(0xFF5B45C9)
            Clay.Sky -> Clay.DarkSky
            Clay.Peach -> Clay.DarkPeach
            else -> Color(0xFF352F4E)
        }
    } else color

    val activeText = if (isDark) {
        when (color) {
            Clay.Mint -> Clay.DarkMintText
            Clay.Lavender -> Color.White
            Clay.Sky -> Clay.DarkSkyText
            Clay.Peach -> Clay.DarkPeachText
            else -> Color.White
        }
    } else Clay.Charcoal

    val unselectedBg = if (isDark) Color(0xFF161422) else Color(0xFFEBE6DC)
    val unselectedText = if (isDark) Color(0xFF908AAB) else Clay.Charcoal.copy(alpha = 0.7f)

    val base = if (selected) activeBg else unselectedBg
    val textC = if (selected) activeText else unselectedText

    val m = Modifier
        .clay(base, RoundedCornerShape(50), if (selected) 2.dp else 1.dp)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }

    Text(
        text = text,
        modifier = m.padding(horizontal = 14.dp, vertical = 7.dp),
        style = MaterialTheme.typography.labelMedium,
        color = textC,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
    )
}

@Composable
fun ClaySwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val isDark = isDark()
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = if (isDark) Color(0xFF5B45C9) else Clay.LavenderDeep,
            checkedThumbColor = Color.White,
            uncheckedTrackColor = if (isDark) Color(0xFF1F1C2E) else Color(0xFFD4CEC3),
            uncheckedThumbColor = if (isDark) Color(0xFF867F9E) else Color.White
        )
    )
}

@Composable
fun ClayTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            ClayIconButton(Icons.Default.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(12.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}

@Composable
fun ClayDialog(title: String, onDismiss: () -> Unit, confirm: @Composable () -> Unit, dismiss: @Composable (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = confirm,
        dismissButton = dismiss,
        shape = RoundedCornerShape(26.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Column(content = content) }
    )
}

@Composable
fun ClayEmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val isDark = isDark()
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(76.dp)
                .clay(if (isDark) Clay.DarkLavender else Clay.Lavender, CircleShape, 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                null,
                Modifier.size(36.dp),
                tint = if (isDark) Clay.DarkLavenderAccent else Clay.Charcoal
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
        if (action != null) { Spacer(Modifier.height(16.dp)); action() }
    }
}

@Composable
fun ClayLoadingIndicator(modifier: Modifier = Modifier) {
    val isDark = isDark()
    val t = rememberInfiniteTransition(label = "load")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            val a by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600, delayMillis = i * 150), RepeatMode.Reverse), label = "dot$i")
            Box(Modifier.size(10.dp).alpha(a).clay(if (isDark) Clay.DarkLavenderAccent else Clay.LavenderDeep, CircleShape, 1.dp))
        }
    }
}

@Composable
fun FileTransferLoadingAnimation(
    modifier: Modifier = Modifier,
    title: String = "NearDrop",
    subtitle: String = "Initializing secure peer engine…"
) {
    val isDark = isDark()
    val transition = rememberInfiniteTransition(label = "fileTransferLoad")

    val flightProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flight"
    )

    val senderPulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "senderPulse"
    )

    val statusIndex by transition.animateValue(
        initialValue = 0,
        targetValue = 3,
        typeConverter = Int.VectorConverter,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "statusIndex"
    )

    val statuses = listOf(
        "Preparing secure session…",
        "Configuring Wi-Fi & Bluetooth transport…",
        "Checking integrity verifier…",
        "Ready for fast offline transfers"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val startX = size.width * 0.18f
                val endX = size.width * 0.82f
                val baseY = size.height * 0.65f
                val arcPeakHeight = size.height * 0.45f

                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(startX, baseY)
                    quadraticTo(
                        (startX + endX) / 2f,
                        baseY - arcPeakHeight * 1.5f,
                        endX,
                        baseY
                    )
                }

                drawPath(
                    path = path,
                    color = if (isDark) Color(0xFF5B45C9).copy(alpha = 0.35f) else Color(0xFFC9B8FF).copy(alpha = 0.6f),
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                )

                val t = flightProgress
                val trailCount = 5
                for (i in 1..trailCount) {
                    val trailT = (t - i * 0.04f)
                    if (trailT in 0.01f..0.99f) {
                        val px = startX + trailT * (endX - startX)
                        val py = baseY - 4 * arcPeakHeight * trailT * (1f - trailT)
                        val alpha = (1f - (i.toFloat() / (trailCount + 1))) * 0.7f
                        val radius = (4f - i * 0.5f).coerceAtLeast(1.5f).dp.toPx()
                        drawCircle(
                            color = (if (isDark) Color(0xFFA78BFA) else Color(0xFF6C5CE7)).copy(alpha = alpha),
                            radius = radius,
                            center = Offset(px, py)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, bottom = 10.dp)
                    .size(52.dp)
                    .graphicsLayer {
                        scaleX = if (flightProgress < 0.25f) senderPulse else 1f
                        scaleY = if (flightProgress < 0.25f) senderPulse else 1f
                    }
                    .clay(
                        if (isDark) Clay.DarkLavender else Clay.Lavender,
                        CircleShape,
                        4.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    tint = if (isDark) Clay.DarkLavenderAccent else Clay.Charcoal,
                    modifier = Modifier.size(24.dp)
                )
            }

            val isReceiving = flightProgress > 0.85f
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 10.dp)
                    .size(52.dp)
                    .graphicsLayer {
                        scaleX = if (isReceiving) 1.08f else 1f
                        scaleY = if (isReceiving) 1.08f else 1f
                    }
                    .clay(
                        if (isReceiving) (if (isDark) Clay.DarkMint else Clay.Mint)
                        else (if (isDark) Clay.DarkSky else Clay.Sky),
                        CircleShape,
                        4.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isReceiving) Icons.Default.Check else Icons.Default.Computer,
                    contentDescription = null,
                    tint = if (isDark) (if (isReceiving) Clay.DarkMintAccent else Clay.DarkSkyAccent) else Clay.Charcoal,
                    modifier = Modifier.size(24.dp)
                )
            }

            val t = flightProgress
            val startPercent = 0.18f
            val endPercent = 0.82f
            val currentXFraction = startPercent + t * (endPercent - startPercent)
            val currentYFraction = 0.65f - 4 * 0.45f * t * (1f - t)
            val tiltAngle = (t - 0.5f) * 36f

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val xPos = maxWidth * currentXFraction - 16.dp
                val yPos = maxHeight * currentYFraction - 16.dp

                Box(
                    modifier = Modifier
                        .offset(x = xPos, y = yPos)
                        .graphicsLayer {
                            rotationZ = tiltAngle
                        }
                        .clay(
                            if (isDark) Color(0xFF2C2642) else Color.White,
                            RoundedCornerShape(12.dp),
                            3.dp
                        )
                        .padding(7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when ((t * 4).toInt() % 4) {
                            0 -> Icons.Default.InsertDriveFile
                            1 -> Icons.Default.Image
                            2 -> Icons.Default.MusicNote
                            else -> Icons.Default.Folder
                        },
                        contentDescription = null,
                        tint = if (isDark) Color(0xFFA78BFA) else Color(0xFF5B45C9),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = (-0.5).sp
        )

        Spacer(Modifier.height(4.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clay(if (isDark) Clay.DarkMintAccent else Clay.Mint, CircleShape, 1.dp)
            )
            Text(
                text = "P2P Wireless File Sharing",
                style = MaterialTheme.typography.labelMedium,
                color = if (isDark) Color(0xFF48D5A3) else Color(0xFF1F7A5C),
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(16.dp))

        val shimmerProgress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmer"
        )

        Box(
            modifier = Modifier
                .width(180.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(if (isDark) Color(0xFF1E1B2E) else Color(0xFFE4DFD6))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.45f)
                    .offset(x = 180.dp * (shimmerProgress - 0.2f))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                if (isDark) Color(0xFFA78BFA) else Color(0xFF6C5CE7),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = statuses[statusIndex.coerceIn(0, statuses.size - 1)],
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ClayRadarScanner(
    modifier: Modifier = Modifier,
    title: String = "Scanning for nearby devices…",
    subtitle: String = "Keep NearDrop open on both devices",
    deviceCount: Int = 0
) {
    val isDark = isDark()
    val transition = rememberInfiniteTransition(label = "radar")
    val ring1 by transition.animateFloat(0.2f, 1f, infiniteRepeatable(tween(2400, 0, LinearEasing)), label = "r1")
    val ring2 by transition.animateFloat(0.2f, 1f, infiniteRepeatable(tween(2400, 800, LinearEasing)), label = "r2")
    val ring3 by transition.animateFloat(0.2f, 1f, infiniteRepeatable(tween(2400, 1600, LinearEasing)), label = "r3")
    val pulseCenter by transition.animateFloat(0.92f, 1.08f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val sweepAngle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "sweep")

    val ringColor = if (isDark) Color(0xFF6B5BB8) else Color(0xFFC9B8FF)
    val sweepColor = if (isDark) Color(0xFF38B88B) else Color(0xFFB5EAD7)

    Column(modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val maxRadius = size.minDimension / 2

                listOf(ring1, ring2, ring3).forEach { progress ->
                    val r = maxRadius * progress
                    val alpha = (1f - progress).coerceIn(0f, 0.5f)
                    drawCircle(
                        color = ringColor.copy(alpha = alpha),
                        radius = r,
                        center = center,
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                }

                rotate(sweepAngle, pivot = center) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            0f to Color.Transparent,
                            0.75f to Color.Transparent,
                            1f to sweepColor.copy(alpha = if (isDark) 0.25f else 0.35f)
                        ),
                        startAngle = 0f,
                        sweepAngle = 90f,
                        useCenter = true,
                        topLeft = Offset(center.x - maxRadius, center.y - maxRadius),
                        size = androidx.compose.ui.geometry.Size(maxRadius * 2, maxRadius * 2)
                    )
                }
            }

            Box(
                Modifier
                    .size(56.dp)
                    .graphicsLayer {
                        scaleX = pulseCenter
                        scaleY = pulseCenter
                    }
                    .clay(if (isDark) Clay.DarkLavender else Clay.Lavender, CircleShape, 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Radar,
                    contentDescription = null,
                    tint = if (isDark) Clay.DarkLavenderAccent else Clay.Charcoal,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            if (deviceCount > 0) "$deviceCount compatible device(s) found" else title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

fun deviceIcon(t: DeviceType) = when (t) { DeviceType.PHONE -> Icons.Default.PhoneAndroid; DeviceType.TABLET -> Icons.Default.TabletAndroid; DeviceType.PC -> Icons.Default.Computer; else -> Icons.Default.Devices }

@Composable
fun ClayDeviceCard(d: DiscoveredDevice, trusted: Boolean, onSend: () -> Unit) {
    val isDark = isDark()
    ClayCard(Modifier.fillMaxWidth(), color = if (isDark) Color(0xFF1E1B2E) else Clay.Sky.copy(alpha = 0.5f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clay(if (isDark) Clay.DarkSky else Clay.Cream, CircleShape, 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(deviceIcon(d.type), null, tint = if (isDark) Clay.DarkSkyAccent else Clay.Charcoal, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(d.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (d.via == Via.WIFI_DIRECT) "Wi-Fi Direct" else "Local network") + " · " + (if (trusted) "Trusted" else "Not trusted yet"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            ClayButton("Send", onSend, icon = Icons.Default.Send, color = Clay.Mint)
        }
    }
}

private fun statusText(s: TransferStatus) = when (s) {
    TransferStatus.CONNECTING -> "Connecting securely…"; TransferStatus.AWAITING_APPROVAL -> "Waiting for approval…"
    TransferStatus.TRANSFERRING -> "Transferring"; TransferStatus.VERIFYING -> "Verifying checksum…"
    TransferStatus.COMPLETED -> "Completed"; TransferStatus.FAILED -> "Failed"; TransferStatus.INTERRUPTED -> "Interrupted"
    TransferStatus.CANCELLED -> "Cancelled"; TransferStatus.REJECTED -> "Declined"
}

@Composable
fun ClayProgressCard(p: TransferProgress, onClick: () -> Unit, onCancel: (() -> Unit)? = null) {
    val isDark = isDark()
    val animatedFraction by animateFloatAsState(
        targetValue = p.fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "fraction"
    )

    val cardBg = if (isDark) Color(0xFF221E36) else Clay.Lavender.copy(alpha = 0.7f)
    val textColor = MaterialTheme.colorScheme.onSurface
    val rawName = p.currentFile.substringAfterLast('/').substringAfterLast('\\')
    val fileName = if (rawName.isNotBlank()) rawName else if (p.fileCount > 1) "${p.fileCount} files" else "File"

    ClayCard(Modifier.fillMaxWidth(), color = cardBg, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clay(if (isDark) Color(0xFF2E274A) else Color.White.copy(alpha = 0.8f), CircleShape, 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when {
                        fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) || fileName.endsWith(".png", true) || fileName.endsWith(".webp", true) -> Icons.Default.Image
                        fileName.endsWith(".mp4", true) || fileName.endsWith(".mkv", true) || fileName.endsWith(".mov", true) -> Icons.Default.Videocam
                        fileName.endsWith(".mp3", true) || fileName.endsWith(".wav", true) -> Icons.Default.MusicNote
                        else -> Icons.Default.InsertDriveFile
                    },
                    contentDescription = null,
                    tint = if (isDark) Color.White else Clay.Charcoal,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(fileName, fontWeight = FontWeight.Bold, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (p.direction == Direction.SEND) "Sending to " else "Receiving from ") + p.peerName + " · " + statusText(p.status),
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.85f)
                )
            }
            if (onCancel != null && !p.status.terminal) IconButton(onCancel) { Icon(Icons.Default.Close, "Cancel", tint = textColor) }
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { animatedFraction },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)),
            color = if (isDark) Color(0xFF9D8BFF) else Clay.LavenderDeep,
            trackColor = if (isDark) Color(0xFF141220) else Color.White.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(6.dp))
        val pct = (p.fraction * 100).toInt()
        Text(
            "${Fmt.bytes(p.bytesDone)} of ${Fmt.bytes(p.totalBytes)} ($pct%)",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = textColor.copy(alpha = 0.9f)
        )
    }
}

@Composable
fun ClayTransferItem(r: TransferRecord, onClick: () -> Unit) {
    val isDark = isDark()
    val ok = r.status == "COMPLETED"
    val firstFile = r.fileNames.lineSequence().firstOrNull()?.trim().orEmpty()
    val rawName = firstFile.substringAfterLast('/').substringAfterLast('\\')
    val displayName = when {
        rawName.isNotBlank() && r.fileCount > 1 -> "$rawName (+${r.fileCount - 1} more)"
        rawName.isNotBlank() -> rawName
        else -> "${r.fileCount} file(s)"
    }

    val fileIcon = when {
        firstFile.endsWith(".jpg", true) || firstFile.endsWith(".jpeg", true) || firstFile.endsWith(".png", true) || firstFile.endsWith(".webp", true) -> Icons.Default.Image
        firstFile.endsWith(".mp4", true) || firstFile.endsWith(".mkv", true) || firstFile.endsWith(".mov", true) -> Icons.Default.Videocam
        firstFile.endsWith(".mp3", true) || firstFile.endsWith(".wav", true) || firstFile.endsWith(".m4a", true) -> Icons.Default.MusicNote
        firstFile.endsWith(".pdf", true) || firstFile.endsWith(".doc", true) || firstFile.endsWith(".docx", true) || firstFile.endsWith(".txt", true) -> Icons.Default.Description
        else -> Icons.Default.InsertDriveFile
    }

    ClayCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            Box(
                Modifier
                    .size(46.dp)
                    .clay(
                        if (ok) (if (isDark) Clay.DarkMint else Clay.Mint.copy(alpha = 0.45f))
                        else (if (isDark) Clay.DarkPeach else Clay.Peach.copy(alpha = 0.45f)),
                        CircleShape,
                        2.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    fileIcon,
                    contentDescription = null,
                    tint = if (ok) (if (isDark) Clay.DarkMintAccent else Color(0xFF1B5E20))
                           else (if (isDark) Clay.DarkPeachAccent else Color(0xFFB71C1C)),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = (if (r.direction == "SEND") "Sent to " else "Received from ") + r.peerName + " · " + Fmt.bytes(r.totalBytes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
            }
            Spacer(Modifier.width(8.dp))
            ClayChip(
                if (ok) "Done" else "Interrupted",
                selected = true,
                color = if (ok) Clay.Mint else Clay.Peach
            )
        }
    }
}

@Composable
fun ClayBottomNavigation(
    items: List<Triple<String, ImageVector, String>>,
    current: String?,
    onSelect: (String) -> Unit
) {
    val isDark = isDark()
    val barBg = if (isDark) Color(0xFF161422) else MaterialTheme.colorScheme.surface
    val activePillBg = if (isDark) Color(0xFF5B45C9) else Clay.Lavender
    val activeTint = if (isDark) Color.White else Clay.Charcoal
    val inactiveTint = if (isDark) Color(0xFF827B9B) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clay(barBg, RoundedCornerShape(32.dp), 8.dp)
                .padding(6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (route, icon, label) ->
                val sel = route == current
                val interactionSource = remember { MutableInteractionSource() }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(interactionSource = interactionSource, indication = null) { onSelect(route) }
                        .then(
                            if (sel) Modifier.clay(activePillBg, RoundedCornerShape(24.dp), 3.dp)
                            else Modifier
                        )
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            icon,
                            contentDescription = label,
                            tint = if (sel) activeTint else inactiveTint,
                            modifier = Modifier.size(20.dp)
                        )
                        if (sel) {
                            Text(
                                label,
                                color = activeTint,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun isDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.4f
