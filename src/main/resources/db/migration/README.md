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
| **enum 상수 추가·삭제** | **체크 제약 교체** (아래) |

빠뜨리면 `SchemaMatchesEntitiesTest`가 실패한다.

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

## 테스트가 확인하는 것

- `SchemaMatchesEntitiesTest` — 마이그레이션을 전부 적용한 스키마가 엔티티와 맞는지, enum 체크 제약이 모든 상수를 허용하는지
- `FlywayMigrationTest` — 마이그레이션이 적용되는지, 테이블·유니크 제약·인덱스가 살아 있는지

둘 다 H2의 PostgreSQL 호환 모드에서 돈다. **PostgreSQL 전용 문법을 쓰면 여기서 깨질 수 있다.** 그때는 Testcontainers로 옮긴다.
