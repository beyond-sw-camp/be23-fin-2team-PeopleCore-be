package com.peoplecore.title.controller;

import com.peoplecore.title.domain.Title;
import com.peoplecore.title.dto.InternalTitleResponseDto;
import com.peoplecore.title.repository.TitleRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/title")
public class InternalTitleController {
    private final TitleRepository titleRepository;

    public InternalTitleController(TitleRepository titleRepository) {
        this.titleRepository = titleRepository;
    }

    @GetMapping("/{titleId}")
    public ResponseEntity<InternalTitleResponseDto> getTitle(@PathVariable Long titleId) {
        Title title = titleRepository.findById(titleId)
                .orElseThrow(() -> new RuntimeException("직책을 찾을 수 없습니다."));
        return ResponseEntity.ok(InternalTitleResponseDto.from(title));
    }
}
