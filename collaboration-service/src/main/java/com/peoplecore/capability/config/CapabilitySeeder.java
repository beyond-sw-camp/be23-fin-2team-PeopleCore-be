package com.peoplecore.capability.config;

import com.peoplecore.capability.entity.Capability;
import com.peoplecore.capability.repository.CapabilityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 파일함 권한(Capability) 시스템 정의 코드 시드.
 *
 * <p>애플리케이션 기동 시 {@code collab_capability} 테이블에
 * 5개의 FILE_* 권한이 없으면 삽입한다 (멱등 upsert).</p>
 *
 * <p>코드가 source of truth 이고 DB 는 사본이므로, 권한 추가 시
 * {@link #SEED} 에 한 줄 추가 후 재배포하면 자동 반영된다.</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class CapabilitySeeder {

    private final CapabilityRepository capabilityRepository;

    private static final List<Capability> SEED = List.of(
        Capability.builder()
            .code("FILE_CREATE_DEPT_FOLDER")
            .description("소속 부서 내 공용 폴더 생성")
            .category("FILE")
            .scope("DEPT")
            .build(),
        Capability.builder()
            .code("FILE_MANAGE_DEPT_FOLDER")
            .description("소속 부서 공용 폴더 관리(수정/삭제/권한)")
            .category("FILE")
            .scope("DEPT")
            .build(),
        Capability.builder()
            .code("FILE_MANAGE_SUBTREE_DEPT_FOLDER")
            .description("하위 부서 공용 폴더까지 관리")
            .category("FILE")
            .scope("DEPT")
            .build(),
        Capability.builder()
            .code("FILE_WRITE_COMPANY_FOLDER")
            .description("전사 공용 폴더 쓰기/관리")
            .category("FILE")
            .scope("COMPANY")
            .build(),
        Capability.builder()
            .code("FILE_VIEW_OTHERS_PERSONAL")
            .description("타 사용자 개인 폴더 열람")
            .category("FILE")
            .scope("PERSONAL")
            .build()
    );

    @Bean
    public ApplicationRunner seedCapabilities() {
        return args -> {
            int inserted = 0;
            for (Capability c : SEED) {
                if (!capabilityRepository.existsById(c.getCode())) {
                    capabilityRepository.save(c);
                    inserted++;
                }
            }
            log.info("[CapabilitySeeder] seeded {} new capabilities (total defined: {})",
                inserted, SEED.size());
        };
    }
}
