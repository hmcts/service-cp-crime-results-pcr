package uk.gov.hmcts.cp.repositories;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

// Real Postgres (Testcontainers singleton, started once for the whole test JVM and left for the
// process to reap) rather than an in-memory substitute — proves the Flyway migrations and these
// JPA entities actually agree, which nothing else in this suite checks.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class RepositoryIntegrationTestBase {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected TestEntityManager testEntityManager;

    @DynamicPropertySource
    static void postgresProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    // save() then findById() in the same persistence context can be satisfied entirely from the
    // first-level cache, without ever round-tripping through Postgres — flushing and clearing
    // forces a real read, which is the only way this test class actually proves Flyway/JPA
    // column-mapping alignment rather than just Java object self-consistency.
    protected void flushAndClear() {
        testEntityManager.flush();
        testEntityManager.clear();
    }
}
