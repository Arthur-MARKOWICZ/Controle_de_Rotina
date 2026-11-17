package com.controlerotina.mobile.api

import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Compara modelos `@Serializable` com os schemas de `contract/openapi.json`.
 *
 * - todo campo do modelo existe no schema (o modelo pode ter só parte dos campos);
 * - em modelos de resposta, campo não anulável e sem default é `required` e não aceita `null` no schema
 *   (com default, `coerceInputValues` absorve nulo/ausente);
 * - enums têm exatamente os mesmos valores.
 */
class VerificadorContrato(private val schemas: JsonObject) {

    fun verificarModelo(descritor: SerialDescriptor, schemaNome: String, resposta: Boolean): List<String> {
        val modelo = descritor.serialName.substringAfterLast('.').substringBefore('<')
        val schema = schemas[schemaNome]?.jsonObject
            ?: return listOf("$modelo: schema '$schemaNome' não existe no contrato")
        val propriedades = schema["properties"]?.jsonObject ?: JsonObject(emptyMap())
        val obrigatorios = schema["required"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty().toSet()
        return (0 until descritor.elementsCount).flatMap { i ->
            val campo = descritor.getElementName(i)
            val propriedade = propriedades[campo]?.jsonObject
                ?: return@flatMap listOf("$modelo.$campo: campo não existe em $schemaNome")
            val exigeValor = !descritor.getElementDescriptor(i).isNullable && !descritor.isElementOptional(i)
            when {
                !resposta || !exigeValor -> emptyList()
                campo !in obrigatorios -> listOf("$modelo.$campo: é obrigatório no modelo mas opcional em $schemaNome")
                aceitaNull(propriedade) -> listOf("$modelo.$campo: é não anulável no modelo mas anulável em $schemaNome")
                else -> emptyList()
            }
        }
    }

    fun verificarEnum(descritor: SerialDescriptor, schemaNome: String, propriedade: String): List<String> {
        val enumNome = descritor.serialName.substringAfterLast('.')
        if (descritor.kind != SerialKind.ENUM) return listOf("$enumNome: não é enum")
        val valoresContrato = schemas[schemaNome]?.jsonObject?.get("properties")?.jsonObject?.get(propriedade)
            ?.let(::valoresEnum)
            ?: return listOf("$enumNome: $schemaNome.$propriedade não existe ou não é enum no contrato")
        val valoresModelo = (0 until descritor.elementsCount).map(descritor::getElementName).toSet()
        return (valoresContrato - valoresModelo).map { "$enumNome: falta o valor $it (existe em $schemaNome.$propriedade)" } +
            (valoresModelo - valoresContrato).map { "$enumNome: valor $it não existe em $schemaNome.$propriedade" }
    }

    private fun valoresEnum(propriedade: JsonElement): Set<String>? {
        val obj = propriedade.jsonObject
        obj["enum"]?.let { lista -> return lista.jsonArray.map { it.jsonPrimitive.content }.toSet() }
        return obj["oneOf"]?.jsonArray?.firstNotNullOfOrNull { alternativa -> alternativa.jsonObject["enum"] }
            ?.jsonArray?.map { it.jsonPrimitive.content }?.toSet()
    }

    private fun aceitaNull(propriedade: JsonObject): Boolean {
        val tipo = propriedade["type"]
        if (tipo is JsonArray && tipo.any { it.jsonPrimitive.content == "null" }) return true
        if (tipo is JsonPrimitive && tipo.content == "null") return true
        return listOf("oneOf", "anyOf").any { chave ->
            propriedade[chave]?.jsonArray?.any { aceitaNull(it.jsonObject) } == true
        }
    }

    companion object {
        fun doArquivo(caminho: String?): VerificadorContrato {
            requireNotNull(caminho) { "Propriedade 'contrato.arquivo' não definida (ver shared/build.gradle.kts)." }
            val arquivo = File(caminho)
            require(arquivo.isFile) {
                "Contrato não encontrado em ${arquivo.absolutePath}. Gere com: " +
                    "cd backend && mvn test -Dtest=ContratoOpenApiIT -Dcontrato.atualizar=true"
            }
            return deTexto(arquivo.readText())
        }

        fun deTexto(texto: String): VerificadorContrato {
            val spec = Json.parseToJsonElement(texto).jsonObject
            val schemas = spec["components"]?.jsonObject?.get("schemas")?.jsonObject
                ?: throw IllegalArgumentException("Contrato sem components.schemas")
            return VerificadorContrato(schemas)
        }
    }
}
