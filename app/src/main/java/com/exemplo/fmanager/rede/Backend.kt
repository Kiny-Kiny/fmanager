package com.exemplo.fmanager.rede

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/*
 * CLIENTE DE BACKEND — PocketBase.
 *
 * A escolha aqui é a decisão mais importante deste módulo, então vale
 * explicar.
 *
 * O pedido foi: multijogador fora da rede local, mas sem construir um
 * servidor. Os projetos de referência (lollito/fm) resolvem com Spring
 * Boot + MySQL + MongoDB + Redis + Nginx + Prometheus via docker-compose.
 * Isso é exatamente escrever e MANTER um servidor.
 *
 * PocketBase resolve o mesmo problema com um binário único de ~15 MB:
 * SQLite embutido, REST automático, autenticação, realtime e painel de
 * administração. O dono do app roda `./pocketbase serve` numa VPS
 * qualquer, cria as coleções pelo painel, e não escreve uma linha de
 * código de servidor.
 *
 * O ENDEREÇO NÃO É FIXO NO CÓDIGO. Cada pessoa aponta para a instância
 * que quiser — a sua, a de um amigo, uma comunitária. Não existe servidor
 * oficial nem ponto único de falha.
 *
 * Sem Retrofit, sem OkHttp: HttpURLConnection e org.json já vêm no
 * Android, e menos dependência é menos coisa para quebrar.
 */

class Backend(private var baseUrl: String) {

    private var token: String? = null
    var usuarioId: String? = null
        private set
    var apelido: String? = null
        private set

    val conectado: Boolean get() = token != null

    fun definirServidor(url: String) {
        baseUrl = url.trimEnd('/')
    }

    val servidor: String get() = baseUrl

    // ----------------------------------------------------- AUTENTICAÇÃO

    /** Cria conta. O apelido é o nome que aparece no ranking. */
    suspend fun registrar(
        email: String,
        senha: String,
        apelido: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val corpo = JSONObject().apply {
            put("email", email)
            put("password", senha)
            put("passwordConfirm", senha)
            put("apelido", apelido)
        }
        requisitar("POST", "/api/collections/tecnicos/records", corpo, autenticado = false)
            .map { }
    }

    suspend fun entrar(email: String, senha: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val corpo = JSONObject().apply {
                put("identity", email)
                put("password", senha)
            }
            requisitar(
                "POST", "/api/collections/tecnicos/auth-with-password",
                corpo, autenticado = false,
            ).map { resposta ->
                token = resposta.optString("token").ifBlank { null }
                val registro = resposta.optJSONObject("record")
                usuarioId = registro?.optString("id")
                apelido = registro?.optString("apelido")
            }
        }

    fun sair() {
        token = null
        usuarioId = null
        apelido = null
    }

    // -------------------------------------------------------- COLEÇÕES

    suspend fun criar(colecao: String, dados: JSONObject): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            requisitar("POST", "/api/collections/$colecao/records", dados)
        }

    suspend fun atualizar(
        colecao: String,
        id: String,
        dados: JSONObject,
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        requisitar("PATCH", "/api/collections/$colecao/records/$id", dados)
    }

    /**
     * Lista registros com filtro no dialeto do PocketBase.
     *
     * Exemplo: `filtrar("desafios", "estado='aberto' && dono!='$eu'")`
     */
    suspend fun listar(
        colecao: String,
        filtro: String? = null,
        ordem: String? = null,
        limite: Int = 30,
    ): Result<List<JSONObject>> = withContext(Dispatchers.IO) {
        val parametros = buildList {
            add("perPage=$limite")
            filtro?.let { add("filter=" + URLEncoder.encode("($it)", "UTF-8")) }
            ordem?.let { add("sort=" + URLEncoder.encode(it, "UTF-8")) }
        }.joinToString("&")

        requisitar(
            "GET", "/api/collections/$colecao/records?$parametros", null,
        ).map { resposta ->
            val itens = resposta.optJSONArray("items") ?: JSONArray()
            (0 until itens.length()).map { itens.getJSONObject(it) }
        }
    }

    suspend fun buscarUm(colecao: String, id: String): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            requisitar("GET", "/api/collections/$colecao/records/$id", null)
        }

    suspend fun apagar(colecao: String, id: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            requisitar("DELETE", "/api/collections/$colecao/records/$id", null)
                .map { }
        }

    /** Confere se o servidor responde antes de tentar qualquer coisa. */
    suspend fun testar(): Result<String> = withContext(Dispatchers.IO) {
        requisitar("GET", "/api/health", null, autenticado = false)
            .map { it.optString("message").ifBlank { "servidor no ar" } }
    }

    // ------------------------------------------------------- TRANSPORTE

    private fun requisitar(
        metodo: String,
        caminho: String,
        corpo: JSONObject?,
        autenticado: Boolean = true,
    ): Result<JSONObject> = runCatching {
        val conexao = (URL("$baseUrl$caminho").openConnection() as HttpURLConnection)
        conexao.requestMethod = metodo
        conexao.connectTimeout = 12_000
        conexao.readTimeout = 15_000
        conexao.setRequestProperty("Content-Type", "application/json")
        if (autenticado) token?.let {
            conexao.setRequestProperty("Authorization", it)
        }

        if (corpo != null) {
            conexao.doOutput = true
            DataOutputStream(conexao.outputStream).use { saida ->
                saida.write(corpo.toString().toByteArray())
            }
        }

        val codigo = conexao.responseCode
        val texto = if (codigo in 200..299) {
            conexao.inputStream.bufferedReader().use { it.readText() }
        } else {
            val erro = conexao.errorStream?.bufferedReader()?.use { it.readText() }
            conexao.disconnect()
            // A mensagem do PocketBase é útil; repassar é melhor que um
            // "erro de rede" genérico que não diz o que corrigir.
            val motivo = runCatching {
                JSONObject(erro ?: "{}").optString("message")
            }.getOrNull()?.ifBlank { null }
            error(motivo ?: "HTTP $codigo")
        }
        conexao.disconnect()

        if (texto.isBlank()) JSONObject() else JSONObject(texto)
    }
}
