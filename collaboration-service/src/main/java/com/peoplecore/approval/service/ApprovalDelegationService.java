package com.peoplecore.approval.service;

import com.peoplecore.approval.dto.AdminDelegationCreateRequest;
import com.peoplecore.approval.dto.ApprovalDelegationCreateRequest;
import com.peoplecore.approval.dto.ApprovalDelegationResponse;
import com.peoplecore.approval.entity.ApprovalDelegation;
import com.peoplecore.approval.repository.ApprovalDelegationRepository;
import com.peoplecore.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@Transactional(readOnly = true)
public class ApprovalDelegationService {

    private final ApprovalDelegationRepository delegationRepository;

    @Autowired
    public ApprovalDelegationService(ApprovalDelegationRepository delegationRepository) {
        this.delegationRepository = delegationRepository;
    }

    /*내 위임 목록 조회 */
    public List<ApprovalDelegationResponse> getMyDelegations(UUID companyId, Long empId) {
        return delegationRepository.findByCompanyIdAndEmpIdOrderByCreatedAtDesc(companyId, empId).stream().map(ApprovalDelegationResponse::from).toList();
    }

    /*위임 등록 */
    @Transactional
    public Long create(UUID companyId, Long empId, String empName, ApprovalDelegationCreateRequest request) {
        boolean isDuplicated = delegationRepository.existsByCompanyIdAndEmpIdAndIsActiveTrueAndStartAtLessThanEqualAndEndAtGreaterThanEqual(companyId, empId, request.getAppDeleEndAt(), request.getAppDeleStartAt());

        if (isDuplicated) {
            throw new BusinessException("해당 기간에 이미 등록된 위임이 존재합니다. ", HttpStatus.CONFLICT);
        }

        ApprovalDelegation delegation = ApprovalDelegation.builder()
                .companyId(companyId)
                .empId(empId)
                .empName(empName)
                .empDeptName(request.getEmpDeptName())
                .empGrade(request.getEmpGrade())
                .deleEmpId(request.getAppDeleEmpId())
                .deleDeptName(request.getDeleDeptName())
                .deleGrade(request.getDeleGrade())
                .deleTitle(request.getDeleTitle())
                .startAt(request.getAppDeleStartAt())
                .endAt(request.getAppDeleEndAt())
                .reason(request.getAppDeleReason())
                .isActive(true)
                .build();

        return delegationRepository.save(delegation).getAppDeleId();
    }

    /*관리자 위임 등록 (대리 등록) */
    @Transactional
    public Long createByAdmin(UUID companyId, AdminDelegationCreateRequest request) {
        boolean isDuplicated = delegationRepository.existsByCompanyIdAndEmpIdAndIsActiveTrueAndStartAtLessThanEqualAndEndAtGreaterThanEqual(
                companyId, request.getEmpId(), request.getAppDeleEndAt(), request.getAppDeleStartAt());

        if (isDuplicated) {
            throw new BusinessException("해당 기간에 이미 등록된 위임이 존재합니다.", HttpStatus.CONFLICT);
        }

        ApprovalDelegation delegation = ApprovalDelegation.builder()
                .companyId(companyId)
                .empId(request.getEmpId())
                .empName(request.getEmpName())
                .empDeptName(request.getEmpDeptName())
                .empGrade(request.getEmpGrade())
                .empTitle(request.getEmpTitle())
                .deleEmpId(request.getAppDeleEmpId())
                .deleName(request.getDeleName())
                .deleDeptName(request.getDeleDeptName())
                .deleGrade(request.getDeleGrade())
                .deleTitle(request.getDeleTitle())
                .startAt(request.getAppDeleStartAt())
                .endAt(request.getAppDeleEndAt())
                .reason(request.getAppDeleReason())
                .isActive(true)
                .build();

        return delegationRepository.save(delegation).getAppDeleId();
    }

    /*관리자 위임 목록 전체 조회 */
    public List<ApprovalDelegationResponse> getAllDelegations(UUID companyId) {
        return delegationRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)
                .stream().map(ApprovalDelegationResponse::from).toList();
    }

    /*관리자 위임 삭제 */
    @Transactional
    public void deleteByAdmin(UUID companyId, Long delegationId) {
        ApprovalDelegation delegation = delegationRepository.findByAppDeleIdAndCompanyId(delegationId, companyId)
                .orElseThrow(() -> new BusinessException("위임 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        delegationRepository.delete(delegation);
    }

    /*관리자 위임 토글 */
    @Transactional
    public void toggleByAdmin(UUID companyId, Long delegationId) {
        ApprovalDelegation delegation = delegationRepository.findByAppDeleIdAndCompanyId(delegationId, companyId)
                .orElseThrow(() -> new BusinessException("위임 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        delegation.toggleIsActive();
    }

    /*위임 삭제 */
    @Transactional
    public void delete(UUID companyId, Long empId, Long delegationId) {
        ApprovalDelegation delegation = findOwnDelegation(companyId, empId, delegationId);
        delegationRepository.delete(delegation);
    }


    /* isActive 토글 */
    @Transactional
    public void toggle(UUID companyId, Long empId, Long delegationId) {
        ApprovalDelegation delegation = findOwnDelegation(companyId, empId, delegationId);
        delegation.toggleIsActive();
    }

    /*본인 위임 조회 (공통 검증) */
    private ApprovalDelegation findOwnDelegation(UUID companyId, Long empId, Long delegationId) {
        return delegationRepository.findByAppDeleIdAndCompanyIdAndEmpId(delegationId, companyId, empId)
                .orElseThrow(() -> new BusinessException("위임 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
    }

}
