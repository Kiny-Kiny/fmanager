package com.exemplo.fmanager.rede

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyPair
import java.security.PublicKey
import java.util.concurrent.atomic.AtomicBoolean

/*
 * SALA — TRANSPORTE PONTO A PONTO.
 *
 * Sem servidor. Um aparelho abre a sala e escuta; o outro encontra na
 * rede local e conecta direto.
 *
 * A descoberta é por broadcast UDP: o convidado grita "tem sala aí?" na
 * rede e quem está hospedando responde com o nome e a porta. Depois disso
 * a conversa é TCP, com quadros de tamanho prefixado e tudo cifrado com
 * a chave de sessão derivada por ECDH.
 *
 * LIMITE HONESTO: isto funciona na MESMA REDE LOCAL (ou por Wi-Fi
 * Direct). Jogar pela internet sem servidor é impossível — atravessar
 * NAT exige, no mínimo, um servidor de sinalização. Não existe truque
 * que contorne isso.
 */

private const val PORTA_TCP = 47820
private const val PORTA_UDP = 47821
private const val MAGICA = "FMANAGER-SALA-V1"

data class SalaEncontrada(
    val nomeDaSala: String,
    val apelidoDoAnfitriao: String,
    val endereco: String,
)

sealed interface EstadoRede {
    data object Desconectado : EstadoRede
    data class Aguardando(val nomeDaSala: String) : EstadoRede
    data object Procurando : EstadoRede
    data object Negociando : EstadoRede
    data class Conectado(
        val apelidoDoOutro: String,
        val codigoVerificacao: String,
    ) : EstadoRede
    data class Recusado(val motivo: String) : EstadoRede
}

class Sala(private val apelido: String) {

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _estado = MutableStateFlow<EstadoRede>(EstadoRede.Desconectado)
    val estado: StateFlow<EstadoRede> = _estado.asStateFlow()

    private val recebidas = Channel<Mensagem>(Channel.BUFFERED)
    val mensagens = recebidas.receiveAsFlow()

    private var socket: Socket? = null
    private var entrada: DataInputStream? = null
    private var saida: DataOutputStream? = null

    private var servidor: ServerSocket? = null
    private var respondedor: Job? = null
    private val ativo = AtomicBoolean(false)

    private val meuPar: KeyPair = Cripto.gerarParDeChaves()
    private var chaveSessao: ByteArray? = null
    private var publicaDoOutro: PublicKey? = null

    val minhaPublica: String get() = Cripto.exportarPublica(meuPar.public)
    val meuNonce: String = Cripto.nonce()
    var nonceDoOutro: String? = null
        private set

    /** Semente combinada da partida. Nenhum lado a escolhe sozinho. */
    val semente: Long?
        get() = nonceDoOutro?.let { Cripto.semeadura(meuNonce, it) }

    // ------------------------------------------------------- HOSPEDAR

    /** Abre a sala e fica esperando alguém entrar. */
    fun hospedar(nomeDaSala: String) {
        ativo.set(true)
        _estado.value = EstadoRede.Aguardando(nomeDaSala)

        // Responde às buscas na rede local.
        respondedor = escopo.launch {
            runCatching {
                DatagramSocket(PORTA_UDP).use { udp ->
                    udp.broadcast = true
                    val buffer = ByteArray(256)
                    while (ativo.get()) {
                        val pacote = DatagramPacket(buffer, buffer.size)
                        udp.receive(pacote)
                        val pedido = String(pacote.data, 0, pacote.length)
                        if (!pedido.startsWith(MAGICA)) continue

                        val resposta = JSONObject().apply {
                            put("magica", MAGICA)
                            put("sala", nomeDaSala)
                            put("apelido", apelido)
                            put("porta", PORTA_TCP)
                        }.toString().toByteArray()

                        udp.send(DatagramPacket(
                            resposta, resposta.size, pacote.address, pacote.port,
                        ))
                    }
                }
            }
        }

        escopo.launch {
            runCatching {
                ServerSocket(PORTA_TCP).also { servidor = it }.use { srv ->
                    val cliente = srv.accept()
                    ativo.set(false)   // sala fechada: só dois jogadores
                    respondedor?.cancel()
                    conectar(cliente, souAnfitriao = true)
                }
            }.onFailure {
                _estado.value = EstadoRede.Recusado(
                    it.message ?: "Falha ao abrir a sala")
            }
        }
    }

    // --------------------------------------------------------- BUSCAR

    /** Procura salas na rede local. Devolve o que achou em até 3s. */
    suspend fun procurar(): List<SalaEncontrada> = withContext(Dispatchers.IO) {
        _estado.value = EstadoRede.Procurando
        val achadas = mutableListOf<SalaEncontrada>()

        runCatching {
            DatagramSocket().use { udp ->
                udp.broadcast = true
                udp.soTimeout = 700

                val pedido = MAGICA.toByteArray()
                udp.send(DatagramPacket(
                    pedido, pedido.size,
                    InetAddress.getByName("255.255.255.255"), PORTA_UDP,
                ))

                val buffer = ByteArray(512)
                val limite = System.currentTimeMillis() + 3000
                while (System.currentTimeMillis() < limite) {
                    val pacote = DatagramPacket(buffer, buffer.size)
                    val ok = runCatching { udp.receive(pacote); true }
                        .getOrDefault(false)
                    if (!ok) continue

                    runCatching {
                        val j = JSONObject(String(pacote.data, 0, pacote.length))
                        if (j.getString("magica") != MAGICA) return@runCatching
                        val nova = SalaEncontrada(
                            nomeDaSala = j.getString("sala"),
                            apelidoDoAnfitriao = j.getString("apelido"),
                            endereco = pacote.address.hostAddress ?: return@runCatching,
                        )
                        if (achadas.none { it.endereco == nova.endereco }) {
                            achadas += nova
                        }
                    }
                }
            }
        }

        if (achadas.isEmpty()) _estado.value = EstadoRede.Desconectado
        achadas
    }

    /** Entra numa sala encontrada. */
    fun entrar(sala: SalaEncontrada) {
        escopo.launch {
            _estado.value = EstadoRede.Negociando
            runCatching {
                val s = Socket()
                s.connect(InetSocketAddress(sala.endereco, PORTA_TCP), 6000)
                conectar(s, souAnfitriao = false)
            }.onFailure {
                _estado.value = EstadoRede.Recusado(
                    it.message ?: "Não foi possível conectar")
            }
        }
    }

    // ------------------------------------------------------ NEGOCIAÇÃO

    /**
     * Aperto de mão: troca de chaves públicas em claro (é seguro, são
     * públicas) e derivação do segredo por ECDH. Daí em diante tudo é
     * cifrado.
     */
    private suspend fun conectar(s: Socket, souAnfitriao: Boolean) {
        socket = s
        s.tcpNoDelay = true
        val ent = DataInputStream(s.getInputStream()).also { entrada = it }
        val sai = DataOutputStream(s.getOutputStream()).also { saida = it }

        _estado.value = EstadoRede.Negociando

        val meuOla = Mensagem(Tipo.OLA, Corpos.ola(minhaPublica, meuNonce, apelido))
            .paraJson()

        // O anfitrião fala primeiro; evita os dois escreverem juntos.
        suspend fun enviarClaro() = withContext(Dispatchers.IO) {
            val b = meuOla.toByteArray()
            sai.writeInt(b.size); sai.write(b); sai.flush()
        }

        suspend fun lerClaro(): JSONObject? = withContext(Dispatchers.IO) {
            withTimeoutOrNull(8000) {
                val n = ent.readInt()
                if (n <= 0 || n > 1_000_000) return@withTimeoutOrNull null
                val b = ByteArray(n); ent.readFully(b)
                Mensagem.deJson(String(b))?.corpo
            }
        }

        val outro = if (souAnfitriao) {
            enviarClaro(); lerClaro()
        } else {
            val recebido = lerClaro(); enviarClaro(); recebido
        }

        if (outro == null) {
            _estado.value = EstadoRede.Recusado("O outro lado não respondeu")
            return
        }
        if (outro.optInt("versao") != VERSAO_PROTOCOLO) {
            _estado.value = EstadoRede.Recusado(
                "Versões diferentes do jogo. Atualizem os dois.")
            return
        }

        val publica = Cripto.importarPublica(outro.getString("publica"))
        publicaDoOutro = publica
        nonceDoOutro = outro.getString("nonce")

        val segredo = Cripto.derivarSegredo(meuPar.private, publica)
        chaveSessao = segredo

        _estado.value = EstadoRede.Conectado(
            apelidoDoOutro = outro.getString("apelido"),
            codigoVerificacao = Cripto.codigoDeVerificacao(
                segredo, minhaPublica, outro.getString("publica"),
            ),
        )

        escopo.launch { escutar() }
    }

    private suspend fun escutar() = withContext(Dispatchers.IO) {
        val ent = entrada ?: return@withContext
        val chave = chaveSessao ?: return@withContext
        runCatching {
            while (true) {
                val n = ent.readInt()
                if (n <= 0 || n > 4_000_000) break
                val b = ByteArray(n); ent.readFully(b)
                val texto = Cripto.decifrar(chave, b) ?: continue
                Mensagem.deJson(texto)?.let { recebidas.send(it) }
            }
        }
        _estado.value = EstadoRede.Desconectado
    }

    // -------------------------------------------------------- ENVIAR

    /** Envia cifrado. Assina o corpo quando ele precisa ser inforjável. */
    suspend fun enviar(tipo: Tipo, corpo: JSONObject, assinar: Boolean = false) =
        withContext(Dispatchers.IO) {
            val chave = chaveSessao ?: return@withContext
            val sai = saida ?: return@withContext

            val assinatura = if (assinar)
                Cripto.assinar(meuPar.private, corpo.toString()) else null

            val bruto = Mensagem(tipo, corpo, assinatura).paraJson()
            val cifrado = Cripto.cifrar(chave, bruto)
            runCatching {
                sai.writeInt(cifrado.size); sai.write(cifrado); sai.flush()
            }
        }

    /** Confere a assinatura de uma mensagem recebida. */
    fun assinaturaValida(m: Mensagem): Boolean {
        val publica = publicaDoOutro ?: return false
        val assinatura = m.assinatura ?: return false
        return Cripto.verificar(publica, m.corpo.toString(), assinatura)
    }

    fun fechar() {
        ativo.set(false)
        runCatching { socket?.close() }
        runCatching { servidor?.close() }
        escopo.cancel()
        _estado.value = EstadoRede.Desconectado
    }
}
