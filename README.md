<p align="center">
  <img src="docs/drawrace-logo.png" alt="DrawRace 로고" width="360" />
</p>

<h1 align="center">Draw Race 2026 · Backend</h1>

<p align="center">
  <strong>프로그래머스 AI 인턴 프로그램 6기 · Team05</strong><br />
  실시간 그림 대전 · AI 채점 · 방·라운드·채팅 API 및 WebSocket 서버
</p>

<p align="center">
  <a href="https://drawrace.site/"><img src="https://img.shields.io/badge/website-drawrace.site-6366f1?style=for-the-badge" alt="Live site" /></a>
  &nbsp;
  <a href="https://github.com/Programmers-Intern-Program/INT1-Project-Team05-FE"><img src="https://img.shields.io/badge/frontend-Next.js-000000?style=for-the-badge&logo=next.js&logoColor=white" alt="Frontend" /></a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-f89820?style=flat-square&logo=openjdk&logoColor=white" alt="Java" />
  <img src="https://img.shields.io/badge/Spring_Boot-3.3-6DB33F?style=flat-square&logo=spring&logoColor=white" alt="Spring Boot" />
  <img src="https://img.shields.io/badge/MySQL-4479A1?style=flat-square&logo=mysql&logoColor=white" alt="MySQL" />
  <img src="https://img.shields.io/badge/Redis-DC382D?style=flat-square&logo=redis&logoColor=white" alt="Redis" />
  <img src="https://img.shields.io/badge/AWS_S3-569A31?style=flat-square&logo=amazons3&logoColor=white" alt="S3" />
</p>

---

## 목차

- [소개](#소개)
- [주요 기능](#주요-기능)
- [기술 스택](#기술-스택)
- [요구 사항](#요구-사항)
- [시작하기](#시작하기)
- [환경 변수](#환경-변수)
- [실행 프로필](#실행-프로필)
- [Gradle 작업](#gradle-작업)
- [API 문서](#api-문서)
- [프로젝트 구조](#프로젝트-구조)
- [CI/CD](#cicd)
- [브랜드 에셋](#브랜드-에셋)
- [관련 링크](#관련-링크)

---

## 소개

**Draw Race** 백엔드는 방·참가자·게임 라운드·그림 제출·AI 판별, 실시간 채팅·검열, 친구·초대, 전적 등을 담당하는 **Spring Boot** 애플리케이션입니다.  
REST API와 **WebSocket(STOMP)** 로 프론트엔드와 연동하며, 운영 환경에서는 **MySQL·Redis·S3** 를 사용합니다.

---

## 주요 기능

| 영역 | 내용 |
| --- | --- |
| **인증·회원** | JWT, 회원가입·로그인, 게스트 로그인, 비밀번호·프로필(정책에 따름) |
| **방·게임** | 방 CRUD, 참가자, 방장, 게임 시작·라운드 진행·타임아웃·결승 |
| **AI 연동** | 제시어 생성, 그림 판별(재시도·동시 호출 제한), 채팅 검열용 LLM 호출 |
| **실시간** | STOMP 채널(방 이벤트, 채팅, 랭킹, 드로잉 중계 등) |
| **채팅** | 키워드·임베딩 유사도 1차, AI 2차 검열, 스팸·스트라이크 |
| **스토리지** | 로컬 디렉터리 또는 **AWS S3** 에 프로필 이미지 등 저장 |
| **기타** | 친구·방 초대, 닉네임 풀·AI 유저 시드 등 |

---

## 기술 스택

| 영역 | 사용 기술 |
| --- | --- |
| 언어·런타임 | **Java 21** |
| 프레임워크 | **Spring Boot 3.3** (Web, Data JPA, Security, WebSocket, Validation) |
| 데이터베이스 | **MySQL** (운영), **H2** (로컬 기본) |
| 캐시·세션 등 | **Redis** |
| 인증 | **JWT** (jjwt) |
| API 문서 | **SpringDoc OpenAPI** (Swagger UI) |
| 클라우드 | **Spring Cloud AWS** — S3 |
| ML·텍스트 | **DJL** + **PyTorch** 엔진, Hugging Face 토크나이저(채팅 임베딩 등) |
| 코드 스타일 | **Spotless** (Palantir Java Format) |

---

## 요구 사항

- **JDK 21** (프로젝트 toolchain)
- **Redis** (로컬 실행 시 기본 `localhost:6379`)
- 운영 프로필 사용 시: **MySQL**, **AWS 자격 증명(S3)** 및 아래 환경 변수

---

## 시작하기

```bash
git clone https://github.com/Programmers-Intern-Program/INT1-Project-Team05.git
cd INT1-Project-Team05

# Windows
gradlew.bat bootRun

# macOS / Linux
chmod +x gradlew
./gradlew bootRun
```

기본 포트는 **8080** 입니다.  
로컬에서는 **H2 인메모리 DB**와 **H2 콘솔**이 켜져 있어 별도 MySQL 없이도 기동할 수 있습니다(설정은 `application.yml` 참고).

프론트엔드와 함께 쓰려면 [INT1-Project-Team05-FE](https://github.com/Programmers-Intern-Program/INT1-Project-Team05-FE) 의 `NEXT_PUBLIC_API_BASE_URL` 을 `http://localhost:8080` 으로 맞춥니다.

---

## 환경 변수

루트에 `.env` 또는 `.env.properties` 를 두면 `application.yml` 의 `optional:file:.env[.properties]` 로 읽을 수 있습니다.

### 공통으로 자주 쓰는 값

| 변수 | 설명 |
| --- | --- |
| `JWT_SECRET_KEY` | JWT 서명용 비밀키(충분한 길이) |

### AI 게이트웨이 (제시어·판별·채팅 검열 등)

| 변수 | 설명 |
| --- | --- |
| `AI_GATEWAY_BASE_URL` | LLM API 베이스 URL |
| `AI_GATEWAY_API_KEY` | API 키 |
| `AI_MODEL` | 모델 식별자 |
| `AI_MAX_COMPLETION_TOKENS` | 완성 토큰 상한(선택) |

### 운영 DB (`prod`)

| 변수 | 설명 |
| --- | --- |
| `DB_USERNAME` | MySQL 사용자 |
| `DB_PASSWORD` | MySQL 비밀번호 |

### 스토리지 (`prod` 기본은 S3 모드)

| 변수 | 설명 |
| --- | --- |
| `STORAGE_MODE` | `s3` 또는 `local` |
| `STORAGE_S3_BUCKET` | 버킷 이름 |
| `STORAGE_S3_REGION` | 리전(기본 예: `ap-northeast-2`) |
| `STORAGE_S3_BASE_URL` | 노출용 베이스 URL 등 |

### AWS

| 변수 | 설명 |
| --- | --- |
| `AWS_REGION` | 리전 |

자세한 기본값·프로필별 차이는 `src/main/resources/application.yml`, `application-prod.yml` 을 참고하세요.

---

## 실행 프로필

| 프로필 | 설명 |
| --- | --- |
| *(미지정)* | 로컬 개발용: H2, 로컬 스토리지 기본값 등 |
| `prod` | 운영: MySQL, Redis, S3, 필수 시크릿 검증 |

예시:

```bash
./gradlew bootRun --args='--spring.profiles.active=prod'
```

---

## Gradle 작업

| 명령 | 설명 |
| --- | --- |
| `./gradlew bootRun` | 애플리케이션 실행 |
| `./gradlew build` | 컴파일 + 테스트 + 패키징 |
| `./gradlew test` | 테스트만 |
| `./gradlew spotlessCheck` | Java 포맷·import 규칙 검사(CI와 동일) |
| `./gradlew spotlessApply` | Spotless 자동 수정 |

---

## API 문서

애플리케이션 실행 후 브라우저에서 **Swagger UI** 로 OpenAPI 명세를 확인합니다.

- 로컬 예: `http://localhost:8080/swagger-ui/index.html` (SpringDoc OpenAPI 2.x 기준)

---

## 프로젝트 구조

```
src/main/java/backend/drawrace/
├── DrawRaceApplication.java    # 진입점
├── domain/                     # 도메인별 패키지
│   ├── room/                   # 방, 참가자, 랭킹
│   ├── round/                  # 라운드, 제출, AI 판별
│   ├── user/                   # 회원, 게스트, 친구
│   └── chat/                   # 채팅, 검열
└── global/                     # 설정, 보안, WebSocket, 스토리지, 예외
```

---

## CI/CD

| 항목 | 내용 |
| --- | --- |
| **CI** | GitHub Actions — JDK 21, Redis 서비스 컨테이너, Spotless 검사, `./gradlew build` |
| **배포** | `main` 푸시 시 EC2에 SSH 후 `git pull`, 빌드, **systemd** 로 백엔드 재시작(워크플로 기준) |

---

## 브랜드 에셋

| 파일 | 용도 |
| --- | --- |
| [`docs/drawrace-logo.png`](docs/drawrace-logo.png) | DrawRace 팀 로고(프론트 `public` 과 동일 에셋 복사) |

---

## 관련 링크

| 구분 | URL |
| --- | --- |
| 서비스 | [drawrace.site](https://drawrace.site/) · [www.drawrace.site](https://www.drawrace.site/) |
| 프론트엔드 | [INT1-Project-Team05-FE](https://github.com/Programmers-Intern-Program/INT1-Project-Team05-FE) |
| ORG | [Programmers-Intern-Program](https://github.com/Programmers-Intern-Program) |

---

## 라이선스

교육·인턴 과제용 프로젝트입니다. 팀 정책에 따릅니다.
