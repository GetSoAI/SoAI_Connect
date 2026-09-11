# SoAI Connect Security Policy

## Single point of contact

`security@soai.to` is the single point of contact for reporting vulnerabilities in SoAI Connect and in SoAI itself. The same contact is published at <https://soai.to/legal-notices/>. Roberto Martini is the manufacturer of SoAI for the purposes of Regulation (EU) 2024/2847.

## Reporting a vulnerability

Report suspected vulnerabilities privately to `security@soai.to` with the subject `SoAI security report`.

Include the client version, the Android version and device, the SoAI server version, prerequisite access, reproduction steps, impact, and any proof-of-concept material that can be shared safely. Do not include live credentials, private user data, or destructive payloads. If sensitive attachments are necessary, request a secure transfer method first.

Do not publish an undisclosed vulnerability or open a public issue containing exploit details. Good-faith testing must be limited to devices and servers you own or are explicitly authorized to test.

## Scope

This repository covers the Android client only. A vulnerability in the SoAI server — its API, its web interface, or its orchestration layer — belongs to the SoAI project. Report it to the same address; say which side you believe is affected, and if you are unsure, report it anyway rather than splitting it across two messages.

Because the client is a shell around the server's own web interface, findings that reduce to server behaviour rendered inside a WebView are handled as server issues.

## Handling and disclosure

Reports are validated and prioritized by exploitability and impact. Remediation and disclosure timing is coordinated with the reporter, and reporters are credited in the advisory on request. There is no bug bounty.

The full policy — acknowledgement and assessment targets, the coordinated-disclosure timeline, the declared security support period for SoAI, and the software bill of materials — is published in `SECURITY.md` in the SoAI repository at <https://github.com/GetSoAI/SoAI> and at <https://soai.to/legal-notices/>. It governs this client too, and is kept in one place so the two cannot drift apart.

The supported baseline is the latest published SoAI Connect release. Older packages may be asked to reproduce against it before investigation continues.

## Verifying a package

Every published APK ships with a `.sha256` sidecar and is signed with APK Signature Scheme v2 and v3, with no v1 JAR signature. `README.md` records the expected signing certificate. A package that fails either check did not come from this project; report it rather than installing it.
