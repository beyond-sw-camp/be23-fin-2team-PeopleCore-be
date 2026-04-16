package com.peoplecore.evaluation.repository;

import com.peoplecore.evaluation.domain.EvalGrade;
import com.peoplecore.evaluation.domain.EvalGradeSortField;
import com.peoplecore.evaluation.dto.DraftListItemDto;
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
}
