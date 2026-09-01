package com.otilm.core.api;

import com.otilm.api.exception.ConnectorClientException;
import com.otilm.api.exception.ConnectorProblemException;
import com.otilm.api.model.common.ErrorMessageDto;
import com.otilm.api.model.common.error.ProblemDetailExtended;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ResponseStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the connector-origin status translation: auth and server statuses from a connector are an upstream fault (502),
 * while entity (404) and validation (422) semantics pass through — on both the problem+json and the legacy path. The
 * deliberately-unchanged neighbor handlers are pinned too, so the translation provably touches only its own rows.
 */
class ExceptionHandlingAdviceConnectorTest {

    private final ExceptionHandlingAdvice advice = new ExceptionHandlingAdvice();

    private static ConnectorProblemException problem(int status, String title) {
        ProblemDetailExtended pd = new ProblemDetailExtended();
        pd.setStatus(status);
        pd.setTitle(title);
        pd.setDetail(title);
        pd.setRetryable(false);
        return new ConnectorProblemException(pd);
    }

    private static int annotatedStatus(String handlerMethod, Class<?> exceptionType) throws NoSuchMethodException {
        ResponseStatus annotation = ExceptionHandlingAdvice.class
                .getMethod(handlerMethod, exceptionType)
                .getAnnotation(ResponseStatus.class);
        assertNotNull(annotation, handlerMethod + " must declare @ResponseStatus");
        return annotation.value().value();
    }

    @Test
    void connectorProblem401BecomesBadGateway() {
        ResponseEntity<ErrorMessageDto> response = advice
                .handleConnectorProblemException(problem(401, "Credentials invalid"));
        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getMessage().contains("Original response code 401"));
    }

    @Test
    void connectorProblem403BecomesBadGateway() {
        assertEquals(HttpStatus.BAD_GATEWAY,
                advice.handleConnectorProblemException(problem(403, "Forbidden")).getStatusCode());
    }

    @Test
    void connectorProblem500BecomesBadGateway() {
        assertEquals(HttpStatus.BAD_GATEWAY,
                advice.handleConnectorProblemException(problem(500, "boom")).getStatusCode());
    }

    @Test
    void connectorProblem599BecomesBadGateway() {
        assertEquals(HttpStatus.BAD_GATEWAY,
                advice.handleConnectorProblemException(problem(599, "odd")).getStatusCode());
    }

    @Test
    void connectorProblem303BecomesBadGateway() {
        assertEquals(HttpStatus.BAD_GATEWAY,
                advice.handleConnectorProblemException(problem(303, "odd redirect")).getStatusCode());
    }

    @Test
    void connectorProblem400StaysVerbatim() {
        assertEquals(HttpStatus.BAD_REQUEST,
                advice.handleConnectorProblemException(problem(400, "malformed")).getStatusCode());
    }

    @Test
    void connectorProblem404StaysVerbatim() {
        assertEquals(HttpStatus.NOT_FOUND,
                advice.handleConnectorProblemException(problem(404, "not tracked")).getStatusCode());
    }

    @Test
    void connectorProblem499StaysVerbatim() {
        assertEquals(499,
                advice.handleConnectorProblemException(problem(499, "client closed")).getStatusCode().value());
    }

    @Test
    void connectorProblem422StaysVerbatim() {
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY,
                advice.handleConnectorProblemException(problem(422, "rejected")).getStatusCode());
    }

    @Test
    void translatedResponseKeepsOriginalMessage() {
        ResponseEntity<ErrorMessageDto> response = advice
                .handleConnectorProblemException(problem(401, "Credentials invalid"));
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getMessage().contains("Credentials invalid"));
    }

    @Test
    void legacyConnector401BecomesBadGateway() {
        ResponseEntity<ErrorMessageDto> response = advice
                .handleConnectorClientException(new ConnectorClientException("denied", HttpStatus.UNAUTHORIZED));
        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getMessage().contains("Original response code 401"));
    }

    @Test
    void legacyConnector403BecomesBadGateway() {
        assertEquals(HttpStatus.BAD_GATEWAY,
                advice
                        .handleConnectorClientException(new ConnectorClientException("no", HttpStatus.FORBIDDEN))
                        .getStatusCode());
    }

    @Test
    void legacyConnector409StaysBadRequest() {
        assertEquals(HttpStatus.BAD_REQUEST,
                advice
                        .handleConnectorClientException(new ConnectorClientException("dup", HttpStatus.CONFLICT))
                        .getStatusCode());
    }

    @Test
    void legacyConnectorNullStatusStaysBadRequest() {
        ResponseEntity<ErrorMessageDto> response = advice
                .handleConnectorClientException(new ConnectorClientException("x", (HttpStatus) null));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().getMessage().contains("Original response code"));
    }

    @Test
    void bothPathsEmitNumericOnlyStatusSuffix() {
        String problemMessage = advice.handleConnectorProblemException(problem(401, "denied")).getBody().getMessage();
        String legacyMessage = advice
                .handleConnectorClientException(new ConnectorClientException("denied", HttpStatus.UNAUTHORIZED))
                .getBody()
                .getMessage();
        assertFalse(problemMessage.contains("UNAUTHORIZED"), "problem path must append the numeric code only");
        assertFalse(legacyMessage.contains("UNAUTHORIZED"), "legacy path must append the numeric code only");
    }

    @Test
    void connectorServerExceptionStaysBadGateway() throws NoSuchMethodException {
        assertEquals(HttpStatus.BAD_GATEWAY.value(), annotatedStatus("handleConnectorServerException",
                com.otilm.api.exception.ConnectorServerException.class));
    }

    @Test
    void connectorCommunicationExceptionStaysServiceUnavailable() throws NoSuchMethodException {
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), annotatedStatus("handleConnectorCommunicationException",
                com.otilm.api.exception.ConnectorCommunicationException.class));
    }

    @Test
    void connectorEntityNotFoundStaysNotFound() throws NoSuchMethodException {
        assertEquals(HttpStatus.NOT_FOUND.value(), annotatedStatus("handleConnectorEntityNotFoundException",
                com.otilm.api.exception.ConnectorEntityNotFoundException.class));
    }
}
