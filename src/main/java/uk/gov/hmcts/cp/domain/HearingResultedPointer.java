package uk.gov.hmcts.cp.domain;

import java.util.UUID;

public record HearingResultedPointer(UUID hearingId, String hearingDay, String userId) {
}