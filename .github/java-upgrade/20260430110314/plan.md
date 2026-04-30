# Security Fix Plan (20260430110314)

- **Project**: simplecd
- **Generated**: 2026-04-30 11:03:14
- **Total CVEs found**: 27 across 5 dependencies
- **Deprecated API usages found**: N/A (scope: cve)

## CVE Vulnerabilities

### 1. `org.apache.tomcat.embed:tomcat-embed-core` — 10.1.31 → 10.1.54 ✅ Upgrade

Fix via: add `<tomcat.version>10.1.54</tomcat.version>` property in `pom.xml` (Spring Boot BOM override)
Also applies to `tomcat-embed-el` and `tomcat-embed-websocket` (same property).

| Severity | CVE | Description |
|----------|-----|-------------|
| CRITICAL | [CVE-2025-24813](https://github.com/advisories/GHSA-83qj-6fr2-vhqg) | RCE/info disclosure via partial PUT (fix: 10.1.35) |
| CRITICAL | [CVE-2026-29145](https://github.com/advisories/GHSA-95jq-rwvf-vjx4) | CLIENT_CERT authentication bypass (fix: 10.1.53) |
| HIGH | [CVE-2024-50379](https://github.com/advisories/GHSA-5j33-cvvr-w245) | TOCTOU race condition during JSP compilation, RCE (fix: 10.1.34) |
| HIGH | [CVE-2024-56337](https://github.com/advisories/GHSA-27hp-xhwr-wr2m) | TOCTOU race condition (incomplete fix for CVE-2024-50379) (fix: 10.1.35) |
| HIGH | [CVE-2025-48988](https://github.com/advisories/GHSA-h3gc-qfqq-6h8f) | DoS in multipart upload via resource exhaustion (fix: 10.1.42) |
| HIGH | [CVE-2025-52520](https://github.com/advisories/GHSA-wr62-c79q-cv37) | DoS via integer overflow bypassing multipart size limits (fix: 10.1.43) |
| HIGH | [CVE-2025-53506](https://github.com/advisories/GHSA-25xr-qj8w-c4vf) | DoS via excessive HTTP/2 streams (fix: 10.1.43) |
| HIGH | [CVE-2025-48989](https://github.com/advisories/GHSA-gqp3-2cvr-x8m3) | Improper resource shutdown (made-you-reset attack) (fix: 10.1.44) |
| HIGH | [CVE-2025-55752](https://github.com/advisories/GHSA-wmwf-9ccg-fff5) | Path traversal / security constraint bypass via rewrite (fix: 10.1.45) |
| HIGH | [CVE-2026-24734](https://github.com/advisories/GHSA-mgp5-rv84-w37q) | OCSP response not verified, certificate revocation bypass (fix: 10.1.52) |
| HIGH | [CVE-2026-34487](https://github.com/advisories/GHSA-x4m4-345f-5h5g) | Kubernetes bearer token logged (fix: 10.1.54) |
| HIGH | [CVE-2026-34483](https://github.com/advisories/GHSA-rv64-5gf8-9qq8) | Improper escaping in JsonAccessLogValve (fix: 10.1.54) |
| MEDIUM | [CVE-2025-31650](https://github.com/advisories/GHSA-3p2h-wqq4-wf4h) | DoS via invalid HTTP priority header (memory leak) (fix: 10.1.40) |
| MEDIUM | [CVE-2025-49125](https://github.com/advisories/GHSA-wc4r-xq3c-5cf3) | Security constraint bypass for pre/post-resources (fix: 10.1.42) |
| MEDIUM | [CVE-2025-49124](https://github.com/advisories/GHSA-42wg-hm62-jcwg) | Untrusted search path in Windows installer (fix: 10.1.42) |
| MEDIUM | [CVE-2025-66614](https://github.com/advisories/GHSA-fpj8-gq4v-p354) | Client certificate verification bypass via SNI mismatch (fix: 10.1.50) |
| MEDIUM | [CVE-2026-24733](https://github.com/advisories/GHSA-qq5r-98hh-rxc9) | Security constraint bypass with HTTP/0.9 (fix: 10.1.50) |
| MEDIUM | [CVE-2026-25854](https://github.com/advisories/GHSA-9m3c-qcxr-9x87) | Open redirect via LoadBalancerDrainingValve (fix: 10.1.53) |
| MEDIUM | [CVE-2026-34500](https://github.com/advisories/GHSA-24j9-x2wg-9qv6) | CLIENT_CERT auth bypass with FFM (fix: 10.1.54) |
| LOW | [CVE-2025-31651](https://github.com/advisories/GHSA-ff77-26x5-69cr) | Rewrite rule bypass for security constraints (fix: 10.1.40) |
| LOW | [CVE-2025-46701](https://github.com/advisories/GHSA-h2fw-rfh5-95r3) | CGI security constraint bypass (fix: 10.1.41) |
| LOW | [CVE-2025-61795](https://github.com/advisories/GHSA-fpj8-gq4v-p354) | Temp uploaded parts not cleaned up (DoS risk) (fix: 10.1.47) |
| LOW | [CVE-2025-55754](https://github.com/advisories/GHSA-vfww-5hm6-hx2j) | ANSI escape sequence injection in console logs (fix: 10.1.45) |

### 2. `org.thymeleaf:thymeleaf` / `thymeleaf-spring6` — 3.1.2.RELEASE → 3.1.4.RELEASE ✅ Upgrade

Fix via: add `<thymeleaf.version>3.1.4.RELEASE</thymeleaf.version>` property in `pom.xml` (Spring Boot BOM override)

| Severity | CVE | Description |
|----------|-----|-------------|
| CRITICAL | [CVE-2026-40477](https://github.com/advisories/GHSA-r4v4-5mwr-2fwr) | SSTI via restricted scope bypass in Thymeleaf expressions |
| CRITICAL | [CVE-2026-40478](https://github.com/advisories/GHSA-xjw8-8c5c-9r79) | SSTI via improper neutralization of syntax patterns |

### 3. `net.minidev:json-smart` — 2.5.1 → 2.5.2 ✅ Upgrade (test scope)

Fix via: add `<json-smart.version>2.5.2</json-smart.version>` property in `pom.xml` (Spring Boot BOM override)

| Severity | CVE | Description |
|----------|-----|-------------|
| HIGH | [CVE-2024-57699](https://github.com/advisories/GHSA-pq2g-wx69-c263) | DoS via uncontrolled recursion on deeply nested JSON input |

### 4. `org.assertj:assertj-core` — 3.25.3 → 3.27.7 ✅ Upgrade (test scope)

Fix via: add `<assertj.version>3.27.7</assertj.version>` property in `pom.xml` (Spring Boot BOM override)

| Severity | CVE | Description |
|----------|-----|-------------|
| HIGH | [CVE-2026-24400](https://github.com/advisories/GHSA-rqfh-9r24-8c9r) | XXE vulnerability in isXmlEqualTo() / XmlStringPrettyFormatter |

### 5. `org.springframework.boot:spring-boot` — 3.3.5 — ⚠️ Not Applicable

| Severity | CVE | Description |
|----------|-----|-------------|
| HIGH | [CVE-2025-22235](https://github.com/advisories/GHSA-rc42-6c7j-7h5r) | EndpointRequest.to() creates wrong matcher if endpoint not exposed |

> **Not applicable**: CVE-2025-22235 only affects apps using Spring Security with `EndpointRequest.to()`. This project has no Spring Security or Spring Actuator dependency.

## Options

- Minimum CVE severity to fix: HIGH and above
- Working branch: `appmod/security-fix-20260430110314`
