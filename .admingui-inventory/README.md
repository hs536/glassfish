# Admin console inventory on Linux (CI only)

This branch only checks that the inventory extractor gives the same result on Linux as on Windows. It is not meant
to be merged or proposed upstream.

- `.github/workflows/admingui-inventory.yml` builds GlassFish at the commit the inventories were generated from,
  regenerates the inventories and compares them with `expected/`.
- `extractor/` and `expected/` are copies of the extractor and the generated inventories maintained in the planning
  repository (tools/inventory/extractor, inventory/*.yaml).
