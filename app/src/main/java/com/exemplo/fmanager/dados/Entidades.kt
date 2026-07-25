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

/**
 * Números do jogador na temporada. Tabela nova na versão 2 do banco.
 *
 * Entrou por MIGRAÇÃO, não por recriação: adicionar tabela é operação
 * segura, e destruir o banco custaria a reimportação dos 16 mil
 * jogadores. Ver AppDatabase.MIGRACAO_1_2.
 */
@Entity(
    tableName = "estatisticas",
    primaryKeys = ["jogadorId", "temporada"],
    indices = [Index("temporada"), Index("clubeId")],
)
data class EstatisticaJogador(
    val jogadorId: Int,
    val temporada: Int,
    val clubeId: Int,
    val jogos: Int = 0,
    val gols: Int = 0,
    val assistencias: Int = 0,
    val amarelos: Int = 0,
    val vermelhos: Int = 0,
    /** Soma das notas; divida por jogos para a média. */
    val somaNotas: Float = 0f,
) {
    val notaMedia: Float get() = if (jogos == 0) 0f else somaNotas / jogos
}

/** Linha da tabela de artilharia, já com o nome resolvido. */
data class Artilheiro(
    val jogadorId: Int,
    val nome: String,
    val clube: String,
    val urlFoto: String?,
    val geral: Int,
    val gols: Int,
    val assistencias: Int,
    val jogos: Int,
)
