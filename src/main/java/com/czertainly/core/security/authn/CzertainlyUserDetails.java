package com.czertainly.core.security.authn;

import com.czertainly.core.security.authn.client.AuthenticationInfo;
import org.springframework.security.core.userdetails.User;

import java.io.Serializable;

public class CzertainlyUserDetails extends User implements Serializable {

    String rawData;

    public CzertainlyUserDetails(AuthenticationInfo authInfo) {
        super(authInfo.getUsername(), "", authInfo.getAuthorities());
        this.rawData = authInfo.getRawData();
    }

    public String getRawData() {
        return rawData;
    }
}
