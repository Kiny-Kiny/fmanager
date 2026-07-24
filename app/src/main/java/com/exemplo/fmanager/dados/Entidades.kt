package com.exemplo.fmanager.dados

import androidx.room.*

@Entity(tableName = "ligas")
data class Liga(
    @PrimaryKey val id: Int,
    val nome: String,
    val pais: String,
    val nivel: Int = 1,          // 1 = primeira divisão
    val reputacao: Int = 50,     // 0..100, influencia o mercado
)

@Entity(
    tableName = "clubes",
    indices = [Index("ligaId")],
)
data class Clube(
    @PrimaryKey val id: Int,
    val nome: String,
    val ligaId: Int,
    val reputacao: Int = 50,     // 0..100
    val caixaEur: Long = 0,      // dinheiro disponível
    val folhaMaxEur: Long = 0,   // teto salarial semanal
    val urlEscudo: String? = null,
)

/** Contrato do jogador com o clube. Separado de Jogador porque muda
 *  toda temporada, enquanto os atributos base mudam por treino. */
@Entity(
    tableName = "contratos",
    primaryKeys = ["jogadorId"],
    indices = [Index("clubeId")],
)
data class Contrato(
    val jogadorId: Int,
    val clubeId: Int,
    val salarioSemanalEur: Long,
    val terminaEmTemporada: Int,
    val clausulaRescisaoEur: Long = 0,
    val moral: Int = 70,         // 0..100
    val condicao: Int = 100,     // 0..100, cai jogando e sobe descansando
    val semanasLesionado: Int = 0,
)

@Entity(
    tableName = "partidas",
    indices = [Index("temporada", "rodada"), Index("ligaId")],
)
data class Partida(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val temporada: Int,
    val rodada: Int,
    val ligaId: Int,
    val mandanteId: Int,
    val visitanteId: Int,
    val golsMandante: Int? = null,   // null = ainda não jogada
    val golsVisitante: Int? = null,
)

/** Estado da carreira. Só existe uma linha (id = 1). */
@Entity(tableName = "carreira")
data class Carreira(
    @PrimaryKey val id: Int = 1,
    val clubeId: Int,
    val temporada: Int = 1,
    val rodada: Int = 1,
    val nomeTreinador: String = "Treinador",
)

/** Linha da tabela de classificação. Calculada, não armazenada. */
data class LinhaTabela(
    val clubeId: Int,
    val nome: String,
    val jogos: Int,
    val vitorias: Int,
    val empates: Int,
    val derrotas: Int,
    val golsPro: Int,
    val golsContra: Int,
) {
    val saldo get() = golsPro - golsContra
    val pontos get() = vitorias * 3 + empates
}
