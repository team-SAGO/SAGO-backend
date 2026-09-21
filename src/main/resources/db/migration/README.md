# DB 마이그레이션 규칙

스키마는 이 폴더의 SQL이 만든다. `ddl-auto`는 `validate`라 스키마를 만들지 않고 엔티티와 맞는지만 확인한다.

## 이미 적용된 파일은 고치지 않는다

`V1__init.sql`처럼 한 번 적용된 파일을 고치면 체크섬이 바뀌어, 그 파일을 이미 적용한 DB에서 Flyway가 기동을 막는다.
변경은 항상 **새 파일(`V2__`, `V3__`)** 로 쌓는다.

## 엔티티를 고치면 스크립트를 함께 올린다

| 엔티티에서 한 일 | 필요한 스크립트 |
| --- | --- |
| 필드(컬럼) 추가·삭제·타입 변경 | `ALTER TABLE ...` |
| 엔티티(테이블) 추가 | `CREATE TABLE ...` |
| 인덱스·유니크 제약 추가 | `CREATE INDEX` / `ALTER TABLE ... ADD CONSTRAINT` |
| **enum 상수 추가** | **체크 제약 교체** (아래) |
| **enum 상수 삭제·이름 변경** | **기존 행 정리 → 체크 제약 교체** (아래) |

위 표의 경우를 빠뜨리면 `SchemaMatchesEntitiesTest`가 실패한다 — 컬럼, enum 체크 제약, 엔티티에 선언한 인덱스·유니크 제약을 각각 대조한다.

## enum 상수를 추가할 때 — 가장 빠뜨리기 쉽다

`@Enumerated(EnumType.STRING)` 컬럼마다 Hibernate가 허용 값을 체크 제약으로 만들어 두었다.

```sql
contact_type varchar(20) not null check (contact_type in ('EMERGENCY_112','EMERGENCY_119','INSURANCE'))
```

enum에 상수만 추가하면 **Hibernate의 `validate`는 이걸 잡지 못한다.** 컬럼 타입만 보고 체크 제약 내용은 보지 않기 때문이다.
그러면 운영에서 새 값을 처음 저장하는 순간 체크 제약 위반으로 터진다.

체크 제약을 새 값 목록으로 교체하는 스크립트가 필요하다.

```sql
-- V2__add_contact_type_towing.sql
alter table contact_log drop constraint <기존 제약 이름>;
alter table contact_log add constraint ck_contact_log_contact_type
    check (contact_type in ('EMERGENCY_112','EMERGENCY_119','INSURANCE','TOWING'));
```

`SchemaMatchesEntitiesTest`의 enum 검사가 이 누락을 잡는다.

## enum 상수를 삭제하거나 이름을 바꿀 때 — 순서가 중요하다

PostgreSQL은 체크 제약을 추가할 때 **기존 행도 검사한다.** 지운 값을 가진 행이 남아 있으면
새 제약을 붙이는 순간 마이그레이션이 실패한다. 그래서 **값을 먼저 정리하고 제약을 바꾼다.**

```sql
-- V3__remove_contact_type_emergency_119.sql
-- 1. 기존 제약을 먼저 뗀다 (그대로 두면 아래 UPDATE가 새 값을 막을 수 있다)
alter table contact_log drop constraint <기존 제약 이름>;
-- 2. 지울 값을 남는 값으로 옮긴다. 어떤 값으로 옮길지는 기획 판단이 필요하다
update contact_log set contact_type = 'EMERGENCY_112' where contact_type = 'EMERGENCY_119';
-- 3. 최종 목록으로 제약을 다시 붙인다
alter table contact_log add constraint ck_contact_log_contact_type
    check (contact_type in ('EMERGENCY_112','INSURANCE'));
```

이름 변경도 같다. 1(제약 제거) → 2(값을 새 이름으로 UPDATE) → 3(새 목록으로 제약 추가).
**테스트는 이 순서를 검사하지 못한다.** 테스트 DB는 빈 상태에서 시작해 옮길 행이 없기 때문이다.

## 테스트가 확인하는 것 — 그리고 확인하지 못하는 것

- `SchemaMatchesEntitiesTest` — 마이그레이션을 전부 적용한 스키마가
  - 엔티티의 컬럼·타입과 맞는지 (`validate`)
  - enum 체크 제약이 모든 상수를 허용하는지
  - 엔티티에 선언한 `@Index`·`@UniqueConstraint`가 모두 있는지
- `FlywayMigrationTest` — 마이그레이션이 적용되는지, 테이블 15개·유니크 제약 2개·인덱스 6개가 V1대로 만들어지는지

**확인하지 못하는 것:**
- **enum 삭제·이름 변경 시 기존 행 정리 순서** — 테스트 DB에는 옮길 행이 없다 (위 참고)
- **외래 키, 체크 제약의 세부 조건** — 이름으로 선언하지 않아 대조할 기준이 없다
- **PostgreSQL 고유의 동작** — 아래

### H2는 보조 검증이다

두 테스트 모두 H2의 PostgreSQL 호환 모드에서 돈다. **H2에서 통과해도 실제 PostgreSQL에서 실패하거나 다르게 동작할 수 있다.**
기존 행 검사처럼 데이터에 따라 달라지는 동작은 특히 그렇다.

**배포 전에는 실제 PostgreSQL에 마이그레이션을 적용해 확인해야 한다.** 이를 자동화하려면
Testcontainers가 필요한데, Docker와 CI 환경이 먼저 있어야 한다.
