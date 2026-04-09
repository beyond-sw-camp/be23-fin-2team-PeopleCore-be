package com.peoplecore.approval.service;

import com.peoplecore.approval.dto.DeptFolderCreateRequest;
import com.peoplecore.approval.dto.DeptFolderReorderRequest;
import com.peoplecore.approval.dto.DeptFolderResponse;
import com.peoplecore.approval.dto.DeptFolderUpdateRequest;
import com.peoplecore.approval.entity.DeptApprovalFolder;
import com.peoplecore.approval.entity.DeptFolderManager;
import com.peoplecore.approval.repository.ApprovalDocumentRepository;
import com.peoplecore.approval.repository.DeptApprovalFolderRepository;
import com.peoplecore.approval.repository.DeptFolderManagerRepository;
import com.peoplecore.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@Transactional(readOnly = true)
public class DeptApprovalFolderService {

    private final DeptApprovalFolderRepository folderRepository;
    private final DeptFolderManagerRepository managerRepository;
    private final ApprovalDocumentRepository documentRepository;

    @Autowired
    public DeptApprovalFolderService(DeptApprovalFolderRepository folderRepository,
                                     DeptFolderManagerRepository managerRepository,
                                     ApprovalDocumentRepository documentRepository) {
        this.folderRepository = folderRepository;
        this.managerRepository = managerRepository;
        this.documentRepository = documentRepository;
    }

    /**  부서 문서함 목록 조회 */
    public List<DeptFolderResponse> getList(UUID companyId, Long deptId) {
        List<DeptApprovalFolder> folders = folderRepository.findByCompanyIdAndDeptIdOrderBySortOrder(companyId, deptId);

        return folders.stream()
                .map(folder -> {
                    List<DeptFolderManager> managers = managerRepository.findByDeptAppFolderIdAndCompanyId(
                            folder.getDeptAppFolderId(), companyId);
                    int docCount = documentRepository.countByDeptFolderIdAndCompanyId(folder.getDeptAppFolderId(), companyId);
                    return DeptFolderResponse.from(folder, docCount, managers);
                })
                .toList();
    }

    /** 부서 문서함 생성 */
    @Transactional
    public DeptFolderResponse create(UUID companyId, Long deptId, Long empId, DeptFolderCreateRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new BusinessException("문서함 이름을 입력해주세요.", HttpStatus.BAD_REQUEST);
        }

        if (folderRepository.existsByCompanyIdAndDeptIdAndFolderName(companyId, deptId, request.getName())) {
            throw new BusinessException("동일한 이름의 문서함이 이미 존재합니다.", HttpStatus.CONFLICT);
        }

        Integer maxSortOrder = folderRepository.findMaxSortOrder(companyId, deptId);

        DeptApprovalFolder folder = folderRepository.save(DeptApprovalFolder.builder()
                .companyId(companyId)
                .deptId(deptId)
                .empId(empId)
                .folderName(request.getName())
                .sortOrder(maxSortOrder + 1)
                .build());

        return DeptFolderResponse.from(folder, 0, List.of());
    }

    /** 부서 문서함 수정 (이름 변경) */
    @Transactional
    public DeptFolderResponse update(UUID companyId, Long folderId, DeptFolderUpdateRequest request) {
        DeptApprovalFolder folder = findFolder(companyId, folderId);

        if (request.getName() == null || request.getName().isBlank()) {
            throw new BusinessException("문서함 이름을 입력해주세요.", HttpStatus.BAD_REQUEST);
        }

        if (!folder.getFolderName().equals(request.getName())
                && folderRepository.existsByCompanyIdAndDeptIdAndFolderName(companyId, folder.getDeptId(), request.getName())) {
            throw new BusinessException("동일한 이름의 문서함이 이미 존재합니다.", HttpStatus.CONFLICT);
        }

        folder.updateName(request.getName());

        List<DeptFolderManager> managers = managerRepository.findByDeptAppFolderIdAndCompanyId(folderId, companyId);
        int docCount = documentRepository.countByDeptFolderIdAndCompanyId(folderId, companyId);
        return DeptFolderResponse.from(folder, docCount, managers);
    }

    /** 부서 문서함 삭제 */
    @Transactional
    public void delete(UUID companyId, Long folderId) {
        DeptApprovalFolder folder = findFolder(companyId, folderId);

        int docCount = documentRepository.countByDeptFolderIdAndCompanyId(folderId, companyId);
        if (docCount > 0) {
            throw new BusinessException("문서가 존재하는 문서함은 삭제할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }

        managerRepository.deleteByDeptAppFolderId(folderId);
        folderRepository.delete(folder);
    }

    /*순서 일괄 변경 */
    @Transactional
    public List<DeptFolderResponse> reorder(UUID companyId, Long deptId, DeptFolderReorderRequest request) {
        for (DeptFolderReorderRequest.ReorderItem item : request.getOrderList()) {
            DeptApprovalFolder folder = findFolder(companyId, item.getId());
            folder.updateSortOrder(item.getSortOrder());
        }

        return getList(companyId, deptId);
    }

    /**담당자 추가 */
    @Transactional
    public DeptFolderResponse.ManagerInfo addManager(UUID companyId, Long folderId,
                                                      Long empId, String empName, String deptName) {
        findFolder(companyId, folderId);

        if (managerRepository.existsByDeptAppFolderIdAndEmpId(folderId, empId)) {
            throw new BusinessException("이미 등록된 담당자입니다.", HttpStatus.CONFLICT);
        }

        DeptFolderManager manager = managerRepository.save(DeptFolderManager.builder()
                .deptAppFolderId(folderId)
                .companyId(companyId)
                .empId(empId)
                .empName(empName)
                .deptName(deptName)
                .build());

        return DeptFolderResponse.ManagerInfo.builder()
                .empId(manager.getEmpId())
                .empName(manager.getEmpName())
                .deptName(manager.getDeptName())
                .build();
    }

    /*담당자 삭제 */
    @Transactional
    public void removeManager(UUID companyId, Long folderId, Long empId) {
        findFolder(companyId, folderId);

        if (!managerRepository.existsByDeptAppFolderIdAndEmpId(folderId, empId)) {
            throw new BusinessException("해당 담당자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        managerRepository.deleteByDeptAppFolderIdAndEmpId(folderId, empId);
    }

    /** 공통: 문서함 조회 (회사 격리) */
    private DeptApprovalFolder findFolder(UUID companyId, Long folderId) {
        return folderRepository.findByDeptAppFolderIdAndCompanyId(folderId, companyId)
                .orElseThrow(() -> new BusinessException("문서함을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
    }
}
