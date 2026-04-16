package com.peoplecore.vacation.entity;

import java.util.EnumSet;
import java.util.Set;

/* 휴가 신청 상태 - VacationRequest.requestStatus 컬럼 */
/* 전자결재 흐름과 연동 - collaboration ApprovalDocument 상태와 1:1 매핑 */
public enum RequestStatus {

    /* 결재 진행 중 - 결재선 통과 대기. balance.pendingDays 반영 */
    PENDING {
        @Override public Set<RequestStatus> allowedNext() {
            return EnumSet.of(APPROVED, REJECTED, CANCELED);
        }
    },

    /* 결재 완료 - balance.usedDays 로 이동, ledger USED 기록 */
    APPROVED {
        /* 사원/관리자 모두 취소 가능 → ledger RESTORED 기록 */
        @Override public Set<RequestStatus> allowedNext() {
            return EnumSet.of(CANCELED);
        }
    },

    /* 결재 반려 - balance.pendingDays 복원, 종결 (관리자 직권 외 전이 불가) */
    REJECTED {
        @Override public Set<RequestStatus> allowedNext() {
            return EnumSet.noneOf(RequestStatus.class);
        }
    },

    /* 사원/관리자 취소 - 종결 */
    CANCELED {
        @Override public Set<RequestStatus> allowedNext() {
            return EnumSet.noneOf(RequestStatus.class);
        }
    };

    /* 정상 전이 가능한 다음 상태 목록 */
    public abstract Set<RequestStatus> allowedNext();

    /* 사원 셀프 전이 가능 여부 */
    public boolean canTransitionTo(RequestStatus next) {
        return allowedNext().contains(next);
    }

    /* 종결 상태 - APPROVED 는 CANCELED 가능하므로 종결 아님 */
    public boolean isTerminal() { return this == REJECTED || this == CANCELED; }
}