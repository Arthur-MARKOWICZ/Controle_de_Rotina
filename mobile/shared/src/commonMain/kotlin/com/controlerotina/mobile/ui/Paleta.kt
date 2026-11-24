package com.controlerotina.mobile.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.controlerotina.mobile.api.OrigemBloco

/**
 * Paleta "híbrido 1 + 4", espelho de frontend-web/src/styles/tokens.css.
 * Base neutra; azul só para ação/seleção; laranja só para XP; cores de linha por projeto.
 */
data class PaletaRotina(
    val bg: Color,
    val chrome: Color,
    val panel: Color,
    val raised: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val lineStrong: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val xp: Color,
    val xpSoft: Color,
    val onXp: Color,
    val sucesso: Color,
    val sucessoFundo: Color,
    val aviso: Color,
    val avisoFundo: Color,
    val erro: Color,
    val erroFundo: Color,
    val linhas: List<Color>,
) {
    val linhaHabito: Color get() = lineStrong
}

val PaletaClara = PaletaRotina(
    bg = Color(0xFFF5F6F7),
    chrome = Color(0xFFECEEF0),
    panel = Color(0xFFFFFFFF),
    raised = Color(0xFFEEF0F2),
    ink = Color(0xFF16181B),
    muted = Color(0xFF535A62),
    line = Color(0xFFDFE2E6),
    lineStrong = Color(0xFF868C93),
    accent = Color(0xFF1F5FD6),
    accentSoft = Color(0xFFE3ECFD),
    onAccent = Color(0xFFFFFFFF),
    xp = Color(0xFFB04E0A),
    xpSoft = Color(0xFFFDEBDC),
    onXp = Color(0xFFFFFFFF),
    sucesso = Color(0xFF1D7D3E),
    sucessoFundo = Color(0xFFE1F4E7),
    aviso = Color(0xFF8A6000),
    avisoFundo = Color(0xFFFEEDC5),
    erro = Color(0xFFB52524),
    erroFundo = Color(0xFFFDE8E7),
    linhas = listOf(Color(0xFF2F6FE0), Color(0xFF1F8A5B), Color(0xFF16849A), Color(0xFF64748B)),
)

val PaletaEscura = PaletaRotina(
    bg = Color(0xFF161718),
    chrome = Color(0xFF0F1011),
    panel = Color(0xFF1D1F21),
    raised = Color(0xFF26282B),
    ink = Color(0xFFECECEC),
    muted = Color(0xFFA4A8AD),
    line = Color(0xFF2E3134),
    lineStrong = Color(0xFF6B7075),
    accent = Color(0xFF86AAF2),
    accentSoft = Color(0xFF22304A),
    onAccent = Color(0xFF0F1011),
    xp = Color(0xFFF0915A),
    xpSoft = Color(0xFF3D2414),
    onXp = Color(0xFF0F1011),
    sucesso = Color(0xFF6FC596),
    sucessoFundo = Color(0xFF1A2E22),
    aviso = Color(0xFFE6CF72),
    avisoFundo = Color(0xFF382C11),
    erro = Color(0xFFEF837B),
    erroFundo = Color(0xFF3A1F1D),
    linhas = listOf(Color(0xFF6F9EF0), Color(0xFF5FBF8F), Color(0xFF5AB8C8), Color(0xFF95A3B8)),
)

/** Todos os papéis do Material 3 preenchidos: nada cai no roxo/lavanda padrão. */
fun esquemaM3(p: PaletaRotina, escuro: Boolean): ColorScheme {
    val outra = if (escuro) PaletaClara else PaletaEscura
    val base = if (escuro) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = p.accent,
        onPrimary = p.onAccent,
        primaryContainer = p.accentSoft,
        onPrimaryContainer = p.accent,
        inversePrimary = outra.accent,
        secondary = p.muted,
        onSecondary = p.panel,
        secondaryContainer = p.accentSoft,
        onSecondaryContainer = p.accent,
        tertiary = p.xp,
        onTertiary = p.onXp,
        tertiaryContainer = p.xpSoft,
        onTertiaryContainer = p.ink,
        background = p.bg,
        onBackground = p.ink,
        surface = p.panel,
        onSurface = p.ink,
        surfaceVariant = p.raised,
        onSurfaceVariant = p.muted,
        surfaceTint = p.accent,
        inverseSurface = p.ink,
        inverseOnSurface = p.bg,
        error = p.erro,
        onError = p.panel,
        errorContainer = p.erroFundo,
        onErrorContainer = p.erro,
        outline = p.lineStrong,
        outlineVariant = p.line,
        surfaceBright = p.panel,
        surfaceDim = p.bg,
        surfaceContainerLowest = p.panel,
        surfaceContainerLow = p.bg,
        surfaceContainer = p.chrome,
        surfaceContainerHigh = p.raised,
        surfaceContainerHighest = p.panel,
    )
}

/** Mesmo hash de frontend-web/src/planner/linhaCor.ts, para o projeto ter a mesma cor nas duas plataformas. */
fun corDaLinha(origem: OrigemBloco, projetoNome: String?, p: PaletaRotina): Color {
    if (origem == OrigemBloco.HABITO) return p.linhaHabito
    val chave = projetoNome?.trim()?.lowercase()
    if (chave.isNullOrEmpty()) return p.accent
    var hash = 0L
    for (c in chave) hash = (hash * 31 + c.code) and 0xFFFFFFFFL
    return p.linhas[(hash % p.linhas.size).toInt()]
}

/** Razão de contraste WCAG entre duas cores opacas. */
fun contraste(a: Color, b: Color): Double {
    val la = a.luminance().toDouble()
    val lb = b.luminance().toDouble()
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}
