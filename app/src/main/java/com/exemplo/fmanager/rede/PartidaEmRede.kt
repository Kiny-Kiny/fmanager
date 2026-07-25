package com.exemplo.fmanager.rede

import com.exemplo.fmanager.dados.Jogador
import com.exemplo.fmanager.formacao.Slot
import com.exemplo.fmanager.formacao.Tatica
import com.exemplo.fmanager.motor.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import kotlin.random.Random

/*
 * PARTIDA EM REDE — PASSO TRANCADO (LOCKSTEP).
 *
 * Nenhum dos dois manda o resultado para o outro. Os dois simulam a MESMA
 * partida localmente, com a mesma semente e a mesma entrada, e comparam
 * checksums a cada 100 lances.
 *
 * Isso só é possível porque o motor é determinístico: PartidaAoVivo
 * recebe um Random(semente) e nada mais é aleatório. Dois aparelhos com
 * a mesma entrada produzem gol a gol o mesmo jogo.
 *
 * É também o que impede trapaça sem servidor. Um cliente modificado não
 * consegue "declarar" que ganhou: se a simulação dele divergir da do
 * outro, o checksum quebra e a partida é anulada. O que ele consegue é
 * apenas invalidar o jogo, nunca fabricar um resultado.
 *
 * COMANDOS COM ATRASO: uma troca de tática não vale no lance atual, vale
 * num lance futuro combinado. Assim os dois têm tempo de receber o aviso
 * e aplicar no mesmo ponto, sem dessincronizar.
 */

const val VERSAO_MOTOR = 3
private const val ATRASO_COMANDO = 12
private const val INTERVALO_CHECKSUM = 100

sealed interface EstadoPartidaRede {
    data object Preparando : EstadoPartidaRede
    data class BaseIncompativel(val detalhe: String) : EstadoPartidaRede
    data object TrocandoEsquadroes : EstadoPartidaRede
    data class EmJogo(val lance: Int) : EstadoPartidaRede
    data class Dessincronizada(val lance: Int) : EstadoPartidaRede
    data class Terminada(val golsMandante: Int, val golsVisitante: Int) :
        EstadoPartidaRede
    data class Abortada(val motivo: String) : EstadoPartidaRede
}

class PartidaEmRede(
    private val sala: Sala,
    private val souAnfitriao: Boolean,
) {
    private val _estado = MutableStateFlow<EstadoPartidaRede>(
        EstadoPartidaRede.Preparando)
    val estado: StateFlow<EstadoPartidaRede> = _estado.asStateFlow()

    var partida: PartidaAoVivo? = null
        private set

    /** Comandos agendados por número de lance. */
    private val agenda = mutableMapOf<Int, MutableList<JSONObject>>()
    private var lanceAtual = 0
    private var meuChecksum: String? = null

    private var esquadraoDoOutro: JSONObject? = null

    /** No lockstep, o anfitrião é sempre o mandante. */
    val souMandante: Boolean get() = souAnfitriao

    // ---------------------------------------------------- 1. IMPRESSÃO

    /**
     * Compara as bases antes de qualquer coisa.
     *
     * Se as bases de jogadores diferem, a simulação determinística não
     * tem como bater — melhor recusar agora do que dessincronizar no
     * minuto 60.
     */
    suspend fun enviarImpressao(totalJogadores: Int, impressaoBase: String) {
        sala.enviar(
            Tipo.IMPRESSAO,
            Corpos.impressao(totalJogadores, impressaoBase, VERSAO_MOTOR),
            assinar = true,
        )
    }

    fun conferirImpressao(
        m: Mensagem,
        meuTotal: Int,
        minhaImpressao: String,
    ): Boolean {
        if (!sala.assinaturaValida(m)) {
            _estado.value = EstadoPartidaRede.Abortada("Assinatura inválida")
            return false
        }
        val c = m.corpo
        if (c.optInt("versaoMotor") != VERSAO_MOTOR) {
            _estado.value = EstadoPartidaRede.BaseIncompativel(
                "Versões diferentes do motor de partida. Atualizem os dois.")
            return false
        }
        if (c.optString("impressaoBase") != minhaImpressao) {
            _estado.value = EstadoPartidaRede.BaseIncompativel(
                "Bases de jogadores diferentes " +
                        "(${c.optInt("totalJogadores")} contra $meuTotal). " +
                        "Os dois precisam ter importado o mesmo dataset."
            )
            return false
        }
        _estado.value = EstadoPartidaRede.TrocandoEsquadroes
        return true
    }

    // ---------------------------------------------------- 2. ESQUADRÃO

    suspend fun enviarEsquadrao(
        nomeClube: String,
        elenco: List<Jogador>,
        slots: List<Slot>,
        tatica: Tatica,
    ) {
        sala.enviar(
            Tipo.ESQUADRAO,
            Corpos.esquadrao(nomeClube, elenco, slots, tatica),
            assinar = true,
        )
    }

    fun receberEsquadrao(m: Mensagem): Boolean {
        if (!sala.assinaturaValida(m)) {
            _estado.value = EstadoPartidaRede.Abortada(
                "Escalação adversária sem assinatura válida")
            return false
        }
        esquadraoDoOutro = m.corpo
        return true
    }

    // ------------------------------------------------------- 3. INÍCIO

    /**
     * Monta a partida. Os dois lados chamam isto com os mesmos dados e
     * a mesma semente, então constroem objetos idênticos.
     */
    fun iniciar(
        meuClube: String,
        meuElenco: List<Jogador>,
        meusSlots: List<Slot>,
        minhaTatica: Tatica,
    ): PartidaAoVivo? {
        val outro = esquadraoDoOutro ?: return null
        val semente = sala.semente ?: return null

        val elencoOutro = outro.getJSONArray("jogadores").let { arr ->
            (0 until arr.length()).map { jogadorDeJson(arr.getJSONObject(it)) }
        }
        val slotsOutro = outro.getJSONArray("slots").let { arr ->
            (0 until arr.length()).map { slotDeJson(arr.getJSONObject(it)) }
        }
        val taticaOutro = taticaDeJson(outro.getJSONObject("tatica"))
        val clubeOutro = outro.getString("clube")

        fun montar(
            nome: String, elenco: List<Jogador>,
            slots: List<Slot>, tatica: Tatica, id: Int,
        ): TimeEmCampo? {
            val porId = elenco.associateBy { it.id }
            val usados = mutableSetOf<Int>()
            val emCampo = slots.take(11).map { s ->
                val j = s.jogadorId?.let { porId[it] }?.takeIf { it.id !in usados }
                    ?: elenco.firstOrNull { it.id !in usados }
                    ?: return null
                usados += j.id
                JogadorEmCampo(j, s, 50)
            }
            if (emCampo.size < 11) return null
            val banco = elenco.filter { it.id !in usados }
                .sortedByDescending { it.geral }.take(9)
            return TimeEmCampo(id, nome, emCampo, tatica, banco)
        }

        // O anfitrião é o mandante nos dois aparelhos. Sem essa regra,
        // cada lado montaria a partida invertida e nada bateria.
        val meuTime = montar(meuClube, meuElenco, meusSlots, minhaTatica, 1)
            ?: return null
        val timeOutro = montar(clubeOutro, elencoOutro, slotsOutro, taticaOutro, 2)
            ?: return null

        val p = if (souAnfitriao) PartidaAoVivo(meuTime, timeOutro, Random(semente))
        else PartidaAoVivo(timeOutro, meuTime, Random(semente))

        partida = p
        lanceAtual = 0
        _estado.value = EstadoPartidaRede.EmJogo(0)
        return p
    }

    // ------------------------------------------------------ 4. LANCES

    /**
     * Avança um lance nos dois lados de forma sincronizada:
     * aplica os comandos agendados para este lance, simula, e de vez em
     * quando troca checksum.
     */
    suspend fun avancar(): Instante? {
        val p = partida ?: return null
        if (p.acabou) return null

        agenda.remove(lanceAtual)?.forEach { aplicarComando(p, it) }

        val i = p.passo()
        lanceAtual++
        _estado.value = EstadoPartidaRede.EmJogo(lanceAtual)

        if (lanceAtual % INTERVALO_CHECKSUM == 0) {
            meuChecksum = checksumDe(i)
            sala.enviar(Tipo.CHECKSUM, Corpos.checksum(lanceAtual, meuChecksum!!))
        }

        if (p.acabou) {
            _estado.value = EstadoPartidaRede.Terminada(
                i.golsMandante, i.golsVisitante)
            sala.enviar(
                Tipo.FIM,
                Corpos.fim(i.golsMandante, i.golsVisitante, checksumDe(i)),
                assinar = true,
            )
        }
        return i
    }

    /**
     * Estado resumido para comparação. Cobre só o que é observável e
     * determinístico — posição de bola em ponto flutuante entraria
     * arredondada, e é isso que a quantização em centésimos faz.
     */
    private fun checksumDe(i: Instante): String {
        val texto = buildString {
            append(i.minuto).append('|')
            append(i.golsMandante).append(':').append(i.golsVisitante).append('|')
            append(i.statsMandante.chutes).append(':')
            append(i.statsVisitante.chutes).append('|')
            append(i.statsMandante.passesCertos).append(':')
            append(i.statsVisitante.passesCertos).append('|')
            append(i.statsMandante.faltas).append(':')
            append(i.statsVisitante.faltas).append('|')
            append((i.bolaX * 100).toInt()).append(',')
            append((i.bolaY * 100).toInt()).append('|')
            append(i.pecas.firstOrNull { it.comABola }?.jogadorId ?: -1)
        }
        return Cripto.impressao(texto)
    }

    /** Recebe o checksum do outro e compara com o meu. */
    fun conferirChecksum(m: Mensagem) {
        val lance = m.corpo.optInt("lance")
        if (lance != lanceAtual) return    // ainda não chegamos lá
        val dele = m.corpo.optString("valor")
        if (meuChecksum != null && dele != meuChecksum) {
            _estado.value = EstadoPartidaRede.Dessincronizada(lance)
        }
    }

    // --------------------------------------------------- 5. COMANDOS

    /**
     * Agenda a mudança para um lance futuro nos DOIS aparelhos.
     *
     * O atraso é o que mantém o passo trancado: sem ele, quem mudou a
     * tática aplicaria antes do outro receber o aviso, e a partir daí as
     * duas simulações seguiriam caminhos diferentes.
     */
    suspend fun mudarTatica(nova: Tatica) {
        val alvo = lanceAtual + ATRASO_COMANDO
        val dados = JSONObject().apply {
            put("tatica", taticaParaJson(nova))
            put("mandante", souMandante)
        }
        agendar(alvo, "tatica", dados)
        sala.enviar(Tipo.COMANDO, Corpos.comando(alvo, "tatica", dados), assinar = true)
    }

    suspend fun substituir(sai: Int, entra: Int) {
        val alvo = lanceAtual + ATRASO_COMANDO
        val dados = JSONObject().apply {
            put("sai", sai); put("entra", entra); put("mandante", souMandante)
        }
        agendar(alvo, "substituicao", dados)
        sala.enviar(
            Tipo.COMANDO, Corpos.comando(alvo, "substituicao", dados), assinar = true)
    }

    fun receberComando(m: Mensagem) {
        if (!sala.assinaturaValida(m)) return
        val lance = m.corpo.optInt("lance")
        val acao = m.corpo.optString("acao")
        val dados = m.corpo.optJSONObject("dados") ?: return
        agendar(lance, acao, dados)
    }

    private fun agendar(lance: Int, acao: String, dados: JSONObject) {
        val entrada = JSONObject().apply {
            put("acao", acao); put("dados", dados)
        }
        agenda.getOrPut(lance.coerceAtLeast(lanceAtual + 1)) { mutableListOf() }
            .add(entrada)
    }

    private fun aplicarComando(p: PartidaAoVivo, entrada: JSONObject) {
        val dados = entrada.optJSONObject("dados") ?: return
        val doMandante = dados.optBoolean("mandante")

        when (entrada.optString("acao")) {
            "tatica" -> dados.optJSONObject("tatica")?.let {
                p.atualizarTatica(doMandante, taticaDeJson(it))
            }
            "substituicao" -> {
                val sai = dados.optInt("sai", -1)
                val entra = dados.optInt("entra", -1)
                if (sai >= 0 && entra >= 0) {
                    p.substituirPorId(doMandante, sai, entra)
                }
            }
        }
    }

    fun abortar(motivo: String) {
        _estado.value = EstadoPartidaRede.Abortada(motivo)
    }
}
