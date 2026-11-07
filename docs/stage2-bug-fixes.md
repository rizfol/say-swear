# Stage 2 Defect Fixes

These November 7, 2026 changes address defects reproduced during the requested Stage 2 audit. Each fix includes its regression coverage in the same commit. The student requested the audit and separate commits; detailed student source review remains to be recorded. Temporary reproduction files remain local under ignored `.tools/`.

## BF01 - Report startup failure to the calling shell

- **Before:** an unknown argument, missing argument value, or invalid timeout printed an error and returned process exit status 0. Launch scripts could therefore treat failed startup as success.
- **Change:** `Main.main()` exits with status 1 after reporting a configuration or CLI I/O error. Successful help and normal CLI shutdown retain status 0.
- **Files:** `src/main/java/com/sayswear/app/Main.java`; `src/test/java/com/sayswear/app/LauncherProcessTest.java`; test runtime-classpath configuration in `build.gradle`.
- **Verification:** subprocess regressions check three invalid invocations plus successful help and real demo CLI `/quit`. Both launcher test methods pass. The invalid-argument regression failed against the original exit behavior.
- **Review:** this changes shell error reporting, with no movement or AI behavior change.

## BF02 - Keep help accessible with broken configuration

- **Before:** an invalid `DECISION_TIMEOUT_MS` or endpoint prevented `--help` from displaying the instructions needed to repair configuration.
- **Change:** `Main.main()` handles explicit `--help`/`-h` before parsing gameplay configuration.
- **Files:** `src/main/java/com/sayswear/app/Main.java`; `src/test/java/com/sayswear/app/LauncherProcessTest.java`.
- **Verification:** a subprocess regression failed before the fix and passes afterward for both help aliases and help combined with an invalid timeout argument. All three launcher test methods pass, including the BF01 exit-status checks.
- **Review:** explicit help takes precedence over runtime configuration validation; normal startup still validates its settings.

## BF03 - Preserve the full timed movement across frame boundaries

- **Before:** `ApplicationController.advance()` expired timers before simulating the frame. A 100 ms RIGHT hold moved 0.35 units in 50 ms frames and 0.63 in 10 ms frames; its correct distance at speed 7 is 0.70. A 200 ms hold similarly moved 1.05 or 1.33 rather than 1.40.
- **Change:** query `secondsUntilExpiry()` through ControlManager/ActionExecutor, simulate each portion up to that boundary, then expire/release the command. Publish a final neutral snapshot when expiry lands exactly at the frame end. Fall/finish cleanup still stops the current lifecycle tick.
- **Files:** `ApplicationController.java`, `ControlManager.java`, `ActionExecutor.java`, `TimedPressCommand.java`, and `ApplicationControllerTest.java` in their existing packages.
- **Verification:** nine duration/frame combinations verify exact distance for 100/200/400 ms; additional checks cover a frame ending exactly at expiry, falling before expiry, stale inference after that fall, and full-distance timed repeat. Controller and control tests pass.
- **Review:** small timed actions now move their full intended distance. SD04's final tick subsection still depicts the old timer-before-simulation order and needs a matching UMLet update: simulate a slice bounded by the next expiry, then advance/release its timer. Timer ownership and the five pattern roles are unchanged.
