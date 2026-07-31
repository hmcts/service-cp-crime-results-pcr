package uk.gov.hmcts.cp.integration.e2e.driftdetection;

import lombok.SneakyThrows;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.support.ParameterDeclarations;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class FixtureProvider implements ArgumentsProvider {

    private static final String FIXTURE_ROOT = "drift-detection";

    @Override
    @SneakyThrows
    public Stream<? extends Arguments> provideArguments(final ParameterDeclarations parameters, final ExtensionContext context) {
        final URL resource = getClass().getClassLoader().getResource(FIXTURE_ROOT);
        final Path root = Path.of(resource.toURI());
        try (Stream<Path> children = Files.list(root)) {
            return children.filter(Files::isDirectory)
                    .map(dir -> Arguments.of(new DriftFixture(dir.getFileName().toString(), dir)))
                    .toList()
                    .stream();
        }
    }
}
