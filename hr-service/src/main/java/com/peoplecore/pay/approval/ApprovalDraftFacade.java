package com.peoplecore.pay.approval;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

    public ApprovalDraftResDto draft(UUID companyId, Long userId, ApprovalFormType type, Long ledgerId){
        return switch (type){
            case SALARY -> payrollApprovalDraftService.draft(companyId, userId, ledgerId);
            case RETIREMENT -> severanceApprovalDraftService.draft(companyId,userId, ledgerId);
        };
    }
}
