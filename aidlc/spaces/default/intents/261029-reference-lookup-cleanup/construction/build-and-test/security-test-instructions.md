# セキュリティの試験の手順 — Build and Test（Intent 261029-reference-lookup-cleanup）

この Intent のセキュリティの目標（要件 NFR1、`team.md` の「他テーブル参照の機能で必ず書くテスト」(a)〜(k)、`project.md` の Forbidden・Mandated）をどのテストと検査で確かめるかと、その流し方。採用しない検査（CodeQL・OWASP ZAP・コンテナイメージの検査）は `team.md` のとおり扱わない。

## 1. 静的な検査（`./gradlew verify` の中）

| 検査 | 止める基準 | 今回の確かめ |
|---|---|---|
| SpotBugs ＋ FindSecBugs（`spotbugsGate`） | priority 1、`SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` は priority にかかわらず | `backend/config/spotbugs-exclude.xml` の前の main からの変更 0（除外を足していない・広げていない） |
| 境界テスト（ArchUnit） | 1件でも落ちたら止める | 19 個の `*ArchitectureTest.java` の変更 0。`java.sql.Connection` の `prepareStatement`・`createStatement` の許可の場所は `RowSqlExecutor` のまま（team.md の (k)） |
| Gitleaks（`gitleaksScan`、履歴の全体） | 1件でも止める | pre-commit・verify・CI の3か所 |
| 記録の検査（`perfEvidenceScan`、U4） | `aidlc/` の下の `evidence/` に JWT の形などがあれば止める（終了コード 4） | verify と CI で成功 |
| OSV-Scanner（`osvScan`） | High 以上（frontend の devDependencies は警告。悪意のあるパッケージと成果物を作る道具は止める） | lockfile の差は Dependabot の取り込み（41b8bf5）だけ |
| npm の `ignore-scripts` | `frontend/.npmrc` | 変更なし |

## 2. 認可・入力・漏えいのテスト（結合テスト）

| 観点（`team.md` の印） | 確かめること | テスト |
|---|---|---|
| (a) 参照先の読み取りの認可 | 401・要る権限だけを欠く利用者の 403・200 を表のパラメーターで。参照元の権限だけで参照先の権限を欠く人に選択肢を返さない。件数と検索の上限の境界・時間切れ | `ReferenceAuthorizationApiIT`・`AbstractReferenceReadIT`・`ReferenceRequestLimitIT`・`ReferenceTimeoutIT` |
| (b) 識別子の照合 | 参照先の名前は要求から受け取らず、写しの DSL と照らす。DSL に無い名前は拒否し状態が変わらない | `ReferenceSameResponseIT`・`ReferencePolicyPropertyTest` |
| (c) 件数と取り出しの上限 | `LIMIT` と `setMaxRows` の両方、201 件で `TOO_MANY` | `RowSqlExecutorTest`・`AbstractReferenceReadIT` |
| (d) 漏えい | 行の値・ラベル・主キー・接続情報がログ（TRACE）・トレースの属性・エラー応答に出ない。参照の値を道（URL）に載せない | `ReferenceSecretLeakIT`・`ReferenceWriteSecretLeakIT`・`ReferenceTraceExposureIT`・`ReferenceWriteTraceExposureIT` |
| (e) 検索のエスケープ | `%`・`_`・エスケープの文字が一致の条件として働かない | `AbstractReferenceReadIT` |
| (f) 画面に出す値 | `<script>`・`<img onerror>`・`"`・`'`・`javascript:` を文字として描く | 画面のテスト（jsdom）と E2E 195（実際の Chromium） |
| (g) 書き込みでの検証（IDOR） | 参照先に無い値・権限の無い値・別の参照先の差し替えの 409 と状態が変わらないこと | `AbstractReferenceWriteIT`・`ReferenceWriteAuthorizationApiIT` |
| (h) 参照先の形 | 複合の主キー・主キー無し・ビュー・自己参照で 500 にしない | `AbstractReferenceReadIT` |
| (i) 3種類の対象DB | MySQL・MariaDB・PostgreSQL のすべて | `*ReferenceReadIT`・`*ReferenceWriteIT` |
| (j) 接続の数 | 1要求の対象DB と内部DB の接続の数（この段で U2 に MariaDB と内部DB の借りた回数を足した） | `ReferenceConnectionUsageIT`・`ReferenceWriteConnectionUsageIT` |
| (k) 許可の一覧 | 既存の境界テストと SpotBugs の除外が緩まない | 1節 |
| API の分類 | 新しい API が「ログインだけ」に明示して分類され、分類の無い API があれば落ちる | `ApiAccessArchitectureTest`・`ApiAccessConsistencyIT` |

## 3. 負荷の試験の記録の伏せ字（U4）

`project.md` の Forbidden（認証の値を伏せずに perf の記録へ置かない）を確かめる。

- 道具のテスト（`perfEvidenceRedactor.test.ts`・`perfEvidenceRedactorCli.test.ts`・`perfEvidenceScan.test.ts`）: JWT の形が残らない・冪等・入れ子と長い文字列・JWT 以外の値を壊さない。
- 実際の summary json を道具で置き、`--scan` と `gitleaks dir` で 0 件（H4。この段で実施、`k6-trial-record.md` 5節）。
- わざと残存を作った 1 回の `perfEvidenceScan`（H1。架空の JWT の形の値を `evidence/` に一時に置き、終了コード 4 で失敗し、出力に値と3区間のどれも出ないことを件数で確かめ、ファイルを消して `git status` に残らないことを記録する）。この段で実施した（`test-results.md` 13節、終了コード 4・出力に値と区間 0 件）。

```bash
# H1 の形（値は実行の時に組み立てる架空のもの。コミットしない）
caffeinate -dims ./gradlew perfEvidenceScan   # 終了コード 4 を期待。出力を件数で検索してから一時のファイルを消す
git status --short aidlc/                     # 残っていないこと
```

## 4. 報告と記録の検索

- E2E の報告の json（`frontend/test-results/e2e-results.json`）に資格情報・接続先・参照の値が入っていないことを文字列で検索する。
- この段の記録（Markdown と `evidence/`）に JWT の形が無いことを `eyJ` の検索と `gitleaks dir` で確かめる。

## 5. 既知の制約（直さずに記録したもの）

- U4 R-02: 道具 `perf/redact-k6-evidence.mjs` のカバレッジ（行 94.2%・分岐 90.61%）は `verify` の下限の外で、下がっても気づく手段が無い。
- U4 R-03: 道具には Prettier（`perfToolFormatCheck`）と oxlint（`perfToolLint`）が当たるが、ESLint（frontend の設定が `perf/` を対象にしない）と型の検査（`.mjs` は tsc の対象外）は当たらない。
- U2・U3 の新しい Minor: `BusinessDataWriteService` の参照の段で、手がかりの無い想定外の失敗のときに `stage` の無い WARN が出る経路が残る（値は出ない）。
