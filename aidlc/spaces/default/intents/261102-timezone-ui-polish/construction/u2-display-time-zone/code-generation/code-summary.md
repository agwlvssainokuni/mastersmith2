# コード生成のまとめ — u2-display-time-zone

## 作った・変えたファイル

`source-manifest.json` のとおり（消したファイルは載せない: `frontend/src/shared/format/formatDateTime.ts`・`formatDateTime.test.ts`、`frontend/src/features/useradmin/lockedUntil.ts`・`lockedUntil.test.ts`）。要点:

- 内部DB: `V14__u2_display_time_zone.sql`（`users.display_time_zone VARCHAR(64)` の追加 1 文）、`User` に更新不可の列の対応。
- バックエンド: `TimeZoneName`（名前の門）、`DisplayTimeZoneInput`（3 状態。`user.domain` に置いた）、`PreferencesValidation` の 5 項目の入口、`UserRepository.savePreferences`（`@Query(nativeQuery)` の `FINAL TABLE (UPDATE ...)` 1 文）、`PreferencesCommand`・`PreferencesResult.Ok`・`UserPreferencesService`・`PreferencesRequest`（`JsonNode`）・`PreferencesResponse`・`MeController`。
- 画面: `app/display-settings/` の提供元・`DisplayTime`・`DisplayTimeZoneErrorBar`・`browserTimeZone`・`decideDisplayTimeZone`・`displayTimeZoneApi`・テスト用の差し込み、プリファレンスの選択（`TimeZoneSelect`・`buildTimeZoneOptions`）、管理の画面 8 ファイルの時刻の置き換え、`formatInstantToMinutes`、`lockedUntilInZone`、ESLint の規則（`eslint-rules/timeZoneSelectors.js`）。
- E2E: 080・100・110・見本・axe の状態、`playwright.config.ts` に `timezoneId: 'Asia/Tokyo'`。k6: `preferencesSaveTimeZone`。文書: `docs/users.md`・`deployment.md`・`verification.md`・`perf/README.md`。

新しい依存・API の道・code・環境変数・監査の種類は足していない。`build.gradle.kts`・`gradle.lockfile`・`package.json`・Dockerfile・compose・`.env.example`・`spotbugs-exclude.xml`・ArchUnit・`packagesJudgedByTotal` は変更なし。

## テストの結果

- バックエンド（`:backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport`）: 単体 2917 件・結合 1708 件、失敗 0（31 分 32 秒）。`user.repository` 100/100%、`user.domain` 99.7/97.6%、`user.service` 99.7/95.9%、`user.web` 96.9/90.5%（行/分岐）。
- 画面（`npm run test:coverage`）: 264 ファイル・2753 件、失敗 0。全体 行 95.15% / 分岐 90.5%。`app/display-settings/` 98.2/96.5%、`features/preferences/` 96.8/89.3%。format・lint・lint:css・license:check・typecheck 通過。ESLint の規則の確かめ 46 件（eslint-config）通過。
- 文の数の基準（変更の前の実装で測定）: GET 2 文・PUT 2 文・内部DB の接続 1 本。変更の後も同じ（`MePreferencesQueryCountIT`）。
- 選択肢の組み立て（418 名、jsdom/Node 26.13.0/Apple M2）: 5 回 48.6, 25.7, 25.3, 23.8, 27.5 ms、中央値 25.7 ms（目標 100 ms）。
- 10 万文字の値: 400 `VALIDATION_FAILED`（413 ではない）。固定した。
- k6: `bash perf/k6/thresholds-test.sh` 全件 ok（測りは流していない）。

## 計画との差

- `DisplayTimeZoneInput` は計画の `user.service` ではなく `user.domain` に置いた（`PreferencesValidation`（domain）が使うため）。
- Step 1 の変更前のカバレッジ基準は測っていない（変更後の値だけ記録）。
- 保存の 1 文は (a) `@Query(nativeQuery)` で成り立った。(b)(c) は不要。この文は持続化の文脈を空にしないため、同じトランザクションで先に読んだ実体は古いまま（業務処理は保存の後に読み直さない）。`UserRepositoryIT` の該当テストは「次のトランザクションで読む」形に直した。
- V8 の `Intl.supportedValuesOf` は `Asia/Calcutta` を返し `Asia/Kolkata` を返さない。保存済みの名前が一覧に無くても解釈できれば先頭の次に足す設計で扱える。
- 提供元の StrictMode のテストは外した（コードのレビューの R-03）。ログインの後に置く提供元は StrictMode でも効果が 2 回流れず（ゲートの答えの後に置かれる）、期待を一つに決めて実装の違いを見分ける形にできなかったため。代わりに、要求の世代の規則を「解決の順を入れ替えても新しい世代の答えが残る」「applySaved が保留中の答えを捨てる」「未ログインで答えを捨てる」のテスト（`DisplayTimeZoneProvider.test.tsx`）で確かめる。
- TimeZoneSelect の再描画での整形回数のテストは足していない（`buildTimeZoneOptions` の配列ごとの 1 回の確かめで代える）。
- E2E の axe の状態（コードのレビューの R-01 の直し）: 080 に解釈できない名前の状態（`preferences-zone-unresolvable`）、120（利用者の管理の画面）に時刻の欄の「読み込み中」（`12-time-zone-loading`、淡い文 `.display-time-note`）と「読み込み失敗」（`13-time-zone-error`、再読み込みの帯と淡い文）を、既存の組ごとのテストの中の step として足した（新しい test() は足していない）。120 は 20 組と 360px の組で、読み取りを保留 → 中断 → 「もう一度読み込む」で本物の応答に戻す。組ごとに axe の違反 0 件・横のはみ出しなし（`./gradlew e2eTest -Pe2e.files=e2e/120-user-admin-accessibility.e2e.ts` で 23 件通過、規則は外していない。文の色・帯の作りの直しは不要だった）。長い名前は 120 の一覧の見本（2語の氏名・長いメールアドレス）が持つ。375px の幅は無く、最も狭い 360px の組で確かめた。招待・DSL の管理の画面の同じ状態は足していない（一覧 1 つで足りるとしたレビューの指摘のとおり）。

## 未実行・Unverified（Deferred）

- 呼び出し元の実測: `./gradlew verify` 通過、`./gradlew e2eTest` 全件 277 件通過（この単位の直しの前の値。直した後は呼び出し元が流し直す）。080・100・110 の個別の E2E は 42 件通過（取得の回数は提供元 1 + 画面 1 の合計 2。解釈できない名前の状態の axe も通過）。120 の個別の E2E は 23 件通過（直した後）。
- k6 の測り（p95）、CI の時間は未実行（Build and Test）。
- 画面のテストの 100 ms の測りは、バックエンドの全件を同時に流した負荷の下で 643 ms となり落ちた（負荷が無いと 25.7 ms）。時計に頼る合否のテストが通常の単体テストの群に入っている点は、落ちたときは team.md の不安定なテストの決まりで扱う（上限は延ばさない）。
- Step 1 の変更前のカバレッジ基準は測っていない。

## 依頼者に確かめたいこと

- 計画の 7 点は推奨のとおりに実装した（D8 案内を足さない、D9 は「時刻の数 + 1」、`vitest.config.ts` に規則の確かめを追加、旧い整形の削除）。
- **AC1.2.4 との食い違い（D8）**: ストーリーの AC1.2.4 は、保存済みの名前を今のブラウザが解釈できないとき「画面の時刻を見る」画面に解釈できない旨を出すことを求める。D8 により、管理の画面の時刻はブラウザのタイムゾーンの時差の数字つきで出すだけで案内は出さない（プリファレンスの画面の警告だけ）。`traceability.json` の AC1.2.4 は OK だが、実際は部分（プリファレンスの画面のみ）。管理の画面にも案内を足すか、このままにするかを承認の場で決めてください。
- `DisplayTimeZoneInput` を domain に置いたこと。
- 持ち越し（今回は直さない。レビューの Minor ③〜⑥）:
  - ③（R-04）文の数のテスト `MePreferencesQueryCountIT` は 4 項目の保存と GET だけ。名前つき・null・無効な名前の PUT の文の数は数えていない（保存の SQL は枝に依らず 1 文）。
  - ④（R-05）100 ms の測りを、ESLint の設定を読むテストのように単独で先に流す群へは移していない。
  - ⑤（R-06）保存済みの名前が解釈できないとき、選択は「ブラウザに合わせる」に見え、同じ項目を選んでも change が起きず null に直せない（別の名前を選んで戻す必要がある）。
  - ⑥（R-07）選択肢はブラウザの Intl の一覧、サーバーの門は JDK の ZoneId の集合で、別々の出どころ。一致を保証する確かめは無い（V8 の `Asia/Calcutta` は JDK にもあるため現状は通る）。

## Build and Test からの繰り戻し 1（2026-11-04）

- きっかけ: Build and Test の `verify` の画面のカバレッジの段で、`frontend/src/eslintTimeZoneRule.test.ts` の最初のテストが 5138 ms で既定の上限 5 秒を超えた（`construction/build-and-test/test-results.md` の Loop-Back Log）。
- 直し（Step R1）: `beforeAll` で ESLint に一度空の文を通し、設定とプラグインの初回の読み込みを済ませる。その `beforeAll` だけに上限 60 秒を付けた。各テストの上限（既定の 5 秒）は変えていない。本体のコードと ESLint の設定は変えていない。
- 確かめ（Step R2。時間の上限 10 分）: 直す前に、(a) このファイルと `src/features` を `--coverage` 付きで同時に流す、を2回（169 ファイル・1764 件、どちらも通過）、(b) `verify` と同じ `npm run test:coverage` を1回（273 ファイル・2962 件、通過）試したが、落ち方は起こせなかった（`verify` の中では、バックエンドのタスクと同じ機械で動く負荷が重なっていた見込み）。(a) の試みでは閾値の判定だけを命令行で 0 にして外した（設定の閾値は変えていない）。直した後は、最初のテスト（`rejects resolvedOptions().timeZone in another file`）が 6 ms で通り、ファイルの 24 件が通過した。初回の読み込みの時間がテストの時間から準備に移ったことはこの数値で確かめた。負荷の下の通過は、Build and Test の `verify` で確かめる。

## Sources

- `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/code-generation/code-generation-plan.md`

## Assumptions & Open Questions

- 最終の結果は「未実行・Unverified」の節のとおり。取得の回数の実測値は 2（提供元 1 + 画面 1）。
