package com.peoplecore.auth.service;

import com.peoplecore.auth.dto.FaceExtractRequest;
import com.peoplecore.auth.dto.FaceExtractResponse;
import com.peoplecore.auth.dto.FaceHealthResponse;
import com.peoplecore.auth.dto.FaceRecognizeResponse;
import com.peoplecore.auth.dto.FaceRegisterResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

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
                        response.bodyToMono(Map.class).flatMap(body -> {
                            String detail = body.getOrDefault("detail", "얼굴 인식에 실패했습니다.").toString();
                            return Mono.error(new IllegalArgumentException(detail));
                        })
                )
                .bodyToMono(FaceExtractResponse.class)
                .block();
    }

    public FaceRegisterResponse registerFace(String image, Long empId, String empName) {
        return faceApiWebClient.post()
                .uri("/register")
                .bodyValue(Map.of(
                        "image", image,
                        "emp_id", empId,
                        "emp_name", empName
                ))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(Map.class).flatMap(body -> {
                            String detail = body.getOrDefault("detail", "얼굴 등록에 실패했습니다.").toString();
                            return Mono.error(new IllegalArgumentException(detail));
                        })
                )
                .bodyToMono(FaceRegisterResponse.class)
                .block();
    }

    public void unregisterFace(Long empId) {
        faceApiWebClient.delete()
                .uri("/unregister/{empId}", empId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(Map.class).flatMap(body -> {
                            String detail = body.getOrDefault("detail", "얼굴 삭제에 실패했습니다.").toString();
                            return Mono.error(new IllegalArgumentException(detail));
                        })
                )
                .bodyToMono(Map.class)
                .block();
    }

    public FaceRecognizeResponse recognizeFace(String base64Image) {
        return faceApiWebClient.post()
                .uri("/recognize")
                .bodyValue(Map.of("image", base64Image))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(Map.class).flatMap(body -> {
                            String detail = body.getOrDefault("detail", "얼굴 인식에 실패했습니다.").toString();
                            return Mono.error(new IllegalArgumentException(detail));
                        })
                )
                .bodyToMono(FaceRecognizeResponse.class)
                .block();
    }
}
