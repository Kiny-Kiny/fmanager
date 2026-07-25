package com.exemplo.fmanager.sistemas

import com.exemplo.fmanager.dados.Jogador
import com.exemplo.fmanager.formacao.Papel

/*
 * DNA DO CLUBE — ideia do PyScoutFM.
 *
 * Lá o autor agrupa atributos e dá pesos próprios para produzir um
 * "rating de DNA": a nota de quanto o jogador combina com a IDENTIDADE do
 * clube, não com a posição dele.
 *
 * É diferente da adequação que já existe. A adequação responde "ele joga
 * bem de lateral?". O DNA responde "ele joga do jeito que ESTE clube
 * joga?". Um lateral tecnicamente ótimo pode ter DNA baixo num time que
 * vive de intensidade física.
 *
 * A partir disso, o mercado e o treino passam a ter uma direção editável
 * pelo usuário em vez de pesos fixos escondidos no código.
 */

/** Um pilar do DNA: um grupo de atributos com um peso. */
data class PilarDna(
    val nome: String,
    val descricao: String,
    val peso: Int,                       // 0..100
    val atributos: List<String>,
)

data class Dna(
    val nome: String,
    val pilares: List<PilarDna>,
) {
    /** Nota de 0 a 100 de quanto o jogador combina com esta identidade. */
    fun notaDe(jogador: Jogador): Int {
        val pesoTotal = pilares.sumOf { it.peso }.coerceAtLeast(1)
        val soma = pilares.sumOf { pilar ->
            val media = pilar.atributos
                .map { Atributos.ler(jogador, it) }
                .filter { it > 0 }
                .let { if (it.isEmpty()) 0.0 else it.average() }
            media * pilar.peso
        }
        return (soma / pesoTotal).toInt().coerceIn(0, 99)
    }

    /** Onde ele encaixa e onde não. Serve de relatório de olheiro. */
    fun detalhar(jogador: Jogador): List<Pair<PilarDna, Int>> =
        pilares.map { pilar ->
            val media = pilar.atributos
                .map { Atributos.ler(jogador, it) }
                .filter { it > 0 }
                .let { if (it.isEmpty()) 0 else it.average().toInt() }
            pilar to media
        }
}

/** Nomes de atributos em um lugar só, para os pesos serem editáveis. */
object Atributos {

    val todos = listOf(
        "Aceleração", "Velocidade", "Agilidade", "Equilíbrio", "Resistência",
        "Contato físico", "Impulsão", "Finalização", "Posicionamento",
        "Força do chute", "Chute de longe", "Chute de primeira", "Cabeceio",
        "Passe baixo", "Passe alto", "Visão", "Cruzamento", "Curva",
        "Cobrança de falta", "Drible", "Controle de bola", "Sangue frio",
        "Reações", "Roubo de bola", "Consciência defensiva", "Interceptação",
        "Carrinho", "Agressividade", "Pênaltis",
    )

    fun ler(j: Jogador, nome: String): Int = when (nome) {
        "Aceleração" -> j.aceleracao
        "Velocidade" -> j.velocidade
        "Agilidade" -> j.agilidade
        "Equilíbrio" -> j.equilibrio
        "Resistência" -> j.resistencia
        "Contato físico" -> j.contatoFisico
        "Impulsão" -> j.impulsao
        "Finalização" -> j.finalizacao
        "Posicionamento" -> j.posicionamento
        "Força do chute" -> j.forcaChute
        "Chute de longe" -> j.chuteLonge
        "Chute de primeira" -> j.chutePrimeira
        "Cabeceio" -> j.cabeceio
        "Passe baixo" -> j.passeBaixo
        "Passe alto" -> j.passeAlto
        "Visão" -> j.visao
        "Cruzamento" -> j.cruzamento
        "Curva" -> j.curva
        "Cobrança de falta" -> j.cobrancaFalta
        "Drible" -> j.drible
        "Controle de bola" -> j.controleBola
        "Sangue frio" -> j.sangueFrio
        "Reações" -> j.reacoes
        "Roubo de bola" -> j.rouboBola
        "Consciência defensiva" -> j.consciencaDef
        "Interceptação" -> j.interceptacao
        "Carrinho" -> j.carrinho
        "Agressividade" -> j.agressividade
        "Pênaltis" -> j.penaltis
        else -> 0
    }
}

object Dnas {

    val intensidade = Dna("Intensidade", listOf(
        PilarDna("Motor", "Aguenta pressionar os 90 minutos", 35,
            listOf("Resistência", "Aceleração", "Velocidade")),
        PilarDna("Duelo", "Ganha a bola no contato", 30,
            listOf("Contato físico", "Agressividade", "Roubo de bola")),
        PilarDna("Recuperação", "Rouba a bola no campo do adversário", 25,
            listOf("Interceptação", "Consciência defensiva", "Reações")),
        PilarDna("Objetividade", "Não complica com a bola", 10,
            listOf("Passe baixo", "Sangue frio")),
    ))

    val tecnica = Dna("Técnica", listOf(
        PilarDna("Circulação", "Faz a bola andar sem perder", 35,
            listOf("Passe baixo", "Controle de bola", "Visão")),
        PilarDna("Frieza", "Decide bem sob pressão", 25,
            listOf("Sangue frio", "Reações", "Equilíbrio")),
        PilarDna("Um contra um", "Resolve no espaço curto", 25,
            listOf("Drible", "Agilidade")),
        PilarDna("Última bola", "Encontra o passe decisivo", 15,
            listOf("Passe alto", "Curva", "Cruzamento")),
    ))

    val verticalidade = Dna("Verticalidade", listOf(
        PilarDna("Profundidade", "Ataca as costas da defesa", 40,
            listOf("Aceleração", "Velocidade")),
        PilarDna("Definição", "Termina a jogada", 30,
            listOf("Finalização", "Posicionamento", "Força do chute")),
        PilarDna("Transição", "Sai jogando rápido", 20,
            listOf("Passe alto", "Visão")),
        PilarDna("Bola aérea", "Resolve pelo alto", 10,
            listOf("Cabeceio", "Impulsão")),
    ))

    val solidez = Dna("Solidez", listOf(
        PilarDna("Leitura", "Antecipa a jogada", 35,
            listOf("Consciência defensiva", "Interceptação", "Reações")),
        PilarDna("Marcação", "Não perde o duelo", 30,
            listOf("Roubo de bola", "Carrinho", "Contato físico")),
        PilarDna("Área", "Domina a bola aérea", 20,
            listOf("Cabeceio", "Impulsão")),
        PilarDna("Saída", "Constrói de trás", 15,
            listOf("Passe baixo", "Sangue frio")),
    ))

    val todos = listOf(intensidade, tecnica, verticalidade, solidez)

    /**
     * Sugere o DNA que mais combina com o elenco atual.
     *
     * Serve para o clube já começar com uma identidade coerente com quem
     * ele tem — mesma lógica da tática herdada.
     */
    fun sugerirPara(elenco: List<Jogador>): Dna {
        if (elenco.isEmpty()) return intensidade
        return todos.maxBy { dna ->
            elenco.map { dna.notaDe(it) }.average()
        }
    }
}

/*
 * PESOS EDITÁVEIS POR POSIÇÃO — também do PyScoutFM.
 *
 * No projeto original os pesos ficam num arquivo JSON que o usuário
 * troca à vontade. Aqui a estrutura permite o mesmo: os pesos padrão são
 * um ponto de partida, e a tela de olheiro pode sobrescrever.
 */
data class PesoAtributo(val atributo: String, val peso: Int)

object PesosPorPosicao {

    private val padrao: Map<Papel, List<PesoAtributo>> = mapOf(
        Papel.GOL to listOf(),   // goleiro usa os atributos próprios
        Papel.ZAG to listOf(
            PesoAtributo("Consciência defensiva", 25),
            PesoAtributo("Roubo de bola", 20),
            PesoAtributo("Cabeceio", 18),
            PesoAtributo("Contato físico", 17),
            PesoAtributo("Interceptação", 12),
            PesoAtributo("Velocidade", 8),
        ),
        Papel.LE to listOf(
            PesoAtributo("Velocidade", 22), PesoAtributo("Resistência", 18),
            PesoAtributo("Roubo de bola", 17), PesoAtributo("Cruzamento", 16),
            PesoAtributo("Aceleração", 14),
            PesoAtributo("Consciência defensiva", 13),
        ),
        Papel.VOL to listOf(
            PesoAtributo("Interceptação", 24),
            PesoAtributo("Consciência defensiva", 20),
            PesoAtributo("Passe baixo", 18), PesoAtributo("Roubo de bola", 16),
            PesoAtributo("Contato físico", 12), PesoAtributo("Resistência", 10),
        ),
        Papel.MC to listOf(
            PesoAtributo("Passe baixo", 24), PesoAtributo("Visão", 20),
            PesoAtributo("Controle de bola", 18), PesoAtributo("Resistência", 14),
            PesoAtributo("Passe alto", 12), PesoAtributo("Sangue frio", 12),
        ),
        Papel.MEI to listOf(
            PesoAtributo("Visão", 24), PesoAtributo("Passe baixo", 19),
            PesoAtributo("Drible", 18), PesoAtributo("Controle de bola", 15),
            PesoAtributo("Chute de longe", 12), PesoAtributo("Sangue frio", 12),
        ),
        Papel.ME to listOf(
            PesoAtributo("Velocidade", 21), PesoAtributo("Cruzamento", 20),
            PesoAtributo("Drible", 18), PesoAtributo("Agilidade", 15),
            PesoAtributo("Resistência", 14), PesoAtributo("Aceleração", 12),
        ),
        Papel.PE to listOf(
            PesoAtributo("Aceleração", 22), PesoAtributo("Drible", 20),
            PesoAtributo("Velocidade", 19), PesoAtributo("Agilidade", 15),
            PesoAtributo("Finalização", 14),
            PesoAtributo("Controle de bola", 10),
        ),
        Papel.ATA to listOf(
            PesoAtributo("Finalização", 28), PesoAtributo("Posicionamento", 22),
            PesoAtributo("Força do chute", 16), PesoAtributo("Sangue frio", 14),
            PesoAtributo("Cabeceio", 10), PesoAtributo("Aceleração", 10),
        ),
    )

    fun de(papel: Papel): List<PesoAtributo> = when (papel) {
        Papel.LD -> padrao.getValue(Papel.LE)
        Papel.MD -> padrao.getValue(Papel.ME)
        Papel.PD -> padrao.getValue(Papel.PE)
        else -> padrao[papel] ?: emptyList()
    }

    /** Nota de 0 a 100 para o papel, usando pesos possivelmente editados. */
    fun nota(
        jogador: Jogador,
        papel: Papel,
        personalizados: List<PesoAtributo>? = null,
    ): Int {
        if (papel == Papel.GOL) {
            return ((jogador.golReflexo * 0.30f + jogador.golMergulho * 0.25f +
                    jogador.golPosicionamento * 0.20f +
                    jogador.golDefesaMao * 0.15f +
                    jogador.golChute * 0.10f)).toInt().coerceIn(0, 99)
        }
        val pesos = personalizados ?: de(papel)
        if (pesos.isEmpty()) return jogador.geral
        val total = pesos.sumOf { it.peso }.coerceAtLeast(1)
        val soma = pesos.sumOf { Atributos.ler(jogador, it.atributo) * it.peso }
        return (soma / total).coerceIn(0, 99)
    }
}
