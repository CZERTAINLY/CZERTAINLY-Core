package com.czertainly.core.messaging.scheduler;

import com.czertainly.core.util.OAuth2Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

@Component
public class SessionExpirationPublisher {

    private static final Logger logger = LoggerFactory.getLogger(SessionExpirationPublisher.class);

    private final JdbcIndexedSessionRepository sessionRepository;
    private final JdbcTemplate jdbcTemplate;
    private final GenericConversionService conversionService;

    @Autowired
    public SessionExpirationPublisher(JdbcIndexedSessionRepository sessionRepository,
                                      JdbcTemplate jdbcTemplate,
                                      @Qualifier("springSessionConversionService") GenericConversionService conversionService) {
        this.sessionRepository = sessionRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.conversionService = conversionService;
    }

    @Scheduled(fixedDelay = 60000) // every 60 seconds
    public void processExpiredSessions() {
        long now = Instant.now().toEpochMilli();
        String schema = System.getProperty("DB_SCHEMA");
        if (schema != null) schema += ".";
        else schema = "";
        List<String> expiredSessionIds = jdbcTemplate.query(
                "SELECT SESSION_ID FROM " + schema + "SPRING_SESSION WHERE EXPIRY_TIME < ?",
                ps -> ps.setLong(1, now),
                (rs, rowNum) -> rs.getString("SESSION_ID")
        );
        logger.debug("Found {} expired sessions to process.", expiredSessionIds.size());
        for (String sessionId : expiredSessionIds) {
            logger.debug("Processing expired session ID: {}", sessionId);
            SecurityContext securityContext = loadSecurityContext(sessionId, schema);
            OAuth2Util.endUserSession(securityContext);
            sessionRepository.deleteById(sessionId);
            logger.debug("Session {} deleted.", sessionId);
        }
    }

    private SecurityContext loadSecurityContext(String sessionId, String schema) {
        try {
            byte[] attributeBytes = jdbcTemplate.query(
                    "SELECT ATTRIBUTE_BYTES FROM " + schema + "SPRING_SESSION_ATTRIBUTES WHERE SESSION_PRIMARY_ID = (SELECT PRIMARY_ID FROM " + schema + "SPRING_SESSION WHERE SESSION_ID = ?) AND ATTRIBUTE_NAME = ?",
                    ps -> {
                        ps.setString(1, sessionId);
                        ps.setString(2, "SPRING_SECURITY_CONTEXT");
                    },
                    rs -> rs.next() ? rs.getBytes("ATTRIBUTE_BYTES") : null
            );
            if (attributeBytes == null) return null;
            Object obj = conversionService.convert(attributeBytes, Object.class);
            if (obj instanceof SecurityContext sc) {
                return sc;
            }
            logger.error("Deserialized object is not a SecurityContext for session {}.", sessionId);
            return null;
        } catch (Exception e) {
            logger.error("Failed to load or deserialize SPRING_SECURITY_CONTEXT for session {}: {}", sessionId, e.getMessage(), e);
            return null;
        }
    }
}
