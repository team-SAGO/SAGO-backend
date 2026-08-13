# SAGO Backend 🛵

이륜차 사고 대응 서비스 **SAGO**의 백엔드입니다.

## 기술 스택
| 구분 | 기술 |
| --- | --- |
| 언어/런타임 | Java 17 |
| 프레임워크 | Spring Boot 3.5.16 |
| 인증 | Spring Security + OAuth2 Client |
| ORM | Spring Data JPA (Hibernate) |
| DB | PostgreSQL |
| 빌드 | Gradle |

> jjwt · springdoc · AWS S3 · Google STT · OpenHTMLtoPDF 등 특화 라이브러리는
> `build.gradle`에 주석(TODO)으로 좌표를 넣어뒀어요. 필요할 때 주석만 풀면 됩니다.

## 시작하기
JDK 17이 필요합니다. ([Temurin 17](https://adoptium.net/temurin/releases/?version=17))
```bash
./gradlew build      # 빌드 + 테스트
./gradlew bootRun    # 서버 실행
```

## 환경 변수
`.env.example`를 복사해 `.env`를 만들어 사용하세요. (`.env`는 커밋 금지)
```bash
cp .env.example .env
```
> `.env`를 실제로 로드하려면 `build.gradle`의 `spring-dotenv` 주석을 해제하세요.

## AI 코드리뷰
PR을 올리면 **CodeRabbit**이 자동으로 리뷰를 달아줍니다. (`.coderabbit.yaml` 참고)
