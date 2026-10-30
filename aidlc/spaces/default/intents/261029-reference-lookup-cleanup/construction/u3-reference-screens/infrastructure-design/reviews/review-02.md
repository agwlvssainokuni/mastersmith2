## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-10-30T08:23:56Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/infrastructure-design/cicd-pipeline.md > 5節 配備と戻し（戻しの注意） | 画面の静的ファイルは同じ実行可能 WAR（`backend/build.gradle.kts` の `bootWar` が `frontend/dist` を同梱）に入り、イメージも 1 つなので、U3 だけを戻す・U1/U2 のサーバー側だけを残す、という戻し方は成り立たない。「U1・U2 のサーバー側を同時に戻すかは Intent 全体の手順で決める」は選べるかのように読める。U1/U2 側に移行や `.env` の変更があるとイメージだけの戻しでは足りなくなる点が、U3 の文書には出てこない。 | 戻しは WAR とイメージの単位で U1・U2・U3 が必ず一緒に戻ると書き直し、U1/U2 の移行・`.env` の差を踏まえた戻しの手順は Deployment Pipeline で一本にまとめると明記する。 | Resolved (reviewer) |
| R-02 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/infrastructure-design/infrastructure-specification.md > 2節 E2E の共有の DSL・上流との差 | 180 の `flowDsl` を `support/` へ移して参照の表を足すと、180 が投入・適用する DSL の中身が変わる（現状は `flowDsl` が 180 のファイル内にあり、`/api/admin/dsl/preview`・`apply` で適用している）。それでも「180 の振る舞いは変えない」とだけ書いている。190・195 が「適用されていなければ適用する」と判定する方法（印・ハッシュの比較など）と、DSL の適用が全 E2E で共有される状態を書き換えること（初期管理者の権限や見本の DSL には触れない）の説明もない。 | 共有 DSL に足す内容が 180 の確かめ（表・メニューの件数や名前）に影響しないことを Code Generation の確認項目に入れる。「適用済みか」の判定方法を 1 行で決める。 | Resolved (reviewer) |
| R-03 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/infrastructure-design/infrastructure-specification.md > 4節 業務データの画面の chunk の測り方 | 測りは `.js` の gzip だけで、`check-bundle-size.mjs` と同じ範囲に揃えている。U3 が部品ごとの CSS を足すと、遅延の chunk に付く `.css` は数えられない。起点を `isDynamicEntry` かつキーが `src/features/tables/` で始まる項目としているが、manifest（`vite.config.ts` の `build.manifest: true`）のキー形を実際のビルドで確かめた記述がない。 | 一時の手順で `.css`（manifest の `css` 配列）を別枠で記録する。起点のキーが期待どおり取れることを最初の測定で確かめ、取れなければ手順を直すと書く。 | Resolved (reviewer) |
| R-04 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/infrastructure-design/infrastructure-specification.md > 3節 共有基盤・monitoring-design.md > 1節 | 作成・更新の拒否を「409 `REFERENCE_NOT_FOUND`」と書いている。contract-summary.md（C3）では問題の code が `BUSINESS_REFERENCE_NOT_FOUND`、`REFERENCE_NOT_FOUND` は項目の誤りの `reason` として別に出てくる。 | 文書内で code と `reason` を区別して書く。 | Resolved (reviewer) |
| R-05 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/infrastructure-design/infrastructure-specification.md > 2節 E2E の共有の DSL | 180・190・195 が毎回、適用中の DSL を置き換える。190 と 195 が 180 の後に流れると、前の DSL を指す 180 の状態が変わる。ファイルは順に流れる前提（workers: 1）で、各ファイルが自分で投入するため実害は小さい。ただし、E2E の最後のファイルが適用中の DSL をどの状態で残すかは書かれていない。 | 最後に流れたファイルの DSL が適用中のまま残ってよいことを、共有の状態の扱いに一言書く。 | New |
| R-06 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/infrastructure-design/infrastructure-specification.md > 4節 起点の確かめ | 起点の条件（キーが `src/features/tables/` で始まり `isDynamicEntry` が真）は、Vite の manifest が lazy の chunk を dynamic entry として持つ前提に立つ。この前提は最初の測定で確かめる手順があるため、設計としては成り立つ。ただし、フォールバック後の条件が記録される先は `code-summary.md` で、4節の表の「記録」と一致しているかは確認が要る。 | 条件を直した場合の記録先を、4節の「記録」の行にそろえて書く。 | New |

> R-01 Reviewer note: cicd-pipeline.md 5節の「戻しの単位」で、WAR とイメージの単位で U1・U2・U3 が必ず一緒に戻ると明記された。戻しの手順を Deployment Pipeline で一本にまとめることも 5節と「後の段に回すこと」に書かれている。

> R-02 Reviewer note: infrastructure-specification.md 2節で、共有の DSL は適用済みの判定をせず毎回投入・適用すると決まり、その理由（ファイルごとの tag で dslHash が一致しない）も書かれた。180 への影響は「後の段に回すこと」の確認項目 (1)〜(3) に入っている。

> R-03 Reviewer note: 4節に、CSS を別枠で数える手順と、最初の測定で起点のキーを確かめ、取れなければ条件を直して記録する手順が足された。

> R-04 Reviewer note: infrastructure-specification.md 3節と monitoring-design.md 1節・3節で、問題の code `BUSINESS_REFERENCE_NOT_FOUND` と項目の誤りの `reason` `REFERENCE_NOT_FOUND` が分けて書かれた。

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| 読み取りだけの突き合わせ（frontend/ と build.gradle.kts の確認） | 未実施 | 作業フォルダの読み取りの制限により、sibling の確認ができなかった。manifest の形と 180 の `flowDsl` の位置は文書の記述を信頼した。これは未確認の前提として扱う。 |

### Summary

前回の R-01〜R-04 はすべて直っている。新しい指摘は Minor 2 件で、どちらも承認を妨げない。READY。
