package indie.fiatsiz.anitv

import android.app.Application
import android.os.Build
import org.conscrypt.Conscrypt
import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class AniTVApplication : Application() {
    companion object {
        internal lateinit var openingHttpClient: OkHttpClient
            private set
    }

    override fun onCreate() {
        super.onCreate()
        val openingClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .followRedirects(false)
            .retryOnConnectionFailure(false)
        if (Build.VERSION.SDK_INT < 29) {
            val root = if (Build.VERSION.SDK_INT < 26) {
                // Android 6/7.0/7.1.0 lack ISRG Root X1; keep normal chain and hostname validation.
                resources.openRawResource(R.raw.isrg_root_x1).use {
                    CertificateFactory.getInstance("X.509").generateCertificate(it)
                }
            } else null
            val trust = legacyTrustManager(root)
            // AniSkip requires TLS 1.3; select its provider only for HTTPS, not Keystore cryptography.
            val tls = SSLContext.getInstance("TLS", Conscrypt.newProvider()).apply {
                init(null, arrayOf(trust), null)
            }
            openingClient.sslSocketFactory(tls.socketFactory, trust)
            if (Build.VERSION.SDK_INT < 26) {
                val platformTls = SSLContext.getInstance("TLS").apply {
                    init(null, arrayOf(trust), null)
                }
                HttpsURLConnection.setDefaultSSLSocketFactory(platformTls.socketFactory)
            }
        }
        openingHttpClient = openingClient.build()
    }
}

internal fun legacyTrustManager(extraRoot: Certificate?): X509TrustManager {
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    factory.init(null as KeyStore?)
    val system = factory.trustManagers.filterIsInstance<X509TrustManager>().single()
    if (extraRoot == null) return system
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
