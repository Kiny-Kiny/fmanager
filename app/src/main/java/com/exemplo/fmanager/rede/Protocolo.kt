package com.exemplo.fmanager.rede

import com.exemplo.fmanager.dados.Jogador
import com.exemplo.fmanager.formacao.*
import org.json.JSONArray
import org.json.JSONObject

/*
 * PROTOCOLO DA SALA.
 *
 * Sequência de uma partida em rede:
 *
 *   1. OLA          troca de chaves públicas e nonces
 *   2. IMPRESSAO    cada lado manda a impressão da própria base
 *                   -> se não bate, a partida nem começa
 *   3. ESQUADRAO    escalação completa, assinada
 *   4. PRONTO       semente combinada, os dois começam a simular
 *   5. COMANDO      mudanças táticas agendadas para um lance futuro
 *   6. CHECKSUM     a cada 100 lances, para detectar dessincronia
 *   7. FIM          resultado final, também comparado
 *
 * O ponto central: NINGUÉM manda o resultado da partida para o outro.
 * Os dois simulam localmente com a mesma semente e a mesma entrada, e
 * comparam checksums. Se divergirem, a partida é anulada — é isso que
 * impede um cliente modificado de simplesmente declarar que ganhou.
 *
 * Serialização em JSON com org.json, que já vem no Android.
 */

const val VERSAO_PROTOCOLO = 1

enum class Tipo { OLA, IMPRESSAO, ESQUADRAO, PRONTO, COMANDO, CHECKSUM, FIM, ERRO }

data class Mensagem(
    val tipo: Tipo,
    val corpo: JSONObject,
    /** Assinatura do corpo, quando o conteúdo precisa ser inforjável. */
    val assinatura: String? = null,
) {
    fun paraJson(): String = JSONObject().apply {
        put("tipo", tipo.name)
        put("corpo", corpo)
        assinatura?.let { put("assinatura", it) }
    }.toString()

    companion object {
        fun deJson(texto: String): Mensagem? = runCatching {
            val j = JSONObject(texto)
            Mensagem(
                tipo = Tipo.valueOf(j.getString("tipo")),
                corpo = j.getJSONObject("corpo"),
                assinatura = if (j.has("assinatura")) j.getString("assinatura") else null,
            )
        }.getOrNull()
    }
}

// ------------------------------------------------------------ CORPOS

object Corpos {

    fun ola(publica: String, nonce: String, apelido: String) = JSONObject().apply {
        put("versao", VERSAO_PROTOCOLO)
        put("publica", publica)
        put("nonce", nonce)
        put("apelido", apelido)
    }

    /**
     * A impressão que os dois comparam.
     *
     * Cobre a base de jogadores, a versão do protocolo e a versão do
     * motor. Qualquer diferença quebraria a simulação determinística, e
     * é melhor descobrir agora do que no minuto 60.
     */
    fun impressao(
        totalJogadores: Int,
        impressaoBase: String,
        versaoMotor: Int,
    ) = JSONObject().apply {
        put("versao", VERSAO_PROTOCOLO)
        put("totalJogadores", totalJogadores)
        put("impressaoBase", impressaoBase)
        put("versaoMotor", versaoMotor)
    }

    fun esquadrao(
        nomeClube: String,
        jogadores: List<Jogador>,
        slots: List<Slot>,
        tatica: Tatica,
    ) = JSONObject().apply {
        put("clube", nomeClube)
        put("tatica", taticaParaJson(tatica))
        put("jogadores", JSONArray().apply {
            jogadores.forEach { put(jogadorParaJson(it)) }
        })
        put("slots", JSONArray().apply {
            slots.forEach { put(slotParaJson(it)) }
        })
    }

    fun pronto(semente: Long) = JSONObject().apply { put("semente", semente) }

    /** Comando agendado. O lance futuro dá tempo de a rede entregar. */
    fun comando(lance: Int, acao: String, dados: JSONObject) = JSONObject().apply {
        put("lance", lance)
        put("acao", acao)
        put("dados", dados)
    }

    fun checksum(lance: Int, valor: String) = JSONObject().apply {
        put("lance", lance)
        put("valor", valor)
    }

    fun fim(golsMandante: Int, golsVisitante: Int, checksum: String) = JSONObject().apply {
        put("golsMandante", golsMandante)
        put("golsVisitante", golsVisitante)
        put("checksum", checksum)
    }

    fun erro(motivo: String) = JSONObject().apply { put("motivo", motivo) }
}

// -------------------------------------------------------- CONVERSÕES

/**
 * Só os campos que o motor realmente usa.
 *
 * Enviar o objeto inteiro seria desperdício de banda numa rede local e,
 * pior, faria a impressão depender de campos que não afetam nada.
 */
fun jogadorParaJson(j: Jogador): JSONObject = JSONObject().apply {
    put("id", j.id); put("nome", j.nome); put("pos", j.posicao)
    put("alt", j.posicoesAlt); put("ger", j.geral); put("foto", j.urlFoto ?: "")
    put("tra", j.tracosTexto)
    put("a", intArrayOf(
        j.aceleracao, j.velocidade, j.posicionamento, j.finalizacao,
        j.forcaChute, j.chuteLonge, j.chutePrimeira, j.penaltis,
        j.visao, j.cruzamento, j.cobrancaFalta, j.passeBaixo,
        j.passeAlto, j.curva, j.drible, j.agilidade,
        j.equilibrio, j.reacoes, j.controleBola, j.sangueFrio,
        j.interceptacao, j.cabeceio, j.consciencaDef, j.rouboBola,
        j.carrinho, j.impulsao, j.resistencia, j.contatoFisico,
        j.agressividade, j.golMergulho, j.golDefesaMao, j.golChute,
        j.golPosicionamento, j.golReflexo, j.estrelasDrible, j.pernaRuim,
    ).joinToString(","))
}

fun jogadorDeJson(o: JSONObject): Jogador {
    val a = o.getString("a").split(",").map { it.toIntOrNull() ?: 0 }
    fun v(i: Int) = a.getOrElse(i) { 0 }
    return Jogador(
        id = o.getInt("id"), nome = o.getString("nome"),
        idade = 25, nacionalidade = "", clubeId = null,
        clube = "", liga = "",
        posicao = o.getString("pos"), posicoesAlt = o.getString("alt"),
        peDominante = "", pernaRuim = v(35), estrelasDrible = v(34),
        geral = o.getInt("ger"), potencial = o.getInt("ger"),
        valorEur = 0, salarioEur = 0, alturaCm = 180, pesoKg = 75,
        aceleracao = v(0), velocidade = v(1), posicionamento = v(2),
        finalizacao = v(3), forcaChute = v(4), chuteLonge = v(5),
        chutePrimeira = v(6), penaltis = v(7), visao = v(8),
        cruzamento = v(9), cobrancaFalta = v(10), passeBaixo = v(11),
        passeAlto = v(12), curva = v(13), drible = v(14), agilidade = v(15),
        equilibrio = v(16), reacoes = v(17), controleBola = v(18),
        sangueFrio = v(19), interceptacao = v(20), cabeceio = v(21),
        consciencaDef = v(22), rouboBola = v(23), carrinho = v(24),
        impulsao = v(25), resistencia = v(26), contatoFisico = v(27),
        agressividade = v(28), golMergulho = v(29), golDefesaMao = v(30),
        golChute = v(31), golPosicionamento = v(32), golReflexo = v(33),
        urlFoto = o.getString("foto").ifBlank { null },
        tracosTexto = o.getString("tra"),
    )
}

fun slotParaJson(s: Slot): JSONObject = JSONObject().apply {
    val base = s.em(Fase.SEM_POSSE)
    put("id", s.id)
    put("papel", base.papel.name)
    put("x", base.x.toDouble())
    put("y", base.y.toDouble())
    put("comp", s.comportamento.name)
    put("jog", s.jogadorId ?: -1)
    put("estilo", s.estilo?.name ?: "")
    put("instr", JSONObject().apply {
        put("mov", s.instrucoes.movimentacao.name)
        put("apoio", s.instrucoes.apoio.name)
        put("marc", s.instrucoes.marcacao.name)
        put("pres", s.instrucoes.pressao)
        put("ampl", s.instrucoes.amplitude)
    })
}

fun slotDeJson(o: JSONObject): Slot {
    val slot = Slot(
        id = o.getInt("id"),
        papelBase = Papel.valueOf(o.getString("papel")),
        x = o.getDouble("x").toFloat(),
        y = o.getDouble("y").toFloat(),
        comportamento = Comportamento.valueOf(o.getString("comp")),
    )
    o.getInt("jog").takeIf { it >= 0 }?.let { slot.jogadorId = it }
    o.getString("estilo").takeIf { it.isNotBlank() }?.let {
        slot.estilo = runCatching { EstiloJogador.valueOf(it) }.getOrNull()
    }
    val i = o.getJSONObject("instr")
    slot.instrucoes = Instrucoes(
        movimentacao = Movimentacao.valueOf(i.getString("mov")),
        apoio = ApoioDefensivo.valueOf(i.getString("apoio")),
        marcacao = Marcacao.valueOf(i.getString("marc")),
        pressao = i.getInt("pres"),
        amplitude = i.getInt("ampl"),
    )
    return slot
}

fun taticaParaJson(t: Tatica): JSONObject = JSONObject().apply {
    put("vc", t.velocidadeConstrucao); put("al", t.alturaLinha)
    put("ip", t.intensidadePressao); put("cp", t.compactacao)
    put("ca", t.contraAtaque); put("lc", t.liberdadeCriativa)
    put("rp", t.riscoNoPasse)
}

fun taticaDeJson(o: JSONObject) = Tatica(
    velocidadeConstrucao = o.getInt("vc"), alturaLinha = o.getInt("al"),
    intensidadePressao = o.getInt("ip"), compactacao = o.getInt("cp"),
    contraAtaque = o.getInt("ca"), liberdadeCriativa = o.getInt("lc"),
    riscoNoPasse = o.getInt("rp"),
)
