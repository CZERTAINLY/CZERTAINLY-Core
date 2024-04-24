package com.czertainly.core.model.request;

public class CrmfCertificateRequest implements CertificateRequest {

    private final byte[] encoded;

    public CrmfCertificateRequest(byte[] request) {
        this.encoded = request;
    }

    @Override
    public byte[] getEncoded() {
        return encoded;
    }

}
