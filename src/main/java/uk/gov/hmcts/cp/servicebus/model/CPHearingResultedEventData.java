package uk.gov.hmcts.cp.servicebus.model;

import java.util.UUID;

public record CPHearingResultedEventData(UUID hearingId, String hearingDay, String userId) {
}