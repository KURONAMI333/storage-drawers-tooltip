# Storage Drawers Tooltip

> Shows what a picked-up Storage Drawers drawer is holding — item and count — right in its inventory tooltip.

[![License: All Rights Reserved](https://img.shields.io/badge/License-All%20Rights%20Reserved-lightgrey.svg)](LICENSE)

---

## Why Storage Drawers Tooltip?

アイテムが入ったままの drawer を壊すと、Storage Drawers はその中身を drawer アイテム自身の中に封印する。バニラの tooltip はサイズと「何か入っている」ことまでは教えてくれるが、何が何個かは表示しない。このMODは drawer アイテムが持つ保存データをそのまま読み、足りない行を tooltip に追記する:

- 通常の drawer は「アイテム名 [個数]」の1行
- compacting drawer（圧縮ドロワー）は tier ごとに1行、`[+n]` 表記はゲーム内の他の個数表示と揃えてある

---

## Features

- 🔍 **拾う前に中身が分かる** — インベントリに入れたまま、tooltip を見るだけで確認できる
- 📦 **compacting drawer にも対応** — 圧縮された複数 tier の在庫を tier ごとに1行ずつ表示
- 🖼️ **アイテムアイコン付き** — 各行の頭にそのアイテムのアイコンが並ぶ
- 🧩 **Storage Drawers 本体への依存なし** — drawer アイテム自身の保存データを直接読むため、Storage Drawers が入っていない環境でも単体で読み込める（表示するものが無いだけ）
- ⚙️ **設定項目なし** — 導入するだけで効く

---

## Installation

1. NeoForge または Fabric（Minecraft 1.21.1）を導入
2. `storage_drawers_tooltip-neoforge-1.21.1-0.1.0.jar`（または `-fabric-`）を `mods/` フォルダに入れる

クライアント専用の MOD なので、サーバー側に入れる必要はない。

---

## Configuration

この MOD に設定項目は無い。導入した時点の挙動がそのまま体験になる。

---

## Compatibility

NeoForge・Fabric、Minecraft 1.21.1 に対応。Storage Drawers が入っていない環境でも読み込めるが、表示する中身が無いため何も追加されない。

---

## Bug Reports / Feature Requests

GitHub Issues に投げてください: [Issues](https://github.com/KURONAMI333/storage-drawers-tooltip/issues)

---

## License

[All Rights Reserved](LICENSE) — modpack への同梱は自由（許可・クレジット不要）。単体での再配布と改変版の配布は不可。ソースは読めるように公開しています。

---

## Credits

- Author: KURONAMI
