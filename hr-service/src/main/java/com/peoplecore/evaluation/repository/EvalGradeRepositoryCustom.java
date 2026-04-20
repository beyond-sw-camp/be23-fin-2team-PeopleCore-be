package com.peoplecore.evaluation.repository;

import com.peoplecore.evaluation.domain.EvalGrade;
import com.peoplecore.evaluation.domain.EvalGradeSortField;
import com.peoplecore.evaluation.dto.DraftListItemDto;
import com.peoplecore.evaluation.dto.FinalGradeListItemDto;
import com.peoplecore.evaluation.dto.UnassignedEmployeeDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

// 등급 커스텀 (초안/보정/결과 조회 - 검색/정렬/페이징)
public interface EvalGradeRepositoryCustom {

    // 1번 - 자동 산정 대상 목록 조회
    //  - 시즌/회사 범위 + 부서·키워드 필터 + 정렬 + 페이징
    Page<DraftListItemDto> searchDraftList(UUID companyId, Long seasonId,
                                           Long deptId, String keyword,
                                           EvalGradeSortField sortField, Pageable pageable);

    // 7번 - 보정 페이지 사원 목록 (엔티티 반환 -> 서비스에서 Calibration 이력 합성)
    //  - autoGrade null(미산정)은 보정 불가 -> 제외
    Page<EvalGrade> searchCalibrationGrades(UUID companyId, Long seasonId,
                                            Long deptId, String keyword,
                                            EvalGradeSortField sortField, Pageable pageable);

    // 11번 - 최종 확정 페이지 미제출·미산정 직원 목록 (finalGrade IS NULL 대상)
    Page<UnassignedEmployeeDto> searchUnassigned(UUID companyId, Long seasonId,
                                                  Long deptId,
                                                  EvalGradeSortField sortField,
                                                  Pageable pageable);

    // 13번 - 평가 결과 목록 (HR 전용, 미산정자 포함)
    //  - unscoredOnly: null=전체 / true=미산정자(autoGrade=null)만 / false=산정자만
    //  - 미산정자는 프론트에서 "미산정자" 배지로 표시 (상세보기 버튼 숨김)
    Page<FinalGradeListItemDto> searchFinalList(UUID companyId, Long seasonId,
                                                Long deptId, String keyword,
                                                Boolean unscoredOnly,
                                                EvalGradeSortField sortField, Pageable pageable);
}
