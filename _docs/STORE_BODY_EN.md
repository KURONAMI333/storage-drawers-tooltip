# Storage Drawers Tooltip

Adds the contents of a picked-up Storage Drawers drawer to its inventory tooltip — item and count, not just "something's inside."

Break a drawer that still has items in it and Storage Drawers keeps them sealed inside the item. The vanilla tooltip tells you the drawer's size and that it's holding something, but not what. This mod reads that sealed data and appends the missing line:

```
Oak Drawers 1x1
Holds 32 stacks per drawer
Contents sealed within
Iron Ingot [1x64]
Storage Drawers
```

Compacting drawers get one line per upgrade tier; the bracket format matches how Storage Drawers formats stack counts elsewhere.

No dependency on Storage Drawers — it reads the drawer's own saved data directly, so it loads fine even without Storage Drawers installed (it just has nothing to show). No config, no commands.

Client-side only — no need to install it on the server.

Free to use in any modpack. Source and issues: https://github.com/KURONAMI333/storage-drawers-tooltip
