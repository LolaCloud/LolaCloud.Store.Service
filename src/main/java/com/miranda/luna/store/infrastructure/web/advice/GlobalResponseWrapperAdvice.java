package com.miranda.luna.store.infrastructure.web.advice;

import com.miranda.luna.store.infrastructure.web.dto.ApiResponse;
import com.miranda.luna.store.infrastructure.web.filter.RequestIdFilter;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@RestControllerAdvice
public class GlobalResponseWrapperAdvice implements ResponseBodyAdvice<Object> {

    private final ObjectMapper objectMapper;

    public GlobalResponseWrapperAdvice(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        Class<?> paramType = returnType.getParameterType();
        return !byte[].class.isAssignableFrom(paramType)
                && !org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.class.isAssignableFrom(paramType);
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        if (body instanceof ApiResponse<?> || body instanceof org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter) {
            return body;
        }

        String requestId = null;
        if (request instanceof ServletServerHttpRequest servletRequest) {
            Object reqIdAttr = servletRequest.getServletRequest().getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
            if (reqIdAttr != null) {
                requestId = reqIdAttr.toString();
            }
        }

        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        ApiResponse<Object> apiResponse = ApiResponse.of(body, requestId);

        if (body instanceof String) {
            try {
                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                return objectMapper.writeValueAsString(apiResponse);
            } catch (Exception e) {
                return apiResponse;
            }
        }

        return apiResponse;
    }
}
