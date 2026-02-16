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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
        List<String> expiredSessionIds = new ArrayList<>();
        String sql = "SELECT SESSION_ID FROM SPRING_SESSION WHERE EXPIRY_TIME < ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, now);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        expiredSessionIds.add(rs.getString("SESSION_ID"));
                    }
            }
        } catch (Exception e) {
            logger.error(MarkerFactory.getMarker(EXPIRED_SESSION), "Failed to query expired sessions: {}", e.getMessage(), e);
            return;
        }
        logger.debug("Found {} expired sessions to process.", expiredSessionIds.size());
        for (String sessionId : expiredSessionIds) {
            logger.debug(MarkerFactory.getMarker(EXPIRED_SESSION), "Processing expired session ID: {}", sessionId);
            SecurityContext securityContext = loadSecurityContext(sessionId);
            OAuth2Util.endUserSession(securityContext);
            sessionRepository.deleteById(sessionId);
            logger.debug("Session {} deleted.", sessionId);
        }
    }

    private SecurityContext loadSecurityContext(String sessionId) {
        String sql = "SELECT ATTRIBUTE_BYTES FROM SPRING_SESSION_ATTRIBUTES WHERE SESSION_PRIMARY_ID = (SELECT PRIMARY_ID FROM SPRING_SESSION WHERE SESSION_ID = ?) AND ATTRIBUTE_NAME = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.setString(2, "SPRING_SECURITY_CONTEXT");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    byte[] attributeBytes = rs.getBytes("ATTRIBUTE_BYTES");
                    Object obj = conversionService.convert(attributeBytes, Object.class);
                    if (obj instanceof SecurityContext sc) {
                        return sc;
                    }
                    logger.error(MarkerFactory.getMarker(EXPIRED_SESSION), "Deserialized object is not a SecurityContext for session {}.", sessionId);
                }
            }
        } catch (Exception e) {
            logger.error(MarkerFactory.getMarker(EXPIRED_SESSION), "Failed to load or deserialize SPRING_SECURITY_CONTEXT for session {}: {}", sessionId, e.getMessage(), e);
        }
        return null;
    }
}
