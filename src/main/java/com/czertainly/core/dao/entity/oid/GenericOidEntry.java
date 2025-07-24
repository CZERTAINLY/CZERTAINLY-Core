package com.czertainly.core.dao.entity.oid;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("GENERIC")
public class GenericOidEntry extends OidEntry {
}
