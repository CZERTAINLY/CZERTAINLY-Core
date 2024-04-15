package com.czertainly.core.api.cmp.error;

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
    SYSTEM_FAILURE(-1, "Fatal system failure"),

    // -- controller.level=CNT
    CMPCNTR001(1,"http get method is not supported"),

    // -- service.level=SRV
    CMPSRVR001(100,"pki message request cannot be parsed"),
    CMPSRVR002(101,"pki message response cannot be parsed"),
    CMPSRVR003(102,"pki message response cannot be processed"),

    // -- validation.level=VAL
    CMPVALR001(200,"validation: pki message type is not supported"),
    CMPVALR002(201,"validation: pki message type is unknown"),

    // -- crypto validators
    CRYPTOPOP001(501, "validation pop: cannot extract public key"),
    CRYPTOPOP002(502, "validation pop: cannot initialize signature"),
    CRYPTOPOP003(503, "validation pop: cannot verification signature"),
    CRYPTOPOP004(504, "validation pop: wrong type"),
    CRYPTOPOP005(505, "validation pop: wrong signature"),
    CRYPTOPRO006(506, "validation protection: protection element is missing"),
    CRYPTOPRO007(507, "validation protection: protectionAlg element is missing"),

    CRYPTOSIG521(521, "signature-based protection: extraCerts is empty"),
    CRYPTOSIG522(522, "signature-based protection: check failed - signature broken"),
    CRYPTOSIG523(523, "signature-based protection: certificate (used for protecting) has key not suitable for signing"),

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
