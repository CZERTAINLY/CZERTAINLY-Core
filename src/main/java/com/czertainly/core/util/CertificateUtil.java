package com.czertainly.core.util;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.core.config.ApplicationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.DatatypeConverter;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.cert.*;
import java.util.*;

public class CertificateUtil {

	private static final Logger logger = LoggerFactory.getLogger(CertificateUtil.class);

	@SuppressWarnings("serial")
	private static final Map<String, String> SAN_TYPE_MAP = new HashMap<>();
    static {
        SAN_TYPE_MAP.put("0", "otherName");
        SAN_TYPE_MAP.put("1", "rfc822Name");
        SAN_TYPE_MAP.put("2", "dNSName");
        SAN_TYPE_MAP.put("3", "x400Address");
        SAN_TYPE_MAP.put("4", "directoryName");
        SAN_TYPE_MAP.put("5", "ediPartyName");
        SAN_TYPE_MAP.put("6", "uniformResourceIdentifier");
        SAN_TYPE_MAP.put("7", "iPAddress");
        SAN_TYPE_MAP.put("8", "registeredID");
    }

	@SuppressWarnings("serial")
	private static final List<String> KEY_USAGE_LIST = Arrays.asList("digitalSignature","nonRepudiation","keyEncipherment","dataEncipherment","keyAgreement","keyCertSign","cRLSign","encipherOnly","decipherOnly");

	private CertificateUtil() {
	}

	public static X509Certificate getX509Certificate(byte[] certInBytes) throws CertificateException {
		try {
			CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509",
					ApplicationConfig.SECURITY_PROVIDER);
			return (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(certInBytes));
		} catch (Exception e) {
			throw new CertificateException("Error when parsing certificate", e);
		}
	}

	public static String getDnFromX509Certificate(String certInBase64) throws CertificateException, NotFoundException {
		return getDnFromX509Certificate(getX509Certificate(certInBase64));
	}

	public static String getDnFromX509Certificate(X509Certificate cert) throws NotFoundException {
		Principal subjectDN = cert.getSubjectDN();
		if (subjectDN != null) {
			return subjectDN.getName();
		} else {
			throw new NotFoundException("Subject DN not found in certificate.");
		}
	}

	public static String getSerialNumberFromX509Certificate(X509Certificate certificate) {
		return certificate.getSerialNumber().toString(16);
	}

	public static X509Certificate getX509Certificate(String certInBase64) throws CertificateException {
		return getX509Certificate(Base64.getDecoder().decode(certInBase64));
	}

	public static List<String> keyUsageExtractor(boolean[] keyUsage) {

		List<String> keyUsg = new ArrayList<>();
		try {
			for (int i = 0; i < keyUsage.length; i++) {
				if (keyUsage[i] == Boolean.TRUE) {
					keyUsg.add((String) KEY_USAGE_LIST.toArray()[i]);
				}
			}
		} catch (NullPointerException e) {
			logger.warn("No Key Usage found");
		}
		return keyUsg;
	}

	public static String getBasicConstraint(int bcValue) {
		if (bcValue >= 0) {
			return "Subject Type=CA";
		} else {
			return "Subject Type=End Entity";
		}
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> getSAN(X509Certificate certificate) {
		@SuppressWarnings("serial")
		Map<String, Object> sans = new HashMap<>();
		sans.put("otherName", new ArrayList<String>());
		sans.put("rfc822Name", new ArrayList<String>());
		sans.put("dNSName", new ArrayList<String>());
		sans.put("x400Address", new ArrayList<String>());
		sans.put("directoryName", new ArrayList<String>());
		sans.put("ediPartyName", new ArrayList<String>());
		sans.put("uniformResourceIdentifier", new ArrayList<String>());
		sans.put("iPAddress", new ArrayList<String>());
		sans.put("registeredID", new ArrayList<String>());

		try {
			for (List<?> san : certificate.getSubjectAlternativeNames()) {
				((ArrayList<String>) sans.get(SAN_TYPE_MAP.get(san.toArray()[0].toString())))
						.add(san.toArray()[1].toString());
			}
		} catch (CertificateParsingException | NullPointerException e) {
			logger.warn("Unable to get the SAN of the certificate");
			logger.warn(e.getMessage());
		}

		return sans;
	}

	public static X509Certificate parseCertificate(String cert) throws CertificateException {
		cert = cert.replace("-----BEGIN CERTIFICATE-----", "").replace("-----END CERTIFICATE-----", "")
				.replace("\r", "").replace("\n", "");
		byte[] decoded = Base64.getDecoder().decode(cert);
		return (X509Certificate) CertificateFactory.getInstance("X.509")
				.generateCertificate(new ByteArrayInputStream(decoded));
	}

	public static String getThumbprint(byte[] encodedContent)
			throws NoSuchAlgorithmException, CertificateEncodingException {
		MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
		messageDigest.update(encodedContent);
		byte[] digest = messageDigest.digest();
		String thumbprint = DatatypeConverter.printHexBinary(digest).toLowerCase();
		logger.debug("Thumbprint of the certificate is {}", thumbprint);
		return thumbprint;
	}
}
