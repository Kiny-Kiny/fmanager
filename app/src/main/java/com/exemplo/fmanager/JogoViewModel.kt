package com.exemplo.fmanager

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.exemplo.fmanager.dados.*
import com.exemplo.fmanager.formacao.*
import com.exemplo.fmanager.motor.*
import com.exemplo.fmanager.rede.*
import com.exemplo.fmanager.sistemas.*
import kotlin.random.Random
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
    // Painel estilo modo carreira
    val expectativa: Expectativa? = null,
    val noticias: List<Noticia> = emptyList(),
    val forma: List<Char> = emptyList(),
    val artilheirosDoClube: List<Artilheiro> = emptyList(),
    val artilheirosDaLiga: List<Artilheiro> = emptyList(),
    val posicao: Int = 0,
    val totalRodadas: Int = 0,
    // Olheiro e DNA (ideias do PyScoutFM)
    val dnaDoClube: Dna = Dnas.intensidade,
    val niveisObservacao: Map<Int, Int> = emptyMap(),
    val candidatosOlheiro: List<Jogador> = emptyList(),
    val custoOlheiroSemanal: Long = 0,
    // Semelhança, moneyball e desenvolvimento
    val desenvolvimento: List<Desenvolvimento> = emptyList(),
    val resumoDesenvolvimento: String = "",
    val garimpo: List<Moneyball.Achado> = emptyList(),
    val parecidos: List<Semelhanca.Parecido> = emptyList(),
    val referenciaSemelhanca: Jogador? = null,
    val calibracao: String = "",
    val calibrando: Boolean = false,
    // Torneios customizados e palmarés
    val torneios: List<Torneio> = emptyList(),
    val torneioAberto: Torneio? = null,
    val gruposDoTorneio: List<Pair<String, List<LinhaTabela>>> = emptyList(),
    val eliminatoriaDoTorneio: List<Partida> = emptyList(),
    val proximaDoTorneio: Partida? = null,
    val titulos: List<Titulo> = emptyList(),
    val clubesParaTorneio: List<Clube> = emptyList(),
    val inscritosNoTorneio: Set<Int> = emptySet(),
    // Gestão humana
    val climaVestiario: Int = 55,
    val insatisfeitos: List<Pair<Jogador, EstadoMoral>> = emptyList(),
    val comissao: List<MembroComissao> = emptyList(),
    val folhaComissao: Long = 0,
    val perguntasDaColetiva: List<Pergunta> = emptyList(),
    val ultimaConversa: String = "",
    // Modo online
    val pvpServidor: String = "",
    val pvpConectado: Boolean = false,
    val pvpUsuarioId: String = "",
    val pvpApelido: String = "",
    val pvpPontos: Int = Elo.INICIAL,
    val pvpDivisao: String = "Divisão 4",
    val pvpVitorias: Int = 0,
    val pvpEmpates: Int = 0,
    val pvpDerrotas: Int = 0,
    val pvpCustoElenco: Long = 0,
    val pvpDesafios: List<Desafio> = emptyList(),
    val pvpMeusDesafios: List<Desafio> = emptyList(),
    val pvpClassificacao: List<RatingTecnico> = emptyList(),
    val pvpAviso: String = "",
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

        val tabelaLiga = Temporada.classificacao(clubesLiga, partidas)
        val comissaoAtual = db.comissao().doClube(clube.id)
        // O motor lê a moral daqui, sem tocar no banco durante a partida.
        moraisConhecidas.clear()
        contratos.forEach { (id, c) -> moraisConhecidas[id] = c.moral }
        val folha = db.contratos().folhaSalarial(clube.id)
        val expectativa = Diretoria.avaliar(
            clube = clube, todosDaLiga = clubesLiga, tabela = tabelaLiga,
            rodadasJogadas = partidas.count { it.golsMandante != null } /
                    clubesLiga.size.coerceAtLeast(1),
            totalRodadas = partidas.maxOfOrNull { it.rodada } ?: 1,
            viveNaCopa = Copa.aindaNaCopa(clube.id, partidasCopa),
        )

        // Preenche a escalação automaticamente com os melhores para cada papel.
        if (slots.isNotEmpty() && slots.all { it.nome.startsWith("Jogador") }) {
            autoEscalar(elenco)
        }

        tirarRetrato(carreira.temporada, elenco)

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
            tabela = tabelaLiga,
            proximaPartida = db.partidas()
                .proximoJogo(carreira.temporada, clube.ligaId, clube.id),
            partidasCopa = partidasCopa,
            proximaCopa = db.partidas()
                .proximoJogo(carreira.temporada, Copa.ID_COPA_NACIONAL, clube.id),
            faseDaCopa = Copa.nomeDaFase(
                partidasCopa.filter { it.rodada == faseAtual }.size
            ),
            viveNaCopa = Copa.aindaNaCopa(clube.id, partidasCopa),
            expectativa = expectativa,
            noticias = CaixaDeEntrada.gerar(
                clube = clube, temporada = carreira.temporada,
                rodada = carreira.rodada, elenco = elenco,
                contratos = contratos, tabela = tabelaLiga,
                expectativa = expectativa,
                viveNaCopa = Copa.aindaNaCopa(clube.id, partidasCopa),
                faseCopa = Copa.nomeDaFase(
                    partidasCopa.filter { it.rodada == faseAtual }.size),
                caixa = clube.caixaEur, folha = folha,
            ),
            forma = formaRecente(partidas, clube.id),
            artilheirosDoClube = db.estatisticas()
                .doClube(carreira.temporada, clube.id),
            artilheirosDaLiga = db.estatisticas()
                .artilheiros(carreira.temporada),
            posicao = tabelaLiga.indexOfFirst { it.clubeId == clube.id } + 1,
            dnaDoClube = _estado.value.dnaDoClube.takeIf {
                _estado.value.clube != null
            } ?: Dnas.sugerirPara(elenco),
            niveisObservacao = db.observacoes().ativas()
                .associate { it.jogadorId to it.nivel },
            custoOlheiroSemanal = Olheiro.custoDe(db.observacoes().ativas()),
            climaVestiario = Moral.climaDoVestiario(elenco, contratos),
            insatisfeitos = Moral.insatisfeitos(elenco, contratos),
            comissao = comissaoAtual,
            folhaComissao = Comissao.folhaSemanal(comissaoAtual),
            perguntasDaColetiva = Coletiva.perguntas(
                posicao = tabelaLiga.indexOfFirst { it.clubeId == clube.id } + 1,
                posicaoAlvo = expectativa.posicaoAlvo,
                forma = formaRecente(partidas, clube.id),
                viveNaCopa = Copa.aindaNaCopa(clube.id, partidasCopa),
                climaVestiario = Moral.climaDoVestiario(elenco, contratos),
                proximoEmCasa = db.partidas()
                    .proximoJogo(carreira.temporada, clube.ligaId, clube.id)
                    ?.mandanteId == clube.id,
            ),
            totalRodadas = partidas.maxOfOrNull { it.rodada } ?: 0,
            ultimoResultado = _estado.value.ultimoResultado,
            entrosamento = CalculadoraEntrosamento.calcular(
                slots, elenco.associateBy { it.id },
            ),
            caixa = clube.caixaEur,
            folha = folha,
        )
    }

    /** Sequência de V/E/D nos últimos jogos do clube. */
    private fun formaRecente(partidas: List<Partida>, clubeId: Int): List<Char> =
        partidas
            .filter {
                it.golsMandante != null &&
                        (it.mandanteId == clubeId || it.visitanteId == clubeId)
            }
            .sortedBy { it.rodada }
            .takeLast(5)
            .map { p ->
                val meus = if (p.mandanteId == clubeId) p.golsMandante!! else p.golsVisitante!!
                val deles = if (p.mandanteId == clubeId) p.golsVisitante!! else p.golsMandante!!
                when {
                    meus > deles -> 'V'
                    meus == deles -> 'E'
                    else -> 'D'
                }
            }

    /** Grava gols, assistências, notas e cartões na tabela da temporada. */
    private suspend fun registrarEstatisticas(r: Resultado, temporada: Int) {
        val idsClube = _estado.value.elenco.map { it.id }.toSet()
        val clubeId = _estado.value.clube?.id ?: return

        val participantes = (r.titularesMandante + r.titularesVisitante)
            .filter { it in idsClube }.distinct()

        val atualizadas = participantes.map { id ->
            val anterior = db.estatisticas().de(id, temporada)
                ?: EstatisticaJogador(id, temporada, clubeId)
            anterior.copy(
                jogos = anterior.jogos + 1,
                gols = anterior.gols + (r.golsPorJogador[id] ?: 0),
                assistencias = anterior.assistencias +
                        (r.assistenciasPorJogador[id] ?: 0),
                amarelos = anterior.amarelos + (if (id in r.amarelosPorJogador) 1 else 0),
                vermelhos = anterior.vermelhos + (if (id in r.vermelhosPorJogador) 1 else 0),
                somaNotas = anterior.somaNotas + (r.notas[id] ?: 6f),
            )
        }
        db.estatisticas().salvarTodas(atualizadas)
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
            clubesConhecidos[adv.id] = adv
            val elencoAdv = db.jogadores().elenco(advId)
            val timeAdv = montarTime(
                adv.id, adv.nome, elencoAdv,
                TreinadorIA.taticaPara(adv, elencoAdv),
            ) ?: return@withContext null

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
                clubesConhecidos[casa.id] = casa
                clubesConhecidos[fora.id] = fora
                val ec = db.jogadores().elenco(casa.id)
                val ef = db.jogadores().elenco(fora.id)
                val tc = montarTime(casa.id, casa.nome, ec,
                    TreinadorIA.taticaPara(casa, ec))
                val tf = montarTime(fora.id, fora.nome, ef,
                    TreinadorIA.taticaPara(fora, ef))
                if (tc == null || tf == null) return@forEach

                val r = motor.simular(tc, tf)
                db.partidas().atualizar(
                    p.copy(golsMandante = r.golsMandante, golsVisitante = r.golsVisitante)
                )
            }
        }
    }

    /** Moral por jogador, para o motor consultar sem ir ao banco. */
    private val moraisConhecidas = mutableMapOf<Int, Int>()

    /** Cache de escalação da IA: o mesmo clube joga igual na temporada. */
    private val escalacoesIA = mutableMapOf<Int, List<Slot>>()

    private fun slotsDaIA(clubeId: Int, elenco: List<Jogador>): List<Slot> =
        escalacoesIA.getOrPut(clubeId) {
            val clube = clubesConhecidos[clubeId]
            if (clube == null) formacaoPadrao()
            else TreinadorIA.escalar(clube, elenco)
        }

    /** Clubes já carregados, para a IA não precisar ir ao banco no meio
     *  de um cálculo síncrono. */
    private val clubesConhecidos = mutableMapOf<Int, Clube>()

    /** Monta os 11 em campo, com a formação e o estilo de cada time. */
    private fun montarTime(
        clubeId: Int, nome: String, elencoBruto: List<Jogador>, tatica: Tatica,
        inscritos: Set<Int>? = null,
    ): TimeEmCampo? {
        // Lista fechada de torneio: quem não está inscrito não joga.
        val elenco = if (inscritos.isNullOrEmpty()) elencoBruto
        else elencoBruto.filter { it.id in inscritos }
        if (elenco.size < 11) return null

        // O meu time usa a minha formação. Cada adversário usa a
        // formação e o estilo que o próprio elenco pede.
        val meuClube = clubeId == _estado.value.clube?.id
        val slotsDoTime = if (meuClube) slots else slotsDaIA(clubeId, elenco)

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
            JogadorEmCampo(
                jogador = j, slot = slot,
                entrosamento = entrosamento.porJogador[slot.id] ?: 50,
                moral = moraisConhecidas[j.id] ?: 55,
            )
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
        clubesConhecidos[adv.id] = adv
        val elencoAdv = db.jogadores().elenco(advId)
        val timeAdv = montarTime(
            adv.id, adv.nome, elencoAdv,
            TreinadorIA.taticaPara(adv, elencoAdv),
        ) ?: return null

        souMandanteAoVivo = partida.mandanteId == clube.id
        taticaDaPartida = e.tatica
        registroAoVivo = partida

        val aoVivo = if (souMandanteAoVivo) PartidaAoVivo(meuTime, timeAdv)
        else PartidaAoVivo(timeAdv, meuTime)
        // A IA conduz o lado que não é o seu.
        aoVivo.definirLadoDaIA(iaEhMandante = !souMandanteAoVivo)

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

        registrarEstatisticas(r, partida.temporada)

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
                    clubesConhecidos[casa.id] = casa
                    clubesConhecidos[fora.id] = fora
                    val ec = db.jogadores().elenco(casa.id)
                    val ef = db.jogadores().elenco(fora.id)
                    val tc = montarTime(casa.id, casa.nome, ec,
                        TreinadorIA.taticaPara(casa, ec))
                    val tf = montarTime(fora.id, fora.nome, ef,
                        TreinadorIA.taticaPara(fora, ef))
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

    // ------------------------------------------------ MODO ONLINE

    private var backend: Backend? = null
    private var pvp: Pvp? = null

    /** Os 11 do PVP e a formação usada nele, separados da carreira. */
    private var elencoPvp: List<Jogador> = emptyList()
    private var slotsPvp: List<Slot> = formacaoPadrao()

    fun testarServidor(url: String) = viewModelScope.launch {
        val b = Backend(url.trimEnd('/'))
        b.testar()
            .onSuccess {
                _estado.value = _estado.value.copy(
                    pvpServidor = url, pvpAviso = "Servidor respondeu: $it")
            }
            .onFailure {
                _estado.value = _estado.value.copy(
                    pvpAviso = "Não respondeu: ${it.message}")
            }
    }

    fun criarContaPvp(url: String, email: String, senha: String, apelido: String) =
        viewModelScope.launch {
            val b = Backend(url.trimEnd('/'))
            b.registrar(email, senha, apelido)
                .onSuccess { entrarNoPvp(url, email, senha) }
                .onFailure {
                    _estado.value = _estado.value.copy(
                        pvpAviso = "Falha ao criar conta: ${it.message}")
                }
        }

    fun entrarNoPvp(url: String, email: String, senha: String) =
        viewModelScope.launch {
            val b = Backend(url.trimEnd('/'))
            b.entrar(email, senha)
                .onSuccess {
                    backend = b
                    pvp = Pvp(b)
                    // Elenco inicial do PVP: sugestão dentro do orçamento.
                    sugerirElencoPvp().join()
                    _estado.value = _estado.value.copy(
                        pvpServidor = url,
                        pvpConectado = true,
                        pvpUsuarioId = b.usuarioId ?: "",
                        pvpApelido = b.apelido ?: "Técnico",
                        pvpAviso = "",
                    )
                    buscarClassificacao()
                }
                .onFailure {
                    _estado.value = _estado.value.copy(
                        pvpAviso = "Não entrou: ${it.message}")
                }
        }

    fun sairDoPvp() {
        backend?.sair()
        backend = null
        pvp = null
        _estado.value = _estado.value.copy(
            pvpConectado = false, pvpAviso = "", pvpDesafios = emptyList())
    }

    /**
     * Sugestão de elenco dentro do orçamento.
     *
     * Busca no acervo inteiro, não no seu clube: o PVP é orçamento igual
     * para todos, então o universo tem que ser o mesmo para todos.
     */
    fun sugerirElencoPvp() = viewModelScope.launch {
        val universo = withContext(Dispatchers.IO) {
            db.jogadores().buscar(geralMin = 62, limite = 800)
        }
        slotsPvp = formacaoPadrao()
        elencoPvp = withContext(Dispatchers.Default) {
            MontadorPvp.sugerir(universo, slotsPvp)
        }
        _estado.value = _estado.value.copy(
            pvpCustoElenco = Precos.totalDe(elencoPvp),
            pvpAviso = "Elenco sugerido: ${elencoPvp.size} jogadores.",
        )
    }

    fun publicarDesafio() = viewModelScope.launch {
        val p = pvp ?: return@launch
        p.publicarDesafio(
            elenco = elencoPvp, slots = slotsPvp,
            tatica = _estado.value.tatica,
            nomeDoTime = _estado.value.pvpApelido + " FC",
        )
            .onSuccess {
                _estado.value = _estado.value.copy(
                    pvpAviso = "Desafio publicado. Alguém vai aceitar.")
                buscarMeusDesafios()
            }
            .onFailure {
                _estado.value = _estado.value.copy(
                    pvpAviso = it.message ?: "Falha ao publicar.")
            }
    }

    fun buscarDesafios() = viewModelScope.launch {
        val p = pvp ?: return@launch
        p.desafiosAbertos(_estado.value.pvpUsuarioId, _estado.value.pvpPontos)
            .onSuccess { _estado.value = _estado.value.copy(pvpDesafios = it) }
            .onFailure {
                _estado.value = _estado.value.copy(
                    pvpAviso = "Falha ao buscar: ${it.message}")
            }
    }

    fun buscarMeusDesafios() = viewModelScope.launch {
        val p = pvp ?: return@launch
        p.meusDesafios(_estado.value.pvpUsuarioId)
            .onSuccess { _estado.value = _estado.value.copy(pvpMeusDesafios = it) }
    }

    fun buscarClassificacao() = viewModelScope.launch {
        val p = pvp ?: return@launch
        p.classificacao().onSuccess { lista ->
            val eu = lista.firstOrNull { it.id == _estado.value.pvpUsuarioId }
            _estado.value = _estado.value.copy(
                pvpClassificacao = lista,
                pvpPontos = eu?.pontos ?: Elo.INICIAL,
                pvpDivisao = eu?.divisao ?: "Divisão 4",
                pvpVitorias = eu?.vitorias ?: 0,
                pvpEmpates = eu?.empates ?: 0,
                pvpDerrotas = eu?.derrotas ?: 0,
            )
        }
    }

    fun aceitarDesafio(d: Desafio) = viewModelScope.launch {
        val p = pvp ?: return@launch
        p.aceitar(d, elencoPvp, slotsPvp, _estado.value.tatica,
            _estado.value.pvpApelido + " FC")
            .onSuccess {
                _estado.value = _estado.value.copy(
                    pvpAviso = "Desafio aceito. Simule para valer o resultado.")
                buscarMeusDesafios()
            }
            .onFailure {
                _estado.value = _estado.value.copy(
                    pvpAviso = it.message ?: "Falha ao aceitar.")
            }
    }

    /**
     * Simula e envia. A partida roda no seu aparelho e no do adversário
     * com a MESMA semente — o servidor só compara as assinaturas.
     */
    fun simularDesafio(d: Desafio) = viewModelScope.launch {
        val p = pvp ?: return@launch
        val partida = p.montarPartida(d)
        if (partida == null) {
            _estado.value = _estado.value.copy(
                pvpAviso = "Não foi possível montar a partida.")
            return@launch
        }

        val resultado = withContext(Dispatchers.Default) {
            partida.pularParaOFim()
            partida.resultado()
        }

        val souDono = d.donoId == _estado.value.pvpUsuarioId
        p.enviarResultado(d, resultado, souDono)
            .onSuccess { estado ->
                val meus = if (souDono) resultado.golsMandante
                else resultado.golsVisitante
                val dele = if (souDono) resultado.golsVisitante
                else resultado.golsMandante

                if (estado == EstadoDesafio.CONFIRMADO) {
                    p.registrarResultado(
                        meusPontos = _estado.value.pvpPontos,
                        pontosDoOutro = d.donoPontos,
                        meusGols = meus, golsDele = dele,
                        vitoriasAtuais = _estado.value.pvpVitorias,
                        empatesAtuais = _estado.value.pvpEmpates,
                        derrotasAtuais = _estado.value.pvpDerrotas,
                    )
                    buscarClassificacao()
                }

                _estado.value = _estado.value.copy(
                    ultimoResultado = resultado,
                    pvpAviso = when (estado) {
                        EstadoDesafio.CONFIRMADO ->
                            "Resultado confirmado: $meus x $dele."
                        EstadoDesafio.EM_DISPUTA ->
                            "Os resultados divergiram. Partida anulada."
                        else -> "Resultado enviado: $meus x $dele. " +
                                "Aguardando o adversário simular."
                    },
                )
                buscarMeusDesafios()
            }
            .onFailure {
                _estado.value = _estado.value.copy(
                    pvpAviso = "Falha ao enviar: ${it.message}")
            }
    }

    // ------------------------------------------ GESTÃO HUMANA

    fun conversar(jogador: Jogador, assunto: AssuntoConversa) =
        viewModelScope.launch {
            val contrato = db.contratos().doJogador(jogador.id) ?: return@launch
            val stats = db.estatisticas()
                .de(jogador.id, _estado.value.carreira?.temporada ?: 1)

            val r = ConversaComJogador.conversar(
                assunto = assunto,
                jogador = jogador,
                contrato = contrato,
                minutosRecentes = (stats?.jogos ?: 0) * 70,
                notaMedia = stats?.notaMedia ?: 0f,
                melhorDoElenco = _estado.value.elenco
                    .maxByOrNull { it.geral }?.id == jogador.id,
            )

            db.contratos().salvar(
                contrato.copy(moral = (contrato.moral + r.deltaMoral).coerceIn(0, 100))
            )
            _estado.value = _estado.value.copy(ultimaConversa = r.texto)
            carregarTudo()
        }

    fun assuntosDisponiveis(jogador: Jogador): List<AssuntoConversa> {
        val c = _estado.value.contratos[jogador.id] ?: return emptyList()
        return ConversaComJogador.assuntosPara(
            jogador, c, minutosRecentes = 200, notaMedia = 6.5f,
            temporadaAtual = _estado.value.carreira?.temporada ?: 1,
        )
    }

    /**
     * Responder a imprensa afeta três coisas ao mesmo tempo: o vestiário,
     * a diretoria e a torcida. Nenhum tom agrada os três.
     */
    fun responderColetiva(pergunta: Pergunta, tom: TomDaResposta) =
        viewModelScope.launch {
            val e = _estado.value
            val clube = e.clube ?: return@launch
            val vaiBem = e.posicao in 1..(e.expectativa?.posicaoAlvo ?: 20)

            val efeito = Coletiva.responder(pergunta, tom, vaiBem)

            // Moral do elenco inteiro se move junto.
            val contratos = db.contratos().doClube(clube.id)
            db.contratos().salvarTodos(contratos.map {
                it.copy(moral = (it.moral + efeito.deltaMoralElenco)
                    .coerceIn(0, 100))
            })

            _estado.value = _estado.value.copy(ultimaConversa = efeito.comentario)
            carregarTudo()
        }

    fun candidatosParaCargo(cargo: Cargo): List<MembroComissao> {
        val clube = _estado.value.clube ?: return emptyList()
        return Comissao.candidatos(cargo, clube.reputacao, clube.id.toLong())
    }

    fun contratarStaff(m: MembroComissao) = viewModelScope.launch {
        val clube = _estado.value.clube ?: return@launch
        // Um cargo, uma pessoa: contratar substitui quem estava.
        db.comissao().demitirDoCargo(clube.id, m.cargo.name)
        db.comissao().contratar(m.copy(id = 0, clubeId = clube.id))
        carregarTudo()
    }

    fun demitirStaff(m: MembroComissao) = viewModelScope.launch {
        db.comissao().demitir(m.id)
        carregarTudo()
    }

    /** Renovação de contrato. Moral baixa fecha a porta. */
    suspend fun renovar(jogador: Jogador, aumento: Float): String {
        val contrato = db.contratos().doJogador(jogador.id)
            ?: return "Contrato não encontrado."
        val temporada = _estado.value.carreira?.temporada ?: 1

        if (!Moral.aceitaRenovar(contrato.moral, aumento)) {
            return "${jogador.nome} recusou. " +
                    if (contrato.moral < 30)
                        "Ele está insatisfeito e não quer ficar."
                    else "A proposta não é suficiente."
        }

        val novoSalario = (contrato.salarioSemanalEur * aumento).toLong()
        db.contratos().salvar(
            contrato.copy(
                salarioSemanalEur = novoSalario,
                terminaEmTemporada = temporada + 4,
                moral = (contrato.moral + 6).coerceAtMost(100),
            )
        )
        carregarTudo()
        return "${jogador.nome} renovou até T${temporada + 4} por " +
                "${novoSalario / 1000}K por semana."
    }

    // ----------------------------------------------- TORNEIOS

    /** Clubes disponíveis para montar um torneio: os melhores de todas
     *  as ligas, para dar de escolher sem listar 700 nomes. */
    fun carregarClubesParaTorneio() = viewModelScope.launch {
        _estado.value = _estado.value.copy(
            clubesParaTorneio = db.clubes().todos(240)
        )
    }

    /**
     * Cria o torneio, faz o sorteio com potes e gera a fase de grupos.
     *
     * O sorteio é semeado pelo instante da criação, então cada torneio
     * novo dá um chaveamento diferente — mas o mesmo torneio, uma vez
     * criado, tem grupos estáveis.
     */
    fun criarTorneio(
        nome: String,
        formato: FormatoTorneio,
        clubesEscolhidos: List<Clube>,
        quantidadeDeGrupos: Int,
        quantosPassam: Int = 2,
    ) = viewModelScope.launch {
        if (clubesEscolhidos.size < 4) return@launch
        val temporada = db.carreira().atual()?.temporada ?: 1

        val grupos = if (formato == FormatoTorneio.GRUPOS_E_ELIMINATORIA)
            Torneios.sortearGrupos(clubesEscolhidos, quantidadeDeGrupos)
        else emptyList()

        val torneio = Torneio(
            nome = nome.ifBlank { "Torneio" },
            temporada = temporada,
            formato = formato.name,
            clubes = clubesEscolhidos.joinToString(",") { it.id.toString() },
            grupos = grupos.joinToString(";") { g ->
                g.joinToString(",") { it.id.toString() }
            },
            quantosPassam = quantosPassam,
        )
        val id = db.torneios().criar(torneio).toInt()
        val salvo = torneio.copy(id = id)

        val jogos = when (formato) {
            FormatoTorneio.GRUPOS_E_ELIMINATORIA -> Torneios.jogosDaFaseDeGrupos(
                grupos.map { g -> g.map { it.id } },
                salvo.ligaIdVirtual, temporada,
            )
            FormatoTorneio.ELIMINATORIA -> Torneios.primeiraEliminatoria(
                clubesEscolhidos.sortedByDescending { it.reputacao }.map { it.id },
                salvo.ligaIdVirtual, temporada,
            )
            FormatoTorneio.PONTOS_CORRIDOS -> Temporada.gerarCalendario(
                clubesEscolhidos, salvo.ligaIdVirtual, temporada,
            ).filter { it.rodada <= clubesEscolhidos.size - 1 }   // turno único
        }
        db.partidas().inserirTodas(jogos)
        abrirTorneio(salvo)
        carregarTorneios()
    }

    fun carregarTorneios() = viewModelScope.launch {
        _estado.value = _estado.value.copy(torneios = db.torneios().todos())
    }

    fun abrirTorneio(t: Torneio) = viewModelScope.launch {
        val partidas = db.partidas().daTemporada(t.temporada, t.ligaIdVirtual)
        val clubes = t.idsClubes().mapNotNull { db.clubes().porId(it) }
        val porId = clubes.associateBy { it.id }

        val grupos = t.gruposMontados().mapIndexed { i, ids ->
            val doGrupo = ids.mapNotNull { porId[it] }
            ('A' + i).toString() to Torneios.tabelaDoGrupo(doGrupo, partidas)
        }

        val meuId = _estado.value.clube?.id
        carregarInscricao(t)
        _estado.value = _estado.value.copy(
            torneioAberto = t,
            gruposDoTorneio = grupos,
            eliminatoriaDoTorneio = partidas
                .filter { it.rodada > Torneios.BASE_ELIMINATORIA }
                .sortedBy { it.rodada },
            proximaDoTorneio = partidas.firstOrNull {
                it.golsMandante == null &&
                        (it.mandanteId == meuId || it.visitanteId == meuId)
            },
        )
    }

    /**
     * Resolve a fase atual do torneio e avança para a próxima.
     *
     * Simula tudo que ainda não foi jogado na fase, e se a eliminatória
     * acabou registra o campeão no palmarés.
     */
    fun avancarTorneio(t: Torneio) = viewModelScope.launch {
        val partidas = db.partidas().daTemporada(t.temporada, t.ligaIdVirtual)
        val aJogar = partidas.filter { it.golsMandante == null }

        withContext(Dispatchers.Default) {
            aJogar.forEach { p ->
                val casa = db.clubes().porId(p.mandanteId) ?: return@forEach
                val fora = db.clubes().porId(p.visitanteId) ?: return@forEach
                clubesConhecidos[casa.id] = casa
                clubesConhecidos[fora.id] = fora
                val ec = db.jogadores().elenco(casa.id)
                val ef = db.jogadores().elenco(fora.id)
                // A inscrição só vale para o clube do usuário: a IA não
                // fecha lista, e obrigá-la a isso só criaria times
                // incompletos sem ganho de jogabilidade.
                val meuId = _estado.value.clube?.id
                val inscritosCasa = if (casa.id == meuId)
                    db.inscricoes().ids(t.id, casa.id).toSet() else null
                val inscritosFora = if (fora.id == meuId)
                    db.inscricoes().ids(t.id, fora.id).toSet() else null

                val tc = montarTime(casa.id, casa.nome, ec,
                    TreinadorIA.taticaPara(casa, ec), inscritosCasa)
                    ?: return@forEach
                val tf = montarTime(fora.id, fora.nome, ef,
                    TreinadorIA.taticaPara(fora, ef), inscritosFora)
                    ?: return@forEach
                val r = motor.simular(tc, tf)
                db.partidas().atualizar(p.copy(
                    golsMandante = r.golsMandante,
                    golsVisitante = r.golsVisitante,
                ))
            }
        }

        val resolvidas = db.partidas().daTemporada(t.temporada, t.ligaIdVirtual)
        val clubes = t.idsClubes().mapNotNull { db.clubes().porId(it) }
        val porId = clubes.associateBy { it.id }

        val eliminatorias = resolvidas.filter { it.rodada > Torneios.BASE_ELIMINATORIA }

        val novos = if (eliminatorias.isEmpty()) {
            // Grupos terminaram: monta a primeira eliminatória.
            val classificados = Torneios.classificados(
                t.gruposMontados().map { ids -> ids.mapNotNull { porId[it] } },
                resolvidas, t.quantosPassam,
            )
            Torneios.primeiraEliminatoria(classificados, t.ligaIdVirtual, t.temporada)
        } else {
            val ultima = eliminatorias.filter {
                it.rodada == eliminatorias.maxOf { p -> p.rodada }
            }
            Torneios.proximaEliminatoria(ultima, t.ligaIdVirtual, t.temporada)
        }

        if (novos.isNotEmpty()) {
            db.partidas().inserirTodas(novos)
        } else {
            Torneios.campeao(resolvidas)?.let { campeao ->
                if (db.titulos().jaRegistrado(campeao, t.nome, t.temporada) == 0) {
                    db.titulos().registrar(
                        Titulo(clubeId = campeao, nomeDaCompeticao = t.nome,
                            temporada = t.temporada, tipo = "torneio")
                    )
                }
                db.torneios().atualizar(t.copy(campeaoId = campeao))
            }
        }

        abrirTorneio(db.torneios().porId(t.id) ?: t)
        carregarTorneios()
    }

    /** Carrega quem está inscrito no torneio aberto. */
    fun carregarInscricao(t: Torneio) = viewModelScope.launch {
        val clube = _estado.value.clube ?: return@launch
        _estado.value = _estado.value.copy(
            inscritosNoTorneio = db.inscricoes().ids(t.id, clube.id).toSet()
        )
    }

    /**
     * Fecha a lista para o torneio. Substitui a anterior inteira: meia
     * inscrição não existe, ou a lista está válida ou não está.
     */
    fun inscreverElenco(t: Torneio, jogadores: List<Jogador>) =
        viewModelScope.launch {
            val clube = _estado.value.clube ?: return@launch
            if (jogadores.size < Inscricoes.MINIMO) return@launch

            db.inscricoes().limpar(t.id, clube.id)
            db.inscricoes().inscrever(
                jogadores.take(Inscricoes.VAGAS).map {
                    Inscricao(t.id, clube.id, it.id)
                }
            )
            carregarInscricao(t)
        }

    fun carregarTitulos() = viewModelScope.launch {
        val clube = _estado.value.clube ?: return@launch
        _estado.value = _estado.value.copy(titulos = db.titulos().doClube(clube.id))
    }

    // ---------------------------------------------- CALIBRAÇÃO

    /**
     * Roda N partidas do seu time contra adversários reais e reporta as
     * médias contra os números do futebol de verdade.
     *
     * Existe porque "está saindo muito gol" é difícil de verificar
     * jogando: seriam dezenas de partidas para formar uma média. Aqui
     * roda tudo em segundos e mostra se o motor está calibrado.
     */
    fun rodarCalibracao(partidas: Int = 40) = viewModelScope.launch {
        val e = _estado.value
        val clube = e.clube ?: return@launch
        _estado.value = e.copy(calibrando = true, calibracao = "Simulando...")

        val relatorio = withContext(Dispatchers.Default) {
            val meuTime = montarTime(clube.id, clube.nome, e.elenco, e.tatica)
                ?: return@withContext "Elenco incompleto."

            val adversarios = db.clubes().porLiga(clube.ligaId)
                .filter { it.id != clube.id }
            if (adversarios.isEmpty()) return@withContext "Sem adversários."

            var gols = 0; var chutes = 0; var noGol = 0
            var passes = 0; var certos = 0
            var faltas = 0; var escanteios = 0; var cartoes = 0
            var jogos = 0

            repeat(partidas) { n ->
                val adv = adversarios[n % adversarios.size]
                val elencoAdv = db.jogadores().elenco(adv.id)
                val timeAdv = montarTime(
                    adv.id, adv.nome, elencoAdv,
                    TreinadorIA.taticaPara(adv, elencoAdv),
                ) ?: return@repeat

                val r = MotorPartida(kotlin.random.Random(n * 7919L))
                    .simular(meuTime, timeAdv)

                gols += r.golsMandante
                chutes += r.statsMandante.chutes
                noGol += r.statsMandante.chutesNoGol
                passes += r.statsMandante.passes
                certos += r.statsMandante.passesCertos
                faltas += r.statsMandante.faltas
                escanteios += r.statsMandante.escanteios
                cartoes += r.statsMandante.amarelos + r.statsMandante.vermelhos
                jogos++
            }

            if (jogos == 0) return@withContext "Nenhuma partida simulável."

            fun m(v: Int) = v.toDouble() / jogos
            val conversao = if (chutes == 0) 0.0 else gols * 100.0 / chutes
            val precisao = if (passes == 0) 0.0 else certos * 100.0 / passes

            buildString {
                appendLine("$jogos partidas · médias do seu time por jogo")
                appendLine()
                appendLine("Gols            %.2f    real 1,3-1,5".format(m(gols)))
                appendLine("Finalizações   %.1f    real 12-14".format(m(chutes)))
                appendLine("No gol          %.1f    real 4-5".format(m(noGol)))
                appendLine("Conversão      %.1f%%   real 10-11%%".format(conversao))
                appendLine("Passes        %.0f    real 400-500".format(m(passes)))
                appendLine("Precisão       %.1f%%   real 82-87%%".format(precisao))
                appendLine("Faltas         %.1f    real 10-13".format(m(faltas)))
                appendLine("Escanteios     %.1f    real 4-6".format(m(escanteios)))
                appendLine("Cartões        %.1f    real 1,8-2,2".format(m(cartoes)))
            }
        }

        _estado.value = _estado.value.copy(
            calibrando = false, calibracao = relatorio)
    }

    // ------------------------------ SEMELHANÇA E DESENVOLVIMENTO

    /**
     * Guarda um retrato do elenco. Chamado ao virar a temporada, e também
     * na primeira carga — sem uma linha de base inicial nunca haveria o
     * que comparar.
     */
    private suspend fun tirarRetrato(temporada: Int, elenco: List<Jogador>) {
        if (elenco.isEmpty()) return
        if (db.retratos().total(temporada) > 0) return
        db.retratos().salvarTodos(elenco.map { RetratoJogador.de(it, temporada) })
    }

    /** Compara o elenco de hoje com o retrato de uma temporada anterior. */
    fun analisarDesenvolvimento(temporadaBase: Int? = null) = viewModelScope.launch {
        val carreira = db.carreira().atual() ?: return@launch
        val atual = carreira.temporada
        val base = temporadaBase ?: (atual - 1)
        if (base < 1) {
            _estado.value = _estado.value.copy(
                desenvolvimento = emptyList(),
                resumoDesenvolvimento =
                    "Ainda na primeira temporada — sem passado para comparar.",
            )
            return@launch
        }

        val antes = db.retratos().daTemporada(base).associateBy { it.jogadorId }
        val agora = db.retratos().daTemporada(atual).associateBy { it.jogadorId }

        val lista = _estado.value.elenco.mapNotNull { j ->
            AnaliseDesenvolvimento.comparar(j, antes[j.id], agora[j.id])
        }.sortedByDescending { it.deltaGeral }

        _estado.value = _estado.value.copy(
            desenvolvimento = lista,
            resumoDesenvolvimento = AnaliseDesenvolvimento.resumir(lista),
        )
    }

    /** Garimpo estilo moneyball: mais qualidade por euro. */
    fun garimpar(papel: Papel) = viewModelScope.launch {
        val orcamento = _estado.value.caixa
        val universo = withContext(Dispatchers.IO) {
            db.jogadores().buscar(
                posicao = null, idadeMax = 34, valorMax = orcamento,
                geralMin = 58, limite = 300,
            )
        }
        _estado.value = _estado.value.copy(
            garimpo = withContext(Dispatchers.Default) {
                Moneyball.garimpar(universo, papel, orcamento)
            }
        )
    }

    /** Quem joga como ele — para repor um titular que saiu. */
    fun buscarParecidos(referencia: Jogador) = viewModelScope.launch {
        val orcamento = _estado.value.caixa
        val universo = withContext(Dispatchers.IO) {
            db.jogadores().buscar(
                posicao = null, idadeMax = 36, valorMax = orcamento,
                geralMin = (referencia.geral - 14).coerceAtLeast(50),
                limite = 400,
            )
        }
        _estado.value = _estado.value.copy(
            referenciaSemelhanca = referencia,
            parecidos = withContext(Dispatchers.Default) {
                Semelhanca.parecidosCom(referencia, universo)
            },
        )
    }

    // ------------------------------------------------- OLHEIRO

    fun definirDna(d: Dna) {
        _estado.value = _estado.value.copy(dnaDoClube = d)
    }

    /** Busca candidatos para o relatório de olheiro. */
    fun buscarParaOlheiro(
        geralMin: Int = 68,
        idadeMax: Int = 32,
    ) = viewModelScope.launch {
        val caixa = _estado.value.caixa
        _estado.value = _estado.value.copy(
            candidatosOlheiro = db.jogadores().buscar(
                posicao = null, idadeMax = idadeMax,
                valorMax = caixa, geralMin = geralMin, limite = 60,
            )
        )
    }

    fun observar(jogador: Jogador) = viewModelScope.launch {
        val atual = db.observacoes().de(jogador.id)
            ?: Observacao(jogador.id, nivel = 0, semanas = 0)
        db.observacoes().salvar(Olheiro.avancarUmaSemana(atual))
        carregarTudo()
    }

    fun pararDeObservar(jogador: Jogador) = viewModelScope.launch {
        db.observacoes().parar(jogador.id)
        carregarTudo()
    }

    /**
     * Uma semana passa: os olheiros avançam e a conta chega.
     *
     * Chamado junto com o treino, porque na prática é a mesma unidade de
     * tempo — o que dá peso à escolha entre gastar em observação ou não.
     */
    private suspend fun avancarSemanaDeObservacao() {
        val ativas = db.observacoes().ativas()
        if (ativas.isEmpty()) return

        db.observacoes().salvarTodas(ativas.map { Olheiro.avancarUmaSemana(it) })

        val custo = Olheiro.custoDe(ativas)
        val clube = _estado.value.clube ?: return
        if (custo > 0) {
            db.clubes().atualizar(
                clube.copy(caixaEur = (clube.caixaEur - custo).coerceAtLeast(0))
            )
        }
    }

    // ---------------------------------------------- MULTIJOGADOR

    var sala: Sala? = null
        private set
    var partidaEmRede: PartidaEmRede? = null
        private set

    private val _estadoRede = MutableStateFlow<EstadoRede>(EstadoRede.Desconectado)
    val estadoRede: StateFlow<EstadoRede> = _estadoRede.asStateFlow()

    private val _estadoPartidaRede =
        MutableStateFlow<EstadoPartidaRede?>(null)
    val estadoPartidaRede: StateFlow<EstadoPartidaRede?> =
        _estadoPartidaRede.asStateFlow()

    private val _salasEncontradas = MutableStateFlow<List<SalaEncontrada>>(emptyList())
    val salasEncontradas: StateFlow<List<SalaEncontrada>> =
        _salasEncontradas.asStateFlow()

    private val _procurando = MutableStateFlow(false)
    val procurando: StateFlow<Boolean> = _procurando.asStateFlow()

    private fun abrirSala(anfitriao: Boolean): Sala {
        fecharSala()
        val nova = Sala(apelido = _estado.value.clube?.nome ?: "Treinador")
        sala = nova
        partidaEmRede = PartidaEmRede(nova, souAnfitriao = anfitriao)

        viewModelScope.launch {
            nova.estado.collect { e ->
                _estadoRede.value = e
                // Assim que conecta, a primeira coisa é comparar as bases.
                if (e is EstadoRede.Conectado) {
                    _estadoPartidaRede.value = EstadoPartidaRede.Preparando
                    val imp = db.impressao().daBase()
                    partidaEmRede?.enviarImpressao(
                        db.jogadores().total(), Cripto.impressao(imp))
                }
            }
        }

        viewModelScope.launch {
            nova.mensagens.collect { m -> tratarMensagem(m) }
        }

        viewModelScope.launch {
            partidaEmRede?.estado?.collect { _estadoPartidaRede.value = it }
        }
        return nova
    }

    private suspend fun tratarMensagem(m: Mensagem) {
        val rede = partidaEmRede ?: return
        when (m.tipo) {
            Tipo.IMPRESSAO -> {
                val minha = Cripto.impressao(db.impressao().daBase())
                if (rede.conferirImpressao(m, db.jogadores().total(), minha)) {
                    // Bases iguais: manda a escalação.
                    val e = _estado.value
                    rede.enviarEsquadrao(
                        e.clube?.nome ?: "Meu clube", e.elenco, slots, e.tatica)
                }
            }
            Tipo.ESQUADRAO -> rede.receberEsquadrao(m)
            Tipo.COMANDO -> rede.receberComando(m)
            Tipo.CHECKSUM -> rede.conferirChecksum(m)
            Tipo.FIM -> { /* o resultado local já é o mesmo */ }
            Tipo.ERRO -> rede.abortar(m.corpo.optString("motivo"))
            Tipo.OLA, Tipo.PRONTO -> {}
        }
    }

    fun hospedarSala(nome: String) {
        abrirSala(anfitriao = true).hospedar(nome)
    }

    fun procurarSalas() = viewModelScope.launch {
        _procurando.value = true
        val s = abrirSala(anfitriao = false)
        _salasEncontradas.value = s.procurar()
        _procurando.value = false
    }

    fun entrarNaSala(encontrada: SalaEncontrada) {
        sala?.entrar(encontrada)
    }

    /** Chamado quando os dois confirmam que o código de segurança bate. */
    fun comecarPartidaEmRede(): PartidaAoVivo? {
        val rede = partidaEmRede ?: return null
        val e = _estado.value
        val p = rede.iniciar(
            meuClube = e.clube?.nome ?: "Meu clube",
            meuElenco = e.elenco,
            meusSlots = slots,
            minhaTatica = e.tatica,
        ) ?: return null

        // Em rede os dois lados são humanos: nada de treinador automático.
        p.desligarIA()
        souMandanteAoVivo = rede.souMandante
        taticaDaPartida = e.tatica
        partidaAoVivo = p
        return p
    }

    fun fecharSala() {
        sala?.fechar()
        sala = null
        partidaEmRede = null
        _estadoRede.value = EstadoRede.Desconectado
        _estadoPartidaRede.value = null
        _salasEncontradas.value = emptyList()
    }

    override fun onCleared() {
        fecharSala()
        super.onCleared()
    }

    // --------------------------------------------------- TREINO

    fun treinarElenco(foco: FocoTreino, intensidade: Intensidade) =
        viewModelScope.launch {
            val elenco = _estado.value.elenco
            // A comissão multiplica o que o treino rende. Sem auxiliar,
            // o time treina a 82% do que treinaria com uma comissão boa.
            val fator = Comissao.fatorDeTreino(_estado.value.comissao, foco)
            val atualizados = withContext(Dispatchers.Default) {
                elenco.map { j ->
                    var r = Treino.semana(j, foco, intensidade).jogador
                    // Aplica o fator repetindo o treino quando a comissão é
                    // boa, e pulando quando é ruim — mantém a evolução
                    // discreta em vez de criar frações de atributo.
                    if (fator > 1.05f && Random.nextFloat() < (fator - 1f) * 3f) {
                        r = Treino.semana(r, foco, intensidade).jogador
                    }
                    r
                }
            }
            db.jogadores().inserirTodos(atualizados)
            avancarSemanaDeObservacao()
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
fun formacaoPadrao(): List<Slot> = Formacoes.padrao.criarSlots()
