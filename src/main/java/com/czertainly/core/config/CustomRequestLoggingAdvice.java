package com.czertainly.core.config;

import com.czertainly.core.util.SerializationUtil;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.stream.Collectors;

@ControllerAdvice
public class CustomRequestLoggingAdvice extends RequestBodyAdviceAdapter {
    @Autowired
    HttpServletRequest httpServletRequest;

    Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public boolean supports(MethodParameter methodParameter, Type type,
                            Class<? extends HttpMessageConverter<?>> aClass) {
        return true;
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage,
                                MethodParameter parameter, Type targetType,
                                Class<? extends HttpMessageConverter<?>> converterType) {
        ToStringBuilder debugMessage = new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("METHOD", httpServletRequest.getMethod())
                .append("PATH", httpServletRequest.getRequestURI())
                .append("FROM", httpServletRequest.getRemoteAddr())
                .append("REQUEST TYPE", httpServletRequest.getContentType())
                .append("REQUEST HEADERS", Collections.list(httpServletRequest.getHeaderNames()).stream()
                        .map(r -> r + " : " + httpServletRequest.getHeader(r)).collect(Collectors.toList()))
                .append("REQUEST BODY", SerializationUtil.serialize(body));
        logger.debug("REQUEST DATA: {}", debugMessage);
        return super.afterBodyRead(body, inputMessage, parameter, targetType, converterType);
    }

}
