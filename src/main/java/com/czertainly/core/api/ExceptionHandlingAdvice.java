package com.czertainly.core.api;

import com.czertainly.api.exception.*;
import com.czertainly.api.model.common.AuthenticationServiceExceptionDto;
import com.czertainly.api.model.common.ErrorMessageDto;
import com.czertainly.api.model.core.acme.ProblemDocument;
import com.czertainly.core.security.exception.AuthenticationServiceException;
import com.czertainly.core.util.BeautificationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.net.ConnectException;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ExceptionHandlingAdvice {

    private static final Logger LOG = LoggerFactory.getLogger(ExceptionHandlingAdvice.class);

    /**
     * Handler for {@link NotFoundException}.
     *
     * @param ex Caught {@link NotFoundException}.
     * @return
     */
    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorMessageDto handleNotFoundException(NotFoundException ex) {
        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append(ex.getMessage());

        if (ex.getConnector() != null) {
            messageBuilder
                    .append(" ")
                    .append("Error is related to connector ")
                    .append("name=").append(ex.getConnector().getName()).append(", ")
                    .append("uuid=").append(ex.getConnector().getUuid())
                    .append(". ");
        }

        LOG.warn("HTTP 404: {}", messageBuilder);
        return ErrorMessageDto.getInstance(messageBuilder.toString());
    }

    /**
     * Handler for {@link AlreadyExistException}.
     *
     * @return
     */
    @ExceptionHandler(AlreadyExistException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorMessageDto handleAlreadyExistException(AlreadyExistException ex) {
        LOG.info("HTTP 400: {}", ex.getMessage());
        return ErrorMessageDto.getInstance(ex.getMessage());
    }

    /**
     * Handler for {@link NotDeletableException}.
     *
     * @return
     */
    @ExceptionHandler(NotDeletableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorMessageDto handleNotDeletableException(NotDeletableException ex) {
        LOG.info("HTTP 400: {}", ex.getMessage());
        return ErrorMessageDto.getInstance(ex.getMessage());
    }

    /**
     * Handler for {@link ValidationException}.
     *
     * @return
     */
    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public List<String> handleValidationException(ValidationException ex) {
        LOG.info("HTTP 422: {}", ex.getMessage());

        return ex.getErrors().stream()
                .map(ValidationError::getErrorDescription)
                .collect(Collectors.toList());
    }

    /**
     * Handler for {@link ConnectorClientException}.
     *
     * @return
     */
    @ExceptionHandler(ConnectorClientException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorMessageDto handleConnectorClientException(ConnectorClientException ex) {
        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append(ex.getMessage());

        if (ex.getConnector() != null) {
            messageBuilder
                    .append(" ")
                    .append("Error is related to connector ")
                    .append("name=").append(ex.getConnector().getName()).append(", ")
                    .append("uuid=").append(ex.getConnector().getUuid())
                    .append(". ");
        }

        if (ex.getHttpStatus() != null) {
            messageBuilder
                    .append(" ")
                    .append("Original response code ")
                    .append(ex.getHttpStatus())
                    .append(". ");
        }

        LOG.info("HTTP 400: {}", messageBuilder);
        return ErrorMessageDto.getInstance(messageBuilder.toString());
    }

    /**
     * Handler for {@link ConnectorServerException}.
     *
     * @return
     */
    @ExceptionHandler(ConnectorServerException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ErrorMessageDto handleConnectorServerException(ConnectorServerException ex) {
        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append(ex.getMessage());

        if (ex.getConnector() != null) {
            messageBuilder
                    .append(" ")
                    .append("Error is related to connector ")
                    .append("name=").append(ex.getConnector().getName()).append(", ")
                    .append("uuid=").append(ex.getConnector().getUuid())
                    .append(". ");
        }

        if (ex.getHttpStatus() != null) {
            messageBuilder
                    .append(" ")
                    .append("Original response code ")
                    .append(ex.getHttpStatus())
                    .append(". ");
        }

        LOG.info("HTTP 502: {}", messageBuilder);
        return ErrorMessageDto.getInstance(messageBuilder.toString());
    }

    /**
     * Handler for {@link ConnectorCommunicationException}.
     *
     * @return
     */
    @ExceptionHandler(ConnectorCommunicationException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorMessageDto handleConnectorCommunicationException(ConnectorCommunicationException ex) {
        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append(ex.getMessage());

        if (ex.getConnector() != null) {
            messageBuilder
                    .append(" ")
                    .append("Error is related to connector ")
                    .append("name=").append(ex.getConnector().getName()).append(", ")
                    .append("uuid=").append(ex.getConnector().getUuid())
                    .append(". ");
        }

        LOG.info("HTTP 503: {}", messageBuilder);
        return ErrorMessageDto.getInstance(messageBuilder.toString());
    }


    /**
     * Handler for {@link HttpMessageNotReadableException}.
     *
     * @return
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorMessageDto handleMessageNotReadable(HttpMessageNotReadableException ex) {
        LOG.info("HTTP 400: {}", ex.getMessage());
        return ErrorMessageDto.getInstance("Unable to read HTTP message");
    }

    /**
     * Handler for {@link java.net.ConnectException}.
     *
     * @return
     */
    @ExceptionHandler(ConnectException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorMessageDto handleConnectException(ConnectException ex) {
        LOG.info("HTTP 400: {}", ex.getMessage());
        return ErrorMessageDto.getInstance(ex.getMessage());
    }

    /**
     * Handler for {@link AccessDeniedException}.
     *
     * @return
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<AuthenticationServiceExceptionDto> handleAccessDeniedException(AccessDeniedException ex) {
        LOG.warn("Access denied: {}", ex.getMessage());
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(MediaType.valueOf("application/problem+json"));
        AuthenticationServiceExceptionDto responseDto = new AuthenticationServiceExceptionDto();
        responseDto.setCode("ACCESS_DENIED");
        responseDto.setStatusCode(HttpStatus.FORBIDDEN.value());
        if(response == null){
            responseDto.setMessage("Access denied for the specified operation");
        } else {
            Object resourceName = attributes.getAttribute("INTERNAL_ATTRIB_DENIED_RESOURCE_NAME", 121);
            Object resourceActionName = attributes.getAttribute("INTERNAL_ATTRIB_DENIED_RESOURCE_ACTION_NAME", 121);
            if (resourceName != null && !((String) resourceName).isEmpty() && resourceActionName != null && !((String) resourceActionName).isEmpty()) {
                responseDto.setMessage("Access Denied. Required '"
                        + BeautificationUtil.camelToHumanForm((String) resourceActionName)
                        + "' permission for '"
                        + BeautificationUtil.camelToHumanForm((String) resourceName)
                        + "'");
            } else {
                responseDto.setMessage("Access denied for the specified operation");
            }
        }
        return response.body(responseDto);
    }

    /**
     * Handler for {@link AcmeProblemDocumentException}.
     *
     * @return
     */
    @ExceptionHandler(AcmeProblemDocumentException.class)
    public ResponseEntity<ProblemDocument> handleAcmeProblemDocumentException(AcmeProblemDocumentException ex) {
        LOG.warn("ACME Error: {}", ex.getProblemDocument().toString());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(ex.getHttpStatusCode()).contentType(MediaType.valueOf("application/problem+json"));
        if (ex.getAdditionalHeaders() != null) {
            for (String entry : ex.getAdditionalHeaders().keySet()) {
                response.header(entry, ex.getAdditionalHeaders().get(entry));
            }
        }
        return response.body(ex.getProblemDocument());
    }

    /**
     * Handler for {@link LocationException}.
     *
     * @return
     */
    @ExceptionHandler(LocationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorMessageDto handleLocationException(LocationException ex) {
        LOG.info("HTTP 400: {}", ex.getMessage());
        return ErrorMessageDto.getInstance(ex.getMessage());
    }

    /**
     * Handler for {@link CertificateOperationException}.
     *
     * @return
     */
    @ExceptionHandler(CertificateOperationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorMessageDto handleCertificateOperationException(CertificateOperationException ex) {
        LOG.info("HTTP 400: {}", ex.getMessage());
        return ErrorMessageDto.getInstance(ex.getMessage());
    }


    /**
     * Handler for {@link AuthenticationServiceException}.
     *
     * @return
     */
    @ExceptionHandler(AuthenticationServiceException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<AuthenticationServiceExceptionDto> handleCertificateOperationException(AuthenticationServiceException ex) {
        Integer statusCode = HttpStatus.BAD_REQUEST.value();
        if(ex.getException() != null){
            statusCode = ex.getException().getStatusCode();
        }
        ResponseEntity.BodyBuilder response = ResponseEntity.status(statusCode).contentType(MediaType.valueOf("application/problem+json"));
        return response.body(ex.getException());
    }


    /**
     * Handler for {@link Exception}.
     *
     * @return
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorMessageDto handleException(Exception ex) {
        LOG.error("General error occurred: {}", ex.getMessage(), ex);
        return ErrorMessageDto.getInstance("Internal server error.");
    }
}
