package com.exemplo.fmanager.sistemas

import com.exemplo.fmanager.dados.Jogador
import com.exemplo.fmanager.formacao.Papel
import kotlin.math.pow
import kotlin.math.sqrt

/*
 * SEMELHANÇA, VALOR E ARQUÉTIPOS.
 *
 * Três ideias de projetos diferentes que se encaixam na mesma tela:
 *
 *   - FIFA-Player-Recomendation: achar jogadores PARECIDOS com um dado
 *   - moneyball-mentality: achar jogadores SUBVALORIZADOS
 *   - moneyball-mentality: agrupar por ARQUÉTIPO em vez de posição
 *
 * Juntas resolvem o problema real do mercado: seu titular saiu, e você
 * precisa de alguém que jogue como ele mas caiba no orçamento.
 */

// ------------------------------------------------------- SEMELHANÇA

/**
 * Vetor de atributos normalizado. Usar sempre a mesma ordem é o que
 * permite comparar dois jogadores com uma conta só.
 */
private val EIXOS = listOf(
    "Aceleração", "Velocidade", "Agilidade", "Resistência", "Contato físico",
    "Impulsão", "Finalização", "Posicionamento", "Força do chute", "Cabeceio",
    "Passe baixo", "Passe alto", "Visão", "Cruzamento", "Drible",
    "Controle de bola", "Sangue frio", "Roubo de bola",
    "Consciência defensiva", "Interceptação",
)

object Semelhanca {

    private fun vetor(j: Jogador): FloatArray =
        FloatArray(EIXOS.size) { i -> Atributos.ler(j, EIXOS[i]) / 100f }

    /**
     * Similaridade de cosseno entre dois jogadores, de 0 a 100.
     *
     * Cosseno em vez de distância euclidiana de propósito: ele compara o
     * PERFIL, não o nível. Um camisa 9 de overall 68 pode ser 94% parecido
     * com um de 85 — mesmo tipo de jogador, qualidade diferente. É isso
     * que interessa quando você procura um substituto barato.
     */
    fun entre(a: Jogador, b: Jogador): Int {
        val va = vetor(a)
        val vb = vetor(b)
        var produto = 0f; var normaA = 0f; var normaB = 0f
        for (i in va.indices) {
            produto += va[i] * vb[i]
            normaA += va[i] * va[i]
            normaB += vb[i] * vb[i]
        }
        val denominador = sqrt(normaA) * sqrt(normaB)
        if (denominador == 0f) return 0
        return ((produto / denominador) * 100).toInt().coerceIn(0, 100)
    }

    data class Parecido(
        val jogador: Jogador,
        val semelhanca: Int,
        val diferencaGeral: Int,
        val economia: Long,
    )

    /**
     * Quem joga como ele. O filtro de semelhança mínima evita devolver
     * uma lista de gente aleatória quando não existe substituto de fato.
     */
    fun parecidosCom(
        referencia: Jogador,
        universo: List<Jogador>,
        semelhancaMinima: Int = 88,
        limite: Int = 20,
    ): List<Parecido> = universo
        .asSequence()
        .filter { it.id != referencia.id }
        .map {
            Parecido(
                jogador = it,
                semelhanca = entre(referencia, it),
                diferencaGeral = it.geral - referencia.geral,
                economia = referencia.valorEur - it.valorEur,
            )
        }
        .filter { it.semelhanca >= semelhancaMinima }
        .sortedWith(
            compareByDescending<Parecido> { it.semelhanca }
                .thenByDescending { it.jogador.geral }
        )
        .take(limite)
        .toList()
}

// -------------------------------------------------------- MONEYBALL

/*
 * MONEYBALL — ideia do moneyball-mentality.
 *
 * A pergunta não é "quem é o melhor?", é "quem entrega mais por euro?".
 * Um jogador de 78 que custa 8 milhões vale mais para um clube pequeno do
 * que um de 84 que custa 60.
 */
object Moneyball {

    data class Achado(
        val jogador: Jogador,
        val notaNoPapel: Int,
        val eficiencia: Int,      // 0..100
        val precoPorPonto: Long,
        val motivo: String,
    )

    /**
     * Eficiência de contratação: qualidade contra preço, corrigida por
     * idade e por margem de crescimento.
     *
     * A curva de preço no futebol é exponencial — cada ponto de overall
     * acima de 75 custa desproporcionalmente mais. Por isso o cálculo usa
     * preço por ponto acima de uma base, e não preço por overall.
     */
    fun avaliar(
        jogador: Jogador,
        papel: Papel,
        pesos: List<PesoAtributo>? = null,
    ): Achado {
        val nota = PesosPorPosicao.nota(jogador, papel, pesos)
        val valor = jogador.valorEur.coerceAtLeast(50_000)

        // Pontos "úteis" acima de um jogador de base.
        val pontosUteis = (nota - 55).coerceAtLeast(1)
        val precoPorPonto = valor / pontosUteis

        // Referência de mercado: quanto custaria esse nível em média.
        val referencia = (nota.toDouble() / 55.0).pow(6.5) * 60_000
        val razao = referencia / valor.toDouble()

        val margem = (jogador.potencial - jogador.geral).coerceAtLeast(0)
        val bonusIdade = when {
            jogador.idade <= 21 -> 1.25
            jogador.idade <= 25 -> 1.10
            jogador.idade <= 29 -> 1.0
            jogador.idade <= 32 -> 0.85
            else -> 0.65
        }

        val eficiencia = (razao * bonusIdade * (1 + margem * 0.02) * 42)
            .toInt().coerceIn(0, 100)

        val motivo = when {
            razao >= 2.2 -> "Preço muito abaixo do nível técnico"
            margem >= 10 && jogador.idade <= 22 -> "Jovem com margem grande"
            razao >= 1.4 -> "Custa menos que o mercado pede por esse nível"
            jogador.idade >= 32 && razao >= 1.2 -> "Veterano barato para tapar buraco"
            razao >= 1.0 -> "Preço justo"
            else -> "Caro para o que entrega"
        }

        return Achado(jogador, nota, eficiencia, precoPorPonto, motivo)
    }

    /** Os melhores negócios para um papel, dentro do orçamento. */
    fun garimpar(
        universo: List<Jogador>,
        papel: Papel,
        orcamento: Long,
        notaMinima: Int = 60,
        limite: Int = 25,
    ): List<Achado> = universo
        .asSequence()
        .filter { it.valorEur in 1..orcamento }
        .map { avaliar(it, papel) }
        .filter { it.notaNoPapel >= notaMinima }
        .sortedByDescending { it.eficiencia }
        .take(limite)
        .toList()
}

// -------------------------------------------------------- ARQUÉTIPOS

/*
 * ARQUÉTIPOS — também do moneyball-mentality.
 *
 * Aquele projeto agrupa a avaliação em seis categorias em vez de doze
 * posições: goleiro, defensor, pressionador, ala, criador, finalizador.
 *
 * É um nível de abstração útil: na hora de olhar o mercado você
 * normalmente quer "um criador", não especificamente "um MEI".
 */
enum class Arquetipo(
    val rotulo: String,
    val descricao: String,
    val atributos: List<String>,
) {
    GOLEIRO("Goleiro", "Defende e organiza a saída", listOf()),

    DEFENSOR("Defensor", "Ganha o duelo e lê a jogada",
        listOf("Consciência defensiva", "Roubo de bola", "Cabeceio",
            "Contato físico", "Interceptação")),

    PRESSIONADOR("Pressionador", "Sufoca a saída de bola adversária",
        listOf("Resistência", "Agressividade", "Interceptação",
            "Aceleração", "Roubo de bola")),

    ALA("Ala", "Faz o corredor e entrega a bola na área",
        listOf("Velocidade", "Cruzamento", "Resistência", "Drible",
            "Aceleração")),

    CRIADOR("Criador", "Encontra o passe que ninguém vê",
        listOf("Visão", "Passe baixo", "Passe alto", "Controle de bola",
            "Sangue frio")),

    FINALIZADOR("Finalizador", "Transforma chance em gol",
        listOf("Finalização", "Posicionamento", "Força do chute",
            "Sangue frio", "Cabeceio"));

    fun notaDe(j: Jogador): Int {
        if (this == GOLEIRO) {
            return ((j.golReflexo * 0.35f + j.golMergulho * 0.25f +
                    j.golPosicionamento * 0.25f + j.golDefesaMao * 0.15f))
                .toInt().coerceIn(0, 99)
        }
        return atributos.map { Atributos.ler(j, it) }.average().toInt()
    }

    companion object {
        /** O arquétipo em que ele é mais forte. */
        fun principalDe(j: Jogador): Arquetipo = entries.maxBy { it.notaDe(j) }

        fun paraPapel(papel: Papel): Arquetipo = when (papel) {
            Papel.GOL -> GOLEIRO
            Papel.ZAG -> DEFENSOR
            Papel.LE, Papel.LD, Papel.ME, Papel.MD -> ALA
            Papel.VOL -> PRESSIONADOR
            Papel.MC, Papel.MEI -> CRIADOR
            Papel.PE, Papel.PD, Papel.ATA -> FINALIZADOR
        }
    }
}
