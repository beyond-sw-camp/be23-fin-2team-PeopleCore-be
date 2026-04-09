package com.peoplecore.company.service;

import com.peoplecore.company.config.CollaborationApiConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
public class CollaborationClient {

    private final RestClient restClient;

    @Autowired
    public CollaborationClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl("http://collaboration-service").build();
    }


    public void initDefaultFormFolder(UUID companyId) {
      restClient
              .post() //post요청
              .uri("/approval/init/formfolder") // 요청보낼 api 엔드포인트
              .header("X-User-Company",companyId.toString()) // 커스텀헤더 추가
              .retrieve() // Http 요청을 실행하고 응답을 받을 준비 
              .toBodilessEntity(); // 응답을 바디를 안 읽고 성공/실패 유무만 반환함
    }
}
