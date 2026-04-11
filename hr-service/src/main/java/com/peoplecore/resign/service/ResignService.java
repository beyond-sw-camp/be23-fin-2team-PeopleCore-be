package com.peoplecore.resign.service;

import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.resign.domain.ApprovalStatus;
import com.peoplecore.resign.domain.Resign;
import com.peoplecore.resign.domain.ResignSortField;
import com.peoplecore.resign.domain.RetireStatus;
import com.peoplecore.resign.dto.ResignDetailDto;
import com.peoplecore.resign.dto.ResignListDto;
import com.peoplecore.resign.dto.ResignStatusDto;
import com.peoplecore.resign.repository.ResignRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ResignService {

    private final ResignRepository resignRepository;

    public ResignService(ResignRepository resignRepository) {
        this.resignRepository = resignRepository;
    }


    @Transactional(readOnly = true)
    public Page<ResignListDto>getResignList(UUID companyId, String keyword, String approvalStatus, String empStatus, ResignSortField sortField, Pageable pageable){
        Page<Resign>resigns = resignRepository.findAllWithFilter(companyId,keyword,approvalStatus,empStatus,sortField,pageable);
        List<ResignListDto>dtoList =new ArrayList<>();
        for(Resign r: resigns.getContent()) { //getContent = List<Resign>반환
            dtoList.add(ResignListDto.fromEntity(r));
        }
        return new PageImpl<>(dtoList, resigns.getPageable(),resigns.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ResignStatusDto getStatus(UUID companyId){
        return ResignStatusDto.builder()
//                퇴직처리 대기 (결재완료 + 재직)
                .processableCount(resignRepository.countByEmployee_Company_CompanyIdAndIsDeletedFalseAndApprovalStatusAndRetireStatus(companyId, ApprovalStatus.APPROVED, RetireStatus.ACTIVE))
//                스케줄러 대기 (인사팀 처리완료, 퇴직예정일 대기)
                .confirmedCount(resignRepository.countByEmployee_Company_CompanyIdAndIsDeletedFalseAndRetireStatus(companyId, RetireStatus.CONFIRMED))
//                퇴직완료
                .completedCount(resignRepository.countByEmployee_Company_CompanyIdAndIsDeletedFalseAndRetireStatus(companyId, RetireStatus.RESIGNED))
//                결재대기
                .pendingCount(resignRepository.countByEmployee_Company_CompanyIdAndIsDeletedFalseAndApprovalStatus(companyId, ApprovalStatus.PENDING))
                .build();
    }

    @Transactional(readOnly = true)
    public ResignDetailDto getResignDetail(UUID companyId, Long resignId){
        Resign resign = resignRepository.findDetailByCompanyAndId(companyId, resignId).orElseThrow(()-> new IllegalArgumentException("해당 퇴직 정보를 찾을 수 없습니다"));
        return ResignDetailDto.fromEntity(resign);
    }

    public void processResign(UUID companyId, Long resignId){
        Resign resign = resignRepository.findByResignIdAndEmployee_Company_CompanyIdAndIsDeletedFalse(resignId,companyId).orElseThrow(()->new IllegalArgumentException("해당퇴직정보를 찾을 수 없습니다"));

        if(resign.getApprovalStatus()!=ApprovalStatus.APPROVED){
            throw new IllegalArgumentException("결제가 완료되지 않은 건은 퇴직 처리 할 수 없습니다");
        }

        if(resign.getRetireStatus()!=RetireStatus.ACTIVE){
            throw new IllegalArgumentException("이미 처리된 퇴직 건입니다");
        }

        resign.confirmRetire(); // ACTIVE -> CONFIRMED (스케줄러 대기)
    }

    //스케줄러용: CONFIRMED 상태이고 퇴직예정일이 오늘 이하인 건들 퇴직 처리
    public void processScheduledResigns() {
        List<Resign> targets = resignRepository.findAllByRetireStatusAndIsDeletedFalseAndResignDateLessThanEqual(
                RetireStatus.CONFIRMED, LocalDate.now());

        for (Resign resign : targets) {
            resign.processRetire();
            Employee employee = resign.getEmployee();
            employee.updateStatus(EmpStatus.RESIGNED);
            employee.updateResignDate(resign.getResignDate());
        }
    }

    public void deleteResign(UUID companyId, Long resignId){
        Resign resign = resignRepository.findByResignIdAndEmployee_Company_CompanyIdAndIsDeletedFalse(resignId,companyId).orElseThrow(()->new IllegalArgumentException("해당퇴직정보를 찾을 수 없습니다"));

        if(resign.getRetireStatus()!=RetireStatus.RESIGNED){
            throw new IllegalArgumentException("퇴직 완료된 건만 삭제가 가능합니다");
        }
        resign.softDelete();
    }


}
