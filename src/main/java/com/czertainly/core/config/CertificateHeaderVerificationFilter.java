package com.czertainly.core.config;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.io.Resource;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;

public class CertificateHeaderVerificationFilter extends OncePerRequestFilter {
    private static final String X509_REQUEST_ATTRIBUTE = "javax.servlet.request.X509Certificate";

    private final Log logger = LogFactory.getLog(this.getClass());

    private final String certificateHeader;
    private final String trustStoreType;
    private final Resource trustStore;
    private final String trustStorePassword;

    private TrustManager[] trustManagers;

    public CertificateHeaderVerificationFilter(String certificateHeader, String trustStoreType, Resource trustStore, String trustStorePassword) {
        this.certificateHeader = certificateHeader;
        this.trustStoreType = trustStoreType;
        this.trustStore = trustStore;
        this.trustStorePassword = trustStorePassword;
    }

    @Override
    protected void initFilterBean() throws ServletException {
        if (trustStore == null || !trustStore.exists()) {
            throw new ServletException("Trust store not found.");
        }
        try (InputStream is = trustStore.getInputStream()) {
            KeyStore ks = KeyStore.getInstance(trustStoreType);
            ks.load(is, trustStorePassword.toCharArray());
            TrustManagerFactory tmFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmFactory.init(ks);
            this.trustManagers = tmFactory.getTrustManagers();
        } catch (Exception e) {
            throw new ServletException("Certificate header verification filter not initialized because of:", e);
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            handleClientCert(request);
            chain.doFilter(request, response);
        } catch (ServletException e) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().println(e.getMessage());
        }
    }

    private void handleClientCert(HttpServletRequest request) throws ServletException {
        String header = request.getHeader(certificateHeader);
        if (StringUtils.isBlank(header)) {
            logger.debug("Header " + certificateHeader + " not found in request.");
            return;
        } else {
            if (logger.isTraceEnabled()) {
                logger.trace("Header " + certificateHeader + " found in request.");
            }
        }

        try {
            header = java.net.URLDecoder.decode(header, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            logger.debug("Header not URL encoded");
        }
        
        header = header.replace("-----BEGIN CERTIFICATE-----", "").replace("-----END CERTIFICATE-----", "").replaceAll("\\r?\\n", "");
        X509Certificate clientCert = null;
        logger.debug("Client certificate in header: " + header);
        try (InputStream is = new ByteArrayInputStream(Base64.getDecoder().decode(header))) {
            clientCert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(is);
            if (logger.isTraceEnabled()) {
                logger.trace("Certificate in header is valid.");
            }
        } catch (Exception e) {
            logger.error("Certificate in header is invalid.", e);
            throw new ServletException("Fail to decode certificate in header", e);
        }

        try {
            X509Certificate[] chain = new X509Certificate[] { clientCert };
            verifyCertificate(chain);
            request.setAttribute(X509_REQUEST_ATTRIBUTE, chain);

            if (logger.isTraceEnabled()) {
                logger.trace("Certificate in header is issued by " + clientCert.getIssuerDN().getName()
                        + " with subject " + clientCert.getSubjectDN().getName());
            }
        } catch (Exception e) {
            logger.warn("Certificate in header issued by " + clientCert.getIssuerDN().getName()
                        + " with subject " + clientCert.getSubjectDN().getName() + " invalid.",
                        e);
            throw new ServletException("Certificate in header is invalid", e);
        }
    }

    private void verifyCertificate(X509Certificate[] clientCertChain) throws CertificateException {
        for (TrustManager trustManager : trustManagers) {
            if (!(trustManager instanceof X509TrustManager)) {
                continue;
            }

            X509Certificate certificate = clientCertChain[0]; // there is always only one certificate

            X509TrustManager tm = (X509TrustManager) trustManager;
            tm.checkClientTrusted(clientCertChain, certificate.getPublicKey().getAlgorithm());
        }
    }

}
