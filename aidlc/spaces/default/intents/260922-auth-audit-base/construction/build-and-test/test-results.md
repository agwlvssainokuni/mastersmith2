# 実行の記録（test-results）

Intent `260922-auth-audit-base` の Build and Test で実際に走らせたコマンドと、その結果。**ここに書く数字はすべて実測**であり、上流の文書の記述を写したものではない。

- 実行した日: 2026-09-23
- 実行の環境: macOS（darwin 25.5.0）、JDK 25（Temurin）、Node.js 24、Gradle Wrapper 9.7.1、組み込みの H2（コンテナ不要）
- Construction Autonomy Mode: **gated**（`aidlc-state.md` には項目が無く、本ステージの指示で gated として扱う）

## 1. 走らせたコマンドの一覧

各単位の `unit-test-instructions.md` の 2 節のコマンドを集め、同じものは1回に畳んだ。

| # | コマンド | 出どころ | 結果 |
|---|---|---|---|
| C1 | `./gradlew verify` | team.md（統合の前の関門）、全単位の 2.4 節 | **成功** |
| C2 | `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` | C1 がテストを UP-TO-DATE で飛ばしたため、実測のためにやり直した | **成功**（2回実行、いずれも同じ件数） |
| C3 | `./gradlew e2eTest` | U3 2.3 節・U4 2.3 節 | **成功**（5 件） |
| C4 | `./gradlew :backend:test --tests 'cherry.mastersmith.common.*' --tests 'cherry.mastersmith.config.*' --tests 'cherry.mastersmith.ArchitectureTest' :backend:integrationTest --tests 'cherry.mastersmith.common.*' --tests 'cherry.mastersmith.config.*' :backend:jacocoTestReport :backend:jacocoTestCoverageVerification` | U1 2.4 節 | **失敗**（下の 3 節。手順の不備） |
| C5 | `(cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/app --coverage)` | U1 2.4 節 | **失敗**（下の 3 節。手順の不備） |
| C6 | `./gradlew :backend:test --tests 'cherry.mastersmith.auth.*' --tests 'cherry.mastersmith.user.*' :backend:integrationTest --tests 'cherry.mastersmith.auth.*' --tests 'cherry.mastersmith.user.*' :backend:jacocoTestReport` | U2 2.4 節 | **成功** |
| C7 | `(cd frontend && ... npx vitest run src/features/auth src/shared/api-client --coverage --coverage.include='src/features/auth/**' --coverage.include='src/shared/api-client/**')` | U2 2.4 節 | **成功**（52 件） |
| C8 | `./gradlew :backend:test --tests 'cherry.mastersmith.access.*' :backend:integrationTest --tests 'cherry.mastersmith.access.*' :backend:jacocoTestReport` | U3 2.4 節 | **成功** |
| C9 | `(cd frontend && ... npx vitest run src/features/admin --coverage --coverage.include='src/features/admin/**')` | U3 2.4 節 | **成功**（30 件） |
| C10 | `./gradlew :backend:test --tests 'cherry.mastersmith.audit.*' :backend:integrationTest --tests 'cherry.mastersmith.audit.*' :backend:jacocoTestReport` | U4 2.4 節 | **成功** |
| C11 | `pre-commit run --all-files` | team.md・project.md Mandated | **成功**（3 項目 Passed） |
| C12 | `./gradlew osvScan --rerun-tasks` | security-test-instructions | **成功**（0 件） |

畳んだコマンド（同じ内容のため1回だけ実行した）。

- `./gradlew :backend:testClasses`（U1〜U4 の 2.2 節）… C1／C2 のビルドの段に含まれる
- `./gradlew :backend:test :backend:integrationTest` と `(cd frontend && npx vitest run)`（U3 2.3 節 Step 9、U4 2.3 節 Step 10 の全体の回帰）… C2 に含まれる
- `npx playwright test e2e/u1-skeleton.e2e.ts` ほか単位ごとの E2E（U1・U2 の 2.3 節）… C3 が同じ5本をまとめて実行する
- `git submodule update --init` ほか前提の用意（U1〜U4 の 2.1 節）… C1 の 0 の段（`verifyPrepare`）が実行する

## 2. 結果の詳細

### 2.1 ビルド（C1・C2）

```
BUILD SUCCESSFUL in 1m 54s   （C2 の1回目）
BUILD SUCCESSFUL in 3m 57s   （C2 の2回目）
```

`verify` の 9 つの段（準備・フォーマット・リンタ・ライセンスヘッダー・ビルド・単体テスト・結合テスト・カバレッジ・安全の検査・成果物）がすべて通った。成果物は `backend/build/libs/mastersmith.war`（画面の `dist` を同梱した実行可能 WAR）。

### 2.2 テストの件数（C2。2回の完全な実行で同じ結果）

| 種類 | テストのクラス／ファイル | 合計 | 成功 | 失敗 | 飛ばし |
|---|---|---|---|---|---|
| バックエンドの単体テスト（`*Test`） | 60 | **387** | 387 | **0** | 0 |
| バックエンドの結合テスト（`*IT`） | 52 | **239** | 239 | **0** | 0 |
| フロントエンドのテスト（Vitest） | 29 | **167** | 167 | **0** | 0 |
| E2E（Playwright／ビルドした WAR。C3） | 3 | **5** | 5 | **0** | 0 |
| **合計** | — | **798** | **798** | **0** | 0 |

単位ごとの内訳（パッケージから割り当て。`common`・`config`・`ArchitectureTest` は U1、`auth`・`user` は U2、`access` は U3、`audit` は U4）。

| 単位 | 単体テスト | 結合テスト | フロントエンド | E2E |
|---|---|---|---|---|
| U1 アプリの骨格 | 130 | 75 | 85（`src/app`） | 2 |
| U2 認証 | 140 | 76 | 52（`src/features/auth`＋`src/shared/api-client`） | 2 |
| U3 アクセス制御 | 69 | 43 | 30（`src/features/admin`） | 1 |
| U4 監査ログ | 48 | 45 | —（画面を持たない） | — |

（結合テストの `@Nested` の 11 件は、いずれも U1 のクラスの入れ子であるため U1 に数えた。）

### 2.3 カバレッジ（C2 の生成した報告から読み取った実測）

バックエンド（JaCoCo。単体＋結合の実行データを合わせて計測。`backend/build/reports/jacoco/test/jacocoTestReport.xml`）。

| 指標 | 実測 | 下限 | 判定 |
|---|---|---|---|
| 行（LINE） | **96.14%**（1393/1449） | 80% | 満たす |
| 分岐（BRANCH） | **91.45%**（417/456） | 70% | 満たす |
| 命令（INSTRUCTION） | 96.92%（6202/6399） | — | — |
| メソッド | 97.78%（397/406） | — | — |
| クラス | 100.00%（114/114） | — | — |

フロントエンド（`@vitest/coverage-v8`。`frontend/coverage/`）。

| 指標 | 実測 | 下限 | 判定 |
|---|---|---|---|
| 行 | **98.73%**（391/396） | 80% | 満たす |
| 分岐 | **94.02%**（189/201） | 70% | 満たす |
| 文 | 98.31%（408/415） | — | — |
| 関数 | 97.03%（131/135） | — | — |

パッケージごとの行カバレッジ（低い順）。

| パッケージ | 行カバレッジ |
|---|---|
| `audit/service` | 77.2%（61/79） |
| `common/health` | 79.2%（42/53） |
| `config` | 92.4%（73/79） |
| `auth/repository` | 93.9%（31/33） |
| `common/web` | 94.3%（66/70） |
| `common/error/web` | 96.8%（181/187） |
| そのほか | 97.5%〜100% |

下限は成果物全体（bundle）に対して定義されている（`backend/build.gradle.kts` の `jacocoTestCoverageVerification`）。パッケージ単位の下限は定義していないため、上の2つは `Not Met` ではない。下限を下げることも、計測の除外を増やすこともしていない。

単位ごとに絞った実行のカバレッジ（C4 の訂正版・C7・C9）。

| 単位 | フロントエンドの行 | フロントエンドの分岐 |
|---|---|---|
| U1（`src/app`） | 99.11%（223/225） | 94.02%（126/134） |
| U2（`src/features/auth`＋`src/shared/api-client`） | 98.57%（138/140） | 96.22%（51/53） |
| U3（`src/features/admin`） | 96.77%（30/31） | 85.71%（12/14） |

### 2.4 安全の検査（C2 の 8 の段、C11、C12）

| 検査 | 実測 | 統合を止める基準 | 判定 |
|---|---|---|---|
| Gitleaks（履歴全体） | 93 コミット・約 10.58MB を走査し、**検出 0 件**（`no leaks found`） | 1件でも失敗 | 通る |
| SpotBugs＋FindSecBugs | **priority 1（High）が 0 件**。priority 2 が 29 件、priority 3 が 23 件（合計 52 件の警告） | High で失敗 | 通る |
| OSV-Scanner（`vendor/make-you-chic-ui` の lockfile を含む） | 「失敗の条件に当たるもの **0 件**、警告 **0 件**」 | 実行時の依存の High 以上ほかで失敗 | 通る |
| `pre-commit run --all-files` | Detect hardcoded secrets **Passed**／Spotless のフォーマットとライセンスヘッダー **Passed**／Prettier のフォーマット **Passed** | 1つでも失敗で止める | 通る |

SpotBugs の警告 52 件はいずれも priority 2・3 で、team.md の基準（High 以上で止める）では警告として扱う。内訳の主なものは `EI_EXPOSE_REP`／`EI_EXPOSE_REP2`（可変なオブジェクトの受け渡し）、`CT_CONSTRUCTOR_THROW`、`SPRING_ENDPOINT`、`SERVLET_HEADER`、`COOKIE_USAGE`、`POTENTIAL_XML_INJECTION`（説明ページの HTML の組み立て。エスケープはテストで確かめている）。

### 2.5 成果物（C2 の 9 の段）

| 確認 | 実測 |
|---|---|
| 初回の読み込みの JavaScript | `assets/index-DwFLCigd.js` **104.7 KB（gzip）**。目安 500KB 以内 |
| 実行可能 WAR | `backend/build/libs/mastersmith.war` が生成された |

### 2.6 E2E（C3）

```
Running 5 tests using 3 workers
  ✓ u1-skeleton.e2e.ts  shows the login layout at / with a CSP header and without violations or script errors (1.2s)
  ✓ u1-skeleton.e2e.ts  opening a screen URL directly also shows the login layout (728ms)
  ✓ u2-auth.e2e.ts      logs in as the initial administrator, keeps the session over a reload and logs out (2.4s)
  ✓ u2-auth.e2e.ts      shows one message for a wrong password and stays on the login screen (1.1s)
  ✓ u3-admin-access.e2e.ts  logs in, opens the administration area from the sidebar and logs out (2.3s)
  5 passed (11.3s)
```

team.md が求める「ログイン → 管理画面に入れるか → ログアウト」の流れは `u3-admin-access.e2e.ts` が受け持つ。

## 3. 実行時に見つかった手順の不備（在ステージの手当て）

### 3.1 症状

U1 の `code-generation/unit-test-instructions.md` 2.4 節のコマンド（C4・C5）が、書かれたままでは必ず失敗する。

```
C4: Rule violated for bundle backend: lines covered ratio is 0.56, but expected minimum is 0.80
    Rule violated for bundle backend: branches covered ratio is 0.51, but expected minimum is 0.70
C5: ERROR: Coverage for lines (61.11%) does not meet global threshold (80%)
    ERROR: Coverage for branches (62.68%) does not meet global threshold (70%)
```

### 3.2 診断

**成果物の不具合ではなく、手順書の不備**である。

- C4 は U1 のテストだけを実行しながら、**成果物全体に対する** `jacocoTestCoverageVerification` を含めている。U2〜U4 のクラスが1行も測られないため、全体の比率が下がるのは当然で、判定が意味を持たない。U2・U3・U4 の同じ 2.4 節は、この理由を明記してこのタスクを**意図的に外している**。U1 だけが外し忘れている。
- C5 は `--coverage.include` で範囲を絞らずに `vitest.config.ts` の全体のしきい値を当てている。U2・U3 の同じ節は `--coverage.include` で絞っている。U1 だけが絞っていない。
- 実際の U1 の本文にも「統合の前の関門としては、これとは別に `./gradlew verify` を実行する」と書かれており、**全体の下限の判定は `verify` が持つ**という設計自体は一貫している。

### 3.3 手当て（失敗の梯子 1 段目: 在ステージの修正。1回目の試行で解決）

品質の目標は一切下げず、**コマンドの絞り込みだけ**を U2〜U4 と同じ形に直して実行し直した。

| 直したもの | 直し方 | 結果 |
|---|---|---|
| C4 | `:backend:jacocoTestCoverageVerification` を外す（`jacocoTestReport` は残す） | **成功**（BUILD SUCCESSFUL in 53s） |
| C5 | `--coverage.include='src/app/**'` を足す | **成功**（85 件、行 99.11%・分岐 94.02%） |

全体の下限（行 80%・分岐 70%）の判定は `./gradlew verify`（C2）が行い、**バックエンド 行 96.14%・分岐 91.45%、フロントエンド 行 98.73%・分岐 94.02%** で満たしている。下限を下げた事実も、計測の除外を増やした事実もない。

### 3.4 人の判断（決定済み）

依頼者の決定: **案A（直す）**。U1 の `unit-test-instructions.md` 2.4 節から `:backend:jacocoTestCoverageVerification` を外し、フロントエンドのコマンドに `--coverage.include='src/app/**'` を足して、U2〜U4 と同じ形にそろえた。下限の判定は `./gradlew verify` が持つことを同節に明記した。品質の目標は変えていない。

以下は、決定の前に示した案の内容である。

### 3.4a 人に判断を仰いだこと

上流の成果物（U1 の `unit-test-instructions.md`）の記述を直すかどうかは、承認を得てから行う。本ステージでは**書き換えていない**。

- 案A: U1 の 2.4 節から `jacocoTestCoverageVerification` を外し、フロントエンドのコマンドに `--coverage.include='src/app/**'` を足す（U2〜U4 と同じ形にそろえる）。影響: 手順書の1行の修正のみ。費用・危険ともほぼ無し。所要 5 分程度。
- 案B: 直さず、注記だけを本書に残す（現状）。影響: 無し。ただし次に U1 の手順をそのまま実行した人が同じ失敗に遭う。

## 4. 既知の観察事項

### 4.1 `SecurityHeadersIT` の一過性の失敗 — 本ステージでは再現しなかった

U4 の生成中に `./gradlew verify` の1回目で `SecurityHeadersIT` が 7 件失敗した（すべて 404）記録がある（U4 の `code-summary.md` 6章）。その後の調査で、原因は確定しておらず、最も筋の通る見立ては macOS の `localhost` が IPv6 と IPv4 の両方に解決されることと、当時 Docker のコンテナが動いていたことの組み合わせとされている。依頼者の判断で、呼び出し先を `127.0.0.1` に固定する案は**採らず**、記録を残して監視することになっている。

**本ステージの実行では再現しなかった**。

| 実行 | `SecurityHeadersIT` | 結合テスト全体 |
|---|---|---|
| C2 の1回目 | 8 件すべて成功 | 239 件すべて成功 |
| C2 の2回目 | 8 件すべて成功 | 239 件すべて成功 |

（U4 の記録では 7 件だったが、その後テストが1件増えて現在は 8 件である。）本ステージの実行中、Docker のコンテナは動かしていない。引き続き CI（`ci-pipeline` の段）で監視する。

### 4.2 パッケージ単位で行カバレッジが 80% を下回る2箇所

`audit/service`（77.2%）と `common/health`（79.2%）。全体の下限は満たしており、パッケージ単位の下限は定義していないため違反ではない。テストを足す余地として記録する。

## Target Verification Matrix（確定版）

確定した表は `build-and-test-summary.md` の `## Target Verification Matrix` にある。`Pending` は1件も残っていない。

| 判定 | 件数 |
|---|---|
| **Met** | **125** |
| **Not Met** | **0** |
| **Unverified** | **17** |
| 対象としない（受け入れた危険） | 4 |

`Unverified` の 17 件と、それを持つ後の段。

| Target ID | 内容 | 持ち主の段 | 証拠の置き場所（予定） |
|---|---|---|---|
| U1-NFR1.1 | ヘルスチェックの 95% が 500 ミリ秒以内 | `performance-validation` | `<record>/operation/performance-validation/` |
| U1-NFR1.3 | 共通の処理が加える時間が 95% で 20 ミリ秒以内 | `performance-validation` | 同上 |
| U1-NFR1.9 | 内部DBのファイルの増え方の実測 | `performance-validation` | 同上 |
| U2-NFR1.1 | ログインの API の 95% が1秒以内 | `performance-validation` | 同上 |
| U2-NFR1.2 | トークンの更新の API の 95% が1秒以内 | `performance-validation` | 同上 |
| U2-NFR1.3 | bcrypt の照合が 100〜500 ミリ秒 | `performance-validation` | 同上 |
| U2-NFR1.4 | 認証つきの要求ごとの処理が 95% で 50 ミリ秒以内 | `performance-validation` | 同上 |
| U2-NFR1.6 | 想定の規模を1台で処理する | `performance-validation` | 同上 |
| U3-NFR1.1 | 確認用 API の 95% が 300 ミリ秒以内 | `performance-validation` | 同上 |
| U3-NFR1.3 | 拒否の記録が 401／403 を 100 ミリ秒以上遅らせない | `performance-validation` | 同上 |
| U4-NFR1.1 | 監査イベント1件の書き込みが 95% で 50 ミリ秒以内 | `performance-validation` | 同上 |
| U4-NFR1.2 | 書き込みが呼び出し元の予算の内側（H2 の待ち合いを名指しで測る） | `performance-validation` | 同上 |
| U2-NFR10.7 | ログインの失敗の割合の指標 | `observability-setup` | `<record>/operation/observability-setup/` |
| U3-NFR10.5 | 管理画面へのアクセス拒否の件数の指標 | `observability-setup` | 同上 |
| U4-NFR1.4 | 監査イベントの件数と大きさの実測 | `observability-setup` | 同上 |
| U4-NFR10.5 | 監査の書き込みの失敗の件数の指標 | `observability-setup` | 同上 |
| U4-NFR3.3 | 内部DBのファイルを OS の権限で守る | `deployment-execution` | `<record>/operation/deployment-execution/` |

いずれも実行の計画に **EXECUTE** として入っている段である（`aidlc-state.md` の OPERATION PHASE）。

## 5. 止まった点と選べる手（決定済み）

依頼者の決定: **案A**。`Unverified` の 17 件は、後の段（`performance-validation`・`observability-setup`・`deployment-execution`）に引き継ぐこととして、本ステージを承認する。`Unverified` を `Met` に書き換えたり、目標を緩めたりはしていない。

以下は、決定の前に示した案の内容である。

実行したコマンドのうち、**成果物の不具合によるものは1件も無い**。すべてのテスト（798 件）が成功し、カバレッジの下限・秘密情報の検出・脆弱性の検査・静的解析のいずれも基準を満たしている。

一方、ステージ定義の判定では、**`Unverified` が 17 件残るため「成功」とはならない**。Construction Autonomy Mode は gated のため、ここで止めて人の判断を仰ぐ。選べる手は次のとおり。

| 手 | 内容 | 影響の見積もり |
|---|---|---|
| **A（推奨）** | `Unverified` の 17 件を後の段（`performance-validation`・`observability-setup`・`deployment-execution`）に引き継ぐこととして、本ステージを承認する | 追加の作業なし。危険は「性能の目標が未検証のまま `ci-pipeline` に進む」こと。ただしいずれの段も実行の計画に入っており、本Intentの配備先は開発者の PC 上のコンテナに限られるため、影響は小さい |
| B | 本ステージの中で簡易な負荷の試験を作り、性能の 11 件をここで測る | 道具の選定（k6／Gatling／`hey`）と試験の作成に 0.5〜1 日。費用は無し（手元で動く道具）。危険は、測定の環境が Performance Validation と違うため、二重に測ることになり数字が食い違う点 |
| C | U1 の `unit-test-instructions.md` の 2.4 節を直す（3.4 節の案A）ことを併せて承認する | 手順書の1行の修正。5 分程度。危険は無し |

**`Unverified` を `Met` に書き換えることや、目標を緩めて「満たした」ことにすることはしない。**

## Sources

- 実行の出力（`./gradlew verify`、`./gradlew e2eTest`、単位ごとのコマンド、`pre-commit run --all-files`、`./gradlew osvScan`）
- `backend/build/test-results/test/`、`backend/build/test-results/integrationTest/`（テストの件数の XML）
- `backend/build/reports/jacoco/test/jacocoTestReport.xml`（カバレッジ）
- `frontend/coverage/`（カバレッジ）、`frontend/playwright-report/`（E2E）
- `build/reports/osv-scanner/osv.json`、`backend/build/reports/spotbugs/`
- 各単位の `construction/<unit>/code-generation/unit-test-instructions.md`・`traceability.json`
- `construction/u4-audit-log/code-generation/code-summary.md` 6章（`SecurityHeadersIT` の調査）
- `aidlc/spaces/default/intents/260922-auth-audit-base/aidlc-state.md`（Test Strategy、実行の計画）

## Assumptions & Open Questions

- U1 の `unit-test-instructions.md` 2.4 節を直すかどうか（3.4 節）。
- 性能の 17 件の未測定を後の段に引き継いでよいか（5 節）。
