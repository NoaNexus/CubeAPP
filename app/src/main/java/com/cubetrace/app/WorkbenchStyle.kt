// SPDX-License-Identifier: GPL-3.0-only
package com.cubetrace.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val WorkbenchTypography = Typography(
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 30.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp)
)

internal val WorkbenchShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)
)

/** Consistent, quiet relief for content panels; diagrams retain their own colors. */
@Composable
internal fun WorkbenchCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(22.dp),
    colors: CardColors = CardDefaults.cardColors(containerColor = CubeTraceColors.paper),
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    border: BorderStroke? = BorderStroke(1.dp, CubeTraceColors.line),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = if (onClick == null) modifier else modifier.clickable(onClick = onClick),
        shape = shape, colors = colors, elevation = elevation, border = border,
        content = {
            Column(Modifier.fillMaxWidth().drawWithContent {
                drawContent()
                drawLine(Color.White.copy(alpha = 0.7f), Offset(16.dp.toPx(), 1.dp.toPx()), Offset(size.width - 16.dp.toPx(), 1.dp.toPx()), 2.dp.toPx())
            }, content = content)
        }
    )
}

/** A restrained tactile rim, with an actual pressed state and native semantics. */
@Composable
internal fun WorkbenchButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(15.dp)
    Button(
        onClick = onClick, enabled = enabled, interactionSource = interaction,
        shape = shape,
        modifier = modifier.heightIn(min = 48.dp).shadow(if (pressed || !enabled) 0.dp else 3.dp, shape).drawWithContent {
            drawContent()
            if (enabled && !pressed) {
                drawLine(Color.White.copy(alpha = 0.24f), Offset(14.dp.toPx(), 3.dp.toPx()), Offset(size.width - 14.dp.toPx(), 3.dp.toPx()), 2.dp.toPx())
                drawLine(CubeTraceColors.graphite.copy(alpha = 0.32f), Offset(14.dp.toPx(), size.height - 3.dp.toPx()), Offset(size.width - 14.dp.toPx(), size.height - 3.dp.toPx()), 3.dp.toPx())
            }
        },
        border = BorderStroke(1.dp, if (enabled) CubeTraceColors.track.copy(alpha = 0.6f) else CubeTraceColors.line),
        colors = ButtonDefaults.buttonColors(containerColor = if (pressed) CubeTraceColors.graphite else CubeTraceColors.track),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        content = content
    )
}

/** The signature is a miniature cube on a grassy plinth, drawn locally. */
@Composable
internal fun WorkbenchBanner(title: String, subtitle: String, badge: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 8.dp)
            .shadow(3.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(CubeTraceColors.trackSoft, CubeTraceColors.blueWash, CubeTraceColors.paper)))
            .border(1.dp, CubeTraceColors.line, RoundedCornerShape(24.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(badge, color = CubeTraceColors.track, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(title, color = CubeTraceColors.graphite, fontSize = 24.sp, lineHeight = 31.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 5.dp))
            Text(subtitle, color = CubeTraceColors.muted, fontSize = 12.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp))
        }
        MiniatureCube(Modifier.padding(start = 10.dp).size(76.dp))
    }
}

@Composable
internal fun MiniatureCube(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun polygon(color: Color, vararg points: Pair<Float, Float>) {
            val path = Path().apply {
                moveTo(points[0].first * w, points[0].second * h)
                points.drop(1).forEach { lineTo(it.first * w, it.second * h) }
                close()
            }
            drawPath(path, color)
        }
        drawOval(CubeTraceColors.graphite.copy(alpha = 0.12f), Offset(w * 0.04f, h * 0.75f), Size(w * 0.92f, h * 0.19f))
        polygon(Color(0xFF759061), .05f to .69f, .49f to .51f, .95f to .69f, .51f to .90f)
        polygon(Color(0xFF516D49), .05f to .69f, .51f to .90f, .51f to .96f, .05f to .75f)
        polygon(Color(0xFF637849), .51f to .90f, .95f to .69f, .95f to .75f, .51f to .96f)
        polygon(Color(0xFFF4CF72), .16f to .29f, .49f to .12f, .83f to .29f, .5f to .48f)
        polygon(Color(0xFFDF9850), .16f to .29f, .5f to .48f, .5f to .79f, .16f to .60f)
        polygon(Color(0xFF638FA3), .5f to .48f, .83f to .29f, .83f to .60f, .5f to .79f)
        val ink = CubeTraceColors.paper.copy(alpha = 0.65f)
        for (i in 1..2) {
            val t = i / 3f
            drawLine(ink, Offset(w * (.16f + .34f*t), h*(.29f+.19f*t)), Offset(w*(.16f+.34f*t), h*(.60f+.19f*t)), 1.4.dp.toPx())
            drawLine(ink, Offset(w * (.5f + .33f*t), h*(.48f-.19f*t)), Offset(w*(.5f+.33f*t), h*(.79f-.19f*t)), 1.4.dp.toPx())
            drawLine(ink, Offset(w*.16f, h*(.29f+.31f*t)), Offset(w*.5f, h*(.48f+.31f*t)), 1.4.dp.toPx())
            drawLine(ink, Offset(w*.5f, h*(.48f+.31f*t)), Offset(w*.83f, h*(.29f+.31f*t)), 1.4.dp.toPx())
            drawLine(ink, Offset(w*(.16f+.33f*t), h*(.29f-.17f*t)), Offset(w*(.5f+.33f*t), h*(.48f-.19f*t)), 1.4.dp.toPx())
            drawLine(ink, Offset(w*(.49f-.33f*t), h*(.12f+.17f*t)), Offset(w*(.83f-.33f*t), h*(.29f+.19f*t)), 1.4.dp.toPx())
        }
    }
}
