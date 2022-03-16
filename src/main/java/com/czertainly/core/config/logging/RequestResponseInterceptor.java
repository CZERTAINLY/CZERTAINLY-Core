package com.czertainly.core.config.logging;

import com.czertainly.core.config.CustomHttpServletResponseWrapper;
import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class RequestResponseInterceptor implements HandlerInterceptor {

    Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (logger.isDebugEnabled()) {
            String body = CharStreams.toString(new InputStreamReader(
                    request.getInputStream(), Charsets.UTF_8));
            ToStringBuilder debugMessage = new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE)
                    .append("METHOD", request.getMethod())
                    .append("PATH", request.getRequestURI())
                    .append("FROM", request.getRemoteAddr())
                    .append("REQUEST TYPE", request.getContentType())
                    .append("REQUEST HEADERS", Collections.list(request.getHeaderNames()).stream()
                            .map(r -> r + " : " + request.getHeader(r)).collect(Collectors.toList()));
                    if(!request.getMethod().equals(HttpMethod.GET.name())) {
                        debugMessage.append("REQUEST BODY", body);
                    }
            logger.debug("REQUEST DATA: {}", debugMessage);
        }
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           @Nullable ModelAndView modelAndView) throws Exception {
        if(logger.isDebugEnabled()) {
            String responseBody;
            try {
                responseBody = getResponseAsString((CustomHttpServletResponseWrapper) response);
            }catch (ClassCastException e){
                responseBody = "";
            }
            List<String> responseHeaders = response.getHeaderNames().stream()
                    .map(r -> r + " : " + response.getHeaders(r)).collect(Collectors.toList());
            ToStringBuilder debugMessage = new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE)
                    .append("METHOD", request.getMethod())
                    .append("RESPONSE FOR", request.getRequestURI())
                    .append("RESPONSE STATUS", response.getStatus())
                    .append("RESPONSE TYPE", response.getContentType())
                    .append("RESPONSE HEADERS", responseHeaders)
                    .append("RESPONSE BODY", responseBody);
            logger.debug("RESPONSE DATA: {}", debugMessage);
        }
    }

    public String getResponseAsString(CustomHttpServletResponseWrapper wrappedResponse) {
        byte[] data = new byte[wrappedResponse.rawData.size()];
        for (int i = 0; i < data.length; i++) {
            data[i] = wrappedResponse.rawData.get(i);
        }
        String responseBody = new String(data);
        return responseBody;
    }
}
