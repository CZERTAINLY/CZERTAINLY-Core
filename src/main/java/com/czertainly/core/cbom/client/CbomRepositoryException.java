package com.czertainly.core.cbom.client;

import lombok.Getter;
import org.springframework.http.ProblemDetail;

@Getter
public class CbomRepositoryException extends RuntimeException {
    private final ProblemDetail problemDetail;

    public CbomRepositoryException(String message) {
        super(message);
        this.problemDetail = null;
    }

    public CbomRepositoryException(ProblemDetail problemDetail) {
        super(problemDetail.getDetail());
        this.problemDetail = problemDetail;
    }

    public CbomRepositoryException(String message, ProblemDetail problemDetail) {
        super(problemDetail != null
                ? message + ": " + problemDetail.getDetail()
                : message);
        this.problemDetail = problemDetail;
    }
}
