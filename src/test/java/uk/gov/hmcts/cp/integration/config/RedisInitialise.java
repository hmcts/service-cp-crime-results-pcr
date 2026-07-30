package uk.gov.hmcts.cp.integration.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

// Same "real, externally-started, not Testcontainers" pattern as PostgresInitialise
public class RedisInitialise implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext ctx) {
        assertRedisReachable("localhost", 6379);
    }

    static void assertRedisReachable(final String host, final int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1000);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "\n\n*** Integration tests require Redis on localhost:6379 ***\n"
                    + "Start it:\n"
                    + "  docker compose up -d redis\n\n",
                    e);
        }
    }
}