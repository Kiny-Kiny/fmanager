package com.exemplo.fmanager.rede

import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/*
 * CRIPTOGRAFIA DA SALA.
 *
 * Ideia trazida do Crypto Football Game: chave de sessão nova a cada
 * partida e tudo assinado. Aqui isso não é enfeite educativo — é a única
 * defesa que existe, porque não há servidor para arbitrar nada.
 *
 * O que cada peça resolve:
 *
 *   ECDH        -> os dois derivam a mesma chave sem nunca transmiti-la
 *   AES-GCM     -> ninguém no mesmo Wi-Fi lê nem altera as mensagens
 *   ECDSA       -> nenhum lado forja um comando "do outro jogador"
 *   Impressão   -> os dois provam que têm a MESMA base de jogadores
 *   Código curto-> os dois confirmam a olho que não há intermediário
 *
 * Sem dependência externa: tudo vem de java.security e javax.crypto,
 * que já estão no Android.
 */

object Cripto {

    private const val CURVA = "secp256r1"
    private const val TAG_BITS = 128
    private val rng = SecureRandom()

    // ------------------------------------------------------- IDENTIDADE

    /** Par de chaves do dispositivo. Gerado por partida (efêmero). */
    fun gerarParDeChaves(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec(CURVA))
        }.generateKeyPair()

    fun exportarPublica(chave: PublicKey): String =
        Base64.encodeToString(chave.encoded, Base64.NO_WRAP)

    fun importarPublica(texto: String): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(
            X509EncodedKeySpec(Base64.decode(texto, Base64.NO_WRAP))
        )

    // -------------------------------------------------- CHAVE DE SESSÃO

    /**
     * Segredo compartilhado por ECDH.
     *
     * Os dois lados chegam ao mesmo valor sem que ele jamais passe pela
     * rede. Passa por SHA-256 para virar chave AES de 256 bits.
     */
    fun derivarSegredo(minhaPrivada: PrivateKey, publicaDoOutro: PublicKey): ByteArray {
        val acordo = KeyAgreement.getInstance("ECDH")
        acordo.init(minhaPrivada)
        acordo.doPhase(publicaDoOutro, true)
        return sha256(acordo.generateSecret())
    }

    /**
     * Código de 6 dígitos que os dois jogadores comparam a olho.
     *
     * Isto é o que fecha a brecha do intermediário: um atacante no meio
     * do caminho pode trocar as chaves públicas, mas não consegue fazer
     * os dois códigos baterem. Se os números diferem na tela dos dois,
     * alguém está no meio.
     */
    fun codigoDeVerificacao(
        segredo: ByteArray,
        publicaA: String,
        publicaB: String,
    ): String {
        // Ordem estável: o mesmo código dos dois lados.
        val (primeira, segunda) = listOf(publicaA, publicaB).sorted()
        val digest = sha256(segredo + primeira.toByteArray() + segunda.toByteArray())
        val n = ((digest[0].toInt() and 0xFF) shl 16) or
                ((digest[1].toInt() and 0xFF) shl 8) or
                (digest[2].toInt() and 0xFF)
        return "%06d".format(n % 1_000_000)
    }

    // ------------------------------------------------------- MENSAGENS

    /** Cifra com AES-GCM. O nonce vai junto, no começo. */
    fun cifrar(chave: ByteArray, texto: String): ByteArray {
        val nonce = ByteArray(12).also { rng.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(chave, "AES"),
            GCMParameterSpec(TAG_BITS, nonce),
        )
        return nonce + cipher.doFinal(texto.toByteArray())
    }

    /** Decifra. Devolve null se a mensagem foi alterada no caminho. */
    fun decifrar(chave: ByteArray, dados: ByteArray): String? = runCatching {
        val nonce = dados.copyOfRange(0, 12)
        val corpo = dados.copyOfRange(12, dados.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(chave, "AES"),
            GCMParameterSpec(TAG_BITS, nonce),
        )
        String(cipher.doFinal(corpo))
    }.getOrNull()

    // ------------------------------------------------------ ASSINATURA

    fun assinar(privada: PrivateKey, dados: String): String {
        val s = Signature.getInstance("SHA256withECDSA")
        s.initSign(privada)
        s.update(dados.toByteArray())
        return Base64.encodeToString(s.sign(), Base64.NO_WRAP)
    }

    fun verificar(publica: PublicKey, dados: String, assinatura: String): Boolean =
        runCatching {
            val s = Signature.getInstance("SHA256withECDSA")
            s.initVerify(publica)
            s.update(dados.toByteArray())
            s.verify(Base64.decode(assinatura, Base64.NO_WRAP))
        }.getOrDefault(false)

    // ------------------------------------------------------ IMPRESSÕES

    fun sha256(dados: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(dados)

    fun impressao(texto: String): String =
        Base64.encodeToString(sha256(texto.toByteArray()), Base64.NO_WRAP)

    /** Semente combinada: nenhum lado escolhe sozinho. */
    fun semeadura(nonceA: String, nonceB: String): Long {
        val (a, b) = listOf(nonceA, nonceB).sorted()
        val d = sha256((a + b).toByteArray())
        var v = 0L
        repeat(8) { i -> v = (v shl 8) or (d[i].toLong() and 0xFF) }
        return v
    }

    fun nonce(): String {
        val b = ByteArray(16).also { rng.nextBytes(it) }
        return Base64.encodeToString(b, Base64.NO_WRAP)
    }
}
