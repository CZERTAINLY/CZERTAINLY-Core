package com.czertainly.core.tasks;

import com.czertainly.core.messaging.scheduler.SessionExpirationPublisher;
import org.mockito.Mockito;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Duration;

import com.czertainly.core.util.BaseSpringBootTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;


class SessionExpirationPublisherTest extends BaseSpringBootTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FindByIndexNameSessionRepository sessionRepository;

    @Autowired
    private SessionExpirationPublisher sessionExpirationPublisher;

    @Test
    void testSessionExpiration() {

        setupSessionTables();

        Assertions.assertDoesNotThrow(() -> sessionExpirationPublisher.processExpiredSessions());

        Session s = sessionRepository.createSession();
        s.setMaxInactiveInterval(Duration.ZERO);
        sessionRepository.save(s);

        sessionExpirationPublisher.processExpiredSessions();

        Assertions.assertNull(sessionRepository.findById(s.getId()));


        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        s = sessionRepository.createSession();
        s.setMaxInactiveInterval(Duration.ZERO);
        s.setAttribute("SPRING_SECURITY_CONTEXT", securityContext);
        sessionRepository.save(s);

        sessionExpirationPublisher.processExpiredSessions();
        Assertions.assertNull(sessionRepository.findById(s.getId()));
    }

    @Test
    void testProcessExpiredSessions_handlesDatabaseException() throws Exception {
        JdbcIndexedSessionRepository mocked = Mockito.mock(JdbcIndexedSessionRepository.class);
        DataSource dataSource = Mockito.mock(DataSource.class);
        GenericConversionService conversionService = Mockito.mock(GenericConversionService.class);
        Mockito.when(dataSource.getConnection()).thenThrow(new SQLException("DB error"));
        SessionExpirationPublisher publisher = new SessionExpirationPublisher(mocked, dataSource, conversionService);
        Assertions.assertDoesNotThrow(publisher::processExpiredSessions);
    }


    private void setupSessionTables() {
        // Create spring_session table
        jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS spring_session (
                        PRIMARY_ID CHAR(36) NOT NULL,
                        SESSION_ID CHAR(36) NOT NULL,
                        CREATION_TIME BIGINT NOT NULL,
                        LAST_ACCESS_TIME BIGINT NOT NULL,
                        MAX_INACTIVE_INTERVAL INT NOT NULL,
                        EXPIRY_TIME BIGINT NOT NULL,
                        PRINCIPAL_NAME VARCHAR(100),
                        CONSTRAINT spring_session_pkey PRIMARY KEY(PRIMARY_ID)
                    );
                """);

        // Create spring_session_attributes table (your JSON setup)
        jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS spring_session_attributes (
                        SESSION_PRIMARY_ID CHAR(36) NOT NULL,
                        ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
                        ATTRIBUTE_BYTES TEXT,
                        CONSTRAINT spring_session_attributes_pkey PRIMARY KEY(SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
                        CONSTRAINT fk_session FOREIGN KEY(SESSION_PRIMARY_ID) REFERENCES spring_session(PRIMARY_ID) ON DELETE CASCADE
                    );
                """);
    }


}
