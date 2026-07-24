package uk.gov.hmcts.cp.clients;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class HearingResultedCacheClient {

    private final StringRedisTemplate redisTemplate;

    public Optional<String> get(final UUID hearingId, final String hearingDay) {
        final String key = cacheKey(hearingId, hearingDay);
        final String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            log.info("Redis miss for hearingId:{} — falling back to REST", hearingId);
        }
        return Optional.ofNullable(value);
    }

    private String cacheKey(final UUID hearingId, final String hearingDay) {
        return "INT_" + hearingId + "_" + hearingDay + "_result_";
    }
}
