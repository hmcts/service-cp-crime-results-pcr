package uk.gov.hmcts.cp.clients;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HearingResultedCacheClientTest {

    private static final UUID HEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final String HEARING_DAY = "2026-07-23";
    private static final String EXPECTED_KEY = "INT_" + HEARING_ID + "_" + HEARING_DAY + "_result_";

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private HearingResultedCacheClient cacheClient;

    @Test
    void get_should_returnCachedValue_whenPresentUnderIntPrefixedKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(EXPECTED_KEY)).thenReturn("{\"hearing\":{}}");

        final Optional<String> result = cacheClient.get(HEARING_ID, HEARING_DAY);

        assertThat(result).contains("{\"hearing\":{}}");
    }

    @Test
    void get_should_returnEmpty_whenRedisMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(EXPECTED_KEY)).thenReturn(null);

        final Optional<String> result = cacheClient.get(HEARING_ID, HEARING_DAY);

        assertThat(result).isEmpty();
    }
}