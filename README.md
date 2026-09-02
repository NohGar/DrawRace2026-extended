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
- [인프라](#인프라)
- [브랜드 에셋](#브랜드-에셋)
- [관련 링크](#관련-링크)

---

## 소개

**Draw Race** 백엔드는 방·참가자·게임 라운드·그림 제출·AI 판별, 실시간 채팅·검열, 친구·초대, 전적 등을 담당하는 **Spring Boot** 애플리케이션입니다.  
**REST API**와 **WebSocket(STOMP)** 로 프론트엔드와 연동합니다.

운영에서는 **MySQL**과 **Redis**를 쓰고, 프로필 이미지 등 파일은 **`storage.mode` 설정에 따라** 서버 로컬 디렉터리에 두거나(기본·팀 운영에 맞게), 코드에 포함된 **S3 구현**을 켤 수 있습니다.

---

## 주요 기능

| 영역 | 내용 |
| --- | --- |
| **인증·회원** | JWT, 회원가입·로그인, **게스트 로그인**, 토큰 재발급, 비밀번호·프로필(일반/게스트 정책 구분) |
| **방·게임** | 방 CRUD, 비밀번호 방, 참가자·방장, 게임 시작, 라운드 진행·**시간 초과·미제출 자동 마감**, 다음 라운드·**결승**, 실시간 랭킹(Redis) |
| **AI 유저** | 기동 시 **AI 전용 계정**이 없으면 자동 생성, 방장이 대기 중 **AI 참가자 추가/제거**, 라운드마다 **QuickDraw 스타일 스케치 데이터**로 자동 제출(실패 시 폴백), AI는 로그인 불가·**종료 전적에서 제외** |
| **AI 연동(LLM)** | 제시어 생성(gateway 모드), 그림 판별(재시도·동시 호출 제한), 채팅 검열·기타 게이트웨이 호출 |
| **제시어 모드** | `gateway`(AI 생성 + fallback 목록) / `quickdraw`(카테고리 랜덤) 등 설정에 따라 분기 |
| **실시간** | STOMP — 방 공통 이벤트, 채팅, 랭킹, 드로잉 좌표 중계 등 |
| **채팅** | 키워드·임베딩 유사도 1차, AI 2차 검열, 도배·스트라이크·일시 채팅 금지 |
| **친구·초대** | 친구 요청·수락, 방으로 친구 초대(정책에 따른 제한), 알림용 구독 채널 |
| **게스트 닉네임** | DB 풀에서 할당, 부족 시 LLM으로 생성하는 경로 |
| **파일 저장** | **기본은 로컬 디렉터리**(`storage.mode=local`, 미지정 시에도 로컬 빈이 기본). `storage.mode=s3`일 때만 S3 빈 활성화 |

---

## 기술 스택

| 영역 | 사용 기술 |
| --- | --- |
| 언어·런타임 | **Java 21** |
| 프레임워크 | **Spring Boot 3.3** — Web, Data JPA, Security, WebSocket, Validation |
| 데이터베이스 | **MySQL**(운영 프로필), **H2**(로컬 기본) |
| 인메모리·캐시 | **Redis** — 랭킹, 채팅 검열 스트라이크 등 |
| 인증 | **JWT** (jjwt) |
| API 문서 | **SpringDoc OpenAPI** (Swagger UI) |
| 파일 저장 | **로컬 디스크**(기본) · **AWS S3**(선택, `spring-cloud-aws-starter-s3`, `storage.mode=s3`) |
| ML·텍스트 | **DJL** + **PyTorch** 엔진, Hugging Face 토크나이저(채팅 임베딩 유사도 등) |
| 코드 스타일 | **Spotless** (Palantir Java Format) |
| 컨테이너 | **Docker** / **Docker Compose**, 이미지 레지스트리 **GHCR** |

---

## 요구 사항

- **JDK 21**
- **Redis** (기본 `localhost:6379`)
- **`prod` 프로필**: **MySQL** 및 `application-prod.yml` 에 맞는 DB 계정
- 파일은 **로컬 모드**면 업로드 디렉터리만 있으면 됨. **S3를 쓰지 않는 배포**면 `STORAGE_MODE=local`(또는 동등 설정)으로 두면 됨

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

- 기본 포트: **8080**
- 프로필 미지정 시: **H2 인메모리**, **로컬 파일 저장**, H2 콘솔 사용 가능

프론트와 연동: [INT1-Project-Team05-FE](https://github.com/Programmers-Intern-Program/INT1-Project-Team05-FE) 에서 `NEXT_PUBLIC_API_BASE_URL` 을 `http://localhost:8080` 등으로 맞춥니다.

### Docker로 실행 (MySQL·Redis 포함)

`prod` 프로필과 동일한 구성(MySQL + Redis)을 로컬에서 그대로 재현하고 싶다면:

```bash
cp .env.example .env   # 값 채우기
docker compose up --build
```

- `Dockerfile`: 멀티스테이지 빌드(빌드용 JDK 스테이지 → 실행용 JRE 스테이지)
- `docker-compose.yml`: app + MySQL + Redis, 업로드 파일은 named volume에 보존
- 운영 배포용 구성은 `docker-compose.prod.yml` 참고 (아래 [CI/CD](#cicd) 참고)

---

## 환경 변수

루트 **`.env`** 또는 **`.env.properties`** 를 두면 설정을 읽습니다.

### 공통

| 변수 | 설명 |
| --- | --- |
| `JWT_SECRET_KEY` | JWT 서명 키(충분한 길이) |

### AI 게이트웨이

| 변수 | 설명 |
| --- | --- |
| `AI_GATEWAY_BASE_URL` | LLM API 베이스 URL |
| `AI_GATEWAY_API_KEY` | API 키 |
| `AI_MODEL` | 모델명 |
| `AI_MODE` | `quickdraw` / `gateway` 등(프로필·기본값 참고) |
| `AI_MAX_COMPLETION_TOKENS` | 완성 토큰 상한(선택) |
| `AI_INFERENCE_MAX_CONCURRENT` 등 | 동시 추론 상한 등(프로덕션 yml 참고) |

### 운영 DB (`prod`)

| 변수 | 설명 |
| --- | --- |
| `DB_USERNAME` | MySQL 사용자 |
| `DB_PASSWORD` | MySQL 비밀번호 |

### 파일 저장

| 변수 | 설명 |
| --- | --- |
| `STORAGE_MODE` | **`local`**(디스크) 또는 **`s3`**. 팀이 S3를 쓰지 않으면 **`local`** |
| `STORAGE_LOCAL_UPLOAD_DIR` | 로컬 업로드 경로(프로덕션 예: 서버 경로) |
| `STORAGE_LOCAL_BASE_URL` | 브라우저에 줄 파일 URL prefix |
| `STORAGE_S3_BUCKET`, `STORAGE_S3_REGION`, `STORAGE_S3_BASE_URL` | **`storage.mode=s3` 일 때만** 의미 있음 |
| `AWS_REGION` | S3 등 AWS 클라이언트용(모드에 따라) |

상세 기본값은 `src/main/resources/application.yml`, `application-prod.yml` 을 기준으로 합니다.

---

## 실행 프로필

| 프로필 | 요약 |
| --- | --- |
| *(미지정)* | H2, Redis 로컬, **스토리지 local**, 개발 편의 |
| `prod` | MySQL, Redis, JWT 필수, 스토리지는 **`STORAGE_MODE`** 로 `local` / `s3` 선택(`application-prod` 기본값은 yaml에 정의됨 — 운영 시 팀 값으로 오버라이드) |

```bash
./gradlew bootRun --args='--spring.profiles.active=prod'
```

---

## Gradle 작업

| 명령 | 설명 |
| --- | --- |
| `./gradlew bootRun` | 실행 |
| `./gradlew build` | 빌드 + 테스트 |
| `./gradlew test` | 테스트만 |
| `./gradlew spotlessCheck` | 포맷 검사(CI와 동일) |
| `./gradlew spotlessApply` | 자동 포맷 |

---

## API 문서

실행 후 예: `http://localhost:8080/swagger-ui/index.html`

---

## 프로젝트 구조

```
src/main/java/backend/drawrace/
├── DrawRaceApplication.java
├── domain/
│   ├── room/          # 방, 참가자, AI 참가자 API, 랭킹
│   ├── round/         # 라운드, 제출, AI 판별, QuickDraw 연동
│   ├── user/          # 회원, 게스트, 친구, 닉네임 풀
│   └── chat/          # 채팅 STOMP, 검열
└── global/            # Security, JWT, WebSocket, 스토리지(local/S3), 예외, 초기 데이터(AI 유저 등)
```

---

## CI/CD

| 항목 | 내용 |
| --- | --- |
| **CI** | GitHub Actions — JDK 21, Redis 서비스, Spotless, `./gradlew build` (PR·`main`/`develop` push마다 실행) |
| **이미지 빌드·배포** | 릴리즈 태그(`v*.*.*`)를 push하면 Docker 이미지를 빌드해 **GHCR**에 올리고, EC2에 SSH로 접속해 `docker compose pull && up -d`로 재기동. 컨테이너 재시작은 systemd 대신 Docker의 `restart` 정책이 담당 |
| **롤백** | Actions 탭에서 배포 워크플로우를 **수동 실행**(`workflow_dispatch`)하며 이전 태그를 지정하면, 재빌드 없이 해당 이미지로 즉시 재배포 |

배포 흐름:

```
main에 기능 머지 (배포 안 됨, CI만 실행)
  → 준비되면 태그 생성 & push
      git tag -a v0.1.0 -m "설명"
      git push origin v0.1.0
  → GitHub Actions: 이미지 빌드 → GHCR push → EC2에 SSH로 배포 지시
```

---

## 인프라

현재 인프라는 **단일 EC2에 Docker Compose로 app + MySQL + Redis**를 올린 구성(Tier 0)이며,
EC2·Elastic IP·보안그룹은 `infra/terraform/`에 **Terraform 코드로 편입**되어 있다.

확장 로드맵(Tier 0 → 3), 각 단계의 도입 트리거, Tier 1(RDS·ElastiCache 분리) 도입 근거와
알려진 제약(SimpleBroker, 보안그룹 등)은 **[`docs/infra.md`](docs/infra.md)** 참고.

---

## 브랜드 에셋

| 파일 | 용도 |
| --- | --- |
| [`docs/drawrace-logo.png`](docs/drawrace-logo.png) | DrawRace 팀 로고 |

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
