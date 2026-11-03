## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-03T13:51:02Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/nfr-design/security-design.md > 2.5 進め方の手順 1（E2E 130 の移し先） | 現在の E2E 130 は、管理の仮の画面が呼ぶ `GET /api/admin/check` だけを 403 の見本に差し替え、返した回数も数えている（`ADMIN_CHECK_PATH`）。仮の画面を外すと、画面側でこの API を呼ぶ箇所が無くなるため、DSL の管理の画面へ移すだけでは見本の API と回数の数え方が合わなくなる | 手順 1 に、130 の見本の API を移し先の画面が実際に呼ぶ `/api/admin/` 配下の GET に替えること、再読み込みの回数の数え方を移し先に合わせることを書く（コード生成の計画に入れてもよい） | New |
| R-02 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/nfr-design/logical-components.md > 1 部品の一覧（`MenuFilterInput`・`NavNode`） | `MenuFilterInput`・`NavNode`・`NavNodeResponse` は record で、構成子の引数が増えると既存の呼び出し（`MenuFilterTest`・`MenuFilterPropertyTest`・`NavNodeTest`を含む 13 か所）がコンパイルできなくなる。設計は影響の範囲に触れていない | コード生成の計画で、既存のテストの構成子の呼び出しを洗い出して影響の範囲に入れる。引数が増えない補助の構成子を残すかどうかも決める | New |
| R-03 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/nfr-design/security-design.md > 2.1 の `NavigationDslSwapIT` の確かめ | 「差し替えの途中でも残った項目の種類が差し替え前のモデルと同じ」を確かめるには、差し替え前のモデルにビューを含め、差し替え後のモデルで同じ名前の種類が変わる（またはテーブルが無くなる）組を使う必要がある。既存の `menuWithRemovedTable` の見本（`T0` のテーブルの削除）だけだと、種類が `TABLE` か `null` かの違いしか見分けられない | 確かめの内容に、差し替え前にビューの項目を 1 つ含めること（種類が `VIEW` のまま残る確かめ）を書く | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| 検証の道具 | 段の定義に検証の道具の指定が無く実行していない（読み取りだけでコードを突き合わせた） | 下の確認事項をコードで直接確かめた |

確認した事項（すべて設計の主張と合う）:

- 「`null` は `NavigationService` の経路で起こらない」: `DslSemanticValidator.menu` が `SEMANTIC_MENU_UNKNOWN_TABLE` でメニューのテーブルの存在を検証し、深さの確かめだけが起動時の読み直しで外れる（`checkMenuDepth`）。現在の `NavigationService.menu` は `present.model()` から木を作っており、種類の操作も同じ `model` を閉じ込める設計と整合する。`DslModel.findTable` はスキーマ名の一致を探してテーブルの表を引くだけで、定数時間。
- `/admin`: `decideRoute` は、ルートが見つからなければログイン済みを `NOT_FOUND`、未ログインをログインへ振り分ける。`features/admin` の登録を外すと `/admin` は一致する経路を持たず、`/admin/users` なども `end: true` で一致しないため、設計の表のとおりになる。現在の E2E 090 は `/admin` でも権限が無い表示を期待しているため、書き換えの記述（実在の管理の道 1 つで 403 の表示、`/admin` は見つからない）が必要で、設計に書かれている。
- 許すアイコンの一覧は、画面の `ALLOWED_ICONS` が 24 個、サーバーの `allowed-icons.txt` がコメントを除いて 24 行で、「24 個でそろっている」と合う。
- 応答の増加の数え方: `"tableKind":"TABLE",` は 20、`"tableKind":"VIEW",` は 19、`"tableKind":null,` は 17 バイトで、設計の数字は正しい。1,000 項目で約 20 KB も合う。
- `Link` の中の `Card`: `Card` は `<div>`（`HTMLDivElement` の props）で、`<a>` の中に置ける。`data-testid="card"` が固定で、`rest` で上書きできる。
- テストの置き場: `MenuFilterTest`・`MenuFilterPropertyTest`・`NavigationRecordsToStringTest`・`NavigationCallCountTest`・`NavigationDslSwapIT`（`menuWithRemovedTable`）・`NavigationApiIT`・`NavigationSecretLeakIT`・`NavigationMenuAccessConsistencyIT` と、E2E の `support/overflow.ts` は実在する。
- 追跡: `traceability.json` の `upstream_ids` 16 件と `coverage` の行が一致し、`NFR2.3`・`NFR2.5` は前の Intent の番号として N/A にした理由が target に書かれている。OK の行の target は設計の文書の節に実在する。

### Summary

設計は実際のコードと合っており、Critical・Major はない。`null` の主張も検証の仕組みで裏付けられた。残るのは、E2E 130 の見本 API の移し先、record の構成子の影響の範囲、`NavigationDslSwapIT` の確かめの見本の 3 点で、いずれもコード生成の計画で扱える細かい指摘である。
