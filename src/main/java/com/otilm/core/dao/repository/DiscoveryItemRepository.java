package com.otilm.core.dao.repository;

import com.otilm.core.dao.entity.DiscoveryItem;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Deliberately bare for the schema groundwork: the staging writes, the union listing and the processing claims each
 * bring their own queries with the tasks that own them.
 */
@Repository
public interface DiscoveryItemRepository extends JpaRepository<DiscoveryItem, UUID> {
}
