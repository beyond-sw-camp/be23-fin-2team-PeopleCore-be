package com.peoplecore.auth.jwt;

import com.peoplecore.employee.domain.Employee;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtProvider {

    @Value("${jwt.secretKey}")
    private String secretKey;

    @Value("${jwt.secretKeyRt}")
    private String secretKeyRt;

    @Value("${jwt.expirationAt}")
    private long expirationAt;

    @Value("${jwt.expirationRt}")
    private long expirationRt;

    private Key accessKey;
    private Key refreshKey;

    @PostConstruct
    public void init() {
        byte[] accessKeyBytes = Base64.getDecoder().decode(secretKey);
        byte[] refreshKeyBytes = Base64.getDecoder().decode(secretKeyRt);
        this.accessKey = Keys.hmacShaKeyFor(accessKeyBytes);
        this.refreshKey = Keys.hmacShaKeyFor(refreshKeyBytes);
    }

    public String createAccessToken(Employee employee) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationAt);

        return Jwts.builder()
                .setSubject(String.valueOf(employee.getEmpId()))
                .claim("companyId", employee.getCompany().getCompanyId())
                .claim("name", employee.getEmpName())
                .claim("role", employee.getEmpRole().name())
                .claim("departmentId", employee.getDept().getDeptId())
                .claim("gradeId", employee.getGrade().getGradeId())
                .claim("titleId", employee.getTitle() != null ? employee.getTitle().getTitleId() : null)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(accessKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /** 인사통합 PIN 검증 후 발급하는 단기 스코프 토큰. ttlSeconds 만큼 유효. */
    public String createHrAdminScopeToken(Long empId, long ttlSeconds) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + ttlSeconds * 1000L);

        return Jwts.builder()
                .setSubject(String.valueOf(empId))
                .claim("scope", "hr-admin")
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(accessKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String createRefreshToken(Employee employee) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationRt);

        return Jwts.builder()
                .setSubject(String.valueOf(employee.getEmpId()))
                .claim("tokenType", "REFRESH")
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(refreshKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseAccessToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(accessKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public Claims parseRefreshToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(refreshKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean validateAccessToken(String token) {
        try {
            parseAccessToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean validateRefreshToken(String token) {
        try {
            parseRefreshToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}