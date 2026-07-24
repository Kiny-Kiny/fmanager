package com.exemplo.fmanager.sistemas

import com.exemplo.fmanager.dados.Jogador
import com.exemplo.fmanager.dados.adequacao
import com.exemplo.fmanager.formacao.Papel
import kotlin.math.max
import kotlin.random.Random

/*
 * SISTEMA DE TREINO.
 *
 * Regras que governam a evolução:
 *   - Jovem evolui rápido, veterano regride. O ponto de virada é 29.
 *   - Ninguém passa do próprio potencial.
 *   - Quanto mais perto do teto, mais devagar (retornos decrescentes).
 *   - Intensidade alta acelera o ganho e aumenta o risco de lesão.
 */

enum class FocoTreino(val rotulo: String) {
    RITMO("Ritmo"),
    FINALIZACAO("Finalização"),
    PASSE("Passe"),
    DRIBLE("Drible"),
    DEFESA("Defesa"),
    FISICO("Físico"),
    GOLEIRO("Goleiro"),
}

enum class Intensidade(val rotulo: String, val fator: Float, val riscoLesao: Float) {
    LEVE("Leve", 0.5f, 0.002f),
    NORMAL("Normal", 1.0f, 0.006f),
    PESADA("Pesada", 1.6f, 0.020f),
}

data class ResultadoTreino(
    val jogador: Jogador,
    val ganhos: Map<String, Int>,   // atributo -> variação
    val lesionadoPorSemanas: Int = 0,
    val perdaCondicao: Int = 0,
)

object Treino {

    /** Atributos afetados por cada foco. */
    private val GRUPOS: Map<FocoTreino, List<String>> = mapOf(
        FocoTreino.RITMO to listOf("aceleracao", "velocidade", "agilidade"),
        FocoTreino.FINALIZACAO to listOf("finalizacao", "forcaChute", "posicionamento", "chuteLonge"),
        FocoTreino.PASSE to listOf("passeBaixo", "passeAlto", "visao", "cruzamento"),
        FocoTreino.DRIBLE to listOf("drible", "controleBola", "equilibrio", "sangueFrio"),
        FocoTreino.DEFESA to listOf("rouboBola", "consciencaDef", "interceptacao", "carrinho"),
        FocoTreino.FISICO to listOf("contatoFisico", "resistencia", "impulsao"),
        FocoTreino.GOLEIRO to listOf("golReflexo", "golMergulho", "golPosicionamento", "golDefesaMao"),
    )

    /**
     * Roda uma semana de treino. Devolve um jogador novo (data class é
     * imutável) mais o relatório do que mudou.
     */
    fun semana(
        jogador: Jogador,
        foco: FocoTreino,
        intensidade: Intensidade,
        rng: Random = Random.Default,
    ): ResultadoTreino {

        val fatorIdade = when {
            jogador.idade <= 19 -> 1.8f
            jogador.idade <= 23 -> 1.4f
            jogador.idade <= 26 -> 1.0f
            jogador.idade <= 28 -> 0.6f
            jogador.idade <= 31 -> 0.2f
            jogador.idade <= 33 -> -0.3f
            else -> -0.7f
        }

        // Quanto espaço ainda existe até o potencial.
        val margem = max(0, jogador.potencial - jogador.geral)
        val fatorMargem = when {
            margem >= 10 -> 1.5f
            margem >= 5 -> 1.0f
            margem >= 2 -> 0.5f
            else -> 0.15f
        }

        val forca = fatorIdade * fatorMargem * intensidade.fator
        val ganhos = mutableMapOf<String, Int>()
        var atualizado = jogador

        GRUPOS.getValue(foco).forEach { atributo ->
            val atual = lerAtributo(jogador, atributo)
            // Retornos decrescentes: subir de 88 para 89 é bem mais
            // difícil do que subir de 60 para 61.
            val resistencia = 1f - (atual / 110f)
            val esperado = forca * resistencia * 0.9f

            val variacao = when {
                esperado > 0 -> if (rng.nextFloat() < esperado.coerceAtMost(0.95f)) 1 else 0
                esperado < 0 -> if (rng.nextFloat() < (-esperado).coerceAtMost(0.5f)) -1 else 0
                else -> 0
            }

            if (variacao != 0) {
                val novo = (atual + variacao).coerceIn(1, 99)
                atualizado = escreverAtributo(atualizado, atributo, novo)
                ganhos[atributo] = variacao
            }
        }

        // O geral acompanha a evolução dos atributos, sem furar o potencial.
        if (ganhos.values.sum() != 0) {
            val delta = if (ganhos.values.sum() > 0) 1 else -1
            val novoGeral = (atualizado.geral + delta)
                .coerceIn(1, atualizado.potencial)
            atualizado = atualizado.copy(geral = novoGeral)
        }

        val lesao = if (rng.nextFloat() < intensidade.riscoLesao)
            rng.nextInt(1, 6) else 0

        return ResultadoTreino(
            jogador = atualizado,
            ganhos = ganhos,
            lesionadoPorSemanas = lesao,
            perdaCondicao = (intensidade.fator * 8).toInt(),
        )
    }

    /** Sugere no que treinar para o jogador render mais no papel
     *  em que você quer usá-lo. Liga o treino ao editor de formação. */
    fun focoRecomendado(jogador: Jogador, papel: Papel): FocoTreino {
        val atual = jogador.adequacao(papel)
        return FocoTreino.entries
            .filter { it != FocoTreino.GOLEIRO || papel == Papel.GOL }
            .maxBy { foco ->
                // Simula +3 em cada atributo do grupo e vê qual foco
                // levanta mais a adequação naquele papel.
                var simulado = jogador
                GRUPOS.getValue(foco).forEach { attr ->
                    val v = (lerAtributo(simulado, attr) + 3).coerceAtMost(99)
                    simulado = escreverAtributo(simulado, attr, v)
                }
                simulado.adequacao(papel) - atual
            }
    }

    // Acesso por nome. Verboso, mas evita reflection (que é lenta no
    // Android e quebra com o R8/ProGuard em release).
    private fun lerAtributo(j: Jogador, nome: String): Int = when (nome) {
        "aceleracao" -> j.aceleracao; "velocidade" -> j.velocidade
        "agilidade" -> j.agilidade; "equilibrio" -> j.equilibrio
        "finalizacao" -> j.finalizacao; "forcaChute" -> j.forcaChute
        "posicionamento" -> j.posicionamento; "chuteLonge" -> j.chuteLonge
        "passeBaixo" -> j.passeBaixo; "passeAlto" -> j.passeAlto
        "visao" -> j.visao; "cruzamento" -> j.cruzamento
        "drible" -> j.drible; "controleBola" -> j.controleBola
        "sangueFrio" -> j.sangueFrio
        "rouboBola" -> j.rouboBola; "consciencaDef" -> j.consciencaDef
        "interceptacao" -> j.interceptacao; "carrinho" -> j.carrinho
        "contatoFisico" -> j.contatoFisico; "resistencia" -> j.resistencia
        "impulsao" -> j.impulsao
        "golReflexo" -> j.golReflexo; "golMergulho" -> j.golMergulho
        "golPosicionamento" -> j.golPosicionamento; "golDefesaMao" -> j.golDefesaMao
        else -> 0
    }

    private fun escreverAtributo(j: Jogador, nome: String, v: Int): Jogador = when (nome) {
        "aceleracao" -> j.copy(aceleracao = v); "velocidade" -> j.copy(velocidade = v)
        "agilidade" -> j.copy(agilidade = v); "equilibrio" -> j.copy(equilibrio = v)
        "finalizacao" -> j.copy(finalizacao = v); "forcaChute" -> j.copy(forcaChute = v)
        "posicionamento" -> j.copy(posicionamento = v); "chuteLonge" -> j.copy(chuteLonge = v)
        "passeBaixo" -> j.copy(passeBaixo = v); "passeAlto" -> j.copy(passeAlto = v)
        "visao" -> j.copy(visao = v); "cruzamento" -> j.copy(cruzamento = v)
        "drible" -> j.copy(drible = v); "controleBola" -> j.copy(controleBola = v)
        "sangueFrio" -> j.copy(sangueFrio = v)
        "rouboBola" -> j.copy(rouboBola = v); "consciencaDef" -> j.copy(consciencaDef = v)
        "interceptacao" -> j.copy(interceptacao = v); "carrinho" -> j.copy(carrinho = v)
        "contatoFisico" -> j.copy(contatoFisico = v); "resistencia" -> j.copy(resistencia = v)
        "impulsao" -> j.copy(impulsao = v)
        "golReflexo" -> j.copy(golReflexo = v); "golMergulho" -> j.copy(golMergulho = v)
        "golPosicionamento" -> j.copy(golPosicionamento = v)
        "golDefesaMao" -> j.copy(golDefesaMao = v)
        else -> j
    }
}
