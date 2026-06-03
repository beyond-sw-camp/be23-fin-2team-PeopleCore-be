# PeopleCore - HR 기반 ERP SaaS

<p align="center">
  <!-- TODO: 메인 이미지 교체 (예: picture/main.png) -->
  <img src="picture/peoplecore-banner.png" alt="PeopleCore 메인 이미지" width="400" />
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
<summary><font size="5"><strong>전자결재</strong></font></summary>

- 양식 수정 및 버전관리

<img src="picture/gifs/approval-settings.gif" alt="전자결재 - 설정" />

- 결재 문서번호 규칙 설정

<img src="picture/gifs/approval-num-setting.gif" alt="전자결재 - 문서번호 설정" />

- 결재 상신

<img src="picture/gifs/approval-pay-up.gif" alt="전자결재 - 결재 상신" />

- 결재 최종 승인

<img src="picture/gifs/approval-pay-ok2.gif" alt="전자결재 - 결재 승인" />

</details>

<details>
<summary><font size="5"><strong>사원 관리 (Employee)</strong></font></summary>

**사원등록 폼 수정**
![사원등록 폼 수정](picture/gifs/사원등록%20폼수정.gif)

**사원 등록**
![사원 등록](picture/gifs/사원등록.gif)

</details>

<details>
<summary><font size="5"><strong>근태 (Attendance)</strong></font></summary>

- 회사의 근태 관련 정책 설정

<img src="picture/gifs/attendance-settings.gif" alt="근태 - 설정" />

- 사원의 초과근무 신청

<img src="picture/gifs/attendance-overRequest.gif" alt="근태 - 초과근무 신청" />

- 사원의 근태 정정 신청

<img src="picture/gifs/attendance-edit.gif" alt="근태 - 정정 신청" />

</details>

<details>
<summary><font size="5"><strong>휴가 (Vacation)</strong></font></summary>

- 회사 내 휴가 정책과 회사가 만들 휴가 화면

<img src="picture/gifs/vacation-settings.gif" alt="휴가 - 설정" />

- 휴가 신청 후 승인시 자신의 캘린더에 자동 반영

<img src="picture/gifs/vacaion-request-calander.gif" alt="휴가 - 신청 캘린더" />

- 자신이 필요한 휴가 부여 신청

<img src="picture/gifs/vacaion-Grant-Request.gif" alt="휴가 - 부여 요청" />

- 관리자가 일반 사원의 휴가를 조정할 수 있는 화면 

<img src="picture/gifs/vacaion-Grant.gif" alt="휴가조정" />

</details>

<details>
<summary><font size="5"><strong>성과 평가 (Evaluation)</strong></font></summary>

**성과평가 산정**

<video src="https://github.com/user-attachments/assets/b64e8d49-1cf4-4fcb-b4d7-0eead3e217a3" autoplay muted loop playsinline width="800"></video>

**평가 결과 및 이력**

<video src="https://github.com/user-attachments/assets/ae70a95d-503b-4fbe-8fff-629dcfd9e39f" autoplay muted loop playsinline width="800"></video>

</details>

<details>
<summary><font size="5"><strong>급여 관리 (Payroll)</strong></font></summary>

- **급여 플로우** <br>
[인사팀] 급여대장 생성 -> 초과근무수당 적용 -> 확정 -> 전자결재 상신/승인 -> 지급처리 -> [사원] 급여명세서 확인 

<img src="picture/gifs/급여플로우.gif"  alt="급여 업무 플로우" width="900">
 

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
<summary><font size="5"><strong>로그인</strong></font></summary>
<p align="center">
  <img src="picture/gifs/login.gif" width="400" />
</p>

</details>

<details>
<summary><font size="5"><strong>안면인식 로그인</strong></font></summary>
<p align="center">
  <img src="picture/gifs/face_login.gif" width="400" />
</p>

</details>

<details>
<summary><font size="5"><strong>사원 관리</strong></font></summary>

**인력 현황**

<video src="https://github.com/user-attachments/assets/53c6b20f-8f03-4f1c-afb6-70109459d4b5" autoplay muted loop playsinline width="800"></video>

**인사발령 및 이력확인**

<video src="https://github.com/user-attachments/assets/357eb242-535d-49f8-8f86-7a563eaf1202" autoplay muted loop playsinline width="800"></video>

**계약서 등록**

<video src="https://github.com/user-attachments/assets/a64c6f82-f2d2-479d-86a9-5d0d00f95cfd" autoplay muted loop playsinline width="800"></video>

**퇴직처리**

<video src="https://github.com/user-attachments/assets/199e4ffc-adc6-4026-b83d-ff7de17ddab9" autoplay muted loop playsinline width="800"></video>

</details>

<details>
<summary><font size="5"><strong>전자결재</strong></font></summary>

<details>
<summary>전자결재 환경설정화면</summary>

<img src="picture/approval-settings.png" alt="전자결재 - 설정" />
</details>

<details>
<summary>양식 일괄 설정</summary>

- 결재문서의 설정을 일괄적으로 설정할 수 있는 화면이다

<img src="picture/approvalForm-settings-all.png" alt="전자결재 - 양식 일괄 설정" />
</details>

<details>
<summary>양식 수정 및 버전관리</summary>

- 관리자가 결재 양식을 등록·수정하고 버전 이력을 관리하는 화면

<img src="picture/gifs/approval-settings.gif" alt="전자결재 - 양식 설정" />
</details>

<details>
<summary>결재 문서번호 규칙 설정</summary>

- 부서·양식·날짜·순번 조합으로 회사별 문서번호 규칙을 정의하는 화면

<img src="picture/gifs/approval-num-setting.gif" alt="전자결재 - 문서번호 설정" />
</details>

<details>
<summary>결재 상신</summary>

- 사원이 양식을 선택하고 결재선·참조·열람자를 지정해 문서를 상신하는 화면

<img src="picture/gifs/approval-pay-up.gif" alt="전자결재 - 결재 상신" />
</details>

<details>
<summary>결재 최종 승인</summary>

- 결재자가 문서를 검토 후 승인·반려 처리하고 마지막 결재 시 문서가 완료되는 화면

<img src="picture/gifs/approval-pay-ok2.gif" alt="전자결재 - 결재 승인" />
</details>

<details>
<summary>결재환경설정 - 기본 설정 (서명관리)</summary>

- 사원 본인의 서명 이미지를 업로드·삭제하고 결재 작성 방식을 설정하는 화면

<img src="picture/my-signuture.png" alt="전자결재 - 결재환경설정 기본" />
</details>

<details>
<summary>결재환경설정 - 부재/위임 설정</summary>

- 부재 기간 동안 본인에게 온 결재를 대결자에게 자동 위임하도록 설정하는 화면

<img src="picture/approval-delegated.png" alt="전자결재 - 부재/위임 설정" />
</details>

<details>
<summary>개인 문서함 관리</summary>

- 사원이 본인 문서함을 추가·삭제·이관하고 순서를 조정하는 화면

<img src="picture/my-folder.png" alt="전자결재 - 개인 문서함 관리" />
</details>

<details>
<summary>개인 문서함 - 자동분류 규칙</summary>

- 제목·양식명·기안자 등 조건을 만족하는 완료 문서를 지정 문서함으로 자동 분류하는 규칙 설정 화면

<img src="picture/my-folder-rule.png" alt="전자결재 - 자동분류 규칙" />
</details>

</details>

<details>
<summary><font size="5"><strong>캘린더</strong></font></summary>

- 캘린더 조회     
<p align="center">  
  <img src="picture/calendar.png" alt="캘린더 서비스 화면" width="900" /> 
</p>  

- 캘린더 일정 등록  
<p align="center"> 
  <img src="picture/calendar_event_create.png" alt="캘린더 일정등록 화면" width="900" /> 
</p> 

- 일정참석자 - 캘린더 일정 등록 알림
  <p align="center"> 
    <img src="picture/calendar_event_attendees_alarm.png" alt="일정참석자 일정등록 알림 화면" width="900" /> 
  </p> 

- 일정참석자 - 캘린더 일정 조회 
  <p align="center"> 
    <img src="picture/calendar_event_attendee_read.png" alt="일정참석자 일정조회 화면" width="900" /> 
  </p> 

- 관심캘린더
  - 관심캘린더 등록
    <p align="center"> 
      <img src="picture/calendar_interest_create.png" alt="관심캘린더 등록 화면" width="900" /> 
    </p> 
  - 관심캘린더 등록 요청 알림
    <p align="center"> 
      <img src="picture/calendar_interest_req_alarm.png" alt="관심캘린더 등록요청 알림 화면" width="900" /> 
    </p> 
  - 관심캘린더 일정 조회
    <p align="center"> 
      <img src="picture/calendar_interest_read.png" alt="관심캘린더 일정조회 화면" width="900" /> 
    </p> 
  - 관심캘린더 관리(환경설정) 
    <p align="center"> 
      <img src="picture/calendar_interest_settings.png" alt="관심캘린더 관리 화면" width="900" /> 
    </p> 
  
- 전사일정
  - 전사일정 등록
    <p align="center"> 
      <img src="picture/calendar_company_event_create.png" alt="전사 일정등록 화면" width="900" /> 
    </p> 
  - 직원 전사일정 조회(캘린더에 자동 저장)
    <p align="center"> 
      <img src="picture/calendar_company_event_read.png" alt="전사 일정조회 화면" width="900" /> 
    </p> 

</details>

<details>
<summary><font size="5"><strong>내 설정</strong></font></summary>

- 내 정보관리
  - 정보 조회 및 프로필 관리
    <p align="center"> 
      <img src="picture/my_settings_profile.png" alt="내정보 조회 화면-1" width="900" /> 
    </p> 
  - 외부이메일 변경
    <p align="center"> 
      <img src="picture/my_settings_mail.png" alt="내정보 조회 화면-2" width="900" /> 
    </p>
  
- 보안설정
  - 보안설정 목록
    <p align="center"> 
      <img src="picture/my_settings_protect.png" alt="보안설정 목록 화면" width="900" /> 
    </p>
  - 비밀번호 관리
    <p align="center"> 
      <img src="picture/my_settings_password.png" alt="비밀번호 관리 화면" width="900" /> 
    </p>
  - 로그인 이력 정보
    <p align="center"> 
      <img src="picture/my_settings_login.png" alt="로그인 이력 정보 화면" width="900" /> 
    </p>
  - 인사통합 pin 관리
    <p align="center"> 
      <img src="picture/my_settings_pin.png" alt="인사통합 pin 관리 화면" width="900" /> 
    </p>
</details>

<details>
<summary><font size="5"><strong>파일함</strong></font></summary>

- 파일 및 폴더 생성 / 삭제
<p align="center">
  <img src="picture/gifs/file_create.gif" width="400" />
</p>

- 파일 및 폴더 즐겨찾기
<p align="center">
  <img src="picture/gifs/file_star.gif" width="400" />
</p>

- 공용 파일함 생성 권한 설정
<p align="center">
  <img src="picture/gifs/file_permission.gif" width="400" />
</p>

- 공용 파일함 멤버 초대 및 멤버별 권한 설정
<p align="center">
  <img src="picture/gifs/file_member_permission.gif" width="400" />
</p>

</details>

<details>
<summary><font size="5"><strong>통합검색</strong></font></summary>

<!-- 통합검색 서비스 화면 자료를 여기에 추가하세요. -->

<p align="center">
  <img src="picture/gifs/search.gif" width="400" />
</p>
</details>

<details>
<summary><font size="5"><strong>AI</strong></font></summary>

- 캘린더에 일정 생성하기
<p align="center">
  <img src="picture/gifs/ai_calendar.gif" width="400" />
</p>

- 휴가신청 전자결재 생성하기
<p align="center">
  <img src="picture/gifs/search.gif" width="400" />
</p>

- 오늘 할 일(다이제스트) 조회
<p align="center">
  <img src="picture/gifs/ai_digest.gif" width="400" />
</p>

- 자신의 민감 정보 조회 
<p align="center">
  <img src="picture/gifs/ai_my_info.gif" width="400" />
</p>

- 타인의 민감 정보 조회
<p align="center">
  <img src="picture/gifs/ai_other_info.gif" width="400" />
</p>

- 이전 대화 이력 보존
<p align="center">
  <img src="picture/gifs/ai_log.gif" width="400" />
</p>

</details>

<details>
<summary><font size="5"><strong>조직도</strong></font></summary>

- 부서 생성 및 순서 편집
<p align="center">
  <img src="picture/gifs/org_create.gif" width="400" />
</p>

- 직급 생성 및 순서 편집
<p align="center">
  <img src="picture/gifs/grade.gif" width="400" />
</p>

- 직책 생성 및 순서 편집
<p align="center">
  <img src="picture/gifs/title.gif" width="400" />
</p>
</details>

<details>
<summary><font size="5"><strong>메신저</strong></font></summary>

- 1대1 채팅방 생성
<p align="center">
  <img src="picture/gifs/chat_room_create.gif" width="400" />
</p>

- 그룹 채팅방 생성
<p align="center">
  <img src="picture/gifs/chat_room_create.gif" width="400" />
</p>

- 조직도를 통한 채팅 시작
<p align="center">
  <img src="picture/gifs/org_chart_chat.gif" width="400" />
</p>

- 메시지 송/수신
<p align="center">
  <img src="picture/gifs/messaging.gif" width="400" />
</p>

- 메시지 삭제
<p align="center">
  <img src="picture/gifs/message_delete.gif" width="400" />
</p>

- 파일 전송
<p align="center">
  <img src="picture/gifs/file_send2.gif" width="400" />
</p>

- 채팅방, 메시지 검색
<p align="center">
  <img src="picture/gifs/chat_search.gif" width="400" />
</p>

- 채팅방 나가기
<p align="center">
  <img src="picture/gifs/chat_del.gif" width="400" />
</p>
</details>

<details>
<summary><font size="5"><strong>휴가</strong></font></summary>

<details>
<summary>휴가 정책 설정</summary>

- 관리자가 회사의 휴가 종류·연차 부여 규칙·소진 정책 등을 설정하는 화면

<img src="picture/gifs/vacation-settings.gif" alt="휴가 - 정책 설정" />
</details>

<details>
<summary>전사 휴가 현황</summary>

- 부서별 휴가 소진율과 사원별 잔여/사용 일수를 한눈에 확인하는 관리자용 대시보드

<img src="picture/admin-vacation-dasboard-all.png" alt="휴가 - 전사 휴가 현황" />
</details>

<details>
<summary>전사 휴가 현황 - 사원 보유 휴가</summary>

- 사원 클릭 시 해당 사원의 휴가 유형별 잔여·사용·만료 내역을 팝업으로 조회하는 화면

<img src="picture/admin-vacation-emp-balance.png" alt="휴가 - 사원 보유 휴가" />
</details>

<details>
<summary>기간별 휴가 현황</summary>

- 지정 기간 동안의 휴가자·총 사용 일수·신청 건수를 사원 단위로 조회하는 화면

<img src="picture/admin-vacation-day.png" alt="휴가 - 기간별 휴가 현황" />
</details>

<details>
<summary>휴가 결재 (신청 현황)</summary>

- 사원의 휴가 신청·부여 요청 결재 진행 상태를 관리자가 확인하는 화면

<img src="picture/admin-vacation-request.png" alt="휴가 - 휴가 결재" />
</details>

<details>
<summary>연차 촉진 이력</summary>

- 연차 사용 촉진 1·2차 통지 발송 이력과 사원 응답 결과를 관리하는 화면

<img src="picture/admin-vacation-vacation-ledger.png" alt="휴가 - 연차 촉진 이력" />
</details>

<details>
<summary>사원 휴가 조정</summary>

- 관리자가 일반 사원의 보유 휴가를 직접 가감 조정하는 화면

<img src="picture/gifs/vacaion-Grant.gif" alt="휴가 - 사원 휴가 조정" />
</details>

<details>
<summary>내 휴가현황</summary>

- 사원 본인의 연차 잔여·사용·결재 대기 일수와 예정/지난 휴가를 확인하는 화면

<img src="picture/emp-vacation-dashboard.png" alt="휴가 - 내 휴가현황" />
</details>

<details>
<summary>휴가 신청</summary>

- 휴가 유형·일자·반차 옵션을 선택해 전자결재로 상신하는 신청 화면

<img src="picture/emp-vacation-request.png" alt="휴가 - 신청" />
</details>

<details>
<summary>휴가 부여 요청</summary>

- 법정휴가·회사 제공 휴가의 부여를 사원이 요청하고 HR_ADMIN 결재로 부여받는 화면

<img src="picture/emp-vacation-grantRequest.png" alt="휴가 - 부여 요청" />
</details>

<details>
<summary>휴가 신청서 (결재 양식)</summary>

- 휴가 신청이 전자결재 양식으로 자동 변환되어 결재선을 따라 진행되는 화면

<img src="picture/emp-vacation-approval.png" alt="휴가 - 신청서 결재 양식" />
</details>

<details>
<summary>휴가 승인 후 캘린더 자동 반영</summary>

- 승인된 휴가가 본인 캘린더에 자동으로 반영되어 일정에 노출되는 화면

<img src="picture/gifs/vacaion-request-calander.gif" alt="휴가 - 캘린더 자동 반영" />
</details>

</details>

<details>
<summary><font size="5"><strong>근태</strong></font></summary>

<details>
<summary>근태 정책 설정</summary>

- 관리자가 회사의 근태 관련 정책(근무시간·지각·조퇴·초과근무 등)을 설정하는 화면

<img src="picture/gifs/attendance-settings.gif" alt="근태 - 정책 설정" />
</details>

<details>
<summary>전사 근태현황 (일자별)</summary>

- 관리자용 전사 근태 현황 대쉬보드(일자별로 조회하는 화면)

<img src="picture/admin-attendance.png" alt="근태 - 전사 근태현황 (일자별)" />
</details>

<details>
<summary>전사 근태현황 (집계)</summary>

- 관리자용 전사 근태 현황 대쉬보드 (집계 화면)

<img src="picture/admin-attendance-all.png" alt="근태 - 전사 근태현황 (집계)" />
</details>

<details>
<summary>전사 근태현황 (기간별)</summary>

- 관리자용 전사 근태 현황 대쉬보드 (기간별 조회하는 화면)

<img src="picture/admin-attendance-day.png" alt="근태 - 전사 근태현황 (기간별)" />
</details>

<details>
<summary>초과근로 신청</summary>

- 초과 근로 신청 화면

<img src="picture/attendance-overRequest.png" alt="근태 - 초과근로 신청" />
</details>

<details>
<summary>근태 정정 신청</summary>

- 사원이 자신의 근태 기록을 정정 신청하는 화면

<img src="picture/attendance-modify.png" alt="근태 - 근태 정정 신청" />
</details>

<details>
<summary>내 근태현황</summary>

- 사원 본인의 근태 현황을 볼 수 있는 화면

<img src="picture/attendance-my.png" alt="근태 - 내 근태현황" />
</details>

</details>

<details>
<summary><font size="5"><strong>급여</strong></font></summary>


- 급여정책 설정 (최고권한자 설정 화면) 
  - 급여지급 설정 
  <img src="picture/1.급여정책설정-급여지급설정.png" alt="급여지급설정 화면" width="900" />

  - 지급 항목 관리 
  <img src="picture/2.급여정책설정-지급항목관리.png" alt="지급항목관리 화면" width="900" /> 

  - 공제 항목 관리
  <img src="picture/3.급여정책설정-공제항목관리.png" alt="공제항목관리 화면" width="900" />

  - 법정수당산정
  <img src="picture/4.급여정책설정-법정수당산정.png" alt="법정수당산정 화면" width="900" />
  
  - 사회보험요율표
  <img src="picture/5.급여정책설정-사회보험요율표.png" alt="사회보험요율표 화면" width="900" />
  
  - 퇴직연금 설정
  <img src="picture/7.급여정책설정-퇴직연금설정.png" alt="퇴직연금설정 화면" width="900" />

- 급여관리 (HR담당자 업무 화면) 
  - 사원별 급여관리-연봉 확인 
  <img src="picture/8.급여관리-사원별급여관리-연봉.png" alt="사원별급여관리 연봉 조회 화면" width="900" /> 

  - 사원별 급여관리-월급여예상지급공제액 확인 
  <img src="picture/9.급여관리-사원별급여관리-월급여예상지급공제.png" alt="월급여예상지급공제액 조회 화면" width="900" /> 

  - 급여대장 
  <img src="picture/8.급여관리-급여대장.png" alt="급여대장 조회 화면" width="900" /> 

  - 입사기념일도래사원 연차 수당 조회 및 정산 
  <img src="picture/10.급여관리-연차수당산정-입사기념일도래사원.png" alt="연차수당조회 화면" width="900" /> 

  - 퇴직자 연차 수당 조회 및 정산 
  <img src="picture/10.급여관리-연차수당산정-퇴직자연차정산.png" alt="연차수당조회 조회 화면" width="900" /> 

  - 정산보험료 조회 및 산정 및 조회/급여대장 적용
    <img src="picture/11.급여관리-정산보험료.png" alt="정산보험료 화면" width="900" />

  - 퇴직금대장 
  <img src="picture/12.급여관리-퇴직금대장.png" alt="퇴직금대장 조회 화면" width="900" /> 

  - 퇴직금추계액 
  <img src="picture/13.급여관리-퇴직금추계액.png" alt="퇴직금추계액 조회 화면" width="900" /> 

  - 퇴직연금적립내역 
  <img src="picture/14.급여관리-퇴직연금적립내역.png" alt="퇴직연금 적립내역 조회 화면" width="900" /> 

- 급여관리 (사원 개인 조회 화면) 
  - 내 급여 확인 
  <img src="picture/15.내급여조회.png" alt="개인 급여 조회 화면" width="900" /> 

  - 예상퇴직금조회-근속기준 퇴직금 예상액 조회(날짜지정) 
  <img src="picture/16.근속기준퇴직금예상액.png" alt="예상퇴직금 조회 화면" width="900" /> 

  - 예상퇴직금조회-퇴직연금적립금액(DC형) 조회 
  <img src="picture/17.DC형퇴직연금적립금액.png" alt="퇴직연금적립금액 조회 화면" width="900" /> 

</details>

<details>

<summary><font size="5"><strong>성과</strong></font></summary>

**평가자 맵핑**
<video src="https://github.com/user-attachments/assets/6aacfe48-168f-40ec-8c40-2b4e37fb0481" autoplay muted loop playsinline width="800"></video>

**성과평가 규칙 설정**
![성과평가 규칙 설정](picture/gifs/성과평가%20규칙%20설정.gif)

**KPI 지표 생성**
![KPI 지표 생성](picture/gifs/kp지표%20생성.gif)

**평가 시즌 생성**
![평가 시즌 생성](picture/gifs/평가생성.gif)

**단계 개폐 및 기간 연장**
<video src="https://github.com/user-attachments/assets/2bd9a895-47ec-4bc9-a8b6-33a91ed198ae" autoplay muted loop playsinline width="800"></video>

**피평가자 목표 작성**
![피평가자 목표 작성](picture/gifs/피평가자목표작성,%20평가자검토.gif)

**평가자 검토**
![평가자 검토](picture/gifs/피평가자목표작성,%20평가자검토.gif)

**상위자 평가**
<video src="https://github.com/user-attachments/assets/2df4115b-4e4a-4084-a684-d2463160fe90" autoplay muted loop playsinline width="800"></video>

</details>

---

<br>

## 5. 기술 문서

각 카테고리를 펼치면 관련 설계·운영 문서로 이동합니다.

<details>
<summary><strong>통합검색</strong></summary>

| 문서 | 핵심 내용 |
|------|-----------|
| [통합검색 (Elasticsearch)](docs_md/elasticsearch.md) | `unified_search` 인덱스 설계 · Nori/n-gram 분석기 · BM25+kNN 하이브리드(RRF) · Debezium CDC 색인 · 멀티테넌트/권한 필터 |

</details>

<details>
<summary><font size="5"><strong>전자결재</strong></font></summary>

| 문서                                                                       | 핵심 내용                                                                          |
|--------------------------------------------------------------------------|--------------------------------------------------------------------------------|
| [결재 상태 관리 - State Pattern](docs_md/approval-state-pattern.md)             | 5개 상태(`DRAFT/PENDING/APPROVED/REJECTED/CANCELED`) × 허용 동작을 State 구현체에 캡슐화, OCP |
| [문서함 동적 검색 - QueryDSL](docs_md/approval-querydsl-search.md)              | 9개 문서함 공통 필터 헬퍼 · 위임까지 포함한 결재선 매칭 · `CaseBuilder` 로 카운트 9쿼리 → 2쿼리             |
| [Kafka 비동기 이벤트 - 결합도 분리](docs_md/kafka-event-driven.md)                  | 결재/HR/근태 동기 결합 제거 · 보상 트랜잭션 · `@RetryableTopic` + DLT 인프라                     |
| [Redis 분산 캐싱 - HR 데이터 Cache-Aside](docs_md/redis-distributed-cache.md)   | fail-soft + Kafka 이벤트 무효화 + TTL 안전망 3중 구조                                      |

</details>

<details>
<summary><font size="5"><strong>AI</strong></font></summary>

| 문서 | 핵심 내용 |
|------|-----------|
| [AI Copilot](docs_md/ai-copilot.md) | 민감도 분류 → Anthropic / 사내 sLLM(EXAONE) 이중 라우팅 · Tool-Use 루프 · Prompt Caching(input -79%) · 응답 인용·액션 스키마 |

</details>

<details>
<summary><font size="5"><strong>배치 / 스케줄러</strong></font></summary>

| 문서                                                                   | 핵심 내용                          |
|----------------------------------------------------------------------|--------------------------------|
| [Quartz + Spring Batch 마이그레이션](docs_md/batch-scheduler-migration.md) | EKS 다중 파드 분산 스케줄링 + 멱등 배치 잡 운영 |

</details>

<details>
<summary><font size="5"><strong>근태</strong></font></summary>

| 문서                                             | 핵심 내용                                 |
|------------------------------------------------|---------------------------------------|
| [MySQL 월별 파티셔닝](docs_md/mysql-partitioning.md) | 수천만 행 근태 테이블 월별 파티셔닝 + 더티체킹 함정 해결     |
| [출퇴근 IP 정책](docs_md/commute-ip-policy.md)      | 멀티 홉 환경에서 클라이언트 공인 IP 정확 추출 + CIDR 매칭 |

</details>

<details>
<summary><font size="5"><strong>사원 관리</strong></font></summary>

| 문서                                                              | 핵심 내용                                                                                          |
|-----------------------------------------------------------------|------------------------------------------------------------------------------------------------|
| [회사별 사원 폼 - 기본/커스텀 두 층위](docs_md/employee-dynamic-form.md)      | `Employee.customFields` JSON + `FormFieldSetup` · 기본 항목(on/off) + 커스텀(JSON) 분리 · 폼 정의·값 분리 |

</details>

<details>
<summary><font size="5"><strong>성과 평가</strong></font></summary>

| 문서                                                                  | 핵심 내용                                                                                  |
|---------------------------------------------------------------------|----------------------------------------------------------------------------------------|
| [시즌 무결성 아키텍처](docs_md/evaluation-season-integrity.md)               | `Season.formSnapshot` 박제 · `EvalGrade.@Version` 낙관적 락 · 평가자 퇴직 `AFTER_COMMIT` 이벤트 보정 |
| [자동 산식 - Z-score + 강제분포](docs_md/evaluation-grade-calculation.md)  | 점수 집계 → 가중치 → Z-score 팀장 편향 보정 → 강제분포 4단 알고리즘 · BigDecimal 결정성                       |

</details>


<details>
<summary><font size="5"><strong>급여</strong></font></summary>

| 문서                                                                        | 핵심 내용                                                                                                                                                                                              |
|---------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [급여 도메인 상태 머신 + 이벤트 자동화](docs_md/pay-state-event-architecture.md)         | `PayrollEmpStatus` / `SeverancePays` / `PensionDeposit` 다수 상태 머신 · 엔티티 메서드 가드 + 멱등 분기 · `PayrollPaidEvent` / `EmployeeRetiredEvent` + `@TransactionalEventListener(AFTER_COMMIT)` + `@Order` 직렬화 |
| [결재 양식 동적 빌드 + 스냅샷](docs_md/pay-approval-html-snapshot.md)                | 활성 PayItem 기준 결의서 HTML 동적 빌드 → 결재 문서에 스냅샷 영구 보존으로 시점 정합성 + 최신 데이터 반영 동시 만족 · 결재팀 (양식 골격 버전관리) / hr-service (동적 본문) 책임 분담                                                                |
| [Redis 캐시 전략 - Cache-Aside + 패턴 무효화](docs_md/pay-redis-cache-strategy.md)  | `EmpSalaryCacheService` (관리자) + `MySalaryCacheService` (사원 본인) Cache-Aside · 캐시 키 설계 (회사+사원+연도) · 패턴 기반 일괄 무효화 (`keys(pattern) + delete`) · 별도 Redis 템플릿 (`hrCacheRedisTemplate`) 분리             |
| [은행별 이체 Strategy + Factory](docs_md/pay-bank-transfer-strategy.md)        | `BankTransferFileGenerator` 인터페이스 + 은행별 구현체 6종 (KB/신한/하나/우리/농협/IBK) · `BankTransferFileFactory` 로 회사 주거래은행 → Generator 라우팅 · 공통 빌더 (`ExcelTransferBuilder`) + 은행별 컬럼 차별화                          |

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
<summary><font size="5"><strong>전자결재</strong></font></summary>

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
<summary>3. EKS 환경에서 @LoadBalanced 가 호스트명을 service-id 로 잘못 해석</summary>

**문제 사항**

- 결재 기안 중 일부 요청에서 `SERVICE_UNAVAILABLE` "HR 서비스 연결 실패" 노출
- 직전 요청은 성공, 일정 시간 후·신규 회사 ID 첫 조회 때만 재현되는 산발적 장애
- collaboration / hr / search 3개 모듈의 사내 호출에 동일 패턴 잠복 → 결재 기안 자체 중단

**원인 분석**

- 사내 호출이 Redis 캐시 → 미스 시 RestClient 2단 구조 → 캐시 만료·재배포·신규 ID 순간에만 RestClient 실제 호출되어 산발 장애로 인지
- `RestClient.Builder` 에 `@LoadBalanced` 부착 → `http://hr-service` 호스트명을 DNS 가 아닌 Spring Cloud LoadBalancer 의 service-id 로 해석 시도
- prod 환경은 `eureka.client.enabled: false` → `DiscoveryClient.getInstances("hr-service")` 가 빈 리스트 반환 → 네트워크 호출 자체가 발생하지 않고 실패
- `@CircuitBreaker` fallback 이 원인을 일반 메시지로 덮어 근본 원인이 가려짐

**시도 방법**

- `@LoadBalanced` 일괄 제거 검토 : 로컬 개발은 여전히 Eureka 디스커버리 필요 → 통째 제거 시 로컬 흐름 단절
- 정상 호출(face-api·redis·kafka)과 비교 : K8s Service 가 이미 서버사이드 LB 수행 → 클라이언트 LB 중복 확인

**해결 방법**

- 3개 모듈의 사내 호출 빈을 `@Profile("local")` / `@Profile("prod")` 두 개로 분리, 하나만 활성화되어 단일 후보로 주입
- `local` 빈만 `@LoadBalanced` 유지 (Eureka 디스커버리), `prod` 빈은 제거 → kube-dns 가 호스트명 해석, K8s Service 가 파드 분산
- 캐시 미스·재배포·신규 ID 케이스에서도 결재 기안 즉시 성공 → 체감 장애 0건으로 UX 개선
- 산발적 알림 제거로 운영팀 가짜 알람 대응 비용 0, 클라이언트 ↔ 서버 LB 이중화를 EKS 단일 경로로 단순화하여 운영 효율 향상

</details>

</details>

<details>
<summary><font size="5"><strong>근태 관리</strong></font></summary>

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

<details>
<summary><font size="5"><strong>캘린더</strong></font></summary>

<details>
<summary>1. 반복 일정 occurrence 생성 - 무한 루프 / 메모리 폭증 방어</summary>

**문제 사항**

- 사용자가 선택한 "무기한 반복" 옵션은 `until`/`count` 모두 null 로 들어와 자연 종료 조건이 없음 → 조회 범위 제어 없이 펼치면 메모리 폭증
- 클라이언트 버그·조작으로 비정상 값(음수 `intervalVal`, 잘못된 `frequency` 등)이 들어오면 cursor 가 전진 안 해 무한 루프 위험

**원인 분석**

- 정상 종료 조건(`until`/`count`)만으로는 "무기한 반복" 정상 옵션을 처리 못 함 → 조회 범위 기반 종료(`viewEnd`)가 추가로 필요
- 클라이언트가 보낸 값은 백엔드가 통제할 수 없으므로 비정상 입력 안전망(`MAX_ITERATIONS`)도 별도로 필요

**시도 방법**

- 종료 조건을 한 가지로 단순화 : 비정상 입력에 무방비 → 무한 루프 위험
- 단순 카운트 제한만 적용 : 일반 케이스는 종료되지만 조회 범위 외 occurrence 까지 생성됨

**해결 방법**

- 루프 매 반복마다 4가지 종료 조건을 동시 평가해 입력 종류와 무관하게 안전 종료 보장
  - `until` 도달 / `maxCount` 도달 — 사용자가 종료 조건 지정한 정상 입력의 자연 종료
  - `cursor > viewEnd` — "무기한 반복" 옵션도 화면 조회 범위까지만 펼침 (메모리 절약)
  - `MAX_ITERATIONS = 1000` — 비정상 입력(음수 `intervalVal`, 잘못된 `frequency` 등) 대비 마지막 안전망
- 안전망 도달 시 WARN 로그로 사후 추적 가능 → 어떤 일정이 한도에 도달했는지 식별

</details>

<details>
<summary>2. 알림 스케줄러 - 부분 실패 격리</summary>

**문제 사항**

- 매분 due 알림을 일괄 조회 후 발송하는 구조에서, 한 알림의 예외가 메서드 트랜잭션 전체를 롤백시킬 위험
- 한 건의 실패로 `markSent` 도 못 찍히고, 다음 분에 같은 알림이 재시도되며 부분 실패가 전체 재시도 폭주로 번질 수 있음

**원인 분석**

- 스케줄러 메서드를 단일 트랜잭션으로 두면 항목 간 독립성 미보장
- 알림은 사원별로 독립적인 작업인데 묶음 트랜잭션은 도메인 특성과 어긋남

**시도 방법**

- 단일 트랜잭션 유지 : 한 건 실패가 전체 발송 차단
- 모든 예외를 메서드 밖으로 던지기 : 다음 분 재시도에서도 같은 실패 반복

**해결 방법**

- 발송 루프 안에 항목별 try-catch 배치 → 예외 발생 항목만 ERROR 로깅 후 다음 항목 진행
- `markSent` 는 try 블록 안에 두어 발송 성공 항목만 정상 마킹 (실패 항목은 markSent 가 안 찍혀 다음 분 실행에서 자동으로 재시도 대상에 포함)
- 메서드 트랜잭션은 유지하되 부분 실패를 흡수하는 패턴으로 운영 안정성 확보

</details>

</details>

<details>
<summary><font size="5"><strong>급여</strong></font></summary>

<details>
<summary>1. 사원별 상태 머신 - 부분 결재 / 부분 지급 표현</summary>

**문제 사항**

- 급여대장은 월 단위로 생성되지만 실무에서는 한 대장 안의 사원들이 서로 다른 단계(산정중/확정/결재중/승인/지급)에 동시에 머무는 게 정상
- run 단일 상태(`PayrollRuns.status`) 로만 가드하면 부분 결재·부분 지급 시나리오 안정적 표현 불가

**원인 분석**

- 표면적 비즈니스 단위(월 단위) 와 실제 도메인의 변화 단위(사원 단위 전이) 가 다름 — 한 대장 안 사원들이 동시에 다른 상태(지급완료/결재중/산정중 등) 에 머무는 게 정상
- 한 단위 상태만으로 가드하면 두 번째 결재 상신·일부 사원 지급 같은 후속 처리가 막힘

**시도 방법**

- run 전체 상태 단독 관리 : 부분 승인/반려/지급에서 서비스 가드 충돌
- 화면 단(클라이언트) 에서만 사원별 상태 계산 : API 직접 호출·배치 처리 경로에서 정합성 보장 불가

**해결 방법**

- `PayrollEmpStatus` 사원별 row 를 기준 데이터 SoT(Source of Truth) 로 두고 run 상태는 레거시 일괄지급 호환용 참고 데이터로만 사용
- 상태 전이 로직(`confirm()` / `approve()` / `pay()` 등) 을 `PayrollEmpStatus` 엔티티 메서드 안에 캡슐화 → 서비스 / 배치 / 이벤트 리스너 어디서 호출하든 동일한 안전성 보장
- 각 메서드에 두 가지 안전장치 동시 적용:
  - **가드** — 잘못된 전이 시도 시 IllegalStateException 발생 (예: `CALCULATING → PAID` 한 단계 건너뛰기 차단)
  - **멱등** — 같은 상태로 재요청 시 조용히 통과 (예: 이미 `APPROVED` 인 사원에게 또 `approve()` 호출 → 아무 일 안 일어남)

</details>

<details>
<summary>2. 자동 산정 트랜잭션 분리 - AFTER_COMMIT + 실패 격리</summary>

**문제 사항**

- 급여 PAID 처리 시 DC 가입 사원의 퇴직연금 적립 row 가 자동 INSERT 되어야 함
- 본 PAID 트랜잭션과 같은 트랜잭션에 묶이면, 적립 INSERT 한 줄 실패가 회사 전체 급여 지급을 롤백시킬 수 있음

**원인 분석**

- 본 도메인(급여 지급) 과 부가 도메인(자동 산정) 결합도 ↑ → 한쪽 실패가 다른 쪽으로 전파
- 자동 산정은 신뢰성 요구가 본 도메인보다 약함에도 동일 트랜잭션이 되면 양쪽 모두 강한 신뢰성을 요구받게 됨

**시도 방법**

- 동일 트랜잭션 처리 : 결합도 ↑, 부가 실패가 본 트랜잭션 롤백
- 비동기 메시지 큐(Kafka) 도입 : 인프라 부담 큼, 부가 도메인 1개만으로는 과한 선택

**해결 방법**

- 급여 PAID 처리 시 `PayrollPaidEvent` 이벤트 발행 → 별도 리스너가 `@TransactionalEventListener(AFTER_COMMIT)` 으로 수신
- 본 PAID 트랜잭션이 **DB 커밋 완료된 후**에만 적립 로직 실행 → 적립 실패가 본 트랜잭션 롤백을 트리거하지 않음 (급여 지급과 적립 자동화의 결합도 분리)
- 리스너 내부 try-catch 로 예외를 잡아 ERROR 로깅만 하고 밖으로 던지지 않음 → 적립 실패해도 PAID 는 이미 커밋된 상태라 운영자가 [월별 일괄 적립] 버튼으로 나중에 수동 보정 가능(실패 격리)
- 자동 산정이 중복 호출되어도 같은 row 가 두 번 생성되지 않도록 `existsByPayrollRun...` 사전 체크로 중복 방지 (같은 요청이 두 번 와도 결과 동일 = 멱등성)

</details>

<details>
<summary>3. 리스너 실행 순서 - @Order 로 도메인 의존성 직렬화</summary>

**문제 사항**

- 사원 퇴직 완료 시 (1) 연차수당 자동 산정 + (2) 퇴직금 자동 산정 두 가지가 일어나야 함
- 퇴직금 평균임금 계산식이 직전 3개월 임금 + 연차수당 보전분을 포함 → (2) 가 (1) 보다 먼저 실행되면 연차수당 누락된 평균임금으로 계산되어 퇴직금이 적게 산정

**원인 분석**

- Spring 이벤트 시스템에서 같은 이벤트를 듣는 리스너들의 실행 순서는 기본적으로 무보장 — "이벤트 기반 분리" 가 항상 비동기/순서 무관을 의미하지 않음
- 도메인 의존성(평균임금 ← 연차수당) 이 있는 두 자동화는 순서 보장이 필요한데, 단순 이벤트 발행만으로는 부족

**시도 방법**

- 두 자동화를 하나의 서비스 메서드에 묶기 : 결합도 ↑, 도메인 분리 의도 훼손
- 비동기 메시지 큐로 순차 처리 : 인프라 부담, 단일 노드 환경에서는 과한 선택

**해결 방법**

- 두 리스너 모두 `EmployeeRetiredEvent` 를 `AFTER_COMMIT` 으로 받되 `@Order` 로 실행 순서 강제
- `LeaveAllowanceEventListener(Order=1)` → `SeveranceEventListener(Order=2)` 직렬 실행
- 자동 산정 순서를 운영자에게 의존하지 않고 시스템 자체에서 보장

</details>

<details>
<summary>4. 이체파일 다운로드 - 누락자 격리 + 응답 헤더 분리</summary>

**문제 사항**

- 대량이체 엑셀 다운로드는 한 사원의 데이터 누락(계좌 미등록 등) 이 회사 전체 응답 실패로 번질 위험
- B2B 운영에서 한 명 누락이 회사 전체 마비로 이어지면 안 됨

**원인 분석**

- 사원 전체를 한 번에 처리하는 일괄 다운로드 특성상, 한 건의 데이터 오류가 응답 본문 생성을 막음
- 응답 본문(엑셀 파일) 에 누락자 정보를 같이 담을 수 없음 (본문은 깨끗한 엑셀이어야 함)

**시도 방법**

- 사원 등록 시점에 계좌 필수 검증 : 데이터 정책 변경 부담 + 기존 사원 마이그레이션 필요
- 누락자 발견 시 다운로드 자체 중단 + 사전 검증 : 운영자가 한 명 보정할 때마다 다시 시도

**해결 방법**

- 백엔드가 계좌·은행코드 누락자를 다운로드 대상에서 선별 제외
- 누락자 명단은 `X-Skipped-Employees` 응답 헤더(URL-encoded JSON) 로 회신
- 결재 승인된 사원이 0명이면 다운로드 자체를 막아 빈 파일 다운로드 방지
- "정상 데이터 본문 + 누락자 메타 헤더" 분리 패턴으로 운영 친화적 응답 구성

</details>

<details>
<summary>5. 은행별 이체 엑셀 - 양식 파일 입수 불가 환경 대응</summary>

**문제 사항**

- 대량이체 운영자가 인터넷뱅킹 화면에 사원 정보를 입력할 때, 한 줄씩 수동 입력하는 비효율을 줄이려면 **셀 영역 복사 → 인터넷뱅킹에 붙여넣기** 시나리오를 지원하는 엑셀 보조 자료 필요
- 그러나 실제 은행 양식 파일은 법인 미보유로 인터넷뱅킹 접근 자체가 불가 → 외부 양식 파일 의존 불가

**원인 분석**

- 외부 자원 접근이 막힌 환경에서는 100% 정확 자동화가 현실적으로 불가능
- 양식 파일 의존 시 추후 양식 변경에도 외부 자원에 종속

**시도 방법**

- 실제 양식 파일 입수 시까지 기능 보류 : 운영 시점에 보조 자료 부재 → 한 줄씩 수동 입력 강제, 실수 위험 ↑
- 단순 CSV 생성 : 시각적 가독성 ↓, 어느 은행 양식인지 한눈에 안 보임, 셀 복붙 시 정렬 깨짐

**해결 방법**

- Apache POI 로 워크북 자체 빌드 → 외부 양식 파일 의존 0
- **은행별 컬럼명·순서 차별화 — 운영자가 셀 영역을 복사해서 인터넷뱅킹 입력 화면에 붙여넣을 때 자동 매핑되도록** 추정 양식에 맞춰 컬럼 구성. 양식 정확 매칭되는 은행은 복붙으로 일괄 입력 가능, 부분 매칭만 되는 은행도 컬럼 재정렬 최소화로 입력 시간 단축
- 공통 골격(헤더 스타일 / 천단위 콤마 / 합계 행 / 시트 메타) 은 모든 은행 동일 → 운영자가 어느 은행이든 같은 사용 방식
- 양식 정확 매칭 안 돼도 운영자가 보고 판단해 한 줄씩 수동 입력 가능한 실패 안전(fail-safe) 설계 — 즉 복붙 → 부분 복붙 → 수동 입력 3단계 fallback

</details>

<details>
<summary>6. 법정공휴일 - 자체 부트스트랩 가능한 멱등 시드</summary>

**문제 사항**

- 법정공휴일은 휴일근무수당 산정 / 연차 영업일 계산 / 주 소정근무시간 등 여러 도메인이 의존하는 운영 데이터
- 매년 1회 정부 발표 갱신 빈도라 외부 API 도입은 비용 대비 이득이 작고, Flyway/Liquibase 도입은 변경 표면적이 크며 배포 임박 시점과 미스매치

**원인 분석**

- 외부 데이터 의존 도메인은 자체 부트스트랩 경로 부재 시 운영 시점에 사일런트 누락 위험
- 매 부팅·매 배포마다 안전하게 재실행 가능한 적재 전략 필요

**시도 방법**

- Flyway/Liquibase 도입 : 변경 표면적 큼, 배포 임박 시점에 도입 위험
- 외부 공식 API (공공데이터포털 특일정보) 호출 : 매년 1회 정부 발표 갱신 빈도 대비 키 발급/응답 매핑/유지 부담 큼
- 매년 수동 INSERT 스크립트 실행 : 운영자 누락 시 사일런트 버그

**해결 방법**

- Spring `spring.sql.init` 으로 SQL 시드 파일 자동 적재 → 더미 리셋 워크플로와 SQL 파일 재사용 가능
- 멱등성 : `UNIQUE` 제약 + `ON DUPLICATE KEY UPDATE` → 매 부팅·매 배포 안전 재실행
- 환경 분리 : local OFF / prod ON (local 은 더미 리셋과 충돌 위험)
- `continue-on-error: true` 로 시드 실패가 부팅 자체를 막지 않도록

</details>

</details>

<details>
<summary><strong>통합검색</strong></summary>
<details>
<summary>1. DB와 ES의 데이터 정합성 - Debezium</summary>

**문제 사항**

- 통합검색은 Elasticsearch 인덱스를 기반으로 동작
- 데이터 변경 직후 검색 결과에 반영되지 않거나, 삭제된 데이터가 계속 검색되는 현상 발생
- 사용자 입장에서 “방금 수정했는데 검색 안됨”, “삭제했는데 검색됨” 같은 UX 혼란 발생

**원인 분석**

- DB 변경 후 Kafka 이벤트를 애플리케이션에서 직접 발행 → 이벤트 누락 가능성 존재
- 트랜잭션 커밋과 이벤트 발행이 분리되어 있어 실패 시 정합성 깨짐
- 도메인별로 이벤트 발행 로직이 분산 → 일부 기능에서 이벤트 미구현/누락 발생

**시도 방법**

- 서비스 레이어에서 Kafka Producer 직접 호출  
  → 구현은 단순하지만 비즈니스 로직과 이벤트 발행 강결합
- `@TransactionalEventListener` 기반 후행 이벤트 발행  
  → 트랜잭션 이후 보장되지만 여전히 애플리케이션 책임, 누락 가능성 존재

**해결 방법**

- Debezium CDC 도입 → DB binlog 기반 변경 데이터 캡처 후 Kafka 자동 전송
- 애플리케이션에서 이벤트 발행 로직 완전 제거 → DB 변경 = 이벤트 발행 구조로 전환
- Elasticsearch 인덱싱은 Kafka Consumer 로 분리하여 비동기 처리

**결과**

- 이벤트 누락 원천 차단 → DB와 검색 인덱스 간 정합성 확보
- 애플리케이션 코드에서 이벤트 책임 제거 → 개발 복잡도 감소
- 신규 도메인 추가 시 별도 이벤트 구현 불필요 → 확장성 향상
- 검색 반영 지연 최소화 → 사용자 UX 개선

</details>
<details>
<summary>2. 검색 정확도 - Nori 형태소 분석 한계</summary>

**문제 사항**

- 통합검색에서 특정 키워드 검색 시 기대 결과가 누락되거나 검색되지 않는 문제 발생
- 예: "결재", "결재문서", "전자결재" 등 유사 키워드 간 검색 결과 불일치
- 사용자 입장에서 "분명 존재하는 데이터인데 검색이 안됨" → 검색 신뢰도 저하

**원인 분석**

- Elasticsearch 기본 Nori 형태소 분석기는 단어를 형태소 단위로 분해하여 색인
- 복합어/부분 문자열 검색(예: "결재문")에 대해 매칭 실패 발생
- 특히 사내 도메인 용어(커스텀 용어, 약어 등)는 사전에 없는 경우가 많아 분석 품질 저하

**시도 방법**

- 사용자 사전 추가 (Nori user dictionary)
  → 특정 단어는 보완 가능하지만 모든 케이스 대응 불가
- synonym 필터 적용
  → 유사어 확장은 가능하지만 부분 검색 문제 해결 불가

**해결 방법**

- ngram 토크나이저 도입 (edge_ngram / ngram)
  → 문자열을 부분 단위로 분해하여 색인
- Nori + ngram 멀티 필드 구성
  → 정확 검색(형태소) + 부분 검색(ngram) 병행 처리
- 검색 시 multi_match 전략으로 두 필드 동시에 조회

**결과**

- 부분 검색 및 유사 키워드 검색 성공률 향상 → 검색 정확도 개선
- 사용자 검색 의도 대응력 증가 → UX 개선
- 형태소 분석의 한계를 보완하면서도 기존 정확 검색 성능 유지

</details>

<details>
<summary>3. 검색 성능 - 다중 인덱스 호출 비용 증가</summary>

**문제 사항**

- 통합검색 시 도메인별로 개별 ES 쿼리 호출 → API 응답 지연
- 네트워크 왕복 증가로 전체 응답 시간 증가

**원인 분석**

- 인덱스를 분리했지만 통합 검색 시 각각 조회
- 결과 병합을 애플리케이션에서 수행

**시도 방법**

- 도메인별 API 호출 → 구조 단순하지만 성능 비효율

**해결 방법**

- multi-index 검색 (index1,index2 형태)
- 또는 alias 기반 통합 인덱스 조회

**결과**

- ES 호출 횟수 감소 → 응답 속도 개선
- 통합검색 구조 단순화

</details>

</details>

<details>
<summary><strong>AI</strong></summary>
<details>
<summary>1. 민감 정보 보호 - 외부 LLM 데이터 유출 위험</summary>

**문제 사항**

- AI 기능(요약, 추천, 질의응답)을 위해 외부 LLM API(Claude Haiku) 호출 필요
- 사내 데이터(인사정보, 결재 내용 등)에 개인정보 및 민감 정보 포함
- 외부 API 호출 시 데이터 유출 및 보안 정책 위반 가능성 존재

**원인 분석**

- 외부 LLM은 입력 데이터를 모델 처리 과정에 사용 → 완전한 데이터 통제 불가
- 모든 요청을 단일 LLM으로 처리할 경우 민감 정보 필터링 없이 전송될 위험 존재
- AI 기능 확장 시 호출 지점 증가 → 보안 누락 가능성 증가

**시도 방법**

- 단일 LLM 사용 + 입력 데이터 마스킹  
  → 일부 보호 가능하지만 마스킹 누락 시 위험 존재
- 규칙 기반 필터링  
  → 케이스별 대응 필요, 유지보수 어려움

**해결 방법**

- LLM 이중 구조 설계 (External LLM + sLLM)
    - 외부 LLM : Claude Haiku → 비민감 데이터 기반 요약/생성 처리
    - 내부 sLLM : EXAONE → 민감 데이터 처리 전용 (온프레미스/로컬)
- 요청 분기 로직 적용
    - 민감 데이터 포함 여부 판단 → 내부 모델로 라우팅
    - 일반 데이터 → 외부 LLM 사용
- 데이터 최소화 원칙 적용 (필요 정보만 전달)

**결과**

- 민감 정보 외부 전송 원천 차단 → 보안 정책 준수
- AI 기능 확장 시에도 안전한 구조 유지 → 확장성 확보
- 모델 역할 분리로 비용(외부 API) + 성능(로컬 처리) 균형 확보

</details>

<details>
<summary>2. 자연어 검색 정확도 - Hybrid Search (BM25 + kNN + RRF)</summary>

**문제 사항**

- 사용자가 자연어 형태로 질의 시 기대 결과가 검색되지 않거나 부정확한 결과 노출
- 예: "이번 주 결재 문서 보여줘", "최근 내가 승인한 문서" 등 의도 기반 질의 처리 한계
- 기존 키워드 기반 검색(BM25)은 정확 일치에는 강하지만 의미 기반 검색에 취약

**원인 분석**

- BM25는 단어 매칭 기반 → 표현이 조금만 달라져도 검색 실패
- 자연어 질의는 문장 단위 의미(semantic)를 포함 → 키워드 매칭만으로 의도 파악 불가
- 반대로 벡터 검색(kNN)은 의미는 잘 잡지만 정확한 키워드 매칭/정렬이 약함

**시도 방법**

- BM25 단독 검색  
  → 정확도는 높지만 자연어 질의 대응 한계
- 벡터 검색(kNN) 단독 적용  
  → 의미 검색 가능하지만 노이즈 결과 증가, 정렬 품질 저하

**해결 방법**

- Elasticsearch 기반 Hybrid Search 구조 도입
    - BM25 (키워드 기반) + kNN (벡터 기반) 병행 검색
- RRF(Reciprocal Rank Fusion) 적용
    - 두 검색 결과를 순위 기반으로 결합하여 최종 결과 생성
- 기존 키워드 검색 인프라 재활용 + 벡터 필드 추가 구성

**결과**

- 자연어 질의 대응력 향상 → 의미 기반 검색 가능
- 키워드 정확도 + 의미 검색 결합 → 검색 품질 향상
- 기존 검색 구조 재활용으로 도입 비용 최소화
- 사용자 의도에 맞는 결과 노출 → UX 개선

</details>
<details>
<summary>3. LLM 비용 최적화 - Prompt Caching으로 토큰 80% 절감</summary>

**문제 사항**

- AI 기능(요약/검색/추천 등) 호출 시 매 요청마다 동일한 시스템 프롬프트와 컨텍스트를 반복 전송
- 특히 대화형 기능에서 이전 대화 히스토리가 누적되며 토큰 사용량 급증
- 요청 수 증가 시 LLM API 비용이 선형적으로 증가 → 운영 비용 부담

**원인 분석**

- LLM은 상태를 서버에 유지하지 않는 stateless 구조 → 매 요청마다 전체 context 재전송 필요
- 동일한 system prompt / 공통 컨텍스트도 매번 토큰으로 과금됨
- 캐싱 없이 모든 요청을 full prompt로 처리 → 비효율적인 토큰 사용 구조

**시도 방법**

- 프롬프트 길이 축소 : 일부 개선되지만 기능 제한 발생
- 대화 히스토리 truncation : 토큰 감소는 가능하나 응답 품질 저하
- 세션 기반 서버 저장 : 구현 복잡도 증가, LLM 호출 비용 자체는 줄지 않음

**해결 방법**

- Anthropic Prompt Caching 기능 도입
    - system prompt 및 고정 컨텍스트를 cache key 기반으로 재사용
    - 동일 prefix 요청 시 캐시된 토큰 재활용
- 동적 사용자 입력만 delta 형태로 전송 → 불필요한 토큰 제거
- 캐시 적중 시 기존 prompt를 다시 전송하지 않도록 구조 개선

**결과**

- 캐시 적중 시 토큰 사용량 최대 80% 절감 → LLM 호출 비용 대폭 감소
- 응답 속도 개선 (토큰 전송량 감소)
- 대화형 기능에서도 긴 context 유지 가능 → 품질 유지 + 비용 최적화 동시 달성
- 트래픽 증가에도 비용 증가율 완화 → 운영 안정성 확보

</details>

</details>

<details>
<summary><font size="5"><strong>사원 관리</strong></font></summary>

<details>
<summary>1. 사원 조회 N+1 - 연관 정보 개별 조회</summary>

**문제 사항**

- 인력 현황 페이지 사원 100명 조회 시 사원마다 부서·직급·조직 추가 조회 → 쿼리 수백 회 발생
- 사원 수 비례로 응답 시간 증가
- 페이지 진입 지연

**원인 분석**

- 사원 엔티티의 부서·직급·조직 연관 관계 lazy 로딩 → 사원 한 명당 개별 SELECT
- 페이지네이션 적용해도 페이지 단위로 동일 N+1 패턴 반복
- 첫 화면에 사원 수 × 연관 정보 동시 요구

**시도 방법**

- 단일 거대 조인 쿼리
    - 사원 × 부서 × 직급 × 조직 카티전 곱 → 결과 행 수 증가, 페이징 처리 어려움
- 페이지 사이즈 축소
    - 페이지 한 건 응답 단축, 전체 누적 비용 동일
- 사원별 캐싱
    - 사원 정보 변경 시 무효화 정책 복잡

**해결 방법**

- `EmployeeRepository` 조회 메서드에 `LEFT JOIN FETCH` / `JOIN FETCH` 적용 → 사원 + 부서 + 직급 + 직책 한 쿼리 일괄 로딩
- 빈번 조회마다 fetch join 메서드 분리 (`findActiveEmployeesWithDeptAndGrade`, `findEvalTargetsByCompany`, `findTitleHoldersByDeptId`)
- 카운트 쿼리 별도 분리 → 페이징 호환 유지

**결과**

- DB 왕복 횟수 일정 → 응답 시간 안정화
- 사원 수 확장에도 페이지 성능 유지

</details>

<details>
<summary>2. 사원 폼 운영 - 회사별 자유도와 무결성 충돌</summary>

**문제 사항**

- 회사 생성 시 기본 값 X
- 사원 정보는 급여·근태·평가 등 다른 모듈이 의존하는 표준 키 보유 → 핵심 필드 삭제 시 모듈 간 연동 파괴
- 회사가 폼 정의를 변경할 때마다 기존 사원 데이터를 마이그레이션해야 하는 부담 발생

**원인 분석**

- 사원 정보에는 다른 모듈이 의존하는 필수 항목과 회사 자율 항목이 혼재
- 둘을 같은 방식으로 다루면 필수 항목도 회사가 삭제 가능
- 폼 정의와 사원 데이터를 같은 출처로 묶으면 폼 변경마다 데이터 이전 필요

**시도 방법**

- 모든 항목을 동등하게 자유 추가·삭제
    - 사원 핵심 정보 보호 장치 부재, 회사 실수 한 번으로 인사 무결성 파괴 가능
- 회사가 폼 변경 시 기존 사원 데이터 일괄 마이그레이션
    - 회사 수·사원 수에 비례한 마이그레이션 비용, 진행 중 일관성 보장 어려움
- 표준 양식 강제 (커스텀 미지원)
    - SaaS 커스터마이즈 컨셉과 모순

**해결 방법**

- 양식을 두 층위로 분리
    - **기본 항목**: 정적 컬럼 유지 + 회사가 사용 여부(on/off) 만 선택해 화면 노출 제어 → 컬럼은 보존, off 후 다시 on 시 데이터 그대로 유지
    - **커스텀 항목**: `Employee.customFields` 를 `@JdbcTypeCode(SqlTypes.JSON)` `Map<String, String>` 으로 정의 → 회사 담당자가 자유 추가·삭제
- `isFixed` / `locked` 플래그로 기본 필드 보호 → 커스텀 작업이 인사 핵심 정보에 영향 없음
- 사원 수정 화면은 폼 정의와 값을 두 출처에서 분리 조회
    - 폼 구조: 회사 현재 `FormFieldSetup` 실시간 조회 → 폼 변경 즉시 반영
    - 값: 사원에 저장된 기본 컬럼 + customFields JSON 에서 조회 → 폼 변경이 사원 데이터에 영향 없음
- `FormFieldSetup` 에서 회사별 + 폼타입별 필드 정의 관리 (`company_id + form_type + field_key` UNIQUE)

**결과**

- 사원 핵심 정보 보호 → 회사 담당자 임의 변경으로 인한 인사 무결성 파괴 차단
- 기본 항목 on/off 토글로 데이터 손실 없이 화면 제어 → 회사 정책 변경 부담 감소
- 폼 변경 시 기존 사원 데이터 마이그레이션 불필요
- 폼·값 분리로 폼 변경이 사원 데이터 무결성에 영향 없음 → 운영 자유도 증가

</details>

</details>

<details>
<summary><font size="5"><strong>성과 평가</strong></font></summary>

<details>
<summary>1. 등급 보정 동시성 - 강제분포 비율 불일치</summary>

**문제 사항**

- 여러 HR 담당자가 같은 시즌 동시 보정 시 마지막 저장만 반영 → 강제분포(S 10% / A 20% / B 40% / C 20% / D 10%) 비율 어긋남
- 일부 보정 이력 유실 → 평가 결과 정합성 어긋남

**원인 분석**

- 보정 흐름이 조회 → 비율 판단 → 일괄 저장 → 처리 사이 다른 트랜잭션 개입 가능
- JPA 기본 Last-Write-Wins → 후행 트랜잭션이 선행 변경을 모르고 저장
- 보정은 시즌 단위 사원 수십~수백 명 행 일괄 갱신 → 충돌 영향 범위 넓음

**시도 방법**

- DB 비관적 락(`@Lock(PESSIMISTIC_WRITE)`)
    - `SELECT ... FOR UPDATE` 행 선점, 충돌 사전 방지
    - 보정 단위 다수 행 갱신 → 락 점유 시간 길어 다른 HR 작업 대기, 처리량 감소
- Redis 분산 락
    - 시즌 단위 lock key 획득, 동시 진입 방지
    - 보정 중 자리 비울 경우 무기한 대기, 락 만료/점유 정책 복잡
- 트랜잭션 격리 수준 상향(`SERIALIZABLE`)
    - 일반 조회까지 영향, 한 케이스 위해 전체 격리 상향은 과도

**해결 방법**

- `EvalGrade` 엔티티에 `@Version` 필드 추가 → JPA 의 UPDATE 시 `WHERE id = ? AND version = ?` 자동 부여
- 후행 트랜잭션 0 row affected → `OptimisticLockingFailureException` 발생
- 전역 `GlobalExceptionHandler` 에서 HTTP 409 + `OPTIMISTIC_LOCK_CONFLICT` 코드 변환

**결과**

- 선행 보정 보존, 후행 HR 은 최신 데이터 기준 재시도
- 강제분포 비율 정합성 유지
- 락 미사용 → 처리량 유지

</details>

<details>
<summary>2. 시즌 규칙 변경 - 무결성 훼손</summary>

**문제 사항**

- 인사팀이 평가 시즌 진행 중 가중치·강제분포 비율 같은 규칙 수정 시 이미 산정된 점수와 진행 중 평가 결과 변동
- 동일 시즌 안 사원마다 다른 기준으로 등급 계산 → 평가 공정성 어긋남

**원인 분석**

- 계산 엔진이 회사 규칙(`EvaluationRules`) 매 산정마다 실시간 조회 → 규칙 변경 순간 과거·현재 계산 기준 혼재
- 규칙 변경 시점 분리 장치 없음

**시도 방법**

- 규칙 변경 시 진행 중 시즌 전체 재계산
    - 인사팀 의도와 무관하게 진행 중 시즌 영향, 재계산 비용 큼
- 규칙 버전 별도 관리 + 시즌마다 버전 ID 지정
    - 시즌별 참조 버전 명시 가능
    - 시즌 OPEN 후 규칙 또 변경 시 참조 버전 모호, 인사팀이 매 시즌 버전 선택 부담
- 시즌 OPEN 후 규칙 변경 잠금
    - 다음 시즌 준비 위한 사전 수정 불가, 인사팀 업무 흐름 제약

**해결 방법**

- `Season` 엔티티에 `form_snapshot` JSON 컬럼 추가 → 시즌 OPEN 시점 회사 규칙 전체 JSON 직렬화 후 박제
- `SeasonService.openSeason()` 에서 `rulesService.buildMergedSnapshotJson(rules)` 로 규칙 병합 후 `season.freezeSnapshot()` 호출
- 계산 엔진은 공식 하드코딩 없이 해당 스냅샷만 참조

**결과**

- 시즌 도중 규칙 변경에도 그 시즌 계산은 OPEN 시점 규칙 고정
- 진행 중 시즌과 다음 시즌 규칙 수정 작업 분리
- 시즌 간 일관성 확보

</details>

<details>
<summary>3. 평가자 퇴직 - 시즌 매핑 정합성 훼손</summary>

**문제 사항**

- 평가 시즌 진행 중 평가자 퇴직 시 `EvalGrade` 행의 평가자 ID 무효 상태로 유지
- 매핑 끊긴 상태로 시즌 진행 시 해당 피평가자 평가 누락
- 시즌 생성 시 매핑 일부만 설정된 상태로 운영 시작 시 평가 단계에서 누락 발견 → 시즌 진행 지연

**원인 분석**

- 평가 시즌과 사원 도메인 분리 → 사원 퇴직이 평가 매핑에 자동 반영 안 됨
- 매핑 검증 수동 → 사원 수 많을수록 누락 가능
- 퇴직 트랜잭션 내부 평가 매핑 동기 정리 시 후속 작업 실패가 퇴직 자체 실패로 전이

**시도 방법**

- 시즌 운영자가 시즌 생성 전 매핑 상태 수동 점검
    - 검증 누락 가능, 활성 사원 수 많을수록 운영 부담 증가
- 평가자 퇴직 시 퇴직 트랜잭션 안에서 평가 매핑 동기 정리
    - 평가 정리 실패 시 퇴직 트랜잭션 자체 막힘 → 인사 업무 가용성 저하
- 시즌 OPEN 시점 자동 재배정
    - 재배정 대상 시스템 판단 근거 부족, 인사 권한 영역 코드 대체 부작용

**해결 방법**

- 시즌 생성 단계 사전 검증
    - `SeasonService.createSeason()` 에서 활성 사원 중 매핑 미결정자(`excluded=false && evaluator=null`) 검증
    - 미지정자 발견 시 시즌 생성 차단
- 시즌 OPEN 후 평가자 퇴직 시 이벤트 기반 자동 보정
    - 퇴직 트랜잭션 커밋 후 `EmployeeRetiredEvent` 발행 → `EvaluatorRetirementListener` 가 AFTER_COMMIT 수신 → 퇴직 트랜잭션과 분리
    - `EvaluatorRetirementHandler` 가 글로벌 매핑(`EmpEvaluatorGlobal`)에서 해당 평가자 row 삭제 + 진행 중 시즌(OPEN)의 `EvalGrade` 중 평가자가 퇴직자인 행 선별
    - `EvalGrade.clearEvaluator()` 호출 → 평가자 필드 null 화
- HR_ADMIN 알림으로 수동 재배정 유도

**결과**

- 매핑이 미완성인 채 시즌이 만들어지지 않음 → 시즌 운영 시작 시점에 평가 누락 위험 제거
- 평가자 퇴직 직후 해당 사원의 평가가 미지정 상태로 복구 → 진행 중 시즌의 평가 누락 방지
- 평가 정리 실패가 퇴직 처리에 영향 없음, 퇴직 롤백 시 평가 정리도 시도되지 않음 → 두 작업 간 일관성 유지

</details>

</details>

---

<br>

## 9. 회고

<details>
<summary><b>이수림</b></summary>
<br>

> 사원관리와 성과 모듈을 담당했습니다. 커스텀과 성과 평가 도메인은 프로젝트 시작 전까지는 생소한 영역이었지만, 평가 시즌 운영 방식부터 KPI 지표 설계, 평가자 매핑 구조까지 직접 학습하고 분석하며 완성도 있게 구현해냈습니다. 낯선 도메인을 끝까지 책임지고 해결해나가는 과정에서 기술적인 성장뿐 아니라 새로운 영역도 빠르게 이해하고 주도적으로 추진할 수 있다는 자신감을 얻을 수 있었습니다. 또한 프로젝트를 함께한 팀원들의 적극적인 협업과 소통 덕에 좋은 방향으로 고민하며 완성도를 높여갈 수 있었습니다. 이에 함께 성장하는 경험의 중요성도 같이 느낄 수 있었습니다.

</details>

---

<details>
<summary><b>정명진</b></summary>
<br>

> 프로젝트를 진행하며 전체적인 설계를 주도하며 실무와 비슷한 B2B 환경을 만들기 위해서는 고려해야 할 부분이 많다고 느꼈으며 이에 따른 흥미를 느낄 수 있었습니다. 팀장으로서의 역할이 무엇인지 배웠고 이 과정을 함께해준 팀원들 덕분에 목표했던 결과물을 완성도 있게 마무리할 수 있었습니다. 전자결재, 근태, 휴가 모듈을 담당하며 다양한 문제를 직접 해결해 볼 수 있었습니다. 기술을 단순히 아는 것과 상황에 맞게 판단해서 쓰는 것이 다르다는 것을 체감했고, 팀장으로서는 팀원 각자가 본인의 도메인에 주도권을 가질 수 있도록 환경을 만드는 것에 집중했습니다. 함께해서 더 많이 성장할 수 있었고, 이번 경험을 발판 삼아 더 큰 규모의 B2B 시스템도 자신 있게 설계할 수 있을 것 같습니다.

</details>

---

<details>
<summary><b>홍진희</b></summary>
<br>

> 급여와 캘린더 모듈을 담당하며 B2B 환경을 처음 경험해보았고, 실무에서 백엔드 코드가 어떻게 활용되는지를 직접 구현해보며 배울 수 있었습니다. 특히 인사팀이 실제 업무에서 사용하는 기능을 만들어보면서, 단순한 CRUD가 아니라 회사 정책과 법규·예외 케이스가 촘촘하게 얽혀 있는 도메인을 다루는 과정 자체가 재미있었습니다. 그러면서 꼼꼼한 ERD와 모델 설계가 개발 그 자체에 반드시 필요한 토대라는 것을 깨달았습니다. 또한, 외부 기술을 덧붙이기보다 상태 머신·이벤트 분리 같은 설계 원칙으로 코드 자체의 완성도를 높이는 방향을 택하면서, 도메인을 깊이 이해할수록 설계가 좋아진다는 것도 함께 배웠습니다. 늘 함께 고민하고 부딪혀준 팀원분들 덕분에 무사히 마무리할 수 있어 감사한 프로젝트였습니다.

</details>

---

<details>
<summary><b>황주완</b></summary>
<br>

> 대형 B2B 프로젝트를 처음 개발해보면서 처음에는 B2B라는 것의 개념도 생소했지만 어느새 SaaS 설계에 대해 이해하고 고객사 중심으로 생각할 수 있게 되었습니다. 또한 WebSocket/Stomp, Kafka, Debezium, Elastic Search, AI등 다양한 기술을 사용하면서 개발자로써 큰 폭으로 성장 할 수 있었던 프로젝트였다고 생각합니다.




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
