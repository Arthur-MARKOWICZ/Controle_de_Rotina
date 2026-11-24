package com.controlerotina.mobile.ui

import androidx.compose.ui.graphics.Color
import com.controlerotina.mobile.api.OrigemBloco
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PaletaTest {

    private val paletas = listOf("claro" to PaletaClara, "escuro" to PaletaEscura)

    private fun exigir(nome: String, frente: Color, fundo: Color, minimo: Double) {
        val razao = contraste(frente, fundo)
        assertTrue(razao >= minimo, "$nome: contraste $razao < $minimo")
    }

    @Test
    fun textoPassaAaNosDoisTemas() {
        for ((tema, p) in paletas) {
            exigir("$tema ink/bg", p.ink, p.bg, 4.5)
            exigir("$tema ink/panel", p.ink, p.panel, 4.5)
            exigir("$tema muted/panel", p.muted, p.panel, 4.5)
            exigir("$tema muted/raised", p.muted, p.raised, 4.5)
            exigir("$tema accent/panel", p.accent, p.panel, 4.5)
            exigir("$tema accent/accentSoft", p.accent, p.accentSoft, 4.5)
            exigir("$tema onAccent/accent", p.onAccent, p.accent, 4.5)
            exigir("$tema onXp/xp", p.onXp, p.xp, 4.5)
            exigir("$tema sucesso/sucessoFundo", p.sucesso, p.sucessoFundo, 4.5)
            exigir("$tema aviso/avisoFundo", p.aviso, p.avisoFundo, 4.5)
            exigir("$tema erro/erroFundo", p.erro, p.erroFundo, 4.5)
            exigir("$tema erro/panel", p.erro, p.panel, 4.5)
            exigir("$tema aviso/accentSoft (barra de sessão)", p.aviso, p.accentSoft, 4.5)
        }
    }

    @Test
    fun bordaDeCampoELinhasPassamTresParaUm() {
        for ((tema, p) in paletas) {
            exigir("$tema lineStrong/panel", p.lineStrong, p.panel, 3.0)
            p.linhas.forEachIndexed { i, cor -> exigir("$tema linha ${i + 1}/panel", cor, p.panel, 3.0) }
        }
    }

    @Test
    fun esquemaM3NaoCaiNoLavandaPadrao() {
        for ((tema, p) in paletas) {
            val esquema = esquemaM3(p, escuro = tema == "escuro")
            assertEquals(p.accentSoft, esquema.secondaryContainer, "$tema: indicador da aba ativa")
            assertEquals(p.lineStrong, esquema.outline, tema)
            assertEquals(p.line, esquema.outlineVariant, tema)
            assertEquals(p.xp, esquema.tertiary, "$tema: laranja só como terciária (XP)")
            assertEquals(p.chrome, esquema.surfaceContainer, "$tema: barra de navegação")
            assertEquals(p.panel, esquema.surfaceContainerHighest, "$tema: cartões")
        }
    }

    @Test
    fun secundariaNaoEOLaranjaDoXp() {
        for ((tema, p) in paletas) {
            assertNotEquals(p.xp, esquemaM3(p, tema == "escuro").secondary, tema)
        }
    }

    @Test
    fun corDaLinhaEhEstavelPorProjeto() {
        val a = corDaLinha(OrigemBloco.TAREFA, "TCC", PaletaClara)
        assertEquals(a, corDaLinha(OrigemBloco.TAREFA, "  tcc ", PaletaClara))
        assertTrue(a in PaletaClara.linhas)
    }

    @Test
    fun corDaLinhaUsaCinzaParaHabitoEAcaoSemProjeto() {
        assertEquals(PaletaEscura.linhaHabito, corDaLinha(OrigemBloco.HABITO, "TCC", PaletaEscura))
        assertEquals(PaletaEscura.accent, corDaLinha(OrigemBloco.TAREFA, null, PaletaEscura))
        assertEquals(PaletaEscura.accent, corDaLinha(OrigemBloco.TAREFA, "   ", PaletaEscura))
    }

    @Test
    fun corDaLinhaCoincideComOWebParaOMesmoProjeto() {
        // Valores calculados com o hash do web (planner/linhaCor.ts): TCC → linha 1, Certificação → linha 2.
        assertEquals(PaletaClara.linhas[0], corDaLinha(OrigemBloco.TAREFA, "TCC", PaletaClara))
        assertEquals(PaletaClara.linhas[1], corDaLinha(OrigemBloco.TAREFA, "Certificação", PaletaClara))
    }

    @Test
    fun contrasteDeCoresIguaisEhUm() {
        assertEquals(1.0, contraste(Color(0xFF123456), Color(0xFF123456)), 1e-9)
    }
}
