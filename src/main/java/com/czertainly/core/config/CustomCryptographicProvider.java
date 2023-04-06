package com.czertainly.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.*;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherSpi;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;


/**
 * Provider for decrypting with Cryptographic Provider REST API.
 */
public class CustomCryptographicProvider extends Provider {

    private static final long serialVersionUID = 1L;

    public static final String PROVIDER_NAME = "CryptographicConnectorProvider";

    public CustomCryptographicProvider(String name) {
        super(name, 1.0, PROVIDER_NAME);
        put("Cipher.RSA" , CryptographicProviderCipher.RSA.class.getName());
    }
}