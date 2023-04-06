package com.czertainly.core.config;

import com.czertainly.api.clients.cryptography.CryptographicOperationsApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.*;
import java.security.*;
import java.security.spec.AlgorithmParameterSpec;

public class CryptographicProviderCipher extends CipherSpi {

    private static final Logger log = LoggerFactory.getLogger(CryptographicProviderCipher.class);

    private int opmode;
    private CustomPrivateKey privateKey;

    public static final class RSA extends CryptographicProviderCipher {
        public RSA() {
        }
    }

    @Override
    protected byte[] engineUpdate(byte[] b, int off, int len) {
        return null;
    }

    @Override
    protected int engineUpdate(byte[] arg0, int arg1, int arg2, byte[] arg3, int arg4) throws ShortBufferException {
        return 0;
    }

    @Override
    protected byte[] engineDoFinal(byte[] arg0, int arg1, int arg2) throws IllegalBlockSizeException, BadPaddingException {
        if (log.isDebugEnabled()) {
            log.debug("engineDoFinal1: " + this.getClass().getName() + ", opmode=" + this.opmode);
        }
        //TO call cryptography provider
        CryptographicOperationsApiClient apiClient = new CryptographicOperationsApiClient()
        return new byte[0];
    }

    @Override
    protected int engineDoFinal(byte[] arg0, int arg1, int arg2, byte[] arg3, int arg4)
            throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
        return 0;
    }

    @Override
    protected int engineGetBlockSize() {
        return 0;
    }

    @Override
    protected byte[] engineGetIV() {
        return null;
    }

    @Override
    protected int engineGetOutputSize(int arg0) {
        return 0;
    }

    @Override
    protected AlgorithmParameters engineGetParameters() {
        return null;
    }

    @Override
    protected void engineInit(int opmode, Key key, SecureRandom random) throws InvalidKeyException {
        if (log.isDebugEnabled()) {
            log.debug("engineInit1: " + this.getClass().getName());
        }
        this.opmode = opmode;
        if (this.opmode != Cipher.DECRYPT_MODE && this.opmode != Cipher.UNWRAP_MODE) {
            throw new IllegalArgumentException("Only DECRYPT_MODE (2) or UNWRAP_MODE (4) can be used: " + opmode);
        }
        this.privateKey = (CustomPrivateKey) key;
    }

    @Override
    protected void engineInit(int opmode, Key arg1, AlgorithmParameterSpec arg2, SecureRandom arg3)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
    }

    @Override
    protected void engineInit(int opmode, Key arg1, AlgorithmParameters arg2, SecureRandom arg3)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
    }

    @Override
    protected void engineSetMode(String mode) throws NoSuchAlgorithmException {
    }

    @Override
    protected void engineSetPadding(String arg0) throws NoSuchPaddingException {
    }

}
