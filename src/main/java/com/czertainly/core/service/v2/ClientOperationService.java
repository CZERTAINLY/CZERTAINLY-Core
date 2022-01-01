package com.czertainly.core.service.v2;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.common.AttributeDefinition;
import com.czertainly.api.model.common.RequestAttributeDto;
import com.czertainly.api.model.core.v2.ClientCertificateDataResponseDto;
import com.czertainly.api.model.core.v2.ClientCertificateRenewRequestDto;
import com.czertainly.api.model.core.v2.ClientCertificateRevocationDto;
import com.czertainly.api.model.core.v2.ClientCertificateSignRequestDto;

import java.security.cert.CertificateException;
import java.util.List;

public interface ClientOperationService {

    List<AttributeDefinition> listIssueCertificateAttributes(
            String raProfileUuid) throws NotFoundException, ConnectorException;

    boolean validateIssueCertificateAttributes(
            String raProfileUuid,
            List<RequestAttributeDto> attributes) throws NotFoundException, ConnectorException, ValidationException;

    ClientCertificateDataResponseDto issueCertificate(
            String raProfileUuid,
            ClientCertificateSignRequestDto request) throws NotFoundException, ConnectorException, AlreadyExistException, CertificateException;

    ClientCertificateDataResponseDto renewCertificate(
            String raProfileUuid,
            String certificateUuid,
            ClientCertificateRenewRequestDto request) throws NotFoundException, ConnectorException, AlreadyExistException, CertificateException;

    List<AttributeDefinition> listRevokeCertificateAttributes(
            String raProfileUuid) throws NotFoundException, ConnectorException;

    boolean validateRevokeCertificateAttributes(
            String raProfileUuid,
            List<RequestAttributeDto> attributes) throws NotFoundException, ConnectorException, ValidationException;

    void revokeCertificate(
            String raProfileUuid,
            String certificateUuid,
            ClientCertificateRevocationDto request) throws NotFoundException, ConnectorException;
}
