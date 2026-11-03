## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-03T13:26:46Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/functional-design/functional-spec.md > D24 | 「どの利用者でも `/admin` は NOT_FOUND」と書くが、`decideRoute` は登録の道に合わない道を、ログイン済みなら NOT_FOUND、未ログインならログインへ送る（`decideRoute.ts` の `loggedIn ? NOT_FOUND : toLogin`）。未ログインの利用者には当てはまらない | 「ログイン中の利用者は NOT_FOUND、未ログインはほかの存在しない道と同じくログインへ」と直し、AC3.3.2 の確かめ（単体・E2E）にもその区別を入れる | New |
| R-02 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/functional-design/functional-spec.md > D5・D30 | D5 の「DSL にテーブルが見つからない」は、`NavigationService` が権限の写し（自前の DSL を持つ）と適用中の DSL を別々に読むために起きる食い違い。D5 内の「DSL に無い組は主権限 NONE」と並べると、起こらないように読める。D30 の「DSL 差し替え後の `null`」は、`NavigationBarrier.afterDslRead` で差し替えを挟まないと再現できない。同じ食い違いで、写しの DSL では TABLE、適用中の DSL では VIEW、という種類のずれも起こりうるが触れていない | D5 に、起きる条件（写しと適用中の DSL の食い違い）を 1 文で書く。D30 の結合テストはバリアで差し替えを挟む方法と明記する。種類のずれは「適用中の DSL を正」と一言添える | New |
| R-03 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/functional-design/functional-spec.md > D26・未解決の点 | E2E 130 を移す先の API（DSL の管理の画面の GET）が未決のまま。`AdminForbiddenProvider` は `/api/admin/` 配下の 403 だけを拾うため、移し先の GET がその配下にあるかで 130 の成立が決まる。検査の移しは team.md が必須とする | コード生成の計画で、移し先の GET が `/api/admin/` 配下であることを確かめる手順を最初の項目に入れる（設計の段で確かめられるなら、今確かめて D26 に道を書く） | New |
| R-04 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/functional-design/frontend-components.md > 2.5 HomeCard | `Link`（`a`）の中に `Card`（`div`）を入れる。ブロック要素を `a` に入れるのは HTML5 では許されるが、`Card` の内部にフォーカスできる要素やロールが入ると読み上げが二重になる。上流 `Card` の既定のロールの有無が設計に書かれていない | コード生成で `Card` のソースを読み、`Card` が持つロール・タブ停止が無いことを確かめて、D21 の「各カードは 1 つのリンク」を `vitest-axe` で確かめる | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| 追跡の検査 | 実行せず（ui の単位は target が D 番号を指す前の Intent の約束で、BRx.y が無い失敗は既知） | traceability.json の AC は D・W 番号に目で突き合わせた。D1〜D32・W1〜W5 は実在し、内容も合う。N/A・Deferred の理由は妥当 |
| 読み取りでの事実の確認 | `NavNode` の構成子の形の決まり・`MenuFilter` の絞り・`parseNode` の読み捨て・`allowed-icons.txt` の 24 個・`decideRoute` の NOT_FOUND・`toTablePlaceholderPath(ref, itemId)` を確認 | 設計の主張（D1〜D3・D7・D9・D17）は今のコードと矛盾しない。移れない項目が子を 1 つ以上持つ決まりにより、D17 の移り先は必ず見つかる |

### Summary

設計はコードと上流の契約（C5・C7）に沿っており、足すだけの応答の変更、アイコンの決め方、ホームの状態、仮の画面の廃止の範囲に実装を壊す矛盾は見当たらない。上流との差（依頼 8 の取り込み済み、Q7 A・Q8 A との差）は 6 節に明記されている。指摘は D24 の文言の不正確さ、D5 の前提の説明、E2E 130 の移し先の未決、`Card` の入れ子の確かめの 4 件の Minor のみ。
