# Auto Launch AA

Detects your car's Bluetooth connection and prompts you to open Android Auto (wireless AA).
Android does not allow any app to force-start Android Auto: on Android 10+ you get a
high-priority notification to tap. Direct launch is optional and usually blocked.

Build: push to GitHub (Actions builds the debug APK), or open in Android Studio.
Setup: grant permissions -> pick car -> turn on the prompt switch -> check Readiness rows
-> exempt app from battery optimization -> Test connection.

Status: NOT compiled or run on a device. Needs a build and a test on real phone + car.
