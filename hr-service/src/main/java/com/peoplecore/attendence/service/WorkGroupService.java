package com.peoplecore.attendence.service;

import com.peoplecore.attendence.dto.WorkGroupDetailResDto;
import com.peoplecore.attendence.dto.WorkGroupMemberResDto;
import com.peoplecore.attendence.dto.WorkGroupReqDto;
import com.peoplecore.attendence.dto.WorkGroupResDto;
import com.peoplecore.attendence.entity.WorkGroup;
import com.peoplecore.attendence.repository.WorkGroupRepository;
import com.peoplecore.attendence.repository.WorkGroupSearchRepository;
import com.peoplecore.company.domain.Company;
import com.peoplecore.company.repository.CompanyRepository;
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
}
