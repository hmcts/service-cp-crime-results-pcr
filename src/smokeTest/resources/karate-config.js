function fn() {
  function env(key, fallback) { return java.lang.System.getenv(key) || fallback; }
  if (env('SMOKE_INSECURE_TLS', 'false') === 'true') { karate.configure('ssl', true); }
  var LogModifier = Java.type('uk.gov.hmcts.cp.smoketest.SmokeTestLogModifier');
  karate.configure('logModifier', new LogModifier());
  var entraTenantId = env('SMOKE_ENTRA_TENANT_ID', '');
  var serviceBaseUrl = java.lang.System.getenv('SMOKE_SERVICE_BASE_URL');
  if (!serviceBaseUrl) { karate.fail('SMOKE_SERVICE_BASE_URL is required - no default, must point at the environment under test'); }
  var environment = java.lang.System.getenv('SMOKE_ENVIRONMENT');
  if (!environment) { karate.fail('SMOKE_ENVIRONMENT is required - no default, selects smoke-test-config/<env>.json (design doc §4)'); }
  return {
    environment: environment,
    backendBaseUrl: env('CP_BACKEND_URL', 'http://localhost:8081'),
    cjscppuid: env('CJSCPPUID', '00000000-0000-0000-0000-000000000000'),
    serviceBaseUrl: serviceBaseUrl,
    tokenUrl: 'https://login.microsoftonline.com/' + entraTenantId + '/oauth2/v2.0/token',
    entraClientId: env('SMOKE_ENTRA_CLIENT_ID', ''),
    entraClientSecret: env('SMOKE_ENTRA_CLIENT_SECRET', ''),
    entraScope: env('SMOKE_ENTRA_SCOPE', ''),
    apimSubscriptionKey: env('SMOKE_APIM_SUBSCRIPTION_KEY', '')
  };
}
