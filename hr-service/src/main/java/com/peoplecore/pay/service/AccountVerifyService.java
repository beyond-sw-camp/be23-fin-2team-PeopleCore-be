package com.peoplecore.pay.service;

import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.dtos.AccountVerifyReqDto;
import com.peoplecore.pay.dtos.AccountVerifyResDto;
import com.peoplecore.pay.repository.OpenBankingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class AccountVerifyService {

    private final OpenBankingProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String TOKEN_KEY_PREFIX = "verify:account:";
    private static final Duration TOKEN_TTL = Duration.ofMinutes(5);

    public AccountVerifyService(
            OpenBankingProperties properties,
            @Qualifier("accountVerifyRedisTemplate") StringRedisTemplate redisTemplate) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }


    // 검증 + 토큰 발급. 검증 실패 시 ACCOUNT_VERIFY_FAILED 던짐.
    public AccountVerifyResDto verifyAndIssueToken(AccountVerifyReqDto req) {

        String holderName;
        try {
            String accessToken = getAccessToken();
            holderName = callRealNameInquiry(accessToken, req);
        } catch (RestClientException e) {
            log.error("[AccountVerify] 오픈뱅킹 호출 실패", e);
            throw new CustomException(ErrorCode.ACCOUNT_VERIFY_FAILED);
        }

        // 입력한 예금주명과 응답 실명이 일치하는지 비교 (공백 제거 후 정확히 일치)
        if (holderName == null || !normalize(holderName).equals(normalize(req.getAccountHolder()))) {
            log.warn("[AccountVerify] 예금주 불일치 - 입력={}, 응답={}",
                    req.getAccountHolder(), holderName);
            throw new CustomException(ErrorCode.ACCOUNT_HOLDER_MISMATCH);
        }

        // 토큰 발급 + Redis 저장 (저장 단계에서 같은 입력값과 일치하는지 비교용)
        String token = UUID.randomUUID().toString();
        String value = req.getBankCode() + "|" + req.getAccountNumber() + "|" + req.getAccountHolder();
        redisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + token, value, TOKEN_TTL);

        return AccountVerifyResDto.builder()
                .verified(true)
                .holder(holderName)
                .verificationToken(token)
                .expiresIn(TOKEN_TTL.getSeconds())
                .build();
    }

    // 저장 단계에서 호출 — 토큰이 유효하고 입력값이 검증 시점과 동일한지 확인 후 토큰 삭제(1회용)
    // 일치하지 않으면 CustomException
    public void consumeToken(String token, String bankCode, String accountNumber, String accountHolder) {
        if (token == null || token.isBlank()) {
            throw new CustomException(ErrorCode.ACCOUNT_VERIFY_TOKEN_INVALID);
        }
        String key = TOKEN_KEY_PREFIX + token;
        String stored = redisTemplate.opsForValue().get(key);
        if (stored == null) {
            throw new CustomException(ErrorCode.ACCOUNT_VERIFY_TOKEN_EXPIRED);
        }
        String expected = bankCode + "|" + accountNumber + "|" + accountHolder;
        if (!stored.equals(expected)) {
            // 토큰 발급 후 사용자가 값을 바꿔치기한 케이스 → 폐기
            redisTemplate.delete(key);
            throw new CustomException(ErrorCode.ACCOUNT_VERIFY_TOKEN_MISMATCH);
        }
        redisTemplate.delete(key); // 1회용
    }



    private String getAccessToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(properties.getClientId(), properties.getClientSecret());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("scope", "oob");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                properties.getTokenUrl(), HttpMethod.POST, request, Map.class);

        Map<String, Object> result = response.getBody();
        if (result == null || result.get("access_token") == null) {
            log.error("[AccountVerify] 토큰 발급 실패 - response={}", result);
            throw new CustomException(ErrorCode.ACCOUNT_VERIFY_FAILED);
        }
        return result.get("access_token").toString();
    }

    // 오픈뱅킹 실명조회 → 응답된 예금주명 반환 (실패 시 예외)
    private String callRealNameInquiry(String accessToken, AccountVerifyReqDto req) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // bank_tran_id: 이용기관코드(10자리, 오픈뱅킹 발급) + 'U' + 9자리 일련번호
        // 테스트 환경에서는 발급받은 이용기관 코드 사용. 임시로 timestamp 기반.
        String tranId = properties.getClientId().substring(0, 10) + "U" +
                String.format("%09d", System.currentTimeMillis() % 1_000_000_000L);
        String tranDtime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("bank_tran_id", tranId);
        body.put("bank_code_std", req.getBankCode());
        body.put("account_num", req.getAccountNumber().replace("-", ""));
        body.put("account_seq",  "000");                  // 회차번호
        body.put("account_holder_info_type", " ");        // 공백 (실명번호 미사용)
        body.put("account_holder_info", " ");             // type과 짝, 미사용 시 공백
        body.put("tran_dtime", tranDtime);                // 누락되어 있던 부분

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                properties.getVerifyUrl(), HttpMethod.POST, request, Map.class);

        Map result = response.getBody();
        if (result == null) {
            throw new CustomException(ErrorCode.ACCOUNT_VERIFY_FAILED);
        }
        // rsp_code == "A0000" 이 정상
        String rspCode = String.valueOf(result.getOrDefault("rsp_code", ""));
        if (!"A0000".equals(rspCode)) {
            log.warn("[AccountVerify] 실명조회 실패 - rspCode={}, msg={}",
                    rspCode, result.get("rsp_message"));
            throw new CustomException(ErrorCode.ACCOUNT_VERIFY_FAILED);
        }
        return String.valueOf(result.getOrDefault("account_holder_name", ""));
    }

    private String normalize(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "");
    }
}
