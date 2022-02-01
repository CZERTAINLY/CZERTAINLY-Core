package com.czertainly.core.config;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Collectors;

@Configuration
@ConditionalOnProperty(value="logging.response.debug.enabled", havingValue = "true")
public class ResponseLoggingFilterConfig implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(ResponseLoggingFilterConfig.class);

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        //Do Nothing
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper((HttpServletResponse) response);
        try {
            chain.doFilter(request, responseWrapper);
            responseWrapper.flushBuffer();
        } finally {
            ToStringBuilder debugMessage = new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                    .append("RESPONSE STATUS", responseWrapper.getStatus())
                    .append("RESPONSE TYPE", responseWrapper.getContentType())
                    .append("RESPONSE HEADERS", responseWrapper.getHeaderNames().stream()
                            .map(r -> r + " : " + responseWrapper.getHeaders(r)).collect(Collectors.toList()))
                    .append("RESPONSE BODY", new String(responseWrapper.getContentAsByteArray()));
            logger.debug("RESPONSE DATA: {}", debugMessage);
            responseWrapper.copyBodyToResponse();
        }
    }

    @Override
    public void destroy() {
        //Do Nothing
    }
}
