package com.czertainly.core.dao.repository;

import java.util.List;
import java.util.Optional;

import javax.transaction.Transactional;

import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.FunctionGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.czertainly.core.dao.entity.Connector2FunctionGroup;

@Repository
@Transactional
public interface Connector2FunctionGroupRepository extends JpaRepository<Connector2FunctionGroup, Long> {

	List<Connector2FunctionGroup> findAllByConnector(Connector connector);

    Optional<Connector2FunctionGroup> findByConnectorAndFunctionGroup(Connector connector, FunctionGroup functionGroup);
}
