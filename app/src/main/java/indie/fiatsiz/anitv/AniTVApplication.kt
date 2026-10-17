package indie.fiatsiz.anitv

import android.app.Application
import android.os.Build
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class AniTVApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT < 26) {
            // Android 6/7.0/7.1.0 lack ISRG Root X1; keep normal chain and hostname validation.
            val root = resources.openRawResource(R.raw.isrg_root_x1).use {
                CertificateFactory.getInstance("X.509").generateCertificate(it)
            }
            val tls = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(legacyTrustManager(root)), null)
            }
            HttpsURLConnection.setDefaultSSLSocketFactory(tls.socketFactory)
        }
    }
}

internal fun legacyTrustManager(extraRoot: Certificate): X509TrustManager {
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    factory.init(null as KeyStore?)
    val system = factory.trustManagers.filterIsInstance<X509TrustManager>().single()
    val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
        load(null)
        system.acceptedIssuers.forEachIndexed { index, certificate ->
            setCertificateEntry("system-$index", certificate)
        }
        setCertificateEntry("isrg-root-x1", extraRoot)
    }
    factory.init(store)
    return factory.trustManagers.filterIsInstance<X509TrustManager>().single()
}
