package com.peoplecore.configs;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HeaderDecodingFilter extends OncePerRequestFilter {

    @jakarta.annotation.PostConstruct
    public void init() {
        System.out.println("=== [HeaderDecodingFilter] 빈 등록 완료 ===");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String userName = request.getHeader("X-User-Name");
        System.out.println("=== [HeaderDecodingFilter] 진입, X-User-Name: " + userName);
        if (userName != null) {
            String decoded = URLDecoder.decode(userName, StandardCharsets.UTF_8);
            System.out.println("=== [HeaderDecodingFilter] 디코딩 결과: " + decoded);
            HttpServletRequestWrapper wrapper = new HttpServletRequestWrapper(request) {
                @Override
                public String getHeader(String name) {
                    if ("X-User-Name".equalsIgnoreCase(name)) {
                        return decoded;
                    }
                    return super.getHeader(name);
                }

                @Override
                public Enumeration<String> getHeaders(String name) {
                    if ("X-User-Name".equalsIgnoreCase(name)) {
                        return Collections.enumeration(Collections.singletonList(decoded));
                    }
                    return super.getHeaders(name);
                }
            };
            filterChain.doFilter(wrapper, response);
            return;
        }
        filterChain.doFilter(request, response);
    }
}