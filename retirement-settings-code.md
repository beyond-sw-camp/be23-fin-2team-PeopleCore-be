# 퇴직연금설정 백엔드 코드

> SuperAdmin 전용 — 회사별 퇴직연금 제도 설정 (퇴직금 / DB / DC / DB+DC)

---

## API 목록

| # | Method | URL | 설명 |
|---|--------|-----|------|
| 1 | GET | `/pay/superadmin/retirement` | 퇴직연금설정 조회 |
| 2 | PUT | `/pay/superadmin/retirement` | 퇴직연금설정 저장/수정 |

---

## 파일 체크리스트

| # | 구분 | 파일명 | 작업 |
|---|------|--------|------|
| 1 | Entity | `RetirementSettings.java` | update 메서드 추가 |
| 2 | Enum | `PensionType.java` | 변경 없음 |
| 3 | Repository | `RetirementSettingsRepository.java` | 신규 |
| 4 | DTO | `RetirementSettingsReqDto.java` | 신규 |
| 5 | DTO | `RetirementSettingsResDto.java` | 신규 |
| 6 | Service | `RetirementSettingsService.java` | 신규 |
| 7 | Controller | `RetirementSettingsController.java` | 신규 |
| 8 | ErrorCode | `ErrorCode.java` | 1개 추가 |

---

## 1. Entity 수정

### RetirementSettings.java (수정)
**파일 위치**: `pay/domain/RetirementSettings.java`

> update 메서드 추가. DB형/DB_DC형이 아닌 경우 운용사·계좌 null 처리

```java
package com.peoplecore.pay.domain;

import com.peoplecore.company.domain.Company;
import com.peoplecore.pay.enums.PensionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "retirement_settings",
    indexes = {
        @Index(name = "idx_retirement_company", columnList = "company_id")
    })
public class RetirementSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long retirementSettingsId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PensionType pensionType;

    // 퇴직연금 운용사 (DB형, DB_DC형)
    @Column(length = 100)
    private String pensionProvider;

    // 퇴직연금 계좌번호 (DB형, DB_DC형)
    @Column(length = 100)
    private String pensionAccount;

    public void update(PensionType pensionType, String pensionProvider, String pensionAccount) {
        this.pensionType = pensionType;

        // DB형 또는 DB_DC형일 때만 운용 정보 저장
        if (pensionType == PensionType.DB || pensionType == PensionType.DB_DC) {
            this.pensionProvider = pensionProvider;
            this.pensionAccount = pensionAccount;
        } else {
            this.pensionProvider = null;
            this.pensionAccount = null;
        }
    }
}
```

---

## 2. Repository

### RetirementSettingsRepository.java (신규)
**파일 위치**: `pay/repository/RetirementSettingsRepository.java`

```java
package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.RetirementSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RetirementSettingsRepository extends JpaRepository<RetirementSettings, Long> {

    Optional<RetirementSettings> findByCompany_CompanyId(UUID companyId);
}
```

---

## 3. DTO

### RetirementSettingsReqDto.java (신규)
**파일 위치**: `pay/dtos/RetirementSettingsReqDto.java`

```java
package com.peoplecore.pay.dtos;

import com.peoplecore.pay.enums.PensionType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetirementSettingsReqDto {

    @NotNull(message = "퇴직연금 제도를 선택해주세요.")
    private PensionType pensionType;

    // DB형, DB_DC형일 때만 입력
    private String pensionProvider;
    private String pensionAccount;
}
```

---

### RetirementSettingsResDto.java (신규)
**파일 위치**: `pay/dtos/RetirementSettingsResDto.java`

```java
package com.peoplecore.pay.dtos;

import com.peoplecore.pay.domain.RetirementSettings;
import com.peoplecore.pay.enums.PensionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetirementSettingsResDto {

    private Long retirementSettingsId;
    private PensionType pensionType;
    private String pensionProvider;
    private String pensionAccount;

    public static RetirementSettingsResDto fromEntity(RetirementSettings s) {
        return RetirementSettingsResDto.builder()
                .retirementSettingsId(s.getRetirementSettingsId())
                .pensionType(s.getPensionType())
                .pensionProvider(s.getPensionProvider())
                .pensionAccount(s.getPensionAccount())
                .build();
    }
}
```

---

## 4. Service

### RetirementSettingsService.java (신규)
**파일 위치**: `pay/service/RetirementSettingsService.java`

```java
package com.peoplecore.pay.service;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.RetirementSettings;
import com.peoplecore.pay.dtos.RetirementSettingsReqDto;
import com.peoplecore.pay.dtos.RetirementSettingsResDto;
import com.peoplecore.pay.enums.PensionType;
import com.peoplecore.pay.repository.RetirementSettingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class RetirementSettingsService {

    private final RetirementSettingsRepository retirementSettingsRepository;
    private final CompanyRepository companyRepository;

    @Autowired
    public RetirementSettingsService(RetirementSettingsRepository retirementSettingsRepository,
                                     CompanyRepository companyRepository) {
        this.retirementSettingsRepository = retirementSettingsRepository;
        this.companyRepository = companyRepository;
    }


    // ── 퇴직연금설정 조회 ──
    public RetirementSettingsResDto getRetirementSettings(UUID companyId) {
        RetirementSettings settings = retirementSettingsRepository
                .findByCompany_CompanyId(companyId)
                .orElse(null);

        // 설정이 없으면 기본값(퇴직금) 반환
        if (settings == null) {
            return RetirementSettingsResDto.builder()
                    .pensionType(PensionType.severance)
                    .build();
        }

        return RetirementSettingsResDto.fromEntity(settings);
    }


    // ── 퇴직연금설정 저장/수정 ──
    @Transactional
    public RetirementSettingsResDto saveRetirementSettings(UUID companyId, RetirementSettingsReqDto reqDto) {

        // DB형 또는 DB_DC형일 때 운용사 필수 검증
        if (reqDto.getPensionType() == PensionType.DB || reqDto.getPensionType() == PensionType.DB_DC) {
            if (reqDto.getPensionProvider() == null || reqDto.getPensionProvider().isBlank()) {
                throw new CustomException(ErrorCode.RETIREMENT_PROVIDER_REQUIRED);
            }
        }

        RetirementSettings settings = retirementSettingsRepository
                .findByCompany_CompanyId(companyId)
                .orElse(null);

        // 최초 설정 → 신규 생성
        if (settings == null) {
            Company company = companyRepository.findById(companyId)
                    .orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND));

            settings = RetirementSettings.builder()
                    .company(company)
                    .pensionType(reqDto.getPensionType())
                    .pensionProvider(needsProviderInfo(reqDto.getPensionType()) ? reqDto.getPensionProvider() : null)
                    .pensionAccount(needsProviderInfo(reqDto.getPensionType()) ? reqDto.getPensionAccount() : null)
                    .build();

            retirementSettingsRepository.save(settings);
        } else {
            // 기존 설정 → 수정
            settings.update(reqDto.getPensionType(), reqDto.getPensionProvider(), reqDto.getPensionAccount());
        }

        return RetirementSettingsResDto.fromEntity(settings);
    }


    private boolean needsProviderInfo(PensionType type) {
        return type == PensionType.DB || type == PensionType.DB_DC;
    }
}
```

---

## 5. Controller

### RetirementSettingsController.java (신규)
**파일 위치**: `pay/controller/RetirementSettingsController.java`

```java
package com.peoplecore.pay.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.pay.dtos.RetirementSettingsReqDto;
import com.peoplecore.pay.dtos.RetirementSettingsResDto;
import com.peoplecore.pay.service.RetirementSettingsService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/pay/superadmin/retirement")
@RoleRequired({"HR_SUPER_ADMIN"})
public class RetirementSettingsController {

    private final RetirementSettingsService retirementSettingsService;

    @Autowired
    public RetirementSettingsController(RetirementSettingsService retirementSettingsService) {
        this.retirementSettingsService = retirementSettingsService;
    }


    //    퇴직연금설정 조회
    @GetMapping
    public ResponseEntity<RetirementSettingsResDto> getRetirementSettings(
            @RequestHeader("X-User-Company") UUID companyId) {
        return ResponseEntity.ok(retirementSettingsService.getRetirementSettings(companyId));
    }

    //    퇴직연금설정 저장/수정
    @PutMapping
    public ResponseEntity<RetirementSettingsResDto> saveRetirementSettings(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestBody @Valid RetirementSettingsReqDto reqDto) {
        return ResponseEntity.ok(retirementSettingsService.saveRetirementSettings(companyId, reqDto));
    }
}
```

---

## 6. ErrorCode 추가

**파일 위치**: `common/.../exception/ErrorCode.java`

```java
// 퇴직연금설정
RETIREMENT_PROVIDER_REQUIRED(400, "DB형/DB+DC형은 퇴직연금 운용사를 입력해주세요."),
```

---

## 프론트 연동 참고

### 조회 (GET)
```
GET /pay/superadmin/retirement
Headers: X-User-Company: {companyId}

Response:
{
  "retirementSettingsId": 1,
  "pensionType": "DB",
  "pensionProvider": "국민은행",
  "pensionAccount": "123-45-6789-012"
}
```

- `pensionType`에 따라 라디오 버튼 선택
- `DB` 또는 `DB_DC`이면 운용 정보 영역 표시
- 설정이 없는 경우 `pensionType: "severance"`, 나머지 null 반환

### 저장 (PUT)

**퇴직금 선택 시:**
```json
{
  "pensionType": "severance"
}
```

**DB형 선택 시:**
```json
{
  "pensionType": "DB",
  "pensionProvider": "국민은행",
  "pensionAccount": "123-45-6789-012"
}
```

**DC형 선택 시:**
```json
{
  "pensionType": "DC"
}
```

**DB+DC형 선택 시:**
```json
{
  "pensionType": "DB_DC",
  "pensionProvider": "국민은행",
  "pensionAccount": "123-45-6789-012"
}
```

> DC형/퇴직금 선택 시 `pensionProvider`, `pensionAccount`를 보내도 서버에서 null 처리됨
