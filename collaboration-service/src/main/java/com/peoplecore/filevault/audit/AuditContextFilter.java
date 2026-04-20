package com.peoplecore.filevault.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * API Gateway 가 JWT 검증 후 내려보내는 X-User-* 헤더를 읽어
 * {@link AuditContextHolder} 에 저장한다.
 *
 * <p>요청 종료 시 ThreadLocal 누수를 막기 위해 try-finally 로 항상 clear 한다.
 * 헤더가 없는 요청(헬스체크 등) 은 컨텍스트를 설정하지 않고 통과시킨다.</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuditContextFilter extends OncePerRequestFilter {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_COMPANY = "X-User-Company";
    private static final String HEADER_USER_NAME = "X-User-Name";
    private static final String HEADER_USER_TITLE = "X-User-Title";

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain
    ) throws ServletException, IOException {
        try {
            populateContext(request);
            chain.doFilter(request, response);
        } finally {
            AuditContextHolder.clear();
        }
    }

    private void populateContext(HttpServletRequest request) {
        String empIdHeader = request.getHeader(HEADER_USER_ID);
        String companyHeader = request.getHeader(HEADER_USER_COMPANY);
        if (empIdHeader == null || companyHeader == null) {
            return;
        }
        try {
            Long empId = Long.parseLong(empIdHeader);
            UUID companyId = UUID.fromString(companyHeader);
            String nameRaw = request.getHeader(HEADER_USER_NAME);
            String name = nameRaw != null
                ? URLDecoder.decode(nameRaw, StandardCharsets.UTF_8)
                : null;
            String titleHeader = request.getHeader(HEADER_USER_TITLE);
            Long titleId = titleHeader != null && !titleHeader.isBlank() && !"null".equals(titleHeader)
                ? Long.parseLong(titleHeader)
                : null;
            AuditContextHolder.set(AuditContext.user(companyId, empId, name, titleId));
        } catch (IllegalArgumentException e) {
            log.warn("AuditContextFilter: invalid auth header (empId={}, company={})",
                empIdHeader, companyHeader);
        }
    }
}
