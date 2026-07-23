package uk.gov.hmcts.cp.servicebus.model;

// Service Bus message body is this envelope, not a flat pointer payload — pointer fields
// are nested under .data. See docs/2026-07-22-pcr-hearing-event-ingestion-design.md §3.1a.
public record EventGridEnvelope(String eventType, String subject, String eventTime, EventGridData data) {
}