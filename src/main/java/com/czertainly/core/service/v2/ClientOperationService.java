package com.czertainly.core.service.v2;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.CertificateOperationException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.common.attribute.AttributeDefinition;
import com.czertainly.api.model.common.attribute.RequestAttributeDto;
import com.czertainly.api.model.core.v2.ClientCertificateDataResponseDto;
import com.czertainly.api.model.core.v2.ClientCertificateRenewRequestDto;
import com.czertainly.api.model.core.v2.ClientCertificateRevocationDto;
import com.czertainly.api.model.core.v2.ClientCertificateSignRequestDto;
import com.czertainly.core.security.authz.SecuredParentUUID;
import com.czertainly.core.security.authz.SecuredUUID;

import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.List;

public interface ClientOperationService {

    List<AttributeDefinition> listIssueCertificateAttributes(
            SecuredParentUUID authorityUuid,
            SecuredUUID raProfileUuid) throws ConnectorException;

    boolean validateIssueCertificateAttributes(
            SecuredParentUUID authorityUuid,
            SecuredUUID raProfileUuid,
            List<RequestAttributeDto> attributes) throws ConnectorException, ValidationException;

    ClientCertificateDataResponseDto issueCertificate(
            SecuredParentUUID authorityUuid,
            SecuredUUID raProfileUuid,
            ClientCertificateSignRequestDto request) throws ConnectorException, AlreadyExistException, CertificateException, NoSuchAlgorithmException;

    ClientCertificateDataResponseDto renewCertificate(
            SecuredParentUUID authorityUuid,
            SecuredUUID raProfileUuid,
            String certificateUuid,
            ClientCertificateRenewRequestDto request) throws ConnectorException, AlreadyExistException, CertificateException, CertificateOperationException;

    List<AttributeDefinition> listRevokeCertificateAttributes(
            SecuredParentUUID authorityUuid,
            SecuredUUID raProfileUuid) throws ConnectorException;

    boolean validateRevokeCertificateAttributes(
            SecuredParentUUID authorityUuid,
            SecuredUUID raProfileUuid,
            List<RequestAttributeDto> attributes) throws ConnectorException, ValidationException;

    void revokeCertificate(
            SecuredParentUUID authorityUuid,
            SecuredUUID raProfileUuid,
            String certificateUuid,
            ClientCertificateRevocationDto request) throws ConnectorException;

    void checkIssuePermissions();

    void checkRenewPermissions();

    void checkRevokePermissions();
}
