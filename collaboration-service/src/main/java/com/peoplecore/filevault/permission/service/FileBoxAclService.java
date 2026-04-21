package com.peoplecore.filevault.permission.service;

import com.peoplecore.client.component.HrCacheService;
import com.peoplecore.client.dto.EmployeeSimpleResDto;
import com.peoplecore.exception.BusinessException;
import com.peoplecore.filevault.entity.FileFolder;
import com.peoplecore.filevault.entity.FolderType;
import com.peoplecore.filevault.permission.dto.FileBoxAclAddRequest;
import com.peoplecore.filevault.permission.dto.FileBoxAclEntryResponse;
import com.peoplecore.filevault.permission.dto.FileBoxAclResponse;
import com.peoplecore.filevault.permission.dto.FileBoxAclUpdateRequest;
import com.peoplecore.filevault.permission.dto.MyFileBoxAclResponse;
import com.peoplecore.filevault.permission.entity.FileBoxAcl;
import com.peoplecore.filevault.permission.repository.FileBoxAclRepository;
import com.peoplecore.filevault.repository.FileFolderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 파일함 단위 ACL (Tier-2) 서비스.
 *
 * <p>대상은 항상 파일함 루트 (parent_folder_id IS NULL && type != PERSONAL).
 * Owner = root.createdBy. Owner 는 자동으로 4-플래그 모두 true 로 행을 보유한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class FileBoxAclService {

    private final FileBoxAclRepository aclRepository;
    private final FileFolderRepository folderRepository;
    private final HrCacheService hrCacheService;

    public FileBoxAclResponse get(Long folderId) {
        FileFolder root = ensureFileBoxRoot(folderId);
        Long ownerEmpId = root.getCreatedBy();

        List<FileBoxAcl> rows = aclRepository.findByFolderId(folderId);
        List<Long> empIds = rows.stream().map(FileBoxAcl::getEmpId).toList();
        Map<Long, EmployeeSimpleResDto> empMap = empIds.isEmpty() ? Map.of()
            : enrichEmployees(empIds);

        FileBoxAclEntryResponse owner = null;
        List<FileBoxAclEntryResponse> members = new ArrayList<>();
        for (FileBoxAcl row : rows) {
            FileBoxAclEntryResponse entry = toEntry(row, empMap.get(row.getEmpId()));
            members.add(entry);
            if (row.getEmpId().equals(ownerEmpId)) owner = entry;
        }

        return FileBoxAclResponse.builder()
            .folderId(root.getId())
            .folderName(root.getName())
            .owner(owner)
            .members(members)
            .build();
    }

    public MyFileBoxAclResponse me(Long folderId, Long empId) {
        FileFolder root = ensureFileBoxRoot(folderId);
        boolean isOwner = empId != null && empId.equals(root.getCreatedBy());
        Optional<FileBoxAcl> aclOpt = aclRepository.findByFolderIdAndEmpId(folderId, empId);
        if (aclOpt.isPresent()) {
            FileBoxAcl acl = aclOpt.get();
            return MyFileBoxAclResponse.builder()
                .folderId(folderId)
                .isOwner(isOwner)
                .canRead(acl.isCanRead())
                .canWrite(acl.isCanWrite())
                .canDownload(acl.isCanDownload())
                .canDelete(acl.isCanDelete())
                .build();
        }
        return MyFileBoxAclResponse.builder()
            .folderId(folderId)
            .isOwner(isOwner)
            .canRead(false)
            .canWrite(false)
            .canDownload(false)
            .canDelete(false)
            .build();
    }

    @Transactional
    public FileBoxAclEntryResponse add(Long folderId, Long ownerEmpId, FileBoxAclAddRequest request) {
        FileFolder root = ensureFileBoxRoot(folderId);
        ensureOwner(root, ownerEmpId);

        Long targetEmpId = request.getEmpId();
        if (targetEmpId.equals(root.getCreatedBy())) {
            throw new BusinessException("Owner 는 별도 추가할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
        if (aclRepository.existsByFolderIdAndEmpId(folderId, targetEmpId)) {
            throw new BusinessException("이미 권한이 부여된 사원입니다.", HttpStatus.CONFLICT);
        }

        boolean read = request.getCanRead() != null ? request.getCanRead() : true;
        boolean write = request.getCanWrite() != null ? request.getCanWrite() : false;
        boolean download = request.getCanDownload() != null ? request.getCanDownload() : true;
        boolean delete = request.getCanDelete() != null ? request.getCanDelete() : false;

        FileBoxAcl saved = aclRepository.save(FileBoxAcl.builder()
            .folderId(folderId)
            .empId(targetEmpId)
            .canRead(read)
            .canWrite(write)
            .canDownload(download)
            .canDelete(delete)
            .build());

        EmployeeSimpleResDto emp = enrichEmployees(List.of(targetEmpId)).get(targetEmpId);
        log.info("ACL 추가 folderId={}, empId={}", folderId, targetEmpId);
        return toEntry(saved, emp);
    }

    @Transactional
    public FileBoxAclEntryResponse update(Long folderId, Long targetEmpId, Long ownerEmpId, FileBoxAclUpdateRequest request) {
        FileFolder root = ensureFileBoxRoot(folderId);
        ensureOwner(root, ownerEmpId);
        if (targetEmpId.equals(root.getCreatedBy())) {
            throw new BusinessException("Owner 권한은 수정할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
        FileBoxAcl acl = aclRepository.findByFolderIdAndEmpId(folderId, targetEmpId)
            .orElseThrow(() -> new BusinessException("ACL 항목을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        acl.updateFlags(request.getCanRead(), request.getCanWrite(), request.getCanDownload(), request.getCanDelete());
        EmployeeSimpleResDto emp = enrichEmployees(List.of(targetEmpId)).get(targetEmpId);
        log.info("ACL 갱신 folderId={}, empId={}", folderId, targetEmpId);
        return toEntry(acl, emp);
    }

    @Transactional
    public void remove(Long folderId, Long targetEmpId, Long ownerEmpId) {
        FileFolder root = ensureFileBoxRoot(folderId);
        ensureOwner(root, ownerEmpId);
        if (targetEmpId.equals(root.getCreatedBy())) {
            throw new BusinessException("Owner 행은 삭제할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
        if (!aclRepository.existsByFolderIdAndEmpId(folderId, targetEmpId)) {
            throw new BusinessException("ACL 항목을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }
        aclRepository.deleteByFolderIdAndEmpId(folderId, targetEmpId);
        log.info("ACL 삭제 folderId={}, empId={}", folderId, targetEmpId);
    }

    /**
     * 파일함 생성 시 Owner 자동 ACL 행 삽입 — 4-플래그 모두 true.
     * {@link com.peoplecore.filevault.service.FileFolderService#createFolder} 에서 호출.
     */
    @Transactional
    public void grantOwnerAcl(Long folderId, Long ownerEmpId) {
        if (aclRepository.existsByFolderIdAndEmpId(folderId, ownerEmpId)) return;
        aclRepository.save(FileBoxAcl.builder()
            .folderId(folderId)
            .empId(ownerEmpId)
            .canRead(true)
            .canWrite(true)
            .canDownload(true)
            .canDelete(true)
            .build());
    }

    private FileFolder ensureFileBoxRoot(Long folderId) {
        FileFolder folder = folderRepository.findById(folderId)
            .orElseThrow(() -> new BusinessException("폴더를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        if (folder.getParentFolderId() != null) {
            throw new BusinessException("ACL 은 파일함 루트에서만 관리할 수 있습니다.", HttpStatus.BAD_REQUEST);
        }
        if (folder.getType() == FolderType.PERSONAL) {
            throw new BusinessException("개인 파일함은 ACL 대상이 아닙니다.", HttpStatus.BAD_REQUEST);
        }
        return folder;
    }

    private void ensureOwner(FileFolder root, Long actorEmpId) {
        if (actorEmpId == null || !actorEmpId.equals(root.getCreatedBy())) {
            throw new BusinessException("파일함 Owner 만 권한을 관리할 수 있습니다.", HttpStatus.FORBIDDEN);
        }
    }

    private Map<Long, EmployeeSimpleResDto> enrichEmployees(List<Long> empIds) {
        Map<Long, EmployeeSimpleResDto> map = new HashMap<>();
        try {
            for (EmployeeSimpleResDto emp : hrCacheService.getEmployees(empIds)) {
                map.put(emp.getEmpId(), emp);
            }
        } catch (Exception e) {
            log.warn("ACL 멤버 enrich 실패 — 이름 없이 반환 empIds={}, error={}", empIds, e.getMessage());
        }
        return map;
    }

    private FileBoxAclEntryResponse toEntry(FileBoxAcl acl, EmployeeSimpleResDto emp) {
        return FileBoxAclEntryResponse.builder()
            .empId(acl.getEmpId())
            .empName(emp != null ? emp.getEmpName() : null)
            .deptName(emp != null ? emp.getDeptName() : null)
            .gradeName(emp != null ? emp.getGradeName() : null)
            .titleName(emp != null ? emp.getTitleName() : null)
            .canRead(acl.isCanRead())
            .canWrite(acl.isCanWrite())
            .canDownload(acl.isCanDownload())
            .canDelete(acl.isCanDelete())
            .build();
    }
}
