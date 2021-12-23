package com.czertainly.core.service.v2;

import com.czertainly.api.core.v2.model.ClientCertificateDataResponseDto;
import com.czertainly.api.core.v2.model.ClientCertificateRenewRequestDto;
import com.czertainly.api.core.v2.model.ClientCertificateRevocationDto;
import com.czertainly.api.core.v2.model.ClientCertificateSignRequestDto;
import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.AttributeDefinition;
import com.czertainly.api.model.ClientAttributeDefinition;
import com.czertainly.api.v2.model.ca.CertRevocationDto;
import com.czertainly.api.v2.model.ca.CertificateDataResponseDto;
import com.czertainly.api.v2.model.ca.CertificateRenewRequestDto;
import com.czertainly.api.v2.model.ca.CertificateSignRequestDto;

import java.security.cert.CertificateException;
import java.util.List;

public interface ClientOperationService {

    List<AttributeDefinition> listIssueCertificateAttributes(
            String raProfileName) throws NotFoundException, ConnectorException;

    boolean validateIssueCertificateAttributes(
            String raProfileName,
            List<ClientAttributeDefinition> attributes) throws NotFoundException, ConnectorException, ValidationException;

    ClientCertificateDataResponseDto issueCertificate(
            String raProfileName,
            ClientCertificateSignRequestDto request) throws NotFoundException, ConnectorException, AlreadyExistException, CertificateException;

    ClientCertificateDataResponseDto renewCertificate(
            String raProfileName,
            String certificateId,
            ClientCertificateRenewRequestDto request) throws NotFoundException, ConnectorException, AlreadyExistException, CertificateException;

    List<AttributeDefinition> listRevokeCertificateAttributes(
            String raProfileName) throws NotFoundException, ConnectorException;

    boolean validateRevokeCertificateAttributes(
            String raProfileName,
            List<ClientAttributeDefinition> attributes) throws NotFoundException, ConnectorException, ValidationException;

    void revokeCertificate(
            String raProfileName,
            String certificateId,
            ClientCertificateRevocationDto request) throws NotFoundException, ConnectorException;
}
