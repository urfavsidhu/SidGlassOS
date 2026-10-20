package com.sidglassos.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object GlassColors {
    val Navy = Color(0xFF0B1220)
    val Midnight = Color(0xFF182A4A)
    val Violet = Color(0xFF4B5FD1)
    val Ice = Color(0xFFDDE5F4)
}

@Composable
fun GlassTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFB8C4FF), onPrimary = GlassColors.Navy,
            secondary = Color(0xFF9EDCFF), background = GlassColors.Navy, onBackground = GlassColors.Ice,
            surface = GlassColors.Midnight, onSurface = GlassColors.Ice,
            surfaceVariant = Color(0xFF263650), onSurfaceVariant = Color(0xFFB9C7DD),
            outline = Color(0xFF7E8BA6),
        ),
        typography = Typography(
            headlineLarge = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light, fontSize = 34.sp),
            headlineSmall = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Medium, fontSize = 23.sp),
            bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
            labelSmall = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, letterSpacing = 1.sp),
        ),
    ) {
        // Transparent layouts inherit a readable foreground without an opaque Surface.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            content()
        }
    }
}

/** A graded translucent surface. This intentionally does not blur foreground content. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    opacity: Float = .16f,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    val safeOpacity = opacity.takeIf { it.isFinite() }?.coerceIn(.05f, .9f) ?: .16f
    Column(
        modifier.clip(shape)
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = safeOpacity), Color(0xFF899FE8).copy(alpha = safeOpacity * .55f), GlassColors.Navy.copy(alpha = .4f))))
            .border(BorderStroke(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = .46f), Color.White.copy(alpha = .04f), Color.White.copy(alpha = .18f)))), shape)
            .padding(20.dp),
        content = content,
    )
}

@Composable
fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(18.dp)) {
        Text(text)
    }
}

@Composable
fun GlassSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit, onValueChangeFinished: (() -> Unit)? = null) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(modifier = Modifier.semantics { contentDescription = label },
            value = value.takeIf { it.isFinite() }?.coerceIn(range.start, range.endInclusive) ?: range.start,
            onValueChange = onValueChange, valueRange = range, onValueChangeFinished = onValueChangeFinished)
    }
}
