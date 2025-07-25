package com.czertainly.core.dao.repository;

import com.czertainly.api.model.core.oid.OidCategory;
import com.czertainly.core.dao.entity.oid.OidEntry;
import org.springframework.data.util.Streamable;
import org.springframework.stereotype.Repository;

@Repository
public interface OidEntryRepository extends SecurityFilterRepository<OidEntry, String> {

    Streamable<OidEntry> findAllByCategory(OidCategory category);

}
