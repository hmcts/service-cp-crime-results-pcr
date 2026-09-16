package uk.gov.hmcts.cp.integration.e2e.driftdetection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FixtureProviderTest {

    @Test
    void provideArguments_should_discoverMultipleDefendantsMultipleOffencesFixture() throws Exception {
        final FixtureProvider provider = new FixtureProvider();

        final List<DriftFixture> fixtures = provider.provideArguments(null, null)
                .map(Arguments::get)
                .map(args -> (DriftFixture) args[0])
                .toList();

        assertThat(fixtures)
                .extracting(DriftFixture::name)
                .contains("multiple-defendants-multiple-offences-as231157673");
    }

    @Test
    void provideArguments_should_resolveRootContainingEventJson() throws Exception {
        final FixtureProvider provider = new FixtureProvider();

        final DriftFixture fixture = provider.provideArguments(null, null)
                .map(Arguments::get)
                .map(args -> (DriftFixture) args[0])
                .filter(f -> "multiple-defendants-multiple-offences-as231157673".equals(f.name()))
                .findFirst()
                .orElseThrow();

        assertThat(fixture.root().resolve("event.json")).exists();
    }
}
