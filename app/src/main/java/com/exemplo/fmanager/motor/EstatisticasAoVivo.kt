package com.exemplo.fmanager.motor

/*
 * ESTATÍSTICA POR JOGADOR EM TEMPO REAL.
 *
 * Lacuna que faltava: durante a partida eu só mostrava números do TIME —
 * posse, chutes, faltas. Nada por jogador. Então não havia como perceber,
 * no minuto 60, que o seu camisa 10 tinha errado catorze passes e a nota
 * dele estava em 5,2. Você descobria no resumo, quando já não dava para
 * fazer nada.
 *
 * Agora cada jogador acumula os próprios números durante o jogo, e a nota
 * é recalculada a cada lance. É informação para DECIDIR a substituição,
 * não para conferir depois.
 */

data class EstatisticaAoVivo(
    val jogadorId: Int,
    val nome: String,
    val sigla: String,
    val doMandante: Boolean,
    val minutosEmCampo: Int = 0,
    val passes: Int = 0,
    val passesCertos: Int = 0,
    val finalizacoes: Int = 0,
    val finalizacoesNoGol: Int = 0,
    val gols: Int = 0,
    val assistencias: Int = 0,
    val driblesTentados: Int = 0,
    val driblesCertos: Int = 0,
    val desarmes: Int = 0,
    val faltasCometidas: Int = 0,
    val faltasSofridas: Int = 0,
    val bolasPerdidas: Int = 0,
    val amarelo: Boolean = false,
    val vermelho: Boolean = false,
    val gas: Int = 100,
) {
    val precisaoPasse: Int
        get() = if (passes == 0) 0 else (passesCertos * 100) / passes

    val sucessoDrible: Int
        get() = if (driblesTentados == 0) 0 else (driblesCertos * 100) / driblesTentados

    /**
     * Nota ao vivo, de 3 a 10.
     *
     * Parte de 6,0 e move conforme o que ele fez. Os pesos são os de uma
     * nota de imprensa: gol vale muito, erro de passe vale pouco de cada
     * vez mas acumula, e cartão vermelho afunda.
     *
     * A precisão de passe só entra depois de 8 passes — antes disso a
     * amostra é pequena demais e a nota ficaria pulando a cada toque.
     */
    val nota: Float
        get() {
            var n = 6.0f
            n += gols * 1.35f
            n += assistencias * 0.85f
            n += finalizacoesNoGol * 0.10f
            n += driblesCertos * 0.07f
            n += desarmes * 0.10f
            n -= bolasPerdidas * 0.045f
            n -= faltasCometidas * 0.045f
            if (amarelo) n -= 0.25f
            if (vermelho) n -= 1.8f

            if (passes >= 8) {
                n += (precisaoPasse - 78) * 0.014f
            }
            return n.coerceIn(3f, 10f)
        }

    /** Como a nota deve aparecer: verde bom, laranja médio, vermelho ruim. */
    val faixaDaNota: Int get() = when {
        nota >= 7.5f -> 2
        nota >= 6.0f -> 1
        else -> 0
    }
}

/**
 * Acumulador interno. Mutável de propósito: é atualizado dezenas de vezes
 * por partida, e criar um objeto novo a cada toque seria desperdício num
 * laço que roda mil vezes.
 */
internal class AcumuladorJogador(
    val jogadorId: Int,
    val nome: String,
    var sigla: String,
    val doMandante: Boolean,
) {
    var passes = 0
    var passesCertos = 0
    var finalizacoes = 0
    var finalizacoesNoGol = 0
    var gols = 0
    var assistencias = 0
    var driblesTentados = 0
    var driblesCertos = 0
    var desarmes = 0
    var faltasCometidas = 0
    var faltasSofridas = 0
    var bolasPerdidas = 0
    var amarelo = false
    var vermelho = false
    var entrouNoMinuto = 0
    var saiuNoMinuto: Int? = null

    fun paraLeitura(minutoAtual: Int, gas: Int) = EstatisticaAoVivo(
        jogadorId = jogadorId,
        nome = nome,
        sigla = sigla,
        doMandante = doMandante,
        minutosEmCampo = ((saiuNoMinuto ?: minutoAtual) - entrouNoMinuto)
            .coerceAtLeast(0),
        passes = passes,
        passesCertos = passesCertos,
        finalizacoes = finalizacoes,
        finalizacoesNoGol = finalizacoesNoGol,
        gols = gols,
        assistencias = assistencias,
        driblesTentados = driblesTentados,
        driblesCertos = driblesCertos,
        desarmes = desarmes,
        faltasCometidas = faltasCometidas,
        faltasSofridas = faltasSofridas,
        bolasPerdidas = bolasPerdidas,
        amarelo = amarelo,
        vermelho = vermelho,
        gas = gas,
    )
}
