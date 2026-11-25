## What changes for the user

<!-- One or two sentences explaining the user-facing change. Link the issue: Fixes #123 -->

## How it works

<!-- Summarize the approach and anything a reviewer should inspect first. -->

## Checklist

- [ ] One focused change; unrelated changes are in separate pull requests
- [ ] Follows the security & privacy rules in `CONTRIBUTING.md` (new outbound hosts documented in `PRIVACY.md` and toggleable; new `Lan.cs` paths or computer-control options explained above)
- [ ] Secrets stay out of `ToPublic()` and logs; processes use `ArgumentList` (no shell); DOM avoids untrusted `innerHTML`
- [ ] Config/settings changes cover `Config.cs`, all three `config.default*.json` files, and `settings.html` (with migration if defaults changed)
- [ ] Android changes preserve `minSdk = 21` compatibility (`./gradlew lintDebug testDebugUnitTest` passes)
- [ ] Checks run locally: `python tools/check-release.py`, `python tools/test-winget-manifests.py`, `dotnet test agent/PhoneBox.Agent.Tests --nologo`, and `npx playwright test` (in `tools/ui-tests`)
- [ ] `VERSION` and `CHANGELOG.md` left untouched for routine PRs

## How I tested it

<!-- Commands executed, OS / phone / Android / WebView versions tested by hand, and before/after screenshots for UI changes. -->
