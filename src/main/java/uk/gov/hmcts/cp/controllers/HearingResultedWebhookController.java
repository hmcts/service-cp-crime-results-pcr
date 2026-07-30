package uk.gov.hmcts.cp.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.cp.openapi.api.InternalApi;
import uk.gov.hmcts.cp.openapi.model.HearingResultedWebhookEvent;
import uk.gov.hmcts.cp.openapi.model.WebhookAck;
import uk.gov.hmcts.cp.services.HearingResultedWebhookService;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class HearingResultedWebhookController implements InternalApi {

    private final HearingResultedWebhookService webhookService;

    @Override
    public ResponseEntity<WebhookAck> receiveHearingResultedWebhook(final List<HearingResultedWebhookEvent> hearingResultedWebhookEvent) {
        return webhookService.handle(hearingResultedWebhookEvent);
    }
}
