package com.exemplo.fmanager

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.exemplo.fmanager.dados.*
import com.exemplo.fmanager.formacao.*
import com.exemplo.fmanager.motor.*
import com.exemplo.fmanager.sistemas.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EstadoJogo(
    val carregando: Boolean = true,
    val mensagem: String = "Preparando banco de dados...",
    val carreira: Carreira? = null,
    val clube: Clube? = null,
    val elenco: List<Jogador> = emptyList(),
    val contratos: Map<Int, Contrato> = emptyMap(),
    val escalacao: List<Slot> = emptyList(),
    val tatica: Tatica = Tatica(),
    val tabela: List<LinhaTabela> = emptyList(),
    val proximaPartida: Partida? = null,
    val ultimoResultado: Resultado? = null,
    val entrosamento: Entrosamento? = null,
    val caixa: Long = 0,
    val folha: Long = 0,
)

class JogoViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.obter(app)
    private val motor = MotorPartida()

    private val _estado = MutableStateFlow(EstadoJogo())
    val estado: StateFlow<EstadoJogo> = _estado.asStateFlow()

    /** Slots da formação. Mutáveis: o editor mexe neles direto. */
    var slots: List<Slot> = emptyList()
        private set

    init { iniciar() }

    private fun iniciar() = viewModelScope.launch {
        _estado.value = _estado.value.copy(
            carregando = true, mensagem = "Conectando na base de jogadores...",
        )

        if (db.jogadores().total() == 0) {
            // Fonte principal: a API. Ela dá atributos, PlayStyles e a
            // imagem da carta de uma vez só.
            val viaApi = ImportadorApi.importar(db) { p ->
                _estado.value = _estado.value.copy(
                    mensagem = "Importando ${p.ligaAtual}\n" +
                            "${p.ligasFeitas}/${p.ligasTotal} ligas · " +
                            "${p.jogadores} jogadores",
                )
            }

            // Reserva: se a API estiver fora do ar, cai pro CSV local.
            if (viaApi.isFailure || db.jogadores().total() == 0) {
                _estado.value = _estado.value.copy(
                    mensagem = "API indisponível. Tentando o CSV local...",
                )
                runCatching {
                    ImportadorCsv.importarSeNecessario(getApplication(), db)
                }.onFailure {
                    _estado.value = _estado.value.copy(
                        carregando = false,
                        mensagem = "Não foi possível importar os jogadores.\n\n" +
                                "A API (api.msmc.cc) não respondeu e não há " +
                                "assets/jogadores.csv como reserva.\n\n" +
                                "Confira sua conexão ou adicione o CSV.",
                    )
                    return@launch
                }
            }
        }

        var carreira = db.carreira().atual()
        if (carreira == null) {
            // Primeira execução: começa num clube de reputação média.
            val clube = db.clubes().todos(300).let { lista ->
                lista.getOrNull(lista.size / 3) ?: lista.first()
            }
            carreira = Carreira(clubeId = clube.id)
            db.carreira().salvar(carreira)
            gerarTemporada(clube.ligaId, carreira.temporada)
        }

        slots = formacaoPadrao()
        carregarTudo()
    }

    private suspend fun gerarTemporada(ligaId: Int, temporada: Int) {
        if (db.partidas().total(temporada) > 0) return
        val clubes = db.clubes().porLiga(ligaId)
        db.partidas().inserirTodas(
            Temporada.gerarCalendario(clubes, ligaId, temporada)
        )
    }

    fun carregarTudo() = viewModelScope.launch {
        val carreira = db.carreira().atual() ?: return@launch
        val clube = db.clubes().porId(carreira.clubeId) ?: return@launch

        val elenco = db.jogadores().elenco(clube.id)
        val contratos = db.contratos().doClube(clube.id).associateBy { it.jogadorId }
        val clubesLiga = db.clubes().porLiga(clube.ligaId)
        val partidas = db.partidas().daTemporada(carreira.temporada, clube.ligaId)

        // Preenche a escalação automaticamente com os melhores para cada papel.
        if (slots.isNotEmpty() && slots.all { it.nome.startsWith("Jogador") }) {
            autoEscalar(elenco)
        }

        _estado.value = EstadoJogo(
            carregando = false,
            mensagem = "",
            carreira = carreira,
            clube = clube,
            elenco = elenco,
            contratos = contratos,
            escalacao = slots,
            tatica = _estado.value.tatica,
            tabela = Temporada.classificacao(clubesLiga, partidas),
            proximaPartida = db.partidas()
                .proximoJogo(carreira.temporada, clube.ligaId, clube.id),
            ultimoResultado = _estado.value.ultimoResultado,
            entrosamento = CalculadoraEntrosamento.calcular(
                slots, elenco.associateBy { it.id },
            ),
            caixa = clube.caixaEur,
            folha = db.contratos().folhaSalarial(clube.id),
        )
    }

    /** Escala o melhor jogador disponível para cada papel da formação. */
    fun autoEscalar(elenco: List<Jogador> = _estado.value.elenco) {
        if (elenco.isEmpty()) return
        val usados = mutableSetOf<Int>()

        // Ordena os slots pelos mais exigentes primeiro (goleiro, depois
        // quanto maior o overall médio necessário).
        slots.sortedBy { if (it.papelPrincipal == Papel.GOL) 0 else 1 }.forEach { slot ->
            val papel = slot.papelPrincipal
            val melhor = elenco
                .filter { it.id !in usados }
                .filter { (papel == Papel.GOL) == (it.posicao.uppercase() == "GK") }
                .maxByOrNull { it.rendimentoEm(papel) }
                ?: elenco.firstOrNull { it.id !in usados }
                ?: return@forEach

            usados += melhor.id
            slot.nome = melhor.nome
            slot.jogadorId = melhor.id
        }
        _estado.value = _estado.value.copy(escalacao = slots)
    }

    fun definirTatica(t: Tatica) {
        _estado.value = _estado.value.copy(tatica = t)
    }

    // ------------------------------------------------- DIA DE JOGO

    fun jogarProximaPartida() = viewModelScope.launch {
        val e = _estado.value
        val partida = e.proximaPartida ?: return@launch
        val clube = e.clube ?: return@launch

        val resultado = withContext(Dispatchers.Default) {
            val meuTime = montarTime(clube.id, clube.nome, e.elenco, e.tatica)
                ?: return@withContext null

            val advId = if (partida.mandanteId == clube.id)
                partida.visitanteId else partida.mandanteId
            val adv = db.clubes().porId(advId) ?: return@withContext null
            val elencoAdv = db.jogadores().elenco(advId)
            val timeAdv = montarTime(adv.id, adv.nome, elencoAdv, Estilos.equilibrado)
                ?: return@withContext null

            if (partida.mandanteId == clube.id)
                motor.simular(meuTime, timeAdv)
            else
                motor.simular(timeAdv, meuTime)
        } ?: return@launch

        db.partidas().atualizar(
            partida.copy(
                golsMandante = resultado.golsMandante,
                golsVisitante = resultado.golsVisitante,
            )
        )

        // Simula o resto da rodada para a tabela andar junto.
        simularRodadaDaIA(partida, clube.id)

        db.carreira().salvar(
            (e.carreira ?: return@launch).copy(rodada = partida.rodada + 1)
        )

        _estado.value = _estado.value.copy(ultimoResultado = resultado)
        carregarTudo()
    }

    private suspend fun simularRodadaDaIA(partida: Partida, meuClubeId: Int) {
        val outras = db.partidas()
            .daRodada(partida.temporada, partida.rodada, partida.ligaId)
            .filter { it.id != partida.id && it.golsMandante == null }

        withContext(Dispatchers.Default) {
            outras.forEach { p ->
                val casa = db.clubes().porId(p.mandanteId) ?: return@forEach
                val fora = db.clubes().porId(p.visitanteId) ?: return@forEach
                val tc = montarTime(casa.id, casa.nome,
                    db.jogadores().elenco(casa.id), Estilos.equilibrado)
                val tf = montarTime(fora.id, fora.nome,
                    db.jogadores().elenco(fora.id), Estilos.equilibrado)
                if (tc == null || tf == null) return@forEach

                val r = motor.simular(tc, tf)
                db.partidas().atualizar(
                    p.copy(golsMandante = r.golsMandante, golsVisitante = r.golsVisitante)
                )
            }
        }
    }

    /** Monta os 11 em campo. Para a IA, usa uma 4-3-3 automática. */
    private fun montarTime(
        clubeId: Int, nome: String, elenco: List<Jogador>, tatica: Tatica,
    ): TimeEmCampo? {
        if (elenco.size < 11) return null

        val slotsDoTime = if (clubeId == _estado.value.clube?.id) slots
        else formacaoPadrao()

        val entrosamento = CalculadoraEntrosamento.calcular(
            slotsDoTime, elenco.associateBy { it.id },
        )
        val usados = mutableSetOf<Int>()
        val emCampo = slotsDoTime.map { slot ->
            val j = slot.jogadorId?.let { id -> elenco.find { it.id == id } }
                ?.takeIf { it.id !in usados }
                ?: elenco.filter { it.id !in usados }
                    .maxByOrNull { it.rendimentoEm(slot.papelPrincipal) }
                ?: return null
            usados += j.id
            JogadorEmCampo(j, slot, entrosamento.porJogador[slot.id] ?: 50)
        }
        return TimeEmCampo(clubeId, nome, emCampo, tatica)
    }

    // --------------------------------------------------- TREINO

    fun treinarElenco(foco: FocoTreino, intensidade: Intensidade) =
        viewModelScope.launch {
            val elenco = _estado.value.elenco
            val atualizados = withContext(Dispatchers.Default) {
                elenco.map { Treino.semana(it, foco, intensidade).jogador }
            }
            db.jogadores().inserirTodos(atualizados)
            carregarTudo()
        }

    // ---------------------------------------------- CONTRATAÇÕES

    suspend fun buscarMercado(
        posicao: String?, idadeMax: Int?, valorMax: Long?, geralMin: Int,
    ): List<Jogador> = db.jogadores().buscar(
        posicao = posicao, idadeMax = idadeMax, valorMax = valorMax,
        geralMin = geralMin, limite = 60,
    )

    suspend fun fazerProposta(alvo: Jogador, valor: Long, salario: Long): RespostaClube {
        val e = _estado.value
        val meuClube = e.clube ?: return RespostaClube(RespostaProposta.RECUSADA)
        val contrato = db.contratos().doJogador(alvo.id)
        val vendedor = alvo.clubeId?.let { db.clubes().porId(it) }

        val proposta = Proposta(alvo.id, meuClube.id, valor, salario, 4)

        val (podeBancar, motivo) = Transferencias.podeBancar(meuClube, e.folha, proposta)
        if (!podeBancar) return RespostaClube(RespostaProposta.RECUSADA, motivo = motivo)

        val resposta = Transferencias.avaliar(
            proposta, alvo, contrato, vendedor, meuClube,
            e.carreira?.temporada ?: 1,
        )

        if (resposta.resposta == RespostaProposta.ACEITA) {
            db.jogadores().inserirTodos(listOf(alvo.copy(clubeId = meuClube.id)))
            db.contratos().salvar(
                Contrato(
                    jogadorId = alvo.id, clubeId = meuClube.id,
                    salarioSemanalEur = salario,
                    terminaEmTemporada = (e.carreira?.temporada ?: 1) + 4,
                )
            )
            db.clubes().atualizar(meuClube.copy(caixaEur = meuClube.caixaEur - valor))
            carregarTudo()
        }
        return resposta
    }

    fun precoDe(alvo: Jogador): Long {
        val e = _estado.value
        return Transferencias.precoPedido(
            alvo, null, null, e.clube ?: return alvo.valorEur,
            e.carreira?.temporada ?: 1,
        )
    }

    fun salarioDe(alvo: Jogador): Long {
        val clube = _estado.value.clube ?: return alvo.salarioEur
        return Transferencias.salarioExigido(alvo, clube)
    }
}

/** Formação inicial do jogador e das equipes controladas pela IA. */
fun formacaoPadrao(): List<Slot> = Formacoes.estaticas[2].criarSlots()
