//package com.peoplecore.pay.service;
//
//import com.peoplecore.pay.dtos.VerifyRequest;
//import com.peoplecore.pay.dtos.VerifyResponse;
//import com.peoplecore.pay.repository.OpenBankingProperties;
//import lombok.RequiredArgsConstructor;
//import org.springframework.http.*;
//import org.springframework.stereotype.Service;
//import org.springframework.util.LinkedMultiValueMap;
//import org.springframework.util.MultiValueMap;
//import org.springframework.web.client.RestTemplate;
//
//import java.util.Map;
//
//@Service
//@RequiredArgsConstructor
//public class OpenBankingService {
//
//    private final OpenBankingProperties properties;
//    private final RestTemplate restTemplate = new RestTemplate();
//
//    public String getAccessToken() {
//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
//
//        headers.setBasicAuth(
//                properties.getClientId(),
//                properties.getClientSecret()
//        );
//
//        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
//        body.add("grant_type", "client_credentials");
//        body.add("scope", "oob");
//
//        System.out.println("clientId = [" + properties.getClientId() + "]");
//        System.out.println("clientSecret = [" + properties.getClientSecret() + "]");
//        System.out.println("body = " + body);
//
//        HttpEntity<MultiValueMap<String, String>> request =
//                new HttpEntity<>(body, headers);
//
//        ResponseEntity<Map> response = restTemplate.exchange(
//                properties.getTokenUrl(),
//                HttpMethod.POST,
//                request,
//                Map.class
//        );
//
//        System.out.println("==== 토큰 응답 ====");
//        System.out.println(response.getBody());
//
//        Map<String, Object> result = response.getBody();
//
//        if (result == null || result.get("access_token") == null) {
//            throw new RuntimeException("토큰 발급 실패: " + result);
//        }
//
//        return response.getBody().get("access_token").toString();
//    }
//
//    public VerifyResponse verifyAccount(VerifyRequest dto) {
//        String token = getAccessToken();
//
//        HttpHeaders headers = new HttpHeaders();
//        headers.setBearerAuth(token);
//        headers.setContentType(MediaType.APPLICATION_JSON);
//
//        Map<String, Object> body = Map.of(
//                "bank_code_std", dto.getBankCode(),
//                "account_num", dto.getAccountNo(),
//                "account_holder_info", dto.getAccountHolderInfo(),
//                "tran_dtime", java.time.LocalDateTime.now()
//                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
//        );
//
//        HttpEntity<Map<String, Object>> request =
//                new HttpEntity<>(body, headers);
//
//        ResponseEntity<Map> response = restTemplate.exchange(
//                properties.getVerifyUrl(),
//                HttpMethod.POST,
//                request,
//                Map.class
//        );
//
//        Map result = response.getBody();
//
//        String name = result.getOrDefault("account_holder_name", "").toString();
//        String check = result.getOrDefault("account_holder_name_check", "N").toString();
//
//        if ("Y".equals(check)) {
//            return new VerifyResponse(true, "실명 확인 성공", name);
//        }
//
//        return new VerifyResponse(false, "실명 불일치", name);
//    }
//}