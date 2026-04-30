# Security Fix Results (20260430110314)

- **Project**: simplecd
- **Completed**: 2026-04-30
- **Build attempts**: 1 (0 failed, 1 succeeded)
- **Plan**: `.github/java-upgrade/20260430110314/plan.md`

## CVE Results

| # | CVE | Dependency | Status |
|---|-----|------------|--------|
| 1 | [CVE-2025-24813](https://github.com/advisories/GHSA-83qj-6fr2-vhqg) | tomcat-embed-core | ✅ Fixed (10.1.31 → 10.1.54) |
| 2 | [CVE-2026-29145](https://github.com/advisories/GHSA-95jq-rwvf-vjx4) | tomcat-embed-core | ✅ Fixed (10.1.31 → 10.1.54) |
| 3 | [CVE-2024-50379](https://github.com/advisories/GHSA-5j33-cvvr-w245) | tomcat-embed-core | ✅ Fixed (10.1.31 → 10.1.54) |
| 4 | [CVE-2024-56337](https://github.com/advisories/GHSA-27hp-xhwr-wr2m) | tomcat-embed-core | ✅ Fixed (10.1.31 → 10.1.54) |
| 5 | [CVE-2025-48988](https://github.com/advisories/GHSA-h3gc-qfqq-6h8f) | tomcat-embed-core | ✅ Fixed (10.1.31 → 10.1.54) |
| 6 | [CVE-2025-52520](https://github.com/advisories/GHSA-wr62-c79q-cv37) | tomcat-embed-core | ✅ Fixed (10.1.31 → 10.1.54) |
| 7 | [CVE-2025-53506](https://github.com/advisories/GHSA-25xr-qj8w-c4vf) | tomcat-embed-core | ✅ Fixed (10.1.31 → 10.1.54) |
| 8 | [CVE-2025-48989](https://github.com/advisories/GHSA-gqp3-2cvr-x8m3) | tomcat-embed-core | ✅ Fixed (10.1.31 → 10.1.54) |
| 9 | [CVE-2025-55752](https://github.com/advisories/GHSA-wmwf-9ccg-fff5) | tomcat-embed-core | ✅ Fixed (10.1.31 → 10.1.54) |
| 10 | [CVE-2026-24734](https://github.com/advisories/GHSA-mgp5-rv84-w37q) | tomcat-embed-core | ✅ Fixed (10.1.31 → 10.1.54) |
| 11 | [CVE-2026-34487](https://github.com/advisories/GHSA-x4m4-345f-5h5g) | tomcat-embed-core | ✅ Fixed (10.1.31 → 10.1.54) |
| 12 | [CVE-2026-34483](https://github.com/advisories/GHSA-rv64-5gf8-9qq8) | tomcat-embed-core | ✅ Fixed (10.1.31 → 10.1.54) |
| 13 | [CVE-2026-40477](https://github.com/advisories/GHSA-r4v4-5mwr-2fwr) | thymeleaf / thymeleaf-spring6 | ✅ Fixed (3.1.2.RELEASE → 3.1.4.RELEASE) |
| 14 | [CVE-2026-40478](https://github.com/advisories/GHSA-xjw8-8c5c-9r79) | thymeleaf / thymeleaf-spring6 | ✅ Fixed (3.1.2.RELEASE → 3.1.4.RELEASE) |
| 15 | [CVE-2024-57699](https://github.com/advisories/GHSA-pq2g-wx69-c263) | json-smart | ✅ Fixed (2.5.1 → 2.5.2) |
| 16 | [CVE-2026-24400](https://github.com/advisories/GHSA-rqfh-9r24-8c9r) | assertj-core | ✅ Fixed (3.25.3 → 3.27.7) |
| 17 | [CVE-2025-22235](https://github.com/advisories/GHSA-rc42-6c7j-7h5r) | spring-boot | ⚠️ Not applicable — no Spring Security / EndpointRequest.to() in project |

> **MEDIUM/LOW CVEs (10 total) in Tomcat**: Skipped per user-selected threshold of HIGH and above. These are addressed by the same `tomcat.version=10.1.54` upgrade — they are already fixed as a side-effect.

## Summary

- **Build status**: ✅ Passing
- **CVEs fixed**: 16/17 (HIGH and above)
- **Remaining**: 1 CVE not applicable (CVE-2025-22235 — Spring Security not used)

## Changes Made

- `pom.xml`: added 4 version properties to override Spring Boot BOM
  - `<tomcat.version>10.1.54</tomcat.version>` — fixes 12 HIGH/CRITICAL Tomcat CVEs
  - `<thymeleaf.version>3.1.4.RELEASE</thymeleaf.version>` — fixes 2 CRITICAL SSTI CVEs
  - `<json-smart.version>2.5.2</json-smart.version>` — fixes 1 HIGH DoS CVE
  - `<assertj.version>3.27.7</assertj.version>` — fixes 1 HIGH XXE CVE (test scope)
