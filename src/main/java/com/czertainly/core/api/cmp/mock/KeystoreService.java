package com.czertainly.core.api.cmp.mock;

import org.springframework.util.ResourceUtils;

import javax.crypto.SecretKey;
import java.io.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;

public class KeystoreService {

    public static File getFile(String nameOfFile) throws IOException {
        File file = ResourceUtils.getFile("classpath:"+nameOfFile);
        if(file.exists()){
            return file;
        }
        return null;
    }

    public static InputStream getFileAsStream(String nameOfFile) throws IOException {
        File file = ResourceUtils.getFile("classpath:"+nameOfFile);
        if(file.exists()){
            return new FileInputStream(file);
        }
        return null;
    }

    /**
     * Load key store (JKS or PKCS #12) from the specified file.
     *
     * @param filename name of key store file or absolutePath to key store file
     * @param password key store password
     * @return key store
     * @throws KeyStoreException if key store could not be loaded from <code>filename</code>
     */
    public static KeyStore loadKeystoreFromFile(String filename, char[] password) throws KeyStoreException {
        KeyStore ks;
        try {
            // guessing type of keystore
            if (filename.toLowerCase(Locale.getDefault()).endsWith(".p12")) {
                try (InputStream in = getFileAsStream(filename)) {
                    ks = loadKeystoreFromStream("PKCS12", in, password);
                } catch (KeyStoreException ex) {
                    try (InputStream in = getFileAsStream(filename)) {
                        ks = loadKeystoreFromStream("JKS", in, password);
                    }
                }
            } else {
                try (InputStream in = getFileAsStream(filename)) {
                    ks = loadKeystoreFromStream("JKS", in, password);
                } catch (KeyStoreException ex) {
                    try (InputStream in = getFileAsStream(filename)) {
                        ks = loadKeystoreFromStream("PKCS12", in, password);
                    }
                }
            }
            return ks;
        } catch (final IOException excpt) {
            throw new KeyStoreException(excpt);
        }
    }

    /**
     * Load key store (JKS or PKCS #12) from the specified stream.
     *
     * @param keyStoreType type of key store, either "JKS" or "PKCS12"
     * @param is           input stream of the key store file
     * @param password     key store password
     * @return key store
     * @throws KeyStoreException if key store could not be loaded from <code>is</code>
     */
    private static KeyStore loadKeystoreFromStream(
            String keyStoreType, InputStream is, char[] password) throws KeyStoreException {
        try {
            KeyStore ks = KeyStore.getInstance(keyStoreType);
            ks.load(is, password);
            return ks;
        } catch (IOException | CertificateException | NoSuchAlgorithmException excpt) {
            throw new KeyStoreException(excpt);
        }
    }


    public static Map<PrivateKey, LinkedList<X509Certificate>> loadKeyAndCertChain(KeyStore keyStore, char[] password)throws Exception {
        LinkedList<X509Certificate> chainOfTrustedCerts = new LinkedList<>();
        for (String alias : Collections.list(keyStore.aliases())) {
            Key privateKey = keyStore.getKey(alias, password);
            if (!(privateKey instanceof PrivateKey)) {
                continue;
            }
            Certificate certificate = keyStore.getCertificate(alias);
            if (!(certificate instanceof X509Certificate)) {
                continue;
            }
            Certificate[] keystoreChain = keyStore.getCertificateChain(alias);

            for (Certificate cert : keystoreChain) {
                chainOfTrustedCerts.add((X509Certificate) cert);
            }
            return Map.of((PrivateKey) privateKey/*privateKeyOfEndCertificate*/, chainOfTrustedCerts);
        }
        throw new SecurityException("there is no chain in Keystore");
    }


    /**
     * Create keyStore file, default type=pkcs12, with password
     *
     * @return keystore
     * @throws KeyStoreException
     *
    //String path="c:\\Users\\user\\Repositories\\czertainly_forks\\CZERTAINLY-Core\\src\\main\\resources\\";
     * @see <a href="https://docs.oracle.com/en/java/javase/21/docs/specs/security/standard-names.html#keystore-types">KeyStore Types, from javadoc</a>
     */
    public static KeyStore createKeystore(String password, String filePath, String fileName) throws
            KeyStoreException, CertificateException, IOException, NoSuchAlgorithmException {
        // -- create/init keystore
        KeyStore ks = KeyStore.getInstance("PKCS12");//KeyStore.getDefaultType()
        ks.load(null, password.toCharArray());
        // -- save keystore to file system
        try (FileOutputStream fos = new FileOutputStream(filePath+""+fileName+".p12")) {
            ks.store(fos, password.toCharArray());
        }
        return ks;
    }

    public static void saveSymmetricKey(KeyStore ks, SecretKey secretKey, String password) throws KeyStoreException {
        KeyStore.SecretKeyEntry secret = new KeyStore.SecretKeyEntry(secretKey);
        KeyStore.ProtectionParameter secretPassword
                = new KeyStore.PasswordProtection(password.toCharArray());
        ks.setEntry("db-encryption-secret", secret, secretPassword);
    }

    public static void saveAsymmetricKey(KeyStore ks, String alias,
                                         PrivateKey caPrivateKey, String privateKeyPassword,
                                         X509Certificate caCert, X509Certificate interCaCert)
            throws KeyStoreException {
        X509Certificate[] certificateChain = new X509Certificate[2];
        certificateChain[0] = interCaCert;
        certificateChain[1] = caCert;
        ks.setKeyEntry(alias, caPrivateKey,
                privateKeyPassword.toCharArray(), certificateChain);
    }
}
