# service-cp-crime-results-pcr

Spring Boot service exposing Prison Court Register (PCR) source data to API Marketplace subscribers — a pull-based read channel alongside the existing PDF/email distribution pipeline, not a replacement for it.

Scaffolded from [service-hmcts-crime-springboot-template](https://github.com/hmcts/service-hmcts-crime-springboot-template); see that repo's own [README](https://github.com/hmcts/service-hmcts-crime-springboot-template/blob/main/README.md) and [docs](https://github.com/hmcts/service-hmcts-crime-springboot-template/blob/main/docs) for generic build/PMD instructions and implementation-pattern examples.

## Design

Full design — architecture, trigger, transformation, versioning, retention/ack, and open cross-team items — is in [docs/2026-07-16-pcr-api-marketplace-design-v2.md](docs/2026-07-16-pcr-api-marketplace-design-v2.md).

## Upstream / downstream

- **Upstream:** Azure Event Grid `Hearing_Resulted` (via `cpp-context-results`) routed through a Service Bus queue; Results Query Client (Redis first, REST fallback against the Results Query API); Reference Data (`ResultDefinition` lookups).
- **Downstream:** `service-cp-crime-hearing-results-document-subscription` — this service's Query API URL is wired into that service's existing subscriber callback payload.

## API contract

No published `api-cp-*` spec exists yet for this service — the `apiSpec` dependency coordinate in `build.gradle` is intentionally left unwired until that spec repo is created. See the design doc's open items (§13) before adding one.

## Documentation

- [Logging Documentation](docs/Logging.md) — logging configuration and best practices.

## Ownership

- **Owning team:** `api-marketplace`.
- **Support model / on-call / escalation path:** TBD — not yet defined for this service.

### New team member setup

Anyone newly added to the owning team should verify push access once:

```bash
gh auth login                                          # if not already authenticated
git clone git@github.com:hmcts/service-cp-crime-results-pcr.git
cd service-cp-crime-results-pcr
git checkout -b smoke/access-check
git commit --allow-empty -m "chore: verify push access"
git push -u origin smoke/access-check
git push origin --delete smoke/access-check             # clean up the throwaway branch
```

If the push is rejected with a permissions error, check the `api-marketplace` team's repo grant and membership before assuming a tooling problem.

### Contribute to This Repository

Contributions are welcome! Please see the [CONTRIBUTING.md](.github/CONTRIBUTING.md) file for guidelines.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details