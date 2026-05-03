package com.peoplecore.title.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.company.domain.Company;
import com.peoplecore.event.TitleUpdatedEvent;
import com.peoplecore.title.domain.Title;
import com.peoplecore.title.dto.TitleCreateRequest;
import com.peoplecore.title.dto.TitleOrderRequest;
import com.peoplecore.title.dto.TitleResponse;
import com.peoplecore.title.dto.TitleUpdateRequest;
import com.peoplecore.title.repository.TitleRepository;
import com.peoplecore.employee.repository.EmployeeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@Transactional
public class TitleService {
    private final TitleRepository titleRepository;
    private final EmployeeRepository employeeRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private static final String TOPIC_TITLE_UPDATED = "hr-title-updated";

    public TitleService(TitleRepository titleRepository,
                        EmployeeRepository employeeRepository,
                        KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.titleRepository = titleRepository;
        this.employeeRepository = employeeRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public List<TitleResponse> getTitles(UUID companyId) {
        return titleRepository.findAllByCompanyId(companyId)
                .stream()
                .sorted(Comparator
                        .comparingInt((Title t) -> t.getTitleOrder() != null ? t.getTitleOrder() : 0)
                        .thenComparingLong(Title::getTitleId))
                .map(TitleResponse::from)
                .toList();
    }

    public TitleResponse createTitle(UUID companyId, TitleCreateRequest request) {
        if (titleRepository.existsByTitleNameAndCompanyId(request.getTitleName(), companyId)) {
            throw new IllegalArgumentException("이미 존재하는 직책명입니다.");
        }

        String titleCode = (request.getTitleCode() != null && !request.getTitleCode().isBlank())
                ? request.getTitleCode().trim()
                : nextTitleCode(companyId);

        int nextOrder = nextTitleOrder(companyId);

        Title title = Title.builder()
                .companyId(companyId)
                .titleName(request.getTitleName())
                .titleCode(titleCode)
                .titleOrder(nextOrder)
                .build();

        Title saved = titleRepository.save(title);

        return TitleResponse.from(saved);
    }

    public TitleResponse updateTitle(UUID companyId, Long titleId, TitleUpdateRequest request) {
        Title title = titleRepository.findById(titleId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 직책입니다."));

        if (!title.getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다.");
        }

        if (titleRepository.existsByTitleNameAndCompanyIdAndTitleIdNot(
                request.getTitleName(), companyId, titleId)) {
            throw new IllegalArgumentException("이미 존재하는 직책명입니다.");
        }

        title.update(request.getTitleName());

        publishTitleUpdatedEvent(titleId);

        return TitleResponse.from(title);
    }

    public void deleteTitle(UUID companyId, Long titleId) {
        Title title = titleRepository.findById(titleId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 직책입니다."));

        if (!title.getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다.");
        }

        if (employeeRepository.existsByTitle(title)) {
            throw new IllegalStateException("해당 직책을 사용 중인 직원이 있어 삭제할 수 없습니다.");
        }

        titleRepository.delete(title);

        publishTitleUpdatedEvent(titleId);
    }

    private String nextTitleCode(UUID companyId) {
        return titleRepository.findAllByCompanyId(companyId).stream()
                .map(Title::getTitleCode)
                .filter(code -> code != null && code.matches("\\d+"))
                .max(Comparator.naturalOrder())
                .map(code -> String.format("%03d", Integer.parseInt(code) + 1))
                .orElse("001");
    }

    private int nextTitleOrder(UUID companyId) {
        return titleRepository.findAllByCompanyId(companyId).stream()
                .map(Title::getTitleOrder)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .map(max -> max + 1)
                .orElse(1);
    }

    public void updateOrder(UUID companyId, TitleOrderRequest request) {
        List<Long> titleIds = request.getTitleIds();
        if (titleIds == null || titleIds.isEmpty()) return;

        for (int i = 0; i < titleIds.size(); i++) {
            Title title = titleRepository.findById(titleIds.get(i))
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 직책입니다."));

            if (!title.getCompanyId().equals(companyId)) {
                throw new IllegalArgumentException("접근 권한이 없습니다.");
            }

            title.updateOrder(i + 1);
        }
    }

    /* 직책 변경 이벤트 kafka로 발행 (실패해도 메인 로직에 영향 없음) */
    private void publishTitleUpdatedEvent(Long titleId) {
        try {
            String message = objectMapper.writeValueAsString(new TitleUpdatedEvent(titleId));
            kafkaTemplate.send(TOPIC_TITLE_UPDATED, String.valueOf(titleId), message);
            log.info("직책 변경 이벤트 발행 완료 topic = {}, titleId = {}", TOPIC_TITLE_UPDATED, titleId);
        } catch (JsonProcessingException e) {
            log.error("직책 변경 이벤트 직렬화 실패 titleId = {}, error = {}", titleId, e.getMessage());
        } catch (Exception e) {
            log.error("직책 변경 이벤트 발행 실패 titleId = {}, error = {}", titleId, e.getMessage());
        }
    }


    //superAdmin 계정 생성시 초기값
    public void initDefault(Company company) {
        titleRepository.save(
            Title.builder()
                    .companyId(company.getCompanyId())
                    .titleName("미배정")
                    .titleCode("000")
                    .build()
        );
    }

}
