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
import com.czertainly.core.security.authz.SecuredUUID;

import java.security.cert.CertificateException;
import java.util.List;

public interface ClientOperationService {

    List<AttributeDefinition> listIssueCertificateAttributes(
            SecuredUUID raProfileUuid) throws ConnectorException;

    boolean validateIssueCertificateAttributes(
            SecuredUUID raProfileUuid,
            List<RequestAttributeDto> attributes) throws ConnectorException, ValidationException;

    ClientCertificateDataResponseDto issueCertificate(
            SecuredUUID raProfileUuid,
            ClientCertificateSignRequestDto request, Boolean ignoreAuthToRa) throws ConnectorException, AlreadyExistException, CertificateException;

    ClientCertificateDataResponseDto renewCertificate(
            SecuredUUID raProfileUuid,
            String certificateUuid,
            ClientCertificateRenewRequestDto request, Boolean ignoreAuthToRa) throws ConnectorException, AlreadyExistException, CertificateException, CertificateOperationException;

    List<AttributeDefinition> listRevokeCertificateAttributes(
            SecuredUUID raProfileUuid) throws ConnectorException;

    boolean validateRevokeCertificateAttributes(
            SecuredUUID raProfileUuid,
            List<RequestAttributeDto> attributes) throws ConnectorException, ValidationException;

    void revokeCertificate(
            SecuredUUID raProfileUuid,
            String certificateUuid,
            ClientCertificateRevocationDto request,
            Boolean ignoreAuthToRa) throws ConnectorException;
}
