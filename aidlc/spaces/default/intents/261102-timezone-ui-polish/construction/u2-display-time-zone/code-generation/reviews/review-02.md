## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-04
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | frontend/e2e/080-preferences-accessibility.e2e.ts・frontend/e2e/support/axe.ts（足した状態は preferences-zone-unresolvable の 1 つだけ）・frontend/src/app/display-settings/DisplayTime.css（.display-time-note は color: var(--color-text-muted)） | 計画 Step 16 は、管理の画面の読み込み中・読み込み失敗・長い名前の axe の状態と 375px のはみ出しを足すとしていたが、足したのは「解釈できない名前」の 1 状態だけ。時刻の欄が読み込み中・失敗のとき淡い文（--color-text-muted）で描かれるが、この文と DisplayTimeZoneErrorBar（Alert と再読み込みの Button）を、ブランドカラーとテーマのすべての組で実際のブラウザの axe にかける流れが e2e 配下に無い（display-time-note などの検索が 0 件）。jsdom の vitest-axe は色のコントラストを見ないため、淡い文のコントラストは未確認。AC5.2.1・AC5.2.2・NFR3.1 を Deferred にした根拠の「Build and Test で E2E の axe の件数」には、この状態が入らない | 管理の画面（一覧 1 つで足りる）で、時刻の欄が loading のときと error のとき（再読み込みの帯を含む）、長い名前の行を、既存の axe の流れの中で全組に対して確かめる状態として足す（規則は外さない）。足せないなら、計画との差と Deferred の持ち主を code-summary.md に明記し、承認の場で諮る | Unresolved |
| R-02 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/code-generation/traceability.json（AC1.2.4） | AC1.2.4 を OK にし、target を TimeZoneSelect.test.tsx にしている。しかし D8 により、管理の画面の時刻には「解釈できない」旨を出さない（時差の数字だけ）。ストーリーの AC1.2.4 は「画面の時刻を見る」ときに画面へ出すことを求めており、管理の画面では満たしていない。code-summary は D8 を「推奨どおりに実装」と書くだけで、AC1.2.4 との差とは書いていない | AC1.2.4 に「部分（プリファレンスの画面と業務データの案内のみ。管理の画面は D8 で案内なし）」と target に書き、code-summary の「依頼者に確かめたいこと」に AC1.2.4 との食い違いだと明記して承認の場で決めてもらう | Unresolved |
| R-03 | Minor | frontend/src/app/display-settings/DisplayTimeZoneProvider.test.tsx 279-306 | StrictMode のテストは、effect が 2 回流れたかで期待を切り替える（requests が 2 以上なら Kolkata、そうでなければ New_York）。どちらの実装でも通るため、新しい世代の答えだけが残るという規則の確かめになっていない。実装自体（generation の規則）は正しく読める | 要求の数に期待を預けず、StrictMode で effect が 2 回流れる形（再マウントを手で起こすなど）を作って新しい要求の答えが残ることを固定する。作れないなら StrictMode のテストを外し、通常の世代のテスト（解決の順の入れ替え）で足りると code-summary に書く | New |
| R-04 | Minor | backend/src/test/java/cherry/mastersmith/user/web/MePreferencesQueryCountIT.java（putStatements は 4 項目の保存 1 つだけ） | NFR2.1.2 は保存の「項目なし・null・名前・無効な名前」で文の数を数えるとしていたが、数えているのは 4 項目の保存（項目なし）と GET だけ。保存の SQL は枝に依らず 1 文なので実害は小さいが、traceability は NFR2.1 を OK としている | 名前つき・null・無効な名前の PUT も同じ基準（2 文・接続 1 本。無効な名前は 0 文でも可）で数える。足さないなら traceability に範囲を書く | New |
| R-05 | Minor | frontend/src/features/preferences/TimeZoneOptionsPerformance.test.ts（LIMIT_MS = 100、5 回の中央値） | 目標は緩められておらず測り方も妥当だが、時計に頼る合否のテストが通常の単体テストの群に入っている。code-summary 自身が、負荷の下で 643 ms となり落ちたと記録している。team.md の不安定なテストの決まりに当たる形になる。上限を延ばさない方針は正しい | 上限は延ばさず、ESLint の設定を読むテストと同じく単独で先に流す群に移す（vitest.config.ts の groupOrder）か、CI で落ちたときの扱いを code-summary に書く | New |
| R-06 | Minor | frontend/src/features/preferences/TimeZoneSelect.tsx（shown と onChange） | 保存済みの名前が解釈できないとき、選択は「ブラウザに合わせる」に見える（value は空の文字列）。このとき利用者が同じ「ブラウザに合わせる」を選んでも change が起きず null に直せない。いったん別の名前を選んで戻す必要がある。AC1.2.4 の「選び直せる」の経路が分かりにくい | 解釈できない状態では、警告の Alert の近くに null へ戻す操作を足すか、文言で別の名前を選ぶよう案内する。足さないなら承認の場で許容してもらう | New |
| R-07 | Minor | frontend/src/features/preferences/buildTimeZoneOptions.ts（readSupportedTimeZoneNames）と backend の TimeZoneName（ZoneId.getAvailableZoneIds） | 選択肢はブラウザの Intl の一覧、サーバーの門は JDK の ZoneId の集合で、別々の出どころ。ブラウザが JDK より新しい名前を返す場合、選べるが保存が 400 になる。V8 の Asia/Calcutta は JDK にもあるため現状は通るが、一致を保証する確かめが無い | 少なくとも Node 26 の一覧がサーバーの集合に含まれることを確かめる（例: 固定の名前の表を共有の見本にして結合テストと画面のテストの両方で使う）。難しければ既知の制約として docs/users.md に書く | New |
| R-08 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/code-generation/code-summary.md（未実行・Unverified と Assumptions & Open Questions）・code-generation-plan.md（Step 16 が未チェック） | code-summary は、同じ文書の中で「verify 全体と E2E の全件は呼び出し元が流す・結果を確認できていない」と「verify 通過・E2E 277 件通過」の両方を書いている。計画の Step 16 のチェックも未実施のまま。どれが最終かが読み取れない | 最終の結果（verify の通過、E2E の件数、取得回数の実測値 2）に 1 本化し、古い記述を直す。Step 16 は足せなかった状態（上の Major）を明記して閉じる | New |

> R-01 Reviewer note: 再現の試みは3種類（同時に流す2回・verify と同じ全体1回）と結果は書かれているが、実際にかかった合計の時間が書かれておらず、先に決めた上限 10 分の中に収まったかが記録から読めない。原因の裏付けは直した後の最初のテストが 6 ms で通ったという数値に頼っており、直す前の同じ測り（5138 ms の内訳）との並べがない。; Required action: 試みの合計時間を一行で足す。可能なら、直す前の最初のテストの時間（5138 ms）と直した後の 6 ms、準備（beforeAll）にかかった時間を並べて書く。

> R-02 Reviewer note: 準備の上限 60 秒は、同じファイルの「the real sources」の 60 秒と同じ値で、初回の読み込みが負荷の下で伸びても余裕が大きい。各テストの 5 秒は変わっておらず、team.md の決まりには反しないが、準備が極端に遅くなった場合に気づく手がかりがない。; Required action: 対応は必須ではない。気になる場合は、Build and Test の verify で準備にかかった時間を記録して、60 秒の根拠にする。

> R-03 Not re-checked this round

> R-04 Not re-checked this round

> R-05 Not re-checked this round

> R-06 Not re-checked this round

> R-07 Not re-checked this round

> R-08 Not re-checked this round

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| 指示により Bash を使わず、実行していない | 未実行 | テストの実行結果は確かめていない。ファイルの読み取りと、計画・まとめ・失敗の記録の突き合わせだけで判断した。負荷の下での通過は Build and Test の verify で確かめる前提（まとめに明記あり）。 |

### Summary

直しは原因（ESLint の設定とプラグインの初回の読み込みが最初のテストに入る）に当たっている。`beforeAll` で空の文を通して準備を済ませ、その準備だけに 60 秒を付けており、各テストの 5 秒と「the real sources」の 60 秒は変えていないため、team.md の「テストの時間の上限は原因を確かめずに延ばさない」に反しない。テストの中身（規則の効きの確かめ）は変わっていない。手元で再現しなかった点は、試みの範囲と見立てが記録され、決まりの「再現しないもの」の扱いを満たす。指摘は Minor 2 件のみ。
