# Admin console baseline (CI only)

This branch only records how the current admin console looks and behaves on a fixed Linux environment. It is not
meant to be merged or proposed upstream.

- `.github/workflows/admingui-baseline.yml` builds GlassFish, creates a disposable test domain, crawls the console
  with Playwright for Java and uploads DOM, accessibility snapshots and screenshots as an artifact.
- `runtime/` and `screens.yaml` are copies of the observation tool and the screen inventory maintained in the
  planning repository (tools/runtime, inventory/screens.yaml).
