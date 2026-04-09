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
9. [파일 위치 요약](#9-파일-위치-요약)

---

## 알림 설계 원칙

캘린더 알림은 두 가지로 나뉘며, 각각 다른 방식으로 처리합니다.

| 구분 | 예시 | 처리 방식 | 이유 |
|------|------|-----------|------|
| **즉시 알림** | 공유 요청/승인/거절, 일정 초대 | `AlarmService.createAndPush()` 직접 호출 | 같은 JVM 내부 호출이므로 Kafka 불필요. 트랜잭션 내에서 DB 저장 + SSE 푸시 동시 처리 |
| **예약 알림** | "10분 전 팝업", "1시간 전 이메일" | `@Scheduled` 스케줄러 | 특정 시각에 맞춰 발송해야 하므로 주기적 폴링 필요 |

> **Kafka는 서비스 간 통신**에 사용합니다 (hr-service → collaboration-service 등).
> 같은 collaboration-service 내부에서는 직접 호출이 더 안전하고 빠릅니다.
> 서비스가 살아있으면 AlarmService도 살아있으므로 유실 경로 자체가 없고,
> `@Transactional` 내에서 DB 저장이 보장되므로 데이터 안정성도 확보됩니다.

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

import lombok.Getter;

import java.util.List;

@Getter
public class AnnualLeaveSettingRequest {

    private List<Long> calendarIds;  // 연동할 캘린더 ID 목록
    private Boolean isPublic;        // 연차 일정 공개 여부
}
```

#### `AnnualLeaveSettingResponse.java`

```java
package com.peoplecore.calendar.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AnnualLeaveSettingResponse {

    private List<Long> linkedCalendarIds;
    private Boolean isPublic;
}
```

---

## 4. Service

### 4-1. `CalendarEventService.java` — 일정 CRUD + 즉시 알림

**파일 위치:** `collaboration-service/src/main/java/com/peoplecore/calendar/service/CalendarEventService.java`

> **변경 사항:** `AlarmEventPublisher`(Kafka) 대신 `AlarmService.createAndPush()` 직접 호출

```java
package com.peoplecore.calendar.service;

import com.peoplecore.alarm.service.AlarmService;
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
    private final AlarmService alarmService;

    @Autowired
    public CalendarEventService(EventsRepository eventsRepository,
                                MyCalendarsRepository myCalendarsRepository,
                                RepeatedRulesRepository repeatedRulesRepository,
                                EventsNotificationsRepository notificationsRepository,
                                InterestCalendarsRepository interestCalendarsRepository,
                                AlarmService alarmService) {
        this.eventsRepository = eventsRepository;
        this.myCalendarsRepository = myCalendarsRepository;
        this.repeatedRulesRepository = repeatedRulesRepository;
        this.notificationsRepository = notificationsRepository;
        this.interestCalendarsRepository = interestCalendarsRepository;
        this.alarmService = alarmService;
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
     * 참석자에게 즉시 알림 발송
     * 같은 collaboration-service 내부이므로 AlarmService 직접 호출
     * → DB 저장 + SSE 실시간 푸시가 같은 트랜잭션 내에서 처리됨
     */
    private void sendAttendeeAlarm(UUID companyId, Events event, List<Long> attendeeEmpIds) {
        if (attendeeEmpIds == null || attendeeEmpIds.isEmpty()) return;

        AlarmEvent alarm = AlarmEvent.builder()
                .companyId(companyId)
                .alarmType("Calendar")
                .alarmTitle("일정 초대")
                .alarmContent(event.getTitle() + " 일정에 초대되었습니다.")
                .alarmLink("/calendar?eventId=" + event.getEventsId())
                .alarmRefType("EVENT")
                .alarmRefId(event.getEventsId())
                .empIds(attendeeEmpIds)
                .build();
        alarmService.createAndPush(alarm);
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
> - `AlarmEventPublisher`(Kafka) 대신 `AlarmService.createAndPush()` 직접 호출
> - 승인/거절 메서드를 `respondShareRequest()`로 통합
> - `HrCacheService`로 타인 사원 이름 조회 (null 제거)

```java
package com.peoplecore.calendar.service;

import com.peoplecore.alram.service.AlarmService;
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
    private final AlarmService alarmService;
    private final HrCacheService hrCacheService;

    @Autowired
    public InterestCalendarService(CalendarShareRequestsRepository shareRequestsRepository,
                                   InterestCalendarsRepository interestCalendarsRepository,
                                   AlarmService alarmService,
                                   HrCacheService hrCacheService) {
        this.shareRequestsRepository = shareRequestsRepository;
        this.interestCalendarsRepository = interestCalendarsRepository;
        this.alarmService = alarmService;
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

        // 상대방에게 즉시 알림 (같은 서비스 → 직접 호출)
        alarmService.createAndPush(AlarmEvent.builder()
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

        // 요청자에게 즉시 알림
        String title = accepted ? "캘린더 공유 승인" : "캘린더 공유 거절";
        String content = accepted
                ? "캘린더 공유 요청이 승인되었습니다."
                : "캘린더 공유 요청이 거절되었습니다.";

        alarmService.createAndPush(AlarmEvent.builder()
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
                    .linkedCalendarIds(List.of())
                    .isPublic(true)
                    .build();
        }

        return AnnualLeaveSettingResponse.builder()
                .linkedCalendarIds(settings.stream()
                        .map(s -> s.getMyCalendar().getMyCalendarsId())
                        .toList())
                .isPublic(settings.get(0).getIsPublic())
                .build();
    }

    /** 연차 연동 설정 저장 (기존 삭제 후 재생성) */
    @Transactional
    public AnnualLeaveSettingResponse saveSettings(UUID companyId, Long empId,
                                                    AnnualLeaveSettingRequest request) {
        // 기존 설정 삭제
        settingRepository.deleteByCompanyIdAndEmpId(companyId, empId);

        // 새 설정 저장
        List<AnnualLeaveSetting> newSettings = request.getCalendarIds().stream()
                .map(calId -> {
                    MyCalendars cal = myCalendarsRepository.findById(calId)
                            .orElseThrow(() -> new BusinessException(
                                    "캘린더를 찾을 수 없습니다. id=" + calId, HttpStatus.NOT_FOUND));
                    return AnnualLeaveSetting.builder()
                            .empId(empId)
                            .companyId(companyId)
                            .myCalendar(cal)
                            .isPublic(request.getIsPublic())
                            .build();
                })
                .toList();

        settingRepository.saveAll(newSettings);

        return AnnualLeaveSettingResponse.builder()
                .linkedCalendarIds(request.getCalendarIds())
                .isPublic(request.getIsPublic())
                .build();
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
import com.peoplecore.calendar.service.CalendarSettingsService;
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

import com.peoplecore.alarm.service.AlarmService;
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
 *   - AlarmService.createAndPush()로 DB 저장 + SSE 실시간 푸시
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
    private final AlarmService alarmService;

    @Autowired
    public EventNotificationScheduler(EventsRepository eventsRepository,
                                      AlarmService alarmService) {
        this.eventsRepository = eventsRepository;
        this.alarmService = alarmService;
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

            alarmService.createAndPush(alarm);

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

import com.peoplecore.calendar.dto.CompanyEventRequest;
import com.peoplecore.calendar.dto.CompanyEventResponse;
import com.peoplecore.calendar.entity.Events;
import com.peoplecore.calendar.repository.EventsRepository;
import com.peoplecore.client.component.HrCacheService;
import com.peoplecore.client.dto.EmployeeSimpleResponse;
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

    @Autowired
    public CompanyEventService(EventsRepository eventsRepository,
                               HrCacheService hrCacheService) {
        this.eventsRepository = eventsRepository;
        this.hrCacheService = hrCacheService;
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

## 9. 파일 위치 요약

```
collaboration-service/src/main/java/com/peoplecore/calendar/
├── controller/
│   ├── CalendarEventController.java        ← 일정 CRUD (일반 사원)
│   ├── CompanyEventController.java         ← 전사 일정 CRUD (Admin 전용, 신규)
│   ├── MyCalendarController.java           ← 내 캘린더 관리
│   ├── InterestCalendarController.java     ← 관심 캘린더 + 공유 요청
│   └── CalendarSettingsController.java     ← 연차 연동 설정
│
├── service/
│   ├── CalendarEventService.java           ← 일정 비즈니스 로직 + 즉시 알림 (일반 사원)
│   ├── CompanyEventService.java            ← 전사 일정 비즈니스 로직 (Admin 전용, 신규)
│   ├── MyCalendarService.java              ← 내 캘린더 비즈니스 로직
│   ├── InterestCalendarService.java        ← 관심 캘린더 + 공유 요청 + 즉시 알림
│   └── CalendarSettingsService.java        ← 연차 연동 설정
│
├── scheduler/
│   └── EventNotificationScheduler.java     ← 예약 알림 (N분 전 팝업/이메일/푸시)
│
├── repository/
│   ├── EventsRepository.java              ← 기본 JPA (extends EventsCustomRepository)
│   ├── EventsCustomRepository.java        ← QueryDSL 인터페이스
│   ├── EventsCustomRepositoryImpl.java    ← QueryDSL 구현체 (BooleanExpression 패턴)
│   ├── MyCalendarsRepository.java
│   ├── InterestCalendarsRepository.java
│   ├── CalendarShareRequestsRepository.java
│   ├── EventsNotificationsRepository.java
│   ├── RepeatedRulesRepository.java
│   ├── HolidaysRepository.java
│   └── AnnualLeaveSettingRepository.java
│
├── dto/
│   ├── EventCreateRequest.java
│   ├── EventUpdateRequest.java
│   ├── EventResponse.java
│   ├── RepeatedRuleRequest.java
│   ├── RepeatedRuleResponse.java
│   ├── NotificationRequest.java
│   ├── NotificationResponse.java
│   ├── MyCalendarCreateRequest.java
│   ├── MyCalendarUpdateRequest.java
│   ├── MyCalendarResponse.java
│   ├── ShareRequestCreateDto.java
│   ├── ShareRequestResponse.java
│   ├── InterestCalendarResponse.java
│   ├── InterestCalendarUpdateRequest.java
│   ├── CompanyEventRequest.java              ← 전사 일정 요청 (신규)
│   ├── CompanyEventResponse.java             ← 전사 일정 응답 (신규)
│   ├── AnnualLeaveSettingRequest.java
│   └── AnnualLeaveSettingResponse.java
│
├── entity/
│   ├── Events.java                  ← 수정 (비즈니스 메서드 + notifications 관계 추가)
│   ├── MyCalendars.java             ← 수정 (비즈니스 메서드 추가)
│   ├── InterestCalendars.java       ← 수정 (비즈니스 메서드 추가)
│   ├── CalendarShareRequests.java   ← 수정 (approve/reject/cancel 추가)
│   ├── AnnualLeaveSetting.java      ← 신규 (연차 연동 설정)
│   ├── EventInstances.java          ← 기존 유지
│   ├── EventAttendees.java          ← 기존 유지
│   ├── EventsNotifications.java     ← 기존 유지
│   ├── RepeatedRules.java           ← 기존 유지
│   └── Holidays.java                ← 기존 유지
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

hr-service/src/main/java/com/peoplecore/employee/
├── controller/
│   └── InternalEmployeeController.java  ← 신규 (/internal/employee)
├── dto/
│   └── InternalEmployeeResponseDto.java ← 신규
└── repository/
    └── EmployeeRepository.java          ← 기존 + findByEmpIdsWithDeptAndGrade() 추가
```

---

## API 엔드포인트 요약

| Method | Endpoint | 설명 |
|--------|----------|------|
| `POST` | `/api/calendar/events` | 일정 등록 |
| `PUT` | `/api/calendar/events/{eventsId}` | 일정 수정 |
| `DELETE` | `/api/calendar/events/{eventsId}` | 일정 삭제 |
| `GET` | `/api/calendar/events/{eventsId}` | 일정 상세 |
| `GET` | `/api/calendar/events?start=&end=` | 기간별 일정 목록 |
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
| `POST` | `/api/calendar/company-events` | 전사 일정 등록 (Admin) |
| `PUT` | `/api/calendar/company-events/{eventsId}` | 전사 일정 수정 (Admin) |
| `DELETE` | `/api/calendar/company-events/{eventsId}` | 전사 일정 삭제 (Admin) |
| `GET` | `/api/calendar/company-events` | 전사 일정 목록 (Admin, 페이징) |
| `GET` | `/api/calendar/company-events/{eventsId}` | 전사 일정 상세 (Admin) |
| `GET` | `/api/calendar/settings/annual-leave` | 연차 연동 설정 조회 |
| `POST` | `/api/calendar/settings/annual-leave` | 연차 연동 설정 저장 |

---

## 알림 흐름

```
┌─────────────────────────────────────────────────────────────────────┐
│                        즉시 알림 (Direct Call)                       │
│                                                                     │
│  같은 collaboration-service 내부 → Kafka 불필요                       │
│  @Transactional 내에서 DB 저장 + SSE 푸시 동시 처리                    │
│                                                                     │
│  [Service 레이어]                                                    │
│    │                                                                │
│    ├─ 일정 등록 시 참석자 알림                                        │
│    │   CalendarEventService.sendAttendeeAlarm()                     │
│    │     → AlarmService.createAndPush(AlarmEvent)                   │
│    │       → DB 저장 + SSE 실시간 푸시                               │
│    │                                                                │
│    ├─ 관심 캘린더 공유 요청 알림                                      │
│    │   InterestCalendarService.requestShare()                       │
│    │     → AlarmService.createAndPush() → DB + SSE                  │
│    │                                                                │
│    └─ 공유 요청 응답 알림 (승인/거절)                                  │
│        InterestCalendarService.respondShareRequest()                │
│          → AlarmService.createAndPush() → DB + SSE                  │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                     예약 알림 (Scheduled)                             │
│                                                                     │
│  "10분 전 팝업", "1시간 전 이메일" 등                                  │
│  @Scheduled(fixedRate=60000)으로 1분 주기 폴링                        │
│                                                                     │
│  EventNotificationScheduler                                         │
│    │                                                                │
│    ├─ 매분 정각 실행                                                 │
│    ├─ 향후 24시간 내 시작하는 일정 + 알림 설정 조회                     │
│    ├─ 일정시작 - minutesBefore == 현재 시각이면 발송                    │
│    └─ AlarmService.createAndPush() → DB + SSE                       │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                     Kafka는 서비스 간 통신에만 사용                     │
│                                                                     │
│  hr-service ──(Kafka "alarm-event")──→ collaboration-service        │
│  hr-service ──(Kafka "hr-dept-updated")──→ HrEventConsumer          │
└─────────────────────────────────────────────────�