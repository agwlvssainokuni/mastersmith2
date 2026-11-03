## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-03T13:33:43Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/nfr-requirements/security-requirements.md > NFR1.3.1 の確かめ方 | 「DSL 差し替え後の `null`」を `NavigationApiIT` に置いているが、DSL の差し替えを扱う既存のテストは `NavigationDslSwapIT`。契約の要約（C5）は「DSL に無いテーブルを指す項目は今の絞りでメニューに出ないため `tableKind` も出ない」と書き、機能設計 D5 は「権限の写しだけが前の DSL を見るときは `null`」と書く。この `null` に実際に届く状況を作れるテストの置き場が、要件の文面からは決まらない | 確かめ方に、権限の写しと適用中の DSL が食い違う状況を作るテスト（`NavigationDslSwapIT` に足すか、`NavigationApiIT` で写しを差し替えるか）を名指しし、`null` に届く条件を 1 行で書く | New |
| R-02 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/nfr-requirements/performance-requirements.md > NFR2.1.1 の確かめ方 | 「項目ごとに木を走査し直さない」を、既存の `NavigationCallCountTest` で確かめるとしているが、このテストが数えるのは `current()`・`snapshotFor`・解決の回数だけで、`findTable` の呼び出しは数えない。`DslModel.findTable` は 1 スキーマの一覧の探索と名前引きなので定数時間だが、その性質を確かめるテストが無い | 数えられるのは読み出しの回数までと明記するか、種類を返す操作の呼び出しが移れる項目 1 つにつき 1 回であることを `MenuFilterTest` で数える確かめを足す | New |
| R-03 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/nfr-requirements/performance-requirements.md > NFR2.1.2 の応答の大きさの見積もり | 1 項目あたりの増加を約 22 バイト（フォルダ約 18 バイト）としているが、数えると `"tableKind":"TABLE",` は 20 バイト、`VIEW` は 19、`null` は 17 バイト。「約」と 1,000 項目で約 22 KB の結論は実害のない誤差で、1,000 項目なら約 20 KB 前後 | 数字を実際の値（20・19・17 バイト、1,000 項目で約 20 KB）に直すか、「最大でも 22 バイト以下」と書く | New |
| R-04 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/nfr-requirements/traceability.json > NFR1.3・NFR2.2 | 上流の NFR1.3 は「印は見られる項目にだけ付く」だけだが、NFR1.3.3〜NFR1.3.5（信頼できない入力・画面で権限を足さない・廃止で権限の確かめを弱めない）も NFR1.3 の枝として OK にしている。NFR2.2.3（ロゴ）も NFR2.2 の枝。番号の付け方の節で理由は書かれているが、追跡の表からは上流のどの要件が追加かが読めず、後の段で NFR1.3 が 5 本とも上流の要件と誤読されうる | traceability.json の target に「NFR1.3.3〜NFR1.3.5・NFR2.2.3 はこの単位が足した要件」と書き添える | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| 実在の確認（読み取りのみ） | `NavigationCallCountTest`・`NavigationConnectionUsageIT`・`NavigationQueryExposureIT`・`NavigationSecretLeakIT`・`NavigationRecordsToStringTest`・`NavigationApiIT`・`NavigationAuthorizationApiIT`・`NavigationMenuAccessConsistencyIT`・`NavigationUntrustedInputIT`・`MenuFilterPropertyTest`・`allowedNavIcons.test.ts`・`AdminAccessIT`・`AdminPathBoundaryIT` ほか管理の 403 のテストが実在 | 確かめ方に挙げたテストの名前は実在し、重大な食い違いは無い |
| 上流 NFR の網羅 | upstream_ids の 14 個がすべて traceability.json に行を持つ。N/A の 6 個（NFR1.1・1.2・3.3・4.1・5.1・5.2）は他の単位の持ち主を書き、理由は妥当 | 漏れ無し |
| 技術の主張 | `Card` は `HTMLAttributes<HTMLDivElement>` でリンクにできず、`AppShell` と `Topbar` に `logo` の口があり、`DslTable.view` と `DslModel.findTable` も実在 | 新しい依存を足さない主張は正しい |

### Summary

セキュリティの要件は、印が見られる項目にだけ付くこと、信頼できない入力の扱い、画面で権限を足さないこと、廃止後もサーバー側の管理の確認 API と 403 の確かめを残すことを、実在するテストに結び付けて測れる形で書いており、前の Intent の p95 1 秒も緩めていない。指摘は確かめ方の置き場・数え方・見積もりの数字・番号の注記に関する Minor 4 件だけで、実装を妨げるものは無い。
