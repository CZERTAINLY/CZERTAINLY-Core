package com.czertainly.core.config;

import com.czertainly.api.exception.AcmeProblemDocumentException;
import com.czertainly.api.model.core.acme.AccountStatus;
import com.czertainly.api.model.core.acme.Problem;
import com.czertainly.api.model.core.acme.ProblemDocument;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.dao.entity.acme.AcmeAccount;
import com.czertainly.core.dao.entity.acme.AcmeProfile;
import com.czertainly.core.dao.repository.AcmeProfileRepository;
import com.czertainly.core.dao.repository.RaProfileRepository;
import com.czertainly.core.dao.repository.acme.AcmeAccountRepository;
import com.czertainly.core.util.AcmeSerializationUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class AcmeValidationFilter extends OncePerRequestFilter {

    @Autowired
    private AcmeAccountRepository acmeAccountRepository;
    @Autowired
    private RaProfileRepository raProfileRepository;
    @Autowired
    private AcmeProfileRepository acmeProfileRepository;

    private Boolean raProfileBased;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if(!request.getRequestURI().startsWith("/api/acme")){
            filterChain.doFilter(request, response);
            return;
        }
        this.filterChain = filterChain;
        this.request = request;
        this.response = response;
        try {
            if (request.getRequestURI().contains("/raProfile/")) {
                setRaProfileBased(true);
            } else {
                setRaProfileBased(false);
            }
            validate();
            filterChain.doFilter(request, response);
        } catch (AcmeProblemDocumentException e) {
            response.setStatus(e.getHttpStatusCode());
            response.setContentType("application/problem+json");
            response.getWriter().println(AcmeSerializationUtil.serialize(e.getProblemDocument()));
        }

    }

    private void validate() throws AcmeProblemDocumentException {
        validateGeneral();
        if (getRaProfileBased()) {
            validateRaBasedAcme();
        } else {
            validateAcme();
        }
        validateAccount();
    }

    private void validateAccount() throws AcmeProblemDocumentException {
        if (!request.getRequestURI().contains("/acct/")) {
            return;
        }
        String accountId;
        if (getRaProfileBased()) {
            accountId = request.getRequestURI().split("/")[6];
        } else {
            accountId = request.getRequestURI().split("/")[5];
        }
        AcmeAccount acmeAccount = acmeAccountRepository.findByAccountId(accountId)
                .orElseThrow(() ->
                        new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.ACCOUNT_DOES_NOT_EXIST));
        if(!acmeAccount.getStatus().equals(AccountStatus.VALID)){
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, new ProblemDocument("accountDeactivated",
                    "Account Deactivated",
                    "The requested account has been deactivated"));
        }
    }

    private void validateGeneral() throws AcmeProblemDocumentException {
        validateNonce();
    }

    private void validateAcme() throws AcmeProblemDocumentException {
        String acmeProfileName = request.getRequestURI().split("/")[3];
        AcmeProfile acmeProfile = acmeProfileRepository.findByName(acmeProfileName);
        if (acmeProfile == null) {
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST,
                    new ProblemDocument("acmeProfileNotFound",
                            "ACME Profile is not found",
                            "Given ACME Profile in the request URL is not found"));
        }

        if (!acmeProfile.getEnabled()) {
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST,
                    new ProblemDocument("acmeProfileDisabled",
                            "ACME Profile is not enabled",
                            "ACME Profile is not enabled"));
        }
    }

    private void validateRaBasedAcme() throws AcmeProblemDocumentException {
        String raProfileName = request.getRequestURI().split("/")[4];
        RaProfile raProfile = raProfileRepository.findByName(raProfileName).orElseThrow(() ->
                new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST,
                        new ProblemDocument("raProfileNotFound",
                                "RA Profile is not found",
                                "Given RA Profile in the request URL is not found")));
        if (raProfile.getAcmeProfile() == null) {
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST,
                    new ProblemDocument("acmeProfileNotAssociated",
                            "ACME Profile is not associated",
                            "ACME Profile is not associated with the RA Profile"));
        }
        if (!raProfile.getEnabled()) {
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST,
                    new ProblemDocument("raProfileDisabled",
                            "RA Profile is not enabled",
                            "RA Profile is not enabled"));
        }

        if (!raProfile.getAcmeProfile().getEnabled()) {
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST,
                    new ProblemDocument("acmeProfileDisabled",
                            "ACME Profile is not enabled",
                            "ACME Profile is not enabled"));
        }

    }

    public Boolean getRaProfileBased() {
        return raProfileBased;
    }

    public void setRaProfileBased(Boolean raProfileBased) {
        this.raProfileBased = raProfileBased;
    }

    private void validateNonce() throws AcmeProblemDocumentException {
        if (request.getRequestURI().endsWith("/new-nonce") || request.getRequestURI().endsWith("/directory") || !request.getRequestURI().contains("/api/acme/")) {
            return;
        }
        if (request.getHeader("Replay-Nonce") == null) {
            throw new AcmeProblemDocumentException(HttpStatus.BAD_REQUEST, Problem.BAD_NONCE);
        }
    }
}
