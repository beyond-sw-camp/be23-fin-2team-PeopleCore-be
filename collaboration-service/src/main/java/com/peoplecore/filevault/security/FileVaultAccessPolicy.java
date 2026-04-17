package com.peoplecore.filevault.security;

import com.peoplecore.capability.service.CapabilityService;
import com.peoplecore.client.component.HrCacheService;
import com.peoplecore.client.dto.TitleInfoResponse;
import com.peoplecore.exception.BusinessException;
import com.peoplecore.filevault.entity.FileFolder;
import com.peoplecore.filevault.entity.FileItem;
import com.peoplecore.filevault.entity.FolderType;
import com.peoplecore.filevault.repository.FileFolderRepository;
import com.peoplecore.filevault.repository.FileItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 파일함 접근 정책.
 *
 * <p>스코프별 규칙:
 * <ul>
 *   <li>PERSONAL: 소유자만 읽기/쓰기. 타인의 개인함 읽기는 VIEW_OTHERS_PERSONAL 필요.</li>
 *   <li>COMPANY: 쓰기는 WRITE_COMPANY_FOLDER. 읽기는 회사 내 전원 개방.</li>
 *   <li>DEPT 루트 생성: CREATE_DEPT_FOLDER.</li>
 *   <li>DEPT 관리(하위 생성/이름/이동/삭제): MANAGE_DEPT_FOLDER 또는 MANAGE_SUBTREE_DEPT_FOLDER.</li>
 * </ul>
 */
@Component
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileVaultAccessPolicy {

    public static final String FILE_CREATE_DEPT_FOLDER = "FILE_CREATE_DEPT_FOLDER";
    public static final String FILE_MANAGE_DEPT_FOLDER = "FILE_MANAGE_DEPT_FOLDER";
    public static final String FILE_MANAGE_SUBTREE_DEPT_FOLDER = "FILE_MANAGE_SUBTREE_DEPT_FOLDER";
    public static final String FILE_WRITE_COMPANY_FOLDER = "FILE_WRITE_COMPANY_FOLDER";
    public static final String FILE_VIEW_OTHERS_PERSONAL = "FILE_VIEW_OTHERS_PERSONAL";

    private final FileFolderRepository folderRepository;
    private final FileItemRepository fileItemRepository;
    private final CapabilityService capabilityService;
    private final HrCacheService hrCacheService;

    public void ensureCanCreateFolder(Long titleId, Long empId, FolderType type, Long parentFolderId) {
        if (parentFolderId == null) {
            ensureCanCreateRoot(titleId, type);
            return;
        }
        FileFolder root = resolveRoot(parentFolderId);
        ensureCanWriteScope(titleId, empId, root);
    }

    public void ensureCanManageFolder(Long titleId, Long empId, FileFolder folder) {
        FileFolder root = folder.getParentFolderId() == null ? folder : resolveRoot(folder.getParentFolderId());
        ensureCanWriteScope(titleId, empId, root);
    }

    public void ensureCanReadFolder(Long titleId, Long empId, Long folderId) {
        FileFolder root = resolveRoot(folderId);
        if (root.getType() == FolderType.PERSONAL) {
            boolean isOwner = empId != null && empId.equals(root.getOwnerEmpId());
            if (isOwner) return;
            if (!capabilityService.hasCapability(titleId, FILE_VIEW_OTHERS_PERSONAL)) {
                throw forbidden("타인의 개인 파일함을 열람할 권한이 없습니다.");
            }
        }
    }

    public void ensureCanWriteFolder(Long titleId, Long empId, Long folderId) {
        FileFolder root = resolveRoot(folderId);
        ensureCanWriteScope(titleId, empId, root);
    }

    public void ensureCanManageFile(Long titleId, Long empId, FileItem file) {
        ensureCanWriteFolder(titleId, empId, file.getFolderId());
    }

    /** 휴지통 목록 필터링용 — 예외 던지지 않는 write-scope 체커. */
    public boolean canManageRoot(Long titleId, Long empId, FileFolder root) {
        try {
            ensureCanWriteScope(titleId, empId, root);
            return true;
        } catch (BusinessException e) {
            return false;
        }
    }

    public FileItem loadFile(Long fileId) {
        return fileItemRepository.findById(fileId)
            .orElseThrow(() -> new BusinessException("파일을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
    }

    public FileFolder loadFolder(Long folderId) {
        return folderRepository.findById(folderId)
            .orElseThrow(() -> new BusinessException("폴더를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
    }

    private void ensureCanCreateRoot(Long titleId, FolderType type) {
        switch (type) {
            case COMPANY:
                if (!capabilityService.hasCapability(titleId, FILE_WRITE_COMPANY_FOLDER)) {
                    throw forbidden("전사 파일함을 생성할 권한이 없습니다.");
                }
                break;
            case DEPT:
                if (!capabilityService.hasCapability(titleId, FILE_CREATE_DEPT_FOLDER)) {
                    throw forbidden("부서 파일함을 생성할 권한이 없습니다.");
                }
                break;
            case PERSONAL:
                throw forbidden("개인 파일함은 사용자가 직접 생성할 수 없습니다.");
        }
    }

    private void ensureCanWriteScope(Long titleId, Long empId, FileFolder root) {
        switch (root.getType()) {
            case PERSONAL:
                if (empId == null || !empId.equals(root.getOwnerEmpId())) {
                    throw forbidden("본인의 개인 파일함만 수정할 수 있습니다.");
                }
                break;
            case COMPANY:
                if (!capabilityService.hasCapability(titleId, FILE_WRITE_COMPANY_FOLDER)) {
                    throw forbidden("전사 파일함을 수정할 권한이 없습니다.");
                }
                break;
            case DEPT:
                ensureDeptScopeMatches(titleId, root);
                break;
        }
    }

    /**
     * DEPT 루트 폴더에 대한 쓰기 권한 검증 (capability + 부서 매칭).
     * <ul>
     *   <li>title.deptId == null (전사 공용 직책): 모든 부서 폴더 통과</li>
     *   <li>MANAGE_DEPT_FOLDER: folder.deptId == title.deptId 일치 필수</li>
     *   <li>MANAGE_SUBTREE_DEPT_FOLDER: folder.deptId의 조상 체인에 title.deptId 포함 필수</li>
     * </ul>
     */
    private void ensureDeptScopeMatches(Long titleId, FileFolder root) {
        boolean hasExact = capabilityService.hasCapability(titleId, FILE_MANAGE_DEPT_FOLDER);
        boolean hasSubtree = capabilityService.hasCapability(titleId, FILE_MANAGE_SUBTREE_DEPT_FOLDER);
        if (!hasExact && !hasSubtree) {
            throw forbidden("부서 파일함을 수정할 권한이 없습니다.");
        }

        TitleInfoResponse title = titleId == null ? null : hrCacheService.getTitle(titleId);
        if (title == null) {
            throw forbidden("직책 정보를 확인할 수 없어 부서 파일함을 수정할 수 없습니다.");
        }
        // 전사 공용 직책 — 모든 부서 통과
        if (title.getDeptId() == null) return;

        Long folderDeptId = root.getDeptId();
        if (folderDeptId == null) {
            throw forbidden("부서 정보가 없는 부서 파일함입니다.");
        }

        if (hasExact && folderDeptId.equals(title.getDeptId())) return;

        if (hasSubtree) {
            List<Long> ancestors = hrCacheService.getDeptAncestors(folderDeptId);
            if (ancestors.contains(title.getDeptId())) return;
        }

        throw forbidden("해당 부서의 파일함을 수정할 권한이 없습니다.");
    }

    private FileFolder resolveRoot(Long folderId) {
        FileFolder current = loadFolder(folderId);
        int depth = 0;
        while (current.getParentFolderId() != null) {
            if (++depth > 64) {
                throw new BusinessException("폴더 구조가 비정상적으로 깊습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            current = loadFolder(current.getParentFolderId());
        }
        return current;
    }

    private BusinessException forbidden(String message) {
        return new BusinessException(message, HttpStatus.FORBIDDEN);
    }
}
