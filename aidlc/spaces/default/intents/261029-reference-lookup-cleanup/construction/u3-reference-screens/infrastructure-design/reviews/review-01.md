## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-10-30T08:13:36Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/infrastructure-design/cicd-pipeline.md > 5節 配備と戻し（戻しの注意） | 画面の静的ファイルは同じ実行可能 WAR（`backend/build.gradle.kts` の `bootWar` が `frontend/dist` を同梱）に入り、イメージも 1 つなので、U3 だけを戻す・U1/U2 のサーバー側だけを残す、という戻し方は成り立たない。「U1・U2 のサーバー側を同時に戻すかは Intent 全体の手順で決める」は選べるかのように読める。U1/U2 側に移行や `.env` の変更があるとイメージだけの戻しでは足りなくなる点が、U3 の文書には出てこない。 | 戻しは WAR とイメージの単位で U1・U2・U3 が必ず一緒に戻ると書き直し、U1/U2 の移行・`.env` の差を踏まえた戻しの手順は Deployment Pipeline で一本にまとめると明記する。 | New |
| R-02 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/infrastructure-design/infrastructure-specification.md > 2節 E2E の共有の DSL・上流との差 | 180 の `flowDsl` を `support/` へ移して参照の表を足すと、180 が投入・適用する DSL の中身が変わる（現状は `flowDsl` が 180 のファイル内にあり、`/api/admin/dsl/preview`・`apply` で適用している）。それでも「180 の振る舞いは変えない」とだけ書いている。190・195 が「適用されていなければ適用する」と判定する方法（印・ハッシュの比較など）と、DSL の適用が全 E2E で共有される状態を書き換えること（初期管理者の権限や見本の DSL には触れない）の説明もない。 | 共有 DSL に足す内容が 180 の確かめ（表・メニューの件数や名前）に影響しないことを Code Generation の確認項目に入れる。「適用済みか」の判定方法を 1 行で決める。 | New |
| R-03 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/infrastructure-design/infrastructure-specification.md > 4節 業務データの画面の chunk の測り方 | 測りは `.js` の gzip だけで、`check-bundle-size.mjs` と同じ範囲に揃えている。U3 が部品ごとの CSS を足すと、遅延の chunk に付く `.css` は数えられない。起点を `isDynamicEntry` かつキーが `src/features/tables/` で始まる項目としているが、manifest（`vite.config.ts` の `build.manifest: true`）のキー形を実際のビルドで確かめた記述がない。 | 一時の手順で `.css`（manifest の `css` 配列）を別枠で記録する。起点のキーが期待どおり取れることを最初の測定で確かめ、取れなければ手順を直すと書く。 | New |
| R-04 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/infrastructure-design/infrastructure-specification.md > 3節 共有基盤・monitoring-design.md > 1節 | 作成・更新の拒否を「409 `REFERENCE_NOT_FOUND`」と書いている。contract-summary.md（C3）では問題の code が `BUSINESS_REFERENCE_NOT_FOUND`、`REFERENCE_NOT_FOUND` は項目の誤りの `reason` として別に出てくる。 | 文書内で code と `reason` を区別して書く。 | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| 読み取りでの確かめ（`backend/build.gradle.kts` の `bootWar`） | `from(frontend/dist) into("WEB-INF/classes/static")` は 362〜366 行 | 文書の「363〜366 行」は実質一致。同梱の主張は正しい |
| 読み取りでの確かめ（`frontend/vite.config.ts`・`frontend/scripts/check-bundle-size.mjs`） | `build.manifest: true` が有効。`check-bundle-size.mjs` は `isEntry` から静的な `imports` をたどる | manifest を使う測り方は成り立つ（指摘 3 の CSS の扱いを除く） |
| 読み取りでの確かめ（`docker/e2e/compose.targetdb-port.yaml`・`build.gradle.kts` の `mailpitInfoUrl`・`checkE2eTargetDb`・`prepareE2eTargetDb`） | 見本の PostgreSQL を `127.0.0.1:${E2E_TARGETDB_PORT:-15432}` に開ける。Mailpit は `8025` に届くかを確かめる。表の SQL を流した後で `GRANT ... ON ALL TABLES IN SCHEMA` を実行し、実行ごとの `e2e_biz_<印>` と一時のロールを作る | 記述どおり。足す表にも読み取り・書き込みの権限が付く |
| 読み取りでの確かめ（`frontend/e2e/180-business-data-flow.e2e.ts`） | `flowDsl` は 180 のファイル内にあり、preview と apply で適用している。`support/` には共有の DSL がない | 機能設計 D39 との差を正しく書いている（指摘 2 のとおり波及の確かめは補う） |
| U1 の `monitoring-design.md` との突き合わせ | パネル id 46・47、警報 `ms-biz-target-db`、`uri=~"/api/tables/.*"` の式は U3 の記述と一致 | 監視の対応は一貫している |
| `traceability.json`（infrastructure-design と nfr-design の ID 比較） | NFR1.41〜1.47、2.41〜2.45、4.41〜4.45、5.41〜5.43、6.41・6.42、7.41、9.41 の 24 件が両方に過不足なく並ぶ | 網羅されている |

### Summary

U3 は画面の静的ファイルだけを変える単位で、WAR への同梱、manifest による chunk の測り方、E2E の前提（見本の PostgreSQL・Mailpit・実行ごとのスキーマと一時のロール）は読み取りで確かめた現行の構成と合っている。team.md の E2E の決まり（自作の前提・初期管理者に触れない・流れ 1 本と数えない axe 用の 195）にも沿っている。直すべき点は、戻しが U1・U2・U3 で分けられないこと、共有 DSL の移動の影響の確かめ、chunk の CSS の扱い、code 名の表記の 4 つで、いずれも Minor であり、READY とする。
