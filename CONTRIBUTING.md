# Contributing to SoAI Connect

Thank you for wanting to help. Please read this before opening a pull request, because SoAI Connect does not work the way most repositories on GitHub do.

## Code contributions are not accepted

**Pull requests are closed without review and are never merged.** This is not a judgement about your code. It is a structural decision about who owns SoAI's first-party source: `LICENSE.md` section 10 of the SoAI license states that outside code contributions are not accepted unless and until a contribution policy and rights process is published, and no such process is open today. That single-party ownership is what makes SoAI's Change Date promise — every numbered SoAI Core release converting to the MIT License four years after its first public distribution — deliverable at all.

So please do not spend your time on a patch for this repository.

## Fork it instead

This client is MIT-licensed precisely so you do not have to ask. You may copy it, change it, rename it, publish it, and ship it under your own terms, including as the basis for a client of your own. The Licensor claims nothing in what you build from it.

Two limits, both from the license rather than from preference: the MIT grant conveys no right in the SoAI name, logos, or product identity, so publish your fork under your own name; and it grants no right to run or distribute SoAI itself, so connecting your client to a SoAI deployment still requires rights applicable to that deployment.

## What is genuinely welcome

**Bug reports.** The most useful thing you can send, at <https://github.com/GetSoAI/SoAI_Connect/issues>. Include the client version, the Android version and device, the SoAI server version, what you did, what happened, and what you expected. A reliable reproduction is worth far more than a proposed fix.

**Feature requests and design feedback.** Describe the problem you are trying to solve and the outcome you want, rather than the implementation.

**Documentation corrections.** Wrong, stale, or confusing documentation is a defect. Point at the exact place and say what it should say.

**Translation corrections.** The app ships user-facing strings in every locale under `android/app/src/main/res/values-*/`. If a string reads wrong in your language, say which string and what it should be — that is a report, not a patch, and it is welcome.

**Security reports.** Do not open a public issue. Follow `SECURITY.md`.

## About code in issues

Please describe the problem rather than supply a patch. A short snippet that makes a bug clearer is fine and welcome. Keep it to the minimum the explanation needs, and understand that including it is not a request to merge it and creates no claim to the resulting code.

## Conduct

Be straightforward and civil. Technical disagreement is fine and useful; personal attacks, harassment, and spam are not, and will be moderated.
