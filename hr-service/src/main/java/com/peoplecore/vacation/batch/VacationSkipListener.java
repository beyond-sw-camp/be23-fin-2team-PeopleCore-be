package com.peoplecore.vacation.batch;

import com.peoplecore.attendance.entity.CommuteRecord;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.vacation.entity.VacationBalance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.SkipListener;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/* SkipListener - chunk Step 에서 skip 발생 시 item 식별자 + 예외 상세를 StepExecution.ExecutionContext 에 누적 */
/* BatchFailureListener.afterJob 가 꺼내서 Discord WARN 페이로드에 포함 → 로그 안 봐도 원인 추적 가능 */
/* 모든 vacation/attendance 배치 Step 에 공통 attach (item 타입 다양 → SkipListener<Object,Object>) */
@Component
@Slf4j
public class VacationSkipListener implements SkipListener<Object, Object> {

    /* StepExecution.ExecutionContext 저장 키 - BatchFailureListener 도 같은 키로 꺼냄 */
    public static final String SKIP_DETAILS_KEY = "skipDetails";

    /* Step 당 최대 보관 건수 - ExecutionContext DB 직렬화 부담 + Discord field 1024자 제한 보호 */
    /* 초과분은 카운트만 BatchFailureListener 가 skipCount-보관건수 차이로 인지 */
    private static final int MAX_SKIP_RECORDS_PER_STEP = 50;

    @Override
    public void onSkipInRead(Throwable t) {
        record(null, t, "READ");
    }

    @Override
    public void onSkipInWrite(Object item, Throwable t) {
        record(item, t, "WRITE");
    }

    @Override
    public void onSkipInProcess(Object item, Throwable t) {
        record(item, t, "PROCESS");
    }

    /* synchronized - 향후 multi-thread step 도입 시 ExecutionContext List 동시 쓰기 보호 */
    /* 현재는 단일 스레드라 무경합. 락 범위 좁아 성능 영향 무시 가능 */
    private synchronized void record(Object item, Throwable t, String phase) {
        String itemDesc = describeItem(item);
        log.error("[BatchSkip] phase={}, item={}, exception={}: {}",
                phase, itemDesc, t.getClass().getSimpleName(), t.getMessage(), t);

        StepContext ctx = StepSynchronizationManager.getContext();
        if (ctx == null) {
            // 정상 흐름에선 도달 X (skip 콜백은 step 실행 중에만 호출됨)
            return;
        }
        ExecutionContext ec = ctx.getStepExecution().getExecutionContext();

        @SuppressWarnings("unchecked")
        List<String> details = (List<String>) ec.get(SKIP_DETAILS_KEY);
        if (details == null) {
            details = new ArrayList<>();
            ec.put(SKIP_DETAILS_KEY, details);
        }
        if (details.size() < MAX_SKIP_RECORDS_PER_STEP) {
            details.add(formatSkip(itemDesc, t, phase));
        }
    }

    private static String formatSkip(String itemDesc, Throwable t, String phase) {
        String msg = t.getMessage() != null ? t.getMessage() : "(no message)";
        return phase + " " + itemDesc + " | " + t.getClass().getSimpleName() + ": " + msg;
    }

    /* item 타입별 식별자 추출. 새 item 타입 추가 시 분기 보강 */
    private static String describeItem(Object item) {
        if (item == null) return "(no item)";
        if (item instanceof Employee e) return "empId=" + e.getEmpId();
        if (item instanceof VacationBalance b) return "balanceId=" + b.getBalanceId();
        if (item instanceof CommuteRecord c) return "comRecId=" + c.getComRecId();
        return item.getClass().getSimpleName();
    }
}
