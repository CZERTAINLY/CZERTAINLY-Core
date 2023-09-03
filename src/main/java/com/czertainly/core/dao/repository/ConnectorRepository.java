package com.czertainly.core.dao.repository;

import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.FunctionGroup;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConnectorRepository extends SecurityFilterRepository<Connector, Long> {

    Optional<Connector> findByUuid(UUID uuid);

    Optional<Connector> findByName(String name);

    List<Connector> findByStatus(ConnectorStatus status);

    @Query(value =
            " select c from Connector c " +
                    " join c.functionGroups c2fg " +
                    " where c.status = 'CONNECTED' " +
                    "   and c2fg.functionGroup = :functionGroup " +
                    "   and c2fg.kinds like '%' || :kind || '%' "
    )
    List<Connector> findConnectedByFunctionGroupAndKind(
            @Param("functionGroup") FunctionGroup functionGroup,
            @Param("kind") String kind);
}
