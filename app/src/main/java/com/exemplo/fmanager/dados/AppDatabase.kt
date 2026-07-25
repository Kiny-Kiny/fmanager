package com.exemplo.fmanager.dados

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import com.exemplo.fmanager.sistemas.Observacao
import com.exemplo.fmanager.sistemas.RetratoJogador
import com.exemplo.fmanager.sistemas.Titulo
import com.exemplo.fmanager.sistemas.Inscricao
import com.exemplo.fmanager.sistemas.Cargo
import com.exemplo.fmanager.sistemas.MembroComissao
import com.exemplo.fmanager.sistemas.Torneio
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
interface TorneioDao {
    @Query("SELECT * FROM torneios WHERE temporada = :temporada ORDER BY id DESC")
    suspend fun daTemporada(temporada: Int): List<Torneio>

    @Query("SELECT * FROM torneios ORDER BY id DESC")
    suspend fun todos(): List<Torneio>

    @Query("SELECT * FROM torneios WHERE id = :id")
    suspend fun porId(id: Int): Torneio?

    @Insert suspend fun criar(t: Torneio): Long
    @Update suspend fun atualizar(t: Torneio)
    @Query("DELETE FROM torneios WHERE id = :id") suspend fun apagar(id: Int)
}

@Dao
interface ComissaoDao {
    @Query("SELECT * FROM comissao WHERE clubeId = :clubeId")
    suspend fun doClube(clubeId: Int): List<MembroComissao>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun contratar(m: MembroComissao)

    @Query("DELETE FROM comissao WHERE clubeId = :clubeId AND cargo = :cargo")
    suspend fun demitirDoCargo(clubeId: Int, cargo: String)

    @Query("DELETE FROM comissao WHERE id = :id")
    suspend fun demitir(id: Int)
}

@Dao
interface InscricaoDao {
    @Query("SELECT jogadorId FROM inscricoes WHERE torneioId = :torneioId AND clubeId = :clubeId")
    suspend fun ids(torneioId: Int, clubeId: Int): List<Int>

    @Query("SELECT COUNT(*) FROM inscricoes WHERE torneioId = :torneioId AND clubeId = :clubeId")
    suspend fun quantos(torneioId: Int, clubeId: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inscrever(lista: List<Inscricao>)

    @Query("DELETE FROM inscricoes WHERE torneioId = :torneioId AND clubeId = :clubeId")
    suspend fun limpar(torneioId: Int, clubeId: Int)
}

@Dao
interface TituloDao {
    @Query("SELECT * FROM titulos WHERE clubeId = :clubeId ORDER BY temporada DESC")
    suspend fun doClube(clubeId: Int): List<Titulo>

    @Query("SELECT COUNT(*) FROM titulos WHERE clubeId = :clubeId")
    suspend fun quantos(clubeId: Int): Int

    @Insert suspend fun registrar(t: Titulo)

    @Query("""
        SELECT COUNT(*) FROM titulos
        WHERE clubeId = :clubeId AND nomeDaCompeticao = :nome
          AND temporada = :temporada
    """)
    suspend fun jaRegistrado(clubeId: Int, nome: String, temporada: Int): Int
}

@Dao
interface RetratoDao {
    @Query("SELECT * FROM retratos WHERE jogadorId = :id AND temporada = :temporada")
    suspend fun de(id: Int, temporada: Int): RetratoJogador?

    @Query("SELECT * FROM retratos WHERE temporada = :temporada")
    suspend fun daTemporada(temporada: Int): List<RetratoJogador>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvarTodos(lista: List<RetratoJogador>)

    @Query("SELECT COUNT(*) FROM retratos WHERE temporada = :temporada")
    suspend fun total(temporada: Int): Int
}

@Dao
interface ObservacaoDao {
    @Query("SELECT * FROM observacoes WHERE jogadorId = :id")
    suspend fun de(id: Int): Observacao?

    @Query("SELECT * FROM observacoes WHERE nivel > 0")
    suspend fun ativas(): List<Observacao>

    @Query("SELECT nivel FROM observacoes WHERE jogadorId = :id")
    suspend fun nivelDe(id: Int): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvar(o: Observacao)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvarTodas(lista: List<Observacao>)

    @Query("DELETE FROM observacoes WHERE jogadorId = :id")
    suspend fun parar(id: Int)
}

@Dao
interface ImpressaoDao {
    /**
     * Assinatura da base de jogadores.
     *
     * Total, soma dos ids e soma dos overalls. Não é criptográfico, mas
     * pega qualquer diferença de dataset — e é o que os dois clientes
     * comparam antes de começar uma partida em rede.
     */
    @Query("""
        SELECT COUNT(*) || ':' || COALESCE(SUM(id), 0) || ':' ||
               COALESCE(SUM(geral), 0) || ':' || COALESCE(SUM(potencial), 0)
        FROM jogadores
    """)
    suspend fun daBase(): String
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

/**
 * Conversores de tipo.
 *
 * Room não sabe persistir enum sozinho — precisa de conversor explícito.
 * Guardo pelo NOME, não pelo ordinal: se eu reordenar o enum um dia, os
 * dados salvos continuam válidos. Ordinal quebraria em silêncio.
 */
class Conversores {
    @TypeConverter
    fun cargoParaTexto(c: Cargo): String = c.name

    @TypeConverter
    fun textoParaCargo(t: String): Cargo =
        runCatching { Cargo.valueOf(t) }.getOrDefault(Cargo.AUXILIAR)
}

@Database(
    entities = [Jogador::class, Clube::class, Liga::class,
        Contrato::class, Partida::class, Carreira::class,
        EstatisticaJogador::class, Observacao::class, RetratoJogador::class,
        Torneio::class, Titulo::class, Inscricao::class,
        MembroComissao::class],
    version = 7,
    exportSchema = false,
)
@TypeConverters(Conversores::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun jogadores(): JogadorDao
    abstract fun clubes(): ClubeDao
    abstract fun ligas(): LigaDao
    abstract fun contratos(): ContratoDao
    abstract fun partidas(): PartidaDao
    abstract fun carreira(): CarreiraDao
    abstract fun estatisticas(): EstatisticaDao
    abstract fun impressao(): ImpressaoDao
    abstract fun observacoes(): ObservacaoDao
    abstract fun retratos(): RetratoDao
    abstract fun torneios(): TorneioDao
    abstract fun titulos(): TituloDao
    abstract fun inscricoes(): InscricaoDao
    abstract fun comissao(): ComissaoDao

    companion object {
        @Volatile private var instancia: AppDatabase? = null

        /**
         * Versão 1 → 2: só cria a tabela de estatísticas.
         *
         * Migração explícita em vez de recriar o banco. Recriar apagaria
         * os 16 mil jogadores importados e obrigaria a esperar a
         * importação inteira de novo.
         */
        /** Versão 6 → 7: comissão técnica. Aditiva. */
        private val MIGRACAO_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS comissao (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        clubeId INTEGER NOT NULL,
                        nome TEXT NOT NULL,
                        cargo TEXT NOT NULL,
                        competencia INTEGER NOT NULL,
                        salarioSemanalEur INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_comissao_clubeId " +
                        "ON comissao(clubeId)")
            }
        }

        /** Versão 5 → 6: inscrição de elenco por torneio. Aditiva. */
        private val MIGRACAO_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS inscricoes (
                        torneioId INTEGER NOT NULL,
                        clubeId INTEGER NOT NULL,
                        jogadorId INTEGER NOT NULL,
                        PRIMARY KEY(torneioId, jogadorId)
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_inscricoes_torneioId " +
                        "ON inscricoes(torneioId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_inscricoes_clubeId " +
                        "ON inscricoes(clubeId)")
            }
        }

        /** Versão 4 → 5: torneios customizados e palmarés. Aditiva. */
        private val MIGRACAO_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS torneios (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        nome TEXT NOT NULL,
                        temporada INTEGER NOT NULL,
                        formato TEXT NOT NULL,
                        clubes TEXT NOT NULL,
                        grupos TEXT NOT NULL DEFAULT '',
                        quantosPassam INTEGER NOT NULL DEFAULT 2,
                        campeaoId INTEGER
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_torneios_temporada " +
                        "ON torneios(temporada)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS titulos (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        clubeId INTEGER NOT NULL,
                        nomeDaCompeticao TEXT NOT NULL,
                        temporada INTEGER NOT NULL,
                        tipo TEXT NOT NULL
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_titulos_clubeId " +
                        "ON titulos(clubeId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_titulos_temporada " +
                        "ON titulos(temporada)")
            }
        }

        /** Versão 3 → 4: retratos por temporada. Aditiva. */
        private val MIGRACAO_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS retratos (
                        jogadorId INTEGER NOT NULL,
                        temporada INTEGER NOT NULL,
                        idade INTEGER NOT NULL,
                        geral INTEGER NOT NULL,
                        potencial INTEGER NOT NULL,
                        atributos TEXT NOT NULL,
                        PRIMARY KEY(jogadorId, temporada)
                    )
                """)
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_retratos_temporada " +
                            "ON retratos(temporada)"
                )
            }
        }

        /** Versão 2 → 3: tabela de observação de olheiro. Aditiva. */
        private val MIGRACAO_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS observacoes (
                        jogadorId INTEGER NOT NULL,
                        nivel INTEGER NOT NULL DEFAULT 0,
                        semanas INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(jogadorId)
                    )
                """)
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_observacoes_nivel " +
                            "ON observacoes(nivel)"
                )
            }
        }

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
                    .addMigrations(MIGRACAO_1_2, MIGRACAO_2_3, MIGRACAO_3_4, MIGRACAO_4_5, MIGRACAO_5_6, MIGRACAO_6_7)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instancia = it }
            }
    }
}
