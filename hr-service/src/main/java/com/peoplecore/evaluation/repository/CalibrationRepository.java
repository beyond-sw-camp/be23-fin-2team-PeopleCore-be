package com.peoplecore.evaluation.repository;

import com.peoplecore.evaluation.domain.Calibration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

// 보정이력 리포지토리
public interface CalibrationRepository extends JpaRepository<Calibration, Long> {

//   7번 - 보정 페이지 사원 목록용 batch 조회
//    - 첫 row -> fromGrade (원본 자동등급)
//    - 마지막 row -> toGrade/reason/actor (최신 보정 결과)
    List<Calibration> findByGrade_GradeIdInOrderByCreatedAtAsc(Collection<Long> gradeIds);

//   8번 - 시즌 전체 보정 이력 (건별, 시간순)
//    - createdAt 오름차순 -> 프론트에서 index+1 로 순번
    List<Calibration> findByGrade_Season_SeasonIdOrderByCreatedAtAsc(Long seasonId);

//   5번 강제배분 재실행 - 보정 이력 개수 (프론트 확인 팝업에 표시할 N)
    long countByGrade_Season_SeasonId(Long seasonId);

//   5번 강제배분 재실행 - 보정 이력 전체 삭제 (cohort 변경 시 + confirm=true)
    void deleteAllByGrade_Season_SeasonId(Long seasonId);
}
