package com.peoplecore.resign.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.auth.service.FaceAuthService;
import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.event.ResignApprovedEvent;
import com.peoplecore.resign.domain.Resign;
import com.peoplecore.resign.domain.ResignSortField;
import com.peoplecore.resign.domain.RetireStatus;
import com.peoplecore.resign.dto.ResignDetailDto;
import com.peoplecore.resign.dto.ResignListDto;
import com.peoplecore.resign.dto.ResignStatusDto;
import com.peoplecore.resign.event.EmployeeRetiredEvent;
import com.peoplecore.resign.repository.ResignRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class ResignService {

    private final ResignRepository resignRepository;
    private final EmployeeRepository employeeRepository;
    private final ObjectMapper objectMapper;
    private final FaceAuthService faceAuthService;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public ResignService(ResignRepository resignRepository, EmployeeRepository employeeRepository, ObjectMapper objectMapper, FaceAuthService faceAuthService, ApplicationEventPublisher eventPublisher) {
        this.resignRepository = resignRepository;
        this.employeeRepository = employeeRepository;
        this.objectMapper = objectMapper;
        this.faceAuthService = faceAuthService;
        this.eventPublisher = eventPublisher;
    }


    @Transactional(readOnly = true)
    public Page<ResignListDto>getResignList(UUID companyId, String keyword, String empStatus, ResignSortField sortField, Pageable pageable){
        Page<Resign>resigns = resignRepository.findAllWithFilter(companyId,keyword,empStatus,sortField,pageable);
        List<ResignListDto>dtoList =new ArrayList<>();
        for(Resign r: resigns.getContent()) { //getContent = List<Resign>반환
            dtoList.add(ResignListDto.fromEntity(r));
        }
        return new PageImpl<>(dtoList, resigns.getPageable(),resigns.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ResignStatusDto getStatus(UUID companyId){
        return ResignStatusDto.builder()
//                퇴직처리 대기
                .processableCount(resignRepository.countByEmployee_Company_CompanyIdAndIsDeletedFalseAndRetireStatus(companyId, RetireStatus.ACTIVE))
//                스케줄러 대기 (인사팀 처리완료, 퇴직예정일 대기)
                .confirmedCount(resignRepository.countByEmployee_Company_CompanyIdAndIsDeletedFalseAndRetireStatus(companyId, RetireStatus.CONFIRMED))
//                퇴직완료
                .completedCount(resignRepository.countByEmployee_Company_CompanyIdAndIsDeletedFalseAndRetireStatus(companyId, RetireStatus.RESIGNED))
                .build();
    }

    @Transactional(readOnly = true)
    public ResignDetailDto getResignDetail(UUID companyId, Long resignId){
        Resign resign = resignRepository.findDetailByCompanyAndId(companyId, resignId).orElseThrow(()-> new IllegalArgumentException("해당 퇴직 정보를 찾을 수 없습니다"));
        return ResignDetailDto.fromEntity(resign);
    }

    public void processResign(UUID companyId, Long resignId){
        Resign resign = resignRepository.findByResignIdAndEmployee_Company_CompanyIdAndIsDeletedFalse(resignId,companyId).orElseThrow(()->new IllegalArgumentException("해당퇴직정보를 찾을 수 없습니다"));

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
            faceAuthService.cascadeUnregisterFace(employee.getEmpId(), employee.getCompany().getCompanyId());

//            이벤트 발생(-> 퇴직금 산정) +평가자 중 퇴직자 알림(리스너 추가)
            eventPublisher.publishEvent(new EmployeeRetiredEvent(employee.getCompany().getCompanyId(),employee.getEmpId()));
        }
    }

    public void deleteResign(UUID companyId, Long resignId){
        Resign resign = resignRepository.findByResignIdAndEmployee_Company_CompanyIdAndIsDeletedFalse(resignId,companyId).orElseThrow(()->new IllegalArgumentException("해당퇴직정보를 찾을 수 없습니다"));

        if(resign.getRetireStatus()!=RetireStatus.RESIGNED){
            throw new IllegalArgumentException("퇴직 완료된 건만 삭제가 가능합니다");
        }
        resign.softDelete();
    }


//    kafka
    public void createResignFromApprocal(ResignApprovedEvent event){
        Employee employee =employeeRepository.findById(event.getEmpId()).orElseThrow(()-> new IllegalArgumentException("사원을 찾을 수 없습니다"));

//        docData(json)에서 퇴직예정일 추출
        LocalDate resignDate = null;
        try{
//            json문자열을 map<키,값>으로 파싱
            Map<String, String> docData = objectMapper.readValue(event.getDocData(), new TypeReference<Map<String, String>>() {});
//            양식에 name= "resignDate"있으면 해당 값 사용
            String dateStr = docData.get("resignDate");
            if(dateStr == null){
//                없으면 자동부여된 키 사용
                dateStr = docData.get("field_5");
            }
            if(dateStr != null && !dateStr.isBlank()){
                resignDate=LocalDate.parse(dateStr);
            }
        } catch (Exception e) {
//            파싱실패해도 Resign생성
            log.error("doc파싱실패");

        }

        Resign resign =Resign.builder()
                .employee(employee) //퇴직 신청 사원
                .department(employee.getDept())
                .grade(employee.getGrade())
                .title(employee.getTitle())
                .docId(event.getDocId()) //결제문서 id
                .resignDate(resignDate) //퇴직 예정일
                .registeredDate(LocalDate.now())
                .build();

        resignRepository.save(resign);
        }

    }

