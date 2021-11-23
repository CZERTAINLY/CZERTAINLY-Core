package com.czertainly.core.util;

import java.security.PublicKey;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;

import org.bouncycastle.jce.provider.JCEECPublicKey;

public class KeySizeUtil {
	public static int getKeyLength(final PublicKey pk) {
		int len = -1;
		if (pk instanceof RSAPublicKey) {
			final RSAPublicKey rsapub = (RSAPublicKey) pk;
			len = rsapub.getModulus().bitLength();
		} else if (pk instanceof JCEECPublicKey) {
			final JCEECPublicKey ecpriv = (JCEECPublicKey) pk;
			final org.bouncycastle.jce.spec.ECParameterSpec spec = ecpriv.getParameters();
			if (spec != null) {
				len = spec.getN().bitLength();
			} else {
				// We support the key, but we don't know the key length
				len = 0;
			}
		} else if (pk instanceof ECPublicKey) {
			final ECPublicKey ecpriv = (ECPublicKey) pk;
			final java.security.spec.ECParameterSpec spec = ecpriv.getParams();
			if (spec != null) {
				len = spec.getOrder().bitLength(); // does this really return something we expect?
			} else {
				// We support the key, but we don't know the key length
				len = 0;
			}
		} else if (pk instanceof DSAPublicKey) {
			final DSAPublicKey dsapub = (DSAPublicKey) pk;
			if (dsapub.getParams() != null) {
				len = dsapub.getParams().getP().bitLength();
			} else {
				len = dsapub.getY().bitLength();
			}
		}
		return len;
	}
}
