package com.czertainly.core.dao.repository;

import com.czertainly.api.model.core.oid.OidCategory;
import com.czertainly.core.dao.entity.oid.CustomOidEntry;
import org.springframework.data.util.Streamable;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomOidEntryRepository extends SecurityFilterRepository<CustomOidEntry, String> {

    Streamable<CustomOidEntry> findAllByCategory(OidCategory category);

}
