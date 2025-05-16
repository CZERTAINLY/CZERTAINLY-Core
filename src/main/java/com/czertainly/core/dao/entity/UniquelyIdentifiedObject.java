package com.czertainly.core.dao.entity;

import java.io.Serializable;
import java.util.UUID;

public interface UniquelyIdentifiedObject extends Serializable {

    UUID getUuid();

}
