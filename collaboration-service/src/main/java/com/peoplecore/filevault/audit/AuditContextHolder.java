package com.peoplecore.filevault.audit;

/**
 * 현재 스레드의 {@link AuditContext} 를 저장하는 ThreadLocal 홀더.
 *
 * <p>{@link AuditContextFilter} 가 HTTP 요청 진입 시 {@link #set(AuditContext)} 하고
 * 종료 시 {@link #clear()} 한다. CDC 컨슈머는 컨슈머 메서드 시작 시 직접 {@link #set} 한 뒤
 * try-finally 로 {@link #clear()} 한다.</p>
 */
public final class AuditContextHolder {

    private static final ThreadLocal<AuditContext> CONTEXT = new ThreadLocal<>();

    private AuditContextHolder() {}

    public static void set(AuditContext context) {
        CONTEXT.set(context);
    }

    public static AuditContext get() {
        return CONTEXT.get();
    }

    /**
     * 컨텍스트가 없으면 IllegalStateException. 비즈니스 메서드는 항상 컨텍스트가 설정된 상태에서 호출되어야 한다.
     */
    public static AuditContext require() {
        AuditContext ctx = CONTEXT.get();
        if (ctx == null) {
            throw new IllegalStateException("AuditContext is not set on current thread");
        }
        return ctx;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
