package com.aimultiviewer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.aimultiviewer.domain.model.DocFormat

// ---- 브랜드 팔레트: 딥 틸 + 앰버 포인트 ----
private val Teal900 = Color(0xFF06414D)
private val Teal700 = Color(0xFF0B7285)
private val Teal400 = Color(0xFF3BA8B8)
private val Teal100 = Color(0xFFD3F0F5)
private val Teal50 = Color(0xFFEDF9FB)
private val Amber = Color(0xFFF5A623)
private val AmberDim = Color(0xFF8A5B00)
private val InkDark = Color(0xFF102A2E)

private val LightColors = lightColorScheme(
    primary = Teal700,
    onPrimary = Color.White,
    primaryContainer = Teal100,
    onPrimaryContainer = Teal900,
    secondary = Teal400,
    onSecondary = Color.White,
    secondaryContainer = Teal50,
    onSecondaryContainer = Teal900,
    tertiary = Amber,
    onTertiary = InkDark,
    tertiaryContainer = Color(0xFFFFE9C7),
    onTertiaryContainer = AmberDim,
    background = Color(0xFFF6FAFB),
    onBackground = InkDark,
    surface = Color.White,
    onSurface = InkDark,
    surfaceVariant = Color(0xFFE4EEF0),
    onSurfaceVariant = Color(0xFF44605F),
    outline = Color(0xFF6E8B8A)
)

private val DarkColors = darkColorScheme(
    primary = Teal400,
    onPrimary = Teal900,
    primaryContainer = Teal900,
    onPrimaryContainer = Teal100,
    secondary = Teal100,
    onSecondary = Teal900,
    secondaryContainer = Color(0xFF0E4A56),
    onSecondaryContainer = Teal100,
    tertiary = Amber,
    onTertiary = InkDark,
    background = Color(0xFF0C1B1E),
    onBackground = Color(0xFFDCE8EA),
    surface = Color(0xFF12252A),
    onSurface = Color(0xFFDCE8EA),
    surfaceVariant = Color(0xFF1C363C),
    onSurfaceVariant = Color(0xFFA8C3C2),
    outline = Color(0xFF6E8B8A)
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.3.sp)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}

/** 문서 포맷별 브랜드 색 (오피스 계열 관례색) */
fun DocFormat.brandColor(): Color = when (this) {
    DocFormat.DOCX, DocFormat.DOC -> Color(0xFF2B579A)   // Word 블루
    DocFormat.XLSX -> Color(0xFF217346)                  // Excel 그린
    DocFormat.PPTX -> Color(0xFFD24726)                  // PowerPoint 오렌지
    DocFormat.PDF -> Color(0xFFD93025)                   // PDF 레드
    DocFormat.HWP, DocFormat.HWPX -> Color(0xFF00A2E8)   // 한컴 스카이블루
    DocFormat.ODF -> Color(0xFF0E85CD)
    DocFormat.GOOGLE -> Color(0xFF4285F4)                // Google 블루
    DocFormat.IMAGE -> Color(0xFF7B1FA2)                 // 퍼플
    DocFormat.MARKDOWN -> Color(0xFF37474F)
    DocFormat.TXT -> Color(0xFF607D8B)
    DocFormat.UNKNOWN -> Color(0xFF9E9E9E)
}

/** 포맷 배지에 쓰는 짧은 표기 */
fun DocFormat.badgeText(): String = when (this) {
    DocFormat.DOCX, DocFormat.DOC -> "W"
    DocFormat.XLSX -> "X"
    DocFormat.PPTX -> "P"
    DocFormat.PDF -> "PDF"
    DocFormat.HWP -> "한"
    DocFormat.HWPX -> "한"
    DocFormat.ODF -> "OD"
    DocFormat.GOOGLE -> "G"
    DocFormat.IMAGE -> "IMG"
    DocFormat.MARKDOWN -> "MD"
    DocFormat.TXT -> "TXT"
    DocFormat.UNKNOWN -> "?"
}
