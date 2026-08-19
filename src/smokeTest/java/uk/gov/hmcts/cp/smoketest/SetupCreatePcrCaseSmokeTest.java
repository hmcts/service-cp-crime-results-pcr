package uk.gov.hmcts.cp.smoketest;

import com.intuit.karate.junit5.Karate;

class SetupCreatePcrCaseSmokeTest {

    @Karate.Test
    Karate testSetupCreatePcrCase() {
        return Karate.run("setup-create-pcr-case").relativeTo(getClass());
    }
}
