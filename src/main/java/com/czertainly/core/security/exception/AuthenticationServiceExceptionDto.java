package com.czertainly.core.security.exception;

import io.swagger.v3.oas.annotations.media.Schema;

public class AuthenticationServiceExceptionDto {

    @Schema(description = "Status code of the HTTP Request", required = true)
    private Integer statusCode;
    @Schema(description = "Code of the result", required = true)
    private String code;
    @Schema(description = "Exception message", required = true)
    private String message;

    public AuthenticationServiceExceptionDto(Integer statusCode, String code, String message) {
        this.statusCode = statusCode;
        this.code = code;
        this.message = message;
    }

    public AuthenticationServiceExceptionDto() {
    }

    public AuthenticationServiceExceptionDto(Integer statusCode, String message) {
        this.statusCode = statusCode;
        this.message = message;
    }

    public AuthenticationServiceExceptionDto(Integer statusCode) {
        this.statusCode = statusCode;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

