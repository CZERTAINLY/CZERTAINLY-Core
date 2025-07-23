package com.czertainly.core.dao.repository;

import com.czertainly.core.dao.entity.oid.OidEntry;
import org.springframework.stereotype.Repository;

@Repository
public interface OidEntryRepository extends SecurityFilterRepository<OidEntry, String> {

}
