# make-you-chic-ui への依頼（6）

依頼元: MasterSmith（Intent 261016-business-data-views、Deployment Execution の段）
置き場の決まり: `aidlc/spaces/default/memory/team.md` の Way of Working（依頼ごとに文面を Intent の記録に置く。上流の対応は上流のソースで確かめてから固定先を上げる）
今の固定先: `b0a1e79`（上流の `main` の先頭と同じ。2026-10-21 に `git fetch` で確かめた）

## 1. Vite を 8.2.4 以上に上げる（GHSA-587h-7wmj-h5pm、CVSS 7.5）

- **困っていること**: `package-lock.json` の `vite` が 8.2.1 で、GHSA-587h-7wmj-h5pm（直る版は 8.2 系なら 8.2.4、8.3 系なら 8.3.3）に当たる。MasterSmith の依存の脆弱性の関門（OSV-Scanner）は、成果物を作る道具（Vite など）の High 以上で統合を止めるため、2026-10-20 の CI（`develop` の 7f2575c）が `osvScan` で失敗し、`main` への取り込み（リリース）を止めている。
- **MasterSmith の側の影響**: `frontend` の Vite は 8.3.4 で当たらず、配備した WAR のビルドには使っていない。当たるのは、同梱版で make-you-chic-ui を組むときの道具だけ。
- **お願いしたい形**: `vite` を 8.2.4 以上（8.3.3 以上でもよい）に上げ、`package-lock.json` を作り直す。`package.json` の指定（`^8.2.1`）の下限も合わせて上げてもらえると、lockfile を作り直したときに戻らない。
- **取り込み**: 上流の `main` に入ったら、上流のソース（読み取りだけの `git show`）で lockfile の版を確かめ、固定先の更新の専用のコミットで上げる。前後の `package-lock.json` の差（増えた・変わったパッケージと、`hasInstallScript` の付いた項目が増えていないか）を同じ記録に書く。

## 2. あわせて上げてほしいもの（任意）

同じ CI で警告（統合は止めない、開発時だけ使うもの）になったもの。急がないが、Vite と一緒に lockfile を作り直すときに上がれば助かる。

| パッケージ | 今の版 | 指摘 |
|---|---|---|
| `undici` | 8.10.0 | GHSA-rfgv-xxqx-mfg5（7.5）・GHSA-vp8m-p9jh-q5pm（7.4）・GHSA-w293-vg96-wgc3（7.4）ほか 10 件 |
| `braces` | 3.0.3 | GHSA-vfj7-8cjw-p6xm（8.7） |
| `source-map-js` | 1.2.1 | GHSA-68fv-2mgg-jv7q（8.7） |
| `brace-expansion` | 5.0.9 | GHSA-6j4f-fj2g-mc7p（7.5）・GHSA-qhr7-859c-m2p7（7.5）・GHSA-q2hr-2g5m-vwhr（5.3） |
| `postcss-selector-parser` | 7.1.5 | GHSA-rj75-hqrm-r3gf（5.9） |
| `vite`（上の 1 と同じ） | 8.2.1 | GHSA-698v-4r3x-3vcv（6.3）・GHSA-r825-w8mm-5pxw（6.3） |

## 依頼しないもの

- 部品の振る舞いの変更（今回は依存の版だけ）
