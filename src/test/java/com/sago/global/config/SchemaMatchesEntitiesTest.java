package com.sago.global.config;

import jakarta.persistence.Column;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.EntityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 마이그레이션을 모두 적용한 스키마가 엔티티와 일치하는지 확인한다.
 *
 * 다른 애플리케이션 테스트는 H2에서 엔티티로 스키마를 만들기 때문에, 엔티티를 고치고
 * 마이그레이션 스크립트를 빠뜨려도 전부 통과한다. 그러면 배포할 때 처음 드러난다.
 * 여기서는 엔티티로 만들지 않고 Flyway로 만든 뒤 validate로 대조한다.
 *
 * H2의 PostgreSQL 호환 모드라, 앞으로 PostgreSQL 전용 문법을 스크립트에 쓰면 여기서 깨질 수 있다.
 * 그때는 Testcontainers로 옮긴다.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.datasource.url=jdbc:h2:mem:schema-validate;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
})
class SchemaMatchesEntitiesTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("마이그레이션을 모두 적용한 스키마가 엔티티와 일치한다")
    void migrationsMatchEntities() {
        // 컨텍스트가 뜨면 통과다. 엔티티를 고치고 V 스크립트를 빠뜨리면
        // Hibernate의 validate가 기동을 막아 여기서 실패한다.
        assertThat(entityManagerFactory.isOpen()).isTrue();
    }

    /**
     * enum 상수를 추가해도 validate는 잡지 못한다. 컬럼 타입만 보고 체크 제약 내용은 보지 않는다.
     * 그러면 운영에서 새 값을 처음 저장하는 순간 체크 제약 위반으로 터진다.
     * 그래서 enum 컬럼마다 DB 체크 제약이 모든 상수를 허용하는지 따로 확인한다.
     */
    @Test
    @DisplayName("enum 컬럼의 체크 제약이 모든 상수를 허용한다")
    void checkConstraintsAllowEveryEnumConstant() {
        List<String> missing = new ArrayList<>();

        for (EntityType<?> entity : entityManagerFactory.getMetamodel().getEntities()) {
            Class<?> type = entity.getJavaType();
            Table table = type.getAnnotation(Table.class);
            if (table == null) {
                continue;
            }
            String tableName = table.name().replace("\"", "").toLowerCase(Locale.ROOT);

            for (Field field : type.getDeclaredFields()) {
                Enumerated enumerated = field.getAnnotation(Enumerated.class);
                Column column = field.getAnnotation(Column.class);
                if (enumerated == null || enumerated.value() != EnumType.STRING || column == null) {
                    continue;
                }
                String clause = checkClauseFor(tableName, column.name());

                for (Object constant : field.getType().getEnumConstants()) {
                    String value = ((Enum<?>) constant).name();
                    if (clause == null || !clause.contains("'" + value + "'")) {
                        missing.add(tableName + "." + column.name() + " = " + value);
                    }
                }
            }
        }

        // 여기서 실패하면 enum에 상수를 추가하고 체크 제약 교체 스크립트(V__)를 빠뜨린 것이다
        assertThat(missing).as("체크 제약이 허용하지 않는 enum 값").isEmpty();
    }

    private String checkClauseFor(String tableName, String columnName) {
        List<String> clauses = jdbcTemplate.queryForList(
            "select cc.check_clause from information_schema.check_constraints cc "
                + "join information_schema.table_constraints tc "
                + "on cc.constraint_name = tc.constraint_name "
                + "where lower(tc.table_name) = ?",
            String.class, tableName);

        return clauses.stream()
            .filter(clause -> clause.toLowerCase(Locale.ROOT).contains(columnName.toLowerCase(Locale.ROOT)))
            .findFirst()
            .orElse(null);
    }
}
