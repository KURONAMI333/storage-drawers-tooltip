Adds the contents of a picked-up Storage Drawers drawer to its inventory tooltip — item and count, not just "something's inside."

Break a drawer that still has items in it and Storage Drawers keeps them sealed inside the item. The vanilla tooltip tells you the drawer's size and that it's holding something, but not what. This mod reads that sealed data and appends the missing line:

![Tooltip for an Oak Drawers 1x1 holding an iron ingot, showing the added "Iron Ingot [1x64]" line](https://raw.githubusercontent.com/KURONAMI333/storage-drawers-tooltip/main/_docs/images/tooltip-standard-drawer.png)

Compacting drawers get one line per upgrade tier; the bracket format matches how Storage Drawers formats stack counts elsewhere.

No dependency on Storage Drawers — it reads the drawer's own saved data directly, so it loads fine even without Storage Drawers installed (it just has nothing to show). No config, no commands.

Bugs and questions: comment on the CurseForge page, or DM @kuronami333 on X.

All Rights Reserved. Modpack inclusion is allowed without permission or credit. Source: https://github.com/KURONAMI333/storage-drawers-tooltip
