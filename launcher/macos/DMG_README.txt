AI Training Platform Launcher for macOS

Requirements
- macOS 13 or later
- A local AI_TT_PLATFORM project checkout
- JDK 17, Maven, Node.js, and a running MySQL service

First launch
1. Open AITrainingPlatformLauncher.app from this disk image.
   If Gatekeeper blocks this unsigned development build, Control-click the app,
   choose Open, then confirm Open in the dialog.
2. When prompted, select the AI_TT_PLATFORM project root containing backend,
   fronternd, and engines/mmdet_run/mmdet_runner_srv.
3. The launcher remembers the selected project folder for future launches.

The launcher starts the local services from the selected project folder. Keep
that project checkout available while using the launcher. MySQL is managed by
the operating system and is not stopped by the launcher.
