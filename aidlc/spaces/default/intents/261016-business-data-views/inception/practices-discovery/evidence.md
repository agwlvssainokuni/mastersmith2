# 証拠と推定（Intent 261016-business-data-views）

> Practices Discovery の再実行の記録。リード（`aidlc-pipeline-deploy-agent`）の下書きに、支援役3名（品質・開発・セキュリティ）の独立した確かめと、依頼者の面談の答え（`practices-discovery-questions.md`、Q1〜Q14 はすべて A、まとめの確認は Looks correct）を取り込んだ。基準は `aidlc/spaces/default/memory/team.md` の 5 つの節（前の Intent までに承認済み）。

## 1. 前提

- Brownfield、範囲は classic、深さは Standard、Test Strategy は Standard（`<record>/aidlc-state.md`）。範囲を決める段（scope-definition）は SKIP で、Performance Validation は EXECUTE。
- 今回の Intent: 業務データの一覧・詳細の画面（J・K）、ロールの主権限・補助権限の業務データへの当て方（主キーの有無による操作の可否を含む）、テーブルを指すメニューの項目からの移り先。F2（`users.admin_flag` を縮める）を入れるかは要件定義で決める。持ち越しは make-you-chic-ui の固定先 `b0a1e79`、axe-core 4.14 の取り込み、不安定なテスト2件、`dslMixed` の `memory.peak` の物差し。
- `./gradlew`・npm・docker はリードも支援役も流していない。`.env` などの秘密情報は開いていない。

## 2. リードが読んだもの

| 読んだもの | 読み方 | 推したこと |
| --- | --- | --- |
| `aidlc/spaces/default/codekb/mastersmith2/` の `business-overview.md`・`architecture.md`・`code-quality-assessment.md`・`api-documentation.md`・`dependencies.md`・`component-inventory.md`・`code-structure.md` | 所見 K-49〜K-61 の本文を中心に読んだ | 業務データを読む・書く仕組みが無い（K-49）、書き込みを範囲に入れると接続・アカウント・境界テスト・待ちの上限・監査が大きく変わる（K-49・K-54）、業務データの API には役割・権限の必須のテストがすべて当たる（K-61）、axe-core 4.14 の取り込みの順（K-55・K-56・K-60）、不安定なテスト2件はテストの側の直しで足りる見込み（K-57・K-58）、`memory.peak` の物差しの変更は目標の測り方の変更に当たる（K-59）、F2 は列を消すだけでは済まない（K-53） |
| `backend/build.gradle.kts` の `packagesJudgedByTotal`（230〜238 行） | 読んだ | 一覧は 7 パッケージのままで、`team.md` の「2026-10-04 の時点」は中身は正しいが時点が古い（P14） |
| `frontend/eslint.config.js`（150〜200 行）と `frontend/src/features/registration/` の検索 | 読んだ・`authSession` を検索した | `useRegistration.ts` の `../auth/authSession` の import は無くなっている。残る `RegistrationPage.test.tsx` の import はテストのファイルで、規則の `ignores: TEST_FILES` により対象の外（P14） |
| `.github/dependabot.yml` | `ignore` の節を読んだ | axe-core の `>= 4.14.0` を見送り中で、外す時期は「規則の違反を直し、make-you-chic-ui の Dropdown の直しを取り込んだ後」（P9） |
| `.github/workflows/` | 一覧だけ | CI は `ci.yml` の1本 |
| `frontend/e2e/` の一覧 | 一覧だけ | E2E は 010〜170 の 18 ファイルと `support`。業務データの流れは無い。`170-menu-navigation-flow` は準備中の置き場への移り先に依る（K-52） |
| `README.md` の E2E の節（83〜95 行） | 読んだ | E2E は WAR と一時の内部DB と Mailpit（届かなければ失敗）で動き、対象DB を前提にしていない（P2） |
| `aidlc/spaces/default/intents/261015-deps-ui-carryover/make-you-chic-ui-request-4.md` | 先頭を読んだ | 依頼の文面を Intent の記録に置く形が既にある（P10） |
| `git log --oneline -25`・`git branch -a`・`git submodule status` | 読み取りだけ | `develop` への squash と固定先の更新の fast-forward が混ざる形、`main` は fast-forward。make-you-chic-ui の固定先は `a9309c4`。基準の Way of Working と食い違いは無い |
| `project.md` の学び（2026-10-15 の E2E の時点、CI の不安定なテスト、`memory.peak`） | 読んだ | 前の Intent の E2E の時点の決定は `team.md` との差として記録された。毎回の差の記録を避けるため決まりにするかを問う（P1） |

## 3. 支援役の確かめ

### 3.1 品質（`aidlc-quality-agent`）

- 読んだもの: 下書き3つ、`code-quality-assessment.md`（K-57〜K-59）、`backend/build.gradle.kts`（JaCoCo の下限・`integrationTest`・テストの JVM のヒープ `1536m`）、`frontend/vitest.config.ts`（`thresholds` は全体の lines 80・branches 70 だけ）、`frontend/playwright.config.*`（`workers: 1`・`retries: 0`）、`.github/workflows/ci.yml`（`timeout-minutes: 60`）、`docker/perf/compose.yaml`、既存の `*ConnectionUsageIT`。
- 推したこと: P1 の判定をパスで決める（Q1 の A に入った）。P2 の資格情報をプロセスの環境変数で渡し報告の json を検索する、見本の行を架空の値にする（Q2 の A に入った）。P4 に接続の数の結合テスト・性質ベースのテスト・403 と 404 の使い分け★を足す（Q3 の A に入った）。P9 で E2E の全件の通過と違反 0 件を件数で記録する（Q9 の A に入った）。
- 足した論点: Q1 verify の時間と資源の測り直し（Q14 に入った）、Q2 負荷の試験の見本のデータ（Q14 に入った）、Q3 フロントエンドのカバレッジの下限（Q14 の A で今の形のまま値を記録）、Q4 対象DB の結合テストのデータの片付け（質問にせず「決まっていること」にした）。
- 異論（OBJECT）: P11 の「直す前のテストが落ちることを必須にする」は、時間の重なりで起きる失敗を起こせないときに進めなくなる。扱い: 二段の確かめを Q11 の推奨にし、依頼者が A を選んだ。

### 3.2 開発（`aidlc-developer-agent`）

- 読んだもの: K-49〜K-51・K-54・K-61、`backend/src/main/java/cherry/mastersmith/` の機能の一覧と `targetdb/`、`common/observability/TraceAspect.java`、`*ProblemTypes.java` の置き場、`frontend/src/features/README.md`・`frontend/src/shared/`・`frontend/src/features/tables/`、`frontend/package.json`。
- 推したこと: P4 に道に乗る主キーの値のエンコードと、行の値・カラムの表示名を文字として描くことを足す（Q3 の A に入った）。P8 で code の置き場と `ACCESS_DENIED` の使い回し（Q8 の A に入った）。
- 足した論点: P15 対象DB のトランザクションと時間の上限（Q5 の A に入った）、P16 行の値の受け渡しの形（Q8 の A に入った）、P17 DB の種類ごとの SQL の置き場（Q8 の A に入った）、P18 画面の側の置き場（Q8 の A に入った）。
- 異論（OBJECT）: (1) Mandated の「見せないカラムを応答に含めない」は、カラムの主権限 NONE に限るべき（DSL の表示の設定は見せ方で権限ではない）。扱い: Q4 の推奨にし、依頼者が A を選んだ。(2) P6 の「`SQL_` の除外を `SqlIdentifiers` の1か所に限る」は、指摘が問い合わせを組み立てて実行するクラスに出るため効かない。扱い: Q6 の推奨を「組み立てて実行する部品を1つに閉じ、除外はクラス・メソッド・パターン名で絞る」に直し、依頼者が A を選んだ。

### 3.3 セキュリティ（`aidlc-devsecops-agent`）

- 読んだもの（読み取りだけ）: `.github/dependabot.yml`・`.github/workflows/ci.yml`、`build.gradle.kts` の `gitleaksScan`・`osvScan`、`backend/build.gradle.kts` の `spotbugsGate`、`backend/config/spotbugs-exclude.xml`、`.gitleaks.toml`、`config/npm-build-tools.txt`、`targetdb/`（`TargetDataSourceConfig`・`SqlIdentifiers`・`*SchemaQueries`）、`UrlQueryStrippingObservationFilter.java`、`gh api` の読み取り（リポジトリの安全の設定）。
- 確かめた今の守り: CI の actions はコミットの SHA で固定、Gitleaks・OSV-Scanner は版と SHA-256 で固定、既定の `permissions` は `contents: read`。secret scanning と push protection は有効。`spotbugs-exclude.xml` の除外は `PREDICTABLE_RANDOM` の1件だけで `SQL_` の除外は無い。対象DB の接続は `setReadOnly(true)` と `SELECT` だけのアカウントの二重の守り。変える候補は無い。
- 推したこと: P5 で付与を DSL の対象のスキーマに限る（Q5 の A と Mandated に入った）。P7 で JDBC の例外のメッセージをログにも出さない（Q7 の A に入った）。P13 で内部DB の複写をリポジトリの外に置きコミットしない（Q13 の A に入った）。P9 で axe-core の版が lockfile で1つにそろうかを確かめる（Q9 の A に入った）。
- 足した論点: S1 サブモジュールの固定先を上げるときの供給の連鎖の確かめ（Q9 の A に入った）、S2 必須のテストの一覧に画面に出す値・検索のワイルドカード・取り出す行の上限を足す（Q3 の A に入った）、S3 `prepareStatement`・`createStatement` を呼んでよい場所を ArchUnit で絞る（Q6 の A に入った）。動的な検査（OWASP ZAP）は今の基準どおり採用しない（配備先が決まったときの見直しの論点）。
- 異論（OBJECT）: (1) P6 の除外の置き場（開発と同じ指摘）。扱いは 3.2 と同じ。(2) P7 の Forbidden の「主キーの値をトレースの属性に含めない」は、`UrlQueryStrippingObservationFilter` が道の部分（`url.path`・`http.target`）を残すため、主キーを道に置く設計と食い違う。扱い: Forbidden は保ち、主キーを道に置かないか道を雛形に置き換えるかを設計の段で決め、結合テストで確かめる形を Q7 の推奨にし、依頼者が A を選んだ（主キーの値を Forbidden から外す B は選ばれなかった）。

## 4. 面談の決定

### 4.1 決まっていること（質問にしない）

- 機能の中身（作成・更新・削除を範囲に入れるか、主キーの無いテーブルで詳細を見せるか、ページの大きさ・並べ替え・検索、監査に残す中身、F2 を入れるか、`memory.peak` の物差しそのもの、機能の置き場の名前）は要件定義・設計の段で決める。
- 古くなった記述を直す（P14）: `packagesJudgedByTotal` の時点を「2026-10-16 の時点でも 7 パッケージ」に直し、Code Style の「今ある違反の1件」の文を、直ったこととテストのファイルは規則の対象の外であることに書き換える。
- 対象DB の読み取りのテストでも表を作るため、テスト（またはクラス）ごとに名前の重ならないスキーマを作って消す決まりが当たり、途中で止まった前の実行の残りに頼らない（品質の Q4）。
- 前の Intent までの学び（`project.md`）はそのまま当てる。

### 4.2 答え

| 問い | 答え | 反映先 |
| --- | --- | --- |
| Q1 E2E を統合の前に流すか | A 変えたパスで分ける | `team-practices.md` の Way of Working（E2E の行）と Testing Posture（E2E の行） |
| Q2 業務データを見せる E2E の前提と本数 | A 見本の PostgreSQL を前提、資格情報はプロセスの環境変数、代表の流れ1本まで | Testing Posture |
| Q3 業務データの画面の必須のテストの一覧 | A 役割・権限と同じ扱いの一覧を足す | Testing Posture |
| Q4 業務データの見せ方を固い制約にするか | A 2つとも固い制約、見せないカラムは主権限 NONE に限る | `discovered-rules.md` の Mandated の2行、Testing Posture のカラムの出し分け |
| Q5 対象DB へ書き込む場合のテストと接続 | A 接続・資格情報を分け、行の操作だけを与え DDL を与えない（固い制約） | `discovered-rules.md` の Mandated の1行、Testing Posture、Code Style のバックエンド |
| Q6 SQL の組み立てと `SQL_` の除外 | A 組み立てて実行する部品を1つに閉じ、除外を3つで絞り、ArchUnit で実行の場所を絞る（固い制約） | `discovered-rules.md` の Forbidden の1行、Code Style の静的解析 |
| Q7 業務データの行の値の漏えい | A 主キーの値を含めて Forbidden、JDBC の例外の文をログにも出さない | `discovered-rules.md` の Forbidden の1行、Code Style のバックエンド |
| Q8 機能の置き場とコードの書き方 | A 置き場・境界テスト・code・DB の差・型の受け渡し・画面の置き場を決まりにする | Code Style のバックエンドとフロントエンド |
| Q9 axe-core の違反と固定先の更新の確かめ | A 規則を外して通さない（固い制約）、件数を記録、版の一致、上流から辿れることと lockfile の差 | `discovered-rules.md` の Forbidden の1行、Testing Posture、Way of Working |
| Q10 make-you-chic-ui への依頼（5） | A 画面イメージの段で一覧にし、文面を記録に置き、機能設計の承認の前に出す | Way of Working |
| Q11 不安定なテストを直したときの確かめ | A 二段にする | Testing Posture の不安定なテストの決まり |
| Q12 物差しを変えるときの決まり | A 要件定義の明示の決定だけ、前の値を並べる | Testing Posture |
| Q13 縮める側の戻し方 | A 配備を分け、戻しは配備の前の複写から、複写はリポジトリの外 | Deployment |
| Q14 テストの時間と資源・カバレッジの記録 | A 約 42 分を超えたら諮る、フロントエンドは合計のまま値を記録、負荷の試験の行は台本の種から | Testing Posture |
| まとめの確認 | Looks correct | 全体 |

## 5. 基準（team.md）からの差

- Way of Working: サブモジュールの固定先を上げる前の確かめ（上流の `main` から辿れること、vendor の lockfile の `hasInstallScript` の差、増えたら諮る）を足した。make-you-chic-ui への依頼の文面の置き場と時点（画面イメージの段で一覧にし機能設計の承認の前に出す、上流のソースで確かめる）を足した。E2E を統合の前に流すかを変えたパスで決める行を足した。
- Walking Skeleton: 変更なし。
- Testing Posture: Methodology（test-after）と Ordering は変えていない。`packagesJudgedByTotal` の時点を 2026-10-16 に直した。フロントエンドのカバレッジは合計のまま新しい機能の値を記録する行、対象DB の読み取りのテストの片付け、書き込みのテスト、`verify` と CI の時間の記録（約 42 分）、E2E の行の時点を Way of Working に寄せた直し、業務データの E2E の前提と本数、不安定なテストの二段の確かめ、業務データの必須のテストの一覧、axe-core を上げるときの扱い、物差しを変えるときの決まり、負荷の試験の行の作り方を足した。
- Deployment: 広げてから縮める二段の「縮める」側の戻し方を足した。
- Code Style: バックエンドに業務データの機能の置き場と境界テスト、code の置き場、DB の種類ごとの SQL の差の閉じ方、行の値の型の受け渡し、行の値の伏せ字と JDBC の例外の文をログに出さないこと、問い合わせの時間の上限、書き込みの接続とトランザクションを足した。フロントエンドの機能どうしの import の行を、違反が直ったことに書き換え、業務データの画面の置き場と型を足した。静的解析に業務データの SQL の部品と `SQL_` の除外の絞り方、ArchUnit での絞り込みを足した。
- 固い制約: Mandated 3行・Forbidden 3行（`discovered-rules.md`）。`project.md` の既存の行（役割・権限の判定は API ごとにサーバー側、JDBC の例外のメッセージをそのままエラー応答に含めない、対象DB の接続情報を画面・API・DSL から受け取らない など）とは重ねていない。

## 6. 採らなかった案と、基準に入れなかった提案

- 面談で選ばれなかった案（B・C）は入れていない。特に、E2E の本数を2本にする案（Q2 の B）、表示の設定で隠すカラムも応答に含めない案（Q4 の B）、主キーの値を Forbidden から外す案（Q7 の B）、フロントエンドに機能ごとの下限を当てる案（Q14 の B）、make-you-chic-ui の対応待ちの間だけ違反の要素を外してよい案（Q9 の B）は採らなかった。
- 支援役の提案のうち面談の答えに含めなかったものは、基準に入れず次の段に回す。
  - 品質: 書き込みの失敗（一意の違反・外部キーの違反・時間切れ）を3種類の DB で確かめる行、`SQL_` の除外の件数を Build and Test で記録して比べること、取り込んだ make-you-chic-ui の部品の振る舞い（並べ替え・ページ送り・行の操作）を画面のテストと E2E で確かめること、縮める前の版の動きを結合テストでも確かめること、物差しを変えた回に複数回流すこと（`project.md` の学びで既に当たる）。
  - 開発: 行の値の型の JSON への書き出しを `toString` と別の経路にすること、MySQL と MariaDB を同じクラスで足りるか確かめてから決めること、権限の判定の結果を `useWorkRole()` の `revision` で読み直すこと（`features/README.md` の今の決まり）、axe-core を上げるコミットで `.github/dependabot.yml` の `ignore` の行とコメントを外すこと。
  - セキュリティ: 並べ替えの向きを列挙で受けること、SQL を組み立てる外部の部品（jOOQ など）を足さない形を先に検討すること（足すなら今の基準のライセンスと依存の木の確かめ）、`TargetDbBoundaryArchitectureTest` を緩めるときに代わりの守りを計画に並べること、java-mustache-processor の固定先を上げるときの確かめ（上流から辿れることは今回の Way of Working の行で当たる）。

## 7. 要件・設計の段に回す論点

- 業務データの作成・更新・削除を範囲に入れるか（入れないなら書き込みのテスト・接続の行と Mandated の書き込みの行は当たらない）。
- 主キーの無いテーブルで詳細を見せるか、主キーが複数のカラムのときの道の形、主キーの値を道に置くか道を雛形に置き換えるか。
- DSL の表示の設定（`list.visible`・`detail.visible`）で隠すカラムを応答に含めるか。
- 一覧のページの大きさの上限・並べ替え・検索の条件、取り出す行の上限、問い合わせの時間の上限、バイナリと大きな文字列の上限の値。
- 403 と 404 の使い分け。
- 業務データの操作を監査に残すか（読み取りを含めるか）、残す中身、対象DB の確定と監査の行の一方だけが残ったときの扱い（K-54。`project.md` の Decided の監査の共通化の宿題を含む）。
- F2 をこの Intent に入れるか（K-53）。
- `memory.peak` の物差しを変えるか（K-59）。
- 業務データの機能の置き場の名前、`role` と `targetdb` の境界との関係（K-49・K-50）。
- 負荷の試験の対象DB の行の数（NFR 要件の段）。
- 業務データの 403 が増えたときの警報 `ms-forbidden` のしきい値（K-61）。

## 8. 残る不確かさ

- 業務データの結合テストを3種類の対象DB に足したときの `verify` の時間・テストの JVM のヒープ・colima の VM のメモリ・CI の時間は測っていない。約 42 分の線は Bolt の後と Build and Test の実測で判定する。
- axe-core 4.14 の違反の要素の `target` は確かめていない。「すべて直してから上げる」の作業の量と、make-you-chic-ui への依頼が要るか（Topbar の部品の見込み、K-55・K-56）は見積もれていない。
- `b0a1e79` への固定先の更新で vendor の lockfile に `hasInstallScript` の項目が増えるかは、まだ確かめていない（固定先を上げる Bolt の前に確かめる）。
- Q1 のパスの一覧（`auth`・`access`・`role`・`group`・`navigation`・`common.security` と業務データの機能）は、業務データの機能の置き場の名前が設計の段で決まった後に読み替える。
