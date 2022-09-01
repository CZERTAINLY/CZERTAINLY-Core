package com.czertainly.core.service.model;

import com.czertainly.core.security.authz.SecurityFilter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecuredListTest {

    @Test
    void createsSecuredListGivenOnlySpecificObjectsAllowed() {
        // given
        SecurableTestItem A = new SecurableTestItem("uuid_A", "A");
        SecurableTestItem B = new SecurableTestItem("uuid_B", "B");
        SecurableTestItem C = new SecurableTestItem("uuid_C", "C");
        SecurableTestItem D = new SecurableTestItem("uuid_D", "D");
        SecurableTestItem E = new SecurableTestItem("uuid_E", "E");
        List<SecurableTestItem> items = List.of(A, B, C, D, E);

        SecurityFilter filter = SecurityFilter.create();
        filter.setAreOnlySpecificObjectsAllowed(true);
        filter.addAllowedObjects(List.of("uuid_A", "uuid_C"));

        // when
        SecuredList<SecurableTestItem> list = SecuredList.fromFilter(filter, items);

        // then
        assertEquals(List.of(A, C), list.getAllowed());
    }

    @Test
    void createsSecuredListGivenAccessIsAllowedToGroupOfObjects() {
        // given
        SecurableTestItem A = new SecurableTestItem("uuid_A", "A");
        SecurableTestItem B = new SecurableTestItem("uuid_B", "B");
        SecurableTestItem C = new SecurableTestItem("uuid_C", "C");
        SecurableTestItem D = new SecurableTestItem("uuid_D", "D");
        SecurableTestItem E = new SecurableTestItem("uuid_E", "E");
        List<SecurableTestItem> items = List.of(A, B, C, D, E);

        SecurityFilter filter = SecurityFilter.create();
        filter.setAreOnlySpecificObjectsAllowed(false);
        filter.addDeniedObjects(List.of("uuid_A", "uuid_C"));

        // when
        SecuredList<SecurableTestItem> list = SecuredList.fromFilter(filter, items);

        // then
        assertEquals(List.of(B, D, E), list.getAllowed());
    }

    static class SecurableTestItem implements Securable {

        private final UUID uuid;
        private final String name;

        public SecurableTestItem(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }

        public SecurableTestItem(String uuid, String name) {
            this.uuid = UUID.fromString(uuid);
            this.name = name;
        }

        @Override
        public UUID getUuid() {
            return uuid;
        }

        @Override
        public String getName() {
            return name;
        }
    }

}