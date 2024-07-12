package com.czertainly.core.model.auth;

import com.czertainly.api.model.core.enums.CertificateProtocol;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CertificateProtocolInfo {

    private CertificateProtocol protocol;
    private UUID protocolProfileUuid;
    private UUID additionalProtocolUuid;

    public static CertificateProtocolInfo Scep(UUID protocolProfileUuid) {
        CertificateProtocolInfo certificateProtocolInfo = new CertificateProtocolInfo();
        certificateProtocolInfo.setProtocol(CertificateProtocol.SCEP);
        certificateProtocolInfo.setProtocolProfileUuid(protocolProfileUuid);
        return certificateProtocolInfo;
    }

    public static CertificateProtocolInfo Acme(UUID protocolProfileUuid, UUID additionalProtocolUuid) {
        CertificateProtocolInfo certificateProtocolInfo = new CertificateProtocolInfo();
        certificateProtocolInfo.setProtocol(CertificateProtocol.ACME);
        certificateProtocolInfo.setProtocolProfileUuid(protocolProfileUuid);
        certificateProtocolInfo.setAdditionalProtocolUuid(additionalProtocolUuid);
        return certificateProtocolInfo;
    }

    public static CertificateProtocolInfo Cmp(UUID protocolProfileUuid) {
        CertificateProtocolInfo certificateProtocolInfo = new CertificateProtocolInfo();
        certificateProtocolInfo.setProtocol(CertificateProtocol.CMP);
        certificateProtocolInfo.setProtocolProfileUuid(protocolProfileUuid);
        return certificateProtocolInfo;
    }
}
