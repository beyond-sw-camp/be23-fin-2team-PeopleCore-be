package com.peoplecore.filevault.security;

import com.peoplecore.capability.service.CapabilityService;
import com.peoplecore.exception.BusinessException;
import com.peoplecore.filevault.entity.FileFolder;
import com.peoplecore.filevault.entity.FileItem;
import com.peoplecore.filevault.entity.FolderType;
import com.peoplecore.filevault.permission.repository.FileBoxAclRepository;
import com.peoplecore.filevault.permission.service.AdminCapabilityService;
import com.peoplecore.filevault.repository.FileFolderRepository;
import com.peoplecore.filevault.repository.FileItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 파일함 접근 정책.
 *
 * <p>스코프별 규칙:
 * <ul>
 *   <li>PERSONAL: 소유자만 읽기/쓰기. 타인의 개인함 읽기는 VIEW_OTHERS_PERSONAL 필요.</li>
 *   <li>COMPANY/DEPT: 생성은 Tier-1 AdminCapability, 모든 read/write/download/delete 는
 *       Tier-2 ACL ({@link com.peoplecore.filevault.permission.entity.FileBoxAcl}) 4-플래그 기준.
 *       ACL 행이 없으면 default-locked 로 모두 차단.</li>
 * </ul>
 */
@Component
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileVaultAccessPolicy {

    public static final String FILE_VIEW_OTHERS_PERSONAL = "FILE_VIEW_OTHERS_PERSONAL";

    private final FileFolderRepository folderRepository;
    private final FileItemRepository fileItemRepository;
    private final CapabilityService capabilityService;
    private final AdminCapabilityService adminCapabilityService;
    private final FileBoxAclRepository aclRepository;

    public void ensureCanCreateFolder(UUID companyId, Long gradeId, Long titleId, Long empId,
                                       FolderType type, Long parentFolderId) {
        if (parentFolderId == null) {
            ensureCanCreateRoot(companyId, gradeId, titleId, type);
            return;
        }
        FileFolder root = resolveRoot(parentFolderId);
        ensureCanWriteScope(empId, root);
    }

    public void ensureCanManageFolder(Long titleId, Long empId, FileFolder folder) {
        FileFolder root = folder.getParentFolderId() == null ? folder : resolveRoot(folder.getParentFolderId());
        ensureCanWriteScope(empId, root);
    }

    public void ensureCanReadFolder(Long titleId, Long empId, Long folderId) {
        FileFolder root = resolveRoot(folderId);
        if (root.getType() == FolderType.PERSONAL) {
            boolean isOwner = empId != null && empId.equals(root.getOwnerEmpId());
            if (isOwner) return;
            if (!capabilityService.hasCapability(titleId, FILE_VIEW_OTHERS_PERSONAL)) {
                throw forbidden("타인의 개인 파일함을 열람할 권한이 없습니다.");
            }
            return;
        }
        boolean ok = aclRepository.findByFolderIdAndEmpId(root.getId(), empId)
            .map(a -> a.isCanRead()).orElse(false);
        if (!ok) throw forbidden("이 파일함을 열람할 권한이 없습니다.");
    }

    public void ensureCanWriteFolder(Long titleId, Long empId, Long folderId) {
        FileFolder root = resolveRoot(folderId);
        ensureCanWriteScope(empId, root);
    }

    public void ensureCanManageFile(Long titleId, Long empId, FileItem file) {
        ensureCanWriteFolder(titleId, empId, file.getFolderId());
    }

    /**
     * 파일 다운로드 — COMPANY/DEPT 는 ACL canDownload, PERSONAL 은 read 와 동일 정책.
     */
    public void ensureCanDownloadFile(Long titleId, Long empId, FileItem file) {
        FileFolder root = resolveRoot(file.getFolderId());
        if (root.getType() == FolderType.PERSONAL) {
            ensureCanReadFolder(titleId, empId, file.getFolderId());
            return;
        }
        boolean ok = aclRepository.findByFolderIdAndEmpId(root.getId(), empId)
            .map(a -> a.isCanDownload()).orElse(false);
        if (!ok) throw forbidden("다운로드 권한이 없습니다.");
    }

    /** 휴지통 목록 필터링용 — 예외 던지지 않는 write-scope 체커. */
    public boolean canManageRoot(Long titleId, Long empId, FileFolder root) {
        try {
            ensureCanWriteScope(empId, root);
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

    /**
     * COMPANY/DEPT 파일함 생성 권한은 새 Tier-1 (AdminCapability) 기준으로 판단한다.
     * PERSONAL 은 사용자 직접 생성 불가 (시스템 자동 생성 only).
     */
    private void ensureCanCreateRoot(UUID companyId, Long gradeId, Long titleId, FolderType type) {
        switch (type) {
            case COMPANY:
            case DEPT:
                if (!adminCapabilityService.isAdmin(companyId, gradeId, titleId)) {
                    throw forbidden("파일함을 생성할 권한이 없습니다.");
                }
                break;
            case PERSONAL:
                throw forbidden("개인 파일함은 사용자가 직접 생성할 수 없습니다.");
        }
    }

    private void ensureCanWriteScope(Long empId, FileFolder root) {
        if (root.getType() == FolderType.PERSONAL) {
            if (empId == null || !empId.equals(root.getOwnerEmpId())) {
                throw forbidden("본인의 개인 파일함만 수정할 수 있습니다.");
            }
            return;
        }
        boolean ok = aclRepository.findByFolderIdAndEmpId(root.getId(), empId)
            .map(a -> a.isCanWrite()).orElse(false);
        if (!ok) throw forbidden("이 파일함에 쓰기 권한이 없습니다.");
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
