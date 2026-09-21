package com.sago.global.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 마이그레이션 SQL이 실제로 적용되는지 확인한다.
 *
 * 애플리케이션 테스트는 H2에서 엔티티로 스키마를 만들기 때문에, 마이그레이션이 깨져 있어도
 * 전부 통과한다. 그러면 배포할 때 처음 알게 된다. 여기서만 마이그레이션을 직접 돌린다.
 *
 * PostgreSQL 대신 H2의 PostgreSQL 호환 모드를 쓴다. 문법 차이가 남아 있어 실제 PostgreSQL을
 * 완전히 대신하지는 못하지만, 오타나 빠진 제약처럼 흔한 실수는 여기서 걸린다.
 */
class FlywayMigrationTest {

    private String jdbcUrl() {
        return "jdbc:h2:mem:migration-" + UUID.randomUUID()
            + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
    }

    private MigrateResult migrate(String url) {
        return Flyway.configure()
            .dataSource(url, "sa", "")
            .locations("classpath:db/migration")
            .load()
            .migrate();
    }

    private List<String> query(String url, String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                values.add(rs.getString(1).toLowerCase());
            }
        }
        return values;
    }

    @Test
    @DisplayName("마이그레이션이 적용된다")
    void appliesMigrations() {
        String url = jdbcUrl();

        MigrateResult result = migrate(url);

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isPositive();
    }

    @Test
    @DisplayName("엔티티에 해당하는 테이블이 모두 만들어진다")
    void createsAllTables() throws SQLException {
        String url = jdbcUrl();
        migrate(url);

        List<String> tables = query(url,
            "select table_name from information_schema.tables "
                + "where upper(table_schema) = 'PUBLIC'");

        assertThat(tables).contains("accident", "user", "photo", "statement", "report",
            "social_auth", "refresh_token", "terms_agreement", "checklist_item", "contact_log");
    }

    @Test
    @DisplayName("동시 요청 방어에 쓰이는 유니크 제약이 살아 있다")
    void keepsUniqueConstraintsThatGuardConcurrency() throws SQLException {
        String url = jdbcUrl();
        migrate(url);

        List<String> constraints = query(url,
            "select constraint_name from information_schema.table_constraints "
                + "where constraint_type = 'UNIQUE'");

        // social_auth: 같은 소셜 계정으로 동시에 두 번 가입하는 것을 막는다
        // refresh_token: 토큰 회전에서 경합의 승자를 DB가 정하게 한다
        assertThat(constraints).contains("uk_social_auth_provider_user", "uk_refresh_token_hash");
    }

    @Test
    @DisplayName("조회 성능에 필요한 인덱스가 살아 있다")
    void keepsIndexes() throws SQLException {
        String url = jdbcUrl();
        migrate(url);

        List<String> indexes = query(url,
            "select index_name from information_schema.indexes "
                + "where upper(table_schema) = 'PUBLIC'");

        assertThat(indexes).contains("idx_accident_user_occurred", "idx_photo_accident_created",
            "idx_statement_accident_created", "idx_terms_agreement_user_type");
    }
}
