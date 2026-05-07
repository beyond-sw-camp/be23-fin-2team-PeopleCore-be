package com.peoplecore.employee.controller;


import com.peoplecore.auth.RoleRequired;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.employee.service.ProfileImageService;
import com.peoplecore.pay.service.MySalaryCacheService;
import io.minio.StatObjectResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@RestController
@Slf4j
public class ProfileImageController{

    private final ProfileImageService profileImageService;
    private final EmployeeRepository employeeRepository;
    private final MySalaryCacheService mySalaryCacheService;

    @Autowired
    public ProfileImageController(ProfileImageService profileImageService, EmployeeRepository employeeRepository, MySalaryCacheService mySalaryCacheService) {
        this.profileImageService = profileImageService;
        this.employeeRepository = employeeRepository;
        this.mySalaryCacheService = mySalaryCacheService;
    }



    /** 내 프로필 이미지 업로드/교체 */
    @PostMapping(value = "/employee/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RoleRequired({"EMPLOYEE", "HR_ADMIN", "HR_SUPER_ADMIN"})
    @Transactional
    public ResponseEntity<Map<String, String>> upload(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestPart("file") MultipartFile file
    ) throws Exception {
        Employee emp = employeeRepository.findById(empId)
                .orElseThrow(() -> new IllegalArgumentException("사원을 찾을 수 없습니다"));

        // 기존 이미지 삭제 (best-effort)
        profileImageService.deleteByUrl(emp.getEmpProfileImageUrl());

        String newUrl = profileImageService.upload(empId, file);
        emp.updateProfileImage(newUrl);
        mySalaryCacheService.evictSalaryInfoCache(companyId, empId);

        return ResponseEntity.ok(Map.of("profileImageUrl", newUrl));
    }

    /** 내 프로필 이미지 제거 */
    @DeleteMapping("/employee/me/profile-image")
    @RoleRequired({"EMPLOYEE", "HR_ADMIN", "HR_SUPER_ADMIN"})
    @Transactional
    public ResponseEntity<Void> delete(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId) {
        Employee emp = employeeRepository.findById(empId)
                .orElseThrow(() -> new IllegalArgumentException("사원을 찾을 수 없습니다"));
        profileImageService.deleteByUrl(emp.getEmpProfileImageUrl());
        emp.updateProfileImage(null);
        mySalaryCacheService.evictSalaryInfoCache(companyId, empId);
        return ResponseEntity.noContent().build();
    }

    /** 프록시 GET — ChatFileController와 동일한 패턴 */
    @GetMapping("/employee/profile-images/**")
    public ResponseEntity<?> download(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String prefix = "/employee/profile-images/";
        int idx = uri.indexOf(prefix);
        if (idx == -1) return ResponseEntity.badRequest().build();
        String objectName = URLDecoder.decode(uri.substring(idx + prefix.length()), StandardCharsets.UTF_8);

        try {
            StatObjectResponse stat = profileImageService.stat(objectName);
            InputStream stream = profileImageService.download(objectName);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(stat.contentType()));
            headers.setContentLength(stat.size());
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline");
            // 브라우저 캐싱 — 새 이미지로 교체할 때 URL이 UUID로 바뀌므로 강하게 캐싱해도 안전
            headers.setCacheControl("public, max-age=86400");
            return ResponseEntity.ok().headers(headers).body(new InputStreamResource(stream));
        } catch (Exception e) {
            log.warn("[ProfileImage] 조회 실패: objectName={}, error={}", objectName, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}
