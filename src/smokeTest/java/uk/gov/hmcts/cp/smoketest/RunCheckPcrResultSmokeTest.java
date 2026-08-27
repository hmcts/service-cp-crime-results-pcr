package uk.gov.hmcts.cp.smoketest;

import com.intuit.karate.junit5.Karate;

class RunCheckPcrResultSmokeTest {

    @Karate.Test
    Karate testRunCheckPcrResult() {
        return Karate.run("run-check-pcr-result").relativeTo(getClass());
    }
}
