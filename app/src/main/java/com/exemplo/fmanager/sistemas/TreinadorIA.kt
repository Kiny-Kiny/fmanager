package com.exemplo.fmanager.sistemas

import com.exemplo.fmanager.dados.Clube
import com.exemplo.fmanager.dados.Jogador
import com.exemplo.fmanager.formacao.*
import kotlin.random.Random

/*
 * TREINADOR ADVERSÁRIO.
 *
 * Antes todo adversário jogava com a mesma 4-3-3 e o mesmo estilo
 * "equilibrado". Isso tornava a liga inteira sem cara própria e fazia a
 * sua escolha tática valer pouco — não havia nada para responder.
 *
 * Agora cada clube da IA tem:
 *   1. FORMAÇÃO escolhida pelo elenco que ele tem de fato
 *   2. ESTILO derivado dos atributos médios do elenco
 *   3. ADAPTAÇÃO durante a partida conforme o placar e o tempo
 *
 * O sorteio usa o id do clube como semente, então o mesmo adversário
 * joga sempre igual ao longo da temporada. Ele tem identidade, não é
 * aleatório a cada jogo.
 */

object TreinadorIA {

    /**
     * Escolhe a formação a partir do que o elenco oferece.
     *
     * A lógica é a de um técnico olhando o plantel: tenho pontas? tenho
     * dois centroavantes? tenho zagueiro sobrando? A formação segue o
     * material humano, não o gosto do programador.
     */
    fun formacaoPara(clube: Clube, elenco: List<Jogador>): Formacao {
        if (elenco.isEmpty()) return Formacoes.padrao
        val rng = Random(clube.id)

        fun contar(vararg siglas: String) = elenco.count { j ->
            siglas.any { it.equals(j.posicao.trim(), ignoreCase = true) }
        }

        val pontas = contar("LW", "RW", "LM", "RM", "LF", "RF")
        val atacantes = contar("ST", "CF")
        val zagueiros = contar("CB")
        val volantes = contar("CDM", "DM")
        val meias = contar("CAM", "AM", "CM")

        // Time fraco encolhe: prioriza não tomar gol.
        val fraco = clube.reputacao < 55

        val candidatas = buildList {
            if (zagueiros >= 5 && fraco) { add(Formacoes.f541); add(Formacoes.f532) }
            if (zagueiros >= 5) { add(Formacoes.f352); add(Formacoes.f523) }
            if (pontas >= 3 && atacantes >= 1) {
                add(Formacoes.f433Ofensiva); add(Formacoes.f4231)
            }
            if (pontas >= 2 && atacantes >= 2) { add(Formacoes.f442); add(Formacoes.f4222) }
            if (volantes >= 2) { add(Formacoes.f4231); add(Formacoes.f4222) }
            if (meias >= 4 && pontas <= 1) { add(Formacoes.f41212); add(Formacoes.f442Diamante) }
            if (fraco) { add(Formacoes.f4141); add(Formacoes.f451) }
            // Sempre existe alguma saída razoável.
            add(Formacoes.f442); add(Formacoes.f433Segurando); add(Formacoes.f4411)
        }

        return candidatas.distinct().let { it[rng.nextInt(it.size)] }
    }

    /** Estilo do clube a partir dos atributos médios do elenco. */
    fun taticaPara(clube: Clube, elenco: List<Jogador>): Tatica {
        if (elenco.isEmpty()) return Estilos.equilibrado
        val (_, base) = TaticaDoClube.derivarDe(
            velocidadeMedia = elenco.map { it.velocidade }.average().toInt(),
            passeMedio = elenco.map { it.passeBaixo }.average().toInt(),
            forcaMedia = elenco.map { it.contatoFisico }.average().toInt(),
            resistenciaMedia = elenco.map { it.resistencia }.average().toInt(),
            geralMedio = elenco.map { it.geral }.average().toInt(),
        )

        // Uma variação pequena e estável por clube, para dois times de
        // perfil parecido não ficarem idênticos.
        val rng = Random(clube.id * 31)
        fun v(x: Int, amplitude: Int) =
            (x + rng.nextInt(-amplitude, amplitude + 1)).coerceIn(0, 100)

        return base.copy(
            velocidadeConstrucao = v(base.velocidadeConstrucao, 12),
            alturaLinha = v(base.alturaLinha, 10),
            intensidadePressao = v(base.intensidadePressao, 12),
            compactacao = v(base.compactacao, 10),
            contraAtaque = v(base.contraAtaque, 14),
            liberdadeCriativa = v(base.liberdadeCriativa, 12),
            riscoNoPasse = v(base.riscoNoPasse, 12),
        )
    }

    /** Monta a escalação da IA: formação própria e melhores por função. */
    fun escalar(clube: Clube, elenco: List<Jogador>): List<Slot> =
        formacaoPara(clube, elenco).criarSlots()

    /**
     * ADAPTAÇÃO NO MEIO DA PARTIDA.
     *
     * É o que faz o adversário parecer que está pensando. Perdendo no
     * fim, ele sobe a linha e arrisca; ganhando fora de casa, recua e
     * segura. Devolve null quando nada precisa mudar.
     */
    fun adaptar(
        atual: Tatica,
        saldoDoTime: Int,
        minuto: Int,
        emCasa: Boolean,
    ): Tatica? {
        val fase = when {
            minuto < 30 -> 0
            minuto < 60 -> 1
            minuto < 80 -> 2
            else -> 3
        }

        return when {
            // Perdendo e o tempo acabando: joga tudo para frente.
            saldoDoTime < 0 && fase >= 2 -> {
                val urgencia = if (fase == 3) 1.6f else 1f
                atual.copy(
                    alturaLinha = (atual.alturaLinha + (18 * urgencia).toInt())
                        .coerceAtMost(96),
                    intensidadePressao = (atual.intensidadePressao +
                            (16 * urgencia).toInt()).coerceAtMost(96),
                    velocidadeConstrucao = (atual.velocidadeConstrucao +
                            (14 * urgencia).toInt()).coerceAtMost(94),
                    riscoNoPasse = (atual.riscoNoPasse + (14 * urgencia).toInt())
                        .coerceAtMost(94),
                    contraAtaque = (atual.contraAtaque - 20).coerceAtLeast(0),
                )
            }

            // Ganhando fora de casa no fim: fecha a loja.
            saldoDoTime > 0 && fase == 3 && !emCasa -> atual.copy(
                alturaLinha = (atual.alturaLinha - 22).coerceAtLeast(8),
                intensidadePressao = (atual.intensidadePressao - 18).coerceAtLeast(10),
                compactacao = (atual.compactacao + 18).coerceAtMost(96),
                contraAtaque = (atual.contraAtaque + 22).coerceAtMost(94),
                riscoNoPasse = (atual.riscoNoPasse - 16).coerceAtLeast(10),
            )

            // Ganhando por muito: administra e poupa.
            saldoDoTime >= 3 && fase >= 2 -> atual.copy(
                intensidadePressao = (atual.intensidadePressao - 20).coerceAtLeast(12),
                velocidadeConstrucao = (atual.velocidadeConstrucao - 18)
                    .coerceAtLeast(10),
            )

            // Empate apertado em casa no fim: aumenta a pressão.
            saldoDoTime == 0 && fase == 3 && emCasa -> atual.copy(
                alturaLinha = (atual.alturaLinha + 14).coerceAtMost(92),
                intensidadePressao = (atual.intensidadePressao + 14).coerceAtMost(92),
            )

            else -> null
        }
    }
}
