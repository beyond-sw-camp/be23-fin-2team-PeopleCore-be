package com.peoplecore.hrorder.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.alarm.publisher.HrAlarmPublisher;
import com.peoplecore.department.domain.Department;
import com.peoplecore.department.repository.DepartmentRepository;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.event.AlarmEvent;
import com.peoplecore.formsetup.domain.FormType;
import com.peoplecore.formsetup.dto.FormFieldSetupResponse;
import com.peoplecore.formsetup.service.FormFieldSetupService;
import com.peoplecore.grade.domain.Grade;
import com.peoplecore.grade.repository.GradeRepository;
import com.peoplecore.hrorder.domain.*;
import com.peoplecore.hrorder.dto.*;
import com.peoplecore.hrorder.repository.HrOrderDetailRepository;
import com.peoplecore.hrorder.repository.HrOrderRepository;
import com.peoplecore.resign.domain.Resign;
import com.peoplecore.resign.repository.ResignRepository;
import com.peoplecore.title.domain.Title;
import com.peoplecore.title.repository.TitleRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

import static org.springframework.integration.dsl.Transformers.fromJson;
import static org.springframework.integration.json.SimpleJsonSerializer.toJson;

@Service
@Transactional
public class HrOrderService {

    private final HrOrderRepository hrOrderRepository;
    private final EmployeeRepository employeeRepository;
    private final FormFieldSetupService formFieldSetupService;
    private final HrOrderDetailRepository hrOrderDetailRepository;
    private final DepartmentRepository departmentRepository;
    private final GradeRepository gradeRepository;
    private final TitleRepository titleRepository;
    private final ResignRepository resignRepository;
    private final ObjectMapper objectMapper;
    private final HrAlarmPublisher hrAlarmPublisher;

    public HrOrderService(HrOrderRepository hrOrderRepository, EmployeeRepository employeeRepository, FormFieldSetupService formFieldSetupService, HrOrderDetailRepository hrOrderDetailRepository, DepartmentRepository departmentRepository, GradeRepository gradeRepository, TitleRepository titleRepository, ResignRepository resignRepository, ObjectMapper objectMapper, HrAlarmPublisher hrAlarmPublisher) {
        this.hrOrderRepository = hrOrderRepository;
        this.employeeRepository = employeeRepository;
        this.formFieldSetupService = formFieldSetupService;
        this.hrOrderDetailRepository = hrOrderDetailRepository;
        this.departmentRepository = departmentRepository;
        this.gradeRepository = gradeRepository;
        this.titleRepository = titleRepository;
        this.resignRepository = resignRepository;
        this.objectMapper = objectMapper;
        this.hrAlarmPublisher = hrAlarmPublisher;
    }

//    1. 목록조회

    @Transactional
    public Page<HrOrderListReqDto> list(UUID companyId, String keyword, OrderType orderType, OrderStatus status, Pageable pageable) {
        Page<HrOrder> orders = hrOrderRepository.findAllWithFilter(companyId, keyword, orderType, status, pageable);
        List<HrOrderListReqDto> dtoList = new ArrayList<>();
        for (HrOrder order : orders.getContent()) {
            dtoList.add(HrOrderListReqDto.fromEntity(order));
        }
        return new PageImpl<>(dtoList, orders.getPageable(), orders.getTotalElements()); //변환된dto, 현재페이지정보, 전체건수
    }

    //    2. 발령등록
    public Long create(UUID companyId, Long userId, HrOrderCreateReqDto req) {

//        대상사원조회
        Long empId = req.getDetails().get(0).getEmpId();
        Employee employee = employeeRepository.findById(empId).orElseThrow(() -> new IllegalArgumentException("사원을 찾을 수 없습니다"));

//        현재 폼 설정 값 저장->json문자열로 변환, 등록 싯점 폼 구성 스냅샷
        List<FormFieldSetupResponse> currentForm = formFieldSetupService.getSetup(companyId, FormType.HR_ORDER);
        String formSnapshot = toJson(currentForm); // 폼 설정 -> json
        long formVersion = System.currentTimeMillis(); // 타임스탬프로 폼 버전 식별

        HrOrder hrOrder = HrOrder.builder()
                .company(employee.getCompany())
                .employee(employee)
                .createBy(userId)
                .orderType(req.getOrderType()) //발령유형
                .effectiveDate(req.getEffectiveDate()) //발령일
                .isNotified(false) //통보여부
                .status(OrderStatus.SCHEDULED) //발령예정으로 시작
                .formValues(toJson(req.getFormValues()))    //동적 폼입력값
                .formSnapshot(formSnapshot) //폼설정 스냅샷 //->json
                .formVersion(formVersion) //폼 버전
                .build();
        hrOrderRepository.save(hrOrder);

//        hrOrderDetail값 입력 및 저장
        for (HrOrderCreateReqDto.DetailItem item : req.getDetails()) {
            hrOrderDetailRepository.save(HrOrderDetail.builder()
                    .hrOrder(hrOrder)
                    .targetType(OrderDetailTargetType.valueOf(item.getTargetType())) //문자열 -> enum
                    .beforeId(item.getBeforeId()) //변경전
                    .afterId(item.getAfterId()) //변경후
                    .build());
        }

//        발령일이 오늘이거나 이전이면 즉시 employee 반영 (스케줄러는 다음 자정에 실행되므로)
        if (!req.getEffectiveDate().isAfter(LocalDate.now())) {
            applyOrder(hrOrder);
        }
        return hrOrder.getOrderId();
    }

    //    3.상세조회
    @Transactional(readOnly = true)
    public HrOrderDetailResDto detail(UUID companyId, Long orderId) {
//        orderId에 해당하는 상세목록 조회
        HrOrder order = hrOrderRepository.findByOrderIdAndCompanyId(orderId, companyId).orElseThrow(() -> new IllegalArgumentException("발령정보를 찾을 수 없습니다"));

        Employee employee = order.getEmployee();

//        변경 상세조회, 각 타입마다 id -> 이름으로 변환
        List<HrOrderDetail> detailEntities = hrOrderDetailRepository.findByHrOrder_OrderId(orderId);
        List<HrOrderDetailResDto.DetailInfo> detailInfos = new ArrayList<>();
        for (HrOrderDetail d : detailEntities) {
            detailInfos.add(HrOrderDetailResDto.DetailInfo.builder()
                    .targetType(d.getTargetType().name())
                    .beforeName(resolveTargetName(d.getTargetType(), d.getBeforeId()))
                    .afterName(resolveTargetName(d.getTargetType(), d.getAfterId()))
                    .build());
        }

//        폼필드 값조립(snapshot +values)
        List<HrOrderDetailResDto.FieldDetail> formFields = buildFormFields(order);
        return HrOrderDetailResDto.builder()
                .orderId(order.getOrderId())
                .empId(employee.getEmpId())
                .empNum(employee.getEmpNum())
                .empName(employee.getEmpName())
                .deptName(employee.getDept().getDeptName())
                .gradeName(employee.getGrade().getGradeName())
                .titleName(employee.getTitle() != null ? employee.getTitle().getTitleName() : "")
                .orderType(order.getOrderType().name())
                .effectiveDate(order.getEffectiveDate().toString())
                .status(order.getStatus().name())
                .isNotified(order.getIsNotified())
                .notifiedAt(order.getNotifiedAt() != null ? order.getNotifiedAt().toString() : null)
                .createdAt(order.getCreatedAt().toString())
                .details(detailInfos)
                .formFields(formFields)
                .build();

    }


    //    4. 수정(반영 전만)
    public Long update(UUID companyId, Long orderId, HrOrderUpdateReqDto reqDto) {

//        발령조회 + 반영 전 상태 검증 else ->예외
        HrOrder order = getOrderAndValidate(companyId, orderId, OrderStatus.SCHEDULED, "반영 전 발령만 수정이 가능합니다");

//        기존 변경상세 삭제
        hrOrderDetailRepository.deleteByHrOrder_OrderId(orderId);

//        새 변경 상세 등록
        for (HrOrderCreateReqDto.DetailItem item : reqDto.getDetails()) {
            hrOrderDetailRepository.save(HrOrderDetail.builder()
                    .hrOrder(order)
                    .targetType(OrderDetailTargetType.valueOf(item.getTargetType()))
                    .beforeId(item.getBeforeId())
                    .afterId(item.getAfterId())
                    .build());
        }
        order.updateOrder(reqDto.getOrderType(), reqDto.getEffectiveDate(), toJson(reqDto.getFormValues()));
        return orderId;
    }


    //    5.삭제(soft, 반영 전만)
    public void delete(UUID companyId, Long orderId) {
        HrOrder order = hrOrderRepository.findByOrderIdAndCompanyId(orderId, companyId).orElseThrow(() -> new IllegalArgumentException("발령정보를 찾을 수 없습니다"));
        if (order.isDeleted()) {
            throw new IllegalStateException("이미 삭제된 발령입니다");
        }
        if (order.getStatus() != OrderStatus.SCHEDULED) {
            throw new IllegalStateException("반영 전 발령만 삭제가 가능합니다");
        }
        order.softDelete();
    }

    //    7. 발령일 도래 시 일괄 반영 (SCHEDULED + 발령일이 오늘 이전인 건 -> employee 반영 + APPLIED)
    public int applyAllScheduledOrders() {
        List<HrOrder> orders = hrOrderRepository.findByStatusAndEffectiveDateLessThanEqual(
                OrderStatus.SCHEDULED, LocalDate.now());
        for (HrOrder order : orders) {
            applyOrder(order);
        }
        return orders.size();
    }

    //    단건 employee 반영 (등록 시 즉시 반영 + 스케줄러에서 공통 사용)
    private void applyOrder(HrOrder order) {
        List<HrOrderDetail> details = hrOrderDetailRepository.findByHrOrder_OrderId(order.getOrderId());
        Employee employee = order.getEmployee();

        for (HrOrderDetail d : details) {
            if (d.getTargetType() == OrderDetailTargetType.DEPARTMENT) {
                Department department = departmentRepository.findById(d.getAfterId()).orElse(null);
                if (department != null) employee.updateDept(department);
            } else if (d.getTargetType() == OrderDetailTargetType.GRADE) {
                Grade grade = gradeRepository.findById(d.getAfterId()).orElse(null);
                if (grade != null) employee.updateGrade(grade);
            } else if (d.getTargetType() == OrderDetailTargetType.TITLE) {
                Title title = titleRepository.findById(d.getAfterId()).orElse(null);
                if (title != null) employee.updateTitle(title);
            }
        }
        order.updateStatus(OrderStatus.APPLIED);

        //  발령 적용 시 본인에게 자동 알림 (alarm-event 토픽 publish)
        StringBuilder content = new StringBuilder();
        content.append(employee.getEmpName()).append("님, ");
        for (HrOrderDetail d : details) {
            String before = resolveTargetName(d.getTargetType(), d.getBeforeId());
            String after  = resolveTargetName(d.getTargetType(), d.getAfterId());
            content.append(before).append("→").append(after).append(" ");
        }
        content.append("발령이 적용되었습니다. (발령일: ").append(order.getEffectiveDate()).append(")");

        hrAlarmPublisher.publisher(AlarmEvent.builder()
                .companyId(employee.getCompany().getCompanyId())
                .alarmType("HR")
                .alarmTitle("인사발령 적용")
                .alarmContent(content.toString())
                .alarmLink("/hr/appointment/" + order.getOrderId())
                .alarmRefType("HR_ORDER")
                .alarmRefId(order.getOrderId())
                .empIds(List.of(employee.getEmpId()))
                .build());
    }


    //    사원별 발령 이력 조회 (HIRE / 발령(PROMOTION/TRANSFER/TITLE_CHANGE) / RESIGN 통합)
    @Transactional(readOnly = true)
    public List<HrOrderHistoryResDto> history(UUID companyId, Long empId) {
        List<HrOrderHistoryResDto> result = new ArrayList<>();

        // 1) hr_order 발령 이력 (PROMOTION / TRANSFER / TITLE_CHANGE)
        // 입사 당시 값 역산용 — 각 type별 가장 빠른(effectiveDate 최소) 발령의 detail 추적
        HrOrder earliestDeptOrder = null;  HrOrderDetail earliestDeptDetail = null;
        HrOrder earliestGradeOrder = null; HrOrderDetail earliestGradeDetail = null;
        HrOrder earliestTitleOrder = null; HrOrderDetail earliestTitleDetail = null;

        List<HrOrder> orders = hrOrderRepository.findHistoryByEmpId(companyId, empId);
        for (HrOrder order : orders) {
            List<HrOrderDetail> detailEntities = hrOrderDetailRepository.findByHrOrder_OrderId(order.getOrderId());
            List<HrOrderHistoryResDto.DetailChange> detailChange = new ArrayList<>();

            for (HrOrderDetail d : detailEntities) {
                detailChange.add(HrOrderHistoryResDto.DetailChange.builder()
                        .targetType(d.getTargetType().name())
                        .beforeName(resolveTargetName(d.getTargetType(), d.getBeforeId()))
                        .afterName(resolveTargetName(d.getTargetType(), d.getAfterId()))
                        .build());

                // 입사 시점 역산용 — 같은 type 중 effectiveDate 최소인 것 갱신
                if (d.getTargetType() == OrderDetailTargetType.DEPARTMENT) {
                    if (earliestDeptOrder == null || order.getEffectiveDate().isBefore(earliestDeptOrder.getEffectiveDate())) {
                        earliestDeptOrder = order;
                        earliestDeptDetail = d;
                    }
                } else if (d.getTargetType() == OrderDetailTargetType.GRADE) {
                    if (earliestGradeOrder == null || order.getEffectiveDate().isBefore(earliestGradeOrder.getEffectiveDate())) {
                        earliestGradeOrder = order;
                        earliestGradeDetail = d;
                    }
                } else if (d.getTargetType() == OrderDetailTargetType.TITLE) {
                    if (earliestTitleOrder == null || order.getEffectiveDate().isBefore(earliestTitleOrder.getEffectiveDate())) {
                        earliestTitleOrder = order;
                        earliestTitleDetail = d;
                    }
                }
            }
            result.add(HrOrderHistoryResDto.builder()
                    .orderId(order.getOrderId())
                    .orderType(order.getOrderType().name())
                    .effectiveDate(order.getEffectiveDate().toString())
                    .status(order.getStatus().name())
                    .createAt(order.getCreatedAt().toString())
                    .detailChange(detailChange)
                    .build());
        }

        // 2) HIRE 합성: Employee.empHireDate 기준 + 입사 당시 부서/직급/직책 채우기
        //    - 첫 번째 전보의 beforeName = 입사 당시 부서 (그 사이 다른 전보가 없었으므로)
        //    - 발령 이력이 없으면 현재값 사용 (입사 후 한 번도 안 바뀌었다는 뜻)
        Employee emp = employeeRepository.findByEmpIdAndCompany_CompanyId(empId, companyId).orElse(null);
        if (emp != null && emp.getEmpHireDate() != null) {
            String initialDept;
            if (earliestDeptDetail != null) {
                initialDept = resolveTargetName(OrderDetailTargetType.DEPARTMENT, earliestDeptDetail.getBeforeId());
            } else {
                initialDept = emp.getDept() != null ? emp.getDept().getDeptName() : null;
            }

            String initialGrade;
            if (earliestGradeDetail != null) {
                initialGrade = resolveTargetName(OrderDetailTargetType.GRADE, earliestGradeDetail.getBeforeId());
            } else {
                initialGrade = emp.getGrade() != null ? emp.getGrade().getGradeName() : null;
            }

            String initialTitle;
            if (earliestTitleDetail != null) {
                initialTitle = resolveTargetName(OrderDetailTargetType.TITLE, earliestTitleDetail.getBeforeId());
            } else {
                initialTitle = emp.getTitle() != null ? emp.getTitle().getTitleName() : null;
            }

            List<HrOrderHistoryResDto.DetailChange> hireDetail = new ArrayList<>();
            if (initialDept != null) {
                hireDetail.add(HrOrderHistoryResDto.DetailChange.builder()
                        .targetType("DEPARTMENT")
                        .beforeName("")
                        .afterName(initialDept)
                        .build());
            }
            if (initialGrade != null) {
                hireDetail.add(HrOrderHistoryResDto.DetailChange.builder()
                        .targetType("GRADE")
                        .beforeName("")
                        .afterName(initialGrade)
                        .build());
            }
            if (initialTitle != null) {
                hireDetail.add(HrOrderHistoryResDto.DetailChange.builder()
                        .targetType("TITLE")
                        .beforeName("")
                        .afterName(initialTitle)
                        .build());
            }

            result.add(HrOrderHistoryResDto.builder()
                    .orderId(null)
                    .orderType("HIRE")
                    .effectiveDate(emp.getEmpHireDate().toString())
                    .status("APPLIED")
                    .createAt(emp.getEmpHireDate().toString())
                    .detailChange(hireDetail)
                    .build());
        }

        // 3) RESIGN 합성: Resign 테이블에서 RESIGNED 상태만
        List<Resign> resigns = resignRepository.findHistoryByEmpId(companyId, empId);
        for (Resign r : resigns) {
            if (r.getResignDate() == null) continue;
            String createAt = r.getProcessedAt() != null
                    ? r.getProcessedAt().toLocalDate().toString()
                    : r.getResignDate().toString();
            result.add(HrOrderHistoryResDto.builder()
                    .orderId(null)
                    .orderType("RESIGN")
                    .effectiveDate(r.getResignDate().toString())
                    .status("APPLIED")
                    .createAt(createAt)
                    .detailChange(new ArrayList<>())
                    .build());
        }

        // 4) effectiveDate 내림차순 정렬 (최신 이벤트가 위에)
        result.sort((a, b) -> b.getEffectiveDate().compareTo(a.getEffectiveDate()));
        return result;
    }


    //    공통유틸 id->이름 반환
    private String resolveTargetName(OrderDetailTargetType targetType, Long id) {
        return switch (targetType) {
            case DEPARTMENT -> departmentRepository.findById(id).map(Department::getDeptName).orElse("");
            case GRADE -> gradeRepository.findById(id).map(Grade::getGradeName).orElse("");
            case TITLE -> titleRepository.findById(id).map(Title::getTitleName).orElse("");

        };
    }


    //    동적 폼 필드 조립(snapshot _values매칭)
    private List<HrOrderDetailResDto.FieldDetail> buildFormFields(HrOrder hrOrder) {
        List<FormFieldSetupResponse> snapshot = fromJson(hrOrder.getFormSnapshot());
        Map<String, String> values = fromJsonMap(hrOrder.getFormValues());
        if (snapshot == null || values == null) return new ArrayList<>();

        List<HrOrderDetailResDto.FieldDetail> fields = new ArrayList<>();
        for (FormFieldSetupResponse f : snapshot) {
            fields.add(HrOrderDetailResDto.FieldDetail.builder()
                    .fieldKey(f.getFieldKey())
                    .label(f.getLabel())
                    .section(f.getSection())
                    .fieldType(f.getFieldType())
                    .value(values.getOrDefault(f.getFieldKey(), ""))
                    .build());
        }
        return fields;

    }


    //    공통유틸: 발령조회+상태검증
    private HrOrder getOrderAndValidate(UUID companyId, Long orderId, OrderStatus expectedStatus, String errorMsg) {
        HrOrder order = hrOrderRepository.findByOrderIdAndCompanyId(orderId, companyId).orElseThrow(() -> new IllegalArgumentException("발령 정보를 찾을 수 없습니다"));
        if (order.getStatus() != expectedStatus) {
            throw new IllegalStateException(errorMsg);
        }
        return order;
    }


    //      자바객체->json(db저장)
    private String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    //    json -> 폼설정 리스트 복원(snapshot복원용)
    private List<FormFieldSetupResponse> fromJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, FormFieldSetupResponse.class));
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    //    json -> Map복원(입력값 복원)
    private Map<String, String> fromJsonMap(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, String.class));
        } catch (JsonProcessingException e) {
            return new HashMap<>();
        }

    }


}