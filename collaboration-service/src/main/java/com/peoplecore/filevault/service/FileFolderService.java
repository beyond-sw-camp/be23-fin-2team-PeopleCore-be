package com.peoplecore.filevault.service;

import com.peoplecore.client.component.HrCacheService;
import com.peoplecore.client.dto.EmployeeSimpleResDto;
import com.peoplecore.exception.BusinessException;
import com.peoplecore.filevault.audit.AuditAction;
import com.peoplecore.filevault.audit.FileVaultAuditEvent;
import com.peoplecore.filevault.audit.ResourceType;
import com.peoplecore.filevault.dto.FolderCreateRequest;
import com.peoplecore.filevault.dto.FolderResponse;
import com.peoplecore.filevault.entity.FileFolder;
import com.peoplecore.filevault.entity.FolderType;
import com.peoplecore.filevault.permission.repository.FileBoxAclRepository;
import com.peoplecore.filevault.permission.service.FileBoxAclService;
import com.peoplecore.filevault.repository.FileFolderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class FileFolderService {

    private final FileFolderRepository folderRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final FileBoxAclService fileBoxAclService;
    private final FileBoxAclRepository fileBoxAclRepository;
    private final HrCacheService hrCacheService;

    public List<FolderResponse> listRootFolders(UUID companyId, FolderType type, Long empId) {
        List<FileFolder> roots = folderRepository
            .findByCompanyIdAndTypeAndParentFolderIdIsNullAndDeletedAtIsNull(companyId, type);
        if (type == FolderType.PERSONAL) {
            roots = roots.stream()
                .filter(f -> empId != null && empId.equals(f.getOwnerEmpId()))
                .toList();
        } else {
            // COMPANY/DEPT — ACL canRead 행이 있는 파일함만 노출 (default-locked)
            roots = roots.stream()
                .filter(f -> fileBoxAclRepository.findByFolderIdAndEmpId(f.getId(), empId)
                    .map(a -> a.isCanRead()).orElse(false))
                .toList();
        }
        return roots.stream().map(FolderResponse::from).toList();
    }

    public List<FolderResponse> listChildren(Long parentFolderId) {
        return folderRepository
            .findByParentFolderIdAndDeletedAtIsNull(parentFolderId)
            .stream()
            .map(FolderResponse::from)
            .toList();
    }

    public FolderResponse getFolder(Long folderId) {
        FileFolder folder = findActiveFolder(folderId);
        return FolderResponse.from(folder);
    }

    @Transactional
    public FolderResponse createFolder(UUID companyId, Long empId, FolderCreateRequest request) {
        if (request.getParentFolderId() != null) {
            findActiveFolder(request.getParentFolderId());
            if (folderRepository.existsByParentFolderIdAndNameAndDeletedAtIsNull(
                    request.getParentFolderId(), request.getName())) {
                throw new BusinessException("같은 이름의 폴더가 이미 존재합니다.", HttpStatus.CONFLICT);
            }
        } else {
            // 루트 파일함 — 같은 회사·같은 타입 내 동일명 금지 (ACL 가시성과 무관한 전역 네임스페이스)
            folderRepository
                .findByCompanyIdAndTypeAndParentFolderIdIsNullAndNameAndDeletedAtIsNull(
                    companyId, request.getType(), request.getName())
                .ifPresent(conflict -> {
                    throw new BusinessException(
                        buildDuplicateRootMessage(conflict), HttpStatus.CONFLICT);
                });
        }

        FileFolder folder = FileFolder.builder()
            .companyId(companyId)
            .name(request.getName())
            .type(request.getType())
            .parentFolderId(request.getParentFolderId())
            .deptId(request.getDeptId())
            .ownerEmpId(request.getType() == FolderType.PERSONAL ? empId : null)
            .createdBy(empId)
            .build();

        FileFolder saved = folderRepository.save(folder);

        // 파일함 루트(비-PERSONAL) 생성 시 Owner 자동 ACL — 4-플래그 모두 true
        if (saved.getParentFolderId() == null && saved.getType() != FolderType.PERSONAL) {
            fileBoxAclService.grantOwnerAcl(saved.getId(), empId);
        }

        eventPublisher.publishEvent(FileVaultAuditEvent.builder()
            .action(AuditAction.CREATE_FOLDER)
            .resourceType(ResourceType.FOLDER)
            .resourceId(saved.getId())
            .resourceName(saved.getName())
            .parentFolderId(saved.getParentFolderId())
            .parentName(lookupFolderName(saved.getParentFolderId()))
            .build());
        return FolderResponse.from(saved);
    }

    @Transactional
    public FolderResponse renameFolder(Long folderId, String newName) {
        FileFolder folder = findActiveFolder(folderId);
        String oldName = folder.getName();
        folder.rename(newName);
        eventPublisher.publishEvent(FileVaultAuditEvent.builder()
            .action(AuditAction.RENAME_FOLDER)
            .resourceType(ResourceType.FOLDER)
            .resourceId(folder.getId())
            .resourceName(newName)
            .parentFolderId(folder.getParentFolderId())
            .parentName(lookupFolderName(folder.getParentFolderId()))
            .changes(Map.of("from", oldName, "to", newName))
            .build());
        return FolderResponse.from(folder);
    }

    @Transactional
    public FolderResponse moveFolder(Long folderId, Long newParentFolderId) {
        FileFolder folder = findActiveFolder(folderId);
        if (newParentFolderId != null) {
            findActiveFolder(newParentFolderId);
        }
        Long oldParentId = folder.getParentFolderId();
        folder.moveTo(newParentFolderId);
        Map<String, Object> changes = new HashMap<>();
        changes.put("fromParentId", oldParentId);
        changes.put("toParentId", newParentFolderId);
        changes.put("fromParentName", lookupFolderName(oldParentId));
        changes.put("toParentName", lookupFolderName(newParentFolderId));
        eventPublisher.publishEvent(FileVaultAuditEvent.builder()
            .action(AuditAction.MOVE_FOLDER)
            .resourceType(ResourceType.FOLDER)
            .resourceId(folder.getId())
            .resourceName(folder.getName())
            .parentFolderId(newParentFolderId)
            .parentName(lookupFolderName(newParentFolderId))
            .changes(changes)
            .build());
        return FolderResponse.from(folder);
    }

    /**
     * 폴더/파일함 soft-delete.
     *
     * <p>파일함 ACL 은 의도적으로 보존한다 — 복원 시 멤버십이 유지되어야 하며,
     * {@code listRootFolders} 는 {@code deletedAt IS NULL} 필터로 삭제된 파일함을
     * 가시성에서 배제하므로 stale ACL 로 인한 정보 누출은 없다.</p>
     *
     * <p>TODO: 향후 hard-delete 경로가 생기면 {@code fileBoxAclRepository.deleteByFolderId(folderId)}
     * 호출을 추가해 고아 ACL row 를 정리해야 한다.</p>
     */
    @Transactional
    public void softDelete(Long folderId) {
        FileFolder folder = findActiveFolder(folderId);
        folder.softDelete();
        eventPublisher.publishEvent(FileVaultAuditEvent.builder()
            .action(AuditAction.SOFT_DELETE_FOLDER)
            .resourceType(ResourceType.FOLDER)
            .resourceId(folder.getId())
            .resourceName(folder.getName())
            .parentFolderId(folder.getParentFolderId())
            .parentName(lookupFolderName(folder.getParentFolderId()))
            .build());
    }

    @Transactional
    public void restore(Long folderId) {
        FileFolder folder = folderRepository.findById(folderId)
            .orElseThrow(() -> new BusinessException("폴더를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        folder.restore();
        eventPublisher.publishEvent(FileVaultAuditEvent.builder()
            .action(AuditAction.RESTORE_FOLDER)
            .resourceType(ResourceType.FOLDER)
            .resourceId(folder.getId())
            .resourceName(folder.getName())
            .parentFolderId(folder.getParentFolderId())
            .parentName(lookupFolderName(folder.getParentFolderId()))
            .build());
    }

    /**
     * 사원의 PERSONAL 루트 파일함을 멱등하게 보장.
     * CDC 누락·신규 사원 등으로 루트가 없을 때 FE가 호출하는 자가치유 경로.
     */
    @Transactional
    public FolderResponse ensurePersonalRoot(UUID companyId, Long empId, String displayName) {
        return folderRepository.findByOwnerEmpIdAndTypeAndDeletedAtIsNull(empId, FolderType.PERSONAL)
            .filter(f -> f.getParentFolderId() == null)
            .map(FolderResponse::from)
            .orElseGet(() -> {
                String safeName = (displayName == null || displayName.isBlank()) ? "개인" : displayName;
                FileFolder created = createSystemDefaultFolder(
                    companyId, safeName + "의 파일함", FolderType.PERSONAL, null, empId, empId);
                log.info("PERSONAL 루트 자가치유 생성 empId={}, folderId={}", empId, created.getId());
                return FolderResponse.from(created);
            });
    }

    @Transactional
    public FileFolder createSystemDefaultFolder(UUID companyId, String name, FolderType type,
                                                 Long deptId, Long ownerEmpId, Long createdBy) {
        FileFolder saved = folderRepository.save(
            FileFolder.builder()
                .companyId(companyId)
                .name(name)
                .type(type)
                .deptId(deptId)
                .ownerEmpId(ownerEmpId)
                .createdBy(createdBy)
                .isSystemDefault(true)
                .build()
        );
        eventPublisher.publishEvent(FileVaultAuditEvent.builder()
            .action(AuditAction.CREATE_FOLDER)
            .resourceType(ResourceType.FOLDER)
            .resourceId(saved.getId())
            .resourceName(saved.getName())
            .parentFolderId(null)
            .parentName("(루트)")
            .metadata(Map.of("isSystemDefault", true, "folderType", type.name()))
            .build());
        return saved;
    }

    private FileFolder findActiveFolder(Long folderId) {
        FileFolder folder = folderRepository.findById(folderId)
            .orElseThrow(() -> new BusinessException("폴더를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        if (folder.isDeleted()) {
            throw new BusinessException("삭제된 폴더입니다.", HttpStatus.GONE);
        }
        return folder;
    }

    /**
     * 루트 파일함 이름 중복 시 Owner 이름까지 포함한 안내 메시지.
     * ACL 상 보이지 않는 파일함과 충돌해도 사용자가 원인을 인지할 수 있게 한다.
     * 시스템 기본 파일함(createdBy=0)은 Owner 표기 생략.
     */
    private String buildDuplicateRootMessage(FileFolder conflict) {
        Long ownerEmpId = conflict.getCreatedBy();
        if (ownerEmpId == null || ownerEmpId == 0L) {
            return "이미 사용 중인 파일함 이름입니다. 다른 이름을 사용해 주세요.";
        }
        String ownerName = null;
        try {
            List<EmployeeSimpleResDto> emps = hrCacheService.getEmployees(List.of(ownerEmpId));
            if (!emps.isEmpty()) ownerName = emps.get(0).getEmpName();
        } catch (Exception e) {
            log.warn("Owner 이름 조회 실패 empId={}, err={}", ownerEmpId, e.getMessage());
        }
        if (ownerName == null || ownerName.isBlank()) {
            return "이미 사용 중인 파일함 이름입니다. 다른 이름을 사용해 주세요.";
        }
        return String.format(
            "이미 사용 중인 이름입니다 (Owner: %s). 다른 이름을 사용하거나 Owner에게 접근 권한을 요청해 주세요.",
            ownerName);
    }

    /**
     * 감사 로그 위치 표시용 — 부모 폴더 이름 스냅샷.
     * null 이면 "(루트)" 로 기록한다.
     */
    private String lookupFolderName(Long folderId) {
        if (folderId == null) return "(루트)";
        return folderRepository.findById(folderId)
            .map(FileFolder::getName)
            .orElse("(unknown)");
    }
}
