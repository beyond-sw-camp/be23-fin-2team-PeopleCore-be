package com.peoplecore.auth.service;

import com.peoplecore.auth.dto.FaceExtractRequest;
import com.peoplecore.auth.dto.FaceExtractResponse;
import com.peoplecore.auth.dto.FaceHealthResponse;
import com.peoplecore.auth.dto.FaceRecognizeResponse;
import com.peoplecore.auth.dto.FaceRegisterResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FaceRecognitionClient {

    private final WebClient faceApiWebClient;

    public FaceHealthResponse healthCheck() {
        return faceApiWebClient.get()
                .uri("/health")
                .retrieve()
                .bodyToMono(FaceHealthResponse.class)
                .block();
    }

    public FaceExtractResponse extractEmbedding(FaceExtractRequest request) {
        return faceApiWebClient.post()
                .uri("/extract")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {}).flatMap(body -> {
                            String detail = body.getOrDefault("detail", "얼굴 인식에 실패했습니다.").toString();
                            return Mono.error(new IllegalArgumentException(detail));
                        })
                )
                .bodyToMono(FaceExtractResponse.class)
                .block();
    }

    public FaceRegisterResponse registerFace(String image, Long empId, String empName, UUID companyId) {
        return faceApiWebClient.post()
                .uri("/register")
                .bodyValue(Map.of(
                        "image", image,
                        "emp_id", empId,
                        "emp_name", empName,
                        "company_id", companyId.toString()
                ))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {}).flatMap(body -> {
                            String detail = body.getOrDefault("detail", "얼굴 등록에 실패했습니다.").toString();
                            return Mono.error(new IllegalArgumentException(detail));
                        })
                )
                .bodyToMono(FaceRegisterResponse.class)
                .block();
    }

    public void unregisterFace(Long empId, UUID companyId) {
        faceApiWebClient.delete()
                .uri("/unregister/{companyId}/{empId}", companyId.toString(), empId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {}).flatMap(body -> {
                            String detail = body.getOrDefault("detail", "얼굴 삭제에 실패했습니다.").toString();
                            return Mono.error(new IllegalArgumentException(detail));
                        })
                )
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }

    public void unregisterCompanyFaces(UUID companyId) {
        faceApiWebClient.delete()
                .uri("/unregister/company/{companyId}", companyId.toString())
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {}).flatMap(body -> {
                            String detail = body.getOrDefault("detail", "회사 얼굴 일괄 삭제에 실패했습니다.").toString();
                            return Mono.error(new IllegalArgumentException(detail));
                        })
                )
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }

    public FaceRecognizeResponse recognizeFace(String base64Image, UUID companyId) {
        return faceApiWebClient.post()
                .uri("/recognize")
                .bodyValue(Map.of(
                        "image", base64Image,
                        "company_id", companyId.toString()
                ))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {}).flatMap(body -> {
                            String detail = body.getOrDefault("detail", "얼굴 인식에 실패했습니다.").toString();
                            return Mono.error(new IllegalArgumentException(detail));
                        })
                )
                .bodyToMono(FaceRecognizeResponse.class)
                .block();
    }
}
