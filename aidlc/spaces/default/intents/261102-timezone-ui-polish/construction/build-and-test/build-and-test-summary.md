# Build and Test のまとめ — Intent 261102-timezone-ui-polish

Test Strategy: Standard。ソースは `fix/261102-timezone-ui-polish-bt1` の `2a6e05f7`（`develop` の `f2f2eb9b` に、繰り戻し 1 の U2・U4 の直しを足したもの）。

## 1. ビルドとテストの結果

| 項目 | 結果 | 記録 |
|---|---|---|
| 1コマンドの検査 `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` | 成功（27分8秒。CI の上限の7割 約 42 分の内） | `test-results.md` 2回目 |
| E2E `./gradlew e2eTest`（実際のブラウザ Chromium） | 298 件すべて通過（15分38秒） | `test-results.md` 2回目 |
| 1回目の失敗 2 件 | 繰り戻し 1 で U2・U4 のテストを直して解いた（本体のコードは変えていない） | `test-results.md` の Loop-Back Log |
| 負荷の試験（k6、PostgreSQL・MySQL・MariaDB） | PostgreSQL の 19 の行と、MySQL・MariaDB の 9 場面 × 2 種類が、すべて 2 回とも Met。`checks` の失敗 0、プールの待ちの時間切れ 0 | `evidence/perf-results.md`・`performance-test-instructions.md` |
| 眠り | verify・E2E・k6 のどの窓にも Sleep・DarkWake 無し | 各記録 |

### テストの種類と件数

| 種類 | 件数 |
|---|---|
| サーバーの単体（JUnit・jqwik・ArchUnit） | 2936 件、失敗 0 |
| サーバーの結合（Spring・内部DB H2・Testcontainers の MySQL・MariaDB・PostgreSQL） | 1716 件、失敗 0、飛ばし 10（既存の種類違いの飛ばし） |
| 画面（Vitest・Testing Library・vitest-axe・fast-check） | 273 ファイル・2962 件（カバレッジなし・ありの2回とも通過） |
| E2E（Playwright、axe を含む） | 298 件 |
| 負荷（k6 2.3.0） | PostgreSQL 19 の行 × 2 回、MySQL・MariaDB 9 場面 × 2 回（summary json 68 件） |

画面の振る舞い（時刻の表示・入力・2択・読み込みの失敗の状態・axe）は、jsdom の単体テストだけでなく、Playwright で実際の Chromium を動かして確かめた（E2E 200・185・180・190・195・100・160 など）。

### カバレッジ（行 / 分岐、下限 80% / 70%）

サーバー全体 97.7% / 91.5%、画面全体 95.49% / 90.95%、`src/features/tables/` 95.63% / 91.42%。手を入れたパッケージの値は `test-results.md`。下限・除外・`packagesJudgedByTotal` は変えていない。

## 2. 記録として残す食い違い

- k6 の担当への依頼では「WAR は verify が作ったもの」としていたが、WAR のファイルの日時（09:29）は U4 のコードのコミット（20:44）より前だった。担当が `./gradlew :backend:bootWar` を流し、全タスクが今の入力に対して UP-TO-DATE であること（Gradle は入力の中身で判定する）と、WAR の中の画面の資産の名前が今の `frontend/dist` と同じことを確かめ、中身は HEAD のものと判断した。試験のイメージの中の WAR の SHA-256 は手元と同じ `e2563954…`（`evidence/perf-results.md` 1節）。
- 担当は、README の節ごとの環境の分け方ではなく、1つの使い捨ての環境で順に流した（時間を詰めるため。差は `performance-test-instructions.md` に記録）。場面の setup は前の場面の状態に頼らない。

## Target Verification Matrix

判定は Met / Not Met / Unverified / N/A。数の目標と、Build and Test に引き継がれた目標は行を分けた。振る舞いの決まり（セキュリティ・互換・アクセシビリティなど）は、単位と区分ごとにまとめ、確かめたテストが `verify`・E2E の中で通ったことを根拠にした。

### 数の目標・引き継がれた目標

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| U1-NFR2.1.1 | U1 performance（security-requirements.md 2節） | 1 万の値の表示の変換が jsdom で合計 500 ms 以内 | 中央値 68.1 ms（並行の負荷の下で 115.3 ms） | U1 code-summary.md、`verify` の画面のテストで通過 | code-generation | Met |
| U1-NFR2.1.2 | 同上 | 1つあたり平均 0.05 ms 以内 | 0.0068 ms（負荷の下 0.0115 ms） | 同上 | code-generation | Met |
| U2-NFR2.1.3 | U2 performance-requirements.md | `preferencesGet`・`Save`・`Invalid` の p95 1 秒、`checks` 率 1 | 悪い側 1.94 / 2.63 / 6.76 ms、失敗 0 | `evidence/perf-results.md` 2節 | build-and-test | Met |
| U2-NFR2.1.4 | 同上 | 新しい場面 `preferencesSaveTimeZone` の p95 1 秒、`checks` 率 1 | op 別の悪い側 4.65 / 2.86 / 3.69 ms、失敗 0 | 同上 | build-and-test | Met |
| U2-NFR2.1.6 | 同上 | 選択肢の組み立て1回が jsdom で 100 ms 以内 | 中央値 25.7 ms（418 名）。Build and Test の `verify` でも通過 | U2 code-summary.md、`test-results.md` | code-generation | Met（コード生成の途中、バックエンドを同時に流した負荷の下で 1 回 643 ms で落ちた記録あり。Build and Test の2回の `verify` では落ちていない） |
| U3-NFR2.1.3 | U3 security-requirements.md 2節 | 1 万セルの読み取りの変換が合計 500 ms 以内 | 中央値 3.15 ms（すべて読めない値で 50.5 ms） | U3 code-summary.md | code-generation | Met |
| U3-NFR2.1.4 | 同上 | 1 セル平均 0.05 ms 以内 | 0.315 µs（最悪 5.05 µs） | 同上 | code-generation | Met |
| U3-NFR2.1.5 (PostgreSQL) | 同上 | 業務データの読み取りの既存の場面を直前直後で並べ、既存の目標を変えない | 直前（Intent 261101）との差 −3.79〜+10.24 ms（最大 +4.7%）。すべて線の内 | `evidence/perf-results.md` | build-and-test | Met |
| U3-NFR2.1.5 (MySQL・MariaDB) | 同上 | 同上 | MySQL は全場面で直前と同じか速い（−0.15〜−189.04 ms）。MariaDB は bizSearchFirst +61.20 ms（+22.4%、334.75 ms）・bizSearchLast +38.38 ms（+3.2%）のほかは同じか速い。すべて線の内 | `evidence/perf-results.md` 5節 | build-and-test | Met（MariaDB の2場面の増えは原因を調べていない。前の版は1回だけの値で揺れを含みうる。bizSearchFirst は Intent 261016 の 649.70 ms より速い） |
| U4-NFR2.1.2 (PostgreSQL) | U4 performance-requirements.md | 業務データ・参照の既存の k6 の場面の p95（1・2・3 秒の線）と `checks` 率 1 を緩めない | `bizDefinition` 6.45〜`bizSearchContains` 613.47 ms、参照 15.28〜526.74 ms、失敗 0、プールの時間切れ 0 | 同上 | build-and-test | Met |
| U4-NFR2.1.2 (MySQL・MariaDB) | 同上 | 同上 | 業務データ5場面・参照4場面 × 2回、全 36 回 Met。線に最も近いのは MariaDB の bizSearchContains 2,573.13 ms（線 3,000）。`checks` 失敗 0、プールの時間切れ 0 | 同上 | build-and-test | Met（記録だけの値: MySQL の refPickerContains の借りる待ちの最大 2.557 秒で、上限 3 秒に近い。前の版は 1.230 秒） |
| U5-NFR2.1.2 | U5 performance-requirements.md | メニューの API の p95 1 秒、`checks` 率 1 | `navMenu` 159.74 ms・`navMenuBaseline` 136.31 ms、失敗 0 | 同上 | build-and-test | Met |
| U6-NFR2.1.1 | U6 security-requirements.md 2節 | SVG 4 KiB・`.ico` 32 KiB・`.png` 16 KiB 以内、ログイン画面の追加の要求なし | `app-icon.svg` 811 B・`favicon.svg` 806 B・`favicon.ico` 2,085 B、`.png` は置いていない。部品として描く | `frontend/public/`、素材のテスト（`verify`） | code-generation | Met |
| U4-H2 / AC3.3.4 | U4 infrastructure-design（E2E の時間）・stories.md | E2E 全体の時間と、移した検査の前後の件数を記録 | 15分38秒、277 → 298 件 | `test-results.md`・`cross-unit-traceability.md` | build-and-test | Met |
| team-verify-time | team.md Testing Posture | `verify` の時間を記録し、約 42 分を超えたら諮る | 27分8秒 | `test-results.md` | build-and-test | Met |
| team-ci-time / U4-H4 | team.md Testing Posture | CI の検査の job の時間を記録 | CI Pipeline の段は無く、CI は依頼者の push の後に動く | — | （push の後の CI） | N/A |
| team-coverage | team.md・U2 NFR5.1.4・U4 NFR5.1.7 | 行 80%・分岐 70%（全体・パッケージごと・画面全体） | サーバー 97.7 / 91.5、画面 95.49 / 90.95、手を入れたパッケージはすべて下限の上 | `test-results.md` | build-and-test | Met |
| k6-redaction | team.md・project.md Forbidden | 記録の json に認証の値を残さない | 68 件の json で JWT の形 0 件、`gitleaks dir` no leaks found | `evidence/`、`evidence/perf-results.md` 6節 | build-and-test | Met |

### 振る舞いの決まり（単位・区分ごと）

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| U1-NFR1.4.x・NFR5.2.x | U1 security-requirements.md | 信頼できない入力を例外なしの結果の種別で返す、値を外へ出さない、タイムゾーンの明示・純粋な関数 | 単体テスト・性質ベースのテスト（fast-check）が通過 | `verify`（画面のテスト） | code-generation | Met |
| U1-NFR2.1.3・NFR1.5.x・NFR5.1.1 | 同上 | `Intl.DateTimeFormat` の使い回し、新しい依存なし、ヘッダー、日時の必須のテスト | テストとリンタ・ライセンスヘッダーの検査が通過 | `verify` | code-generation | Met |
| U2-NFR1.2.x・NFR1.4.x | U2 security-requirements.md | 本人だけ・一括代入の防止・停止中の拒否・名前の検証・3状態・漏えいなし・監査なし・ESLint の規則 | 結合テスト（`*SecretLeakIT` を含む）・単体テスト・ESLint の規則のテストが通過 | `verify` | code-generation | Met |
| U2-NFR2.1.1・2.1.2・2.1.5・2.1.7・NFR2.3.x | U2 performance・scalability・observability-requirements.md | 読み取り1回、問い合わせを増やさない、整形の回数、指標・ログの形 | 回数を数えるテスト・結合テストが通過 | `verify` | code-generation | Met |
| U2-NFR4.1.x・NFR4.3.x | U2 reliability-requirements.md | 移行 1 つ・前の版の互換・戻し・読み込みの失敗と回復・古い答えの競り合い | 互換の結合テスト・状態のテストが通過、E2E の差し替えの回数が通過 | `verify`・E2E | code-generation | Met |
| U2-NFR3.x・NFR6.1.1 | U2 security-requirements.md | axe 違反 0（状態ごと・全組）、キーボード、375px、2言語 | E2E 100・185 の axe 通過、画面のテスト通過 | E2E・`verify` | code-generation | Met |
| U2-NFR5.x | U2 security-requirements.md | 夏時間・端数の時差、性質ベース、必須のテスト、時差の固定、`waitFor` | テスト通過（繰り戻し 1 で ESLint の規則のテストの準備を直した。上限は緩めていない） | `verify`・`test-results.md` | code-generation | Met |
| U3-NFR1.4.x・NFR1.5.x | U3 security-requirements.md | 値・接続情報・JDBC の文を出さない、読めない値の扱い、入力の拒否、初期化の失敗、SQL の組み立ての決まり、依存なし | 3種類の対象DB の結合テスト・`*SecretLeakIT`・境界テスト・SpotBugs が通過 | `verify` | code-generation | Met |
| U3-NFR2.1.1・2.1.2・NFR5.x | 同上 | 問い合わせ・接続の数を増やさない、初期化は接続ごとに1回、日時の必須のテスト、JVM の既定に頼らない | `*ConnectionUsageIT`・3種類の DB の型の結合テスト・`user.timezone` を変えたテストが通過 | `verify` | code-generation | Met |
| U4-NFR2.1.1・2.1.3〜2.1.5 | U4 performance-requirements.md | 問い合わせ・接続の数、整形・変換の回数 | `ReferenceConnectionUsageIT`（1件足した）・回数を数える画面のテストが通過 | `verify` | code-generation | Met |
| U4-NFR3.1.x | U4 security-requirements.md | 業務データの画面の axe 違反 0（全組・状態）、入力欄の a11y、375px | E2E 185（43 件、時刻の状態 20 件を含む）・200 が通過 | E2E | code-generation | Met |
| U4-NFR4.2.1・NFR5.1.x・NFR5.2.1・NFR6.1.1 | 同上 | 定義の応答は足すだけ、日時のテスト・性質ベース、`labelValueType` で値を明かさない、値を出さない、XSS、依存なし、時差の固定、2言語 | 単体・結合テスト通過、E2E の json に JWT の形 0 件 | `verify`・E2E | code-generation | Met |
| U4-NFR5.3.1 | 同上 | 日時の代表の E2E を1本足す（`America/New_York`・存在しない時刻・重なる時刻の2択・`-05:00`・更新） | E2E 200 が実際の Chromium と見本の PostgreSQL で通過 | E2E | build-and-test | Met |
| U5-NFR1.3.x | U5 security-requirements.md | `tableKind` の絞り・漏えいなし・信頼できない入力・画面で権限を足さない・管理の確認 API を残す | 単体・結合テスト（401・403・200）が通過 | `verify` | code-generation | Met |
| U5-NFR2.1.1・NFR2.2.x | U5 performance-requirements.md | 読み出しの数、ホームの要求 0、O(N)、ロゴの要求なし・新しい依存なし | テスト通過、`frontendBundleSize` 通過（169.7 KB gzip） | `verify` | code-generation | Met |
| U5-NFR3.x・NFR4.2.1・NFR5.3.1・NFR6.1.1 | U5 security-requirements.md | ホーム・サイドバーの axe 違反 0、375px、アイコンを飾りに、応答は足すだけ、E2E の移し、2言語 | E2E 030・050・090・130・160 通過 | E2E・`verify` | code-generation | Met |
| U6-NFR1.1.x・NFR3.x・NFR5.3.1 | U6 security-requirements.md | SVG の許可リスト・ヘッダー・favicon の種類・素材のテスト・ログイン画面の axe 違反 0・アイコンを飾りに | 素材のテスト・`verifyFaviconInWar` が通過、ログイン画面のアイコンと axe を確かめる E2E 050（と 020）が通過 | `verify`・E2E | code-generation | Met |

Not Met・Unverified は無い。MySQL・MariaDB の k6 は、依頼者の決定（2026-11-05、Unverified で持ち越さず今流す）で追加の回として流した（約 1 時間 52 分、眠り無し）。

## Sources

- `test-results.md`・`build-instructions.md`・`integration-test-instructions.md`・`cross-unit-traceability.md`・`performance-test-instructions.md`・`evidence/perf-results.md`
- 各単位の `construction/*/nfr-requirements/*.md` と `construction/*/code-generation/code-summary.md`
- `aidlc/spaces/default/memory/team.md`・`project.md`
