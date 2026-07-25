package com.exemplo.fmanager.dados

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Dao
interface ClubeDao {
    @Query("SELECT * FROM clubes WHERE id = :id")
    suspend fun porId(id: Int): Clube?

    @Query("SELECT * FROM clubes WHERE ligaId = :ligaId ORDER BY reputacao DESC")
    suspend fun porLiga(ligaId: Int): List<Clube>

    @Query("SELECT * FROM clubes ORDER BY reputacao DESC LIMIT :limite")
    suspend fun todos(limite: Int = 1000): List<Clube>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodos(clubes: List<Clube>)

    @Update suspend fun atualizar(clube: Clube)
}

@Dao
interface LigaDao {
    @Query("SELECT * FROM ligas ORDER BY reputacao DESC")
    suspend fun todas(): List<Liga>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirTodas(ligas: List<Liga>)
}

@Dao
interface ContratoDao {
    @Query("SELECT * FROM contratos WHERE clubeId = :clubeId")
    suspend fun doClube(clubeId: Int): List<Contrato>

    @Query("SELECT * FROM contratos WHERE jogadorId = :jogadorId")
    suspend fun doJogador(jogadorId: Int): Contrato?

    @Query("SELECT COALESCE(SUM(salarioSemanalEur), 0) FROM contratos WHERE clubeId = :clubeId")
    suspend fun folhaSalarial(clubeId: Int): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvar(contrato: Contrato)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvarTodos(contratos: List<Contrato>)

    @Query("DELETE FROM contratos WHERE jogadorId = :jogadorId")
    suspend fun remover(jogadorId: Int)
}

@Dao
interface PartidaDao {
    @Query("SELECT * FROM partidas WHERE temporada = :temporada AND ligaId = :ligaId ORDER BY rodada")
    suspend fun daTemporada(temporada: Int, ligaId: Int): List<Partida>

    @Query("""
        SELECT * FROM partidas
        WHERE temporada = :temporada AND rodada = :rodada AND ligaId = :ligaId
    """)
    suspend fun daRodada(temporada: Int, rodada: Int, ligaId: Int): List<Partida>

    @Query("""
        SELECT * FROM partidas
        WHERE temporada = :temporada AND ligaId = :ligaId
          AND (mandanteId = :clubeId OR visitanteId = :clubeId)
          AND golsMandante IS NULL
        ORDER BY rodada LIMIT 1
    """)
    suspend fun proximoJogo(temporada: Int, ligaId: Int, clubeId: Int): Partida?

    @Insert suspend fun inserirTodas(partidas: List<Partida>)
    @Update suspend fun atualizar(partida: Partida)

    @Query("SELECT COUNT(*) FROM partidas WHERE temporada = :temporada")
    suspend fun total(temporada: Int): Int
}

@Dao
interface CarreiraDao {
    @Query("SELECT * FROM carreira WHERE id = 1")
    suspend fun atual(): Carreira?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvar(carreira: Carreira)
}

@Dao
interface EstatisticaDao {

    @Query("SELECT * FROM estatisticas WHERE jogadorId = :id AND temporada = :temporada")
    suspend fun de(id: Int, temporada: Int): EstatisticaJogador?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvar(e: EstatisticaJogador)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvarTodas(lista: List<EstatisticaJogador>)

    /** Artilharia da liga inteira, cruzando com os dados do jogador. */
    @Query("""
        SELECT e.jogadorId AS jogadorId, j.nome AS nome, j.clube AS clube,
               j.urlFoto AS urlFoto, j.geral AS geral,
               e.gols AS gols, e.assistencias AS assistencias, e.jogos AS jogos
        FROM estatisticas e
        JOIN jogadores j ON j.id = e.jogadorId
        WHERE e.temporada = :temporada AND e.gols > 0
        ORDER BY e.gols DESC, e.assistencias DESC
        LIMIT :limite
    """)
    suspend fun artilheiros(temporada: Int, limite: Int = 20): List<Artilheiro>

    @Query("""
        SELECT e.jogadorId AS jogadorId, j.nome AS nome, j.clube AS clube,
               j.urlFoto AS urlFoto, j.geral AS geral,
               e.gols AS gols, e.assistencias AS assistencias, e.jogos AS jogos
        FROM estatisticas e
        JOIN jogadores j ON j.id = e.jogadorId
        WHERE e.temporada = :temporada AND e.clubeId = :clubeId
        ORDER BY e.gols DESC, e.assistencias DESC
        LIMIT :limite
    """)
    suspend fun doClube(
        temporada: Int, clubeId: Int, limite: Int = 30,
    ): List<Artilheiro>
}

@Database(
    entities = [Jogador::class, Clube::class, Liga::class,
        Contrato::class, Partida::class, Carreira::class,
        EstatisticaJogador::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun jogadores(): JogadorDao
    abstract fun clubes(): ClubeDao
    abstract fun ligas(): LigaDao
    abstract fun contratos(): ContratoDao
    abstract fun partidas(): PartidaDao
    abstract fun carreira(): CarreiraDao
    abstract fun estatisticas(): EstatisticaDao

    companion object {
        @Volatile private var instancia: AppDatabase? = null

        /**
         * Versão 1 → 2: só cria a tabela de estatísticas.
         *
         * Migração explícita em vez de recriar o banco. Recriar apagaria
         * os 16 mil jogadores importados e obrigaria a esperar a
         * importação inteira de novo.
         */
        private val MIGRACAO_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS estatisticas (
                        jogadorId INTEGER NOT NULL,
                        temporada INTEGER NOT NULL,
                        clubeId INTEGER NOT NULL,
                        jogos INTEGER NOT NULL DEFAULT 0,
                        gols INTEGER NOT NULL DEFAULT 0,
                        assistencias INTEGER NOT NULL DEFAULT 0,
                        amarelos INTEGER NOT NULL DEFAULT 0,
                        vermelhos INTEGER NOT NULL DEFAULT 0,
                        somaNotas REAL NOT NULL DEFAULT 0,
                        PRIMARY KEY(jogadorId, temporada)
                    )
                """)
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_estatisticas_temporada " +
                            "ON estatisticas(temporada)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_estatisticas_clubeId " +
                            "ON estatisticas(clubeId)"
                )
            }
        }

        fun obter(context: Context): AppDatabase =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fmanager.db",
                )
                    // Se um dia você quiser embutir o banco já pronto
                    // em vez de importar o CSV na primeira execução:
                    // .createFromAsset("fmanager.db")
                    .addMigrations(MIGRACAO_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instancia = it }
            }
    }
}
