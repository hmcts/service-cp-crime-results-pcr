Feature: check the PCR query endpoint for smoke testing

  Run only - reads caseUrn/hearingId/defendantId from smoke-test-config/<env>.json (written by
  Setup) and verifies the recorded PCR result is retrievable. Ingestion is asynchronous (real
  Event Grid delivery, relay-function forward, PCR's own internal retry on an incomplete hearing -
  design doc §5), so this polls with a bounded retry rather than a single GET. The query endpoint
  always returns 200 (empty array until ingestion lands, per PcrResultsService's no-404 contract),
  so the retry condition checks for a non-empty array, not the status code.

  Background:
    * def smokeUtils = read('smoke-utils.js')()
    * def caseConfig = smokeUtils.readCaseConfig(environment)
    * assert caseConfig.environment == environment
    * def caseUrn = caseConfig.caseUrn
    * def hearingId = caseConfig.hearingId
    * def defendantId = caseConfig.defendantId

  Scenario: verify the PCR query endpoint returns the configured result

    Given url tokenUrl
    And header Content-Type = 'application/x-www-form-urlencoded'
    And form field grant_type = 'client_credentials'
    And form field client_id = entraClientId
    And form field client_secret = entraClientSecret
    And form field scope = entraScope
    When method post
    Then status 200
    * def accessToken = response.access_token

    # Bounded retry: every 10s, up to 5 minutes - long enough for real Event Grid delivery plus
    # PCR's own 2s/4s/8s completeness retry, short enough to fail distinctly from a genuine hang
    * configure retry = { count: 30, interval: 10000 }
    * retry until responseStatus == 200 && response[0] != null
    Given url serviceBaseUrl + '/cases/' + caseUrn + '/hearings/' + hearingId + '/defendants/' + defendantId
    And header Authorization = 'Bearer ' + accessToken
    And header Ocp-Apim-Subscription-Key = apimSubscriptionKey
    When method get
    Then status 200
    And match response[0] != null

    And match response[0].prosecutionCase.caseURN == caseUrn
    And match response[0].hearing.id == hearingId
    And match response[0].defendant.id == defendantId
    * karate.log('PCR result verified for caseUrn:', caseUrn, 'hearingId:', hearingId, 'defendantId:', defendantId)
