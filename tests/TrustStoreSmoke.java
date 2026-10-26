import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.Set;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

// Run in a disposable image after adding a private CA with update-ca-certificates.
public class TrustStoreSmoke {
    public static void main(String[] args) throws Exception {
        if (args.length != 15) throw new IllegalArgumentException("Expected a private CA and fourteen incident chains");
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init((KeyStore) null);
        X509TrustManager manager = null;
        for (TrustManager candidate : factory.getTrustManagers()) {
            if (candidate instanceof X509TrustManager) manager = (X509TrustManager) candidate;
        }
        if (manager == null) throw new IllegalStateException("No X509 trust manager");
        Set<String> distrustedRoots = Set.of(
                "40F6AF0346A99AA1CD1D555A4E9CCE62C7F9634603EE406615833DC8C8D00367",
                "1AA0C2709E831BD6E3B5129A00BA41F7EEEF020872F1E6504BF0F6C3F24F3AF3",
                "86A1ECBA089C4A8D3BBE2734C612BA341D813E043CF9E8A862CD5C57A36BBE6B",
                "D7A7A0FB5D7E2731D771E9484EBCDEF71D5F0C3E0A2948782BC83EE0EA699EF4");
        for (X509Certificate root : manager.getAcceptedIssuers()) {
            StringBuilder fingerprint = new StringBuilder();
            for (byte value : MessageDigest.getInstance("SHA-256").digest(root.getEncoded())) {
                fingerprint.append(String.format("%02X", value));
            }
            if (distrustedRoots.contains(fingerprint.toString())) {
                throw new IllegalStateException("Incident root remains trusted: " + root.getSubjectX500Principal());
            }
        }
        for (int index = 0; index < args.length; index++) {
            X509Certificate[] chain;
            try (FileInputStream input = new FileInputStream(args[index])) {
                chain = CertificateFactory.getInstance("X.509").generateCertificates(input).toArray(new X509Certificate[0]);
            }
            boolean rejected = false;
            try {
                manager.checkServerTrusted(chain, "EC".equals(chain[0].getPublicKey().getAlgorithm()) ? "ECDHE_ECDSA" : "RSA");
            } catch (CertificateException error) {
                if (!error.toString().contains("unable to find valid certification path")
                        && !error.toString().contains("Untrusted certificate:")) throw error;
                rejected = true;
            }
            if (rejected != (index > 0)) throw new IllegalStateException("Unexpected trust result: " + args[index]);
        }
        System.out.println("Private CA accepted; four incident roots absent; fourteen incident chains rejected by the active Java trust store.");
    }
}
