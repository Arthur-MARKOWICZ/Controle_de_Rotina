package com.controlerotina.mobile.dominio

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

/**
 * Corpo de um `PATCH` parcial (design D6b): só os campos cujo valor mudou entre o que foi
 * carregado e o que o usuário editou. Um campo editado para `JsonNull` sai como `null`
 * explícito, que o backend lê como "limpar".
 */
fun diffPatch(original: Map<String, JsonElement>, editado: Map<String, JsonElement>): JsonObject =
    JsonObject(editado.filter { (campo, valor) -> (original[campo] ?: JsonNull) != valor })

/** Texto vira `JsonNull` quando vazio; o resto vira primitivo. */
fun campoTexto(valor: String?): JsonElement =
    valor?.trim()?.takeIf { it.isNotEmpty() }?.let(::JsonPrimitive) ?: JsonNull

fun campoInt(valor: Int?): JsonElement = valor?.let(::JsonPrimitive) ?: JsonNull

/** Item do Decidir com data limite já passada: só destaque visual, sem status de atraso. */
fun decidirVencido(decidirAte: String?, agora: Instant): Boolean {
    if (decidirAte == null) return false
    val limite = runCatching { Instant.parse(decidirAte) }.getOrNull() ?: return false
    return limite < agora
}

/** Mapeia os erros de campo de um Problem Details para `campo -> mensagem`. */
fun errosPorCampo(problema: com.controlerotina.mobile.api.ProblemDetail?): Map<String, String> =
    problema?.errors.orEmpty()
        .mapNotNull { erro -> erro.property?.let { it to (erro.message ?: "inválido") } }
        .toMap()
