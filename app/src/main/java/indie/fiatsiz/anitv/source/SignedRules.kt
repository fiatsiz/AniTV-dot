package indie.fiatsiz.anitv.source

import org.json.JSONObject
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

class SignedRules private constructor(val rules: SourceRules, payload: ByteArray, envelope: ByteArray) {
    private val payloadBytes = payload.copyOf()
    private val envelopeBytes = envelope.copyOf()
    fun envelope(): ByteArray = envelopeBytes.copyOf()

    fun requireSuccessorOf(current: SignedRules) {
        require(rules.revision >= current.rules.revision) { "Rules revision rollback" }
        require(rules.revision != current.rules.revision || payloadBytes.contentEquals(current.payloadBytes)) { "Rules revision conflict" }
    }

    companion object {
        const val MAX_BYTES = 256 * 1024
        val publicKey: PublicKey by lazy {
            KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(
                SignedRules::class.java.getResourceAsStream("/hianime-public.der")!!.use { it.readBytes() }))
        }
        val bundled: SignedRules by lazy {
            verify(SignedRules::class.java.getResourceAsStream("/hianime.signed.json")!!.use { it.readBytes() }, publicKey, 1)
        }
        fun verify(envelope: ByteArray, publicKey: PublicKey, appVersionCode: Int): SignedRules {
            require(envelope.size <= MAX_BYTES) { "Rules envelope too large" }
            require((publicKey as? RSAPublicKey)?.modulus?.bitLength() == 3072) { "Rules require RSA-3072" }
            val json = JSONObject(envelope.toString(Charsets.UTF_8))
            val payload = Base64.getDecoder().decode(json.getString("payload"))
            val signature = Base64.getDecoder().decode(json.getString("signature"))
            require(Signature.getInstance("SHA256withRSA").run {
                initVerify(publicKey); update(payload); verify(signature)
            }) { "Invalid rules signature" }
            return SignedRules(SourceRules.parse(payload, appVersionCode), payload, envelope)
        }
    }
}
