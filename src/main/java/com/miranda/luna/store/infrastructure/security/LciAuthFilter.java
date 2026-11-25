package com.miranda.luna.store.infrastructure.security;

import com.miranda.luna.store.domain.model.AuthenticatedOperator;
import com.miranda.luna.store.infrastructure.web.dto.ApiResponse;
import com.miranda.luna.store.infrastructure.web.filter.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class LciAuthFilter extends OncePerRequestFilter {

    public static final String CURRENT_OPERATOR_ATTRIBUTE = "CURRENT_OPERATOR";

    private final LciAuthClientPort lciAuthClientPort;
    private final ObjectMapper objectMapper;

    public LciAuthFilter(LciAuthClientPort lciAuthClientPort, ObjectMapper objectMapper) {
        this.lciAuthClientPort = lciAuthClientPort;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        String path = request.getRequestURI();
        return path.equals("/health")
                || path.startsWith("/health/")
                || path.equals("/api/v1/store/status")
                || !path.startsWith("/api/v1/store");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestId = getRequestId(request);
        String token = null;
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7).trim();
        } else if (request.getParameter("token") != null && !request.getParameter("token").isBlank()) {
            token = request.getParameter("token").trim();
        } else if (request.getParameter("access_token") != null && !request.getParameter("access_token").isBlank()) {
            token = request.getParameter("access_token").trim();
        }

        if (token == null || token.isBlank()) {
            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Missing or invalid Authorization header", "UNAUTHORIZED", requestId);
            return;
        }

        Optional<AuthenticatedOperator> operatorOpt = lciAuthClientPort.introspect(token);
        if (operatorOpt.isEmpty()) {
            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid or expired operator session token", "UNAUTHORIZED", requestId);
            return;
        }

        AuthenticatedOperator operator = operatorOpt.get();
        String requiredPermission = resolveRequiredPermission(request);

        if (!operator.can(requiredPermission)
                && !operator.can("store::*")
                && !operator.can("store::bucket::*")
                && !operator.can("store::object::*")
                && !operator.can("*::*::*")
                && !operator.can("*")) {
            writeErrorResponse(response, HttpServletResponse.SC_FORBIDDEN,
                    "Operator lacks required permission: " + requiredPermission, "FORBIDDEN", requestId);
            return;
        }

        request.setAttribute(CURRENT_OPERATOR_ATTRIBUTE, operator);
        request.setAttribute("BEARER_TOKEN", token);

        List<SimpleGrantedAuthority> authorities = operator.permissions().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(operator.username(), token, authorities);

        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private String resolveRequiredPermission(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        if (path.contains("/objects")) {
            if ("GET".equalsIgnoreCase(method)) {
                return "store::object::read";
            }
            if ("POST".equalsIgnoreCase(method)) {
                if (path.contains("/delete-batch")) {
                    return "store::object::delete";
                }
                return "store::object::create";
            }
            if ("DELETE".equalsIgnoreCase(method)) {
                return "store::object::delete";
            }
        }

        if (path.startsWith("/api/v1/store/buckets")) {
            if ("GET".equalsIgnoreCase(method)) {
                return "store::bucket::read";
            }
            if ("POST".equalsIgnoreCase(method)) {
                return "store::bucket::create";
            }
            if ("PUT".equalsIgnoreCase(method)) {
                return "store::bucket::policy";
            }
            if ("DELETE".equalsIgnoreCase(method)) {
                return "store::bucket::delete";
            }
        }

        return "store::*";
    }

    private String getRequestId(HttpServletRequest request) {
        Object attr = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        if (attr != null) {
            return attr.toString();
        }
        String header = request.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        if (header != null && !header.isBlank()) {
            return header;
        }
        return UUID.randomUUID().toString();
    }

    private void writeErrorResponse(HttpServletResponse response, int status, String message, String code, String requestId) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> errorBody = Map.of(
                "message", message,
                "code", code
        );

        ApiResponse<Map<String, Object>> envelope = ApiResponse.of(errorBody, requestId);
        response.getWriter().write(objectMapper.writeValueAsString(envelope));
    }
}
