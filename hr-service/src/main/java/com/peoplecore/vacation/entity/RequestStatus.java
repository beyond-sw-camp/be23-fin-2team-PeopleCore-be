package com.peoplecore.vacation.entity;

import com.peoplecore.vacation.entity.VacationBalance;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/* 휴가 신청 상태 - VacationRequest.requestStatus 컬럼 */
/* 상태 패턴: */
/*   allowedNext - 전이 규칙 */
/*   applyKafkaResult - Kafka 결재 결과 반영 시 Balance/Ledger (PENDING→APPROVED/REJECTED) */
/*   cancelFrom - 취소 시 Balance/Ledger (PENDING/APPROVED 에서만. 종결 상태는 throw) */
public enum RequestStatus {

    /* 결재 진행 중 - balance.pendingDays 반영 */
    PENDING {
        @Override public Set<RequestStatus> allowedNext() {
            return EnumSet.of(APPROVED, REJECTED, CANCELED);
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
            return EnumSet.of(CANCELED);
        }
        @Override
        public Optional<VacationLedger> applyKafkaResult(VacationBalance balance, BigDecimal useDays,
                                                         Long requestId, Long managerId) {
            BigDecimal before = balance.getTotalDays();
            balance.consume(useDays);
            BigDecimal after = balance.getTotalDays();
            return Optional.of(VacationLedger.ofUsed(balance, useDays, before, after, requestId, managerId));
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

    /* 결재 반려 - balance.pendingDays 복원, 종결 */
    REJECTED {
        @Override public Set<RequestStatus> allowedNext() {
            return EnumSet.noneOf(RequestStatus.class);
        }
        @Override
        public Optional<VacationLedger> applyKafkaResult(VacationBalance balance, BigDecimal useDays,
                                                         Long requestId, Long managerId) {
            balance.releasePending(useDays);
            return Optional.empty();
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

    /* Kafka 결재 결과 - APPROVED/REJECTED 만 override */
    public Optional<VacationLedger> applyKafkaResult(VacationBalance balance, BigDecimal useDays,
                                                     Long requestId, Long managerId) {
        throw new UnsupportedOperationException(
                "applyKafkaResult 는 Kafka 진입 상태(APPROVED/REJECTED) 에서만 호출 - actual=" + this);
    }

    /* 취소 처리 - PENDING/APPROVED 만 override */
    /* 종결 상태(REJECTED/CANCELED) 에서 호출되면 예외 → Service 의 request.apply 에서 이미 차단되어야 함 (이중 방어) */
    public Optional<VacationLedger> cancelFrom(VacationBalance balance, BigDecimal useDays,
                                               Long requestId, Long actorId, String reason) {
        throw new UnsupportedOperationException(
                "cancelFrom 은 PENDING/APPROVED 에서만 호출 - actual=" + this);
    }
}