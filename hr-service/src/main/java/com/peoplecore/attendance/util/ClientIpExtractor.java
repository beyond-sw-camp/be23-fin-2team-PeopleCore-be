package com.peoplecore.attendance.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/* 클라이언트 실 IP 추출 컴포넌트
 * 출퇴근 체크인/아웃 IP 정책 검증, 허용 IP 등록 모달의 "내 현재 IP" 표시에서 공통 사용
 * 두 호출처가 동일한 IP 를 보도록 단일 진입점 유지 → 등록한 IP 가 매칭에서 누락되는 사고 방지
 *
 * TODO: [배포 시 인프라 점검 필수 — 누락 시 전사 출퇴근 차단 사고 가능]
 *   NGINX Ingress + AWS NLB 환경에서 클라이언트 IP 보존 설정이 빠지면
 *   hr-service 가 받는 IP 는 사설 IP(10.x.x.x)만 보임 → 등록한 회사 외부 공인 IP CIDR 매칭 실패.
 *
 *   필수 설정 1) ingress-nginx-controller Service spec.externalTrafficPolicy: Local
 *               + NLB target-type: instance (또는 target-type: ip + preserve_client_ip.enabled=true)
 *   필수 설정 2) ingress-nginx-controller ConfigMap
 *               - use-forwarded-headers: "true"
 *               - compute-full-forwarded-for: "true"
 *   권장 설정 3) api-gateway application.yml
 *               - spring.cloud.gateway.x-forwarded.enabled: true
 *
 *   배포 직후 [checkIn-DEBUG] 로그의 clientIp 가 회사 외부 공인 IP 로 찍히는지 반드시 확인. */
@Component
public class ClientIpExtractor {

    /* X-Forwarded-For 토큰 순회 → loopback(::1, 127.0.0.1) 건너뛰고 첫 유효 토큰 채택
     * XFF 가 비었거나 모든 토큰이 loopback 이면 getRemoteAddr() 폴백
     * IPv4-매핑 IPv6(::ffff:1.2.3.4) → IPv4 정규화 → IPv4 CIDR 매칭과 호환 */
    public String extract(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            for (String token : xff.split(",")) {
                String normalized = normalizeIp(token.trim());
                if (normalized != null && !isLoopback(normalized)) {
                    return normalized;
                }
            }
        }
        return normalizeIp(request.getRemoteAddr());
    }

    /* IPv4-매핑 IPv6(::ffff:x.x.x.x) → IPv4 정규화. 그 외 형식은 원본 유지 */
    private String normalizeIp(String ip) {
        if (ip == null || ip.isBlank()) return null;
        String trimmed = ip.trim();
        if (trimmed.startsWith("::ffff:")) return trimmed.substring(7);
        return trimmed;
    }

    /* IPv6/IPv4 loopback 표현 매치 */
    private boolean isLoopback(String ip) {
        return "127.0.0.1".equals(ip) || "::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip);
    }
}
