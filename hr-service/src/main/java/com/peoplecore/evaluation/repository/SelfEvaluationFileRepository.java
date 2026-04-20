package com.peoplecore.evaluation.repository;

import com.peoplecore.evaluation.domain.SelfEvaluationFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// 자기평가 근거 파일 리포지토리
public interface SelfEvaluationFileRepository extends JpaRepository<SelfEvaluationFile, Long> {

    // 자기평가 한 건에 딸린 파일 목록 - 업로드 순 정렬
    List<SelfEvaluationFile> findBySelfEvaluation_SelfEvalIdOrderByFileIdAsc(Long selfEvalId);

    // 여러 자기평가의 파일을 IN 절로 한 번에 조회 (루프 개별 조회 대신)
    List<SelfEvaluationFile> findBySelfEvaluation_SelfEvalIdIn(List<Long> selfEvalIds);
}
