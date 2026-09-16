package uk.gov.hmcts.cp.integration.config;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

// Real, manually-started Postgres, not Testcontainers — matches PostgresInitialise elsewhere.
public class PostgresInitialise implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext ctx) {
        assertPostgresReachable("jdbc:postgresql://localhost:5432/pcrdb", "postgres", "postgres");
        TestPropertyValues.of(
                "spring.datasource.url=jdbc:postgresql://localhost:5432/pcrdb",
                "spring.datasource.username=postgres",
                "spring.datasource.password=postgres",
                // Cached test contexts each keep their own Hikari pool; default size exhausts max_connections.
                "spring.datasource.hikari.maximum-pool-size=4"
        ).applyTo(ctx.getEnvironment());
    }

    static void assertPostgresReachable(final String url, final String user, final String password) {
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "\n\n*** Integration tests require PostgreSQL on localhost:5432 (database: pcrdb) ***\n"
                    + "Start it:\n"
                    + "  docker compose up -d postgres\n\n",
                    e);
        }
    }
}
