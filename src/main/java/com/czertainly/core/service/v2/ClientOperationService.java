package com.czertainly.core.service.v2;

import com.czertainly.api.exception.*;
import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.core.certificate.CertificateDetailDto;
import com.czertainly.api.model.core.v2.*;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.util.List;

public interface ClientOperationService {

    List<BaseAttribute> listIssueCertificateAttributes(
            SecuredParentUUID authorityUuid,
            SecuredUUID raProfileUuid
    ) throws ConnectorException;

    boolean validateIssueCertificateAttributes(
            SecuredParentUUID authorityUuid,
            SecuredUUID raProfileUuid,
            List<RequestAttributeDto> attributes
    ) throws ConnectorException, ValidationException;

    CertificateDetailDto createCsr(
            ClientCertificateRequestDto request
    ) throws NotFoundException, CertificateException, IOException, NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException;

    ClientCertificateDataResponseDto issueCertificate(
            SecuredParentUUID authorityUuid,
            SecuredUUID raProfileUuid,
            SecuredUUID certificateUuid
    ) throws ConnectorException, AlreadyExistException, CertificateException, NoSuchAlgorithmException;

    ClientCertificateDataResponseDto issueCertificate(
            SecuredParentUUID authorityUuid,
            SecuredUUID raProfileUuid,
            ClientCertificateSignRequestDto request
    ) throws ConnectorException, AlreadyExistException, CertificateException, NoSuchAlgorithmException;

    ClientCertificateDataResponseDto renewCertificate(
            SecuredParentUUID authorityUuid,
            SecuredUUID raProfileUuid,
            String certificateUuid,
            ClientCertificateRenewRequestDto request
    ) throws ConnectorException, AlreadyExistException, CertificateException, CertificateOperationException;

    ClientCertificateDataResponseDto rekeyCertificate(
            SecuredParentUUID authorityUuid,
            SecuredUUID raProfileUuid,
            String certificateUuid,
            ClientCertificateRekeyRequestDto request
    ) throws ConnectorException, AlreadyExistException, CertificateException, CertificateOperationException;

    List<BaseAttribute> listRevokeCertificateAttributes(
            SecuredParentUUID authorityUuid,
            SecuredUUID raProfileUuid) throws ConnectorException;

    boolean validateRevokeCertificateAttributes(
            SecuredParentUUID authorityUuid,
            SecuredUUID raProfileUuid,
            List<RequestAttributeDto> attributes
    ) throws ConnectorException, ValidationException;

    void revokeCertificate(
            SecuredParentUUID authorityUuid,
            SecuredUUID raProfileUuid,
            String certificateUuid,
            ClientCertificateRevocationDto request
    ) throws ConnectorException;
}
