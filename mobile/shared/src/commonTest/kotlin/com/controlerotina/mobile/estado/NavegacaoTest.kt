package com.controlerotina.mobile.estado

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavegacaoTest {
    @Test
    fun abaInicialETarefasEOrdemDasAbas() {
        assertEquals(Aba.TAREFAS, Navegacao().aba.value)
        assertEquals(listOf("Tarefas", "Plano", "Hábitos", "Projetos", "Decidir"), Aba.entries.map { it.rotulo })
    }

    @Test
    fun perfilEmpilhaEVoltarRetornaParaAAba() {
        val nav = Navegacao()
        nav.selecionar(Aba.HABITOS)
        nav.abrir(Destino.Perfil)
        assertEquals(Destino.Perfil, nav.pilha.value.last())
        assertTrue(nav.voltar())
        assertTrue(nav.pilha.value.isEmpty())
        assertEquals(Aba.HABITOS, nav.aba.value)
        assertFalse(nav.voltar())
    }

    @Test
    fun trocarDeAbaFechaDetalhesELimparVoltaParaTarefas() {
        val nav = Navegacao()
        nav.abrir(Destino.TarefaDetalhe("t1"))
        nav.selecionar(Aba.PLANO)
        assertTrue(nav.pilha.value.isEmpty())
        nav.abrir(Destino.Perfil)
        nav.limpar()
        assertEquals(Aba.TAREFAS, nav.aba.value)
        assertTrue(nav.pilha.value.isEmpty())
    }

    @Test
    fun lembretesEmpilhaSobrePerfilEVoltarRetornaAoPerfil() {
        val nav = Navegacao()
        nav.abrir(Destino.Perfil)
        nav.abrir(Destino.Lembretes)
        assertEquals(listOf(Destino.Perfil, Destino.Lembretes), nav.pilha.value)
        nav.abrir(Destino.Lembretes)
        assertEquals(2, nav.pilha.value.size, "abrir duas vezes não empilha duplicado")
        assertEquals(true, nav.voltar())
        assertEquals(Destino.Perfil, nav.pilha.value.last())
    }
}
