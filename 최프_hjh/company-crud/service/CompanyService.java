package com.peoplecore.service;

import com.peoplecore.dto.*;
import com.peoplecore.entity.Company;
import com.peoplecore.enums.CompanyStatus;
import com.peoplecore.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final AdminAccountService adminAccountService; // 최고관리자 계정 생성 담당
    // private final EmailService emailService;            // 이메일 발송 담당 (별도 구현)

    // ════════════════════════════════════════════════════
    // 1. 회사 등록 (기본정보 + 최고관리자 계정 생성)
    // POST /internal/companies
    // ════════════════════════════════════════════════════

    @Transactional
    public CompanyCreateResponse createCompany(CompanyCreateRequest request) {
        // 1) 회사 기본정보 저장 (UUID 자동 발급, 상태: PENDING)
        Company company = Company.builder()
                .companyName(request.getCompanyName())
                .foundingDate(request.getFoundingDate())
                .ipAddress(request.getIpAddress())
                .contractStartDate(request.getContractStartDate())
                .contractEndDate(request.getContractEndDate())
                .contractType(request.getContractType())
                .maxEmployees(request.getMaxEmployees())
                .contactName(request.getContactName())
                .contactEmail(request.getContactEmail())
                .contactPhone(request.getContactPhone())
                .build();

        Company saved = companyRepository.save(company);
        log.info("회사 등록 완료: companyId={}, name={}", saved.getCompanyId(), saved.getCompanyName());

        // 2) 최고관리자 계정 생성 (ADMIN 권한, 임시 비밀번호 발급)
        AdminAccountResponse adminResponse = adminAccountService.createAdminAccount(
                saved.getCompanyId(),
                request.getAdminName(),
                request.getAdminEmail()
        );

        // 3) 이메일 발송: 회사 UUID + 관리자 계정 정보 전달
        //    emailService.sendAdminCredentials(adminResponse);

        // 4) 회사 상태를 ACTIVE로 변경
        saved.changeStatus(CompanyStatus.ACTIVE);

        log.info("회사 활성화 완료: companyId={}", saved.getCompanyId());

        return CompanyCreateResponse.builder()
                .company(CompanyResponse.from(saved))
                .admin(adminResponse)
                .build();
    }

    // ════════════════════════════════════════════════════
    // 2. 회사 단건 조회
    // GET /internal/companies/{companyId}
    // ════════════════════════════════════════════════════

    public CompanyResponse getCompany(UUID companyId) {
        Company company = findCompanyOrThrow(companyId);
        return CompanyResponse.from(company);
    }

    // ════════════════════════════════════════════════════
    // 3. 회사 목록 조회 (전체 or 상태별)
    // GET /internal/companies?status=ACTIVE
    // ════════════════════════════════════════════════════

    public List<CompanyResponse> getCompanies(CompanyStatus status) {
        List<Company> companies;
        if (status != null) {
            companies = companyRepository.findByStatus(status);
        } else {
            companies = companyRepository.findAll();
        }
        return companies.stream()
                .map(CompanyResponse::from)
                .collect(Collectors.toList());
    }

    // ════════════════════════════════════════════════════
    // 4. 계약 상태 변경
    // PATCH /internal/companies/{companyId}/status
    //
    // 상태 전이 규칙:
    //   PENDING → ACTIVE (계약 확정)
    //   ACTIVE → SUSPENDED (관리자 수동 중지)
    //   ACTIVE → EXPIRED (수동 만료 처리)
    //   SUSPENDED → ACTIVE (재활성화)
    //   EXPIRED → ACTIVE (연장 시에는 extend 사용)
    //
    // 상태별 서비스 접근 제한:
    //   ACTIVE: 정상 이용
    //   PENDING: 로그인 차단 + "계약 확정 대기 중" 메시지
    //   SUSPENDED: 로그인 차단 + "서비스 일시 중지" 메시지
    //   EXPIRED: 로그인 차단 + "계약 만료" 메시지
    // ════════════════════════════════════════════════════

    @Transactional
    public CompanyResponse updateStatus(UUID companyId, CompanyStatusUpdateRequest request) {
        Company company = findCompanyOrThrow(companyId);

        validateStatusTransition(company.getStatus(), request.getStatus());

        company.changeStatus(request.getStatus());
        log.info("회사 상태 변경: companyId={}, {} → {}, 사유: {}",
                companyId, company.getStatus(), request.getStatus(), request.getReason());

        return CompanyResponse.from(company);
    }

    // ════════════════════════════════════════════════════
    // 5. 계약 연장
    // PATCH /internal/companies/{companyId}/contract/extend
    //
    // 구두/메일 계약 확정 후 처리:
    //   - 만료일 재설정
    //   - max_employees, 계약유형 변경 (선택)
    //   - EXPIRED → ACTIVE 자동 복구
    //   - 알림 초기화
    // ════════════════════════════════════════════════════

    @Transactional
    public CompanyResponse extendContract(UUID companyId, ContractExtendRequest request) {
        Company company = findCompanyOrThrow(companyId);

        company.extendContract(
                request.getNewContractEndDate(),
                request.getMaxEmployees(),
                request.getContractType()
        );

        log.info("계약 연장 완료: companyId={}, 새 만료일={}", companyId, request.getNewContractEndDate());

        return CompanyResponse.from(company);
    }

    // ════════════════════════════════════════════════════
    // Private helpers
    // ════════════════════════════════════════════════════

    private Company findCompanyOrThrow(UUID companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "회사를 찾을 수 없습니다: " + companyId));
    }

    /**
     * 상태 전이 유효성 검증
     */
    private void validateStatusTransition(CompanyStatus current, CompanyStatus next) {
        boolean valid = switch (current) {
            case PENDING -> next == CompanyStatus.ACTIVE;
            case ACTIVE -> next == CompanyStatus.SUSPENDED || next == CompanyStatus.EXPIRED;
            case SUSPENDED -> next == CompanyStatus.ACTIVE;
            case EXPIRED -> next == CompanyStatus.ACTIVE; // 연장 시
        };

        if (!valid) {
            throw new IllegalStateException(
                    String.format("잘못된 상태 전이: %s → %s", current, next));
        }
    }
}
