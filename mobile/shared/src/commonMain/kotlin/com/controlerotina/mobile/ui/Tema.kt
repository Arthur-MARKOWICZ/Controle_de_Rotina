package com.controlerotina.mobile.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalPaleta = staticCompositionLocalOf { PaletaClara }

/** Cores do Rotina que o Material 3 não tem papel próprio: sucesso, aviso, XP e linhas do plano. */
object Rotina {
    val cores: PaletaRotina
        @Composable
        @ReadOnlyComposable
        get() = LocalPaleta.current
}

@Composable
fun TemaRotina(escuro: Boolean = isSystemInDarkTheme(), conteudo: @Composable () -> Unit) {
    val paleta = if (escuro) PaletaEscura else PaletaClara
    CompositionLocalProvider(LocalPaleta provides paleta) {
        MaterialTheme(colorScheme = esquemaM3(paleta, escuro), content = conteudo)
    }
}
