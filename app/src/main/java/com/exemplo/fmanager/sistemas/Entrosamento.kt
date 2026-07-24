package com.exemplo.fmanager.sistemas

import com.exemplo.fmanager.dados.Jogador
import com.exemplo.fmanager.formacao.Fase
import com.exemplo.fmanager.formacao.Slot
import kotlin.math.sqrt

/*
 * ENTROSAMENTO (linkagem).
 *
 * Jogadores que se conhecem jogam melhor juntos. Vale para quem veio do
 * mesmo clube, atua na mesma liga ou divide a mesma seleção.
 *
 * O detalhe importante: como a formação é livre, não dá para ter um
 * grafo fixo de ligações como nos jogos de cartinha. Aqui os vizinhos
 * são calculados por DISTÂNCIA NO CAMPO. Quem você põe perto, se liga.
 * Se você espalha o time, o entrosamento cai — o que é justo, porque
 * jogadores distantes se combinam menos mesmo.
 */

data class Ligacao(
    val slotA: Int,
    val slotB: Int,
    val forca: Int,           // 0..3
    val motivos: List<String>,
)

data class Entrosamento(
    val porJogador: Map<Int, Int>,    // slotId -> 0..100
    val ligacoes: List<Ligacao>,
    val media: Int,
)

object CalculadoraEntrosamento {

    /** Quantos vizinhos mais próximos contam como ligação. */
    private const val VIZINHOS = 3

    /** Além desta distância, a ligação não se forma. */
    private const val ALCANCE = 0.42f

    fun calcular(
        slots: List<Slot>,
        elenco: Map<Int, Jogador>,
        fase: Fase = Fase.SEM_POSSE,
    ): Entrosamento {

        val ligacoes = mutableListOf<Ligacao>()
        val pontos = mutableMapOf<Int, Int>()
        val maximos = mutableMapOf<Int, Int>()

        slots.forEach { slot ->
            val jogador = slot.jogadorId?.let { elenco[it] } ?: return@forEach
            val posA = slot.em(fase)

            // Os N mais próximos dentro do alcance.
            val vizinhos = slots
                .filter { it.id != slot.id && it.jogadorId != null }
                .map { outro ->
                    val posB = outro.em(fase)
                    val dx = posA.x - posB.x
                    val dy = posA.y - posB.y
                    outro to sqrt(dx * dx + dy * dy)
                }
                .filter { it.second <= ALCANCE }
                .sortedBy { it.second }
                .take(VIZINHOS)

            vizinhos.forEach { (outro, distancia) ->
                val vizinho = elenco[outro.jogadorId] ?: return@forEach

                val motivos = mutableListOf<String>()
                var forca = 0

                if (jogador.clube == vizinho.clube && jogador.clube.isNotBlank()) {
                    forca += 2; motivos += "mesmo clube"
                } else if (jogador.liga == vizinho.liga && jogador.liga.isNotBlank()) {
                    forca += 1; motivos += "mesma liga"
                }

                if (jogador.nacionalidade == vizinho.nacionalidade &&
                    jogador.nacionalidade.isNotBlank()
                ) {
                    forca += 1; motivos += "mesma seleção"
                }

                forca = forca.coerceAtMost(3)

                // Ligação perde força com a distância.
                val peso = (1f - distancia / ALCANCE).coerceIn(0.35f, 1f)
                pontos.merge(slot.id, (forca * peso * 10).toInt(), Int::plus)
                maximos.merge(slot.id, 30, Int::plus)

                // Registra só uma vez por par, para a lista da tela.
                if (slot.id < outro.id && forca > 0) {
                    ligacoes += Ligacao(slot.id, outro.id, forca, motivos)
                }
            }
        }

        val porJogador = slots.associate { slot ->
            val p = pontos[slot.id] ?: 0
            val m = maximos[slot.id] ?: 1
            slot.id to ((p * 100) / m).coerceIn(0, 100)
        }

        return Entrosamento(
            porJogador = porJogador,
            ligacoes = ligacoes.sortedByDescending { it.forca },
            media = if (porJogador.isEmpty()) 0 else porJogador.values.average().toInt(),
        )
    }

    /**
     * Multiplicador aplicado no rendimento do jogador.
     *
     * A faixa é estreita de propósito: entrosamento tempera o time, não
     * decide a partida. Um elenco desentrosado de craques ainda ganha de
     * um time entrosado de pernas de pau.
     */
    fun multiplicador(entrosamento: Int): Float =
        (0.92f + (entrosamento / 100f) * 0.13f).coerceIn(0.92f, 1.05f)
}
