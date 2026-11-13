package com.controlerotina.mobile.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.controlerotina.mobile.ui.App

class MainActivity : ComponentActivity() {
    private val deps get() = (application as RotinaApplication).deps
    private var primeiroResume = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App(deps) }
    }

    override fun onResume() {
        super.onResume()
        // Ao voltar do segundo plano: recarrega plano, execuções e sessão ativa (spec mobile-hoje).
        if (primeiroResume) primeiroResume = false else deps.aoVoltarAoPrimeiroPlano()
    }
}
