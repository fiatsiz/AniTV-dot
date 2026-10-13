package indie.fiatsiz.anitv.source

import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec

class RulesPublicationTest {
    @Test fun validateSignAndVerifyCandidate() {
        val path = System.getenv("ANITV_RULES_DIR")
        assumeTrue("Run tools/publish-rules with the rules worktree", path != null)
        val directory = File(path!!).canonicalFile
        val payload = File(directory, "hianime.json").readBytes()
        val rules = SourceRules.parse(payload, 1)
        RulesFixtures.validate(File(directory, "fixtures"), rules)
        val keyFile = File(requireNotNull(System.getenv("ANITV_RULES_KEY")) { "Set ANITV_RULES_KEY to the external PKCS#8 DER private key" }).canonicalFile
        require(!keyFile.toPath().startsWith(directory.toPath())) { "Keep private key outside the rules worktree" }
        require(!keyFile.toPath().startsWith(File("..").canonicalFile.toPath())) { "Keep private key outside application worktree" }
        val permissions = java.nio.file.Files.getPosixFilePermissions(keyFile.toPath())
        require(permissions.none { it.name.startsWith("GROUP_") || it.name.startsWith("OTHERS_") }) { "Private key must be owner-only" }
        val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
        val envelope = RulesTestSupport.sign(payload, key)
        val verified = SignedRules.verify(envelope, SignedRules.publicKey, 1)
        val published = File(directory, "hianime.signed.json")
        if (published.isFile) verified.requireSuccessorOf(SignedRules.verify(published.readBytes(), SignedRules.publicKey, 1))
        if (System.getenv("ANITV_RULES_INITIAL") == "1") {
            val bundled = JSONObject(SignedRules.bundled.envelope().toString(Charsets.UTF_8))
            val candidate = JSONObject(envelope.toString(Charsets.UTF_8))
            require(candidate.getString("payload") == bundled.getString("payload") && candidate.getString("signature") == bundled.getString("signature")) {
                "Initial published rules must match bundled payload and signature"
            }
        }
        val output = if (System.getenv("ANITV_RULES_INITIAL") == "1") SignedRules.bundled.envelope() else envelope
        val temp = File(directory, "hianime.signed.json.tmp")
        try {
            java.io.FileOutputStream(temp).use { it.write(output); it.fd.sync() }
            java.nio.file.Files.move(temp.toPath(), published.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } finally { temp.delete() }
    }
}
