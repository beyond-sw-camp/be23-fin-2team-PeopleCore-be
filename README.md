# PeopleCore - HR 기반 ERP SaaS

<p align="center">
  <!-- TODO: 메인 이미지 교체 (예: picture/main.png) -->
  <img src="picture/peoplecore-banner.png" alt="PeopleCore 메인 이미지" width="800" />
</p>

**PeopleCore**는 인사·근태·급여·성과·전자결재·협업·AI Copilot을 단일 플랫폼으로 통합한 **워크플로우 기반 엔터프라이즈 SaaS**입니다.

회사마다 다른 결재선·근무 정책·급여 항목·문서번호 규칙·평가 등급 분포를 **코드 수정 없이 운영 데이터만으로 커스터마이징**할 수 있어, 도입 즉시 회사 고유의 업무 흐름에 맞춰 동작합니다.

## 팀원 소개

<table align="center">
  <tr>
    <td align="center">
      <img src="picture/second.png" width="100" height="100" alt="이수림" /><br />
      <b>이수림</b><br />
      <a href="https://github.com/sssurim-png">@sssurim-png</a>
    </td>
    <td align="center">
      <img src="picture/qudra.png" width="100" height="100" alt="정명진" /><br />
      <b>👑 정명진 (팀장)</b><br />
      <a href="https://github.com/jmj010702">@jmj010702</a>
    </td>
    <td align="center">
      <img src="picture/first.png" width="100" height="100" alt="홍진희" /><br />
      <b>홍진희</b><br />
      <a href="https://github.com/lampshub">@lampshub</a>
    </td>
    <td align="center">
      <img src="picture/third.png" width="100" height="100" alt="황주완" /><br />
      <b>황주완</b><br />
      <a href="https://github.com/HwangJwan">@HwangJwan</a>
    </td>
  </tr>
</table>
<br>

## 목차

1. [주요 기능](#1-주요-기능)
2. [기술 스택](#2-기술-스택)
3. [시스템 아키텍처](#3-시스템-아키텍처)
4. [상세 서비스 화면](#4-상세-서비스-화면)
5. [기술 문서](#5-기술-문서)
6. [성능 테스트](#6-성능-테스트)
7. [실행 방법](#7-실행-방법)
8. [트러블 슈팅](#8-트러블-슈팅)
9. [회고](#9-회고)
10. [그 외 산출물](#10-그-외-산출물)

## 1. 주요 기능

<details>
<summary><h3>전자결재</h3></summary>

<p align="center">
양식 수정 및 버전관리
<br/>
  <img src="picture/gifs/approval-settings.gif" alt="전자결재 - 설정" />
</p>

<p align="center">
결재 문서번호 규칙 설정
<br/>
  <img src="picture/gifs/approval-num-setting.gif" alt="전자결재 - 문서번호 설정" />
</p>

<p align="center">
결재 상신
<br/>
  <img src="picture/gifs/approval-pay-up.gif" alt="전자결재 - 결재 상신" />
</p>

<p align="center">
결재 최종 승인
<br/>
  <img src="picture/gifs/approval-pay-ok2.gif" alt="전자결재 - 결재 승인" />
</p>

</details>

<details>
<summary><h3>사원 관리 (Employee)</h3></summary>

</details>

<details>
<summary><h3>근태 (Attendance)</h3></summary>

<p align="center">
회사의 근태 관련 정책 설정
<br/>
  <img src="picture/gifs/attendance-settings.gif" alt="근태 - 설정" />
</p>

<p align="center">
사원의 초과근무 신청
<br/>
  <img src="picture/gifs/attendance-overRequest.gif" alt="근태 - 초과근무 신청" />
</p>

<p align="center">
사원의 근태 정정 신청
<br/>
  <img src="picture/gifs/attendance-edit.gif" alt="근태 - 정정 신청" />
</p>

</details>

<details>
<summary><h3>휴가 (Vacation)</h3></summary>

<p align="center">
회사 내 휴가 정책과 회사가 만들 휴가 화면
<br/>
  <img src="picture/gifs/vacation-settings.gif" alt="휴가 - 설정" />
</p>

<p align="center">
휴가 신청 후 승인시 자신의 캘린더에 자동 반영
<br/>
  <img src="picture/gifs/vacaion-request-calander.gif" alt="휴가 - 신청 캘린더" />
</p>

<p align="center">
자신이 필요한 휴가 부여 신청
<br/>
  <img src="picture/gifs/vacaion-Grant-Request.gif" alt="휴가 - 부여 요청" />
</p>

<p align="center">
관리자가 일반 사원의 휴가를 조정할 수 있는 화면 
<br/>
  <img src="picture/gifs/vacaion-Grant.gif" alt="휴가조정" />
</p>

</details>

<details>
<summary><h3>성과 평가 (Evaluation)</h3></summary>

</details>

<details>
<summary><h3>급여 관리 (Payroll)</h3></summary>

</details>

---

<br>

## 2. 기술 스택

| 분류                      | 기술                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
|-------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **프론트엔드**               | ![React](https://img.shields.io/badge/React-20232A?logo=react) ![Vue.js](https://img.shields.io/badge/Vue.js-4FC08D?logo=vuedotjs&logoColor=white) ![Vuera](https://img.shields.io/badge/Vuera-42B883) ![TypeScript](https://img.shields.io/badge/TypeScript-3178C6?logo=typescript&logoColor=white) ![Vite](https://img.shields.io/badge/Vite-646CFF?logo=vite&logoColor=white) ![TinyMCE](https://img.shields.io/badge/TinyMCE-3776AB) ![Tailwind CSS](https://img.shields.io/badge/Tailwind_CSS-06B6D4?logo=tailwindcss&logoColor=white) ![Ant Design](https://img.shields.io/badge/Ant_Design-0170FE?logo=antdesign&logoColor=white) ![Zustand](https://img.shields.io/badge/Zustand-443E38) ![TanStack Query](https://img.shields.io/badge/TanStack_Query-FF4154) ![TanStack Router](https://img.shields.io/badge/TanStack_Router-FF4154) ![React Hook Form](https://img.shields.io/badge/React_Hook_Form-EC5990?logo=reacthookform&logoColor=white) ![Axios](https://img.shields.io/badge/Axios-5A29E4) ![Chart.js](https://img.shields.io/badge/Chart.js-FF6384?logo=chartdotjs&logoColor=white) ![xlsx](https://img.shields.io/badge/xlsx-217346) ![mammoth](https://img.shields.io/badge/mammoth-7A3E1D) ![hwp.js](https://img.shields.io/badge/hwp.js-009688) |
| **백엔드 (Java / Spring)** | ![Java](https://img.shields.io/badge/Java-007396?logo=openjdk&logoColor=white) ![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?logo=springboot&logoColor=white) ![Spring Cloud](https://img.shields.io/badge/Spring_Cloud-6DB33F?logo=spring&logoColor=white) ![Spring Security](https://img.shields.io/badge/Spring_Security-6DB33F?logo=springsecurity&logoColor=white) ![JPA](https://img.shields.io/badge/JPA-59666C) ![QueryDSL](https://img.shields.io/badge/QueryDSL-0769AD) ![Spring Batch](https://img.shields.io/badge/Spring_Batch-6DB33F) ![Quartz](https://img.shields.io/badge/Quartz-D24939) ![Scheduled](https://img.shields.io/badge/@Scheduled-6DB33F) ![WebSocket](https://img.shields.io/badge/WebSocket-010101?logo=socketdotio&logoColor=white) ![SSE](https://img.shields.io/badge/SSE-1E88E5) ![Cloud Gateway](https://img.shields.io/badge/Cloud_Gateway-6DB33F) ![Eureka](https://img.shields.io/badge/Eureka-6DB33F) ![Resilience4j](https://img.shields.io/badge/Resilience4j-6DB33F) ![JWT](https://img.shields.io/badge/JWT-000000?logo=jsonwebtokens) ![Swagger](https://img.shields.io/badge/Swagger-85EA2D?logo=swagger&logoColor=black)                                                                                |
| **백엔드 (Python / AI)**   | ![Python](https://img.shields.io/badge/Python-3776AB?logo=python&logoColor=white) ![FastAPI](https://img.shields.io/badge/FastAPI-009688?logo=fastapi&logoColor=white) ![OpenAI](https://img.shields.io/badge/OpenAI-412991?logo=openai&logoColor=white) ![ChromaDB](https://img.shields.io/badge/ChromaDB-FCC624)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| **AI**                  | ![EXAONE](https://img.shields.io/badge/EXAONE-7B61FF) ![Claude](https://img.shields.io/badge/Claude-D97706)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| **메시징**                 | ![Kafka](https://img.shields.io/badge/Kafka-231F20?logo=apachekafka&logoColor=white) ![STOMP](https://img.shields.io/badge/STOMP-E91E63)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| **데이터베이스**              | ![MySQL](https://img.shields.io/badge/MySQL-4479A1?logo=mysql&logoColor=white) ![Redis](https://img.shields.io/badge/Redis-DC382D?logo=redis&logoColor=white) ![Elasticsearch](https://img.shields.io/badge/Elasticsearch-005571?logo=elasticsearch&logoColor=white) ![MinIO](https://img.shields.io/badge/MinIO-C72E49?logo=minio&logoColor=white)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| **인프라 / 클라우드**          | ![AWS RDS](https://img.shields.io/badge/AWS_RDS-527FFF?logo=amazonrds&logoColor=white) ![AWS Route 53](https://img.shields.io/badge/Route_53-8C4FFF?logo=amazonroute53&logoColor=white) ![AWS EKS](https://img.shields.io/badge/AWS_EKS-FF9900?logo=amazoneks&logoColor=white) ![AWS ECR](https://img.shields.io/badge/AWS_ECR-FF9900?logo=amazonecr&logoColor=white) ![Docker](https://img.shields.io/badge/Docker-2496ED?logo=docker&logoColor=white) ![CloudFront](https://img.shields.io/badge/CloudFront-8C4FFF?logo=amazoncloudfront&logoColor=white) ![AWS IAM](https://img.shields.io/badge/AWS_IAM-DD344C?logo=amazoniam&logoColor=white)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| **CI / CD**             | ![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-2088FF?logo=githubactions&logoColor=white) ![GitHub Packages](https://img.shields.io/badge/GitHub_Packages-181717?logo=github&logoColor=white)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| **협업 도구**               | ![Git](https://img.shields.io/badge/Git-F05032?logo=git&logoColor=white) ![GitHub](https://img.shields.io/badge/GitHub-181717?logo=github&logoColor=white) ![Notion](https://img.shields.io/badge/Notion-000000?logo=notion&logoColor=white) ![Discord](https://img.shields.io/badge/Discord-5865F2?logo=discord&logoColor=white) ![Figma](https://img.shields.io/badge/Figma-F24E1E?logo=figma&logoColor=white)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |

---

<br>

## 3. 시스템 아키텍처

<p align="center">
  <img src="picture/system-architecture.png" alt="PeopleCore 시스템 아키텍처" width="900" />
</p>

---

<br>

## 4. 상세 서비스 화면

<details>
<summary><h3>로그인</h3></summary>


</details>

<details>
<summary><h3>전자결재</h3></summary>

<details>
<summary>양식 수정 및 버전관리</summary>
<br/>
<p align="center">
관리자가 결재 양식을 등록·수정하고 버전 이력을 관리하는 화면
<br/>
  <img src="picture/gifs/approval-settings.gif" alt="전자결재 - 양식 설정" />
</p>
</details>

<details>
<summary>결재 문서번호 규칙 설정</summary>
<br/>
<p align="center">
부서·양식·날짜·순번 조합으로 회사별 문서번호 규칙을 정의하는 화면
<br/>
  <img src="picture/gifs/approval-num-setting.gif" alt="전자결재 - 문서번호 설정" />
</p>
</details>

<details>
<summary>결재 상신</summary>
<br/>
<p align="center">
사원이 양식을 선택하고 결재선·참조·열람자를 지정해 문서를 상신하는 화면
<br/>
  <img src="picture/gifs/approval-pay-up.gif" alt="전자결재 - 결재 상신" />
</p>
</details>

<details>
<summary>결재 최종 승인</summary>
<br/>
<p align="center">
결재자가 문서를 검토 후 승인·반려 처리하고 마지막 결재 시 문서가 완료되는 화면
<br/>
  <img src="picture/gifs/approval-pay-ok2.gif" alt="전자결재 - 결재 승인" />
</p>
</details>

<details>
<summary>결재환경설정 - 기본 설정 (서명관리)</summary>
<br/>
<p align="center">
사원 본인의 서명 이미지를 업로드·삭제하고 결재 작성 방식을 설정하는 화면
<br/>
  <img src="picture/my-signuture.png" alt="전자결재 - 결재환경설정 기본" />
</p>
</details>

<details>
<summary>결재환경설정 - 부재/위임 설정</summary>
<br/>
<p align="center">
부재 기간 동안 본인에게 온 결재를 대결자에게 자동 위임하도록 설정하는 화면
<br/>
  <img src="picture/approval-delegated.png" alt="전자결재 - 부재/위임 설정" />
</p>
</details>

<details>
<summary>개인 문서함 관리</summary>
<br/>
<p align="center">
사원이 본인 문서함을 추가·삭제·이관하고 순서를 조정하는 화면
<br/>
  <img src="picture/my-folder.png" alt="전자결재 - 개인 문서함 관리" />
</p>
</details>

<details>
<summary>개인 문서함 - 자동분류 규칙</summary>
<br/>
<p align="center">
제목·양식명·기안자 등 조건을 만족하는 완료 문서를 지정 문서함으로 자동 분류하는 규칙 설정 화면
<br/>
  <img src="picture/my-folder-rule.png" alt="전자결재 - 자동분류 규칙" />
</p>
</details>

</details>

<details>
<summary><h3>캘린더</h3></summary>

<!-- 캘린더 서비스 화면 자료를 여기에 추가하세요. -->

</details>

<details>
<summary><h3>내 설정</h3></summary>

<!-- 내 설정 서비스 화면 자료를 여기에 추가하세요. -->

</details>

<details>
<summary><h3>파일함</h3></summary>

<!-- 파일함 서비스 화면 자료를 여기에 추가하세요. -->

</details>

<details>
<summary><h3>통합검색</h3></summary>

<!-- 통합검색 서비스 화면 자료를 여기에 추가하세요. -->

</details>

<details>
<summary><h3>AI</h3></summary>

<!-- AI 서비스 화면 자료를 여기에 추가하세요. -->

</details>

<details>
<summary><h3>조직도</h3></summary>

<!-- 조직도 서비스 화면 자료를 여기에 추가하세요. -->

</details>

<details>
<summary><h3>휴가</h3></summary>

<!-- 휴가 서비스 화면 자료를 여기에 추가하세요. -->

</details>

<details>
<summary><h3>근태</h3></summary>

<details>
<summary>근태 정책 설정</summary>
<br/>
<p align="center">
관리자가 회사의 근태 관련 정책(근무시간·지각·조퇴·초과근무 등)을 설정하는 화면
<br/>
  <img src="picture/gifs/attendance-settings.gif" alt="근태 - 정책 설정" />
</p>
</details>

<details>
<summary>전사 근태현황 (일자별)</summary>
<br/>
<p align="center">
관리자용 전사 근태 현황 대쉬보드(일자별로 조회하는 화면)
<br/>
  <img src="picture/admin-attendance.png" alt="근태 - 전사 근태현황 (일자별)" />
</p>
</details>

<details>
<summary>전사 근태현황 (집계)</summary>
<br/>
<p align="center">
관리자용 전사 근태 현황 대쉬보드 (집계 화면)
<br/>
  <img src="picture/admin-attendance-all.png" alt="근태 - 전사 근태현황 (집계)" />
</p>
</details>

<details>
<summary>전사 근태현황 (기간별)</summary>
<br/>
<p align="center">
관리자용 전사 근태 현황 대쉬보드 (기간별 조회하는 화면)
<br/>
  <img src="picture/admin-attendance-day.png" alt="근태 - 전사 근태현황 (기간별)" />
</p>
</details>

<details>
<summary>초과근로 신청</summary>
<br/>
<p align="center">
초과 근로 신청 화면
<br/>
  <img src="picture/attendance-overRequest.png" alt="근태 - 초과근로 신청" />
</p>
</details>

<details>
<summary>근태 정정 신청</summary>
<br/>
<p align="center">
사원이 자신의 근태 기록을 정정 신청하는 화면
<br/>
  <img src="picture/attendance-modify.png" alt="근태 - 근태 정정 신청" />
</p>
</details>

<details>
<summary>내 근태현황</summary>
<br/>
<p align="center">
사원 본인의 근태 현황을 볼 수 있는 화면
<br/>
  <img src="picture/attendance-my.png" alt="근태 - 내 근태현황" />
</p>
</details>

</details>

<details>
<summary><h3>급여</h3></summary>

<!-- 급여 서비스 화면 자료를 여기에 추가하세요. -->

</details>

<details>
<summary><h3>성과</h3></summary>

<!-- 성과 서비스 화면 자료를 여기에 추가하세요. -->

</details>

---

<br>

## 5. 기술 문서

각 카테고리를 펼치면 관련 설계·운영 문서로 이동합니다.

<details>
<summary><h3>AI</h3></summary>

| 문서                                               | 핵심 내용                                                                                                 |
|--------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| [통합검색 (Elasticsearch)](docs_md/elasticsearch.md) | `unified_search` 인덱스 설계 · Nori/n-gram 분석기 · BM25+kNN 하이브리드(RRF) · Debezium CDC 색인 · 멀티테넌트/권한 필터       |
| [AI Copilot](docs_md/ai-copilot.md)              | 민감도 분류 → Anthropic / 사내 sLLM(EXAONE) 이중 라우팅 · Tool-Use 루프 · Prompt Caching(input -79%) · 응답 인용·액션 스키마 |

</details>

<details>
<summary><h3>배치 / 스케줄러</h3></summary>

| 문서                                                                   | 핵심 내용                          |
|----------------------------------------------------------------------|--------------------------------|
| [Quartz + Spring Batch 마이그레이션](docs_md/batch-scheduler-migration.md) | EKS 다중 파드 분산 스케줄링 + 멱등 배치 잡 운영 |

</details>

<details>
<summary><h3>근태</h3></summary>

| 문서                                             | 핵심 내용                                 |
|------------------------------------------------|---------------------------------------|
| [MySQL 월별 파티셔닝](docs_md/mysql-partitioning.md) | 수천만 행 근태 테이블 월별 파티셔닝 + 더티체킹 함정 해결     |
| [출퇴근 IP 정책](docs_md/commute-ip-policy.md)      | 멀티 홉 환경에서 클라이언트 공인 IP 정확 추출 + CIDR 매칭 |

</details>


---

<br>

## 6. 성능 테스트

| 영역           | 문서                                          | 핵심 내용                                                     |
|--------------|---------------------------------------------|-----------------------------------------------------------|
| AI sLLM 벤치마크 | [sLLM Benchmark](docs_md/sllm-benchmark.md) | EXAONE 3.5 vs Qwen 2.5 비교 · 한국어 품질 우위 채택 · 온프레미스 50 tok/s |
| 프롬프트 캐싱      | [Prompt Caching](docs_md/prompt-caching.md) | Prompt Caching 83% 비용 절감 · 5분 TTL · Haiku 4.5 단가 기준       |

---

<br>

<details>
<summary><h2>7. 실행 방법</h2></summary>

### 로컬 실행 가이드 (git clone → 실행)

`git clone` 부터 로컬 실행까지 한 번에 따라할 수 있는 가이드입니다.

#### 준비물

| 항목             | 비고                                |
|----------------|-----------------------------------|
| JDK 17         |                                   |
| Node.js 18+    |                                   |
| Docker Desktop |                                   |
| MySQL 8.0      | binlog 활성화 + 팀에서 받은 SQL 4종 import |
| IntelliJ IDEA  | 권장                                |

> **MySQL binlog** — `[mysqld]` 섹션에 `log_bin = mysql-bin` / `binlog_format = ROW` / `binlog_row_image = FULL` /
`server_id = 1` 추가 후 서비스 재시작. (OS별 설정 파일 경로는 아래 통합검색 상세 참고)
> **SQL import 순서** — `peoplecore-common` → `peoplecore-hr-1` → `peoplecore-hr-2` → `peoplecore-collab`.

#### 1) 레포 클론

```bash
git clone https://github.com/beyond-sw-camp/be23-fin-2team-PeopleCore-be.git
git clone https://github.com/beyond-sw-camp/be23-fin-2team-PeopleCore-fe.git
```

#### 2) 비밀 파일 배치 (팀 메신저 수령)

| 파일                        | 위치                                                                       |
|---------------------------|--------------------------------------------------------------------------|
| `application-local.yml`   | [search-service/src/main/resources/](search-service/src/main/resources/) |
| `debezium-connector.json` | [scripts/search/](scripts/search/)                                       |

> `debezium-connector.json`의 `database.password`는 본인 로컬 MySQL 비번으로 수정.

#### 3) 인프라 기동

백엔드 루트에서:

```bash
docker-compose up -d
```

| 컨테이너                       | 포트          | 역할             |
|----------------------------|-------------|----------------|
| `redis1` / `redis2`        | 6379 / 6380 | 캐시 · 분산락       |
| `kafka` + `kafka-connect`  | 9092 / 8083 | 메시징 + Debezium |
| `elasticsearch` + `kibana` | 9200 / 5601 | 통합검색           |
| `minio`                    | 9000 / 9001 | 파일 스토리지        |
| `face-api`                 | 8001        | 얼굴인식 (Python)  |

> `search-init` · `minio-init` 컨테이너가 ES 인덱스 · Debezium 커넥터 · MinIO 버킷을 자동 세팅.

#### 4) 백엔드 기동 (IntelliJ)

각 모듈 메인 클래스를 **순서대로** Run:

1. `eureka-server` (8761)
2. `api-gateway` (8000)
3. `hr-service`
4. `collaboration-service`
5. `search-service`

> 모든 서비스 `profiles.active: local` 기본 — 추가 VM 옵션 불필요.

#### 5) 프론트엔드 기동

```bash
cd be23-fin-2team-PeopleCore-fe
npm install
npm run dev
```

브라우저에서 `http://localhost:5173` 접속.

#### 6) 동작 확인

```bash
curl http://localhost:8761                                              # Eureka
curl http://localhost:9200/unified_search                               # ES 인덱스
curl http://localhost:8083/connectors/peoplecore-mysql-connector/status # Debezium RUNNING
```

#### 트러블슈팅

| 증상                       | 해결                                                                                     |
|--------------------------|----------------------------------------------------------------------------------------|
| Debezium `state: FAILED` | `debezium-connector.json` DB 비번 확인 → 커넥터 DELETE → `docker-compose restart search-init` |
| search-service 기동 실패     | `application-local.yml` 배치 누락                                                          |
| 검색 500 / 결과 0건           | `./scripts/search/reindex.sh` 로 재색인                                                    |

---

### 통합 검색 (Elasticsearch + Debezium CDC) 로컬 세팅 상세

MySQL의 변경 이벤트를 Debezium이 binlog 기반으로 감지하여 Kafka → search-service → Elasticsearch로 전파합니다. hr-service 코드는 MySQL에만 저장하면 되고,
검색 색인은 완전히 분리되어 있습니다.

> 🔧 **운영 가이드**: 인덱스 재색인·트러블슈팅 절차는 [scripts/search/README.md](scripts/search/README.md) 참고.

### 빠른 시작

1. **팀 메신저에서 받은 2개 파일을 지정 위치에 배치** (0번 참고)
2. **MySQL binlog 활성화** (최초 1회, 1번 참고)
3. **`docker-compose up -d`**
4. **IntelliJ에서 서비스 기동** — config → eureka → gateway → hr → collaboration → **search**

### 0. 사전 파일 배치 (팀 메신저에서 수령)

아래 두 파일은 git에 포함되지 않으므로 팀 메신저에서 받아 지정 위치에 저장하세요.

| 파일                        | 배치 위치                                                     |
|---------------------------|-----------------------------------------------------------|
| `application-local.yml`   | `search-service/src/main/resources/application-local.yml` |
| `debezium-connector.json` | `scripts/search/debezium-connector.json`                  |

> `debezium-connector.json`의 `database.password`가 본인 로컬 MySQL 비밀번호와 다르면 수정 필요.

### 1. MySQL binlog 활성화 (최초 1회)

MySQL 설정 파일의 `[mysqld]` 섹션에 추가:

```ini
log_bin = mysql-bin
binlog_format = ROW
binlog_row_image = FULL
server_id = 1
```

**설정 파일 위치 & 재시작 (OS별)**

| OS             | 설정 파일                                                                         | 재시작                                                        |
|----------------|-------------------------------------------------------------------------------|------------------------------------------------------------|
| Windows        | `C:\ProgramData\MySQL\MySQL Server 8.0\my.ini`                                | 서비스 관리자 → MySQL 재시작                                        |
| Mac (Homebrew) | `/opt/homebrew/etc/my.cnf` (Apple Silicon) 또는 `/usr/local/etc/my.cnf` (Intel) | `brew services restart mysql`                              |
| Mac (공식 설치)    | `/etc/my.cnf` 또는 `/usr/local/mysql/etc/my.cnf`                                | `sudo /usr/local/mysql/support-files/mysql.server restart` |

DataGrip/DBeaver에서 검증:

```sql
SHOW
VARIABLES WHERE Variable_name IN ('log_bin','binlog_format','binlog_row_image','server_id');
```

- `log_bin=ON`, `binlog_format=ROW`, `binlog_row_image=FULL`, `server_id≥1` 이어야 함

### 2. 인프라 기동

프로젝트 루트에서:

```bash
docker-compose up -d
```

자동으로 다음이 실행됩니다:

- Elasticsearch (9200) + Kibana (5601)
- Kafka (9092) + Kafka Connect + Debezium (8083)
- `search-init` 컨테이너가 ES 인덱스 생성 + Debezium Connector 자동 등록

### 3. 세팅 검증

```bash
curl http://localhost:9200/unified_search                       # 인덱스 존재 확인
curl http://localhost:8083/connectors                           # ["peoplecore-mysql-connector"]
curl http://localhost:8083/connectors/peoplecore-mysql-connector/status   # state: RUNNING
```

### 4. 서비스 기동 (IntelliJ)

아래 순서대로 기동:

1. `config-server`
2. `eureka-server`
3. `api-gateway`
4. `hr-service`
5. `collaboration-service`
6. **`search-service`** — 통합검색 기능 사용을 위해 필수

### 5. 사용

- 통합검색 API: `GET /search-service/search?keyword=...&type=EMPLOYEE|DEPARTMENT|APPROVAL|CALENDAR`
- MySQL INSERT/UPDATE/DELETE → Debezium이 감지 → ES 자동 색인 (1초 내)
- 데이터는 Docker Volume(`es-data`, `kafka-data`)에 영속화되어 재시작에도 유지

### 6. 트러블슈팅

| 증상                        | 원인                         | 해결                                                                                                                                                        |
|---------------------------|----------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| Connector `state: FAILED` | DB 비밀번호 불일치                | `scripts/search/debezium-connector.json`의 `database.password` 수정 → `curl -X DELETE .../peoplecore-mysql-connector` → `docker-compose restart search-init` |
| search-service 기동 실패      | `application-local.yml` 없음 | 팀 메신저에서 받아 `search-service/src/main/resources/`에 배치                                                                                                       |
| 검색 결과 0건                  | Debezium 초기 스냅샷 진행 중       | `curl .../status`로 상태 확인, 30초 대기                                                                                                                          |
| 검색 시 500 에러               | ES 인덱스 매핑 불일치              | 아래 "초기화" 절차 수행                                                                                                                                            |
| binlog 설정 후에도 OFF         | MySQL 재시작 안 됨              | 위 1번 표의 재시작 명령 재확인                                                                                                                                        |

### 7. 재색인이 필요한 경우

매핑 변경·데이터 누락 등으로 인덱스를 처음부터 다시 만들어야 할 때는 **Debezium offset, ES 인덱스, Kafka consumer group 3곳을 모두 리셋**해야 합니다. 하나라도 빠지면 부분
누락이 발생합니다.

```bash
./scripts/search/reindex.sh
```

상세 절차·트러블슈팅·검증 방법은 [scripts/search/README.md](scripts/search/README.md) 참고.

### 아키텍처

```
MySQL (binlog)
  ↓ Debezium MySQL Connector
Kafka topics (peoplecore.peoplecore.employee 등)
  ↓ search-service CdcEventListener
Elasticsearch (unified_search 인덱스)
  ↓
Search API (/search-service/search)
```

`hr-service`는 Search 로직을 전혀 알지 못하며, DB 저장만 담당합니다. MSA에서의 완전한 decoupling 달성.

</details>

---

<br>

## 8. 트러블 슈팅

<details>
<summary><h3>전자결재</h3></summary>

<details>
<summary>1. 동시성 충돌 - 결재 승인과 기안자 회수</summary>

**문제 사항**

- 결재자가 승인 버튼을 누르는 찰나에 기안자가 동일 문서 회수 요청
- 두 트랜잭션이 같은 `ApprovalDocument` 행을 동시 UPDATE → 한쪽 결과가 다른 쪽 결과를 덮어쓰기
- 결과적으로 "회수된 문서가 승인 상태"·"승인된 문서가 기안 대기" 같은 잘못된 최종 상태 노출 가능

**원인 분석**

- JPA 기본 쓰기 동작은 마지막 커밋이 항상 승리(Last-Write-Wins) → 동시성 제어 부재 시 정합성 보장 불가
- 결재 도메인 특성상 충돌 자체는 드물지만, 한 번이라도 발생하면 감사 로그 / 결재 이력의 신뢰성이 깨짐 → 락 도입 필요

**시도 방법**

- DB 비관적 락(`@Lock(PESSIMISTIC_WRITE)`) 검토
    - `SELECT ... FOR UPDATE` 로 행 선점 → 충돌 사전 차단
    - 단점 : 결재 승인 트랜잭션이 서명 첨부·외부 알림으로 길어 후행 회수 요청 락 대기 → 처리량 저하, DB 커넥션 점유
- Redis 분산 락(`SETNX lock:approval:{docId}`) 검토
    - 외부 저장소로 사전 차단 가능
    - 단점 : Redis 장애가 결재 전체 장애로 전파, 결재 도메인이 외부 캐시 가용성에 종속

**해결 방법**

- `ApprovalDocument` 에 `@Version` 필드 추가 → JPA 가 UPDATE 시 `WHERE id = ? AND version = ?` 자동 부여
- 후행 트랜잭션은 0 row affected → `OptimisticLockException` 발생 시 사용자에게 "다른 사용자가 먼저 처리했습니다" 안내 후 화면 갱신
- 락 점유 0 → 처리량 유지로 운영 효율 향상
- 충돌 시점 즉시 차단 → 잘못된 상태 노출 방지로 UX 개선

</details>

<details>
<summary>2. 채번 동시성 - 동시 기안 시 중복 번호 발급</summary>

**문제 사항**

- 다중 사용자가 같은 부서·양식·날짜에 동시 기안 시 동일 `ApprovalSeqCounter.seq` 값을 읽어 같은 순번 발급
- 동일 문서번호 INSERT → `doc_num` UNIQUE 제약 위반으로 한쪽 사용자 기안 실패
- 정상 사용자가 직접 재시도해야 하는 부담

**원인 분석**

- 채번 로직이 `read → +1 → write` 흐름 → 격리 수준 부족 시 두 트랜잭션이 같은 값을 읽음
- 채번은 짧고 빈번한 핫 리소스 → 일반 결재보다 충돌 빈도 훨씬 높음

**시도 방법**

- UNIQUE 제약 단독 : 중복은 막히나 충돌 시 호출자가 직접 재시도 → 사용자/프론트가 책임 부담
- 비관적 락 단독 : 카운터 행 선점으로 직렬화 가능, 락 타임아웃 시 사용자 노출, 단일 안전망이라 장애 내성 부족

**해결 방법**

- 비관적 락 + 낙관적 락 + 자동 재시도 3중 안전망 적용
- `findWithLock` 으로 카운터 행 `PESSIMISTIC_WRITE` 선점 + `@Version` 이중 검증 → 동시 채번 직렬화 및 락 우회 차단
- `DataIntegrityViolationException` 캐치 후 최대 3회 자동 재시도 → 사용자는 충돌 인지 없이 정상 채번으로 UX 개선
- 중복 문서번호 원천 차단 → 채번 장애 대응 비용 0 으로 운영 효율 향상

</details>

<details>
<summary>3. 상태 전이 복잡도 - State Pattern 적용</summary>

**문제 사항**

- 결재 문서는 임시저장 / 진행중 / 승인 / 반려 / 회수 등 다수 상태를 가짐
- 각 상태별 다른 행동(승인/반려/회수/재기안 허용 여부)에 대한 if-else 체인으로 비즈니스 로직 곳곳에 분기 반복

**원인 분석**

- 상태 전이 규칙이 곳곳에 흩어지면 OCP 위반 → 신규 상태 추가 시 회귀 위험
- 분기 누락 시 잘못된 전이가 통과 → 데이터 무결성 직결

**시도 방법**

- if-else / switch 분기 : 단순하나 상태 추가마다 모든 메서드 분기 수정 → 누락 위험
- enum 메서드 내부 분기 : 한 곳에 모이지만 메서드별 거대한 switch 누적, 가독성 ↓

**해결 방법**

- State Pattern 적용 → `ApprovalState` 인터페이스 + 상태별 구현체(`DraftState`, `PendingState`, `ApprovedState`, `RejectedState`,
  `CanceledState`)
- 각 State 가 자기 자신의 허용 동작을 캡슐화 → 호출부 분기 제거, 신규 상태는 클래스 추가만으로 확장
- 잘못된 전이 즉시 차단 → 데이터 정합성 보장으로 운영 효율 향상, 일관된 에러 응답으로 UX 개선

</details>

<details>
<summary>4. 동적 검색 조건 - QueryDSL 활용</summary>

**문제 사항**

- 문서함 조회는 제목·기안자·날짜·양식·상태 등 다수 조건이 선택적으로 조합
- 정적 쿼리로는 모든 조합 대응 불가
- 결재선 기반 필터(대기함·참조함)와 다중 문서함 카운트가 겹쳐 N+1·중복 쿼리 우려

**원인 분석**

- 문서함은 사용자 첫 진입 화면 → 응답 지연 시 체감 UX 직접 악화
- 조건 분기를 코드로 풀면 메서드/쿼리 폭증, 유지보수 비용 누적

**시도 방법**

- JPQL 문자열 concat : 단순하나 타입 검증 불가, 런타임 오류 위험
- Specification (Criteria API) : 타입 안전하나 verbose, 가독성·작성성 ↓

**해결 방법**

- QueryDSL + `BooleanBuilder` 로 동적 WHERE 조립 → 타입 안전 + 입력 조건만 반영
- 다중 문서함 카운트는 단일 쿼리로 통합 → 첫 진입 응답 단축으로 UX 개선
- N+1 제거 + 불필요 JOIN 차단 → DB 호출 감소로 운영 효율 향상

</details>

<details>
<summary>5. 문서번호 유연성 - Strategy Pattern 적용</summary>

**문제 사항**

- 회사마다 문서번호 형식이 상이 (부서코드/양식명/커스텀 텍스트 등 조합 다양)
- 단일 빌더에 분기로 처리하면 신규 회사 온보딩마다 코드 수정 필요

**원인 분석**

- 문서번호는 회사별 정책에 종속되는 가변 영역 → 핵심 결재 로직과 분리 필요
- 분기형 구현은 OCP 위반 → 회사 추가가 곧 회귀 위험

**시도 방법**

- 단일 빌더 내부 분기 : 단순하나 회사 추가 시 분기 폭증
- 설정값 기반 템플릿 문자열 치환 : 유연하나 문자열 의존, 검증 약함

**해결 방법**

- Strategy Pattern + Registry 적용 → 슬롯 타입별 독립 객체로 분리, `SlotTypeRegistry` 가 런타임에 선택
- 신규 슬롯은 구현체 추가만으로 확장 → 코드 수정 없이 회사 정책 대응으로 운영 효율 향상
- 회사 정책에 맞는 문서번호 노출 → UX 개선

</details>

<details>
<summary>6. Kafka - 비동기 이벤트 기반 알림 및 캐시 무효화</summary>

**문제 사항**

- 결재 도메인이 알림·HR·근태 등 타 서비스 모듈과 직접 동기 통신 → 한쪽 장애가 결재 전체 실패로 전파되고 응답 지연 누적
- HR 부서 변경 시 Collaboration 캐시 무효화·결재 반영 후 정합성 위반 발견 등, 동기 호출만으로는 다룰 수 없는 후행 처리 다수

**원인 분석**

- MSA 환경에서 동기 결합은 장애·지연을 그대로 전파 → 핵심 트랜잭션과 부수/후행 작업 분리 필요
- 분산 트랜잭션(2PC)은 도입 비용 과다 → 메시지 기반 비동기·보상 모델이 현실적

**시도 방법**

- 동기 HTTP 호출 : 강결합, 알림/HR 장애가 결재 실패로 전파
- 인메모리 이벤트(Spring `ApplicationEventPublisher`) : 단일 JVM 한정 → 타 서비스 전파 불가

**해결 방법**

- Kafka 토픽으로 결재 트랜잭션과 부수 작업(알림/캐시 무효화) 분리 → 부수 장애가 결재로 전파되지 않아 운영 효율 향상, 응답 지연 제거로 UX 개선
- 보상 트랜잭션 : 후행 서비스가 정합성 위반 감지 시 역방향 이벤트 발행 → 선행 서비스가 결재 자동 반려로 상태 복구
- 일시 장애는 자동 재시도, 반복 실패 메시지는 별도 DLT 로 분리해 운영 가시성 확보

</details>

<details>
<summary>7. Redis 분산 캐싱 - 서비스 간 동기 호출 최소화</summary>

**문제 사항**

- 결재 문서 조회마다 HR 데이터(부서·직급) 필요 → 매 요청 HR 호출 시 서비스 의존도·응답 지연 누적
- 멀티 파드 환경에서 파드별 캐시 상태가 달라져 정합성 문제 발생

**원인 분석**

- HR 데이터는 조회 빈도 ↑·변경 빈도 ↓ → 캐싱 적합
- 멀티 파드에서 일관된 캐시 상태를 유지하려면 외부 공유 저장소 필요

**시도 방법**

- HR 매번 호출 : 일관성은 강하나 호출 비용·지연 누적
- 로컬 캐시(Caffeine) : 빠르나 파드별 상태 상이, 멀티 파드 공유 불가

**해결 방법**

- Redis 분산 캐시 + Cache-Aside → 결재 조회 응답 단축으로 UX 개선
- Kafka(`hr-dept-updated`) 연동 무효화 → HR 변경 즉시 반영으로 정합성 유지, HR 호출 횟수 감소로 운영 효율 향상

</details>

</details>

<details>
<summary><h3>근태 관리</h3></summary>

<details>
<summary>1. 출근이 두 번 찍히는 문제 - 동시 체크인 중복 INSERT</summary>

**문제 사항**

- 출근 버튼 연타·네트워크 재전송으로 같은 `(companyId, empId, workDate)` 키에 출근 레코드가 2건 생성
- 동일 사원이 같은 날짜에 출근 2회로 잡혀 근태 집계·급여 반영 정합성 훼손
- 야간 ABSENT 배치가 만든 빈 레코드와 사용자 체크인이 겹쳐 중복 발생 가능

**원인 분석**

- 체크인은 사용자 첫 클릭 시점에 몰리는 짧고 빈번한 핫 요청 → 트랜잭션 격리만으로는 동일 키 동시 INSERT 차단 불가
- 야간 ABSENT 배치가 미리 만든 빈 레코드와도 충돌 가능 → 단순 select-then-insert 만으로는 race window 존재

**시도 방법**

- 애플리케이션 락(`synchronized`/`ReentrantLock`) : 단일 JVM 한정, 멀티 파드 환경에서 무력화
- Redis 분산 락 : 차단 가능하나 체크인 한 건마다 락 획득/해제 비용 + Redis 장애 의존성 도입

**해결 방법**

- DB UNIQUE 제약으로 중복 INSERT 원천 차단 → 정합성 보장
- 1차 방어 : 서비스 진입 시 동일 키 레코드 선조회 → ABSENT 배치 레코드 포함 즉시 409 반환으로 정상 케이스 빠른 응답, UX 개선
- 2차 방어 : `saveAndFlush` 로 UNIQUE 위반 즉시 감지 → race window 통과 요청도 안전 차단
- 애플리케이션 락 0 → 멀티 파드 환경에서 추가 인프라 없이 동작, 운영 효율 향상

</details>

<details>
<summary>2. 주간 집계 N+1 - 사원 수에 비례한 응답 지연</summary>

**문제 사항**

- 주간 근태 집계 진입 시 사원 수가 늘수록 응답이 선형으로 느려짐
- 사원 100명 기준 SELECT 700+회 발산 → DB 왕복으로만 수 초 단위 응답 지연 발생
- 첫 진입 화면이 무거워져 관리자가 페이지 로딩만 기다리는 UX 악화

**원인 분석**

- 사원마다 7일치 출퇴근·휴가를 개별 조회 → 사원 수 × (7일 + 휴가) 쿼리로 N+1 폭증
- 사원 루프 안에서 요일·휴가 비율을 매번 재계산 → 동일 연산이 사원 수만큼 반복돼 CPU 도 함께 누적

**시도 방법**

- 사원별 7일 개별 조회 : 단순하나 사원 100명 = 700+ 쿼리, 응답시간이 사원 수에 선형 폭증
- 단일 거대 조인 쿼리 : 왕복 1회로 줄지만 카티전 곱으로 결과 행 폭증, DTO 매핑 복잡

**해결 방법**

- 3쿼리 일괄 조회 : 사원 / 출퇴근 / 승인 휴가를 각각 플랫 DTO 로 한 번에 조회 → DB 왕복을 사원 수와 무관한 상수 3 으로 고정
- 메모리 인덱싱 : `Map<empId, Map<workDate, WorkStatus>>` + `Map<empId, 주간누적분>` + `Map<empId, Map<workDate, 휴가비율>>` 사전 구축 →
  매트릭스 조회 O(1)
- 요일 비트마스킹 사전 계산(`dayBits[7]`) : 사원 루프 안에서 근무예정일 판정을 비트 AND 1회로 처리, 7일 재계산 제거
- 결과 : 사원 수에 비례하던 응답시간을 상수 시간대로 평탄화 → 운영 효율 + UX 동시 개선

</details>

</details>

---

<br>

## 9. 회고

<details>
<summary><b>이수림</b></summary>
<br>

> 작성 예정

</details>

---

<details>
<summary><b>정명진</b></summary>
<br>

> 프로젝트를 진행하며 몰랐던 기술에 대해 공부를 하며 많은 것을 배우고 성장할 수 있습니다. 전체적인 설계를 주도하며 실무와 비슷한 B2B 환경을 만들기 위해서는 고려해야 할 부분이 많다고 느꼈으며 이에 따른
> 흥미를 느낄 수 있었습니다. 팀장으로서의 역할이 무엇인지 배웠고 이 과정을 함께해준 팀원들 덕분에 목표했던 결과물을 완성도 있게 마무리할 수 있었습니다.

</details>

---

<details>
<summary><b>홍진희</b></summary>
<br>

> 작성 예정

</details>

---

<details>
<summary><b>황주완</b></summary>
<br>

> 작성 예정



</details>

---

<br>

## 10. 그 외 산출물

### 프로젝트 문서

- [WBS 및 요구사항 명세서](https://docs.google.com/spreadsheets/d/1ALYx-2p5l8czzkQxdX7Dp3tdlmTaNh0fP9mfEfIhK14/edit?usp=sharing)
- [기획서](https://docs.google.com/document/d/1LhBwkw5gadTXXApqSiI7-_ngIhpgbRAm/edit?usp=sharing&ouid=113011859077434472718&rtpof=true&sd=true)
- [화면 설계 (Figma)](https://www.figma.com/design/GRt4wS7G4Gc4oMM8hzOZSi/PeopleCore-%ED%99%94%EB%A9%B4-%EC%84%A4%EA%B3%84?node-id=15-71&t=PgQIPCy8W7eyuK2v-1)
- [프로그램 사양서 및 단위테스트결과서](https://documenter.getpostman.com/view/51059727/2sBXqDrhar)

### ERD

<details>
<summary>ERD 전체</summary>

![ERD 전체](picture/PEOPLECORE.png)

</details>

<details>
<summary>HR / 기타 모듈 ERD</summary>

![HR / 기타 모듈 ERD](picture/peoplecore-another.png)

</details>

<details>
<summary>Collaboration 모듈 ERD (전자결재, 캘린더, 알림)</summary>

![Collaboration 모듈 ERD](picture/PEOPLECORE-PURPLE.png)

</details>
