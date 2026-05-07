package com.peoplecore.attendance.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/* 클라이언트 실 IP 추출 컴포넌트
 * 출퇴근 체크인/아웃 IP 정책 검증, 허용 IP 등록 모달의 "내 현재 IP" 표시에서 공통 사용
 * 두 호출처가 동일한 IP 를 보도록 단일 진입점 유지 → 등록한 IP 가 매칭에서 누락되는 사고 방지
 *
 * 인프라 의존 (검증 완료: 2026-05-07)
 *   적용 위치: ingress-nginx-controller ConfigMap
 *     kubectl -n ingress-nginx edit configmap ingress-nginx-controller
 *     - use-forwarded-headers: "true"        → XFF 헤더 신뢰
 *     - compute-full-forwarded-for: "true"   → XFF 에 직전 remote_addr 누적
 *     - proxy-real-ip-cidr: "0.0.0.0/0"      → 신뢰 대역 (NLB 외 외부 도달 경로 없어 운영상 안전)
 *
 *   추가 설정이 불필요했던 이유
 *     - NLB 단계: 기존 ingress-nginx 설치 구성만으로 클라이언트 공인 IP 가 NGINX remote_addr 까지 보존됨
 *       (externalTrafficPolicy / target-type 별도 작업 불필요)
 *     - api-gateway 단계: Spring Cloud Gateway 의 x-forwarded.enabled 기본값이 true 라
 *       명시적 설정 없이도 XFF 가 hr-service 까지 누적 전달됨 (검증 중 추가했던 설정은 롤백)
 *
 *   검증 방법: GET /hr-service/company/allowed-ips/my-ip 응답 IP 가 사용자 공인 IP 와 일치
 *            (whatismyipaddress.com 비교) */
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
