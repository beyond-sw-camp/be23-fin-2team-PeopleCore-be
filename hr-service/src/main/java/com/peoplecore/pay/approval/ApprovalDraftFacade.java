package com.peoplecore.pay.approval;

import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ApprovalDraftFacade {

    private final PayrollApprovalDraftService payrollApprovalDraftService;
    private final SeveranceApprovalDraftService severanceApprovalDraftService;

    @Autowired
    public ApprovalDraftFacade(PayrollApprovalDraftService payrollApprovalDraftService, SeveranceApprovalDraftService severanceApprovalDraftService) {
        this.payrollApprovalDraftService = payrollApprovalDraftService;
        this.severanceApprovalDraftService = severanceApprovalDraftService;
    }

    public ApprovalDraftResDto draft(UUID companyId, Long userId, ApprovalFormType type, Long ledgerId,
                                     List<Long> sevIds){
        return switch (type){
            case SALARY -> {
                if (ledgerId == null) throw new CustomException(ErrorCode.INVALID_REQUEST);
                yield payrollApprovalDraftService.draft(companyId, userId, ledgerId);
            }
            case RETIREMENT -> {
                if (sevIds == null || sevIds.isEmpty())
                    throw new CustomException(ErrorCode.INVALID_REQUEST);
                yield severanceApprovalDraftService.draft(companyId, userId, sevIds);
            }
        };
    }
}
