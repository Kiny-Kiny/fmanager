package com.exemplo.fmanager.dados

/*
 * MODELO DE JOGADOR — espelha as colunas do CSV do EA FC.
 *
 * Os nomes dos campos seguem o vocabulário do eFootball (passe baixo,
 * roubo de bola, contato físico...), mas cada um aponta para a coluna
 * equivalente do dataset. O comentário ao lado de cada atributo diz
 * qual coluna do CSV alimenta ele.
 */

import androidx.room.*
import com.exemplo.fmanager.formacao.Papel

@Entity(
    tableName = "jogadores",
    indices = [
        Index("posicao"),
        Index("geral"),
        Index("clubeId"),
        Index("valorEur"),
    ],
)
data class Jogador(
    @PrimaryKey val id: Int,
    val nome: String,
    val idade: Int,
    val nacionalidade: String,
    val clubeId: Int?,
    val clube: String,
    val liga: String,

    val posicao: String,            // Position
    val posicoesAlt: String,        // Alternative Positions (separadas por vírgula)
    val peDominante: String,        // Preferred Foot
    val pernaRuim: Int,             // Weak Foot (1..5)
    val estrelasDrible: Int,        // Skill Moves (1..5)

    val geral: Int,                 // OVR
    val potencial: Int,             // Potential
    val valorEur: Long,             // Value
    val salarioEur: Long,           // Wage
    val alturaCm: Int,
    val pesoKg: Int,

    // --- RITMO ---
    val aceleracao: Int,            // Acceleration
    val velocidade: Int,            // Sprint Speed

    // --- FINALIZAÇÃO ---
    val posicionamento: Int,        // Positioning
    val finalizacao: Int,           // Finishing
    val forcaChute: Int,            // Shot Power
    val chuteLonge: Int,            // Long Shots
    val chutePrimeira: Int,         // Volleys
    val penaltis: Int,              // Penalties

    // --- PASSE ---
    val visao: Int,                 // Vision
    val cruzamento: Int,            // Crossing
    val cobrancaFalta: Int,         // Free Kick Accuracy
    val passeBaixo: Int,            // Short Passing
    val passeAlto: Int,             // Long Passing
    val curva: Int,                 // Curve

    // --- DRIBLE ---
    val drible: Int,                // Dribbling
    val agilidade: Int,             // Agility
    val equilibrio: Int,            // Balance
    val reacoes: Int,               // Reactions
    val controleBola: Int,          // Ball Control
    val sangueFrio: Int,            // Composure

    // --- DEFESA ---
    val interceptacao: Int,         // Interceptions
    val cabeceio: Int,              // Heading Accuracy
    val consciencaDef: Int,         // Def Awareness
    val rouboBola: Int,             // Standing Tackle
    val carrinho: Int,              // Sliding Tackle

    // --- FÍSICO ---
    val impulsao: Int,              // Jumping
    val resistencia: Int,           // Stamina
    val contatoFisico: Int,         // Strength
    val agressividade: Int,         // Aggression

    // --- GOLEIRO (0 para jogadores de linha) ---
    val golMergulho: Int = 0,       // GK Diving
    val golDefesaMao: Int = 0,      // GK Handling
    val golChute: Int = 0,          // GK Kicking
    val golPosicionamento: Int = 0, // GK Positioning
    val golReflexo: Int = 0,        // GK Reflexes

    val urlFoto: String? = null,    // URL da carta, vem do campo "card" da API

    /** PlayStyles da EA, serializados: "RAPID+,FINESSE_SHOT,TRIVELA".
     *  Room não guarda lista, então vira texto e volta via tracos(). */
    val tracosTexto: String = "",
)

// ------------------------------------------------ ADEQUAÇÃO AO PAPEL

/*
 * Aqui os dois pedaços do projeto se encontram.
 *
 * O editor de formação devolve um Papel a partir da zona do campo.
 * Esta função pega esse Papel e diz o quanto um jogador serve para ele,
 * de 0 a 100, olhando só os atributos que importam naquela função.
 *
 * É a base de três sistemas de uma vez:
 *   - escalação (avisar quando alguém está fora de posição)
 *   - contratação (buscar quem resolve o buraco do elenco)
 *   - treino (saber qual atributo evoluir para o papel pretendido)
 */

private typealias Peso = Pair<(Jogador) -> Int, Float>

private val PESOS: Map<Papel, List<Peso>> = mapOf(
    Papel.GOL to listOf(
        Jogador::golReflexo to .30f, Jogador::golMergulho to .25f,
        Jogador::golPosicionamento to .20f, Jogador::golDefesaMao to .15f,
        Jogador::golChute to .10f,
    ),
    Papel.ZAG to listOf(
        Jogador::consciencaDef to .25f, Jogador::rouboBola to .20f,
        Jogador::cabeceio to .18f, Jogador::contatoFisico to .17f,
        Jogador::interceptacao to .12f, Jogador::velocidade to .08f,
    ),
    Papel.LE to listOf(
        Jogador::velocidade to .22f, Jogador::resistencia to .18f,
        Jogador::rouboBola to .17f, Jogador::cruzamento to .16f,
        Jogador::aceleracao to .14f, Jogador::consciencaDef to .13f,
    ),
    Papel.VOL to listOf(
        Jogador::interceptacao to .24f, Jogador::consciencaDef to .20f,
        Jogador::passeBaixo to .18f, Jogador::rouboBola to .16f,
        Jogador::contatoFisico to .12f, Jogador::resistencia to .10f,
    ),
    Papel.MC to listOf(
        Jogador::passeBaixo to .24f, Jogador::visao to .20f,
        Jogador::controleBola to .18f, Jogador::resistencia to .14f,
        Jogador::passeAlto to .12f, Jogador::sangueFrio to .12f,
    ),
    Papel.MEI to listOf(
        Jogador::visao to .24f, Jogador::passeBaixo to .19f,
        Jogador::drible to .18f, Jogador::controleBola to .15f,
        Jogador::chuteLonge to .12f, Jogador::sangueFrio to .12f,
    ),
    Papel.ME to listOf(
        Jogador::velocidade to .21f, Jogador::cruzamento to .20f,
        Jogador::drible to .18f, Jogador::agilidade to .15f,
        Jogador::resistencia to .14f, Jogador::aceleracao to .12f,
    ),
    Papel.PE to listOf(
        Jogador::aceleracao to .22f, Jogador::drible to .20f,
        Jogador::velocidade to .19f, Jogador::agilidade to .15f,
        Jogador::finalizacao to .14f, Jogador::controleBola to .10f,
    ),
    Papel.ATA to listOf(
        Jogador::finalizacao to .28f, Jogador::posicionamento to .22f,
        Jogador::forcaChute to .16f, Jogador::sangueFrio to .14f,
        Jogador::cabeceio to .10f, Jogador::aceleracao to .10f,
    ),
)

// lados espelhados usam os mesmos pesos
private fun pesosDe(papel: Papel): List<Peso> = when (papel) {
    Papel.LD -> PESOS.getValue(Papel.LE)
    Papel.MD -> PESOS.getValue(Papel.ME)
    Papel.PD -> PESOS.getValue(Papel.PE)
    else -> PESOS.getValue(papel)
}

/** 0..100 — o quanto este jogador serve para este papel. */
fun Jogador.adequacao(papel: Papel): Int =
    pesosDe(papel).sumOf { (atributo, peso) -> atributo(this) * peso.toDouble() }
        .toInt().coerceIn(0, 100)

/** Diferença entre o papel natural do jogador e o papel em que você o
 *  escalou. Útil para mostrar o aviso vermelho de "fora de posição". */
fun Jogador.melhorPapel(): Papel =
    Papel.entries.maxBy { adequacao(it) }

// ------------------------------------------------------------- DAO

@Dao
interface JogadorDao {

    @Query("SELECT * FROM jogadores WHERE id = :id")
    suspend fun porId(id: Int): Jogador?

    @Query("SELECT * FROM jogadores WHERE clubeId = :clubeId ORDER BY geral DESC")
    suspend fun elenco(clubeId: Int): List<Jogador>

    /**
     * Busca do olheiro. Todos os filtros são opcionais: passar null
     * desliga aquele critério. Os índices na tabela fazem isso
     * responder instantâneo mesmo com 18 mil jogadores.
     */
    @Query("""
        SELECT * FROM jogadores
        WHERE (:posicao   IS NULL OR posicao = :posicao)
          AND (:idadeMax  IS NULL OR idade  <= :idadeMax)
          AND (:valorMax  IS NULL OR valorEur <= :valorMax)
          AND geral BETWEEN :geralMin AND :geralMax
        ORDER BY geral DESC
        LIMIT :limite
    """)
    suspend fun buscar(
        posicao: String? = null,
        idadeMax: Int? = null,
        valorMax: Long? = null,
        geralMin: Int = 0,
        geralMax: Int = 99,
        limite: Int = 50,
    ): List<Jogador>

    /** Promessas: jovens com muito espaço para evoluir. */
    @Query("""
        SELECT * FROM jogadores
        WHERE idade <= :idadeMax AND (potencial - geral) >= :margem
        ORDER BY potencial DESC LIMIT :limite
    """)
    suspend fun promessas(
        idadeMax: Int = 21,
        margem: Int = 8,
        limite: Int = 50,
    ): List<Jogador>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodos(jogadores: List<Jogador>)

    @Query("SELECT COUNT(*) FROM jogadores")
    suspend fun total(): Int
}
