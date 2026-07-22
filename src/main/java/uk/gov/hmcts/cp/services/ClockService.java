package uk.gov.hmcts.cp.services;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class ClockService {

    private final Clock clock;

    public ClockService(final Clock clock) {
        this.clock = clock;
    }

    public Instant now() {
        return clock.instant();
    }

    public OffsetDateTime nowOffsetUTC() {
        return clock.instant().atOffset(ZoneOffset.UTC);
    }
}