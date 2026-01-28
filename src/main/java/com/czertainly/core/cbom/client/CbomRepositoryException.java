package com.czertainly.core.cbom.client;

import lombok.Getter;
import org.springframework.http.ProblemDetail;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Getter
public class CbomRepositoryException extends Exception {
    private final ProblemDetail problemDetail;

    public CbomRepositoryException(String message) {
        super(message);
        this.problemDetail = null;
    }

    public CbomRepositoryException(ProblemDetail problemDetail) {
        super(problemDetail.getDetail());
        this.problemDetail = problemDetail;
    }

    public CbomRepositoryException(String message, Throwable cause) {
        super(message, cause);
        this.problemDetail = extractProblemDetail(cause);
    }

    private static ProblemDetail extractProblemDetail(Throwable cause) {
        if (cause instanceof WebClientResponseException e) {
            try {
                return e.getResponseBodyAs(ProblemDetail.class);
            } catch (Exception ex) {
                return null;
            }
        }
        return null;
    }

    @Override
    public String getMessage() {
        return problemDetail != null 
            ? super.getMessage() + ": " + problemDetail.getDetail()
            : super.getMessage();
    }
}
