package indie.fiatsiz.anitv

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class LegacyTlsTest {
    @Test
    fun addsVerifiedPublicRootAndPreservesSystemTrustWithoutAcceptingUnknownCertificates() {
        val parser = CertificateFactory.getInstance("X.509")
        val root = File("src/main/res/raw/isrg_root_x1.pem").inputStream().use {
            parser.generateCertificate(it) as X509Certificate
        }
        assertEquals(
            "96bcec06264976f37460779acf28c5a7cfe8a3c0aae11a8ffcee05c0bddf08c6",
            MessageDigest.getInstance("SHA-256").digest(root.encoded).joinToString("") { "%02x".format(it) }
        )
        root.checkValidity()
        val systemFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        systemFactory.init(null as KeyStore?)
        val system = systemFactory.trustManagers.filterIsInstance<X509TrustManager>().single()
        val combined = legacyTrustManager(root)
        assertTrue(combined.acceptedIssuers.toSet().containsAll(system.acceptedIssuers.toSet()))
        assertTrue(root in combined.acceptedIssuers)
        val untrusted = javaClass.getResourceAsStream("/tls/untrusted.pem")!!.use {
            parser.generateCertificate(it) as X509Certificate
        }
        assertThrows(CertificateException::class.java) {
            combined.checkServerTrusted(arrayOf(untrusted), "RSA")
        }
    }
}
