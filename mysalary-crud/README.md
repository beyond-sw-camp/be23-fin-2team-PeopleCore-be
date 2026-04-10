# 내 급여 조회 / 예상 퇴직금 조회 CRUD

## 파일 배치 가이드

각 파일을 아래 경로에 복사하세요.

### Repository (5개)
```
hr-service/src/main/java/com/peoplecore/pay/repository/
├── PayStubsRepository.java
├── EmpAccountsRepository.java
├── EmpRetirementAccountRepository.java
├── RetirementPensionDepositsRepository.java
└── MySalaryQueryRepository.java          ← QueryDSL 동적쿼리
```

### DTO (8개)
```
hr-service/src/main/java/com/peoplecore/pay/dto/
├── MySalaryInfoResDto.java
├── PayStubListResDto.java
├── PayStubItemResDto.java
├── PayStubDetailResDto.java
├── SeveranceEstimateReqDto.java
├── SeveranceEstimateResDto.java
├── PensionInfoResDto.java
└── AccountUpdateReqDto.java
```

### Cache (1개)
```
hr-service/src/main/java/com/peoplecore/pay/cache/
└── MySalaryCacheService.java             ← Redis 캐싱
```

### Event (3개)
```
hr-service/src/main/java/com/peoplecore/pay/event/
├── SalaryEvent.java
├── SalaryEventPublisher.java             ← Kafka 발행
└── SalaryEventConsumer.java              ← Kafka 소비 (캐시 무효화)
```

### Service (1개)
```
hr-service/src/main/java/com/peoplecore/pay/service/
└── MySalaryService.java
```

### Controller (1개)
```
hr-service/src/main/java/com/peoplecore/pay/controller/
└── MySalaryController.java
```

## API 엔드포인트

| Method | URL                    | 설명                    |
|--------|------------------------|------------------------|
| GET    | /pay/my/info           | 내 급여 정보              |
| GET    | /pay/my/stubs?year=    | 급여명세서 목록 (연도별)     |
| GET    | /pay/my/stubs/{stubId} | 급여명세서 상세             |
| POST   | /pay/my/severance      | 예상 퇴직금 산정           |
| GET    | /pay/my/pension        | DB/DC 퇴직연금 적립금      |
| PUT    | /pay/my/account        | 급여 계좌 변경             |

## 기술 스택 적용

- **@Autowired** 의존성 주입
- **QueryDSL** BooleanExpression 동적 필터링
- **Redis** hrCacheRedisTemplate 캐싱 (TTL: 30~120분)
- **Kafka** salary-event 토픽 (실시간 캐시 무효화)
- **재귀 패턴** 리스트 매핑, 필터링, 캐시 무효화
