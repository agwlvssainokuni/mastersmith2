# スモークテストの結果 — Intent 261104-tz-options-perf

`operation/deployment-pipeline/deployment-strategy.md` の S1〜S10 を、配備した版（`mastersmith:local`、`4ee5fb45593e`）で行った。画面の操作は依頼者が行い、AI はログ・監査・内部DB と対象DB の件数で裏付けた。値（メールアドレス・パスワード・業務データの値・主キーの値・DSL の本文）は記録しない。時刻は UTC（画面の時刻は依頼者の報告）。

## 1. 結果

| # | 確かめ | 行った人 | 結果 |
|---|---|---|---|
| S1 | healthy・V14・起動のログ | AI | 通過（`health-check-report.md` 1節） |
| S2 | ログイン・ホーム・サイドバー・ロゴ（Chrome） | 依頼者 | 通過 |
| S3 | 表示のタイムゾーンの選択（Chrome、Temporal の道） | 依頼者 | 通過。`America/New_York` を保存し、管理の画面の時刻が `-05:00` で出た |
| S4 | 業務データの時刻の読み取り | 依頼者・AI | 通過。前からある見本の行の `created_at` は `-04:00` で出た。AI が対象DB で数えると、2 行とも夏時間の終わり（2026-11-01 06:00 UTC）の前の時点（前 2・後 0。件数だけ）。`America/New_York` のその時点の時差は `-04:00` で、時点ごとの時差で出す設計（261102 の U1・U4）どおり |
| S5 | ★業務データの時刻の書き込み | 依頼者・AI | 通過。作った行は今の時点のため `-05:00` で出た。存在しない時刻の誤りと重なる時刻の2択が出た。時刻ではない欄だけの更新で時刻が変わらなかった。行は削除し、対象DB で目印 `U4 確かめ` の行が 0 件 |
| S6 | カードの移り先・`/admin`・管理者でない利用者 | 依頼者 | 通過。管理者でない利用者（user01）でログインし、ホームに「管理」が出なかった。user01 では管理の道を開いていない（「権限が無い」の表示は確かめていない。結合テストと E2E で確かめ済み） |
| S7 | favicon とアプリのアイコン | AI・依頼者 | 通過。AI: `/favicon.ico` は 200・`image/x-icon`・`nosniff`・`no-cache`、先頭 4 バイト `00 00 01 00`。`/favicon.svg` は 200・`image/svg+xml`・`nosniff`、`<script>`・`onload`・外部の `href` 0 件。依頼者: タブの favicon・ログイン画面のアイコン・色の追従・画像として出ること |
| S8 | ★Firefox・Safari | 依頼者 | 通過。Firefox 159.0（Temporal を持つ版のため Temporal の道）と Safari（Intl の道）で、選択肢・3つの端数の時差の表記が Chrome と同じ、保存・管理の画面と一覧の時刻の時差、タブの favicon |
| S9 | ★既定の DSL の生成と破棄 | 依頼者 | 通過。生成は失敗せず、適用中の DSL との差分は無かった。プレビューは破棄した（複写のプレビュー 0）。見本の `sales.customers.created_at` は桁を指定していない `timestamptz`（PostgreSQL の既定の 6 桁）のため、生成の決まり（`DslTreeBuilder` の BR3.8: 既定と違う桁の列だけ `dbType.precision` を書く）で `precision` は出ない。差分が無いのは期待どおり |
| S10 | 片付け・監査と状態 | 依頼者・AI | 通過。表示のタイムゾーンを「ブラウザに合わせる」に戻した（値の入った行 0）。監査は 2節。スモークテストの間のアプリのログ（55 行、すべて JSON）で ERROR 0・5xx 0、メールアドレス 0・JWT の形 0（F2: A の監視の代わり） |

- 確かめた項目と 261102 の単位ごとの確かめの対応は、`deployment-strategy.md` の 3.3 のとおり。省いたもの（U2 の S6・U4 の S6・U6 の S4）も同じ。
- この Intent の Build and Test の残り（Intl の道は jsdom だけ）と、261102 の U1・U6 の Firefox・Safari の Unverified は、S8 で確かめた。

## 2. 監査（T0 = 20:55:03Z の後、配備の後の複写で数えた）

| 種類 | 結果・理由 | 件数 | 期待 | 説明 |
|---|---|---|---|---|
| `LOGIN_SUCCEEDED` | `SUCCESS` | 5 | 5 | Chrome の初期管理者 2・user01 1・Firefox 1・Safari 1（依頼者の報告） |
| `LOGGED_OUT` | `SUCCESS` | 4 | 利用者の切り替えの回数 | 利用者・ブラウザの切り替えでのログアウト |
| `LOGIN_FAILED` | `FAILURE`・`USER_NOT_FOUND` | 1 | 0（予定外） | Safari での入力の間違い（依頼者に確かめた。アプリのログの `AUTHENTICATION_FAILED` の 401 と同じ時刻） |
| `BUSINESS_ROW_CREATED`・`UPDATED`・`DELETED` | `SUCCESS` | 各 1 | 各 1 | S5。3件とも schema・table・作業ロールの列が空でない |
| `DSL_GENERATED`・`DSL_PREVIEW_DISCARDED` | `SUCCESS` | 各 1 | 各 1 | S9 |
| `ACCESS_DENIED` | — | 0 | 0 | user01 で管理の道を開いていない |
| そのほか | — | 0 | 0 | — |

合計 15 件。予定外の1件（Safari の入力の間違い）は、依頼者の操作で説明がつく。

## 3. アプリのログの 4xx（スモークテストの間）

`GlobalExceptionHandler` の WARN が 9 件。どれも想定内の応答で、5xx は 0 件。

| code | 状態 | 件数 | 説明 |
|---|---|---|---|
| `REFRESH_FAILED` | 401 | 5 | 新しい窓・ブラウザ（プライベートの窓・Firefox・Safari）で開いたとき、続けて使える状態が無いためログインの画面へ移る |
| `DSL_PREVIEW_NOT_FOUND` | 404 | 3 | プレビューの無い状態で DSL の画面を開いたときの応答。画面は「プレビュー無し」として扱う（`frontend/src/features/dsl/useDslAdmin.ts`） |
| `AUTHENTICATION_FAILED` | 401 | 1 | Safari での入力の間違い（2節） |

## Sources

- `operation/deployment-pipeline/deployment-strategy.md`（S1〜S10・3.2・3.3）
- 依頼者の報告（S2〜S9 の結果、ログインの回数、Firefox の版、入力の間違い）
- 実行したコマンドの出力（`curl -sI`・`docker logs`・内部DB の複写の読み取り・見本の対象DB の件数）
- `backend/src/main/java/cherry/mastersmith/dslmanage/generate/DslTreeBuilder.java`（BR3.8）、`frontend/src/features/dsl/useDslAdmin.ts`
