package com.exemplo.fmanager.dados

import android.content.Context
import androidx.room.*

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

@Database(
    entities = [Jogador::class, Clube::class, Liga::class,
        Contrato::class, Partida::class, Carreira::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun jogadores(): JogadorDao
    abstract fun clubes(): ClubeDao
    abstract fun ligas(): LigaDao
    abstract fun contratos(): ContratoDao
    abstract fun partidas(): PartidaDao
    abstract fun carreira(): CarreiraDao

    companion object {
        @Volatile private var instancia: AppDatabase? = null

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
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instancia = it }
            }
    }
}
