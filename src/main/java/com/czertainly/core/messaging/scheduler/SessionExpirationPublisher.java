package com.czertainly.core.messaging.scheduler;

import com.czertainly.core.util.OAuth2Util;
import org.slf4j.MarkerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

@Component
public class SessionExpirationPublisher {

    private static final Logger logger = LoggerFactory.getLogger(SessionExpirationPublisher.class);
    public static final String EXPIRED_SESSION = "expired-session";

    private final JdbcIndexedSessionRepository sessionRepository;
    private final DataSource dataSource;
    private final GenericConversionService conversionService;

    @Autowired
    public SessionExpirationPublisher(JdbcIndexedSessionRepository sessionRepository,
                                      DataSource dataSource,
                                      @Qualifier("springSessionConversionService") GenericConversionService conversionService) {
        this.sessionRepository = sessionRepository;
        this.dataSource = dataSource;
        this.conversionService = conversionService;
    }

    @Scheduled(fixedDelay = 60000) // every 60 seconds
    public void processExpiredSessions() {
        long now = Instant.now().toEpochMilli();
        String sql = """
                SELECT s.SESSION_ID, a.ATTRIBUTE_BYTES
                FROM SPRING_SESSION s
                LEFT JOIN SPRING_SESSION_ATTRIBUTES a
                  ON a.SESSION_PRIMARY_ID = s.PRIMARY_ID
                  AND a.ATTRIBUTE_NAME = ?
                WHERE s.EXPIRY_TIME < ?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "SPRING_SECURITY_CONTEXT");
            ps.setLong(2, now);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                processExpiredSession(rs);
            }
        } catch (SQLException e) {
            logger.error(MarkerFactory.getMarker(EXPIRED_SESSION),"Failed to process expired sessions: {}", e.getMessage(), e);
        }
    }

    private void processExpiredSession(ResultSet rs) {
        String sessionId = null;
        try {
            sessionId = rs.getString("SESSION_ID");
            SecurityContext securityContext = null;
            byte[] attributeBytes = rs.getBytes("ATTRIBUTE_BYTES");
            if (attributeBytes != null) {
                securityContext = getSecurityContext(attributeBytes, securityContext, sessionId);
            }
            logger.debug(MarkerFactory.getMarker(EXPIRED_SESSION),
                    "Processing expired session ID: {}", sessionId);
            OAuth2Util.endUserSession(securityContext);
            sessionRepository.deleteById(sessionId);
        } catch (Exception ex) {
            logger.error(MarkerFactory.getMarker(EXPIRED_SESSION),
                    "Failed to process expired session {}: {}",
                    sessionId, ex.getMessage(), ex);
        }
    }

    private SecurityContext getSecurityContext(byte[] attributeBytes, SecurityContext securityContext, String sessionId) {
        try {
            Object obj = conversionService.convert(attributeBytes, Object.class);
            if (obj instanceof SecurityContext sc) {
                securityContext = sc;
            } else {
                logger.error(MarkerFactory.getMarker(EXPIRED_SESSION),
                        "Deserialized object is not a SecurityContext for session {}.", sessionId);
            }
        } catch (Exception ex) {
            logger.error(MarkerFactory.getMarker(EXPIRED_SESSION),
                    "Failed to deserialize SPRING_SECURITY_CONTEXT for session {}: {}",
                    sessionId, ex.getMessage(), ex);
        }
        return securityContext;
    }
}
