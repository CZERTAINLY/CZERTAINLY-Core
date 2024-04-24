package com.czertainly.core.api.cmp.message;

import org.bouncycastle.asn1.ASN1Enumerated;
import org.bouncycastle.asn1.ASN1GeneralizedTime;
import org.bouncycastle.asn1.ASN1Object;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.anssi.ANSSIObjectIdentifiers;
import org.bouncycastle.asn1.bc.BCObjectIdentifiers;
import org.bouncycastle.asn1.bsi.BSIObjectIdentifiers;
import org.bouncycastle.asn1.cmp.*;
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers;
import org.bouncycastle.asn1.crmf.CRMFObjectIdentifiers;
import org.bouncycastle.asn1.cryptopro.CryptoProObjectIdentifiers;
import org.bouncycastle.asn1.dvcs.DVCSObjectIdentifiers;
import org.bouncycastle.asn1.eac.EACObjectIdentifiers;
import org.bouncycastle.asn1.edec.EdECObjectIdentifiers;
import org.bouncycastle.asn1.gnu.GNUObjectIdentifiers;
import org.bouncycastle.asn1.iana.IANAObjectIdentifiers;
import org.bouncycastle.asn1.icao.ICAOObjectIdentifiers;
import org.bouncycastle.asn1.isismtt.ISISMTTObjectIdentifiers;
import org.bouncycastle.asn1.iso.ISOIECObjectIdentifiers;
import org.bouncycastle.asn1.kisa.KISAObjectIdentifiers;
import org.bouncycastle.asn1.microsoft.MicrosoftObjectIdentifiers;
import org.bouncycastle.asn1.misc.MiscObjectIdentifiers;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.ntt.NTTObjectIdentifiers;
import org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers;
import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.sec.SECObjectIdentifiers;
import org.bouncycastle.asn1.teletrust.TeleTrusTObjectIdentifiers;
import org.bouncycastle.asn1.ua.UAObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.X509ObjectIdentifiers;
import org.bouncycastle.asn1.x509.qualified.ETSIQCObjectIdentifiers;
import org.bouncycastle.asn1.x509.qualified.RFC3739QCObjectIdentifiers;
import org.bouncycastle.asn1.x509.sigi.SigIObjectIdentifiers;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle.pqc.asn1.PQCObjectIdentifiers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A utility class providing functions for dumping messages.
 *
 * @author Andreas Kretschmer (cmp-ra-component/Apache 2.0 Licence)
 *
 * @see <a href="https://github.com/siemens/cmp-ra-component/blob/main/src/main/java/com/siemens/pki/cmpracomponent/util/MessageDumper.java">...</a>
 * @see <a href="https://law.stackexchange.com/questions/59999/can-i-copy-pieces-of-apache-licensed-source-code-if-i-attribute">...</a>
 */
public class PkiMessageDumper {

    private static final Logger LOG = LoggerFactory.getLogger(PkiMessageDumper.class.getName());
    private static final Map<Integer, String> TYPES = new ConcurrentHashMap<>();
    private static Map<ASN1ObjectIdentifier, OidDescription> oidToKeyMap;

    static {
        // load symbolic names defined in PKIBody
        for (final Field aktField : PKIBody.class.getFields()) {
            if (aktField.getType().equals(Integer.TYPE)
                    && (aktField.getModifiers() & Modifier.STATIC) != 0
                    && aktField.getName().startsWith("TYPE_")) {
                try {
                    TYPES.put(aktField.getInt(null), aktField.getName().substring(5));
                } catch (IllegalArgumentException | IllegalAccessException e) {
                    LOG.error("error filling typemap", e);
                }
            }
        }
    }

    /**
     * Dump PKI message to a string.
     *
     * @param msg PKI message to be dumped
     * @return string representation of the PKI message
     */
    public static String dumpPkiMessage(final PKIMessage msg) {
        if (msg == null) {
            return "<null>";
        }
        final StringBuilder ret = new StringBuilder(10000);
        ret.append("\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> ");
        ret.append(msgTypeAsString(msg));
        ret.append(" message:\n");
        try {
            dumpSingleValue("Header", msg.getHeader(), ret);
            dumpSingleValue("Body", msg.getBody(), ret);
            dumpSingleValue("Protection", msg.getProtection(), ret);
            dumpSingleValue("ExtraCerts", msg.getExtraCerts(), ret);
        } catch (final Exception e) {
            LOG.error("dump error", e);
        }
        ret.append("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<\n");
        return ret.toString();
    }

    private static void dumpSingleValue(final String indent, final Object callRet, final StringBuilder ret)
            throws ParseException {
        if (callRet == null) {
            ret.append(indent);
            ret.append(":<absent>\n");
            return;
        }
        if (callRet.getClass().isArray()) {
            if (callRet instanceof byte[]) {
                ret.append(indent);
                ret.append(": ");
                ret.append(Arrays.toString((byte[]) callRet));
                ret.append("\n");
                return;
            }
            final Object[] callRetArray = (Object[]) callRet;
            if (callRetArray.length == 0) {
                ret.append(indent);
                ret.append(":[]\n");
                return;
            }
            for (int i = 0; i < callRetArray.length; i++) {
                final Object elem = callRetArray[i];
                dumpSingleValue(indent + "[" + i + "]", elem, ret);
            }
            return;
        }
        if (callRet instanceof Iterable<?> callRetCollection) {
            int i = 0;
            for (final Object elem : callRetCollection) {
                dumpSingleValue(indent + "[" + i++ + "]", elem, ret);
            }
            return;
        }
        if (callRet instanceof ASN1GeneralizedTime) {
            ret.append(indent).append(": ").append(((ASN1GeneralizedTime) callRet).getDate()).append("\n");
            return;
        }
        if (callRet instanceof ASN1ObjectIdentifier) {
            ret.append(indent).append(": ").append(getOidDescriptionForOid((ASN1ObjectIdentifier) callRet)).append("\n");
            return;
        }
        if (callRet instanceof PKIFreeText val) {
            final int size = val.size();
            if (size == 0) {
                ret.append(indent);
                ret.append(":[]\n");
                return;
            }
            for (int i = 0; i < size; i++) {
                ret.append(indent).append("[").append(i).append("] : ").append(val.getStringAtUTF8(i)).append("\n");
            }
            return;
        }
        if (callRet instanceof PollRepContent val) {
            final int size = val.size();
            if (size == 0) {
                ret.append(indent);
                ret.append(":[]\n");
                return;
            }
            for (int i = 0; i < size; i++) {
                dumpSingleValue(indent + "[" + i + "]/CertReqId: ", val.getCertReqId(i), ret);
                dumpSingleValue(indent + "[" + i + "]/Reason: ", val.getReason(i), ret);
                dumpSingleValue(indent + "[" + i + "]/CheckAfter: ", val.getCheckAfter(i), ret);
            }
            return;
        }
        if (callRet instanceof Extensions val) {
            final ASN1ObjectIdentifier[] extensionOIDs = val.getExtensionOIDs();
            final int size = extensionOIDs.length;
            if (size == 0) {
                ret.append(indent);
                ret.append(":[]\n");
                return;
            }
            for (int i = 0; i < size; i++) {
                final Extension ext = val.getExtension(extensionOIDs[i]);
                dumpSingleValue(
                        indent + "[" + i + "]/Id" + (ext.isCritical() ? "(critical)" : ""), ext.getExtnId(), ret);
                dumpSingleValue(indent + "[" + i + "]/Value", ext.getParsedValue(), ret);
            }
            return;
        }
        if (callRet instanceof ASN1Enumerated) {
            ret.append(indent).append(": ").append(((ASN1Enumerated) callRet).getValue()).append("\n");
            return;
        }
        if (callRet instanceof org.bouncycastle.asn1.ASN1Primitive
                || callRet instanceof GeneralName
                || callRet instanceof Number
                || callRet instanceof CharSequence
                || callRet instanceof X500Name
                || callRet instanceof Date) {
            ret.append(indent).append(": ").append(callRet).append("\n");
            return;
        }
        if (callRet instanceof ASN1Object) {
            dump(indent + "/", (ASN1Object) callRet, ret);
            return;
        }
        ret.append(indent);
        ret.append(": <could not decode, skipped> ==============\n");
    }

    /**
     * @param msg message to be dumped
     * @return short representation of the message
     */
    public static String msgAsShortString(final PKIMessage msg) {
        if (msg == null) {
            return "<null>";
        }
        return "tid=" +msg.getHeader().getTransactionID()+ ": "+ msgTypeAsString(msg.getBody()) + " [" + msg.getHeader().getSender() + " => "
                + msg.getHeader().getRecipient() + "]";
    }

    /**
     * Get message type from a PKI message body as string
     *
     * @param msgType PKI message type
     * @return message type as string
     */
    public static String msgTypeAsString(final int msgType) {
        return TYPES.get(msgType);
    }

    /**
     * Get message type from a PKI message body as string
     *
     * @param body PKI message body
     * @return message type as string
     */
    public static String msgTypeAsString(final PKIBody body) {
        return TYPES.get(body.getType());
    }

    /**
     * Get message type from a PKI message as string
     *
     * @param msg PKI message
     * @return message type as string
     */
    public static String msgTypeAsString(final PKIMessage msg) {
        if (msg == null) {
            return null;
        }
        return msgTypeAsString(msg.getBody());
    }

    private static void dump(final String indent, final ASN1Object object, final StringBuilder ret) {
        final List<String> nullMemberList = new ArrayList<>();
        for (final Method method : object.getClass().getMethods()) {
            if ((method.getModifiers() & Modifier.STATIC) != 0 || method.getParameterCount() != 0) {
                continue;
            }
            final Class<?> declaringClass = method.getDeclaringClass();
            if (declaringClass.equals(Object.class) || declaringClass.equals(ASN1Object.class)) {
                continue;
            }
            final String methodName = method.getName();
            try {
                final boolean isGetter = methodName.startsWith("get");
                final boolean isArray = methodName.startsWith("to") && methodName.endsWith("Array");
                if (!isGetter && !isArray) {
                    continue;
                }
                String memberName;
                if (isGetter) {
                    memberName = methodName.substring(3);
                } else {
                    memberName = methodName.substring(2).replace("Array", "");
                }
                final Object callRet = method.invoke(object);
                if (callRet == null) {
                    nullMemberList.add(memberName);
                    continue;
                }
                dumpSingleValue(indent + memberName, callRet, ret);
            } catch (final InvocationTargetException ex) {
                ret.append(indent).append(methodName).append(": ").append(ex.getTargetException().getMessage()).append(": <could not parse, skipped> ==============\n");
            } catch (final Exception ex) {
                ret.append(indent).append(methodName).append(":").append(ex.getMessage()).append(": <could not parse, skipped> ==============\n");
            }
        }
        if (!nullMemberList.isEmpty()) {
            ret.append(indent);
            ret.append('(');
            for (final String akt : nullMemberList) {
                ret.append(akt);
                ret.append('|');
            }
            ret.deleteCharAt(ret.length() - 1);
            ret.append("):<null>\n");
        }
    }    public static class OidDescription {
        private final String id;
        private final String declaringPackage;
        private final ASN1ObjectIdentifier oid;
        private final Class<?> declaringClass;

        /**
         * Constructor for OID Descriptor class
         *
         * @param declaringClass declaring class of the OID
         * @param id             ID
         * @param oid            ASN.1 representation of the OID
         */
        public OidDescription(final Class<?> declaringClass, final String id, final ASN1ObjectIdentifier oid) {
            this.declaringPackage =
                    ifNotNull(declaringClass, x -> x.getSimpleName().replace("ObjectIdentifiers", ""));
            this.declaringClass = declaringClass;
            this.id = id;
            this.oid = oid;
        }

        /**
         * get declaring class
         * @return declaring class
         */
        public String getBcDeclaration() {
            return ifNotNull(declaringClass, Class::getCanonicalName) + "." + id;
        }

        /**
         * Get declaring package of the OID
         *
         * @return declaring package of the OID
         */
        public String getDeclaringPackage() {
            return declaringPackage;
        }

        /**
         * Get ID
         *
         * @return ID
         */
        public String getId() {
            return id;
        }

        /**
         * Get ASN.1 representation of the OID
         *
         * @return ASN.1 representation of the OID
         */
        public ASN1ObjectIdentifier getOid() {
            return oid;
        }

        @Override
        public String toString() {
            return declaringPackage + "." + id + " (" + oid + ")";
        }
    }    /**
     * // load ObjectIdentifiers defined somewhere in BouncyCastle
     */
    private static synchronized void initNameOidMaps() {
        if (oidToKeyMap != null) {
            // already initialized
            return;
        }
        oidToKeyMap = new HashMap<>();
        for (final Class<?> aktClass : Arrays.asList(
                CMPObjectIdentifiers.class,
                PKCSObjectIdentifiers.class,
                X509ObjectIdentifiers.class,
                OIWObjectIdentifiers.class,
                CRMFObjectIdentifiers.class,
                CryptoProObjectIdentifiers.class,
                EACObjectIdentifiers.class,
                NISTObjectIdentifiers.class,
                ICAOObjectIdentifiers.class,
                ISISMTTObjectIdentifiers.class,
                SECObjectIdentifiers.class,
                ANSSIObjectIdentifiers.class,
                BCObjectIdentifiers.class,
                BSIObjectIdentifiers.class,
                CMSObjectIdentifiers.class,
                DVCSObjectIdentifiers.class,
                GNUObjectIdentifiers.class,
                IANAObjectIdentifiers.class,
                ISISMTTObjectIdentifiers.class,
                ISOIECObjectIdentifiers.class,
                KISAObjectIdentifiers.class,
                MicrosoftObjectIdentifiers.class,
                MiscObjectIdentifiers.class,
                NTTObjectIdentifiers.class,
                OCSPObjectIdentifiers.class,
                TeleTrusTObjectIdentifiers.class,
                UAObjectIdentifiers.class,
                ETSIQCObjectIdentifiers.class,
                RFC3739QCObjectIdentifiers.class,
                SigIObjectIdentifiers.class,
                X9ObjectIdentifiers.class,
                PQCObjectIdentifiers.class,
                org.bouncycastle.asn1.x509.Extension.class,
                EdECObjectIdentifiers.class,
                CMPObjectIdentifiers.class)) {
            for (final Field aktField : aktClass.getFields()) {
                if (aktField.getType().equals(ASN1ObjectIdentifier.class)
                        && (aktField.getModifiers() & Modifier.STATIC) != 0) {
                    try {
                        final ASN1ObjectIdentifier oid = (ASN1ObjectIdentifier) aktField.get(null);
                        final String name = aktField.getName();
                        final OidDescription oidDescription = new OidDescription(aktClass, name, oid);
                        oidToKeyMap.put(oid, oidDescription);
                    } catch (IllegalArgumentException | IllegalAccessException e) {
                        LOG.error("error loading ObjectIdentifier Names from BC", e);
                    }
                }
            }
        }
    }

    /**
     * Get OID Description for a given OID (ASN.1 representation)
     *
     * @param oid OID (ASN.1 representation)
     * @return OID Description for a given OID (ASN.1 representation)
     */
    public static OidDescription getOidDescriptionForOid(final ASN1ObjectIdentifier oid) {
        initNameOidMaps();
        final OidDescription ret = oidToKeyMap.get(oid);
        if (ret == null) {
            return new OidDescription(null, "<unknown>", oid);
        }
        return ret;
    }

    /**
     * function with one argument throwing an exception
     * @param <T> argument type
     * @param <R> result type
     * @param <E> exception type
     */
    public interface ExFunction<T, R, E extends Exception> {
        /**
         * execute function
         * @param arg function argument
         * @return result
         * @throws E in case of error
         */
        R apply(T arg) throws E;
    }

    /**
     * evaluate a function if a parameter is not <code>null</code>
     * @param <T> function result type
     * @param <R> value type
     * @param <E> exception thrown by function
     * @param value value to evaluate for <code>null</code>, function parameter
     * @param function function to evaluate
     * @return null or function result
     * @throws E if function throws an exception
     */
    public static <T, R, E extends Exception> T ifNotNull(final R value, final ExFunction<R, T, E> function) throws E {
        try {
            return value == null ? null : function.apply(value);
        } catch (final NullPointerException npe) {
            return null;
        }
    }
}
