package com.exemplo.fmanager.motor

/*
 * LANCES E NARRAÇÃO.
 *
 * Cada lance tem autor. Isso é o que muda tudo: antes a bola vagava
 * sozinha, agora todo movimento sai da decisão de um jogador, e a
 * narração é só a descrição do que ele fez.
 */

enum class Importancia { ROTINA, DESTAQUE, DECISIVO }

sealed interface Lance {
    val minuto: Int
    val time: String
    val importancia: Importancia
    fun narrar(): String

    // ------------------------------------------------ CONSTRUÇÃO

    data class Passe(
        override val minuto: Int, override val time: String,
        val de: String, val para: String, val longo: Boolean,
    ) : Lance {
        override val importancia = Importancia.ROTINA
        override fun narrar() =
            if (longo) "$de lança para $para" else "$de toca para $para"
    }

    data class PasseErrado(
        override val minuto: Int, override val time: String,
        val de: String, val interceptador: String?,
    ) : Lance {
        override val importancia = Importancia.ROTINA
        override fun narrar() = interceptador
            ?.let { "$de erra o passe, $it intercepta" }
            ?: "$de erra o passe e a bola sai"
    }

    data class Drible(
        override val minuto: Int, override val time: String,
        val autor: String, val marcador: String, val sucesso: Boolean,
    ) : Lance {
        override val importancia =
            if (sucesso) Importancia.DESTAQUE else Importancia.ROTINA
        override fun narrar() =
            if (sucesso) "$autor deixa $marcador no chão e avança"
            else "$marcador fica com a bola no duelo com $autor"
    }

    data class Conducao(
        override val minuto: Int, override val time: String,
        val autor: String,
    ) : Lance {
        override val importancia = Importancia.ROTINA
        override fun narrar() = "$autor conduz e ganha terreno"
    }

    // ---------------------------------------------------- DEFESA

    data class Desarme(
        override val minuto: Int, override val time: String,
        val autor: String, val vitima: String,
    ) : Lance {
        override val importancia = Importancia.ROTINA
        override fun narrar() = "$autor desarma $vitima"
    }

    data class Escanteio(
        override val minuto: Int, override val time: String,
        val cobrador: String, val cabeceio: String?, val perigoso: Boolean,
    ) : Lance {
        override val importancia =
            if (perigoso) Importancia.DESTAQUE else Importancia.ROTINA
        override fun narrar() = when {
            cabeceio != null && perigoso ->
                "Escanteio de $cobrador e $cabeceio cabeceia com perigo!"
            cabeceio != null -> "$cobrador cobra e $cabeceio cabeceia por cima"
            else -> "Escanteio de $cobrador afastado pela defesa"
        }
    }

    data class Impedimento(
        override val minuto: Int, override val time: String,
        val autor: String,
    ) : Lance {
        override val importancia = Importancia.ROTINA
        override fun narrar() = "$autor estava impedido"
    }

    // ----------------------------------------------------- FALTA

    data class Falta(
        override val minuto: Int, override val time: String,
        val infrator: String, val vitima: String, val perigosa: Boolean,
    ) : Lance {
        override val importancia =
            if (perigosa) Importancia.DESTAQUE else Importancia.ROTINA
        override fun narrar() =
            if (perigosa) "Falta de $infrator em $vitima em posição perigosa"
            else "Falta de $infrator em $vitima"
    }

    data class Penalti(
        override val minuto: Int, override val time: String,
        val infrator: String, val vitima: String,
    ) : Lance {
        override val importancia = Importancia.DECISIVO
        override fun narrar() =
            "PÊNALTI! $infrator derruba $vitima dentro da área"
    }

    data class CobrancaFalta(
        override val minuto: Int, override val time: String,
        val autor: String, val noGol: Boolean, val naBarreira: Boolean,
    ) : Lance {
        override val importancia = Importancia.DESTAQUE
        override fun narrar() = when {
            naBarreira -> "$autor cobra e a barreira bloqueia"
            noGol -> "$autor bate e o goleiro salva"
            else -> "$autor bate por cima do travessão"
        }
    }

    data class Cartao(
        override val minuto: Int, override val time: String,
        val autor: String, val vermelho: Boolean,
    ) : Lance {
        override val importancia =
            if (vermelho) Importancia.DECISIVO else Importancia.DESTAQUE
        override fun narrar() =
            if (vermelho) "CARTÃO VERMELHO para $autor! Time com um menos"
            else "Amarelo para $autor"
    }

    // ------------------------------------------------- FINALIZAÇÃO

    data class Chute(
        override val minuto: Int, override val time: String,
        val autor: String, val desfecho: Desfecho,
    ) : Lance {
        override val importancia = Importancia.DESTAQUE
        override fun narrar() = when (desfecho) {
            Desfecho.DEFENDIDO -> "$autor finaliza e o goleiro defende"
            Desfecho.PARA_FORA -> "$autor chuta para fora"
            Desfecho.NA_TRAVE -> "NA TRAVE! $autor quase abriu o placar"
            Desfecho.BLOQUEADO -> "Chute de $autor bloqueado pela defesa"
        }
    }

    enum class Desfecho { DEFENDIDO, PARA_FORA, NA_TRAVE, BLOQUEADO }

    data class Gol(
        override val minuto: Int, override val time: String,
        val autor: String, val assistencia: String?, val dePenalti: Boolean,
    ) : Lance {
        override val importancia = Importancia.DECISIVO
        override fun narrar() = when {
            dePenalti -> "GOL! $autor converte a cobrança"
            assistencia != null -> "GOL! $autor completa o passe de $assistencia"
            else -> "GOL DE $autor!"
        }
    }

    // -------------------------------------------------- ELENCO

    data class Substituicao(
        override val minuto: Int, override val time: String,
        val sai: String, val entra: String,
    ) : Lance {
        override val importancia = Importancia.DESTAQUE
        override fun narrar() = "Sai $sai, entra $entra"
    }

    data class Lesao(
        override val minuto: Int, override val time: String,
        val autor: String, val semanas: Int,
    ) : Lance {
        override val importancia = Importancia.DECISIVO
        override fun narrar() =
            "$autor sente e não consegue continuar ($semanas semanas)"
    }

    data class Apito(
        override val minuto: Int, override val time: String,
        val texto: String,
    ) : Lance {
        override val importancia = Importancia.DESTAQUE
        override fun narrar() = texto
    }
}

/** Estatísticas de uma equipe na partida. */
data class Estatisticas(
    val posse: Int = 50,
    val chutes: Int = 0,
    val chutesNoGol: Int = 0,
    val passes: Int = 0,
    val passesCertos: Int = 0,
    val faltas: Int = 0,
    val amarelos: Int = 0,
    val vermelhos: Int = 0,
    val impedimentos: Int = 0,
    val desarmes: Int = 0,
    val escanteios: Int = 0,
) {
    val precisaoPasse: Int
        get() = if (passes == 0) 0 else (passesCertos * 100) / passes
}
