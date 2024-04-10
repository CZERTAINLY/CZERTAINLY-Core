package com.czertainly.core.api.cmp;

/**
 * List of implementation error states - it helps to find quickly a purpose of problem
 *
 * rules:
 *  1. DO NOT REUSE THEM - each error has own enum item
 *  2. MUST BE USED ONLY FOR ONE TIME - uniquest is clear, bug is quickly identifiable
 *  3. DO NOT BE LAZY - create own error codes every(time|where) you need it!
 *  4. FORMAT errorCode:
 *      0-2 chars CMP,
 *      3-5 shortcut for location (eg. Controller->CNT),
 *      6-8 number (from 001-999)
 */
public enum ImplFailureInfo {

    // -- general
    INCONS(-1, "Inconsistency state"), // very general error (if detail missing)

    // -- controller.level=CNT
    CMPCNTR001(1,"http get method is not supported"),

    // -- service.level=SRV
    CMPSRVR001(100,"pki message request cannot be parsed"),
    CMPSRVR002(101,"pki message response cannot be parsed"),
    CMPSRVR003(102,"pki message response cannot be processed"),

    // -- validation.level=VAL
    CMPVALR001(200,"validation: pki message type is not supported"),
    CMPVALR002(201,"validation: pki message type is unknown"),

    // -- developer
    TODO(-999, "Only for developer purpose - inform czertainly admin")
    ;
    private int errorCode;
    private String errorDescription;

    ImplFailureInfo(int errorCode, String errorDescrition) {
        this.errorCode = errorCode;
        this.errorDescription =errorDescrition;
    }

    public int getCode() {
        return errorCode;
    }

    public String getDescription() {
        return errorDescription;
    }
}
