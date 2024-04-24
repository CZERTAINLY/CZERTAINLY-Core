package com.czertainly.core.model.request;

public class Pkcs10CertificateRequest implements CertificateRequest {

    private final byte[] encoded;

    public Pkcs10CertificateRequest(byte[] request) {
        this.encoded = request;
    }

    @Override
    public byte[] getEncoded() {
        return encoded;
    }


}
