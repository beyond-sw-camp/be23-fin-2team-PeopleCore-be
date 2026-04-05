package com.peoplecore.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.List;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secretKey}")
    private String secretKey;

    @Value("${internal.api-key}")
    private String internalApiKey;

    private Key accessKey;

    private static final List<String> EXCLUDE_PATHS = List.of(
            "/hr-service/auth/login",
            "/hr-service/auth/refresh",
            "/hr-service/auth/password",
            "/hr-service/auth/email"
    );

//  hr담담자만 추가 접근 가능 경로
    private static final List<String> HR_ONLY_PATHS = List.of(
            "/hr-service/employee",
            "/hr-service/resign"
    );

//  서버운영팀 전용 경로 (API Key 인증)
    private static final String INTERNAL_PATH_PREFIX = "/hr-service/internal/";


    @PostConstruct
    public void init() {
        byte[] keyBytes = Base64.getDecoder().decode(secretKey);
        this.accessKey = Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        System.out.println("=== 요청 경로: " + path);//**

        // 인증 제외 경로
        if (isExcludedPath(path)) {
            return chain.filter(exchange);
        }

        // 서버운영팀 → API Key 인증 (JWT 아님)
        if (path.startsWith(INTERNAL_PATH_PREFIX)) {
            return handleInternalAuth(exchange, chain, request);
        }

        // Authorization 헤더 확인
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return onError(exchange, "인증 토큰이 없습니다.", HttpStatus.UNAUTHORIZED);
        }

        String token = authHeader.substring(7);

        // 토큰 검증 및 Claims 추출
        Claims claims;
        try {
            claims = Jwts.parserBuilder()
                    .setSigningKey(accessKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

        } catch (JwtException e) {

            return onError(exchange, "유효하지 않은 토큰입니다.", HttpStatus.UNAUTHORIZED);
        }

//        hr전용 경로 시 추가 role체크
        if(isHrOnlyPath(path)){
            String role = claims.get("role", String.class);
            if(!role.equals("HR_ADMIN")&&!role.equals("HR_SUPER_ADMIN")){
                return onError(exchange,"접근권한이 없습니다",HttpStatus.FORBIDDEN);
            }
        }

        // 검증 통과 → 사용자 정보를 헤더에 실어서 하위 서비스로 전달
        ServerHttpRequest.Builder requestBuilder = request.mutate()
                .header("X-User-Id", claims.getSubject())
                .header("X-User-Company", claims.get("companyId", String.class))
                .header("X-User-Name", claims.get("name", String.class))
                .header("X-User-Role", claims.get("role", String.class))
                .header("X-User-Department", String.valueOf(claims.get("departmentId")))
                .header("X-User-Grade", String.valueOf(claims.get("gradeId")));

        if (claims.get("titleId") != null) {
            requestBuilder.header("X-User-Title", String.valueOf(claims.get("titleId")));
        }

        ServerHttpRequest mutatedRequest = requestBuilder.build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private boolean isExcludedPath(String path) {
        return EXCLUDE_PATHS.stream().anyMatch(path::startsWith);
    }

//    hr 전용경로 확인
    private boolean isHrOnlyPath(String path){
        for(String hrPath : HR_ONLY_PATHS){
            if(path.startsWith(hrPath)){
                return true;
            }
        }
        return false;
    }

    // 서버운영팀 API Key 인증
    private Mono<Void> handleInternalAuth(ServerWebExchange exchange,
                                           GatewayFilterChain chain,
                                           ServerHttpRequest request) {
        String apiKey = request.getHeaders().getFirst("X-Internal-Api-Key");
        if (apiKey == null || !apiKey.equals(internalApiKey)) {
            return onError(exchange, "내부 관리자 인증에 실패했습니다.", HttpStatus.UNAUTHORIZED);
        }
        // API Key 인증 통과 → JWT 없이 바로 하위 서비스로 전달
        return chain.filter(exchange);
    }

    private Mono<Void> onError(ServerWebExchange exchange, String message, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = "{\"error\": \"" + message + "\"}";
        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -1; // 최우선 실행
    }
}