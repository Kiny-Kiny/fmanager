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
    // Escolha de clube no começo da carreira
    val precisaEscolherClube: Boolean = false,
    val ligas: List<Liga> = emptyList(),
    val ligaSelecionada: Liga? = null,
    val clubesDaLiga: List<Clube> = emptyList(),
    val tetoReputacao: Int = 66,
    val estiloHerdado: String = "",
    // Copa nacional, em paralelo à liga
    val partidasCopa: List<Partida> = emptyList(),
    val proximaCopa: Partida? = null,
    val faseDaCopa: String = "",
    val viveNaCopa: Boolean = false,
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

        val carreira = db.carreira().atual()
        if (carreira == null) {
            // Sem carreira ainda: o jogador escolhe o clube.
            _estado.value = EstadoJogo(
                carregando = false,
                precisaEscolherClube = true,
                ligas = db.ligas().todas().filter { db.clubes().porLiga(it.id).size >= 8 },
                tetoReputacao = reputacaoMaximaPara(1),
            )
            return@launch
        }

        slots = formacaoPadrao()
        carregarTudo()
    }

    fun selecionarLiga(liga: Liga?) = viewModelScope.launch {
        _estado.value = _estado.value.copy(
            ligaSelecionada = liga,
            clubesDaLiga = if (liga == null) emptyList()
            else db.clubes().porLiga(liga.id),
        )
    }

    /** Sorteia um clube elegível — o começo estilo Soccer Champs. */
    fun clubeAleatorio() = viewModelScope.launch {
        val teto = reputacaoMaximaPara(1)
        val elegiveis = _estado.value.ligas.flatMap { liga ->
            db.clubes().porLiga(liga.id).filter { it.reputacao <= teto }
        }
        val sorteado = elegiveis.randomOrNull() ?: return@launch
        escolherClube(sorteado).join()
    }

    fun escolherClube(clube: Clube) = viewModelScope.launch {
        _estado.value = _estado.value.copy(
            carregando = true, mensagem = "Montando a temporada...",
        )
        val carreira = Carreira(clubeId = clube.id)
        db.carreira().salvar(carreira)
        gerarTemporada(clube.ligaId, carreira.temporada)
        gerarCopa(clube.ligaId, carreira.temporada)
        slots = formacaoPadrao()

        // O clube já vem com um estilo, derivado do próprio elenco.
        val elenco = db.jogadores().elenco(clube.id)
        val (nomeEstilo, tatica) = if (elenco.isEmpty()) {
            "Equilibrado" to Tatica()
        } else {
            TaticaDoClube.derivarDe(
                velocidadeMedia = elenco.map { it.velocidade }.average().toInt(),
                passeMedio = elenco.map { it.passeBaixo }.average().toInt(),
                forcaMedia = elenco.map { it.contatoFisico }.average().toInt(),
                resistenciaMedia = elenco.map { it.resistencia }.average().toInt(),
                geralMedio = elenco.map { it.geral }.average().toInt(),
            )
        }

        _estado.value = _estado.value.copy(
            tatica = tatica, estiloHerdado = nomeEstilo,
        )
        carregarTudo()
    }

    /** Copa nacional em paralelo, com os melhores clubes da liga. */
    private suspend fun gerarCopa(ligaId: Int, temporada: Int) {
        val jaTem = db.partidas().daTemporada(temporada, Copa.ID_COPA_NACIONAL)
        if (jaTem.isNotEmpty()) return
        val clubes = db.clubes().porLiga(ligaId)
        db.partidas().inserirTodas(
            Copa.primeiraFase(clubes, Copa.ID_COPA_NACIONAL, temporada)
        )
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
        val partidasCopa = db.partidas()
            .daTemporada(carreira.temporada, Copa.ID_COPA_NACIONAL)
        val faseAtual = partidasCopa.filter { it.golsMandante == null }
            .minOfOrNull { it.rodada } ?: (partidasCopa.maxOfOrNull { it.rodada } ?: 0)

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
            estiloHerdado = _estado.value.estiloHerdado,
            tetoReputacao = reputacaoMaximaPara(carreira.temporada),
            tabela = Temporada.classificacao(clubesLiga, partidas),
            proximaPartida = db.partidas()
                .proximoJogo(carreira.temporada, clube.ligaId, clube.id),
            partidasCopa = partidasCopa,
            proximaCopa = db.partidas()
                .proximoJogo(carreira.temporada, Copa.ID_COPA_NACIONAL, clube.id),
            faseDaCopa = Copa.nomeDaFase(
                partidasCopa.filter { it.rodada == faseAtual }.size
            ),
            viveNaCopa = Copa.aindaNaCopa(clube.id, partidasCopa),
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
        // Reservas: quem sobrou do elenco, os melhores primeiro.
        val banco = elenco.filter { it.id !in usados }
            .sortedByDescending { it.geral }
            .take(9)
        return TimeEmCampo(clubeId, nome, emCampo, tatica, banco)
    }

    // ------------------------------------------ PARTIDA AO VIVO

    var partidaAoVivo: PartidaAoVivo? = null
        private set
    var souMandanteAoVivo = true
        private set
    var taticaDaPartida: Tatica = Tatica()
        private set

    private var registroAoVivo: Partida? = null

    /**
     * Prepara a partida assistida. Devolve null se não houver jogo,
     * elenco incompleto ou adversário sem time montável.
     */
    suspend fun prepararAoVivo(daCopa: Boolean): PartidaAoVivo? {
        val e = _estado.value
        val clube = e.clube ?: return null
        val partida = (if (daCopa) e.proximaCopa else e.proximaPartida) ?: return null

        val meuTime = montarTime(clube.id, clube.nome, e.elenco, e.tatica)
            ?: return null

        val advId = if (partida.mandanteId == clube.id)
            partida.visitanteId else partida.mandanteId
        val adv = db.clubes().porId(advId) ?: return null
        val timeAdv = montarTime(adv.id, adv.nome,
            db.jogadores().elenco(advId), Estilos.equilibrado) ?: return null

        souMandanteAoVivo = partida.mandanteId == clube.id
        taticaDaPartida = e.tatica
        registroAoVivo = partida

        val aoVivo = if (souMandanteAoVivo) PartidaAoVivo(meuTime, timeAdv)
        else PartidaAoVivo(timeAdv, meuTime)

        partidaAoVivo = aoVivo
        return aoVivo
    }

    /** Grava o resultado, simula o resto da rodada e avança a copa. */
    fun encerrarAoVivo() = viewModelScope.launch {
        val aoVivo = partidaAoVivo ?: return@launch
        val partida = registroAoVivo ?: return@launch
        val e = _estado.value
        val clube = e.clube ?: return@launch

        val r = aoVivo.resultado()
        db.partidas().atualizar(
            partida.copy(
                golsMandante = r.golsMandante,
                golsVisitante = r.golsVisitante,
            )
        )

        val daCopa = partida.ligaId == Copa.ID_COPA_NACIONAL
        if (daCopa) {
            simularFaseDaCopa(partida)
        } else {
            simularRodadaDaIA(partida, clube.id)
            db.carreira().salvar(
                (e.carreira ?: return@launch).copy(rodada = partida.rodada + 1)
            )
        }

        partidaAoVivo = null
        registroAoVivo = null
        _estado.value = _estado.value.copy(ultimoResultado = r)
        carregarTudo()
    }

    /** Resolve os outros confrontos da fase e cria a fase seguinte. */
    private suspend fun simularFaseDaCopa(partida: Partida) {
        val daFase = db.partidas()
            .daRodada(partida.temporada, partida.rodada, Copa.ID_COPA_NACIONAL)

        withContext(Dispatchers.Default) {
            daFase.filter { it.id != partida.id && it.golsMandante == null }
                .forEach { p ->
                    val casa = db.clubes().porId(p.mandanteId) ?: return@forEach
                    val fora = db.clubes().porId(p.visitanteId) ?: return@forEach
                    val tc = montarTime(casa.id, casa.nome,
                        db.jogadores().elenco(casa.id), Estilos.equilibrado)
                    val tf = montarTime(fora.id, fora.nome,
                        db.jogadores().elenco(fora.id), Estilos.equilibrado)
                    if (tc == null || tf == null) return@forEach
                    val res = motor.simular(tc, tf)
                    db.partidas().atualizar(p.copy(
                        golsMandante = res.golsMandante,
                        golsVisitante = res.golsVisitante,
                    ))
                }
        }

        val resolvidas = db.partidas()
            .daRodada(partida.temporada, partida.rodada, Copa.ID_COPA_NACIONAL)
        val proxima = Copa.proximaFase(
            resolvidas, Copa.ID_COPA_NACIONAL, partida.temporada,
        )
        if (proxima.isNotEmpty()) db.partidas().inserirTodas(proxima)
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
