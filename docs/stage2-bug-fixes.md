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

## BF04 - Close the microphone after cancelled native startup

- **Before:** `close()` could detach and close a Java Sound line while `open()` was still initializing it. A late native open/start could then leave the detached line recording, with no reference left for cleanup.
- **Change:** serialize native open/start and close within MicrophoneDevice. VoiceInputService already performs these operations on workers, preserving nonblocking controller/UI stopping.
- **Files:** `src/main/java/com/sayswear/voice/MicrophoneDevice.java`; `src/test/java/com/sayswear/voice/MicrophoneDeviceTest.java`.
- **Verification:** a controlled TargetDataLine blocks inside native open while stop arrives. Before the fix the probe ends open and recording; afterward the regression verifies both are false. The new regression and all six voice-service tests pass; no microphone hardware is opened by these tests.
- **Review:** public microphone methods and relationships are unchanged. The package-private line factory only permits deterministic lifecycle testing.

## BF05 - Refresh the interactive CLI before the next turn is missed

- **Before:** the in-place terminal dashboard retained a one-second throttle from the old scrolling renderer. At speed 7 the player moved seven units between ordinary frames, making the narrow corridor difficult to follow; the recorded CLI demonstration also identified this limitation.
- **Change:** refresh the supported in-place dashboard at up to 10 Hz. Plain interactive fallback retains its one-second throttle, and redirected/scripted sessions still suppress ordinary periodic maps.
- **Files:** `src/main/java/com/sayswear/view/CliView.java`; `src/test/java/com/sayswear/view/CliTerminalTest.java`.
- **Verification:** a real JLine terminal and independent JediTerm screen emulator verify that a position change appears at 250 ms while the partially typed command remains intact on the same row, then completes correctly. All eight CLI tests pass, including the existing fallback, editing, resize and terminal cleanup checks.
- **Review:** terminal movement feedback updates more frequently without replacing the input editor or adding scrolling frames.
