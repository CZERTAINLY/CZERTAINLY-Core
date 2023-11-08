package com.czertainly.core.util;

import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import javax.security.auth.x500.X500Principal;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class CzertainlyX500NameStyleTest {
     private static final Logger logger = LoggerFactory.getLogger(CzertainlyX500NameStyleTest.class);

    @Test
    public void testCustomNameStyle() throws CertificateException {
        Assertions.assertEquals("CN=Certificate Authority, L=Location, ST=State, C=US, O=Organization", getDnWithCustomStyle("CN=Certificate Authority; L=Location,ST=State, C=US;O=Organization"));
        Assertions.assertEquals("2.5.4.10=Organization,2.5.4.3=Certificate Authority,2.5.4.6=US,2.5.4.7=Location,2.5.4.8=State", getDnWithCustomStyleNormalized("CN=Certificate Authority; L=Location,ST=State, C=US;O=Organization"));
        Assertions.assertEquals("CN=Example Root CA, O=Example Corp, EMAIL=admin@example.com, C=US", getDnWithCustomStyle("CN=Example Root CA; O=Example Corp; EMAILADDRESS=admin@example.com,C=US"));
        Assertions.assertEquals("1.2.840.113549.1.9.1=admin@example.com,2.5.4.10=Example Corp,2.5.4.3=Example Root CA,2.5.4.6=US", getDnWithCustomStyleNormalized("CN=Example Root CA; O=Example Corp; EMAILADDRESS=admin@example.com,C=US"));
        Assertions.assertEquals("OU=IT Security, O=SecureCorp, L=City, ST=State, C=US, 2.5.4.77=SSL Issuer", getDnWithCustomStyle("OU=IT Security; O=SecureCorp; L=City; ST=State; C=US, 2.5.4.77=SSL Issuer"));
        Assertions.assertEquals("2.5.4.10=SecureCorp,2.5.4.11=IT Security,2.5.4.6=US,2.5.4.7=City,2.5.4.77=SSL Issuer,2.5.4.8=State", getDnWithCustomStyleNormalized("OU=IT Security; O=SecureCorp; L=City; ST=State; C=US, 2.5.4.77=SSL Issuer"));
        X509Certificate certificateAVA = CertificateUtil.getX509Certificate("MIIFkDCCA3igAwIBAgITGAAAAIcz3IsRPKO3uAAAAAAAhzANBgkqhkiG9w0BAQ0FADA3MRcwFQYDVQQDDA5EZW1vIE1TIFN1YiBDQTEcMBoGA1UECgwTM0tleSBDb21wYW55IHMuci5vLjAeFw0yMTEwMDQwNzMyMTdaFw0yMzEwMDQwNzMyMTdaMFQxFTATBgoJkiaJk/IsZAEZFgVsb2NhbDEUMBIGCgmSJomT8ixkARkWBDNrZXkxDjAMBgNVBAMTBVVzZXJzMRUwEwYDVQQDEwxNaWNoYWwgVHV0a28wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQC28AZyo5vtUe8k0vszmnrANZsuxGT9qys89+CfVtn8XoqpdUrl/cErDdR+XfefpsXPgnxTIAgsr7VtAznvea6W0zur4eEMhhVvpLkFMhZY21J3Yz9gOsW37etqYZxO24N5YLjc3o1h+9G4kgCkPrDpnXgdYxUGd6PaiMZDV9Ierh2beS9t6sx+2XeexidVQzoLkUyQnrxNT1Bp8td6/kFQj50P1pwQu7iUanpbm4bNenN9yD3SBL/zgJhn+PXO1FQs+FCMBQcxpSV4MRraVww2ESWZH9QFr3Fm/GaLJcBljtk/tIP2B6nVbGM8MvLBu9Cd+Aj3vx3s/sRcHi3U2p+3AgMBAAGjggF2MIIBcjAtBgkrBgEEAYI3FAIEIB4eAEUAbgByAG8AbABsAG0AZQBuAHQAQQBnAGUAbgB0MBUGA1UdJQQOMAwGCisGAQQBgjcUAgEwDgYDVR0PAQH/BAQDAgeAMB0GA1UdDgQWBBQlXZODlayWJFGAD9u0JVPZBFk1gDAfBgNVHSMEGDAWgBSSwrzfVcXBk4VJB/esyR0LaAEHUTBNBgNVHR8ERjBEMEKgQKA+hjxodHRwOi8vbGFiMDIuM2tleS5jb21wYW55L2NybHMvZGVtby9EZW1vJTIwTVMlMjBTdWIlMjBDQS5jcmwwVwYIKwYBBQUHAQEESzBJMEcGCCsGAQUFBzABhjtodHRwOi8vbGFiMDIuM2tleS5jb21wYW55L2Nhcy9kZW1vL0RlbW8lMjBNUyUyMFN1YiUyMENBLmNydDAyBgNVHREEKzApoCcGCisGAQQBgjcUAgOgGQwXbWljaGFsLnR1dGtvQDNrZXkubG9jYWwwDQYJKoZIhvcNAQENBQADggIBABL3kq943BYNTqxYmk7lYtTuX37kSQG9jUlrhonWLSSreUzpeoI7y4MNpKYvoKwi5pgLb5AZk9L+5F5MAzqEiM4n9AzYdJPD7p549cXy++wFj1YXBMTjPmJjo3MK/0A5d7kygESIzI0r6/KIVShn82d7f7W7caov1+IGD2bWbQ8TcFk0JIaRk8u+rfDV+jYOD+NvbHOB79o7iJATD7KtIX2HrsZ2pYYwf/dcEwsLwYJ6mEBr4vQb7blPvkc/fDB/Pn1kr97TK8fFzIk8yHB/892IN45vL3G7bKfQuU8dfjtvFrTtW2AKL7eO25Pw+Ovlv15QMUhWM+yWcT1AQ3k5d6I+lind/n1uvNLYMG1Uwf/D6FTZV/Jvfz3SgdUAXZ1WmXEsmHT1B8Iz2wUXT98XDfmr95yMl5sME7NFx9+dgyQRKkz8Pbi0+nZh2OWfo3wE8eYoLS8oau3g886VT0Y0AHpjyF3722/1Xx5mRLnt4v4yxSWh4e2Rxc9QVGHLdYxDbOatNhMqvJRohft5Pghp5BW2FD/7loRjuhktT/jFH195mnB5IoGtysY2KzTX6XxKRUicqd+Qp5O6yxa2DbemGSaSDITF9MLpsjdn9Ihy6+cpyGIZm2bnD3iF8+7dr9TOFPYmm3LTbP4+jixqwlJVNu0wG+U5hPCQg3UYaQewaPQj");
        X509Certificate certificateO = CertificateUtil.getX509Certificate("MIIGyDCCBbCgAwIBAgIQCzmtuBLB7KKyPgzj/vgwCzANBgkqhkiG9w0BAQsFADBE" +
                "MQswCQYDVQQGEwJVUzEVMBMGA1UEChMMRGlnaUNlcnQgSW5jMR4wHAYDVQQDExVH" +
                "ZW9UcnVzdCBFViBSU0EgQ0EgRzIwHhcNMjMwNjAyMDAwMDAwWhcNMjQwNzAyMjM1" +
                "OTU5WjCBnTETMBEGCysGAQQBgjc8AgEDEwJTSzEdMBsGA1UEDwwUUHJpdmF0ZSBP" +
                "cmdhbml6YXRpb24xEzARBgNVBAUTCjUxIDMwNiA3MjcxCzAJBgNVBAYTAlNLMRMw" +
                "EQYDVQQHEwpCcmF0aXNsYXZhMR0wGwYDVQQKExRGaW5heCwgby5jLnAuLCBhLiBz" +
                "LjERMA8GA1UEAxMIZmluYXguZXUwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEK" +
                "AoIBAQC9+TA3ZlYSzzT9dNiLL1MId70/GQwVCW2mFgADpBisyGfOyfnHQncEHjXz" +
                "5Is5frOMDJtKMykxBjsLz8/K1zqbeqnXNG8tCa9HekTWhdLhPzJD1j8okEaN5kjV" +
                "OlvaFJMDKZye4iq3Wb4981qinXnQ2idaRXXbFbiPjTDB927/PDOoxaRyFkblLD5D" +
                "/uJofcQiZ/aFB9r4bxtJ0QAqUSS4JFVWzzBrJ9Fvs0MOKkhPrAC7Jz6G6jUsYM0A" +
                "AUCk/GxSPrS4V8sxfL8i8tfXG/MuAfhU1BbLlBc5Drb+MF1rAzg7HschrYW/M4sD" +
                "KEVwzm1Zw/L2kBDBxMUPO0y7k7lxAgMBAAGjggNaMIIDVjAfBgNVHSMEGDAWgBQo" +
                "0s/uCYR13bWytb881aDGc4hdHzAdBgNVHQ4EFgQUf1rmPe3nKPcPuLlgDuj7U+n8" +
                "tFAwIQYDVR0RBBowGIIIZmluYXguZXWCDHd3dy5maW5heC5ldTAOBgNVHQ8BAf8E" +
                "BAMCBaAwHQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsGAQUFBwMCMHUGA1UdHwRuMGww" +
                "NKAyoDCGLmh0dHA6Ly9jcmwzLmRpZ2ljZXJ0LmNvbS9HZW9UcnVzdEVWUlNBQ0FH" +
                "Mi5jcmwwNKAyoDCGLmh0dHA6Ly9jcmw0LmRpZ2ljZXJ0LmNvbS9HZW9UcnVzdEVW" +
                "UlNBQ0FHMi5jcmwwSgYDVR0gBEMwQTALBglghkgBhv1sAgEwMgYFZ4EMAQEwKTAn" +
                "BggrBgEFBQcCARYbaHR0cDovL3d3dy5kaWdpY2VydC5jb20vQ1BTMHMGCCsGAQUF" +
                "BwEBBGcwZTAkBggrBgEFBQcwAYYYaHR0cDovL29jc3AuZGlnaWNlcnQuY29tMD0G" +
                "CCsGAQUFBzAChjFodHRwOi8vY2FjZXJ0cy5kaWdpY2VydC5jb20vR2VvVHJ1c3RF" +
                "VlJTQUNBRzIuY3J0MAkGA1UdEwQCMAAwggF9BgorBgEEAdZ5AgQCBIIBbQSCAWkB" +
                "ZwB1AO7N0GTV2xrOxVy3nbTNE6Iyh0Z8vOzew1FIWUZxH7WbAAABiHw5qMUAAAQD" +
                "AEYwRAIgXe2yypWz/3t4TsvJcDmVvf61mxI9jppLUiGPaKfpDRsCIHRJ5Z5ADQo6" +
                "LcboU88hc1cadNEdX9lDck72fNt11YtCAHYASLDja9qmRzQP5WoC+p0w6xxSActW" +
                "3SyB2bu/qznYhHMAAAGIfDmotAAABAMARzBFAiEAru8hGh6DeWBJo16QqnbJ4ZPx" +
                "RzqYhOEPbRgcv8XGVPsCIE6ChWowQbMq5SxE1jEAFCJOc5cA4i8dG6qNokQrEtUK" +
                "AHYA2ra/az+1tiKfm8K7XGvocJFxbLtRhIU0vaQ9MEjX+6sAAAGIfDmohgAABAMA" +
                "RzBFAiEAv5fFff2YEzFpIXdF+hfmW/bxVILrtFDMH5ePTHTTBBsCIADJxZntD4PJ" +
                "xqGEiRg6P2G8r5Lep73SR6hIRPfzKfWOMA0GCSqGSIb3DQEBCwUAA4IBAQDHMhh4" +
                "vl+EAsnpc/q40D5tP1lF08z4BTI6qO7koYeU1ZSsdDSo7Rr2oLwwZYEwcOy0S4ub" +
                "iM7uS1mjlf/EEBBX8yeItQBUBvZKi0lJLmbeBq9qLfo6MUfUNZgeg5VjhaCLNu/G" +
                "3A91xjfzGlnTjZNIiAhUBUwFKBK7N/VAN8CP7cBED0slucXxqxCPOhaD50tEZTi2" +
                "FdV2/EK53ljThNnlwogBUPXBzGyeZsC1QStqQWysLhrnqryRy0v0yvuCTdOkiFBX" +
                "3rsdp4QpxNYY5M9zYka+17ebSuXOW9gFxPJYwlr1AZ9F/u33unXJ6hWaweO/CNMW" +
                "0m4uktyxBoDjR3pj");
        Assertions.assertEquals("CN=Michal Tutko, CN=Users, DC=3key, DC=local", X500Name.getInstance(CzertainlyX500NameStyle.DEFAULT_INSTANCE, certificateAVA.getSubjectX500Principal().getEncoded()).toString());
        Assertions.assertEquals("0.9.2342.19200300.100.1.25=3key,0.9.2342.19200300.100.1.25=local,2.5.4.3=Michal Tutko,2.5.4.3=Users", X500Name.getInstance(CzertainlyX500NameStyle.NORMALIZED_INSTANCE, certificateAVA.getSubjectX500Principal().getEncoded()).toString());
        Assertions.assertEquals("CN=finax.eu, O=Finax, o.c.p., a. s., L=Bratislava, C=SK, SERIALNUMBER=51 306 727, BusinessCategory=Private Organization, 1.3.6.1.4.1.311.60.2.1.3=SK", X500Name.getInstance(CzertainlyX500NameStyle.DEFAULT_INSTANCE, certificateO.getSubjectX500Principal().getEncoded()).toString());
        Assertions.assertEquals("1.3.6.1.4.1.311.60.2.1.3=SK,2.5.4.10=Finax, o.c.p., a. s.,2.5.4.15=Private Organization,2.5.4.3=finax.eu,2.5.4.5=51 306 727,2.5.4.6=SK,2.5.4.7=Bratislava", X500Name.getInstance(CzertainlyX500NameStyle.NORMALIZED_INSTANCE, certificateO.getSubjectX500Principal().getEncoded()).toString());
        }
    private String getDnWithCustomStyle(String dn){
        return X500Name.getInstance(CzertainlyX500NameStyle.DEFAULT_INSTANCE, new X500Principal(dn).getEncoded()).toString();
    }

    private String getDnWithCustomStyleNormalized(String dn){
        return X500Name.getInstance(CzertainlyX500NameStyle.NORMALIZED_INSTANCE, new X500Principal(dn).getEncoded()).toString();
    }
    }


