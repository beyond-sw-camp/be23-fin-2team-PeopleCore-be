package com.peoplecore.vacation.entity;

import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/* 휴가 신청 상태 - VacationRequest.requestStatus 컬럼 */
/* 상태 패턴: */
/*   allowedNext       - 전이 규칙 */
/*   kafkaTransitionTo - Kafka 결재 결과 반영 시 Balance/Ledger (PENDING→APPROVED/REJECTED/CANCELED) */
/*   cancelFrom        - 관리자 직권 취소 시 Balance/Ledger (PENDING/APPROVED 에서만) */
public enum RequestStatus {

    /* 결재 진행 중 - balance.pendingDays 반영 */
    PENDING {
        @Override public Set<RequestStatus> allowedNext() {
            return EnumSet.of(APPROVED, REJECTED, CANCELED);
        }

        /* Kafka 전이: PENDING → APPROVED(consume+USED) / REJECTED(releasePending) / CANCELED(releasePending, 기안자 회수) */
        /* to 가 allowedNext 에 없으면 INVALID_REQUEST_STATUS_TRANSITION */
        @Override
        public Optional<VacationLedger> kafkaTransitionTo(RequestStatus to,
                                                          VacationBalance balance, BigDecimal useDays,
                                                          Long requestId, Long managerId, String rejectReason) {
            if (!canTransitionTo(to)) {
                throw new CustomException(ErrorCode.INVALID_REQUEST_STATUS_TRANSITION);
            }
            return switch (to) {
                case APPROVED -> {
                    BigDecimal before = balance.getTotalDays();
                    balance.consume(useDays);                          // pending → used
                    BigDecimal after = balance.getTotalDays();
                    yield Optional.of(VacationLedger.ofUsed(
                            balance, useDays, before, after, requestId, managerId));
                }
                case REJECTED, CANCELED -> {
                    balance.releasePending(useDays);                   // pending 해제 (Ledger 미기록)
                    yield Optional.empty();
                }
                default -> throw new CustomException(ErrorCode.INVALID_REQUEST_STATUS_TRANSITION);
            };
        }

        /* PENDING → CANCELED: pending 해제. Ledger 미기록 (확정 변동 아님) */
        @Override
        public Optional<VacationLedger> cancelFrom(VacationBalance balance, BigDecimal useDays,
                                                   Long requestId, Long actorId, String reason) {
            balance.releasePending(useDays);
            return Optional.empty();
        }
    },

    /* 결재 완료 - balance.usedDays 로 이동 */
    APPROVED {
        @Override public Set<RequestStatus> allowedNext() {
            return EnumSet.of(CANCELED);                               // 관리자 직권 취소만 가능
        }

        /* APPROVED → CANCELED: used 복원 + RESTORED ledger 기록 */
        @Override
        public Optional<VacationLedger> cancelFrom(VacationBalance balance, BigDecimal useDays,
                                                   Long requestId, Long actorId, String reason) {
            BigDecimal before = balance.getTotalDays();
            balance.restore(useDays);
            BigDecimal after = balance.getTotalDays();
            return Optional.of(VacationLedger.ofRestored(
                    balance, useDays, before, after, requestId, actorId, reason));
        }
    },

    /* 결재 반려 - 종결 */
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

    /* 종결 상태 */
    public boolean isTerminal() { return this == REJECTED || this == CANCELED; }

    /* Kafka 결재 결과 전이 - PENDING 만 override. */
    /* 그 외 상태(APPROVED/REJECTED/CANCELED)에서 호출되면 이중 수신이므로 Service 멱등 가드가 먼저 차단해야 함 (이중 방어) */
    public Optional<VacationLedger> kafkaTransitionTo(RequestStatus to,
                                                      VacationBalance balance, BigDecimal useDays,
                                                      Long requestId, Long managerId, String rejectReason) {
        throw new UnsupportedOperationException(
                "kafkaTransitionTo 는 PENDING 에서만 호출 - actual=" + this);
    }

    /* 취소 처리 - PENDING/APPROVED 만 override */
    /* 종결 상태(REJECTED/CANCELED) 에서 호출되면 예외 → Service 의 request.apply 에서 이미 차단되어야 함 (이중 방어) */
    public Optional<VacationLedger> cancelFrom(VacationBalance balance, BigDecimal useDays,
                                               Long requestId, Long actorId, String reason) {
        throw new UnsupportedOperationException(
                "cancelFrom 은 PENDING/APPROVED 에서만 호출 - actual=" + this);
    }
}
