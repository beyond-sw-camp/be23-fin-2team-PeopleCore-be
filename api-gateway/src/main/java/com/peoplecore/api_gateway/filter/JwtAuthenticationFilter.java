package com.peoplecore.api_gateway.filter;

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

    private Key accessKey;

    private static final List<String> EXCLUDE_PATHS = List.of(
            "/hr-service/auth/login",
            "/hr-service/auth/refresh",
            "/hr-service/auth/password"
    );
//  hr담담자만 추가 접근 가능 경로
    private static final List<String>HR_ONLY_PATHS =List.of(
            "/hr-service/employee",
            "/hr-service/resign"
    );


    @PostConstruct
    public void init() {
        byte[] keyBytes = Base64.getDecoder().decode(secretKey);
        this.accessKey = Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 인증 제외 경로
        if (isExcludedPath(path)) {
            return chain.filter(exchange);
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
        ServerHttpRequest mutatedRequest = request.mutate()
                .header("X-User-Id", claims.getSubject())
                .header("X-User-Company", claims.get("companyId", String.class))
                .header("X-User-Name", claims.get("name", String.class))
                .header("X-User-Role", claims.get("role", String.class))
                .header("X-User-Department", String.valueOf(claims.get("departmentId")))
                .header("X-User-Grade", String.valueOf(claims.get("gradeId")))
                .header("X-User-Title", String.valueOf(claims.get("titleId")))
                .build();

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