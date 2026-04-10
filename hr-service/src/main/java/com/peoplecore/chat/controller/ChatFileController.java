package com.peoplecore.chat.controller;

import com.peoplecore.chat.service.ChatFileService;
import io.minio.StatObjectResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ChatFileController {

    private final ChatFileService chatFileService;

    @GetMapping("/chat/files/**")
    public ResponseEntity<?> downloadFile(HttpServletRequest request) {
        String uri = request.getRequestURI();
        log.info("[ChatFile] 요청 URI: {}", uri);

        // /chat/files/ 이후 경로 추출
        String prefix = "/chat/files/";
        int idx = uri.indexOf(prefix);
        if (idx == -1) {
            log.error("[ChatFile] 경로 파싱 실패: {}", uri);
            return ResponseEntity.badRequest().build();
        }

        String objectName = uri.substring(idx + prefix.length());
        // URL 인코딩된 한글 파일명 디코딩
        objectName = URLDecoder.decode(objectName, StandardCharsets.UTF_8);
        log.info("[ChatFile] objectName: {}", objectName);

        try {
            StatObjectResponse stat = chatFileService.getFileStat(objectName);
            InputStream stream = chatFileService.downloadFile(objectName);

            String contentType = stat.contentType();
            String fileName = objectName;
            // room-1/uuid_파일명.jpg → 파일명.jpg
            if (objectName.contains("/")) {
                fileName = objectName.substring(objectName.lastIndexOf("/") + 1);
            }
            if (fileName.contains("_")) {
                fileName = fileName.substring(fileName.indexOf("_") + 1);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.setContentLength(stat.size());

            // 미리보기 가능한 타입은 inline, 나머지는 다운로드
            boolean canInline = contentType != null && (
                    contentType.startsWith("image/") ||
                    contentType.equals("application/pdf") ||
                    contentType.startsWith("text/")
            );

            if (canInline) {
                headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline");
            } else {
                String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
                headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName);
            }

            return ResponseEntity.ok().headers(headers).body(new InputStreamResource(stream));

        } catch (Exception e) {
            log.error("[ChatFile] 파일 조회 실패: objectName={}, error={}", objectName, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("{\"error\": \"파일이 만료되었거나 존재하지 않습니다.\"}");
        }
    }
}
