package uk.gov.hmcts.cp.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract test: verifies that log message fragments used in KQL queries (support/alerts-kql/) still
 * exist in the expected Java source files, the correct number of times.
 *
 * <p>If a log message is renamed or removed, this test fails — reminding you to consider the KQL
 * queries too.
 */
class KqlLogMessageContractTest {

    private static final Pattern KQL_CONTAINS_PATTERN =
        Pattern.compile("(?:\\| where|or) LogMessage contains ['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);

    static Stream<Arguments> kqlLogMessageContracts() {
        return Stream.of(
            kqlContract("native delivery limit reached, dead-lettering", "HearingResultedServiceBusConsumer", 1),
            kqlContract("dead-lettering, not redelivering",              "HearingResultedServiceBusConsumer", 1),
            kqlContract("handleIncomplete exhausted after",              "HearingResultedServiceBusConsumer", 1),
            kqlContract("processError unexpected error on pcr queue",    "HearingResultedServiceBusConsumer", 1),
            kqlContract("does not exist — expected to be provisioned by Terraform", "HearingResultedServiceBusConsumer", 1)
        );
    }

    private static Arguments kqlContract(final String fragment, final String className, final int expectedCount) {
        return Arguments.of(fragment, className, expectedCount);
    }

    @Test
    void all_kql_log_message_fragments_should_be_covered_by_contracts() throws IOException {
        final Set<String> kqlFragments = extractKqlFragments();
        final Set<String> contractFragments = kqlLogMessageContracts()
            .map(args -> (String) args.get()[0])
            .collect(Collectors.toSet());

        assertThat(kqlFragments)
            .as("KQL fragments not covered by contract test — add entries to kqlLogMessageContracts()")
            .containsExactlyInAnyOrderElementsOf(contractFragments);
    }

    @ParameterizedTest(name = "\"{0}\" should appear {2} time(s) in {1}")
    @MethodSource("kqlLogMessageContracts")
    void kql_log_message_fragment_should_exist_in_java_source(
            final String fragment, final String className, final int expectedCount) throws IOException {
        final Path sourceFile = findJavaFile(className);
        assertThat(sourceFile).as("Java source file not found for class: " + className).isNotNull();

        final String source = Files.readString(sourceFile);
        final int actualCount = countOccurrences(source, fragment);

        assertThat(actualCount)
            .as("Log message fragment \"%s\" expected %d time(s) in %s but found %d — "
                + "be careful changing this log message it may affect KQL alert queries",
                fragment, expectedCount, className, actualCount)
            .isEqualTo(expectedCount);
    }

    private Set<String> extractKqlFragments() throws IOException {
        final Set<String> fragments = new java.util.HashSet<>();
        final Path kqlPath = Path.of("support/alerts-kql");
        if (!Files.exists(kqlPath)) {
            return fragments;
        }
        try (Stream<Path> files = Files.walk(kqlPath)) {
            files.filter(p -> p.toString().endsWith(".kql")).forEach(kqlFile -> {
                try {
                    final Matcher matcher = KQL_CONTAINS_PATTERN.matcher(Files.readString(kqlFile));
                    while (matcher.find()) {
                        fragments.add(matcher.group(1));
                    }
                } catch (final IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        return fragments;
    }

    private Path findJavaFile(final String className) throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            return files
                .filter(p -> p.getFileName().toString().equals(className + ".java"))
                .findFirst()
                .orElse(null);
        }
    }

    private int countOccurrences(final String source, final String fragment) {
        final Pattern logPattern = Pattern.compile("log\\.[^\n]*" + Pattern.quote(fragment));
        return (int) logPattern.matcher(source).results().count();
    }
}
