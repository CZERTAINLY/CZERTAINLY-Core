package com.czertainly.core.config.logging;

import com.czertainly.core.util.SerializationUtil;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.stream.Collectors;

@ControllerAdvice
public class CustomResponseLoggingAdvice implements ResponseBodyAdvice<Object> {
    @Autowired
    HttpServletRequest httpServletRequest;

    Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public boolean supports(MethodParameter methodParameter,
                            Class<? extends HttpMessageConverter<?>> aClass) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter methodParameter,
                                  MediaType mediaType,
                                  Class<? extends HttpMessageConverter<?>> aClass,
                                  ServerHttpRequest serverHttpRequest,
                                  ServerHttpResponse serverHttpResponse) {

        if (serverHttpRequest instanceof ServletServerHttpRequest &&
                serverHttpResponse instanceof ServletServerHttpResponse) {
            HttpServletRequest request = ((ServletServerHttpRequest) serverHttpRequest).getServletRequest();
            HttpServletResponse response = ((ServletServerHttpResponse) serverHttpResponse).getServletResponse();
            Object printableBody;
            try {
                printableBody = SerializationUtil.serialize(body);
            } catch (Exception e){
                printableBody = body.toString();
            }
            ToStringBuilder debugMessage = new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                    .append("RESPONSE FOR", request.getRequestURI())
                    .append("RESPONSE STATUS", response.getStatus())
                    .append("RESPONSE TYPE", response.getContentType())
                    .append("RESPONSE HEADERS", response.getHeaderNames().stream()
                            .map(r -> r + " : " + response.getHeaders(r)).collect(Collectors.toList()))
                    .append("RESPONSE BODY", printableBody);
            logger.debug("RESPONSE DATA: {}", debugMessage);
        }
        return body;
    }
}
