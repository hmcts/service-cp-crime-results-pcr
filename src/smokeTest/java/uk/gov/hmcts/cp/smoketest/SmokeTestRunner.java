package uk.gov.hmcts.cp.smoketest;

import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.io.PrintWriter;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

// Standalone entry point for running one smokeTest class outside Gradle (no ./gradlew, no
// Gradle-managed JUnit Platform launcher available) - the ADO deploy pipeline agent can reach
// the internet only for a narrow allowlist (confirmed: Docker Hub, github.com; confirmed blocked:
// services.gradle.org), so it cannot invoke Gradle to resolve dependencies or run tests. This
// class exists so a pre-built, fully self-contained jar (see gradle/smoketestjar.gradle) can run
// a smokeTest class with a plain `java -jar ... <FullyQualifiedClassName>` call instead.
public final class SmokeTestRunner {

    private SmokeTestRunner() {
    }

    public static void main(final String[] args) throws ClassNotFoundException {
        if (args.length != 1) {
            System.err.println("Usage: java -jar <jar> <FullyQualifiedTestClassName>");
            System.exit(2);
            return;
        }

        final Class<?> testClass = Class.forName(args[0]);
        final LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(testClass))
                .build();

        final Launcher launcher = LauncherFactory.create();
        final SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);

        final TestExecutionSummary summary = listener.getSummary();
        summary.printTo(new PrintWriter(System.out));
        summary.printFailuresTo(new PrintWriter(System.out), 100);

        if (summary.getTotalFailureCount() > 0) {
            System.exit(1);
        }
    }
}
