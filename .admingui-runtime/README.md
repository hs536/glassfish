# Admin console runtime on main and 9.0 (CI only)

This branch only crawls the admin console built from main and from the upstream 9.0 branch with the same tool and
test domain. The upstream repository is only checked out. This branch is not meant to be merged or proposed upstream.

- `.github/workflows/admingui-runtime-compare.yml` is a copy of tools/ci/admingui-runtime-compare.yml in the planning
  repository; `runtime/` and `screens.yaml` are copies of tools/runtime and inventory/screens.yaml.
