package com.peoplecore.attendence.service;

import com.peoplecore.attendence.dto.*;
import com.peoplecore.attendence.entity.WorkGroup;
import com.peoplecore.attendence.repository.WorkGroupRepository;
import com.peoplecore.attendence.repository.WorkGroupSearchRepository;
import com.peoplecore.company.domain.Company;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Slf4j
public class WorkGroupService {
    private final WorkGroupRepository workGroupRepository;
    private final WorkGroupSearchRepository workGroupSearchRepository;
    private final EmployeeRepository employeeRepository;
    private final CompanyRepository companyRepository;

    @Autowired
    public WorkGroupService(WorkGroupRepository workGroupRepository, WorkGroupSearchRepository workGroupSearchRepository, EmployeeRepository employeeRepository, CompanyRepository companyRepository) {
        this.workGroupRepository = workGroupRepository;
        this.workGroupSearchRepository = workGroupSearchRepository;
        this.employeeRepository = employeeRepository;
        this.companyRepository = companyRepository;
    }

    /*근무 그룹 목록 조회 */
    @Transactional(readOnly = true)
    public List<WorkGroupResDto> getWorkGroups(UUID companyId) {
        return workGroupSearchRepository.findWorkGroupWithEmpCount(companyId);
    }

    /*근무 그룹 상세 조회 */
    @Transactional(readOnly = true)
    public WorkGroupDetailResDto getWorkGroup(Long workGroupId) {
        WorkGroup workGroup = workGroupRepository.findByWorkGroupIdAndGroupDeleteAtIsNull(workGroupId).orElseThrow(() -> new CustomException(ErrorCode.WORK_GROUP_NOT_FOUND));
        return WorkGroupDetailResDto.from(workGroup);
    }


    /* 근무 그룹 소속 사원 조회 */
    @Transactional(readOnly = true)
    public Page<WorkGroupMemberResDto> getEmployees(Long workGroupId, Pageable pageable) {
        /* 그룹 존재 여부 체크*/
        workGroupRepository.findByWorkGroupIdAndGroupDeleteAtIsNull(workGroupId).orElseThrow(() -> new CustomException(ErrorCode.WORK_GROUP_NOT_FOUND));
        return employeeRepository.findByWorkGroup_WorkGroupId(workGroupId, pageable).map(WorkGroupMemberResDto::from);
    }

    /* 근무 그룹 생성 */
    public WorkGroupDetailResDto createWorkGroup(UUID companyId, Long managerId, String managerName, WorkGroupReqDto dto) {
        /* 근무 그룹 코드 중복 체크 */
        if (workGroupRepository.existsByCompany_CompanyIdAndGroupCodeAndGroupDeleteAtIsNull(companyId, dto.getGroupCode())) {
            throw new CustomException(ErrorCode.WORK_GROUP_CODE_DUPLICATE);
        }

        /* company Fk로 조회 */
        Company company = companyRepository.findById(companyId).orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND));

        /* 엔티티 생성 */
        WorkGroup workGroup = WorkGroup.builder()
                .company(company)
                .groupName(dto.getGroupName())
                .groupCode(dto.getGroupCode())
                .groupDesc(dto.getGroupDesc())
                .groupStartTime(dto.getGroupStartTime())
                .groupEndTime(dto.getGroupEndTime())
                .groupWorkDay(dto.getGroupWorkDay())
                .groupBreakStart(dto.getGroupBreakStart())
                .groupBreakEnd(dto.getGroupBreakEnd())
                .groupOvertimeRecognize(dto.getGroupOvertimeRecognize())
                .groupMobileCheck(dto.getGroupMobileCheck())
                .groupManagerId(managerId)
                .groupManagerName(managerName)
                .build();

        /*저장 */
        workGroupRepository.save(workGroup);
        return WorkGroupDetailResDto.from(workGroup);
    }


    /* 근무 그룹 수정 */
    public WorkGroupDetailResDto updateWorkGroup(Long workGroupId, WorkGroupReqDto dto) {
        WorkGroup workGroup = workGroupRepository.findByWorkGroupIdAndGroupDeleteAtIsNull(workGroupId).orElseThrow(() -> new CustomException(ErrorCode.WORK_GROUP_NOT_FOUND));
        workGroup.update(dto);
        return WorkGroupDetailResDto.from(workGroup);
    }

    /*근무 그룹 삭제*/
    public void deleteWorkGroup(Long workGroupId) {
        WorkGroup workGroup = workGroupRepository.findByWorkGroupIdAndGroupDeleteAtIsNull(workGroupId).orElseThrow(() -> new CustomException(ErrorCode.WORK_GROUP_NOT_FOUND));

        Long memberCount = employeeRepository.countByWorkGroup_WorkGroupId(workGroupId);
        if (memberCount > 0) {
            throw new CustomException(ErrorCode.WORK_GROUP_HAS_MEMBERS);
        }

        workGroup.softDelete();
    }


    /*회사 생성 시 기본 근무 그룹 자동 생성 */
    public void initDefault(Company company) {
        WorkGroup defaultGroup = WorkGroup.builder()
                .company(company)
                .groupName("기본 근무그룹")
                .groupCode("DEFAULT")
                .groupDesc("기본 근무 그룹")
                .groupStartTime(LocalTime.of(9, 0))
                .groupEndTime(LocalTime.of(18, 0))
                .groupWorkDay(31)
                .groupBreakStart(LocalTime.of(12, 0))
                .groupBreakEnd(LocalTime.of(13, 0))
                .groupOvertimeRecognize(WorkGroup.GroupOvertimeRecognize.APPROVAL)
                .groupMobileCheck(false)
                .build();

        workGroupRepository.save(defaultGroup);
    }


    /**
     * 근무 그룹 간 사원 이관
     */
    public WorkGroupTransferResDto transferEmp(Long sourceWorkGroupId, WorkGroupTransferReqDto dto) {
        /* source / target 근무 그룹 검증 */
        WorkGroup source = workGroupRepository.findByWorkGroupIdAndGroupDeleteAtIsNull(sourceWorkGroupId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORK_GROUP_NOT_FOUND));

        WorkGroup target = workGroupRepository.findByWorkGroupIdAndGroupDeleteAtIsNull(dto.getTargetWorkGroupId())
                .orElseThrow(() -> new CustomException(ErrorCode.WORK_GROUP_NOT_FOUND));

        /* 동일 그룹 체크 */
        if (source.getWorkGroupId().equals(target.getWorkGroupId())) {
            throw new CustomException(ErrorCode.WORK_GROUP_TRANSFER_SAME_TARGET);
        }

        /* 회사 일치 체크 */
        if (!source.getCompany().getCompanyId().equals(target.getCompany().getCompanyId())) {
            throw new CustomException(ErrorCode.WORK_GROUP_TRANSFER_DIFFERENT_COMPANY);
        }

        /* 이관 대상 조회 (JOIN FETCH → dept/grade/title 동시 로딩, N+1 방지) */
        List<Long> requestIds = dto.getEmpIds();
        List<Employee> targets = employeeRepository.findByWorkGroupIdAndEmpIdsFetchJoin(sourceWorkGroupId, requestIds);

        /* 엄격 검증: 요청 ID 중 source 미소속/미존재 포함 시 전체 거부 */
        if (targets.size() != requestIds.size()) {
            throw new CustomException(ErrorCode.WORK_GROUP_TRANSFER_INVALID_MEMBERS);
        }

        /* 재배정 + 응답 DTO 수집 (평탄 리스트 순회이므로 for-each) */
        List<WorkGroupMemberResDto> workGroupMembers = new ArrayList<>(targets.size());
        for (Employee emp : targets) {
            emp.assignWorkGroup(target);
            workGroupMembers.add(WorkGroupMemberResDto.from(emp));
        }

        /* 결과 요약 반환 */
        return WorkGroupTransferResDto.builder()
                .sourceWorkGroupId(source.getWorkGroupId())
                .targetWorkGroupId(target.getWorkGroupId())
                .moveCount(workGroupMembers.size())
                .movedMembers(workGroupMembers)
                .build();
    }

}
