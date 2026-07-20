package uk.gov.hmcts.cp.filters.tracing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class UUIDService {

    public UUID random() {
        return UUID.randomUUID();
    }

    public String randomString() {
        return UUID.randomUUID().toString();
    }
}