package com.otilm.core.api;

import com.otilm.api.exception.CbomRepositoryException;
import com.otilm.api.exception.CertificateRequestException;
import com.otilm.api.model.common.ErrorMessageDto;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExceptionHandlingAdviceTest {

    private final ExceptionHandlingAdvice advice = new ExceptionHandlingAdvice();

    @Test
    void handleCbomRepositoryException_ShouldUseProblemDetailDetailWhenPresent() {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "version must be an integer");
        CbomRepositoryException ex = new CbomRepositoryException(problemDetail);

        ResponseEntity<ErrorMessageDto> response = advice.handleCbomRepositoryException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("version must be an integer", response.getBody().getMessage());
    }

    @Test
    void handleCbomRepositoryException_ShouldFallbackToExceptionMessageWhenDetailIsBlank() {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setDetail("");
        CbomRepositoryException ex = new CbomRepositoryException(problemDetail);

        ResponseEntity<ErrorMessageDto> response = advice.handleCbomRepositoryException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ex.getMessage(), response.getBody().getMessage());
    }

    @Test
    void handleCbomRepositoryException_ShouldFallbackToExceptionMessageWhenDetailIsNull() {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setDetail(null);
        CbomRepositoryException ex = new CbomRepositoryException(problemDetail);

        ResponseEntity<ErrorMessageDto> response = advice.handleCbomRepositoryException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ex.getMessage(), response.getBody().getMessage());
    }

    @Test
    void handleCbomRepositoryException_ShouldReturnInternalServerErrorWhenProblemDetailIsNull() {
        CbomRepositoryException ex = new CbomRepositoryException("Upload of BOM failed.");

        ResponseEntity<ErrorMessageDto> response = advice.handleCbomRepositoryException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Upload of BOM failed.", response.getBody().getMessage());
    }

    @Test
    void handleCertificateRequestException_ShouldNotFailWhenCauseIsNull() {
        CertificateRequestException ex = new CertificateRequestException("Invalid CSR");

        ErrorMessageDto response = advice.handleCertificateRequestException(ex);

        assertNotNull(response);
        assertEquals("Invalid CSR", response.getMessage());
    }

    @Test
    void handleCertificateRequestException_ShouldNotExposeCauseInResponse() {
        CertificateRequestException ex =
                new CertificateRequestException("Invalid CSR", new IllegalArgumentException("bad encoding"));

        ErrorMessageDto response = advice.handleCertificateRequestException(ex);

        assertEquals("Invalid CSR", response.getMessage());
    }

    @Test
    void handleMethodArgumentNotValidException_ShouldReturnStringArray() throws NoSuchMethodException {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "name", "must not be blank"));
        MethodParameter parameter =
                new MethodParameter(ExceptionHandlingAdviceTest.class.getDeclaredMethod("dummy", String.class), 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, binding);

        List<String> response = advice.handleMethodArgumentNotValidException(ex);

        assertEquals(List.of("name must not be blank"), response);
    }

    @SuppressWarnings("unused")
    private void dummy(String s) {
        // Target for the MethodParameter used to build a MethodArgumentNotValidException above.
    }
}
