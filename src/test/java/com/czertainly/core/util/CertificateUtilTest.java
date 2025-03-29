package com.czertainly.core.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;

public class CertificateUtilTest {

    /*
    [ req ]
    default_md = sha256
    prompt = no
    req_extensions = req_ext
    distinguished_name = req_distinguished_name
    [ req_distinguished_name ]
    commonName = czertainly.com
    countryName = CZ
    [ req_ext ]
    subjectKeyIdentifier = hash
    authorityKeyIdentifier = keyid:always,issuer
    keyUsage=critical,digitalSignature,keyEncipherment
    extendedKeyUsage=critical,serverAuth,clientAuth
    subjectAltName = @alt_names
    [ alt_names ]
    DNS.0 = czertainly.com
    IP.0 = 192.168.10.10
    otherName.0 = 1.2.3.4;UTF8:example othername
     */
    // TODO: This can be improved by implementing a proper certificate generation utility
    private static final String x509CertificateB64 = "MIIDjTCCAnWgAwIBAgIUW50iy974VNrc/YrqigdVzcHm8dUwDQYJKoZIhvcNAQELBQAwJjEXMBUGA1UEAwwOY3plcnRhaW5seS5jb20xCzAJBgNVBAYTAkNaMB4XDTI1MDMyOTE3MTQ0NFoXDTQ1MDMyNDE3MTQ0NFowJjEXMBUGA1UEAwwOY3plcnRhaW5seS5jb20xCzAJBgNVBAYTAkNaMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqRxGO/mgWIUsZ02LM2b1phzUprHsru2wFZbt18toQwKq3jHkXmwAC9eQ6Lx2mWhtFNVkD/OanvUBHy1P7pzZ6opG5FvX3P4KfHBUCFDcVL44SlAFYXqBU2FEIl2bFKzrZ3EtDFHE894msoZJa0podDTDhrX0fzDEARiVbgKLP9O5FWo9bdOD/RMEsVvVTYT3BfeX9t6vQKBj3qRAJk9E/XfDWpnmoGbCrFSo2StInZ0C6FiE/mhL/H+m/ZcRSTH+LTOQ26k0XPRPx4ONxT0rOG1bERUST5s5CkMmN+CJQWO1kcuUzBic20/yX6rVz4ORMLJ7bo+3115hqtCkkuaYowIDAQABo4GyMIGvMB0GA1UdDgQWBBTeiIEBiPeMLVVyaHOOYMCf9KpZdDAfBgNVHSMEGDAWgBTeiIEBiPeMLVVyaHOOYMCf9KpZdDAOBgNVHQ8BAf8EBAMCBaAwIAYDVR0lAQH/BBYwFAYIKwYBBQUHAwEGCCsGAQUFBwMCMDsGA1UdEQQ0MDKCDmN6ZXJ0YWlubHkuY29thwTAqAoKoBoGAyoDBKATDBFleGFtcGxlIG90aGVybmFtZTANBgkqhkiG9w0BAQsFAAOCAQEAfy3apxky6PmA/PeoWJwShNCglbwORTO03gpVgY3/pzqxJAlrqY2k7lzpv33XfTi/DTV5tO3nH0vg9G/X97l2n3N834rvHOa/Uq4gAsh9RDI7vlGrxPM9Zmsz/hxpKe96HeJHn8LjFNvHeuvT/yozn0wCiQWNr0dbFcdViPL/bOIrg4GLlqhyhP45GQmQz22VeGn3atCURLodnEY1yoxZK+Lpx/SDioGljn1Ff0xR2bXV5QvDeREoDY0EsoehZGlTET2P5rcbueQRH33bU6s8LouWGWwDSQ8itooWkU8I06p39ehHqlGpkN4VvUXjmvixN9YA9J1p72Hz0kfPzUsDQw==";
    private static final X509Certificate x509certificate;

    static {
        try {
            x509certificate = CertificateUtil.getX509Certificate(x509CertificateB64);
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        }
    }

    // The expected string representation of the SAN list from the X509 certificate above
    private static final String sansString = "{\"registeredID\":[],\"ediPartyName\":[],\"iPAddress\":[\"192.168.10.10\"],\"x400Address\":[],\"rfc822Name\":[],\"otherName\":[\"1.2.3.4=example othername\"],\"dNSName\":[\"czertainly.com\"],\"uniformResourceIdentifier\":[],\"directoryName\":[]}";
    private static final Map<String, List<String>> sanList = CertificateUtil.getSAN(x509certificate);

    @Test
    public void testSerializeSans() {
        Map<String, List<String>> sanList = CertificateUtil.getSAN(x509certificate);
        String result = CertificateUtil.serializeSans(sanList);
        Assertions.assertEquals(sansString, result);
    }

    @Test
    public void testDeserializeSans() {
        Map<String, List<String>> result = CertificateUtil.deserializeSans(sansString);
        Assertions.assertEquals(sanList, result);
    }
}