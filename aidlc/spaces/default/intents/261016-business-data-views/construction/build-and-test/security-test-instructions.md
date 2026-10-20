# セキュリティの試験の手順 — Intent 261016-business-data-views

この Intent のセキュリティの要件（要件の NFR4.1〜NFR4.4、各単位の NFR4.x・NFR7.x、`project.md` の Forbidden・Mandated）を確かめる手順を書く。CodeQL・OWASP ZAP・コンテナイメージの検査は採用しない（team.md の Code Style）。検査はすべて `./gradlew verify` の中か E2E の中で流れ、別の道具は足さない。

## 1. 静的な検査（`./gradlew verify` の 8 の段 `verifySecurity`）

| 検査 | コマンド | 止める基準 | この Intent で確かめること |
|---|---|---|---|
| SpotBugs ＋ FindSecBugs | `./gradlew :backend:spotbugsGate` | priority 1、パターン名が `SQL_` で始まるもの・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` は priority によらず | `SQL_` の除外は `RowSqlExecutor` の読み取りの2メソッド（`search`・`detail`）と書き込みの5メソッドに、パターン名2つ（`SQL_INJECTION_JDBC`・`SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING`）で絞ったものだけ（`backend/config/spotbugs-exclude.xml`、理由と根拠つき）。パッケージや `SQL_` の全体では外していないこと |
| OSV-Scanner | `./gradlew osvScan --rerun` | バックエンドと画面の実行時の依存の High 以上、成果物を作る道具（`config/npm-build-tools.txt`）の High 以上、`MAL-` | axe-core 4.14.0（MPL-2.0、開発用）の指摘が無いこと。lockfile が変わらないと UP-TO-DATE で飛ぶため `--rerun` を付ける |
| Gitleaks | `./gradlew gitleaksScan` | 漏れ 1 件でも | リポジトリの履歴全体。`.env`・`.env.targetdb` がコミットされていないこと |
| ライセンス | 足した依存の確かめ | Apache License 2.0 と異なるものは採用の理由を記録 | axe-core 4.14.0 は MPL-2.0（開発用。U3 の B7 で依頼者が採ることを決めた A9）。`hasInstallScript` の付いた項目が増えていない（`node_modules/fsevents` の1件のまま） |

## 2. 構造の検査（ArchUnit、`verify` の単体テスト）

| テスト | 確かめること |
|---|---|
| `BusinessDataBoundaryArchitectureTest` | `java.sql.Connection` の `prepareStatement`・`createStatement` を呼んでよいのは `RowSqlExecutor` だけ。読み取りの側にトランザクションを置かない。`businessdata` に依存してよいのは `audit` の domain の出来事と理由の値だけ |
| `TargetDbBoundaryArchitectureTest` | 書き込み・`JdbcTemplate`・`@Transactional` の禁止を緩めていない（U2 は書き込みの口を足す形で、既存の決まりは足す形のみ） |
| `RoleBoundaryArchitectureTest` | `role` に依存してよい相手に `businessdata` を足した（U1 の計画の A1、依頼者の承認済み） |
| `ApiAccessArchitectureTest`・`ApiAccessConsistencyIT` | 業務データの API の6本（読み取り3・書き込み3）がすべて「ログインだけ」（`@ApiAccess(AUTHENTICATED)`）に明示して分類され、分類の無い API があれば落ちる |
| `ArchitectureTest` | 層の境界（web は repository を呼ばない、トランザクションは service、DTO は record、コンストラクター注入） |

## 3. 結合テスト（`verify` の結合テスト、3種類の対象DB）

| 項目 | 確かめること | テスト |
|---|---|---|
| 認可（401・403・200） | パラメーターを使う表で、API と権限の組ごと。403 の利用者は要る権限だけを欠く人。作業ロールを切り替えた直後・割り当てを変えた直後の次の要求で切り替わる。停止中の利用者は通らない | `BusinessDataAuthorizationApiIT`・`BusinessDataWriteAuthorizationApiIT` |
| IDOR と一括代入 | スキーマ名・テーブル名・主キーの値・並べ替えの列・検索の列の差し替え（DSL に無い名前、権限の無いテーブル、無い行）は拒否され状態が変わらない。書けないカラムを本文に足すと 400。`definitionId` の差し替えは 409 | 同上・`AbstractBusinessDataWriteIT` |
| 主キーとビュー | 主キーの無いテーブルとビューの更新・削除はサーバーで拒否（409 `BUSINESS_ROW_OPERATION_NOT_ALLOWED`）。複合の主キー | `AbstractBusinessDataReadIT`・`AbstractBusinessDataWriteIT`・`TableAccessPolicyTest` |
| カラムの出し分け | 主権限 NONE のカラムは応答に含めない（画面で隠すだけにしない） | `TableAccessPolicyTest`・`AbstractBusinessDataReadIT` |
| SQL の組み立て | 識別子・検索の値・並べ替えに `'`・`"`・`;`・`--`・バッククォート・改行を含めても SQL として解釈されない。部分一致の `%`・`_`・`\`・`!` は文字として扱う | `AbstractBusinessDataReadIT`・`AbstractBusinessDataWriteIT`・`IdentifierMatchingPropertyTest`・`ContainsPatternPropertyTest` |
| 本文と値の上限 | 読み取り・削除 64 KB、作成・更新 4 MiB の境界（ちょうどは受け付け、超えると 413）。検索の値と主キー 1,000 字、書き込みの値 10,000 字の境界 | `BusinessDataRequestLimitIT`・`BusinessDataWriteRequestLimitIT`・`WriteRequestValidatorTest` |
| 時間の上限 | 一覧・詳細 5 秒、書き込みの文ごと 5 秒、pattern は値ごと 100 ms・合計 300 ms（注入した時計） | `BusinessDataTimeoutIT`・`BusinessDataWriteTimeoutIT`・`DeadlinePatternMatcherTest`・`DeadlinePatternTimingTest` |
| 漏えい（ログ） | TRACE を有効にして、行の値（主キーを含む）・検索の値・接続先・JDBC の例外のメッセージがアプリのログに出ない（ログは SQLState・業者の番号・例外のクラスの名前だけ） | `BusinessDataSecretLeakIT`・`BusinessDataWriteSecretLeakIT`・`TargetDbSecretLeakIT` |
| 漏えい（トレース） | 外部へ送る設定を有効にして、トレースの属性に値が無い（道は固定し、値は本文で運ぶ） | `BusinessDataTraceExposureIT`・`BusinessDataWriteTraceExposureIT` |
| 漏えい（応答） | 400・404・409・503 の応答に入力の値と主キーの値と例外のメッセージが無い | 上の SecretLeakIT と各 `XxxIT` |
| 漏えい（監査） | 監査の `detail` は主キーだけ（16,384 字で切り `keyTruncated`）。監査の書き込みの失敗の ERROR に `detail`・主キー・原因を載せない | `BusinessDataWriteAuditIT`・`AuditSecretLeakIT` |
| 接続と資格情報の分離 | 書き込みの API は書き込みのアカウント、読み取りの API は読み取りのアカウントの接続を使う。書き込みのアカウントは表を作る・消すことを DB に拒まれる（3種類）。資格情報は `.env` だけから受け取る | `TargetWriteTransactionManagerInjectionIT`・`AbstractBusinessDataWriteIT`・`TargetDbSampleInitScriptIT` |
| 書き込みの設定の欠け | ユーザー名とパスワードの片方だけなら項目の名前だけを WARN、値は出さない。起動と読み取りは続き、書き込みは 409 | `BusinessDataWriteNotConfiguredIT` |

## 4. 性質ベースのテスト（`verify` の中、失敗時の乱数の種を記録）

| 道具 | 対象 | 性質 |
|---|---|---|
| jqwik | 許す操作の関数 `TableAccessPolicy` | 権限を足しても許す操作は減らない、主キーの無いテーブルに行を1件に決める操作が出ない |
| jqwik | 識別子の照合 | DSL に無い名前は拒否される、引用符・`;`・改行を含む任意の文字列でも SQL の構文として解釈されない |
| jqwik | `WriteRequestValidator`・pattern の打ち切り | 境界の内側は受け付け外側は拒む、書けないカラムは必ず拒む、合計の締め切りを超えて照合が続かない |
| fast-check | 画面の写しの関数・書式・行のラベル・本文の写し・`rowActionsName` | BIGINT・DECIMAL の桁を失わない、変えないカラムが本文に入らない、`<`・`"`・`'` が文字のまま返る、見える文字が名前に含まれる |

## 5. 画面と E2E

| 項目 | 確かめること | テスト・検索 |
|---|---|---|
| 画面に出す値 | `<script>`・`<img onerror>`・`"`・`'`・`javascript:` を含む値と表示名が文字として出る。`dangerouslySetInnerHTML` を使わない（oxlint の `react/no-danger`）。リンクの先は道を作る1か所の関数だけ | 画面の部品のテスト（`BusinessDataTable.test.tsx` ほか） |
| 主キーを置かない | 道・履歴の状態（置くのは意味の無い印 `{ rowToken }` だけ）・ブラウザの保存・DOM の `id`・`data-testid`・`console` に主キーと行の値を置かない | コードの検索（下）、`businessDataStore.test.ts` |
| 画面の出し分けをサーバーの判定の代わりにしない | `operations` に無い操作のボタンが出ない、権限の無いテーブルへ直接移ると 403 の表示 | 画面の部品のテスト（判定はサーバーの結合テスト） |
| E2E の資格情報 | 対象DB の一時のロールの名前とパスワードはプロセスの環境変数で渡し、`webServer.env` に置かない。報告の部品が json の報告と `frontend/test-results/` を検索して見つかった件数 0 | `./gradlew e2eTest`（`playwright-secret-check-reporter.ts`） |
| 見本の PG のポート | 15432 を開けた直後に誤ったパスワードが拒まれる（scram）。流し終えたら閉じる | `./gradlew e2eTest` の前提の確かめ (d)、`nc -z 127.0.0.1 15432` が失敗すること |
| axe の記録 | 選択子（`target`）と値を出さず、規則・鍵（element・owner・marks・menuOwner・path）だけ | `axeElementKey.test.ts`、json の報告の検索 |

コードの検索（テストのファイルを除く本体）:

```bash
cd frontend
grep -rnE 'console\.|localStorage|sessionStorage|indexedDB|dangerouslySetInnerHTML|document\.title' \
  --include='*.ts' --include='*.tsx' --exclude='*.test.ts' --exclude='*.test.tsx' \
  src/features/tables src/shared/scroll-region src/shared/page-size src/shared/null-value src/shared/unsaved-guard
# 0 件であること（説明文の中の語は除いて読む）。no-console は ESLint でも止める（tables・3つの共通の部品・app/login-state・app/work-role）
grep -rc -e axe-core -e axe.run -e Deque dist | grep -v ':0$'   # npm run build の後。何も出ないこと
```

## 6. 合格の線と最後の結果（B7 の作業ブランチの上、レビューの直しの後）

| 検査 | 線 | 結果 |
|---|---|---|
| SpotBugs の関門 | 0 件 | 0 件（priority 2・3 の警告だけ） |
| OSV-Scanner | 失敗の条件 0 件 | 0 件（警告 18 件。開発用の `braces`・`brace-expansion`・`postcss-selector-parser`・`source-map-js`・`undici`、多くは vendor の lockfile） |
| Gitleaks | 0 件 | 0 件 |
| 結合テスト・性質ベースのテスト | 失敗 0 | 失敗 0 |
| E2E の報告の秘密の確かめ | 見つかった件数 0 | 0 件 |
| `dist` の axe-core | 0 件 | 0 件 |

## Sources

- 各単位の `nfr-requirements/security-requirements.md`・`nfr-design/security-design.md`
- 各単位の `code-generation/code-summary.md`・`generation-notes.md`（U3 の 10.6・10.10・11.4）
- `README.md`（1コマンドの検査・依存関係の脆弱性の判定・E2E の見本の PostgreSQL）、`backend/config/spotbugs-exclude.xml`
- `aidlc/spaces/default/memory/team.md` の Code Style（静的解析とセキュリティ検査）・Testing Posture、`project.md` の Forbidden・Mandated

## Assumptions & Open Questions

- [assumption] 6節の結果は作業ブランチの上の最後の回の値で、`develop` の今の形では流していない（Q1: B）。CI の `verify` が同じ検査を受け持つ。
- OSV-Scanner の警告 18 件の1つ `braces@3.0.3`（GHSA-vfj7-8cjw-p6xm、CVSS 8.7、開発用）は、team.md の決まりで警告にとどまる（成果物を作る道具の一覧に無い）。Dependabot の知らせの扱いとあわせて次の Intent で見る。
