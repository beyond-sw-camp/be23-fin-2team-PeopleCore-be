package com.peoplecore.approval.service;

import com.peoplecore.alram.publiher.AlarmEventPublisher;
import com.peoplecore.approval.dto.PersonalFolderMoveRequest;
import com.peoplecore.approval.dto.PersonalFolderReorderRequest;
import com.peoplecore.approval.dto.PersonalFolderRequest;
import com.peoplecore.approval.dto.PersonalFolderResponse;
import com.peoplecore.approval.entity.PersonalApprovalFolder;
import com.peoplecore.approval.repository.ApprovalDocumentRepository;
import com.peoplecore.approval.repository.PersonalApprovalFolderRepository;
import com.peoplecore.event.AlarmEvent;
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
public class PersonalApprovalFolderService {
    private final PersonalApprovalFolderRepository folderRepository;
    private final ApprovalDocumentRepository documentRepository;
    private final AlarmEventPublisher alarmEventPublisher;

    @Autowired
    public PersonalApprovalFolderService(PersonalApprovalFolderRepository folderRepository, ApprovalDocumentRepository documentRepository, AlarmEventPublisher alarmEventPublisher) {
        this.folderRepository = folderRepository;
        this.documentRepository = documentRepository;
        this.alarmEventPublisher = alarmEventPublisher;
    }

    /* 개인 문서함 목록 조회 정렬 순서대로 조회 -> 각 문서함에 속한 문서 수도 같이 반환*/
    public List<PersonalFolderResponse> getList(UUID companyId, Long empId) {
        return folderRepository.findByCompanyIdAndEmpIdOrderBySortOrder(companyId, empId).stream().map(folder -> {
            int docCount = documentRepository.countByPersonalFolderIdAndCompanyId(folder.getPersonalFolderId(), companyId);
            return PersonalFolderResponse.from(folder, docCount);
        }).toList();
    }

    /* 개인 문서함 생성 정렬 순서는 기존 최대값 +1 로 부여  */
    @Transactional
    public PersonalFolderResponse create(UUID companyId, Long empId, PersonalFolderRequest request) {
        /* 이름 빈값 검증 */
        if (request.getName() == null || request.getName().isBlank()) {
            throw new BusinessException("문서함 이름을 입력해주세요 ", HttpStatus.BAD_REQUEST);
        }

        /*이름 중복 검증 */
        if (folderRepository.existsByCompanyIdAndEmpIdAndFolderName(companyId, empId, request.getName())) {
            throw new BusinessException("동일한 이름의 문서함이 이미 존재합니다. ", HttpStatus.CONFLICT);
        }

        /*정렬 순서 부여 */
        Integer maxSortOrder = folderRepository.findMaxSortOrder(companyId, empId);

        PersonalApprovalFolder folder = folderRepository.save(PersonalApprovalFolder.builder()
                .companyId(companyId)
                .empId(empId)
                .folderName(request.getName())
                .sortOrder(maxSortOrder + 1)
                .build());
        return PersonalFolderResponse.from(folder, 0);
    }


    /*개인 문서함 이름 수정 -> 소유자 검증 -> 이름 변경 -> 이름 중복 검증 후 변경 */
    public PersonalFolderResponse update(UUID companyId, Long empId, Long folderId, PersonalFolderRequest request) {
        /*문서함 본인 검증 */
        PersonalApprovalFolder folder = findFolderWithOwnerCheck(companyId, empId, folderId);

        /*이름 빈값 검증 */
        if (request.getName() == null || request.getName().isBlank()) {
            throw new BusinessException("문서함 이름을 입력해주세요. ", HttpStatus.BAD_REQUEST);
        }

        /*이름이 변경된 경우 중복 검증 */
        if (!folder.getFolderName().equals(request.getName()) && folderRepository.existsByCompanyIdAndEmpIdAndFolderName(companyId, empId, request.getName())) {
            throw new BusinessException("동일한 이름의 문서함이 이미 존재합니다. ", HttpStatus.CONFLICT);
        }

        folder.updateName(request.getName());

        int docCount = documentRepository.countByPersonalFolderIdAndCompanyId(folderId, companyId);
        return PersonalFolderResponse.from(folder, docCount);
    }

    /*개인 문서함 삭제 -> 소유자 검증 후 문서함 내 문서 있을 시 삭제 실패 반환 */
    @Transactional
    public void delete(UUID companyId, Long empId, Long folderId) {
        PersonalApprovalFolder folder = findFolderWithOwnerCheck(companyId, empId, folderId);
        /* 문서 있으면 삭제 실패 */
        int docCount = documentRepository.countByPersonalFolderIdAndCompanyId(folderId, companyId);

        if (docCount > 0) {
            throw new BusinessException("문서가 존재하는 문서함은 삭제할 수 없습니다. ", HttpStatus.BAD_REQUEST);
        }
        folderRepository.delete(folder);
    }


    /*순서 일괄ㄹ 변경 -> 프론트에서 순서 바꾼걸로 적용 */
    @Transactional
    public List<PersonalFolderResponse> reorder(UUID companyId, Long empId, PersonalFolderReorderRequest request) {
        for (PersonalFolderReorderRequest.ReorderItem item : request.getOrderList()) {
            PersonalApprovalFolder folder = findFolderWithOwnerCheck(companyId, empId, item.getId());
            folder.updateSortOrder(item.getSortOrder());
        }
        return getList(companyId, empId);
    }

    /* 문서 개별  이동 -> 선택한 문서들을 다른 개인 문서함으로 이동   -*/
    @Transactional
    public void moveDocuments(UUID companyId, Long empId, Long folderId, PersonalFolderMoveRequest request) {

        findFolderWithOwnerCheck(companyId, empId, folderId);
        if (request.getTargetFolderId() != null) {
            findFolderWithOwnerCheck(companyId, empId, request.getTargetFolderId());
        }
        documentRepository.updatePersonalFolderIdByDocIds(request.getTargetFolderId(), request.getDocIds(), companyId);
    }

    /*문서 전체 이동 -> 폴더에서 폴더로 */
    @Transactional
    public void moveAllDocuments(UUID companyId, Long empId, Long folderId, Long targetFolderId) {
        findFolderWithOwnerCheck(companyId, empId, folderId);
        if (targetFolderId != null) {
            findFolderWithOwnerCheck(companyId, empId, targetFolderId);
        }

        documentRepository.updatePersonalFolderIdByFolderId(targetFolderId, folderId, companyId);
    }

    /* 문서함 이관 -> 개인 문서함을 다른 사원에게 통째로 넘김  -> 대상 사원한테 알림 발송 */
    @Transactional
    public void transfer(UUID companyId, Long empId, Long folderId, Long targetEmpId) {
        PersonalApprovalFolder folder = findFolderWithOwnerCheck(companyId, empId, folderId);
        folder.transferTo(targetEmpId);

        /* 이관 대상 사원한테 알림 발송 */
        alarmEventPublisher.publisher(AlarmEvent.builder()
                .companyId(companyId)
                .alarmType("Approval")
                .alarmTitle("개인 문서함 이관")
                .alarmContent(folder.getFolderName() + "문서함이 이관되었습니다,")
                .alarmRefType("PersonalFolder")
                .alarmRefId(folderId)
                .empIds(List.of(targetEmpId))
                .build());
    }


    /*문서함 조회 (회사 ID, 소유자 검증 ->문서함 일치 검증  */
    private PersonalApprovalFolder findFolderWithOwnerCheck(UUID companyId, Long empId, Long folderId) {
        PersonalApprovalFolder folder = folderRepository.findByPersonalFolderIdAndCompanyId(folderId, companyId).orElseThrow(() -> new BusinessException("문서함을 찾을 수 없습니다, ", HttpStatus.NOT_FOUND));

        if (!folder.getEmpId().equals(empId)) {
            throw new BusinessException("본인의 문서함만 관리할 수 있습니다,. ", HttpStatus.FORBIDDEN);
        }
        return folder;
    }


}
