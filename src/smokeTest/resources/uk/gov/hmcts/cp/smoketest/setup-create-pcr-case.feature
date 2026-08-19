Feature: drive a real hearing result through the CP backend for PCR smoke testing

  Setup only - creates a case via SPI-IN, enters a guilty plea, drafts and shares a custodial
  result, then records the resolved caseUrn/hearingId/defendantId in
  smoke-test-config/<env>.json for run-check-pcr-result.feature to read. Never calls PCR's own
  /internal/hearing-results directly - the real Event Grid -> pcr-eventgrid-relay-function path
  delivers ingestion, so this proves the whole pipeline, not just the PCR service in isolation
  (design doc §3). Dev/sit only, no PRP/PRD tier exists for PCR.

  Does not seed a now-subscriptions entry - dev/sit are assumed to already carry a standing
  PCR subscription for the smoke test's court/offence combination (confirmed decision, design
  doc §8 follow-up).

  Background:
    * def smokeUtils = read('smoke-utils.js')()
    * def caseUrn = smokeUtils.generateUrn()
    * def todayDate = smokeUtils.today()
    * def orderDate = smokeUtils.today()
    * def futureDate = smokeUtils.hearingDatePlusDays(30)

  Scenario: submit SPI-IN case creation, share a custodial result, record the resolved ids

    * def requestId = java.util.UUID.randomUUID() + ''
    * def hearingDate = smokeUtils.hearingDatePlusDays(4)
    * def soapTemplate = karate.readAsString('classpath:spi-in-minimal.xml')
    * def soapEnvelope = soapTemplate.replace('REQUEST_ID', requestId).replace('NEW_URN', caseUrn).replace('TODAY+4', hearingDate).replace('ADDRESS_LINE1', '1 Smoke Test Street')

    Given url backendBaseUrl + '/stagingprosecutorsspi-service/CJSEService'
    And header Content-Type = 'application/soap+xml;charset=UTF-8'
    And header CJSCPPUID = cjscppuid
    And request soapEnvelope
    When method post
    Then status 200
    * def responseText = new java.lang.String(responseBytes, 'UTF-8')
    And match responseText contains '<ResponseCode>1</ResponseCode>'

    # Resolve the server-assigned caseId from the caseUrn (SPI-IN's SOAP response doesn't hand it back directly)
    * retry until responseStatus == 200 && response.caseId != null
    Given url backendBaseUrl + '/prosecutioncasefile-query-api/query/api/rest/prosecutioncasefile/cases'
    And param prosecutionCaseReference = caseUrn
    And header Accept = 'application/vnd.prosecutioncasefile.query.case-by-prosecutionCaseReference+json'
    And header CJSCPPUID = cjscppuid
    When method get
    * def caseId = response.caseId

    # Resolve hearingId/defendantId/offenceId from the progression prosecutioncase view
    * retry until responseStatus == 200 && response.hearingsAtAGlance.hearings[0] != null
    Given url backendBaseUrl + '/progression-query-api/query/api/rest/progression/prosecutioncases/' + caseId
    And header Accept = 'application/vnd.progression.query.prosecutioncase+json'
    And header CJSCPPUID = cjscppuid
    When method get
    * def hearingId = response.hearingsAtAGlance.hearings[0].id
    * def defendantId = response.prosecutionCase.defendants[0].id
    * def offenceId = response.prosecutionCase.defendants[0].offences[0].id

    # Resolve hearingDay (the sitting day the draft/shared-results endpoints are keyed on)
    * retry until responseStatus == 200 && response.hearing.hearingDays[0] != null
    Given url backendBaseUrl + '/hearing-query-api/query/api/rest/hearing/hearings/' + hearingId
    And header Accept = 'application/vnd.hearing.get.hearing+json'
    And header CJSCPPUID = cjscppuid
    When method get
    * def hearingDay = response.hearing.hearingDays[0].sittingDay.split('T')[0]

    # Enter a guilty plea
    * def pleaPayload = karate.readAsString('classpath:fixtures/pleas/hearing-update-plea-guilty.json').replaceAll('DEFENDANT_ID', defendantId).replaceAll('OFFENCE_ID', offenceId).replaceAll('CASE_ID', caseId).replaceAll('HEARING_ID', hearingId).replaceAll('TODAY_DATE', todayDate)

    Given url backendBaseUrl + '/hearing-command-api/command/api/rest/hearing/hearings/' + hearingId
    And header Content-Type = 'application/vnd.hearing.update-plea+json'
    And header CJSCPPUID = cjscppuid
    And request pleaPayload
    When method post
    Then status 200

    # Save a custodial (IMP) draft result
    * def resultLineIds = { imp: '#(java.util.UUID.randomUUID() + \'\')', vulnerability: '#(java.util.UUID.randomUUID() + \'\')', vsa: '#(java.util.UUID.randomUUID() + \'\')', oatc: '#(java.util.UUID.randomUUID() + \'\')', timp: '#(java.util.UUID.randomUUID() + \'\')', fvs: '#(java.util.UUID.randomUUID() + \'\')', nocollo: '#(java.util.UUID.randomUUID() + \'\')', acon: '#(java.util.UUID.randomUUID() + \'\')', collom: '#(java.util.UUID.randomUUID() + \'\')', payt: '#(java.util.UUID.randomUUID() + \'\')', pdate: '#(java.util.UUID.randomUUID() + \'\')' }
    * def draftPayload = karate.readAsString('classpath:fixtures/draftresults/pcr-draft-result-v2-imp.json')
    * draftPayload = draftPayload.replaceAll('IMP_LINE_ID', resultLineIds.imp).replaceAll('VULNERABILITY_LINE_ID', resultLineIds.vulnerability).replaceAll('VSA_LINE_ID', resultLineIds.vsa).replaceAll('OATC_LINE_ID', resultLineIds.oatc).replaceAll('TIMP_LINE_ID', resultLineIds.timp).replaceAll('FVS_LINE_ID', resultLineIds.fvs).replaceAll('NOCOLLO_LINE_ID', resultLineIds.nocollo).replaceAll('ACON_LINE_ID', resultLineIds.acon).replaceAll('COLLOM_LINE_ID', resultLineIds.collom).replaceAll('PAYT_LINE_ID', resultLineIds.payt).replaceAll('PDATE_LINE_ID', resultLineIds.pdate)
    * draftPayload = draftPayload.replaceAll('HEARING_ID', hearingId).replaceAll('HEARING_DAY', hearingDay).replaceAll('ORDER_DATE', orderDate).replaceAll('DEFENDANT_ID', defendantId).replaceAll('CASE_ID', caseId).replaceAll('OFFENCE_ID', offenceId).replaceAll('FUTURE_DATE', futureDate)

    Given url backendBaseUrl + '/hearing-command-api/command/api/rest/hearing/hearings/' + hearingId + '/' + hearingDay
    And header Content-Type = 'application/vnd.hearing.save-draft-result-v2+json'
    And header CJSCPPUID = cjscppuid
    And request draftPayload
    When method post
    Then status 200

    # Finalise and share the result - this is the real trigger for Event Grid -> relay -> PCR ingestion
    * def sharedPayload = karate.readAsString('classpath:fixtures/sharedresults/pcr-shared-result-v2-imp.json')
    * sharedPayload = sharedPayload.replaceAll('IMP_LINE_ID', resultLineIds.imp).replaceAll('TIMP_LINE_ID', resultLineIds.timp).replaceAll('VSA_LINE_ID', resultLineIds.vsa).replaceAll('FVS_LINE_ID', resultLineIds.fvs).replaceAll('COLLOM_LINE_ID', resultLineIds.collom).replaceAll('PAYT_LINE_ID', resultLineIds.payt).replaceAll('PDATE_LINE_ID', resultLineIds.pdate)
    * sharedPayload = sharedPayload.replaceAll('ORDER_DATE', orderDate).replaceAll('SHARED_DATE', todayDate).replaceAll('DEFENDANT_ID', defendantId).replaceAll('CASE_ID', caseId).replaceAll('OFFENCE_ID', offenceId).replaceAll('FUTURE_DATE', futureDate)

    Given url backendBaseUrl + '/hearing-command-api/command/api/rest/hearing/hearings/' + hearingId + '/' + hearingDay
    And header Content-Type = 'application/vnd.hearing.shared-results+json'
    And header CJSCPPUID = cjscppuid
    And request sharedPayload
    When method post
    Then status 200

    * smokeUtils.writeCaseConfig(environment, caseUrn, hearingId, defendantId)
    * karate.log('smoke-test-config written for environment:', environment, 'caseUrn:', caseUrn, 'hearingId:', hearingId, 'defendantId:', defendantId)
