## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-03
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | frontend/e2e/080-preferences-accessibility.e2e.ts・frontend/e2e/support/axe.ts（足した状態は preferences-zone-unresolvable の 1 つだけ）・frontend/src/app/display-settings/DisplayTime.css（.display-time-note は color: var(--color-text-muted)） | 計画 Step 16 は、管理の画面の読み込み中・読み込み失敗・長い名前の axe の状態と 375px のはみ出しを足すとしていたが、足したのは「解釈できない名前」の 1 状態だけ。時刻の欄が読み込み中・失敗のとき淡い文（--color-text-muted）で描かれるが、この文と DisplayTimeZoneErrorBar（Alert と再読み込みの Button）を、ブランドカラーとテーマのすべての組で実際のブラウザの axe にかける流れが e2e 配下に無い（display-time-note などの検索が 0 件）。jsdom の vitest-axe は色のコントラストを見ないため、淡い文のコントラストは未確認。AC5.2.1・AC5.2.2・NFR3.1 を Deferred にした根拠の「Build and Test で E2E の axe の件数」には、この状態が入らない | 管理の画面（一覧 1 つで足りる）で、時刻の欄が loading のときと error のとき（再読み込みの帯を含む）、長い名前の行を、既存の axe の流れの中で全組に対して確かめる状態として足す（規則は外さない）。足せないなら、計画との差と Deferred の持ち主を code-summary.md に明記し、承認の場で諮る | New |
| R-02 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/code-generation/traceability.json（AC1.2.4） | AC1.2.4 を OK にし、target を TimeZoneSelect.test.tsx にしている。しかし D8 により、管理の画面の時刻には「解釈できない」旨を出さない（時差の数字だけ）。ストーリーの AC1.2.4 は「画面の時刻を見る」ときに画面へ出すことを求めており、管理の画面では満たしていない。code-summary は D8 を「推奨どおりに実装」と書くだけで、AC1.2.4 との差とは書いていない | AC1.2.4 に「部分（プリファレンスの画面と業務データの案内のみ。管理の画面は D8 で案内なし）」と target に書き、code-summary の「依頼者に確かめたいこと」に AC1.2.4 との食い違いだと明記して承認の場で決めてもらう | New |
| R-03 | Minor | frontend/src/app/display-settings/DisplayTimeZoneProvider.test.tsx 279-306 | StrictMode のテストは、effect が 2 回流れたかで期待を切り替える（requests が 2 以上なら Kolkata、そうでなければ New_York）。どちらの実装でも通るため、新しい世代の答えだけが残るという規則の確かめになっていない。実装自体（generation の規則）は正しく読める | 要求の数に期待を預けず、StrictMode で effect が 2 回流れる形（再マウントを手で起こすなど）を作って新しい要求の答えが残ることを固定する。作れないなら StrictMode のテストを外し、通常の世代のテスト（解決の順の入れ替え）で足りると code-summary に書く | New |
| R-04 | Minor | backend/src/test/java/cherry/mastersmith/user/web/MePreferencesQueryCountIT.java（putStatements は 4 項目の保存 1 つだけ） | NFR2.1.2 は保存の「項目なし・null・名前・無効な名前」で文の数を数えるとしていたが、数えているのは 4 項目の保存（項目なし）と GET だけ。保存の SQL は枝に依らず 1 文なので実害は小さいが、traceability は NFR2.1 を OK としている | 名前つき・null・無効な名前の PUT も同じ基準（2 文・接続 1 本。無効な名前は 0 文でも可）で数える。足さないなら traceability に範囲を書く | New |
| R-05 | Minor | frontend/src/features/preferences/TimeZoneOptionsPerformance.test.ts（LIMIT_MS = 100、5 回の中央値） | 目標は緩められておらず測り方も妥当だが、時計に頼る合否のテストが通常の単体テストの群に入っている。code-summary 自身が、負荷の下で 643 ms となり落ちたと記録している。team.md の不安定なテストの決まりに当たる形になる。上限を延ばさない方針は正しい | 上限は延ばさず、ESLint の設定を読むテストと同じく単独で先に流す群に移す（vitest.config.ts の groupOrder）か、CI で落ちたときの扱いを code-summary に書く | New |
| R-06 | Minor | frontend/src/features/preferences/TimeZoneSelect.tsx（shown と onChange） | 保存済みの名前が解釈できないとき、選択は「ブラウザに合わせる」に見える（value は空の文字列）。このとき利用者が同じ「ブラウザに合わせる」を選んでも change が起きず null に直せない。いったん別の名前を選んで戻す必要がある。AC1.2.4 の「選び直せる」の経路が分かりにくい | 解釈できない状態では、警告の Alert の近くに null へ戻す操作を足すか、文言で別の名前を選ぶよう案内する。足さないなら承認の場で許容してもらう | New |
| R-07 | Minor | frontend/src/features/preferences/buildTimeZoneOptions.ts（readSupportedTimeZoneNames）と backend の TimeZoneName（ZoneId.getAvailableZoneIds） | 選択肢はブラウザの Intl の一覧、サーバーの門は JDK の ZoneId の集合で、別々の出どころ。ブラウザが JDK より新しい名前を返す場合、選べるが保存が 400 になる。V8 の Asia/Calcutta は JDK にもあるため現状は通るが、一致を保証する確かめが無い | 少なくとも Node 26 の一覧がサーバーの集合に含まれることを確かめる（例: 固定の名前の表を共有の見本にして結合テストと画面のテストの両方で使う）。難しければ既知の制約として docs/users.md に書く | New |
| R-08 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/code-generation/code-summary.md（未実行・Unverified と Assumptions & Open Questions）・code-generation-plan.md（Step 16 が未チェック） | code-summary は、同じ文書の中で「verify 全体と E2E の全件は呼び出し元が流す・結果を確認できていない」と「verify 通過・E2E 277 件通過」の両方を書いている。計画の Step 16 のチェックも未実施のまま。どれが最終かが読み取れない | 最終の結果（verify の通過、E2E の件数、取得回数の実測値 2）に 1 本化し、古い記述を直す。Step 16 は足せなかった状態（上の Major）を明記して閉じる | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| 読み取りのみでの確認（gradlew・npm・docker・git の変更系は使っていない） | 実行せず | テスト結果（単体 2917・結合 1708・画面 2753・E2E 277）は code-summary の記録を引用した。自分では再現していない。名前の門・3 状態・保存の 1 文・V14・ESLint の選択子・提供元の世代の規則はソースを読んで確かめた |

### Summary

バックエンドは設計どおりに実装されている。名前の門（長さ 1〜64 → 集合への完全一致）、3 状態の入力（Keep・Clear・Text・NotText）、保存の 1 文（FINAL TABLE (UPDATE …)）、列を足すだけの V14、値の伏せ字は筋が通っており、ArchUnit・SpotBugs の除外・packagesJudgedByTotal・カバレッジの除外は緩んでいない。DisplayTimeZoneInput を user.domain に置いたのは、PreferencesValidation（domain）が使うため妥当。目標（100 ミリ秒の 5 回中央値、保存 2 文）も緩められていない。画面側も提供元の世代の規則は正しい。主な不足は、管理の画面の loading・error（淡い文）が実際のブラウザの axe を通っていないこと（Major 1 件）。AC1.2.4 の D8 の差を traceability で OK と書いていること、StrictMode のテストが弱いことなどは Minor で、承認の場での決定に回せる。
