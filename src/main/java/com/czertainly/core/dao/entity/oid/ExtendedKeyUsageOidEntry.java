package com.czertainly.core.dao.entity.oid;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("EXTENDED_KEY_USAGE")
public class ExtendedKeyUsageOidEntry extends OidEntry {
}
