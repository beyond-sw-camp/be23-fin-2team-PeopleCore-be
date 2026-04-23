# 캘린더 백엔드 구현 가이드

> collaboration-service 모듈 내 `com.peoplecore.calendar` 패키지에 작성
> 기존 엔티티/Enum은 그대로 사용, **Repository → DTO → Service → Controller → Scheduler** 순서로 구현

---

## 목차

1. [엔티티 수정 사항](#1-엔티티-수정-사항)
2. [Repository](#2-repository)
3. [DTO (Request / Response)](#3-dto-request--response)
4. [Service](#4-service)
5. [Controller](#5-controller)
6. [예약 알림 스케줄러](#6-예약-알림-스케줄러)
7. [사원 정보 조회 (서비스 간 통신)](#7-사원-정보-조회-서비스-간-통신)
8. [전사 일정 관리 (Admin 전용)](#8-전사-일정-관리-admin-전용)
9. [참석자 초대 · 승인 · 거절](#9-참석자-초대--승인--거절)
10. [휴일 관리 (사내휴일 + 법정공휴일)](#10-휴일-관리-사내휴일--법정공휴일)
11. [파일 위치 요약](#11-파일-위치-요약)

---

## 알림 설계 원칙

캘린더 알림은 두 가지로 나뉘며, 전자결재와 동일한 Kafka 비동기 방식을 사용합니다.

| 구분 | 예시 | 처리 방식 | 이유 |
|------|------|-----------|------|
| **즉시 알림** | 공유 요청/승인/거절, 일정 초대, 전사 일정 등록 | `AlarmEventPublisher.publisher()` → Kafka → `AlarmService.createAndPush()` | 비동기 처리로 알림 실패가 비즈니스 로직에 영향 안 줌. 전자결재와 패턴 통일 |
| **예약 알림** | "10분 전 팝업", "1시간 전 이메일" | `@Scheduled` 스케줄러 | 특정 시각에 맞춰 발송해야 하므로 주기적 폴링 필요 |

> **Kafka 비동기 알림의 장점:**
> 1. 일정 생성 트랜잭션과 알림 발송이 분리 → 알림 실패해도 일정은 정상 저장
> 2. 전자결재(`ApprovalDocumentService` 등)와 동일한 `AlarmEventPublisher.publisher()` 패턴 사용
> 3. 흐름: 서비스 → `AlarmEventPublisher.publisher(AlarmEvent)` → Kafka 토픽 `"alarm-event"` → `AlarmEventConsumer` → `AlarmService.createAndPush()` → DB 저장 + SSE 푸시

---

## 1. 엔티티 수정 사항

기존 엔티티에 비즈니스 메서드 및 누락 필드를 추가합니다.

### 1-0. `MyCalendars.java` 수정 — 기본 캘린더 필드 추가

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/entity/MyCalendars.java`

> `isDefault` 필드를 추가하여 기본 캘린더 여부를 식별합니다.
> 기본 캘린더는 삭제/이름변경이 불가합니다.

```java
// ─── 기존 필드 아래에 추가 ───

@Column(nullable = false)
@Builder.Default
private Boolean isDefault = false;
```

> 기존 비즈니스 메서드에 기본 캘린더 체크 메서드도 추가:

```java
public boolean isDefaultCalendar() {
    return Boolean.TRUE.equals(this.isDefault);
}
```

---

### 1-1. `Events.java` 수정

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/entity/Events.java`

```java
package com.peoplecore.calendar.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Events extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long eventsId;

    @Column(nullable = false)
    private Long empId;

    @Column(nullable = false)
    private String title;

    private String description;
    private String location;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    private Boolean isAllDay;
    private Boolean isPublic;
    private LocalDateTime deletedAt;
    private Boolean isAllEmployees;

    @Column(nullable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "my_calendars_id")
    private MyCalendars myCalendars;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repeated_rules_id")
    private RepeatedRules repeatedRules;

    @OneToMany(mappedBy = "events", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<EventsNotifications> notifications = new ArrayList<>();

    // ── 비즈니스 메서드 ──

    public void update(String title, String description, String location,
                       LocalDateTime startAt, LocalDateTime endAt,
                       Boolean isAllDay, Boolean isPublic, MyCalendars myCalendars) {
        this.title = title;
        this.description = description;
        this.location = location;
        this.startAt = startAt;
        this.endAt = endAt;
        this.isAllDay = isAllDay;
        this.isPublic = isPublic;
        this.myCalendars = myCalendars;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
```

### 1-2. `MyCalendars.java` 수정

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/entity/MyCalendars.java`

```java
package com.peoplecore.calendar.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "my_calendars")
public class MyCalendars extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long myCalendarsId;

    @Column(nullable = false)
    private Long empId;

    @Column(nullable = false, length = 100)
    private String calendarName;

    @Column(length = 7)
    private String myDisplayColor;

    private Boolean isVisible;
    private Integer sortOrder;

    @Column(nullable = false)
    private UUID companyId;

    // ── 비즈니스 메서드 ──

    public void updateName(String calendarName) {
        this.calendarName = calendarName;
    }

    public void updateColor(String color) {
        this.myDisplayColor = color;
    }

    public void toggleVisible() {
        this.isVisible = !Boolean.TRUE.equals(this.isVisible);
    }

    public void updateSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
```

### 1-3. `InterestCalendars.java` 수정

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/entity/InterestCalendars.java`

```java
package com.peoplecore.calendar.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterestCalendars {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long viewerEmpId;
    private Long targetEmpId;
    private Boolean isVisible;

    @Column(length = 7)
    private String insDisplayColor;

    private Integer sortOrder;
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private CalendarShareRequests calendarShareRequest;

    // ── 비즈니스 메서드 ──

    public void updateColor(String color) {
        this.insDisplayColor = color;
    }

    public void toggleVisible() {
        this.isVisible = !Boolean.TRUE.equals(this.isVisible);
    }

    public void updateSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
```

### 1-4. `CalendarShareRequests.java` 수정

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/entity/CalendarShareRequests.java`

```java
package com.peoplecore.calendar.entity;

import com.peoplecore.calendar.enums.Permission;
import com.peoplecore.calendar.enums.ShareStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "calendar_share_requests")
public class CalendarShareRequests {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long calendarShareReqid;

    private Long fromEmpId;
    private Long toEmpId;

    @Enumerated(EnumType.STRING)
    private Permission permission;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ShareStatus shareStatus = ShareStatus.PENDING;

    private LocalDateTime requestedAt;
    private LocalDateTime respondedAt;

    @Column(nullable = false)
    private UUID companyId;

    // ── 비즈니스 메서드 ──

    public void approve() {
        this.shareStatus = ShareStatus.APPROVED;
        this.respondedAt = LocalDateTime.now();
    }

    public void reject() {
        this.shareStatus = ShareStatus.REJECTED;
        this.respondedAt = LocalDateTime.now();
    }

    public void cancel() {
        this.shareStatus = ShareStatus.CANCELLED;
        this.respondedAt = LocalDateTime.now();
    }
}
```

---

## 2. Repository

### 2-1. `EventsRepository.java` — 기본 JPA (단순 CRUD만)

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/repository/EventsRepository.java`

```java
package com.peoplecore.calendar.repository;

import com.peoplecore.calendar.entity.Events;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventsRepository extends JpaRepository<Events, Long>, EventsCustomRepository {
    // 복잡한 조회는 EventsCustomRepository(QueryDSL)에서 처리
}
```

### 2-1a. `EventsCustomRepository.java` — QueryDSL 인터페이스

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/repository/EventsCustomRepository.java`

```java
package com.peoplecore.calendar.repository;

import com.peoplecore.calendar.entity.Events;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface EventsCustomRepository {

    /** 내 캘린더에 속한 일정 조회 (기간 필터) */
    List<Events> findByCalendarIdsAndPeriod(List<Long> calendarIds, UUID companyId,
                                            LocalDateTime start, LocalDateTime end);

    /** 전사 일정 조회 */
    List<Events> findCompanyEvents(UUID companyId, LocalDateTime start, LocalDateTime end);

    /** 타인 공개 일정 조회 (관심 캘린더용) */
    List<Events> findPublicEventsByEmpId(Long targetEmpId, UUID companyId,
                                         LocalDateTime start, LocalDateTime end);

    /** 예약 알림 스케줄러용: 알림 설정이 있는 일정 조회 */
    List<Events> findEventsWithNotificationsBetween(LocalDateTime from, LocalDateTime to);
}
```

### 2-1b. `EventsCustomRepositoryImpl.java` — QueryDSL 구현체

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/repository/EventsCustomRepositoryImpl.java`

> 기존 `ApprovalDocumentCustomRepositoryImpl` 패턴을 따릅니다.
> BooleanExpression 방식 사용 (가이드 권장), fetchJoin으로 N+1 방지

```java
package com.peoplecore.calendar.repository;

import com.peoplecore.calendar.entity.Events;
import com.peoplecore.calendar.entity.QEvents;
import com.peoplecore.calendar.entity.QEventsNotifications;
import com.peoplecore.calendar.entity.QMyCalendars;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class EventsCustomRepositoryImpl implements EventsCustomRepository {

    private final JPAQueryFactory queryFactory;

    /* Q클래스 선언 */
    private final QEvents event = QEvents.events;
    private final QMyCalendars calendar = QMyCalendars.myCalendars;
    private final QEventsNotifications notification = QEventsNotifications.eventsNotifications;

    @Autowired
    public EventsCustomRepositoryImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    // ────────────────────────────────────────────
    // 내 캘린더에 속한 일정 조회
    // ────────────────────────────────────────────
    @Override
    public List<Events> findByCalendarIdsAndPeriod(List<Long> calendarIds, UUID companyId,
                                                    LocalDateTime start, LocalDateTime end) {
        return queryFactory
                .selectFrom(event)
                .join(event.myCalendars, calendar).fetchJoin()
                .leftJoin(event.notifications, notification).fetchJoin()
                .where(
                        calendar.myCalendarsId.in(calendarIds),
                        companyIdEq(companyId),
                        notDeleted(),
                        periodOverlap(start, end)
                )
                .orderBy(event.startAt.asc())
                .fetch();
    }

    // ────────────────────────────────────────────
    // 전사 일정 조회
    // ────────────────────────────────────────────
    @Override
    public List<Events> findCompanyEvents(UUID companyId,
                                          LocalDateTime start, LocalDateTime end) {
        return queryFactory
                .selectFrom(event)
                .leftJoin(event.notifications, notification).fetchJoin()
                .where(
                        companyIdEq(companyId),
                        event.isAllEmployees.isTrue(),
                        notDeleted(),
                        periodOverlap(start, end)
                )
                .orderBy(event.startAt.asc())
                .fetch();
    }

    // ────────────────────────────────────────────
    // 타인 공개 일정 조회 (관심 캘린더용)
    // ────────────────────────────────────────────
    @Override
    public List<Events> findPublicEventsByEmpId(Long targetEmpId, UUID companyId,
                                                 LocalDateTime start, LocalDateTime end) {
        return queryFactory
                .selectFrom(event)
                .join(event.myCalendars, calendar).fetchJoin()
                .leftJoin(event.notifications, notification).fetchJoin()
                .where(
                        calendar.empId.eq(targetEmpId),
                        companyIdEq(companyId),
                        event.isPublic.isTrue(),
                        notDeleted(),
                        periodOverlap(start, end)
                )
                .orderBy(event.startAt.asc())
                .fetch();
    }

    // ────────────────────────────────────────────
    // 예약 알림 스케줄러용
    // ────────────────────────────────────────────
    @Override
    public List<Events> findEventsWithNotificationsBetween(LocalDateTime from, LocalDateTime to) {
        return queryFactory
                .selectFrom(event).distinct()
                .join(event.notifications, notification).fetchJoin()
                .where(
                        notDeleted(),
                        event.startAt.between(from, to)
                )
                .fetch();
    }

    // ────────────────────────────────────────────
    // BooleanExpression 헬퍼 (null 반환 → where절 자동 무시)
    // ────────────────────────────────────────────

    private BooleanExpression companyIdEq(UUID companyId) {
        return companyId != null ? event.companyId.eq(companyId) : null;
    }

    private BooleanExpression notDeleted() {
        return event.deletedAt.isNull();
    }

    /**
     * 기간 겹침 조건: 일정이 조회 범위와 겹치는지 확인
     * (일정 시작 < 조회 끝) AND (일정 끝 > 조회 시작)
     */
    private BooleanExpression periodOverlap(LocalDateTime start, LocalDateTime end) {
        return event.startAt.lt(end).and(event.endAt.gt(start));
    }
}
```

### 2-2. `MyCalendarsRepository.java`

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/repository/MyCalendarsRepository.java`

```java
package com.peoplecore.calendar.repository;

import com.peoplecore.calendar.entity.MyCalendars;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MyCalendarsRepository extends JpaRepository<MyCalendars, Long> {

    List<MyCalendars> findByCompanyIdAndEmpIdOrderBySortOrderAsc(UUID companyId, Long empId);

    boolean existsByCompanyIdAndEmpIdAndCalendarName(UUID companyId, Long empId, String calendarName);
}
```

### 2-3. `InterestCalendarsRepository.java`

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/repository/InterestCalendarsRepository.java`

```java
package com.peoplecore.calendar.repository;

import com.peoplecore.calendar.entity.InterestCalendars;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface InterestCalendarsRepository extends JpaRepository<InterestCalendars, Long> {

    @Query("SELECT ic FROM InterestCalendars ic " +
           "JOIN FETCH ic.calendarShareRequest csr " +
           "WHERE ic.viewerEmpId = :empId " +
           "AND ic.companyId = :companyId " +
           "ORDER BY ic.sortOrder ASC")
    List<InterestCalendars> findByViewerEmpIdWithRequest(
            @Param("empId") Long empId,
            @Param("companyId") UUID companyId);

    boolean existsByCompanyIdAndViewerEmpIdAndTargetEmpId(UUID companyId, Long viewerEmpId, Long targetEmpId);
}
```

### 2-4. `CalendarShareRequestsRepository.java`

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/repository/CalendarShareRequestsRepository.java`

```java
package com.peoplecore.calendar.repository;

import com.peoplecore.calendar.entity.CalendarShareRequests;
import com.peoplecore.calendar.enums.ShareStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CalendarShareRequestsRepository extends JpaRepository<CalendarShareRequests, Long> {

    /** 내가 보낸 관심 캘린더 요청 목록 */
    Page<CalendarShareRequests> findByCompanyIdAndFromEmpIdOrderByRequestedAtDesc(
            UUID companyId, Long fromEmpId, Pageable pageable);

    /** 나에게 온 관심 캘린더 요청 목록 (내 일정을 보고 있는 동료) */
    Page<CalendarShareRequests> findByCompanyIdAndToEmpIdOrderByRequestedAtDesc(
            UUID companyId, Long toEmpId, Pageable pageable);

    /** 중복 요청 방지 */
    boolean existsByCompanyIdAndFromEmpIdAndToEmpIdAndShareStatus(
            UUID companyId, Long fromEmpId, Long toEmpId, ShareStatus status);
}
```

### 2-5. `EventsNotificationsRepository.java`

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/repository/EventsNotificationsRepository.java`

```java
package com.peoplecore.calendar.repository;

import com.peoplecore.calendar.entity.EventsNotifications;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventsNotificationsRepository extends JpaRepository<EventsNotifications, Long> {

    List<EventsNotifications> findByEvents_EventsId(Long eventsId);

    void deleteByEvents_EventsId(Long eventsId);
}
```

### 2-6. `RepeatedRulesRepository.java`

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/repository/RepeatedRulesRepository.java`

```java
package com.peoplecore.calendar.repository;

import com.peoplecore.calendar.entity.RepeatedRules;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepeatedRulesRepository extends JpaRepository<RepeatedRules, Long> {
}
```

### 2-7. `HolidaysRepository.java`

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/repository/HolidaysRepository.java`

```java
package com.peoplecore.calendar.repository;

import com.peoplecore.calendar.entity.EventsNotifications.Holidays;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface HolidaysRepository extends JpaRepository<Holidays, Long> {

    @Query("SELECT h FROM Holidays h " +
           "WHERE h.companyId = :companyId " +
           "AND ((h.isRepeating = true) OR (h.date BETWEEN :start AND :end))")
    List<Holidays> findByCompanyIdAndPeriod(
            @Param("companyId") UUID companyId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);
}
```

---

## 3. DTO (Request / Response)

### 3-1. 일정 관련

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/dto/`

#### `EventCreateRequest.java`

```java
package com.peoplecore.calendar.dto;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class EventCreateRequest {

    private String title;
    private String description;
    private String location;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Boolean isAllDay;
    private Boolean isPublic;
    private Long myCalendarsId;

    // 반복 설정 (null이면 비반복)
    private RepeatedRuleRequest repeatedRule;

    // 알림 설정
    private List<NotificationRequest> notifications;

    // 참석자
    private List<Long> attendeeEmpIds;
}
```

#### `EventUpdateRequest.java`

```java
package com.peoplecore.calendar.dto;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class EventUpdateRequest {

    private String title;
    private String description;
    private String location;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Boolean isAllDay;
    private Boolean isPublic;
    private Long myCalendarsId;

    private List<NotificationRequest> notifications;
}
```

#### `RepeatedRuleRequest.java`

```java
package com.peoplecore.calendar.dto;

import com.peoplecore.calendar.enums.Frequency;
import lombok.Getter;

import java.time.LocalDate;

@Getter
public class RepeatedRuleRequest {

    private Frequency frequency;    // DAILY, WEEKLY, MONTHLY, YEARLY
    private Integer intervalVal;    // 간격 (1=매일/매주, 2=격일/격주 등)
    private String byDay;           // 요일별 (MO,TU,WE...)
    private String byMonthDay;      // 일별 (1,15 등)
    private LocalDate until;        // 반복 종료일
    private Integer count;          // 반복 횟수
}
```

#### `NotificationRequest.java`

```java
package com.peoplecore.calendar.dto;

import com.peoplecore.calendar.enums.EventsNotiMethod;
import lombok.Getter;

@Getter
public class NotificationRequest {

    private EventsNotiMethod method;   // POPUP, EMAIL, PUSH
    private Integer minutesBefore;     // N분 전
}
```

#### `EventResponse.java`

```java
package com.peoplecore.calendar.dto;

import com.peoplecore.calendar.entity.Events;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class EventResponse {

    private Long eventsId;
    private String title;
    private String description;
    private String location;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Boolean isAllDay;
    private Boolean isPublic;
    private Long myCalendarsId;
    private String calendarName;
    private String displayColor;
    private Long empId;
    private LocalDateTime createdAt;

    private RepeatedRuleResponse repeatedRule;
    private List<NotificationResponse> notifications;

    public static EventResponse from(Events event) {
        return EventResponse.builder()
                .eventsId(event.getEventsId())
                .title(event.getTitle())
                .description(event.getDescription())
                .location(event.getLocation())
                .startAt(event.getStartAt())
                .endAt(event.getEndAt())
                .isAllDay(event.getIsAllDay())
                .isPublic(event.getIsPublic())
                .myCalendarsId(event.getMyCalendars() != null
                        ? event.getMyCalendars().getMyCalendarsId() : null)
                .calendarName(event.getMyCalendars() != null
                        ? event.getMyCalendars().getCalendarName() : null)
                .displayColor(event.getMyCalendars() != null
                        ? event.getMyCalendars().getMyDisplayColor() : null)
                .empId(event.getEmpId())
                .createdAt(event.getCreatedAt())
                .repeatedRule(event.getRepeatedRules() != null
                        ? RepeatedRuleResponse.from(event.getRepeatedRules()) : null)
                .notifications(event.getNotifications() != null
                        ? event.getNotifications().stream()
                              .map(NotificationResponse::from).toList()
                        : List.of())
                .build();
    }
}
```

#### `RepeatedRuleResponse.java`

```java
package com.peoplecore.calendar.dto;

import com.peoplecore.calendar.entity.RepeatedRules;
import com.peoplecore.calendar.enums.Frequency;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class RepeatedRuleResponse {

    private Long repeatedRulesId;
    private Frequency frequency;
    private Integer intervalVal;
    private String byDay;
    private String byMonthDay;
    private LocalDate until;
    private Integer count;

    public static RepeatedRuleResponse from(RepeatedRules rule) {
        return RepeatedRuleResponse.builder()
                .repeatedRulesId(rule.getRepeatedRulesId())
                .frequency(rule.getFrequency())
                .intervalVal(rule.getIntervalVal())
                .byDay(rule.getByDay())
                .byMonthDay(rule.getByMonthDay())
                .until(rule.getUntil())
                .count(rule.getCount())
                .build();
    }
}
```

#### `NotificationResponse.java`

```java
package com.peoplecore.calendar.dto;

import com.peoplecore.calendar.entity.EventsNotifications;
import com.peoplecore.calendar.enums.EventsNotiMethod;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotificationResponse {

    private Long notificationId;
    private EventsNotiMethod method;
    private Integer minutesBefore;

    public static NotificationResponse from(EventsNotifications n) {
        return NotificationResponse.builder()
                .notificationId(n.getNotificationId())
                .method(n.getEventsNotiMethod())
                .minutesBefore(n.getMinutesBefore())
                .build();
    }
}
```

### 3-2. 내 캘린더 관련

#### `MyCalendarCreateRequest.java`

```java
package com.peoplecore.calendar.dto;

import lombok.Getter;

@Getter
public class MyCalendarCreateRequest {

    private String calendarName;
    private String displayColor;  // #FF5733 형식
}
```

#### `MyCalendarUpdateRequest.java`

```java
package com.peoplecore.calendar.dto;

import lombok.Getter;

@Getter
public class MyCalendarUpdateRequest {

    private String calendarName;
    private String displayColor;
    private Boolean isVisible;
    private Integer sortOrder;
}
```

#### `MyCalendarResponse.java`

```java
package com.peoplecore.calendar.dto;

import com.peoplecore.calendar.entity.MyCalendars;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MyCalendarResponse {

    private Long myCalendarsId;
    private String calendarName;
    private String displayColor;
    private Boolean isVisible;
    private Integer sortOrder;

    public static MyCalendarResponse from(MyCalendars cal) {
        return MyCalendarResponse.builder()
                .myCalendarsId(cal.getMyCalendarsId())
                .calendarName(cal.getCalendarName())
                .displayColor(cal.getMyDisplayColor())
                .isVisible(cal.getIsVisible())
                .sortOrder(cal.getSortOrder())
                .build();
    }
}
```

### 3-3. 관심 캘린더 / 공유 요청 관련

#### `ShareRequestCreateDto.java`

```java
package com.peoplecore.calendar.dto;

import lombok.Getter;

@Getter
public class ShareRequestCreateDto {

    private Long targetEmpId;  // 공유 요청할 상대방 사원 ID
}
```

#### `ShareRequestResponse.java`

```java
package com.peoplecore.calendar.dto;

import com.peoplecore.calendar.entity.CalendarShareRequests;
import com.peoplecore.calendar.enums.ShareStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ShareRequestResponse {

    private Long calendarShareReqId;
    private Long fromEmpId;
    private String fromEmpName;
    private Long toEmpId;
    private String toEmpName;
    private ShareStatus shareStatus;
    private LocalDateTime requestedAt;
    private LocalDateTime respondedAt;

    public static ShareRequestResponse from(CalendarShareRequests req,
                                            String fromName, String toName) {
        return ShareRequestResponse.builder()
                .calendarShareReqId(req.getCalendarShareReqid())
                .fromEmpId(req.getFromEmpId())
                .fromEmpName(fromName)
                .toEmpId(req.getToEmpId())
                .toEmpName(toName)
                .shareStatus(req.getShareStatus())
                .requestedAt(req.getRequestedAt())
                .respondedAt(req.getRespondedAt())
                .build();
    }
}
```

#### `InterestCalendarResponse.java`

```java
package com.peoplecore.calendar.dto;

import com.peoplecore.calendar.entity.InterestCalendars;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InterestCalendarResponse {

    private Long id;
    private Long targetEmpId;
    private String targetEmpName;
    private String displayColor;
    private Boolean isVisible;
    private Integer sortOrder;

    public static InterestCalendarResponse from(InterestCalendars ic, String empName) {
        return InterestCalendarResponse.builder()
                .id(ic.getId())
                .targetEmpId(ic.getTargetEmpId())
                .targetEmpName(empName)
                .displayColor(ic.getInsDisplayColor())
                .isVisible(ic.getIsVisible())
                .sortOrder(ic.getSortOrder())
                .build();
    }
}
```

#### `InterestCalendarUpdateRequest.java`

```java
package com.peoplecore.calendar.dto;

import lombok.Getter;

@Getter
public class InterestCalendarUpdateRequest {

    private String displayColor;
    private Boolean isVisible;
    private Integer sortOrder;
}
```

### 3-4. 연차 연동 설정

#### `AnnualLeaveSettingRequest.java`

```java
package com.peoplecore.calendar.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AnnualLeaveSettingRequest {

    private List<CalendarLinkItem> calendars;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CalendarLinkItem {
        private Long calendarId;
        private Boolean isPublic;   // 이 캘린더에서 연차를 공개할지
    }
}
```

> **요청 예시:**
> ```json
> {
>   "calendars": [
>     { "calendarId": 5, "isPublic": true },
>     { "calendarId": 8, "isPublic": false }
>   ]
> }
> ```

#### `AnnualLeaveSettingResponse.java`

```java
package com.peoplecore.calendar.dto;

import com.peoplecore.calendar.entity.AnnualLeaveSetting;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnualLeaveSettingResponse {

    private List<LinkedCalendar> calendars;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LinkedCalendar {
        private Long calendarId;
        private String calendarName;
        private Boolean isPublic;
    }

    public static AnnualLeaveSettingResponse from(List<AnnualLeaveSetting> settings) {
        List<LinkedCalendar> calendars = settings.stream()
                .map(s -> LinkedCalendar.builder()
                        .calendarId(s.getMyCalendar().getMyCalendarsId())
                        .calendarName(s.getMyCalendar().getCalendarName())
                        .isPublic(s.getIsPublic())
                        .build())
                .toList();
        return AnnualLeaveSettingResponse.builder()
                .calendars(calendars)
                .build();
    }
}
```

> **응답 예시:**
> ```json
> {
>   "calendars": [
>     { "calendarId": 5, "calendarName": "내 일정(기본)", "isPublic": true },
>     { "calendarId": 8, "calendarName": "프로젝트", "isPublic": false }
>   ]
> }
> ```

---

## 4. Service

### 4-1. `CalendarEventService.java` — 일정 CRUD + 즉시 알림

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/service/CalendarEventService.java`

> **알림 방식:** 전자결재와 동일하게 `AlarmEventPublisher.publisher()` → Kafka 비동기 처리
> 일정 생성 트랜잭션과 알림이 분리되므로 알림 실패가 일정 저장에 영향 안 줌

```java
package com.peoplecore.calendar.service;

import com.peoplecore.alarm.publisher.AlarmEventPublisher;
import com.peoplecore.calendar.dto.*;
import com.peoplecore.calendar.entity.*;
import com.peoplecore.calendar.repository.*;
import com.peoplecore.event.AlarmEvent;
import com.peoplecore.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

@Service
@Slf4j
@Transactional(readOnly = true)
public class CalendarEventService {

    private final EventsRepository eventsRepository;
    private final MyCalendarsRepository myCalendarsRepository;
    private final RepeatedRulesRepository repeatedRulesRepository;
    private final EventsNotificationsRepository notificationsRepository;
    private final InterestCalendarsRepository interestCalendarsRepository;
    private final AlarmEventPublisher alarmEventPublisher;

    @Autowired
    public CalendarEventService(EventsRepository eventsRepository,
                                MyCalendarsRepository myCalendarsRepository,
                                RepeatedRulesRepository repeatedRulesRepository,
                                EventsNotificationsRepository notificationsRepository,
                                InterestCalendarsRepository interestCalendarsRepository,
                                AlarmEventPublisher alarmEventPublisher) {
        this.eventsRepository = eventsRepository;
        this.myCalendarsRepository = myCalendarsRepository;
        this.repeatedRulesRepository = repeatedRulesRepository;
        this.notificationsRepository = notificationsRepository;
        this.interestCalendarsRepository = interestCalendarsRepository;
        this.alarmEventPublisher = alarmEventPublisher;
    }

    // ────────────────────────────────────────────
    // 일정 등록
    // ────────────────────────────────────────────
    @Transactional
    public EventResponse createEvent(UUID companyId, Long empId, EventCreateRequest request) {
        MyCalendars calendar = myCalendarsRepository.findById(request.getMyCalendarsId())
                .orElseThrow(() -> new BusinessException("캘린더를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        validateCalendarOwner(calendar, empId);

        // 반복 규칙 저장 (없으면 null)
        RepeatedRules repeatedRules = saveRepeatedRule(companyId, request.getRepeatedRule());

        Events event = Events.builder()
                .empId(empId)
                .title(request.getTitle())
                .description(request.getDescription())
                .location(request.getLocation())
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .isAllDay(request.getIsAllDay())
                .isPublic(request.getIsPublic())
                .isAllEmployees(false)
                .companyId(companyId)
                .myCalendars(calendar)
                .repeatedRules(repeatedRules)
                .build();

        eventsRepository.save(event);

        // 알림 설정 저장
        saveNotifications(event, request.getNotifications());

        // 참석자에게 즉시 알림 발송 (같은 서비스 내부 → 직접 호출)
        sendAttendeeAlarm(companyId, event, request.getAttendeeEmpIds());

        return EventResponse.from(event);
    }

    // ────────────────────────────────────────────
    // 일정 수정
    // ────────────────────────────────────────────
    @Transactional
    public EventResponse updateEvent(UUID companyId, Long empId, Long eventsId,
                                     EventUpdateRequest request) {
        Events event = findEventOrThrow(eventsId, companyId);
        validateEventOwner(event, empId);

        MyCalendars calendar = myCalendarsRepository.findById(request.getMyCalendarsId())
                .orElseThrow(() -> new BusinessException("캘린더를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        event.update(
                request.getTitle(), request.getDescription(), request.getLocation(),
                request.getStartAt(), request.getEndAt(),
                request.getIsAllDay(), request.getIsPublic(), calendar
        );

        // 알림 갱신: 기존 삭제 → 신규 저장
        notificationsRepository.deleteByEvents_EventsId(eventsId);
        saveNotifications(event, request.getNotifications());

        return EventResponse.from(event);
    }

    // ────────────────────────────────────────────
    // 일정 삭제 (소프트 딜리트)
    // ────────────────────────────────────────────
    @Transactional
    public void deleteEvent(UUID companyId, Long empId, Long eventsId) {
        Events event = findEventOrThrow(eventsId, companyId);
        validateEventOwner(event, empId);
        event.softDelete();
    }

    // ────────────────────────────────────────────
    // 일정 상세 조회
    // ────────────────────────────────────────────
    public EventResponse getEvent(UUID companyId, Long eventsId) {
        Events event = findEventOrThrow(eventsId, companyId);
        return EventResponse.from(event);
    }

    // ────────────────────────────────────────────
    // 캘린더 뷰 일정 조회 (월/주/일)
    // 내 캘린더 + 관심 캘린더 + 전사 일정 통합
    // ────────────────────────────────────────────
    public List<EventResponse> getEventsForView(UUID companyId, Long empId,
                                                LocalDateTime start, LocalDateTime end) {
        // 1) 내 캘린더 목록에서 보이기 설정된 캘린더 ID 추출
        List<MyCalendars> myCalendars = myCalendarsRepository
                .findByCompanyIdAndEmpIdOrderBySortOrderAsc(companyId, empId);
        List<Long> visibleCalIds = myCalendars.stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsVisible()))
                .map(MyCalendars::getMyCalendarsId)
                .toList();

        // 2) 내 일정
        List<Events> myEvents = visibleCalIds.isEmpty()
                ? List.of()
                : eventsRepository.findByCalendarIdsAndPeriod(visibleCalIds, companyId, start, end);

        // 3) 관심 캘린더 일정 (공개 일정만)
        List<InterestCalendars> interests = interestCalendarsRepository
                .findByViewerEmpIdWithRequest(empId, companyId);
        List<Events> interestEvents = interests.stream()
                .filter(ic -> Boolean.TRUE.equals(ic.getIsVisible()))
                .flatMap(ic -> eventsRepository
                        .findPublicEventsByEmpId(ic.getTargetEmpId(), companyId, start, end)
                        .stream())
                .toList();

        // 4) 전사 일정
        List<Events> companyEvents = eventsRepository.findCompanyEvents(companyId, start, end);

        // 5) 통합 (중복 제거)
        Map<Long, Events> merged = new LinkedHashMap<>();
        Stream.of(myEvents, interestEvents, companyEvents)
                .flatMap(Collection::stream)
                .forEach(e -> merged.putIfAbsent(e.getEventsId(), e));

        return merged.values().stream()
                .map(EventResponse::from)
                .toList();
    }

    // ────────────────────────────────────────────
    // Private 헬퍼 메서드
    // ────────────────────────────────────────────

    private Events findEventOrThrow(Long eventsId, UUID companyId) {
        Events event = eventsRepository.findById(eventsId)
                .orElseThrow(() -> new BusinessException("일정을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        if (!event.getCompanyId().equals(companyId)) {
            throw new BusinessException("접근 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }
        if (event.isDeleted()) {
            throw new BusinessException("삭제된 일정입니다.", HttpStatus.NOT_FOUND);
        }
        return event;
    }

    private void validateEventOwner(Events event, Long empId) {
        if (!event.getEmpId().equals(empId)) {
            throw new BusinessException("본인의 일정만 수정/삭제할 수 있습니다.", HttpStatus.FORBIDDEN);
        }
    }

    private void validateCalendarOwner(MyCalendars calendar, Long empId) {
        if (!calendar.getEmpId().equals(empId)) {
            throw new BusinessException("본인의 캘린더에만 일정을 등록할 수 있습니다.", HttpStatus.FORBIDDEN);
        }
    }

    private RepeatedRules saveRepeatedRule(UUID companyId, RepeatedRuleRequest req) {
        if (req == null || req.getFrequency() == null) {
            return null;
        }
        return repeatedRulesRepository.save(
                RepeatedRules.builder()
                        .frequency(req.getFrequency())
                        .intervalVal(req.getIntervalVal())
                        .byDay(req.getByDay())
                        .byMonthDay(req.getByMonthDay())
                        .until(req.getUntil())
                        .count(req.getCount())
                        .companyId(companyId)
                        .build()
        );
    }

    private void saveNotifications(Events event, List<NotificationRequest> requests) {
        if (requests == null || requests.isEmpty()) return;

        List<EventsNotifications> notiList = requests.stream()
                .map(r -> EventsNotifications.builder()
                        .eventsNotiMethod(r.getMethod())
                        .minutesBefore(r.getMinutesBefore())
                        .events(event)
                        .build())
                .toList();
        notificationsRepository.saveAll(notiList);
    }

    /**
     * 참석자에게 알림 발송 (Kafka 비동기)
     * 전자결재와 동일한 AlarmEventPublisher 패턴
     * → 일정 저장 트랜잭션과 분리되어 알림 실패가 일정에 영향 안 줌
     */
    private void sendAttendeeAlarm(UUID companyId, Events event, List<Long> attendeeEmpIds) {
        if (attendeeEmpIds == null || attendeeEmpIds.isEmpty()) return;

        alarmEventPublisher.publisher(AlarmEvent.builder()
                .companyId(companyId)
                .alarmType("Calendar")
                .alarmTitle("일정 초대")
                .alarmContent(event.getTitle() + " 일정에 초대되었습니다.")
                .alarmLink("/calendar?eventId=" + event.getEventsId())
                .alarmRefType("EVENT")
                .alarmRefId(event.getEventsId())
                .empIds(attendeeEmpIds)
                .build());
    }
}
```

### 4-2. `MyCalendarService.java` — 내 캘린더 CRUD + 기본 캘린더

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/service/MyCalendarService.java`

> **변경 사항:**
> - 신규 사원 첫 접근 시 '내 일정(기본)' 캘린더 자동 생성
> - 기본 캘린더는 삭제/이름변경 불가
> - 색상, 보이기, 순서는 기본 캘린더도 변경 가능

```java
package com.peoplecore.calendar.service;

import com.peoplecore.calendar.dto.*;
import com.peoplecore.calendar.entity.MyCalendars;
import com.peoplecore.calendar.repository.MyCalendarsRepository;
import com.peoplecore.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class MyCalendarService {

    private static final String DEFAULT_CALENDAR_NAME = "내 일정(기본)";
    private static final String DEFAULT_CALENDAR_COLOR = "#1A73E8";

    private final MyCalendarsRepository myCalendarsRepository;

    @Autowired
    public MyCalendarService(MyCalendarsRepository myCalendarsRepository) {
        this.myCalendarsRepository = myCalendarsRepository;
    }

    /** 내 캘린더 목록 조회 — 기본 캘린더 없으면 자동 생성 */
    @Transactional
    public List<MyCalendarResponse> getMyCalendars(UUID companyId, Long empId) {
        List<MyCalendars> calendars = myCalendarsRepository
                .findByCompanyIdAndEmpIdOrderBySortOrderAsc(companyId, empId);

        // 첫 접근 시 기본 캘린더 자동 생성
        if (calendars.isEmpty()) {
            MyCalendars defaultCal = createDefaultCalendar(companyId, empId);
            calendars = List.of(defaultCal);
        }

        return calendars.stream()
                .map(MyCalendarResponse::from)
                .toList();
    }

    /** 내 캘린더 추가 */
    @Transactional
    public MyCalendarResponse createMyCalendar(UUID companyId, Long empId,
                                                MyCalendarCreateRequest request) {
        if (myCalendarsRepository.existsByCompanyIdAndEmpIdAndCalendarName(
                companyId, empId, request.getCalendarName())) {
            throw new BusinessException("이미 같은 이름의 캘린더가 존재합니다.");
        }

        // 정렬 순서: 기존 캘린더 수 + 1
        List<MyCalendars> existing = myCalendarsRepository
                .findByCompanyIdAndEmpIdOrderBySortOrderAsc(companyId, empId);

        MyCalendars calendar = MyCalendars.builder()
                .empId(empId)
                .calendarName(request.getCalendarName())
                .myDisplayColor(request.getDisplayColor())
                .isVisible(true)
                .isDefault(false)
                .sortOrder(existing.size() + 1)
                .companyId(companyId)
                .build();

        return MyCalendarResponse.from(myCalendarsRepository.save(calendar));
    }

    /** 내 캘린더 수정 (이름, 색상, 보이기, 순서) */
    @Transactional
    public MyCalendarResponse updateMyCalendar(UUID companyId, Long empId,
                                                Long calendarId,
                                                MyCalendarUpdateRequest request) {
        MyCalendars calendar = findAndValidate(calendarId, companyId, empId);

        // 기본 캘린더는 이름 변경 불가
        if (request.getCalendarName() != null) {
            if (calendar.isDefaultCalendar()) {
                throw new BusinessException("기본 캘린더의 이름은 변경할 수 없습니다.");
            }
            calendar.updateName(request.getCalendarName());
        }
        if (request.getDisplayColor() != null) {
            calendar.updateColor(request.getDisplayColor());
        }
        if (request.getIsVisible() != null) {
            if (!request.getIsVisible().equals(calendar.getIsVisible())) {
                calendar.toggleVisible();
            }
        }
        if (request.getSortOrder() != null) {
            calendar.updateSortOrder(request.getSortOrder());
        }

        return MyCalendarResponse.from(calendar);
    }

    /** 내 캘린더 삭제 — 기본 캘린더 삭제 불가 */
    @Transactional
    public void deleteMyCalendar(UUID companyId, Long empId, Long calendarId) {
        MyCalendars calendar = findAndValidate(calendarId, companyId, empId);

        if (calendar.isDefaultCalendar()) {
            throw new BusinessException("기본 캘린더는 삭제할 수 없습니다.");
        }

        myCalendarsRepository.delete(calendar);
    }

    // ────────────────────────────────────────────
    // Private 헬퍼
    // ────────────────────────────────────────────

    /** 기본 캘린더 생성 (첫 접근 시 1회) */
    private MyCalendars createDefaultCalendar(UUID companyId, Long empId) {
        MyCalendars defaultCal = MyCalendars.builder()
                .empId(empId)
                .calendarName(DEFAULT_CALENDAR_NAME)
                .myDisplayColor(DEFAULT_CALENDAR_COLOR)
                .isVisible(true)
                .isDefault(true)
                .sortOrder(0)
                .companyId(companyId)
                .build();
        return myCalendarsRepository.save(defaultCal);
    }

    private MyCalendars findAndValidate(Long calendarId, UUID companyId, Long empId) {
        MyCalendars calendar = myCalendarsRepository.findById(calendarId)
                .orElseThrow(() -> new BusinessException("캘린더를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        if (!calendar.getCompanyId().equals(companyId) || !calendar.getEmpId().equals(empId)) {
            throw new BusinessException("본인의 캘린더만 관리할 수 있습니다.", HttpStatus.FORBIDDEN);
        }
        return calendar;
    }
}
```

### 4-3. `InterestCalendarService.java` — 관심 캘린더 + 공유 요청 + 즉시 알림

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/service/InterestCalendarService.java`

> **변경 사항:**
> - 전자결재와 동일하게 `AlarmEventPublisher.publisher()` → Kafka 비동기 알림
> - 승인/거절 메서드를 `respondShareRequest()`로 통합
> - `HrCacheService`로 타인 사원 이름 조회 (null 제거)

```java
package com.peoplecore.calendar.service;

import com.peoplecore.alarm.publisher.AlarmEventPublisher;
import com.peoplecore.calendar.dto.*;
import com.peoplecore.calendar.entity.CalendarShareRequests;
import com.peoplecore.calendar.entity.InterestCalendars;
import com.peoplecore.calendar.enums.Permission;
import com.peoplecore.calendar.enums.ShareStatus;
import com.peoplecore.calendar.repository.CalendarShareRequestsRepository;
import com.peoplecore.calendar.repository.InterestCalendarsRepository;
import com.peoplecore.client.component.HrCacheService;
import com.peoplecore.client.dto.EmployeeSimpleResponse;
import com.peoplecore.event.AlarmEvent;
import com.peoplecore.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
public class InterestCalendarService {

    private final CalendarShareRequestsRepository shareRequestsRepository;
    private final InterestCalendarsRepository interestCalendarsRepository;
    private final AlarmEventPublisher alarmEventPublisher;
    private final HrCacheService hrCacheService;

    @Autowired
    public InterestCalendarService(CalendarShareRequestsRepository shareRequestsRepository,
                                   InterestCalendarsRepository interestCalendarsRepository,
                                   AlarmEventPublisher alarmEventPublisher,
                                   HrCacheService hrCacheService) {
        this.shareRequestsRepository = shareRequestsRepository;
        this.interestCalendarsRepository = interestCalendarsRepository;
        this.alarmEventPublisher = alarmEventPublisher;
        this.hrCacheService = hrCacheService;
    }

    // ────────────────────────────────────────────
    // 관심 캘린더 공유 요청 생성 + 즉시 알림
    // ────────────────────────────────────────────
    @Transactional
    public void requestShare(UUID companyId, Long fromEmpId, ShareRequestCreateDto request) {
        Long targetEmpId = request.getTargetEmpId();

        if (fromEmpId.equals(targetEmpId)) {
            throw new BusinessException("본인에게는 공유 요청을 보낼 수 없습니다.");
        }

        // 중복 PENDING 요청 방지
        if (shareRequestsRepository.existsByCompanyIdAndFromEmpIdAndToEmpIdAndShareStatus(
                companyId, fromEmpId, targetEmpId, ShareStatus.PENDING)) {
            throw new BusinessException("이미 대기 중인 요청이 있습니다.");
        }

        CalendarShareRequests shareRequest = CalendarShareRequests.builder()
                .fromEmpId(fromEmpId)
                .toEmpId(targetEmpId)
                .permission(Permission.READ_ONLY)
                .shareStatus(ShareStatus.PENDING)
                .requestedAt(LocalDateTime.now())
                .companyId(companyId)
                .build();

        shareRequestsRepository.save(shareRequest);

        // 상대방에게 알림 (Kafka 비동기)
        alarmEventPublisher.publisher(AlarmEvent.builder()
                .companyId(companyId)
                .alarmType("Calendar")
                .alarmTitle("캘린더 공유 요청")
                .alarmContent("캘린더 공유 요청이 도착했습니다.")
                .alarmLink("/calendar/settings")
                .alarmRefType("CALENDAR_SHARE")
                .alarmRefId(shareRequest.getCalendarShareReqid())
                .empIds(List.of(targetEmpId))
                .build());
    }

    // ────────────────────────────────────────────
    // 공유 요청 응답 (승인/거절 통합)
    // ────────────────────────────────────────────
    @Transactional
    public ShareRequestResponse respondShareRequest(UUID companyId, Long empId,
                                                     Long shareReqId, boolean accepted) {
        CalendarShareRequests request = findShareRequestOrThrow(shareReqId);
        validateShareRequestTarget(request, companyId, empId);

        if (accepted) {
            request.approve();

            // 관심 캘린더 레코드 생성 (요청자 → 대상자 일정 구독)
            InterestCalendars interest = InterestCalendars.builder()
                    .viewerEmpId(request.getFromEmpId())
                    .targetEmpId(request.getToEmpId())
                    .isVisible(true)
                    .shareDisplayColor("#4CAF50")
                    .sortOrder(1)
                    .createdAt(LocalDateTime.now())
                    .companyId(companyId)
                    .calendarShareRequest(request)
                    .build();
            interestCalendarsRepository.save(interest);
        } else {
            request.reject();
        }

        // 요청자에게 알림 (Kafka 비동기)
        String title = accepted ? "캘린더 공유 승인" : "캘린더 공유 거절";
        String content = accepted
                ? "캘린더 공유 요청이 승인되었습니다."
                : "캘린더 공유 요청이 거절되었습니다.";

        alarmEventPublisher.publisher(AlarmEvent.builder()
                .companyId(companyId)
                .alarmType("Calendar")
                .alarmTitle(title)
                .alarmContent(content)
                .alarmLink("/calendar/interest")
                .alarmRefType("CALENDAR_SHARE")
                .alarmRefId(shareReqId)
                .empIds(List.of(request.getFromEmpId()))
                .build());

        // 사원 이름 조회
        Map<Long, EmployeeSimpleResponse> empMap = getEmpMap(
                List.of(request.getFromEmpId(), request.getToEmpId()));

        return ShareRequestResponse.from(request,
                getEmpName(empMap, request.getFromEmpId()),
                getEmpName(empMap, request.getToEmpId()));
    }

    // ────────────────────────────────────────────
    // 내가 보낸 관심 캘린더 요청 목록
    // ────────────────────────────────────────────
    public Page<ShareRequestResponse> getMyShareRequests(UUID companyId, Long empId,
                                                         Pageable pageable) {
        Page<CalendarShareRequests> page = shareRequestsRepository
                .findByCompanyIdAndFromEmpIdOrderByRequestedAtDesc(companyId, empId, pageable);

        // 페이지 내 empId 일괄 조회
        List<Long> empIds = page.getContent().stream()
                .flatMap(req -> Stream.of(req.getFromEmpId(), req.getToEmpId()))
                .distinct()
                .toList();
        Map<Long, EmployeeSimpleResponse> empMap = getEmpMap(empIds);

        return page.map(req -> ShareRequestResponse.from(req,
                getEmpName(empMap, req.getFromEmpId()),
                getEmpName(empMap, req.getToEmpId())));
    }

    // ────────────────────────────────────────────
    // 나에게 온 요청 목록
    // ────────────────────────────────────────────
    public Page<ShareRequestResponse> getReceivedShareRequests(UUID companyId, Long empId,
                                                               Pageable pageable) {
        Page<CalendarShareRequests> page = shareRequestsRepository
                .findByCompanyIdAndToEmpIdOrderByRequestedAtDesc(companyId, empId, pageable);

        List<Long> empIds = page.getContent().stream()
                .flatMap(req -> Stream.of(req.getFromEmpId(), req.getToEmpId()))
                .distinct()
                .toList();
        Map<Long, EmployeeSimpleResponse> empMap = getEmpMap(empIds);

        return page.map(req -> ShareRequestResponse.from(req,
                getEmpName(empMap, req.getFromEmpId()),
                getEmpName(empMap, req.getToEmpId())));
    }

    // ────────────────────────────────────────────
    // 관심 캘린더 목록 조회
    // ────────────────────────────────────────────
    public List<InterestCalendarResponse> getInterestCalendars(UUID companyId, Long empId) {
        List<InterestCalendars> interests = interestCalendarsRepository
                .findByViewerEmpIdWithRequest(empId, companyId);

        // targetEmpId 일괄 조회
        List<Long> empIds = interests.stream()
                .map(InterestCalendars::getTargetEmpId)
                .distinct()
                .toList();
        Map<Long, EmployeeSimpleResponse> empMap = getEmpMap(empIds);

        return interests.stream()
                .map(ic -> InterestCalendarResponse.from(ic,
                        getEmpName(empMap, ic.getTargetEmpId())))
                .toList();
    }

    // ────────────────────────────────────────────
    // 관심 캘린더 설정 변경 (색상, 보이기, 순서)
    // ────────────────────────────────────────────
    @Transactional
    public InterestCalendarResponse updateInterestCalendar(UUID companyId, Long empId,
                                                           Long interestCalendarId,
                                                           InterestCalendarUpdateRequest request) {
        InterestCalendars ic = interestCalendarsRepository.findById(interestCalendarId)
                .orElseThrow(() -> new BusinessException("관심 캘린더를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        if (!ic.getViewerEmpId().equals(empId) || !ic.getCompanyId().equals(companyId)) {
            throw new BusinessException("본인의 관심 캘린더만 수정할 수 있습니다.", HttpStatus.FORBIDDEN);
        }

        if (request.getDisplayColor() != null) {
            ic.updateColor(request.getDisplayColor());
        }
        if (request.getIsVisible() != null && !request.getIsVisible().equals(ic.getIsVisible())) {
            ic.toggleVisible();
        }
        if (request.getSortOrder() != null) {
            ic.updateSortOrder(request.getSortOrder());
        }

        Map<Long, EmployeeSimpleResponse> empMap = getEmpMap(List.of(ic.getTargetEmpId()));
        return InterestCalendarResponse.from(ic, getEmpName(empMap, ic.getTargetEmpId()));
    }

    // ────────────────────────────────────────────
    // 관심 캘린더 삭제 (구독 해제)
    // ────────────────────────────────────────────
    @Transactional
    public void deleteInterestCalendar(UUID companyId, Long empId, Long interestCalendarId) {
        InterestCalendars ic = interestCalendarsRepository.findById(interestCalendarId)
                .orElseThrow(() -> new BusinessException("관심 캘린더를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        if (!ic.getViewerEmpId().equals(empId) || !ic.getCompanyId().equals(companyId)) {
            throw new BusinessException("본인의 관심 캘린더만 삭제할 수 있습니다.", HttpStatus.FORBIDDEN);
        }

        // 관련 공유 요청도 CANCELLED 처리
        ic.getCalendarShareRequest().cancel();
        interestCalendarsRepository.delete(ic);
    }

    // ────────────────────────────────────────────
    // Private 헬퍼
    // ────────────────────────────────────────────

    private CalendarShareRequests findShareRequestOrThrow(Long shareReqId) {
        return shareRequestsRepository.findById(shareReqId)
                .orElseThrow(() -> new BusinessException("공유 요청을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
    }

    private void validateShareRequestTarget(CalendarShareRequests request,
                                            UUID companyId, Long empId) {
        if (!request.getCompanyId().equals(companyId) || !request.getToEmpId().equals(empId)) {
            throw new BusinessException("본인에게 온 요청만 처리할 수 있습니다.", HttpStatus.FORBIDDEN);
        }
        if (request.getShareStatus() != ShareStatus.PENDING) {
            throw new BusinessException("이미 처리된 요청입니다.");
        }
    }

    /** empId 리스트 → Map 변환 (HrCacheService 일괄 조회) */
    private Map<Long, EmployeeSimpleResponse> getEmpMap(List<Long> empIds) {
        return hrCacheService.getEmployeesBulk(empIds).stream()
                .collect(Collectors.toMap(EmployeeSimpleResponse::getEmpId, e -> e));
    }

    /** Map에서 이름 꺼내기 (없으면 null) */
    private String getEmpName(Map<Long, EmployeeSimpleResponse> empMap, Long empId) {
        EmployeeSimpleResponse emp = empMap.get(empId);
        return emp != null ? emp.getEmpName() : null;
    }
}
```

### 4-4. `CalendarSettingsService.java` — 연차 연동 설정

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/service/CalendarSettingsService.java`

> 연차 연동 설정은 별도 엔티티가 필요합니다. 아래에 엔티티 + 서비스를 함께 작성합니다.

#### 새 엔티티: `AnnualLeaveSetting.java`

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/entity/AnnualLeaveSetting.java`

```java
package com.peoplecore.calendar.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "annual_leave_settings")
public class AnnualLeaveSetting extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long empId;

    @Column(nullable = false)
    private UUID companyId;

    /** 연동할 캘린더 ID (여러 개이면 레코드 여러 건) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "my_calendars_id", nullable = false)
    private MyCalendars myCalendar;

    /** 연차 일정 공개 여부 */
    private Boolean isPublic;

    // ── 비즈니스 메서드 ──

    public void updatePublic(Boolean isPublic) {
        this.isPublic = isPublic;
    }
}
```

#### 새 레포지토리: `AnnualLeaveSettingRepository.java`

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/repository/AnnualLeaveSettingRepository.java`

```java
package com.peoplecore.calendar.repository;

import com.peoplecore.calendar.entity.AnnualLeaveSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AnnualLeaveSettingRepository extends JpaRepository<AnnualLeaveSetting, Long> {

    List<AnnualLeaveSetting> findByCompanyIdAndEmpId(UUID companyId, Long empId);

    void deleteByCompanyIdAndEmpId(UUID companyId, Long empId);
}
```

#### 서비스: `CalendarSettingsService.java`

```java
package com.peoplecore.calendar.service;

import com.peoplecore.calendar.dto.AnnualLeaveSettingRequest;
import com.peoplecore.calendar.dto.AnnualLeaveSettingResponse;
import com.peoplecore.calendar.entity.AnnualLeaveSetting;
import com.peoplecore.calendar.entity.MyCalendars;
import com.peoplecore.calendar.repository.AnnualLeaveSettingRepository;
import com.peoplecore.calendar.repository.MyCalendarsRepository;
import com.peoplecore.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CalendarSettingsService {

    private final AnnualLeaveSettingRepository settingRepository;
    private final MyCalendarsRepository myCalendarsRepository;

    @Autowired
    public CalendarSettingsService(AnnualLeaveSettingRepository settingRepository,
                                   MyCalendarsRepository myCalendarsRepository) {
        this.settingRepository = settingRepository;
        this.myCalendarsRepository = myCalendarsRepository;
    }

    /** 연차 연동 설정 조회 */
    public AnnualLeaveSettingResponse getSettings(UUID companyId, Long empId) {
        List<AnnualLeaveSetting> settings = settingRepository.findByCompanyIdAndEmpId(companyId, empId);

        if (settings.isEmpty()) {
            return AnnualLeaveSettingResponse.builder()
                    .calendars(List.of())
                    .build();
        }

        return AnnualLeaveSettingResponse.from(settings);
    }

    /** 연차 연동 설정 저장 (기존 삭제 후 재생성) */
    @Transactional
    public AnnualLeaveSettingResponse saveSettings(UUID companyId, Long empId,
                                                    AnnualLeaveSettingRequest request) {
        // 기존 설정 삭제
        settingRepository.deleteByCompanyIdAndEmpId(companyId, empId);

        // 새 설정 저장 — 캘린더별 isPublic 개별 적용
        List<AnnualLeaveSetting> newSettings = request.getCalendars().stream()
                .map(item -> {
                    MyCalendars cal = myCalendarsRepository.findById(item.getCalendarId())
                            .orElseThrow(() -> new BusinessException(
                                    "캘린더를 찾을 수 없습니다. id=" + item.getCalendarId(),
                                    HttpStatus.NOT_FOUND));
                    return AnnualLeaveSetting.builder()
                            .empId(empId)
                            .companyId(companyId)
                            .myCalendar(cal)
                            .isPublic(item.getIsPublic())
                            .build();
                })
                .toList();

        settingRepository.saveAll(newSettings);

        return AnnualLeaveSettingResponse.from(newSettings);
    }
}
```

---

## 5. Controller

### 5-1. `CalendarEventController.java` — 일정 CRUD

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/controller/CalendarEventController.java`

```java
package com.peoplecore.calendar.controller;

import com.peoplecore.calendar.dto.*;
import com.peoplecore.calendar.service.CalendarEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/calendar/events")
public class CalendarEventController {

    private final CalendarEventService calendarEventService;

    @Autowired
    public CalendarEventController(CalendarEventService calendarEventService) {
        this.calendarEventService = calendarEventService;
    }

    /** 일정 등록 */
    @PostMapping
    public ResponseEntity<EventResponse> createEvent(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestBody EventCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(calendarEventService.createEvent(companyId, empId, request));
    }

    /** 일정 수정 */
    @PutMapping("/{eventsId}")
    public ResponseEntity<EventResponse> updateEvent(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long eventsId,
            @RequestBody EventUpdateRequest request) {
        return ResponseEntity.ok(calendarEventService.updateEvent(companyId, empId, eventsId, request));
    }

    /** 일정 삭제 */
    @DeleteMapping("/{eventsId}")
    public ResponseEntity<Void> deleteEvent(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long eventsId) {
        calendarEventService.deleteEvent(companyId, empId, eventsId);
        return ResponseEntity.noContent().build();
    }

    /** 일정 상세 조회 */
    @GetMapping("/{eventsId}")
    public ResponseEntity<EventResponse> getEvent(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long eventsId) {
        return ResponseEntity.ok(calendarEventService.getEvent(companyId, eventsId));
    }

    /** 캘린더 뷰 일정 조회 (기간별) */
    @GetMapping
    public ResponseEntity<List<EventResponse>> getEvents(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(calendarEventService.getEventsForView(companyId, empId, start, end));
    }
}
```

### 5-2. `MyCalendarController.java` — 내 캘린더 관리

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/controller/MyCalendarController.java`

```java
package com.peoplecore.calendar.controller;

import com.peoplecore.calendar.dto.*;
import com.peoplecore.calendar.service.MyCalendarService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/calendar/my-calendars")
public class MyCalendarController {

    private final MyCalendarService myCalendarService;

    @Autowired
    public MyCalendarController(MyCalendarService myCalendarService) {
        this.myCalendarService = myCalendarService;
    }

    /** 내 캘린더 목록 조회 */
    @GetMapping
    public ResponseEntity<List<MyCalendarResponse>> getMyCalendars(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId) {
        return ResponseEntity.ok(myCalendarService.getMyCalendars(companyId, empId));
    }

    /** 내 캘린더 추가 */
    @PostMapping
    public ResponseEntity<MyCalendarResponse> createMyCalendar(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestBody MyCalendarCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(myCalendarService.createMyCalendar(companyId, empId, request));
    }

    /** 내 캘린더 수정 */
    @PatchMapping("/{calendarId}")
    public ResponseEntity<MyCalendarResponse> updateMyCalendar(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long calendarId,
            @RequestBody MyCalendarUpdateRequest request) {
        return ResponseEntity.ok(
                myCalendarService.updateMyCalendar(companyId, empId, calendarId, request));
    }

    /** 내 캘린더 삭제 */
    @DeleteMapping("/{calendarId}")
    public ResponseEntity<Void> deleteMyCalendar(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long calendarId) {
        myCalendarService.deleteMyCalendar(companyId, empId, calendarId);
        return ResponseEntity.noContent().build();
    }
}
```

### 5-3. `InterestCalendarController.java` — 관심 캘린더 + 공유 요청

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/controller/InterestCalendarController.java`

```java
package com.peoplecore.calendar.controller;

import com.peoplecore.calendar.dto.*;
import com.peoplecore.calendar.service.InterestCalendarService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/calendar/interest")
public class InterestCalendarController {

    private final InterestCalendarService interestCalendarService;

    @Autowired
    public InterestCalendarController(InterestCalendarService interestCalendarService) {
        this.interestCalendarService = interestCalendarService;
    }

    /** 관심 캘린더 공유 요청 */
    @PostMapping("/share-request")
    public ResponseEntity<Void> requestShare(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestBody ShareRequestCreateDto request) {
        interestCalendarService.requestShare(companyId, empId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /** 공유 요청 응답 (승인/거절 통합) — ?accepted=true 또는 ?accepted=false */
    @PatchMapping("/share-request/{shareReqId}")
    public ResponseEntity<ShareRequestResponse> respondShareRequest(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long shareReqId,
            @RequestParam boolean accepted) {
        return ResponseEntity.ok(
                interestCalendarService.respondShareRequest(companyId, empId, shareReqId, accepted));
    }

    /** 내가 등록한 관심 캘린더 요청 목록 */
    @GetMapping("/share-request/sent")
    public ResponseEntity<Page<ShareRequestResponse>> getSentRequests(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            Pageable pageable) {
        return ResponseEntity.ok(
                interestCalendarService.getMyShareRequests(companyId, empId, pageable));
    }

    /** 내 일정을 보고 있는 동료 (나에게 온 요청) */
    @GetMapping("/share-request/received")
    public ResponseEntity<Page<ShareRequestResponse>> getReceivedRequests(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            Pageable pageable) {
        return ResponseEntity.ok(
                interestCalendarService.getReceivedShareRequests(companyId, empId, pageable));
    }

    /** 관심 캘린더 목록 조회 */
    @GetMapping
    public ResponseEntity<List<InterestCalendarResponse>> getInterestCalendars(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId) {
        return ResponseEntity.ok(
                interestCalendarService.getInterestCalendars(companyId, empId));
    }

    /** 관심 캘린더 설정 변경 (색상, 보이기, 순서) */
    @PatchMapping("/{interestCalendarId}")
    public ResponseEntity<InterestCalendarResponse> updateInterestCalendar(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long interestCalendarId,
            @RequestBody InterestCalendarUpdateRequest request) {
        return ResponseEntity.ok(interestCalendarService
                .updateInterestCalendar(companyId, empId, interestCalendarId, request));
    }

    /** 관심 캘린더 삭제 (구독 해제) */
    @DeleteMapping("/{interestCalendarId}")
    public ResponseEntity<Void> deleteInterestCalendar(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long interestCalendarId) {
        interestCalendarService.deleteInterestCalendar(companyId, empId, interestCalendarId);
        return ResponseEntity.noContent().build();
    }
}
```

### 5-4. `CalendarSettingsController.java` — 캘린더 환경설정

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/controller/CalendarSettingsController.java`

```java
package com.peoplecore.calendar.controller;

import com.peoplecore.calendar.dto.AnnualLeaveSettingRequest;
import com.peoplecore.calendar.dto.AnnualLeaveSettingResponse;
import com.peoplecore.calendar.service.AnnualLeaveSettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/calendar/settings")
public class CalendarSettingsController {

    private final CalendarSettingsService settingsService;

    @Autowired
    public CalendarSettingsController(CalendarSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /** 연차 연동 설정 조회 */
    @GetMapping("/annual-leave")
    public ResponseEntity<AnnualLeaveSettingResponse> getAnnualLeaveSettings(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId) {
        return ResponseEntity.ok(settingsService.getSettings(companyId, empId));
    }

    /** 연차 연동 설정 저장 */
    @PostMapping("/annual-leave")
    public ResponseEntity<AnnualLeaveSettingResponse> saveAnnualLeaveSettings(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestBody AnnualLeaveSettingRequest request) {
        return ResponseEntity.ok(settingsService.saveSettings(companyId, empId, request));
    }
}
```

---

## 6. 예약 알림 스케줄러

"10분 전 팝업", "1시간 전 이메일" 같은 예약 알림을 처리하는 스케줄러입니다.

### 6-1. `@EnableScheduling` 설정

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/CollaborationServiceApplication.java`

> 기존 `@SpringBootApplication`에 `@EnableScheduling` 추가

```java
@EnableScheduling   // ← 추가
@EnableJpaAuditing
@SpringBootApplication(scanBasePackages = {
    "com.peoplecore.collaboration_service",
    "com.peoplecore.common"
})
public class CollaborationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CollaborationServiceApplication.class, args);
    }
}
```

### 6-2. `EventNotificationScheduler.java`

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/scheduler/EventNotificationScheduler.java`

```java
package com.peoplecore.calendar.scheduler;

import com.peoplecore.alarm.publisher.AlarmEventPublisher;
import com.peoplecore.calendar.entity.Events;
import com.peoplecore.calendar.entity.EventsNotifications;
import com.peoplecore.calendar.repository.EventsRepository;
import com.peoplecore.event.AlarmEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 예약 알림 스케줄러
 *
 * 1분 주기로 실행되어, "지금으로부터 N분 뒤에 시작하는 일정"이 있으면
 * 해당 일정의 알림 설정(minutesBefore)에 맞춰 알림을 발송합니다.
 *
 * 동작 원리:
 *   - 매분 정각에 실행 (fixedRate = 60000)
 *   - 현재 시각 기준으로 "1분 뒤 ~ 최대 예약 시간 뒤"에 시작하는 일정을 조회
 *   - 각 일정의 알림 설정을 확인하여, "일정시작 - minutesBefore"가 현재 분과 일치하면 발송
 *   - AlarmEventPublisher.publisher()로 Kafka 비동기 알림 발송
 *
 * 예시:
 *   - 일정 시작: 10:30, 알림 설정: 10분 전
 *   - 스케줄러가 10:20에 실행될 때, 10:30 시작 일정을 발견
 *   - 10:30 - 10분 = 10:20 → 현재 시각과 일치 → 알림 발송
 */
@Component
@Slf4j
public class EventNotificationScheduler {

    private final EventsRepository eventsRepository;
    private final AlarmEventPublisher alarmEventPublisher;

    @Autowired
    public EventNotificationScheduler(EventsRepository eventsRepository,
                                      AlarmEventPublisher alarmEventPublisher) {
        this.eventsRepository = eventsRepository;
        this.alarmEventPublisher = alarmEventPublisher;
    }

    /**
     * 1분 주기로 예약 알림 체크 및 발송
     */
    @Scheduled(fixedRate = 60000)
    @Transactional(readOnly = true)
    public void checkAndSendScheduledNotifications() {
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);

        // 향후 24시간 이내에 시작하는 일정 중 알림 설정이 있는 것만 조회
        // (최대 minutesBefore가 1440분=24시간이라 가정)
        LocalDateTime scanEnd = now.plusHours(24);

        List<Events> events = eventsRepository.findEventsWithNotificationsBetween(now, scanEnd);

        for (Events event : events) {
            processEventNotifications(event, now);
        }
    }

    /**
     * 개별 일정의 알림 설정을 확인하여 발송 시점이 맞으면 알림 전송
     */
    private void processEventNotifications(Events event, LocalDateTime now) {
        for (EventsNotifications noti : event.getNotifications()) {
            // 알림 발송 시각 계산: 일정 시작 시각 - minutesBefore
            LocalDateTime notifyAt = event.getStartAt()
                    .minusMinutes(noti.getMinutesBefore())
                    .withSecond(0).withNano(0);

            // 현재 분과 정확히 일치할 때만 발송 (중복 발송 방지)
            if (notifyAt.equals(now)) {
                sendScheduledAlarm(event, noti);
            }
        }
    }

    /**
     * 예약 알림 발송
     */
    private void sendScheduledAlarm(Events event, EventsNotifications noti) {
        try {
            String methodLabel = switch (noti.getEventsNotiMethod()) {
                case POPUP -> "팝업";
                case EMAIL -> "이메일";
                case PUSH -> "푸시";
            };

            AlarmEvent alarm = AlarmEvent.builder()
                    .companyId(event.getCompanyId())
                    .alarmType("Calendar")
                    .alarmTitle("일정 알림")
                    .alarmContent(noti.getMinutesBefore() + "분 후 일정: " + event.getTitle())
                    .alarmLink("/calendar?eventId=" + event.getEventsId())
                    .alarmRefType("EVENT_REMINDER")
                    .alarmRefId(event.getEventsId())
                    .empIds(List.of(event.getEmpId()))
                    .build();

            alarmEventPublisher.publisher(alarm);

            log.info("예약 알림 발송 완료 - 일정: {}, 방식: {}, {}분 전",
                    event.getTitle(), methodLabel, noti.getMinutesBefore());

        } catch (Exception e) {
            log.error("예약 알림 발송 실패 - 일정ID: {}, 오류: {}",
                    event.getEventsId(), e.getMessage());
        }
    }
}
```

---

## 7. 사원 정보 조회 (서비스 간 통신)

> 캘린더에서 타인의 이름/부서/직급을 표시하려면 hr-service API를 호출해야 합니다.
> 기존 `HrServiceClient` + `HrCacheService` 패턴(InternalDeptController 방식)을 그대로 따릅니다.

### 7-1. hr-service 측 — Internal API 추가

#### `InternalEmployeeResponseDto.java`

**파일 위치:** `hr-service/src/main/java/com/peoplecore/employee/dto/InternalEmployeeResponseDto.java`

```java
package com.peoplecore.employee.dto;

import com.peoplecore.employee.domain.Employee;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternalEmployeeResponseDto {
    private Long empId;
    private String empName;
    private String deptName;
    private String gradeName;
    private String titleName;
    private String empProfileImageUrl;

    public static InternalEmployeeResponseDto from(Employee employee) {
        return InternalEmployeeResponseDto.builder()
                .empId(employee.getEmpId())
                .empName(employee.getEmpName())
                .deptName(employee.getDept() != null ? employee.getDept().getDeptName() : null)
                .gradeName(employee.getGrade() != null ? employee.getGrade().getGradeName() : null)
                .titleName(employee.getTitle() != null ? employee.getTitle().getTitleName() : null)
                .empProfileImageUrl(employee.getEmpProfileImageUrl())
                .build();
    }
}
```

#### `InternalEmployeeController.java`

**파일 위치:** `hr-service/src/main/java/com/peoplecore/employee/controller/InternalEmployeeController.java`

> 기존 `InternalDeptController` 패턴 동일. `/internal/employee` 경로.

```java
package com.peoplecore.employee.controller;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.dto.InternalEmployeeResponseDto;
import com.peoplecore.employee.repository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/employee")
public class InternalEmployeeController {

    private final EmployeeRepository employeeRepository;

    @Autowired
    public InternalEmployeeController(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    /** 단건 조회 */
    @GetMapping("/{empId}")
    public ResponseEntity<InternalEmployeeResponseDto> getEmployee(@PathVariable Long empId) {
        Employee employee = employeeRepository.findById(empId)
                .orElseThrow(() -> new RuntimeException("사원을 찾을 수 없습니다."));
        return ResponseEntity.ok(InternalEmployeeResponseDto.from(employee));
    }

    /** 다건 조회 (캘린더 목록에서 타인 정보 일괄 조회용) */
    @GetMapping("/bulk")
    public ResponseEntity<List<InternalEmployeeResponseDto>> getEmployeesBulk(
            @RequestParam List<Long> empIds) {
        List<Employee> employees = employeeRepository.findAllById(empIds);
        List<InternalEmployeeResponseDto> result = employees.stream()
                .map(InternalEmployeeResponseDto::from)
                .toList();
        return ResponseEntity.ok(result);
    }
}
```

> **참고:** `findAllById`는 JpaRepository 기본 제공 메서드. 별도 쿼리 작성 불필요.
> 단, Employee → Department/Grade/Title이 `LAZY`이므로 N+1이 발생합니다.
> EmployeeRepository에 아래 메서드를 추가하세요.

#### `EmployeeRepository.java` — 추가 메서드

**파일 위치:** `hr-service/src/main/java/com/peoplecore/employee/repository/EmployeeRepository.java`

```java
// 기존 메서드들 아래에 추가

@Query("SELECT e FROM Employee e " +
       "JOIN FETCH e.dept " +
       "JOIN FETCH e.grade " +
       "LEFT JOIN FETCH e.title " +
       "WHERE e.empId IN :empIds AND e.deleteAt IS NULL")
List<Employee> findByEmpIdsWithDeptAndGrade(@Param("empIds") List<Long> empIds);
```

> `title`은 nullable이므로 `LEFT JOIN FETCH` 사용.
> 이 메서드를 `InternalEmployeeController.getEmployeesBulk()`에서 사용하세요.

`InternalEmployeeController` 수정:

```java
/** 다건 조회 — N+1 방지 버전 */
@GetMapping("/bulk")
public ResponseEntity<List<InternalEmployeeResponseDto>> getEmployeesBulk(
        @RequestParam List<Long> empIds) {
    List<Employee> employees = employeeRepository.findByEmpIdsWithDeptAndGrade(empIds);
    List<InternalEmployeeResponseDto> result = employees.stream()
            .map(InternalEmployeeResponseDto::from)
            .toList();
    return ResponseEntity.ok(result);
}
```

---

### 7-2. collaboration-service 측 — 기존 Client에 메서드 추가

#### `EmployeeSimpleResponse.java` (신규)

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/client/dto/EmployeeSimpleResponse.java`

```java
package com.peoplecore.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeSimpleResponse {
    private Long empId;
    private String empName;
    private String deptName;
    private String gradeName;
    private String titleName;
    private String empProfileImageUrl;
}
```

#### `HrServiceClient.java` — 메서드 추가

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/client/component/HrServiceClient.java`

> 기존 `getDept()`, `getCompany()` 아래에 추가

```java
// ─── 기존 코드 아래에 추가 ───

@CircuitBreaker(name = "hrService", fallbackMethod = "getEmployeesBulkFallback")
public List<EmployeeSimpleResponse> getEmployeesBulk(List<Long> empIds) {
    String ids = empIds.stream()
            .map(String::valueOf)
            .collect(Collectors.joining(","));
    return restClient.get()
            .uri("/internal/employee/bulk?empIds={ids}", ids)
            .retrieve()
            .body(new ParameterizedTypeReference<List<EmployeeSimpleResponse>>() {});
}

public List<EmployeeSimpleResponse> getEmployeesBulkFallback(List<Long> empIds, Throwable t) {
    log.warn("HR 서비스 사원 일괄 조회 실패 empIds: {}, error: {}", empIds, t.getMessage());
    throw new BusinessException("HR 서비스 연결 실패: 사원 정보를 조회할 수 없습니다.", HttpStatus.SERVICE_UNAVAILABLE);
}
```

> **import 추가 필요:**
> ```java
> import com.peoplecore.client.dto.EmployeeSimpleResponse;
> import org.springframework.core.ParameterizedTypeReference;
> import java.util.List;
> import java.util.stream.Collectors;
> ```

#### `HrCacheService.java` — 메서드 추가

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/client/component/HrCacheService.java`

> 기존 `getDept()`, `getCompany()` 아래에 추가.
> 사원 정보는 캐시 키를 `hr:emp:{empId}`로 개별 캐싱합니다.

```java
// ─── 기존 코드 아래에 추가 ───

private static final String EMP_KEY = "hr:emp:";

/**
 * 사원 정보 일괄 조회 (캐시 우선)
 * 1) Redis에서 개별 캐시 조회
 * 2) 캐시 미스된 empId만 모아서 hr-service 호출
 * 3) 호출 결과 캐싱 후 병합 반환
 */
public List<EmployeeSimpleResponse> getEmployeesBulk(List<Long> empIds) {
    List<EmployeeSimpleResponse> result = new ArrayList<>();
    List<Long> missedIds = new ArrayList<>();

    // 1) 캐시 조회
    for (Long empId : empIds) {
        try {
            EmployeeSimpleResponse cached =
                    (EmployeeSimpleResponse) redisTemplate.opsForValue().get(EMP_KEY + empId);
            if (cached != null) {
                result.add(cached);
                continue;
            }
        } catch (Exception e) {
            log.warn("Redis 조회 실패 empId={}, error={}", empId, e.getMessage());
        }
        missedIds.add(empId);
    }

    // 2) 캐시 미스분 hr-service 호출
    if (!missedIds.isEmpty()) {
        List<EmployeeSimpleResponse> fetched = hrServiceClient.getEmployeesBulk(missedIds);

        // 3) 캐싱 + 결과 병합
        for (EmployeeSimpleResponse emp : fetched) {
            try {
                redisTemplate.opsForValue().set(EMP_KEY + emp.getEmpId(), emp, TTL);
            } catch (Exception e) {
                log.warn("Redis 저장 실패 empId={}, error={}", emp.getEmpId(), e.getMessage());
            }
            result.add(emp);
        }
    }

    return result;
}

public void evictEmployee(Long empId) {
    try {
        redisTemplate.delete(EMP_KEY + empId);
        log.info("사원 캐시 무효화 empId={}", empId);
    } catch (Exception e) {
        log.warn("사원 캐시 무효화 실패 empId={}", empId);
    }
}
```

> **import 추가 필요:**
> ```java
> import com.peoplecore.client.dto.EmployeeSimpleResponse;
> import java.util.ArrayList;
> ```

---

### 7-3. 서비스에서 사용 예시

캘린더 서비스에서 타인 사원 정보가 필요할 때 `HrCacheService`를 주입받아 사용합니다.

```java
// CalendarEventService 또는 InterestCalendarService에서

private final HrCacheService hrCacheService;

// 일정 목록에서 작성자 정보 보강
public List<EventResponse> getEvents(...) {
    List<Events> events = ...;  // DB 조회

    // empId 목록 추출 (중복 제거)
    List<Long> empIds = events.stream()
            .map(Events::getEmpId)
            .distinct()
            .toList();

    // hr-service 일괄 조회 (캐시 우선)
    Map<Long, EmployeeSimpleResponse> empMap = hrCacheService.getEmployeesBulk(empIds)
            .stream()
            .collect(Collectors.toMap(EmployeeSimpleResponse::getEmpId, e -> e));

    // Response 변환 시 사원 정보 매핑
    return events.stream()
            .map(event -> EventResponse.from(event, empMap.get(event.getEmpId())))
            .toList();
}
```

> **핵심:** 건건이 호출하지 않고 empId를 모아서 **한 번에 조회** → Map으로 변환 → 매핑

---

## 8. 전사 일정 관리 (Admin 전용)

> 전사 일정(`isAllEmployees=true`)은 **HR_SUPER_ADMIN / HR_ADMIN**만 등록·수정·삭제할 수 있습니다.
> 일반 사원의 `createEvent`는 항상 `isAllEmployees=false`로 고정되며,
> 일정 등록 시 캘린더 목록 API(`getMyCalendars`)는 본인 캘린더만 반환하므로 전사캘린더는 선택지에 노출되지 않습니다.
>
> 전사 일정은 `myCalendars`(개인 캘린더)에 종속되지 않고, `myCalendars = null`로 저장합니다.
> 조회 시에는 기존 `getEventsForView()`의 4번 단계에서 `findCompanyEvents()`로 자동 통합됩니다.

### 8-1. `CompanyEventRequest.java` (신규)

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/dto/CompanyEventRequest.java`

```java
package com.peoplecore.calendar.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyEventRequest {
    private String title;
    private String description;
    private String location;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Boolean isAllDay;
}
```

### 8-2. `CompanyEventResponse.java` (신규)

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/dto/CompanyEventResponse.java`

```java
package com.peoplecore.calendar.dto;

import com.peoplecore.calendar.entity.Events;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyEventResponse {
    private Long eventsId;
    private String title;
    private String description;
    private String location;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Boolean isAllDay;
    private UUID companyId;
    private String creatorName;
    private LocalDateTime createdAt;

    public static CompanyEventResponse from(Events event, String creatorName) {
        return CompanyEventResponse.builder()
                .eventsId(event.getEventsId())
                .title(event.getTitle())
                .description(event.getDescription())
                .location(event.getLocation())
                .startAt(event.getStartAt())
                .endAt(event.getEndAt())
                .isAllDay(event.getIsAllDay())
                .companyId(event.getCompanyId())
                .creatorName(creatorName)
                .createdAt(event.getCreatedAt())
                .build();
    }
}
```

### 8-3. `CompanyEventService.java` (신규)

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/service/CompanyEventService.java`

```java
package com.peoplecore.calendar.service;

import com.peoplecore.alarm.publisher.AlarmEventPublisher;
import com.peoplecore.calendar.dto.CompanyEventRequest;
import com.peoplecore.calendar.dto.CompanyEventResponse;
import com.peoplecore.calendar.entity.Events;
import com.peoplecore.calendar.repository.EventsRepository;
import com.peoplecore.client.component.HrCacheService;
import com.peoplecore.client.dto.EmployeeSimpleResponse;
import com.peoplecore.event.AlarmEvent;
import com.peoplecore.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CompanyEventService {

    private final EventsRepository eventsRepository;
    private final HrCacheService hrCacheService;
    private final AlarmEventPublisher alarmEventPublisher;

    @Autowired
    public CompanyEventService(EventsRepository eventsRepository,
                               HrCacheService hrCacheService,
                               AlarmEventPublisher alarmEventPublisher) {
        this.eventsRepository = eventsRepository;
        this.hrCacheService = hrCacheService;
        this.alarmEventPublisher = alarmEventPublisher;
    }

    // ────────────────────────────────────────────
    // 전사 일정 등록
    // ────────────────────────────────────────────
    @Transactional
    public CompanyEventResponse createCompanyEvent(UUID companyId, Long empId,
                                                    CompanyEventRequest request) {
        Events event = Events.builder()
                .empId(empId)
                .title(request.getTitle())
                .description(request.getDescription())
                .location(request.getLocation())
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .isAllDay(request.getIsAllDay())
                .isPublic(true)
                .isAllEmployees(true)       // ← 전사 일정 핵심
                .companyId(companyId)
                .myCalendars(null)          // ← 개인 캘린더에 종속 안 됨
                .build();

        eventsRepository.save(event);

        // 전 직원에게 알림 (Kafka 비동기)
        // empIds를 빈 리스트로 보내면 AlarmService에서 전사 발송 처리
        // 또는 별도 전사 알림용 alarmRefType 사용
        alarmEventPublisher.publisher(AlarmEvent.builder()
                .companyId(companyId)
                .alarmType("Calendar")
                .alarmTitle("전사 일정 등록")
                .alarmContent(request.getTitle() + " 전사 일정이 등록되었습니다.")
                .alarmLink("/calendar?eventId=" + event.getEventsId())
                .alarmRefType("COMPANY_EVENT")
                .alarmRefId(event.getEventsId())
                .empIds(List.of())  // 전사 발송 — AlarmService에서 회사 전 직원 대상 처리
                .build());

        return CompanyEventResponse.from(event, getCreatorName(empId));
    }

    // ────────────────────────────────────────────
    // 전사 일정 수정
    // ────────────────────────────────────────────
    @Transactional
    public CompanyEventResponse updateCompanyEvent(UUID companyId, Long empId,
                                                    Long eventsId,
                                                    CompanyEventRequest request) {
        Events event = findCompanyEventOrThrow(eventsId, companyId);

        event.update(
                request.getTitle(),
                request.getDescription(),
                request.getLocation(),
                request.getStartAt(),
                request.getEndAt(),
                request.getIsAllDay(),
                true,               // isPublic 항상 true
                null                // myCalendars 없음
        );

        return CompanyEventResponse.from(event, getCreatorName(event.getEmpId()));
    }

    // ────────────────────────────────────────────
    // 전사 일정 삭제 (소프트 삭제)
    // ────────────────────────────────────────────
    @Transactional
    public void deleteCompanyEvent(UUID companyId, Long eventsId) {
        Events event = findCompanyEventOrThrow(eventsId, companyId);
        event.softDelete();
    }

    // ────────────────────────────────────────────
    // 전사 일정 목록 조회 (페이징)
    // ────────────────────────────────────────────
    public Page<CompanyEventResponse> getCompanyEvents(UUID companyId, Pageable pageable) {
        Page<Events> page = eventsRepository
                .findByCompanyIdAndIsAllEmployeesTrueAndDeletedAtIsNullOrderByStartAtDesc(
                        companyId, pageable);

        // 작성자 이름 일괄 조회
        List<Long> empIds = page.getContent().stream()
                .map(Events::getEmpId)
                .distinct()
                .toList();
        Map<Long, String> nameMap = getCreatorNameMap(empIds);

        return page.map(event ->
                CompanyEventResponse.from(event, nameMap.get(event.getEmpId())));
    }

    // ────────────────────────────────────────────
    // 전사 일정 상세 조회
    // ────────────────────────────────────────────
    public CompanyEventResponse getCompanyEvent(UUID companyId, Long eventsId) {
        Events event = findCompanyEventOrThrow(eventsId, companyId);
        return CompanyEventResponse.from(event, getCreatorName(event.getEmpId()));
    }

    // ────────────────────────────────────────────
    // Private 헬퍼
    // ────────────────────────────────────────────

    private Events findCompanyEventOrThrow(Long eventsId, UUID companyId) {
        Events event = eventsRepository.findById(eventsId)
                .orElseThrow(() -> new BusinessException(
                        "전사 일정을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        if (!event.getCompanyId().equals(companyId)) {
            throw new BusinessException("접근 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }
        if (!Boolean.TRUE.equals(event.getIsAllEmployees())) {
            throw new BusinessException("전사 일정이 아닙니다.", HttpStatus.BAD_REQUEST);
        }
        if (event.isDeleted()) {
            throw new BusinessException("삭제된 일정입니다.", HttpStatus.NOT_FOUND);
        }
        return event;
    }

    private String getCreatorName(Long empId) {
        List<EmployeeSimpleResponse> emps = hrCacheService.getEmployeesBulk(List.of(empId));
        return emps.isEmpty() ? null : emps.get(0).getEmpName();
    }

    private Map<Long, String> getCreatorNameMap(List<Long> empIds) {
        return hrCacheService.getEmployeesBulk(empIds).stream()
                .collect(Collectors.toMap(
                        EmployeeSimpleResponse::getEmpId,
                        EmployeeSimpleResponse::getEmpName,
                        (a, b) -> a));
    }
}
```

### 8-4. `CompanyEventController.java` (신규)

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/controller/CompanyEventController.java`

> `@RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})` 클래스 레벨 적용 → 모든 엔드포인트 Admin 전용

```java
package com.peoplecore.calendar.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.calendar.dto.CompanyEventRequest;
import com.peoplecore.calendar.dto.CompanyEventResponse;
import com.peoplecore.calendar.service.CompanyEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/calendar/company-events")
@RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
public class CompanyEventController {

    private final CompanyEventService companyEventService;

    @Autowired
    public CompanyEventController(CompanyEventService companyEventService) {
        this.companyEventService = companyEventService;
    }

    /** 전사 일정 등록 */
    @PostMapping
    public ResponseEntity<CompanyEventResponse> createCompanyEvent(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestBody CompanyEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(companyEventService.createCompanyEvent(companyId, empId, request));
    }

    /** 전사 일정 수정 */
    @PutMapping("/{eventsId}")
    public ResponseEntity<CompanyEventResponse> updateCompanyEvent(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long eventsId,
            @RequestBody CompanyEventRequest request) {
        return ResponseEntity.ok(
                companyEventService.updateCompanyEvent(companyId, empId, eventsId, request));
    }

    /** 전사 일정 삭제 */
    @DeleteMapping("/{eventsId}")
    public ResponseEntity<Void> deleteCompanyEvent(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long eventsId) {
        companyEventService.deleteCompanyEvent(companyId, eventsId);
        return ResponseEntity.noContent().build();
    }

    /** 전사 일정 목록 조회 (Admin 관리 화면용, 페이징) */
    @GetMapping
    public ResponseEntity<Page<CompanyEventResponse>> getCompanyEvents(
            @RequestHeader("X-User-Company") UUID companyId,
            Pageable pageable) {
        return ResponseEntity.ok(companyEventService.getCompanyEvents(companyId, pageable));
    }

    /** 전사 일정 상세 조회 */
    @GetMapping("/{eventsId}")
    public ResponseEntity<CompanyEventResponse> getCompanyEvent(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long eventsId) {
        return ResponseEntity.ok(companyEventService.getCompanyEvent(companyId, eventsId));
    }
}
```

### 8-5. `EventsRepository.java` — 전사 일정 페이징 쿼리 추가

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/repository/EventsRepository.java`

```java
// 기존 메서드들 아래에 추가

/** 전사 일정 목록 (Admin 관리 화면 + 페이징) */
Page<Events> findByCompanyIdAndIsAllEmployeesTrueAndDeletedAtIsNullOrderByStartAtDesc(
        UUID companyId, Pageable pageable);
```

### 8-6. 기존 `CalendarEventService.createEvent()` 전사캘린더 차단 확인

기존 일반 사원용 `createEvent`는 이미 `isAllEmployees(false)`로 고정되어 있습니다:

```java
// CalendarEventService.createEvent() 내부 — 이미 반영됨
Events event = Events.builder()
        ...
        .isAllEmployees(false)    // ← 일반 사원은 전사 일정 생성 불가
        .myCalendars(calendar)    // ← 반드시 개인 캘린더에 종속
        .build();
```

> **프론트엔드 참고:** 일정 등록 화면의 캘린더 선택 드롭다운은
> `GET /api/calendar/my-calendars` 호출 결과를 사용하면 됩니다.
> 이 API는 본인의 `MyCalendars`만 반환하므로 전사캘린더는 선택지에 **자동으로 노출되지 않습니다.**

---

## 9. 참석자 초대 · 승인 · 거절

> 일정 등록 시 참석자를 선택하면 PENDING 상태로 초대 레코드가 생성되고 Kafka 알림이 발송됩니다.
> 참석자가 승인하면 해당 참석자의 **기본 캘린더에 일정이 연결**되고, 일정 생성자에게 응답 알림이 갑니다.
> 거절 시에도 생성자에게 거절 알림이 발송됩니다.

### 9-1. `EventAttendees.java` 수정 — Events 직접 참조 + 비즈니스 메서드

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/entity/EventAttendees.java`

> 기존 엔티티가 `EventInstances`를 참조하고 있지만, 단일 일정 참석자 플로우에서는
> `Events`를 직접 참조하는 것이 훨씬 단순합니다. `EventInstances`는 반복 일정의
> 개별 인스턴스 관리용이므로, 참석자는 원본 일정(`Events`)에 연결합니다.

```java
package com.peoplecore.calendar.entity;

import com.peoplecore.calendar.enums.InviteStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventAttendees {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long eventAttendeesId;

    // 초대받은 사원 ID
    private Long invitedEmpId;

    // 참석 상태
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private InviteStatus inviteStatus = InviteStatus.PENDING;

    private String rejectReason;

    @Builder.Default
    private Boolean isHidden = false;

    private LocalDateTime invitedAt;
    private LocalDateTime respondedAt;

    @Column(nullable = false)
    private UUID companyId;

    // ── 변경: EventInstances → Events 직접 참조 ──
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "events_id", nullable = false)
    private Events events;

    // ────────────────────────────────────────────
    // 비즈니스 메서드
    // ────────────────────────────────────────────

    /** 참석 승인 */
    public void approve() {
        this.inviteStatus = InviteStatus.APPROVED;
        this.respondedAt = LocalDateTime.now();
    }

    /** 참석 거절 */
    public void reject(String reason) {
        this.inviteStatus = InviteStatus.REJECTED;
        this.rejectReason = reason;
        this.respondedAt = LocalDateTime.now();
    }

    /** 참석자 본인 확인 */
    public boolean isOwner(Long empId) {
        return this.invitedEmpId.equals(empId);
    }
}
```

> **DDL 변경 필요:** 기존 `event_instances` FK를 `events_id` FK로 변경해야 합니다.

### 9-2. `EventAttendeesRepository.java` (신규)

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/repository/EventAttendeesRepository.java`

```java
package com.peoplecore.calendar.repository;

import com.peoplecore.calendar.entity.EventAttendees;
import com.peoplecore.calendar.enums.InviteStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventAttendeesRepository extends JpaRepository<EventAttendees, Long> {

    /** 일정별 참석자 목록 */
    List<EventAttendees> findByEvents_EventsIdAndCompanyId(Long eventsId, UUID companyId);

    /** 특정 참석자 조회 (응답 처리용) */
    Optional<EventAttendees> findByEvents_EventsIdAndInvitedEmpIdAndCompanyId(
            Long eventsId, Long invitedEmpId, UUID companyId);

    /** 내가 받은 초대 목록 (PENDING만) */
    List<EventAttendees> findByInvitedEmpIdAndCompanyIdAndInviteStatus(
            Long invitedEmpId, UUID companyId, InviteStatus status);
}
```

### 9-3. `AttendeeResDto.java` (신규)

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/dtos/AttendeeResDto.java`

```java
package com.peoplecore.calendar.dtos;

import com.peoplecore.calendar.entity.EventAttendees;
import com.peoplecore.calendar.enums.InviteStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AttendeeResDto {

    private Long eventAttendeesId;
    private Long invitedEmpId;
    private InviteStatus inviteStatus;
    private String rejectReason;
    private LocalDateTime invitedAt;
    private LocalDateTime respondedAt;

    public static AttendeeResDto fromEntity(EventAttendees attendee) {
        return AttendeeResDto.builder()
                .eventAttendeesId(attendee.getEventAttendeesId())
                .invitedEmpId(attendee.getInvitedEmpId())
                .inviteStatus(attendee.getInviteStatus())
                .rejectReason(attendee.getRejectReason())
                .invitedAt(attendee.getInvitedAt())
                .respondedAt(attendee.getRespondedAt())
                .build();
    }
}
```

### 9-4. `AttendeeRespondReqDto.java` (신규)

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/dtos/AttendeeRespondReqDto.java`

```java
package com.peoplecore.calendar.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttendeeRespondReqDto {
    private Boolean accepted;       // true=승인, false=거절
    private String rejectReason;    // 거절 시 사유 (optional)
}
```

### 9-5. `EventResDto.java` 수정 — 참석자 목록 추가

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/dtos/EventResDto.java`

> 기존 `fromEntity` 메서드에서는 참석자를 포함할 수 없으므로 (Events 엔티티에 attendees 관계가 없음),
> **참석자를 외부에서 세팅**하는 방식으로 처리합니다.

```java
// ─── 기존 필드 아래에 추가 ───

private List<AttendeeResDto> attendees;

// ─── fromEntity는 기존 그대로 유지 (attendees=null) ───
// 참석자 포함이 필요한 곳에서는 아래 오버로드 사용:

public static EventResDto fromEntityWithAttendees(Events events, List<AttendeeResDto> attendees) {
    EventResDto dto = fromEntity(events);
    dto.setAttendees(attendees);
    return dto;
}
```

### 9-6. `CalendarEventService.java` 수정 — 참석자 저장 + 응답 처리

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/service/CalendarEventService.java`

> `createEvent()`에 참석자 레코드 저장 로직을 추가하고,
> 참석자 응답(승인/거절) 메서드를 새로 작성합니다.

#### 의존성 추가

```java
// 기존 필드에 추가
private final EventAttendeesRepository eventAttendeesRepository;
private final MyCalendarsRepository myCalendarsRepository;  // 이미 있음

// 생성자에 EventAttendeesRepository 추가
@Autowired
public CalendarEventService(MyCalendarsRepository myCalendarsRepository,
                            EventsRepository eventsRepository,
                            RepeatedRulesRepository repeatedRulesRepository,
                            EventsNotificationsRepository eventsNotificationsRepository,
                            InterestCalendarsRepository interestCalendarsRepository,
                            AlarmEventPublisher alarmEventPublisher,
                            EventAttendeesRepository eventAttendeesRepository) {
    this.myCalendarsRepository = myCalendarsRepository;
    this.eventsRepository = eventsRepository;
    this.repeatedRulesRepository = repeatedRulesRepository;
    this.eventsNotificationsRepository = eventsNotificationsRepository;
    this.interestCalendarsRepository = interestCalendarsRepository;
    this.alarmEventPublisher = alarmEventPublisher;
    this.eventAttendeesRepository = eventAttendeesRepository;
}
```

#### createEvent() 수정 — 참석자 레코드 저장

```java
@Transactional
public EventResDto createEvent(UUID companyId, Long empId, EventCreateReqDto reqDto) {
    MyCalendars calendar = myCalendarsRepository.findById(reqDto.getMyCalendarsId())
            .orElseThrow(() -> new CustomException(ErrorCode.CALENDAR_NOT_FOUND));
    validateCalendarOwner(calendar, empId);

    // 반복규칙 저장
    RepeatedRules repeatedRules = saveRepeatedRule(companyId, reqDto.getRepeatedRule());

    Events event = Events.builder()
            .empId(empId)
            .title(reqDto.getTitle())
            .description(reqDto.getDescription())
            .location(reqDto.getLocation())
            .startAt(reqDto.getStartAt())
            .endAt(reqDto.getEndAt())
            .isAllDay(reqDto.getIsAllDay())
            .isPublic(reqDto.getIsPublic())
            .isAllEmployees(false)
            .companyId(companyId)
            .myCalendars(calendar)
            .repeatedRules(repeatedRules)
            .build();

    eventsRepository.save(event);

    // 알림설정 저장
    saveNotifications(event, reqDto.getNotifications());

    // ── 참석자 레코드 저장 (추가) ──
    saveAttendees(companyId, event, reqDto.getAttendeeEmpIds());

    // 참석자에게 초대 알림 발송 (Kafka)
    sendAttendeeAlarm(companyId, event, reqDto.getAttendeeEmpIds());

    return EventResDto.fromEntity(event);
}
```

#### 참석자 저장 Private 메서드

```java
// ── 참석자 레코드 저장 ──
private void saveAttendees(UUID companyId, Events event, List<Long> attendeeEmpIds) {
    if (attendeeEmpIds == null || attendeeEmpIds.isEmpty()) return;

    List<EventAttendees> attendees = attendeeEmpIds.stream()
            .map(invitedEmpId -> EventAttendees.builder()
                    .invitedEmpId(invitedEmpId)
                    .inviteStatus(InviteStatus.PENDING)
                    .isHidden(false)
                    .invitedAt(LocalDateTime.now())
                    .companyId(companyId)
                    .events(event)
                    .build())
            .toList();

    eventAttendeesRepository.saveAll(attendees);
}
```

#### 참석자 응답 (승인/거절) 메서드

```java
// ────────────────────────────────────────────
// 참석자 응답 처리 (승인 / 거절)
// ────────────────────────────────────────────
@Transactional
public AttendeeResDto respondToInvite(UUID companyId, Long empId, Long eventsId,
                                       AttendeeRespondReqDto reqDto) {

    // 1. 참석자 레코드 조회
    EventAttendees attendee = eventAttendeesRepository
            .findByEvents_EventsIdAndInvitedEmpIdAndCompanyId(eventsId, empId, companyId)
            .orElseThrow(() -> new CustomException(ErrorCode.ATTENDEE_NOT_FOUND));

    // 이미 응답한 경우
    if (attendee.getInviteStatus() != InviteStatus.PENDING) {
        throw new CustomException(ErrorCode.ATTENDEE_ALREADY_RESPONDED);
    }

    // 2. 승인 / 거절 처리
    if (Boolean.TRUE.equals(reqDto.getAccepted())) {
        attendee.approve();

        // 3. 승인 시 → 참석자의 기본 캘린더에 일정 복제
        copyEventToAttendeeCalendar(companyId, empId, attendee.getEvents());

        // 4. 생성자에게 "승인" 알림
        sendRespondAlarm(companyId, attendee.getEvents(), empId, true);
    } else {
        attendee.reject(reqDto.getRejectReason());

        // 4. 생성자에게 "거절" 알림
        sendRespondAlarm(companyId, attendee.getEvents(), empId, false);
    }

    return AttendeeResDto.fromEntity(attendee);
}

// ── 승인 시 참석자 캘린더에 일정 복제 ──
private void copyEventToAttendeeCalendar(UUID companyId, Long attendeeEmpId, Events originalEvent) {

    // 참석자의 기본 캘린더 조회
    MyCalendars defaultCalendar = myCalendarsRepository
            .findByCompanyIdAndEmpIdAndIsDefaultTrue(companyId, attendeeEmpId)
            .orElseThrow(() -> new CustomException(ErrorCode.CALENDAR_NOT_FOUND));

    // 원본 일정을 참석자의 기본 캘린더에 복제 저장
    Events copied = Events.builder()
            .empId(attendeeEmpId)
            .title(originalEvent.getTitle())
            .description(originalEvent.getDescription())
            .location(originalEvent.getLocation())
            .startAt(originalEvent.getStartAt())
            .endAt(originalEvent.getEndAt())
            .isAllDay(originalEvent.getIsAllDay())
            .isPublic(false)              // 참석자 본인 캘린더 → 비공개
            .isAllEmployees(false)
            .companyId(companyId)
            .myCalendars(defaultCalendar)
            .repeatedRules(originalEvent.getRepeatedRules())
            .build();

    eventsRepository.save(copied);
}

// ── 응답 알림 발송 (생성자에게) ──
private void sendRespondAlarm(UUID companyId, Events event, Long respondEmpId, boolean accepted) {
    String status = accepted ? "수락" : "거절";

    alarmEventPublisher.publisher(AlarmEvent.builder()
            .companyId(companyId)
            .alarmType("Calendar")
            .alarmContent(event.getTitle() + " 일정 초대를 " + status + "하였습니다")
            .alarmLink("/calendar?eventId=" + event.getEventsId())
            .alarmRefType("EVENT_RESPOND")
            .alarmRefId(event.getEventsId())
            .empIds(List.of(event.getEmpId()))  // 일정 생성자에게만
            .build());
}
```

#### 일정 상세 조회 수정 — 참석자 포함

```java
// 기존 getEvent() 수정
public EventResDto getEvent(UUID companyId, Long eventsId) {
    Events event = findEventOrThrow(eventsId, companyId);

    // 참석자 목록 조회
    List<AttendeeResDto> attendees = eventAttendeesRepository
            .findByEvents_EventsIdAndCompanyId(eventsId, companyId)
            .stream()
            .map(AttendeeResDto::fromEntity)
            .toList();

    return EventResDto.fromEntityWithAttendees(event, attendees);
}
```

#### 필요한 import 추가

```java
import com.peoplecore.calendar.dtos.AttendeeResDto;
import com.peoplecore.calendar.dtos.AttendeeRespondReqDto;
import com.peoplecore.calendar.entity.EventAttendees;
import com.peoplecore.calendar.enums.InviteStatus;
import com.peoplecore.calendar.repository.EventAttendeesRepository;
```

### 9-7. `MyCalendarsRepository.java` — 기본 캘린더 조회 메서드 추가

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/repository/MyCalendarsRepository.java`

```java
// 기존 메서드들 아래에 추가

/** 기본 캘린더 조회 (참석 승인 시 사용) */
Optional<MyCalendars> findByCompanyIdAndEmpIdAndIsDefaultTrue(UUID companyId, Long empId);
```

### 9-8. `CalendarEventController.java` — 참석자 응답 API 추가

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/controller/CalendarEventController.java`

```java
// 기존 엔드포인트들 아래에 추가

/** 참석자 응답 (승인/거절) */
@PatchMapping("/events/{eventsId}/attendees/respond")
public ResponseEntity<AttendeeResDto> respondToInvite(
        @RequestHeader("X-User-Company") UUID companyId,
        @RequestHeader("X-User-Id") Long empId,
        @PathVariable Long eventsId,
        @RequestBody AttendeeRespondReqDto request) {
    return ResponseEntity.ok(
            calendarEventService.respondToInvite(companyId, empId, eventsId, request));
}

/** 내가 받은 초대 목록 (PENDING) */
@GetMapping("/events/invitations")
public ResponseEntity<List<AttendeeResDto>> getMyInvitations(
        @RequestHeader("X-User-Company") UUID companyId,
        @RequestHeader("X-User-Id") Long empId) {
    return ResponseEntity.ok(
            calendarEventService.getMyPendingInvitations(companyId, empId));
}
```

> `getMyPendingInvitations` 서비스 메서드:

```java
// CalendarEventService에 추가
public List<AttendeeResDto> getMyPendingInvitations(UUID companyId, Long empId) {
    return eventAttendeesRepository
            .findByInvitedEmpIdAndCompanyIdAndInviteStatus(empId, companyId, InviteStatus.PENDING)
            .stream()
            .map(AttendeeResDto::fromEntity)
            .toList();
}
```

### 9-9. 참석자 플로우 요약

```
┌─────────────────────────────────────────────────────────┐
│                   참석자 초대 플로우                       │
│                                                         │
│  1. 일정 생성자가 참석자 선택하여 일정 등록                  │
│     POST /api/calendar/events                           │
│     body: { ..., attendeeEmpIds: [2, 3, 5] }           │
│                                                         │
│  2. EventAttendees 레코드 생성 (PENDING)                  │
│     + Kafka 알림 → 참석자들에게 초대 알림                   │
│                                                         │
│  3. 참석자가 초대 목록 확인                                │
│     GET /api/calendar/events/invitations                │
│                                                         │
│  4. 참석자가 승인/거절 응답                                │
│     PATCH /api/calendar/events/{id}/attendees/respond   │
│     body: { accepted: true }                            │
│            또는 { accepted: false, rejectReason: "..." } │
│                                                         │
│  5-A. 승인 시:                                           │
│     - inviteStatus → APPROVED                           │
│     - 참석자의 기본 캘린더에 일정 복제                      │
│     - 일정 생성자에게 "수락" 알림 (Kafka)                  │
│                                                         │
│  5-B. 거절 시:                                           │
│     - inviteStatus → REJECTED                           │
│     - 일정 생성자에게 "거절" 알림 (Kafka)                  │
│                                                         │
│  6. 일정 상세 조회 시 참석자 목록 + 상태 포함               │
│     GET /api/calendar/events/{id}                       │
│     response: { ..., attendees: [{empId, status}, ...]} │
└─────────────────────────────────────────────────────────┘
```

### 9-10. ErrorCode 추가 필요

```java
// common/exception/ErrorCode.java에 추가

ATTENDEE_NOT_FOUND(HttpStatus.NOT_FOUND, "참석자 정보를 찾을 수 없습니다."),
ATTENDEE_ALREADY_RESPONDED(HttpStatus.BAD_REQUEST, "이미 응답한 초대입니다."),
```

---

## 10. 휴일 관리 (사내휴일 + 법정공휴일)

> **사내휴일**: 전사 일정 등록 시 `isHoliday=true` 체크 → `Holidays` 테이블에 `COMPANY` 타입 자동 INSERT
> **법정공휴일**: 공공데이터포털 API로 자동 동기화 → `Holidays` 테이블에 `NATIONAL` 타입 저장
> **근태 연동**: hr-service에서 `Holidays` 테이블 직접 조회 (common 엔티티)

### 10-1. 전사 일정 `isHoliday` 플래그 추가

#### `CompanyEventRequest.java` 수정

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/dto/CompanyEventRequest.java`

```java
// ─── 기존 필드 아래에 추가 ───

private Boolean isHoliday;   // true 시 사내휴일로 등록 → Holidays 테이블 자동 INSERT
```

#### `CompanyEventResponse.java` 수정

```java
// ─── 기존 필드 아래에 추가 ───

private Boolean isHoliday;   // 사내휴일 여부

// from() 메서드의 builder에 추가:
//  .isHoliday(isHoliday)

// from() 메서드 시그니처 변경:
public static CompanyEventResponse from(Events event, String creatorName, Boolean isHoliday) {
    return CompanyEventResponse.builder()
            .eventsId(event.getEventsId())
            .title(event.getTitle())
            .description(event.getDescription())
            .location(event.getLocation())
            .startAt(event.getStartAt())
            .endAt(event.getEndAt())
            .isAllDay(event.getIsAllDay())
            .companyId(event.getCompanyId())
            .creatorName(creatorName)
            .createdAt(event.getCreatedAt())
            .isHoliday(isHoliday)
            .build();
}
```

### 10-2. `CompanyEventService.java` 수정 — Holidays 자동 연동

```java
// ── 의존성 추가 ──
private final HolidayRepository holidayRepository;

// 생성자에 HolidayRepository 추가
@Autowired
public CompanyEventService(EventsRepository eventsRepository,
                           HrCacheService hrCacheService,
                           AlarmEventPublisher alarmEventPublisher,
                           HolidayRepository holidayRepository) {
    this.eventsRepository = eventsRepository;
    this.hrCacheService = hrCacheService;
    this.alarmEventPublisher = alarmEventPublisher;
    this.holidayRepository = holidayRepository;
}
```

#### createCompanyEvent() 수정

```java
@Transactional
public CompanyEventResponse createCompanyEvent(UUID companyId, Long empId,
                                                CompanyEventRequest request) {
    Events event = Events.builder()
            .empId(empId)
            .title(request.getTitle())
            .description(request.getDescription())
            .location(request.getLocation())
            .startAt(request.getStartAt())
            .endAt(request.getEndAt())
            .isAllDay(request.getIsAllDay())
            .isPublic(true)
            .isAllEmployees(true)
            .companyId(companyId)
            .myCalendars(null)
            .build();

    eventsRepository.save(event);

    // ── 사내휴일 자동 등록 (추가) ──
    boolean isHoliday = Boolean.TRUE.equals(request.getIsHoliday());
    if (isHoliday) {
        saveCompanyHoliday(companyId, empId, event);
    }

    // 전 직원에게 알림 (Kafka)
    alarmEventPublisher.publisher(AlarmEvent.builder()
            .companyId(companyId)
            .alarmType("Calendar")
            .alarmTitle("전사 일정 등록")
            .alarmContent(request.getTitle() + " 전사 일정이 등록되었습니다.")
            .alarmLink("/calendar?eventId=" + event.getEventsId())
            .alarmRefType("COMPANY_EVENT")
            .alarmRefId(event.getEventsId())
            .empIds(List.of())
            .build());

    return CompanyEventResponse.from(event, getCreatorName(empId), isHoliday);
}
```

#### 사내휴일 저장 Private 메서드

```java
// ── 사내휴일 Holidays 테이블 INSERT ──
private void saveCompanyHoliday(UUID companyId, Long empId, Events event) {
    // 종일 일정이면 startAt 날짜만, 아니면 startAt 날짜 기준
    LocalDate holidayDate = event.getStartAt().toLocalDate();

    Holidays holiday = Holidays.builder()
            .date(holidayDate)
            .holidayName(event.getTitle())
            .holidayType(HolidayType.COMPANY)
            .isRepeating(false)             // 사내휴일은 매년 반복 아님 (해당 연도만)
            .companyId(companyId)
            .empId(empId)
            .build();

    holidayRepository.save(holiday);
}
```

> **import 추가:**
> ```java
> import com.peoplecore.entity.Holidays;
> import com.peoplecore.entity.HolidayType;
> import com.peoplecore.calendar.repository.HolidayRepository;
> import java.time.LocalDate;
> ```

#### deleteCompanyEvent() 수정 — 휴일 연동 삭제

```java
@Transactional
public void deleteCompanyEvent(UUID companyId, Long eventsId) {
    Events event = findCompanyEventOrThrow(eventsId, companyId);
    event.softDelete();

    // ── 연동된 사내휴일도 삭제 (추가) ──
    deleteLinkedCompanyHoliday(companyId, event);
}

private void deleteLinkedCompanyHoliday(UUID companyId, Events event) {
    LocalDate holidayDate = event.getStartAt().toLocalDate();
    holidayRepository.deleteByCompanyIdAndDateAndHolidayTypeAndHolidayName(
            companyId, holidayDate, HolidayType.COMPANY, event.getTitle());
}
```

#### HolidayRepository — 삭제 메서드 추가

```java
// HolidayRepository.java에 추가

void deleteByCompanyIdAndDateAndHolidayTypeAndHolidayName(
        UUID companyId, LocalDate date, HolidayType holidayType, String holidayName);
```

### 10-3. 법정공휴일 외부 API 연동

> 공공데이터포털 (data.go.kr)의 **특일정보 API**를 사용하여 법정공휴일을 자동 동기화합니다.
> API: `http://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService/getRestDeInfo`

#### `HolidayApiClient.java` (신규)

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/client/HolidayApiClient.java`

```java
package com.peoplecore.calendar.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class HolidayApiClient {

    @Value("${holiday.api.service-key}")
    private String serviceKey;

    private final RestTemplate restTemplate;

    private static final String API_URL =
            "http://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService/getRestDeInfo";

    @Autowired
    public HolidayApiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 특정 연도의 법정공휴일 목록 조회
     * @param year 조회할 연도 (예: 2026)
     * @return HolidayInfo 리스트
     */
    public List<HolidayInfo> fetchNationalHolidays(int year) {
        List<HolidayInfo> holidays = new ArrayList<>();

        // 1~12월 순회 (API가 월 단위 조회)
        for (int month = 1; month <= 12; month++) {
            fetchMonthHolidays(year, month, holidays);
        }

        return holidays;
    }

    private void fetchMonthHolidays(int year, int month, List<HolidayInfo> holidays) {
        String url = UriComponentsBuilder.fromHttpUrl(API_URL)
                .queryParam("serviceKey", serviceKey)
                .queryParam("solYear", String.valueOf(year))
                .queryParam("solMonth", String.format("%02d", month))
                .queryParam("numOfRows", "30")
                .build()
                .toUriString();

        try {
            String xml = restTemplate.getForObject(url, String.class);
            parseXmlResponse(xml, holidays);
        } catch (Exception e) {
            log.error("법정공휴일 API 조회 실패 year={}, month={}", year, month, e);
        }
    }

    private void parseXmlResponse(String xml, List<HolidayInfo> holidays) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(
                    new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            NodeList items = doc.getElementsByTagName("item");

            for (int i = 0; i < items.getLength(); i++) {
                var item = items.item(i);
                String dateName = getTagValue(item, "dateName");
                String locdate = getTagValue(item, "locdate");
                String isHoliday = getTagValue(item, "isHoliday");

                if ("Y".equals(isHoliday)) {
                    LocalDate date = LocalDate.parse(locdate,
                            DateTimeFormatter.ofPattern("yyyyMMdd"));
                    holidays.add(new HolidayInfo(date, dateName));
                }
            }
        } catch (Exception e) {
            log.error("법정공휴일 XML 파싱 실패", e);
        }
    }

    private String getTagValue(org.w3c.dom.Node item, String tagName) {
        var element = (org.w3c.dom.Element) item;
        NodeList nodeList = element.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent();
        }
        return null;
    }

    // ── Inner DTO ──
    public record HolidayInfo(LocalDate date, String name) {}
}
```

#### `application.yml` 설정 추가

```yaml
# collaboration-service의 application.yml에 추가
holiday:
  api:
    service-key: ${HOLIDAY_API_KEY}   # 공공데이터포털 인증키
```

#### `RestTemplate` Bean 등록

```java
// 기존 Config 클래스에 추가하거나 별도 생성
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

### 10-4. `HolidayResDto.java` (신규)

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/dtos/HolidayResDto.java`

```java
package com.peoplecore.calendar.dtos;

import com.peoplecore.entity.Holidays;
import com.peoplecore.entity.HolidayType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class HolidayResDto {

    private Long holidayId;
    private LocalDate date;
    private String holidayName;
    private HolidayType holidayType;
    private Boolean isRepeating;

    public static HolidayResDto fromEntity(Holidays holiday) {
        return HolidayResDto.builder()
                .holidayId(holiday.getHolidayId())
                .date(holiday.getDate())
                .holidayName(holiday.getHolidayName())
                .holidayType(holiday.getHolidayType())
                .isRepeating(holiday.getIsRepeating())
                .build();
    }
}
```

### 10-5. `HolidayService.java` (신규)

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/service/HolidayService.java`

```java
package com.peoplecore.calendar.service;

import com.peoplecore.calendar.client.HolidayApiClient;
import com.peoplecore.calendar.dtos.HolidayResDto;
import com.peoplecore.calendar.repository.HolidayRepository;
import com.peoplecore.entity.Holidays;
import com.peoplecore.entity.HolidayType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@Transactional(readOnly = true)
public class HolidayService {

    private final HolidayRepository holidayRepository;
    private final HolidayApiClient holidayApiClient;

    @Autowired
    public HolidayService(HolidayRepository holidayRepository,
                          HolidayApiClient holidayApiClient) {
        this.holidayRepository = holidayRepository;
        this.holidayApiClient = holidayApiClient;
    }

    // ────────────────────────────────────────────
    // 기간별 휴일 조회 (캘린더 뷰에서 사용)
    // ────────────────────────────────────────────
    public List<HolidayResDto> getHolidays(UUID companyId, LocalDate start, LocalDate end) {
        return holidayRepository.findByCompanyIdAndPeriod(companyId, start, end)
                .stream()
                .map(HolidayResDto::fromEntity)
                .toList();
    }

    // ────────────────────────────────────────────
    // 법정공휴일 외부 API 동기화
    // ────────────────────────────────────────────
    @Transactional
    public int syncNationalHolidays(UUID companyId, Long adminEmpId, int year) {

        // 1. 외부 API로 해당 연도 법정공휴일 조회
        List<HolidayApiClient.HolidayInfo> fetched = holidayApiClient.fetchNationalHolidays(year);

        // 2. 기존 해당 연도 NATIONAL 공휴일 삭제 후 재삽입 (idempotent)
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        holidayRepository.deleteByCompanyIdAndHolidayTypeAndDateBetween(
                companyId, HolidayType.NATIONAL, yearStart, yearEnd);

        // 3. 신규 INSERT
        List<Holidays> holidays = fetched.stream()
                .map(info -> Holidays.builder()
                        .date(info.date())
                        .holidayName(info.name())
                        .holidayType(HolidayType.NATIONAL)
                        .isRepeating(isYearlyRepeating(info.name()))
                        .companyId(companyId)
                        .empId(adminEmpId)
                        .build())
                .toList();

        holidayRepository.saveAll(holidays);

        log.info("법정공휴일 동기화 완료 companyId={}, year={}, count={}",
                companyId, year, holidays.size());

        return holidays.size();
    }

    // ── 매년 반복 공휴일 판별 (대체공휴일은 false) ──
    private boolean isYearlyRepeating(String holidayName) {
        // 대체공휴일, 임시공휴일 등은 매년 반복 아님
        if (holidayName != null && holidayName.contains("대체")) return false;
        if (holidayName != null && holidayName.contains("임시")) return false;
        return true;
    }
}
```

#### HolidayRepository — 동기화용 삭제 메서드 추가

```java
// HolidayRepository.java에 추가

void deleteByCompanyIdAndHolidayTypeAndDateBetween(
        UUID companyId, HolidayType holidayType, LocalDate start, LocalDate end);
```

### 10-6. `HolidayController.java` (신규)

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/controller/HolidayController.java`

```java
package com.peoplecore.calendar.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.calendar.dtos.HolidayResDto;
import com.peoplecore.calendar.service.HolidayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/calendar/holidays")
public class HolidayController {

    private final HolidayService holidayService;

    @Autowired
    public HolidayController(HolidayService holidayService) {
        this.holidayService = holidayService;
    }

    /** 기간별 휴일 조회 (캘린더 뷰용 — 법정공휴일 + 사내휴일 통합) */
    @GetMapping
    public ResponseEntity<List<HolidayResDto>> getHolidays(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ResponseEntity.ok(holidayService.getHolidays(companyId, start, end));
    }

    /** 법정공휴일 외부 API 동기화 (Admin 전용) */
    @PostMapping("/sync-national")
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    public ResponseEntity<Map<String, Object>> syncNationalHolidays(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestParam int year) {
        int count = holidayService.syncNationalHolidays(companyId, empId, year);
        return ResponseEntity.ok(Map.of(
                "year", year,
                "syncedCount", count,
                "message", year + "년 법정공휴일 " + count + "건 동기화 완료"));
    }
}
```

### 10-7. hr-service — HolidayRepository 추가 (근태 연동)

**파일 위치:** `hr-service/src/main/java/com/peoplecore/attendence/repository/HolidayRepository.java`

> `Holidays`가 common 엔티티이므로 hr-service에서도 레포를 만들어 직접 조회할 수 있습니다.
> 근태 서비스에서 특정 날짜가 공휴일/사내휴일인지 판별할 때 사용합니다.

```java
package com.peoplecore.attendence.repository;

import com.peoplecore.entity.Holidays;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface HolidayRepository extends JpaRepository<Holidays, Long> {

    /** 특정 날짜가 휴일인지 확인 (isRepeating=true인 경우 월/일만 비교) */
    @Query("""
        SELECT h FROM Holidays h
        WHERE h.companyId = :companyId
        AND (
            (h.isRepeating = true AND MONTH(h.date) = MONTH(:targetDate) AND DAY(h.date) = DAY(:targetDate))
            OR
            (h.isRepeating = false AND h.date = :targetDate)
        )
        """)
    List<Holidays> findHolidaysOnDate(@Param("companyId") UUID companyId,
                                       @Param("targetDate") LocalDate targetDate);

    /** 기간 내 휴일 목록 (근태 통계용) */
    @Query("""
        SELECT h FROM Holidays h
        WHERE h.companyId = :companyId
        AND ((h.isRepeating = true) OR (h.date BETWEEN :start AND :end))
        """)
    List<Holidays> findByCompanyIdAndPeriod(@Param("companyId") UUID companyId,
                                             @Param("start") LocalDate start,
                                             @Param("end") LocalDate end);
}
```

#### 근태 서비스에서 사용 예시

```java
// 근태 계산 시 휴일 체크 로직 (hr-service)

@Autowired
private HolidayRepository holidayRepository;

/**
 * 특정 날짜가 근무일인지 판별
 * 1) WorkGroup.groupWorkDay 비트마스크로 요일 체크
 * 2) Holidays 테이블에서 공휴일/사내휴일 체크
 */
public boolean isWorkingDay(UUID companyId, LocalDate date, WorkGroup workGroup) {
    // 1. 요일 체크 (월=1, 화=2, 수=4, 목=8, 금=16, 토=32, 일=64)
    int dayBit = 1 << (date.getDayOfWeek().getValue() - 1);
    if ((workGroup.getGroupWorkDay() & dayBit) == 0) {
        return false;  // 비근무 요일
    }

    // 2. 공휴일/사내휴일 체크
    List<Holidays> holidays = holidayRepository.findHolidaysOnDate(companyId, date);
    if (!holidays.isEmpty()) {
        return false;  // 휴일
    }

    return true;
}
```

### 10-8. 휴일 관리 플로우 요약

```
┌─────────────────────────────────────────────────────────┐
│              사내휴일 (전사일정 연동)                      │
│                                                         │
│  Admin이 전사일정 등록 시 isHoliday=true 체크             │
│    → Events 테이블 INSERT (isAllEmployees=true)          │
│    → Holidays 테이블 INSERT (COMPANY 타입) ← 자동        │
│                                                         │
│  전사일정 삭제 시                                         │
│    → Events softDelete                                  │
│    → Holidays 레코드도 삭제 ← 자동                       │
│                                                         │
│  캘린더 뷰: 전사일정으로 표시 + 휴일 아이콘               │
│  근태 시스템: Holidays 조회 → 비근무일 처리              │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│              법정공휴일 (외부 API 동기화)                  │
│                                                         │
│  Admin이 관리 화면에서 연도 선택 후 "동기화" 클릭          │
│    POST /api/calendar/holidays/sync-national?year=2026  │
│                                                         │
│  1. 공공데이터포털 API (1~12월 순회) 호출                 │
│  2. 기존 해당 연도 NATIONAL 레코드 삭제                   │
│  3. 조회된 공휴일 bulk INSERT                            │
│     - 설날, 추석 등 → isRepeating=true                   │
│     - 대체공휴일 → isRepeating=false                     │
│                                                         │
│  캘린더 뷰: GET /api/calendar/holidays?start=&end=       │
│  근태 시스템: hr-service HolidayRepository 직접 조회      │
└─────────────────────────────────────────────────────────┘
```

---

## 11. 파일 위치 요약

```
collaboration-service/src/main/java/com/peoplecore/calendar/
├── controller/
│   ├── CalendarEventController.java        ← 일정 CRUD + 참석자 응답 API (수정)
│   ├── CompanyEventController.java         ← 전사 일정 CRUD (Admin 전용, 신규)
│   ├── HolidayController.java             ← 휴일 조회 + 법정공휴일 동기화 (신규)
│   ├── MyCalendarController.java           ← 내 캘린더 관리
│   ├── InterestCalendarController.java     ← 관심 캘린더 + 공유 요청
│   └── CalendarSettingsController.java     ← 연차 연동 설정
│
├── service/
│   ├── CalendarEventService.java           ← 일정 로직 + 참석자 저장/응답 (수정)
│   ├── CompanyEventService.java            ← 전사 일정 + isHoliday 연동 (수정)
│   ├── HolidayService.java                ← 휴일 조회 + 법정공휴일 API 동기화 (신규)
│   ├── MyCalendarService.java              ← 내 캘린더 비즈니스 로직
│   ├── InterestCalendarService.java        ← 관심 캘린더 + 공유 요청 + 즉시 알림
│   └── CalendarSettingsService.java        ← 연차 연동 설정
│
├── client/
│   └── HolidayApiClient.java              ← 공공데이터포털 API 클라이언트 (신규)
│
├── scheduler/
│   └── EventNotificationScheduler.java     ← 예약 알림 (N분 전 팝업/이메일/푸시)
│
├── repository/
│   ├── EventsRepository.java              ← 기본 JPA (extends EventsCustomRepository)
│   ├── EventsCustomRepository.java        ← QueryDSL 인터페이스
│   ├── EventsCustomRepositoryImpl.java    ← QueryDSL 구현체 (BooleanExpression 패턴)
│   ├── EventAttendeesRepository.java      ← 참석자 레포 (신규)
│   ├── MyCalendarsRepository.java         ← 수정 (findByIsDefaultTrue 추가)
│   ├── InterestCalendarsRepository.java
│   ├── CalendarShareRequestsRepository.java
│   ├── EventsNotificationsRepository.java
│   ├── RepeatedRulesRepository.java
│   ├── HolidayRepository.java             ← 수정 (삭제 메서드 추가)
│   └── AnnualLeaveSettingRepository.java
│
├── dtos/
│   ├── EventCreateReqDto.java
│   ├── EventUpdateReqDto.java
│   ├── EventResDto.java                   ← 수정 (attendees 필드 + fromEntityWithAttendees)
│   ├── AttendeeResDto.java                ← 참석자 응답 DTO (신규)
│   ├── AttendeeRespondReqDto.java         ← 참석자 승인/거절 요청 (신규)
│   ├── HolidayResDto.java                 ← 휴일 응답 DTO (신규)
│   ├── RepeatedRulesReqDto.java
│   ├── RepeatedRulesResDto.java
│   ├── NotificationReqDto.java
│   ├── NotificationResDto.java
│   ├── MyCalendarCreateRequest.java
│   ├── MyCalendarUpdateRequest.java
│   ├── MyCalendarResponse.java
│   ├── ShareRequestCreateDto.java
│   ├── ShareRequestResponse.java
│   ├── InterestCalendarResponse.java
│   ├── InterestCalendarUpdateRequest.java
│   ├── CompanyEventRequest.java           ← 수정 (isHoliday 추가)
│   ├── CompanyEventResponse.java          ← 수정 (isHoliday 추가)
│   ├── AnnualLeaveSettingRequest.java
│   └── AnnualLeaveSettingResponse.java
│
├── entity/
│   ├── Events.java                  ← 수정 (비즈니스 메서드 + notifications 관계 추가)
│   ├── MyCalendars.java             ← 수정 (비즈니스 메서드 + isDefault 추가)
│   ├── InterestCalendars.java       ← 수정 (비즈니스 메서드 추가)
│   ├── CalendarShareRequests.java   ← 수정 (approve/reject/cancel 추가)
│   ├── EventAttendees.java          ← 수정 (Events 직접 참조 + approve/reject 메서드)
│   ├── AnnualLeaveSetting.java      ← 신규 (연차 연동 설정)
│   ├── EventInstances.java          ← 기존 유지
│   ├── EventsNotifications.java     ← 기존 유지
│   ├── RepeatedRules.java           ← 기존 유지
│   └── Holidays.java                ← 기존 유지 (common 엔티티)
│
└── enums/                           ← 기존 유지 (변경 없음)
    ├── EventInstancesType.java
    ├── EventsNotiMethod.java
    ├── Frequency.java
    ├── HolidayType.java
    ├── InviteStatus.java
    ├── Permission.java
    └── ShareStatus.java

─── 서비스 간 통신 (기존 파일에 추가) ───

collaboration-service/src/main/java/com/peoplecore/client/
├── component/
│   ├── HrServiceClient.java          ← 기존 + getEmployeesBulk() 추가
│   └── HrCacheService.java           ← 기존 + getEmployeesBulk(), evictEmployee() 추가
└── dto/
    ├── DeptInfoResponse.java          ← 기존 유지
    ├── CompanyInfoResponse.java       ← 기존 유지
    └── EmployeeSimpleResponse.java    ← 신규

hr-service/src/main/java/com/peoplecore/
├── employee/
│   ├── controller/
│   │   └── InternalEmployeeController.java  ← 신규 (/internal/employee)
│   ├── dto/
│   │   └── InternalEmployeeResponseDto.java ← 신규
│   └── repository/
│       └── EmployeeRepository.java          ← 기존 + findByEmpIdsWithDeptAndGrade() 추가
│
└── attendence/repository/
    └── HolidayRepository.java               ← 신규 (근태 연동용, common 엔티티 조회)
```

---

## API 엔드포인트 요약

| Method | Endpoint | 설명 |
|--------|----------|------|
| `POST` | `/api/calendar/events` | 일정 등록 (참석자 포함) |
| `PUT` | `/api/calendar/events/{eventsId}` | 일정 수정 |
| `DELETE` | `/api/calendar/events/{eventsId}` | 일정 삭제 |
| `GET` | `/api/calendar/events/{eventsId}` | 일정 상세 (참석자 목록 포함) |
| `GET` | `/api/calendar/events?start=&end=` | 기간별 일정 목록 |
| `PATCH` | `/api/calendar/events/{eventsId}/attendees/respond` | 참석자 승인/거절 응답 |
| `GET` | `/api/calendar/events/invitations` | 내가 받은 초대 목록 (PENDING) |
| `GET` | `/api/calendar/my-calendars` | 내 캘린더 목록 |
| `POST` | `/api/calendar/my-calendars` | 내 캘린더 추가 |
| `PATCH` | `/api/calendar/my-calendars/{id}` | 내 캘린더 수정 (이름/색상/보이기) |
| `DELETE` | `/api/calendar/my-calendars/{id}` | 내 캘린더 삭제 |
| `POST` | `/api/calendar/interest/share-request` | 관심 캘린더 공유 요청 |
| `PATCH` | `/api/calendar/interest/share-request/{id}?accepted=true/false` | 공유 요청 응답 (승인/거절) |
| `GET` | `/api/calendar/interest/share-request/sent` | 내가 보낸 요청 목록 |
| `GET` | `/api/calendar/interest/share-request/received` | 나에게 온 요청 목록 |
| `GET` | `/api/calendar/interest` | 관심 캘린더 목록 |
| `PATCH` | `/api/calendar/interest/{id}` | 관심 캘린더 설정 변경 |
| `DELETE` | `/api/calendar/interest/{id}` | 관심 캘린더 삭제 |
| `POST` | `/api/calendar/company-events` | 전사 일정 등록 (Admin, isHoliday 포함) |
| `PUT` | `/api/calendar/company-events/{eventsId}` | 전사 일정 수정 (Admin) |
| `DELETE` | `/api/calendar/company-events/{eventsId}` | 전사 일정 삭제 (Admin, 휴일 연동 삭제) |
| `GET` | `/api/calendar/company-events` | 전사 일정 목록 (Admin, 페이징) |
| `GET` | `/api/calendar/company-events/{eventsId}` | 전사 일정 상세 (Admin) |
| `GET` | `/api/calendar/holidays?start=&end=` | 기간별 휴일 조회 (법정+사내) |
| `POST` | `/api/calendar/holidays/sync-national?year=` | 법정공휴일 동기화 (Admin) |
| `GET` | `/api/calendar/settings/annual-leave` | 연차 연동 설정 조회 |
| `POST` | `/api/calendar/settings/annual-leave` | 연차 연동 설정 저장 |

---

## 알림 흐름

> 전자결재와 동일하게 `AlarmEventPublisher.publisher()` → Kafka 비동기 방식 통일

```
┌─────────────────────────────────────────────────────────────────────┐
│                  즉시 알림 (Kafka 비동기)                              │
│                                                                     │
│  전자결재(ApprovalDocumentService 등)와 동일한 패턴                     │
│  알림 실패가 비즈니스 트랜잭션에 영향 안 줌                               │
│                                                                     │
│  [Service 레이어]                                                    │
│    │                                                                │
│    ├─ 일정 등록 시 참석자 알림                                        │
│    │   CalendarEventService.sendAttendeeAlarm()                     │
│    │     → AlarmEventPublisher.publisher(AlarmEvent)                 │
│    │       → Kafka "alarm-event" 토픽                                │
│    │         → AlarmEventConsumer → AlarmService.createAndPush()     │
│    │           → DB 저장 + SSE 실시간 푸시                            │
│    │                                                                │
│    ├─ 관심 캘린더 공유 요청 알림                                      │
│    │   InterestCalendarService.requestShare()                       │
│    │     → AlarmEventPublisher.publisher() → Kafka → DB + SSE       │
│    │                                                                │
│    ├─ 공유 요청 응답 알림 (승인/거절)                                  │
│    │   InterestCalendarService.respondShareRequest()                │
│    │     → AlarmEventPublisher.publisher() → Kafka → DB + SSE       │
│    │                                                                │
│    ├─ 참석자 초대 응답 알림 (승인/거절 → 생성자에게)                    │
│    │   CalendarEventService.sendRespondAlarm()                      │
│    │     → AlarmEventPublisher.publisher() → Kafka → DB + SSE       │
│    │                                                                │
│    └─ 전사 일정 등록 알림 (Admin)                                     │
│        CompanyEventService.createCompanyEvent()                     │
│          → AlarmEventPublisher.publisher() → Kafka → DB + SSE       │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                     예약 알림 (Scheduled + Kafka)                     │
│                                                                     │
│  "10분 전 팝업", "1시간 전 이메일" 등                                  │
│  @Scheduled(fixedRate=60000)으로 1분 주기 폴링                        │
│                                                                     │
│  EventNotificationScheduler                                         │
│    │                                                                │
│    ├─ 매분 정각 실행                                                 │
│    ├─ 향후 24시간 내 시작하는 일정 + 알림 설정 조회                     │
│    ├─ 일정시작 - minutesBefore == 현재 시각이면 발송                    │
│    └─ AlarmEventPublisher.publisher() → Kafka → DB + SSE             │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 백엔드 구현 가이드 — `AnnualLeaveSetting` 제거 & `MyCalendars.isPublic` 추가 (2026-04-23)

> 다음 두 가지 백엔드 변경에 대한 파일별 구현 디테일.
>
> 1. `AnnualLeaveSetting` 엔티티 **삭제** (연차 연동 설정 도메인 제거)
> 2. `MyCalendars` 엔티티에 **`isPublic: Boolean` 컬럼 추가** (기본값 `true`, NOT NULL) — 캘린더 단위 공개 여부

### 0. 공유 모델 정리 — `isPublic`의 의미

공유 여부는 기존부터 다음 3계층이 **AND 조건**으로 엮여 결정됩니다. `isPublic`은 사람 단위 공유를 대체하는 게 아니라, 캘린더 단위의 마스터 스위치로 **한 축이 더 추가**된 것입니다.

| 계층 | 엔티티 | 제어 단위 | 의미 |
|------|--------|----------|------|
| ① 구독자 | `InterestCalendar` + `CalendarShareRequest` | 사람별 | 내 캘린더를 볼 수 있는 사람 목록 — 승인/거절로 관리 |
| ② 캘린더 (신규) | `MyCalendars.isPublic` | 캘린더별 | 내 N개 캘린더 중 어느 것을 구독자에게 노출할지 |
| ③ 일정 | `Events.isPublic` | 일정별 | 공개 캘린더 내부에서 특정 일정만 숨기기 |

**"여러 명 vs 0명 공유"** 는 ①에서 해결. `isPublic`은 "구독 승인은 유지한 채 이 캘린더만 잠시 감추기"용 스위치로 이해하면 됩니다.

### 0-1. `AnnualLeaveSetting` CRUD의 자리는 어디로?

원래 이 엔티티는 **(empId × myCalendarsId) 매핑 + 매핑별 isPublic**을 저장하던 설정 테이블이었습니다. 삭제되면서 다음과 같이 재배치:

| 원래 관리 대상 | 이전 후 | 신규 CRUD |
|---------------|---------|----------|
| 연차가 자동 등록될 캘린더 선택 | 고정된 "휴가 일정" `MyCalendars` 한 개 (사용자별 시스템 예약 캘린더) | ❌ 없음 — 사용자 선택권 제거(단순화) |
| 해당 캘린더의 공개 여부 | `MyCalendars.isPublic` | ❌ 없음 — 기존 `PATCH /api/calendar/my/{id}` 재사용 |
| 사람 단위 공유 여부 | (원래부터) `InterestCalendar` + `ShareRequest` | ❌ 없음 — 이전부터 존재 |

**결론: 대체 컨트롤러/서비스를 새로 만들 필요 없음.** 사라지는 UX는 "연차를 등록할 캘린더를 사용자가 고르는 기능"뿐이며, 시스템은 "휴가 일정" 기본 캘린더로 항상 등록하는 규칙으로 단순화합니다.

연차 승인 훅 자체는 유지되며, 내부 조회 대상만 바뀝니다 — 아래 B-6 참조.

### A. `AnnualLeaveSetting` 도메인 전면 제거

#### A-1. 제거 대상 파일

```
collaboration-service/src/main/java/com/peoplecore/calendar/
├── entity/AnnualLeaveSetting.java                  ← 삭제
├── repository/AnnualLeaveSettingRepository.java    ← 삭제
├── dto/AnnualLeaveSettingRequest.java              ← 삭제
├── dto/AnnualLeaveSettingResponse.java             ← 삭제
├── service/CalendarSettingsService.java            ← 삭제 또는 내용 전체 교체
└── controller/CalendarSettingsController.java      ← 삭제 또는 엔드포인트 전체 제거
```

> 위 가이드 본문 §3-4, §4-4, §5-4 절은 **이번 변경 이후 obsolete**입니다. 갱신이 필요하지만 본 하단 섹션으로 덮어쓴다 보시면 됩니다.

#### A-2. 제거 후 영향 점검

다음 패턴을 전체 검색해 모두 0건이 되어야 합니다.

```bash
grep -r "AnnualLeaveSetting" collaboration-service/src
grep -r "annual-leave"        collaboration-service/src
grep -r "CalendarSettings"    collaboration-service/src
```

- `import com.peoplecore.calendar.entity.AnnualLeaveSetting` → 0건
- `@RequestMapping("/calendar/settings")` → 0건 (컨트롤러 자체 제거)
- 다른 서비스에서 `CalendarSettingsService`를 주입받는 곳 → 0건 확인

#### A-3. DB 마이그레이션

```sql
-- V{next}__drop_annual_leave_settings.sql
DROP TABLE IF EXISTS annual_leave_settings;
```

> 승인된 연차의 "휴가 일정" 자동 등록 로직(전자결재 연동)은 **그대로 유지**됩니다. 단, 해당 자동 등록 시 "휴가 일정" 기본 캘린더(default MyCalendar)를 지목하면 되며, `isPublic` 설정은 자동으로 해당 캘린더의 값을 따르게 됩니다 (아래 B-4 참조).

---

### B. `MyCalendars.isPublic` 추가 구현

#### B-1. 엔티티 — 본 가이드 §1-2 대체

**`collaboration-service/src/main/java/com/peoplecore/calendar/entity/MyCalendars.java`**

```java
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "my_calendars")
public class MyCalendars extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long myCalendarsId;

    @Column(nullable = false)
    private Long empId;

    @Column(nullable = false, length = 100)
    private String calendarName;

    @Column(length = 7)
    private String myDisplayColor;

    private Boolean isVisible;
    private Integer sortOrder;

    @Column(nullable = false)
    private UUID companyId;

    // ── 신규: 캘린더 단위 공개 여부 ──
    // true  : 관심 캘린더 구독자가 이 캘린더의 일정을 볼 수 있음(단, 개별 Event.isPublic도 true여야 함)
    // false : 캘린더가 비공개 — 구독자에게는 이 캘린더의 어떤 일정도 노출되지 않음
    @Column(nullable = false)
    @Builder.Default
    private Boolean isPublic = true;

    // ── 비즈니스 메서드 ──

    public void updateName(String calendarName) {
        this.calendarName = calendarName;
    }

    public void updateColor(String color) {
        this.myDisplayColor = color;
    }

    public void toggleVisible() {
        this.isVisible = !Boolean.TRUE.equals(this.isVisible);
    }

    public void updateSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void updatePublic(Boolean isPublic) {
        if (isPublic != null) this.isPublic = isPublic;
    }
}
```

#### B-2. DTO 수정 — 본 가이드 §3-2의 MyCalendar DTO 3종 갱신

```java
// MyCalendarCreateRequest.java
@Getter
public class MyCalendarCreateRequest {
    private String calendarName;
    private String displayColor;
    private Boolean isPublic;   // null이면 entity default(true) 사용
}
```

```java
// MyCalendarUpdateRequest.java
@Getter
public class MyCalendarUpdateRequest {
    private String calendarName;
    private String displayColor;
    private Boolean isVisible;
    private Boolean isPublic;   // 신규
    private Integer sortOrder;
}
```

```java
// MyCalendarResponse.java
@Getter
@Builder
public class MyCalendarResponse {
    private Long myCalendarsId;
    private String calendarName;
    private String displayColor;
    private Boolean isVisible;
    private Boolean isPublic;   // 신규
    private Integer sortOrder;
    private Boolean isDefault;  // 이미 구현됐다면 유지

    public static MyCalendarResponse from(MyCalendars cal) {
        return MyCalendarResponse.builder()
                .myCalendarsId(cal.getMyCalendarsId())
                .calendarName(cal.getCalendarName())
                .displayColor(cal.getMyDisplayColor())
                .isVisible(cal.getIsVisible())
                .isPublic(cal.getIsPublic())
                .sortOrder(cal.getSortOrder())
                // .isDefault(cal.getIsDefault())
                .build();
    }
}
```

#### B-3. 서비스 — `MyCalendarService` 수정 포인트

**생성 시 기본값 주입**

```java
public MyCalendarResponse createMyCalendar(UUID companyId, Long empId, MyCalendarCreateRequest req) {
    MyCalendars cal = MyCalendars.builder()
            .empId(empId)
            .companyId(companyId)
            .calendarName(req.getCalendarName())
            .myDisplayColor(req.getDisplayColor())
            .isVisible(true)
            .sortOrder(nextSortOrder(companyId, empId))
            .isPublic(req.getIsPublic() != null ? req.getIsPublic() : Boolean.TRUE)  // ← 추가
            .build();
    return MyCalendarResponse.from(myCalendarsRepository.save(cal));
}
```

**업데이트 — 부분 필드 패치 패턴 유지**

```java
public MyCalendarResponse updateMyCalendar(UUID companyId, Long empId, Long id, MyCalendarUpdateRequest req) {
    MyCalendars cal = findOwnedOrThrow(companyId, empId, id);
    if (req.getCalendarName() != null) cal.updateName(req.getCalendarName());
    if (req.getDisplayColor() != null) cal.updateColor(req.getDisplayColor());
    if (req.getIsVisible() != null && !req.getIsVisible().equals(cal.getIsVisible())) cal.toggleVisible();
    if (req.getSortOrder()  != null) cal.updateSortOrder(req.getSortOrder());
    if (req.getIsPublic()   != null) cal.updatePublic(req.getIsPublic());  // ← 추가
    return MyCalendarResponse.from(cal);
}
```

#### B-4. 관심 캘린더 구독자 대상 필터링 — **핵심 비즈니스 규칙**

본 가이드 §4-1/§4-2 `CalendarEventService.getEventsByPeriod()` (L1322–1330 부근)에서 관심 캘린더 이벤트를 모으는 스트림을 다음과 같이 강화해야 합니다.

**현재 (Event.isPublic만 체크):**

```java
List<Events> interestEvents = interests.stream()
        .filter(ic -> Boolean.TRUE.equals(ic.getIsVisible()))
        .flatMap(ic -> eventsRepository
                .findPublicEventsByEmpId(ic.getTargetEmpId(), companyId, start, end)
                .stream())
        .toList();
```

**변경 (Calendar.isPublic AND Event.isPublic):**

쿼리 레벨에서 함께 거르는 게 가장 안전. `EventsRepository`(또는 QueryDSL 구현부)에 `findPublicEventsByEmpIdRespectingCalendar`를 추가하거나 기존 쿼리를 아래 조건으로 갱신:

```java
// 의사 JPQL
@Query("""
    SELECT e FROM Events e
    JOIN e.myCalendars mc
    WHERE mc.empId = :empId
      AND mc.companyId = :companyId
      AND mc.isPublic = true       // ← 신규 조건
      AND e.isPublic = true
      AND e.startAt < :end AND e.endAt > :start
""")
List<Events> findPublicEventsByEmpId(
        @Param("empId") Long empId,
        @Param("companyId") UUID companyId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);
```

QueryDSL 구현체(§2-1의 `EventsRepositoryImpl`)라면 `calendar.isPublic.isTrue()`를 `where` 절에 추가.

> **소유자 본인에게는 여전히 보여야 함** — `myEvents` 조회(§4-1의 step 2)는 소유자의 `visibleCalIds` 기준이므로 `isPublic`과 무관하게 그대로 유지. 즉 **`isPublic`은 "관심 캘린더 구독자가 볼 수 있는지"에만 영향**을 줍니다.

#### B-5. 컨트롤러 — 변경 없음

`MyCalendarController`의 엔드포인트 시그니처는 유지(요청/응답 DTO만 바뀜). Swagger 샘플은 아래 샘플로 갱신 권장.

**요청 예시**

> 게이트웨이 통과 후 실제 컨트롤러가 요구하는 헤더: `X-User-Company` (UUID), `X-User-Id` (Long). 프론트는 `client.ts` 인터셉터가 자동 주입하지만, Postman 등 수동 테스트에선 명시 필요.

```http
POST /api/calendar/my
Content-Type: application/json
X-User-Company: 11111111-2222-3333-4444-555555555555
X-User-Id: 42

{
  "calendarName": "개인 프로젝트",
  "displayColor": "#3b82f6",
  "isPublic": false
}
```

```http
PATCH /api/calendar/my/7
Content-Type: application/json
X-User-Company: 11111111-2222-3333-4444-555555555555
X-User-Id: 42

{ "isPublic": true }
```

> `PATCH`는 부분 업데이트 — `MyCalendarUpdateReqDto`의 필드 중 보낸 것만 반영되고 나머지는 그대로 유지됩니다. `calendarName`/`displayColor`/`isVisible`/`isPublic`/`sortOrder` 중 필요한 것만 조합 가능.

**응답 예시**

```json
{
  "myCalendarsId": 7,
  "calendarName": "개인 프로젝트",
  "displayColor": "#3b82f6",
  "isVisible": true,
  "isPublic": false,
  "sortOrder": 2,
  "isDefault": false
}
```

#### B-6. "휴가 일정" 기본 캘린더 연동

전자결재의 연차 승인 → 자동 일정 등록 플로우([§알림 흐름](#알림-흐름))는 **유지**되며, 내부에서 조회하던 `AnnualLeaveSettingRepository`만 "휴가 일정" MyCalendar 단일 조회로 교체합니다.

**(a) "휴가 일정" 기본 캘린더 개념 정의**

사용자별로 시스템이 예약하는 캘린더 하나를 둡니다. 식별은 이름 상수보다 **전용 플래그**가 안전합니다. `MyCalendars`에 다음 필드를 추가(선택사항, 권장):

```java
// MyCalendars.java — 추가 권장 필드
@Column(nullable = false)
@Builder.Default
private Boolean isLeaveDefault = false;   // true인 행이 사원당 정확히 1개
```

> **대안**: 플래그를 두지 않고 `calendarName == "휴가 일정"`로 lookup 해도 동작은 하지만, 이름 변경/다국어화 시 깨지므로 플래그 방식을 권장.

사원 가입 또는 최초 캘린더 조회 시 해당 캘린더가 없으면 자동 생성 — 아래 B-6-2 훅에서 처리.

**(b) 연차 승인 훅 수정**

연차 승인 이벤트를 받는 곳(예: `hr-service`의 연차 승인 서비스 또는 `collaboration-service`의 Kafka consumer)에서:

```java
// 변경 전 (의사 코드)
List<AnnualLeaveSetting> settings = settingRepository.findByCompanyIdAndEmpId(companyId, empId);
for (AnnualLeaveSetting s : settings) {
    eventsRepository.save(buildLeaveEvent(empId, s.getMyCalendar(), leave, s.getIsPublic()));
}

// 변경 후
MyCalendars leaveCal = myCalendarsRepository
        .findByCompanyIdAndEmpIdAndIsLeaveDefaultTrue(companyId, empId)
        .orElseGet(() -> myCalendarsRepository.save(
                MyCalendars.builder()
                        .companyId(companyId).empId(empId)
                        .calendarName("휴가 일정")
                        .myDisplayColor("#10b981")
                        .isVisible(true)
                        .isPublic(true)            // 초기값
                        .isLeaveDefault(true)
                        .sortOrder(99)
                        .build()));

eventsRepository.save(buildLeaveEvent(empId, leaveCal, leave, /* Events.isPublic */ true));
```

**(c) `Events.isPublic` 정책**

자동 생성되는 연차 이벤트의 `Events.isPublic`은 **항상 `true`로 고정**하는 것을 권장합니다. 이유:
- 구독자 가시성은 §B-4의 쿼리에서 `calendar.isPublic AND event.isPublic` AND 조건이 처리
- 사용자가 "휴가 일정" 캘린더를 비공개로 토글하면 → 모든 휴가 일정이 구독자 화면에서 일괄 사라짐
- 다시 공개로 돌리면 → 일괄 재등장
- 매 이벤트마다 isPublic을 복사·관리할 필요 없이 **캘린더 레벨 스위치 하나로 일관**

**(d) 기존 휴가 일정 데이터 마이그레이션**

`AnnualLeaveSetting`을 DROP하기 전, 기존 연동으로 생성됐던 이벤트들이 이미 여러 `MyCalendars`에 흩어져 있다면:

```sql
-- 선택 1: 그대로 유지 — 사용자가 원래 지정했던 캘린더에 남아있음(과거 데이터 보존)
-- 선택 2: "휴가 일정" 기본 캘린더로 통합 (일관성)
--   UPDATE events e
--     SET my_calendars_id = (
--       SELECT mc.my_calendars_id FROM my_calendars mc
--       WHERE mc.emp_id = e.emp_id AND mc.is_leave_default = true
--     )
--   WHERE e.source_type = 'ANNUAL_LEAVE';   -- 이벤트에 출처 구분 컬럼이 있을 때
```

운영 정책에 따라 선택. 선택 1이 안전.

#### B-7. DB 마이그레이션

```sql
-- V{next}__add_is_public_to_my_calendars.sql
ALTER TABLE my_calendars
  ADD COLUMN is_public BOOLEAN NOT NULL DEFAULT TRUE;

-- 기존 행도 DEFAULT로 자동 채워짐. 명시 백필이 필요하면:
UPDATE my_calendars SET is_public = TRUE WHERE is_public IS NULL;
```

#### B-8. 테스트 포인트

| 시나리오 | 기대 결과 |
|---------|----------|
| `POST /my`에 `isPublic: false` 전달 | 응답의 `isPublic === false`, DB 컬럼도 false |
| `POST /my`에 `isPublic` 생략 | DB default(true) 적용, 응답 `isPublic === true` |
| `PATCH /my/{id}`로 `isPublic: false` | 해당 캘린더의 모든 기존 이벤트가 관심 캘린더 구독자 조회에서 제외 |
| 비공개 캘린더의 `Event.isPublic=true`인 일정을 구독자가 `/events?start=…&end=…` 조회 | 결과에 포함되지 않음 |
| 본인이 자기 비공개 캘린더 일정 조회 | 정상적으로 포함됨 (소유자 뷰에는 영향 없음) |
| 연차 자동 등록 → "휴가 일정" 캘린더를 비공개로 전환 → 공개로 복귀 | 동료 화면에서 사라졌다가 다시 나타남 |

#### B-9. 교차 참조 가이드

본 가이드 내 아래 섹션은 이번 변경에 맞춰 **함께 갱신**해야 합니다.

| 섹션 | 수정 내용 |
|------|----------|
| §1-2 `MyCalendars.java` (L156) | `isPublic` 필드 + `updatePublic()` 추가 (위 B-1로 통째 대체) |
| §3-2 `MyCalendarCreateRequest` / `UpdateRequest` / `Response` (L899–960) | `isPublic` 필드 추가 (B-2로 대체) |
| §3-4 `AnnualLeaveSettingRequest/Response` (L1068–1150) | **섹션 전체 삭제** |
| §4-4 `CalendarSettingsService` (L1867–) | **섹션 전체 삭제** |
| §5-4 `CalendarSettingsController` (L2274–) | **섹션 전체 삭제** |
| §4-1 `CalendarEventService.getEventsByPeriod()` (L1322 부근) | 관심 캘린더 필터링에 `calendar.isPublic` 조건 추가 (B-4) |
| §2-1 `EventsRepositoryImpl` | `findPublicEventsByEmpId` QueryDSL에 `calendar.isPublic.isTrue()` 조건 추가 |
| 파일 트리 (L4286, L4294) | `CalendarSettingsController.java` / `CalendarSettingsService.java` 항목 제거 |
| API 표 (L4414–4415) | `/settings/annual-leave` 두 행 삭제 |