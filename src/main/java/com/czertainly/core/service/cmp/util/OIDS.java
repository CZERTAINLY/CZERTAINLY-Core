package com.czertainly.core.service.cmp.util;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public enum OIDS {

    // NIST --
    // AES 2.16.840.1.101.3.4.1.*
    AES("2.16.840.1.101.3.4.1"),
    AES_128$ECB$NoPadding("2.16.840.1.101.3.4.1.1", "AES_128/ECB/NoPadding"),
    AES_128$CBC$NoPadding("2.16.840.1.101.3.4.1.2", "AES_128/CBC/NoPadding"),
    AES_128$OFB$NoPadding("2.16.840.1.101.3.4.1.3", "AES_128/OFB/NoPadding"),
    AES_128$CFB$NoPadding("2.16.840.1.101.3.4.1.4", "AES_128/CFB/NoPadding"),
    AES_128$KW$NoPadding("2.16.840.1.101.3.4.1.5", "AES_128/KW/NoPadding",
            "AESWrap_128"),
    AES_128$GCM$NoPadding("2.16.840.1.101.3.4.1.6", "AES_128/GCM/NoPadding"),
    AES_128$KWP$NoPadding("2.16.840.1.101.3.4.1.8", "AES_128/KWP/NoPadding",
            "AESWrapPad_128"),

    AES_192$ECB$NoPadding("2.16.840.1.101.3.4.1.21", "AES_192/ECB/NoPadding"),
    AES_192$CBC$NoPadding("2.16.840.1.101.3.4.1.22", "AES_192/CBC/NoPadding"),
    AES_192$OFB$NoPadding("2.16.840.1.101.3.4.1.23", "AES_192/OFB/NoPadding"),
    AES_192$CFB$NoPadding("2.16.840.1.101.3.4.1.24", "AES_192/CFB/NoPadding"),
    AES_192$KW$NoPadding("2.16.840.1.101.3.4.1.25", "AES_192/KW/NoPadding",
            "AESWrap_192"),
    AES_192$GCM$NoPadding("2.16.840.1.101.3.4.1.26", "AES_192/GCM/NoPadding"),
    AES_192$KWP$NoPadding("2.16.840.1.101.3.4.1.28", "AES_192/KWP/NoPadding",
            "AESWrapPad_192"),

    AES_256$ECB$NoPadding("2.16.840.1.101.3.4.1.41", "AES_256/ECB/NoPadding"),
    AES_256$CBC$NoPadding("2.16.840.1.101.3.4.1.42", "AES_256/CBC/NoPadding"),
    AES_256$OFB$NoPadding("2.16.840.1.101.3.4.1.43", "AES_256/OFB/NoPadding"),
    AES_256$CFB$NoPadding("2.16.840.1.101.3.4.1.44", "AES_256/CFB/NoPadding"),
    AES_256$KW$NoPadding("2.16.840.1.101.3.4.1.45", "AES_256/KW/NoPadding",
            "AESWrap_256"),
    AES_256$GCM$NoPadding("2.16.840.1.101.3.4.1.46", "AES_256/GCM/NoPadding"),
    AES_256$KWP$NoPadding("2.16.840.1.101.3.4.1.48", "AES_256/KWP/NoPadding",
            "AESWrapPad_256"),

    // hashAlgs 2.16.840.1.101.3.4.2.*
    SHA_256("2.16.840.1.101.3.4.2.1", "SHA-256", "SHA256"),
    SHA_384("2.16.840.1.101.3.4.2.2", "SHA-384", "SHA384"),
    SHA_512("2.16.840.1.101.3.4.2.3", "SHA-512", "SHA512"),
    SHA_224("2.16.840.1.101.3.4.2.4", "SHA-224", "SHA224"),
    SHA_512$224("2.16.840.1.101.3.4.2.5", "SHA-512/224", "SHA512/224"),
    SHA_512$256("2.16.840.1.101.3.4.2.6", "SHA-512/256", "SHA512/256"),
    SHA3_224("2.16.840.1.101.3.4.2.7", "SHA3-224"),
    SHA3_256("2.16.840.1.101.3.4.2.8", "SHA3-256"),
    SHA3_384("2.16.840.1.101.3.4.2.9", "SHA3-384"),
    SHA3_512("2.16.840.1.101.3.4.2.10", "SHA3-512"),
    SHAKE128("2.16.840.1.101.3.4.2.11"),
    SHAKE256("2.16.840.1.101.3.4.2.12"),
    HmacSHA3_224("2.16.840.1.101.3.4.2.13", "HmacSHA3-224"),
    HmacSHA3_256("2.16.840.1.101.3.4.2.14", "HmacSHA3-256"),
    HmacSHA3_384("2.16.840.1.101.3.4.2.15", "HmacSHA3-384"),
    HmacSHA3_512("2.16.840.1.101.3.4.2.16", "HmacSHA3-512"),
    SHAKE128_LEN("2.16.840.1.101.3.4.2.17", "SHAKE128-LEN"),
    SHAKE256_LEN("2.16.840.1.101.3.4.2.18", "SHAKE256-LEN"),

    // sigAlgs 2.16.840.1.101.3.4.3.*
    SHA224withDSA("2.16.840.1.101.3.4.3.1"),
    SHA256withDSA("2.16.840.1.101.3.4.3.2"),
    SHA384withDSA("2.16.840.1.101.3.4.3.3"),
    SHA512withDSA("2.16.840.1.101.3.4.3.4"),
    SHA3_224withDSA("2.16.840.1.101.3.4.3.5", "SHA3-224withDSA"),
    SHA3_256withDSA("2.16.840.1.101.3.4.3.6", "SHA3-256withDSA"),
    SHA3_384withDSA("2.16.840.1.101.3.4.3.7", "SHA3-384withDSA"),
    SHA3_512withDSA("2.16.840.1.101.3.4.3.8", "SHA3-512withDSA"),
    SHA3_224withECDSA("2.16.840.1.101.3.4.3.9", "SHA3-224withECDSA"),
    SHA3_256withECDSA("2.16.840.1.101.3.4.3.10", "SHA3-256withECDSA"),
    SHA3_384withECDSA("2.16.840.1.101.3.4.3.11", "SHA3-384withECDSA"),
    SHA3_512withECDSA("2.16.840.1.101.3.4.3.12", "SHA3-512withECDSA"),
    SHA3_224withRSA("2.16.840.1.101.3.4.3.13", "SHA3-224withRSA"),
    SHA3_256withRSA("2.16.840.1.101.3.4.3.14", "SHA3-256withRSA"),
    SHA3_384withRSA("2.16.840.1.101.3.4.3.15", "SHA3-384withRSA"),
    SHA3_512withRSA("2.16.840.1.101.3.4.3.16", "SHA3-512withRSA"),

    // RSASecurity
    // PKCS1 1.2.840.113549.1.1.*
    PKCS1("1.2.840.113549.1.1", "RSA") { // RSA KeyPairGenerator and KeyFactory
        @Override
        boolean isEnabled() { return false; }
    },
    RSA("1.2.840.113549.1.1.1"), // RSA encryption

    MD2withRSA("1.2.840.113549.1.1.2"),
    MD5withRSA("1.2.840.113549.1.1.4"),
    SHA1withRSA("1.2.840.113549.1.1.5"),
    OAEP("1.2.840.113549.1.1.7"),
    MGF1("1.2.840.113549.1.1.8"),
    PSpecified("1.2.840.113549.1.1.9"),
    RSASSA_PSS("1.2.840.113549.1.1.10", "RSASSA-PSS", "PSS"),
    SHA256withRSA("1.2.840.113549.1.1.11"),
    SHA384withRSA("1.2.840.113549.1.1.12"),
    SHA512withRSA("1.2.840.113549.1.1.13"),
    SHA224withRSA("1.2.840.113549.1.1.14"),
    SHA512$224withRSA("1.2.840.113549.1.1.15", "SHA512/224withRSA"),
    SHA512$256withRSA("1.2.840.113549.1.1.16", "SHA512/256withRSA"),

    // PKCS3 1.2.840.113549.1.3.*
    DiffieHellman("1.2.840.113549.1.3.1", "DiffieHellman", "DH"),

    // PKCS5 1.2.840.113549.1.5.*
    PBEWithMD5AndDES("1.2.840.113549.1.5.3"),
    PBEWithMD5AndRC2("1.2.840.113549.1.5.6"),
    PBEWithSHA1AndDES("1.2.840.113549.1.5.10"),
    PBEWithSHA1AndRC2("1.2.840.113549.1.5.11"),
    PBKDF2WithHmacSHA1("1.2.840.113549.1.5.12"),
    PBES2("1.2.840.113549.1.5.13"),

    // PKCS7 1.2.840.113549.1.7.*
    PKCS7("1.2.840.113549.1.7"),
    Data("1.2.840.113549.1.7.1"),
    SignedData("1.2.840.113549.1.7.2"),
    JDK_OLD_Data("1.2.840.1113549.1.7.1"), // extra 1 in 4th component
    JDK_OLD_SignedData("1.2.840.1113549.1.7.2"),
    EnvelopedData("1.2.840.113549.1.7.3"),
    SignedAndEnvelopedData("1.2.840.113549.1.7.4"),
    DigestedData("1.2.840.113549.1.7.5"),
    EncryptedData("1.2.840.113549.1.7.6"),

    // digestAlgs 1.2.840.113549.2.*
    MD2("1.2.840.113549.2.2"),
    MD5("1.2.840.113549.2.5"),
    HmacSHA1("1.2.840.113549.2.7"),
    HmacSHA224("1.2.840.113549.2.8"),
    HmacSHA256("1.2.840.113549.2.9"),
    HmacSHA384("1.2.840.113549.2.10"),
    HmacSHA512("1.2.840.113549.2.11"),
    HmacSHA512$224("1.2.840.113549.2.12", "HmacSHA512/224"),
    HmacSHA512$256("1.2.840.113549.2.13", "HmacSHA512/256"),

    // ANSI --
    // X9 1.2.840.10040.4.*
    DSA("1.2.840.10040.4.1"),
    SHA1withDSA("1.2.840.10040.4.3", "SHA1withDSA", "DSS"),
    // X9.62 1.2.840.10045.*
    EC("1.2.840.10045.2.1"),

    c2tnb191v1("1.2.840.10045.3.0.5", "X9.62 c2tnb191v1"),
    c2tnb191v2("1.2.840.10045.3.0.6", "X9.62 c2tnb191v2"),
    c2tnb191v3("1.2.840.10045.3.0.7", "X9.62 c2tnb191v3"),
    //c2pnb208w1("1.2.840.10045.3.0.10", "X9.62 c2pnb208w1"),
    c2tnb239v1("1.2.840.10045.3.0.11", "X9.62 c2tnb239v1"),
    c2tnb239v2("1.2.840.10045.3.0.12", "X9.62 c2tnb239v2"),
    c2tnb239v3("1.2.840.10045.3.0.13", "X9.62 c2tnb239v3"),
    c2tnb359v1("1.2.840.10045.3.0.18", "X9.62 c2tnb359v1"),
    c2tnb431r1("1.2.840.10045.3.0.20", "X9.62 c2tnb431r1"),

    secp192r1("1.2.840.10045.3.1.1",
            "secp192r1", "NIST P-192", "X9.62 prime192v1"),
    prime192v2("1.2.840.10045.3.1.2", "X9.62 prime192v2"),
    prime192v3("1.2.840.10045.3.1.3", "X9.62 prime192v3"),
    prime239v1("1.2.840.10045.3.1.4", "X9.62 prime239v1"),
    prime239v2("1.2.840.10045.3.1.5", "X9.62 prime239v2"),
    prime239v3("1.2.840.10045.3.1.6", "X9.62 prime239v3"),
    secp256r1("1.2.840.10045.3.1.7",
            "secp256r1", "NIST P-256", "X9.62 prime256v1"),
    SHA1withECDSA("1.2.840.10045.4.1"),
    SHA224withECDSA("1.2.840.10045.4.3.1"),
    SHA256withECDSA("1.2.840.10045.4.3.2"),
    SHA384withECDSA("1.2.840.10045.4.3.3"),
    SHA512withECDSA("1.2.840.10045.4.3.4"),
    SpecifiedSHA2withECDSA("1.2.840.10045.4.3"),

    // X9.42 1.2.840.10046.2.*
    X942_DH("1.2.840.10046.2.1", "DiffieHellman") { // unused by JDK providers
        @Override
        boolean isEnabled() { return false; }
    },

    sect131r1("1.3.132.0.22"),
    sect131r2("1.3.132.0.23"),
    sect193r1("1.3.132.0.24"),
    sect193r2("1.3.132.0.25"),
    sect233k1("1.3.132.0.26", "sect233k1", "NIST K-233"),
    sect233r1("1.3.132.0.27", "sect233r1", "NIST B-233"),
    secp128r1("1.3.132.0.28"),
    secp128r2("1.3.132.0.29"),
    secp160r2("1.3.132.0.30"),
    secp192k1("1.3.132.0.31"),
    secp224k1("1.3.132.0.32"),
    secp224r1("1.3.132.0.33", "secp224r1", "NIST P-224"),
    secp384r1("1.3.132.0.34", "secp384r1", "NIST P-384"),
    secp521r1("1.3.132.0.35", "secp521r1", "NIST P-521"),
    sect409k1("1.3.132.0.36", "sect409k1", "NIST K-409"),
    sect409r1("1.3.132.0.37", "sect409r1", "NIST B-409"),
    sect571k1("1.3.132.0.38", "sect571k1", "NIST K-571"),
    sect571r1("1.3.132.0.39", "sect571r1", "NIST B-571"),

    ECDH("1.3.132.1.12"),

    // OIW secsig 1.3.14.3.*
    OIW_DES_CBC("1.3.14.3.2.7", "DES/CBC", "DES"),

    OIW_DSA("1.3.14.3.2.12", "DSA") {
        @Override
        boolean isEnabled() { return false; }
    },

    OIW_JDK_SHA1withDSA("1.3.14.3.2.13", "SHA1withDSA") {
        @Override
        boolean isEnabled() { return false; }
    },

    OIW_SHA1withRSA_Odd("1.3.14.3.2.15", "SHA1withRSA") {
        @Override
        boolean isEnabled() { return false; }
    },

    DESede("1.3.14.3.2.17", "DESede"),

    SHA_1("1.3.14.3.2.26", "SHA-1", "SHA", "SHA1"),

    OIW_SHA1withDSA("1.3.14.3.2.27", "SHA1withDSA") {
        @Override
        boolean isEnabled() { return false; }
    },

    OIW_SHA1withRSA("1.3.14.3.2.29", "SHA1withRSA") {
        @Override
        boolean isEnabled() { return false; }
    },
    ;

    private String algName;
    private String oid;
    private String[] aliases;

    // find the matching enum using either name or oid string
    // return null if no match found
    public static OIDS findMatch(String s) {
        s = s.toUpperCase(Locale.ENGLISH);
        return name2enum.get(s);
    }

    private static final ConcurrentHashMap<String, OIDS> name2enum =
            new ConcurrentHashMap<>();

    static { for (OIDS o : OIDS.values()) { addOid(o); }; }

    private static void addOid(OIDS o) {
        OIDS ov = name2enum.put(o.oid, o);
        if (ov != null) {
            throw new RuntimeException("ERROR: This OID " + o.oid + " already exists in map, oid=" + ov);
        }
        //System.out.println(o.oid + " => " + o.name());
        if (o.isEnabled()) {//only enabled add into map
            String algName = o.algName.toUpperCase(Locale.ENGLISH);
            if (Objects.nonNull(name2enum.put(algName, o))) {
                throw new RuntimeException("ERROR: Duplicate algName " + algName + " exists already");
            }
            //System.out.println(algName + " => " + o.name());

            for (String a :  o.aliases) {
                String aliasUpper = a.toUpperCase(Locale.ENGLISH);
                if (Objects.nonNull(name2enum.put(aliasUpper, o))) {
                    throw new RuntimeException("ERROR: Duplicate alias " + aliasUpper + " exists already");
                }
                //System.out.println(aliasUpper + " => " + o.name());
            }
        }
    }

    OIDS(String oid) {
        this.oid = oid;
        this.algName = name(); // defaults to enum name
        this.aliases = new String[0];
    }

    OIDS(String oid, String algName, String... aliases) {
        this.oid = oid;
        this.algName = algName;
        this.aliases = aliases;
    }

    boolean isEnabled() {
        return true;
    }

    // returns the oid string related to enum item
    public String value() {
        return oid;
    }

    // returns the standard algorithm name
    public String algName() {
        return algName;
    }

    // return the internal aliases
    public String[] aliases() {
        return aliases;
    }
}
