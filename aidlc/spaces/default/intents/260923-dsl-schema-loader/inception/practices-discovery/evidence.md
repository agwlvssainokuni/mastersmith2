# Evidence — Practices Discovery

主担当（aidlc-pipeline-deploy-agent）の初稿、支援役3名（aidlc-quality-agent・aidlc-developer-agent・aidlc-devsecops-agent）の独立レビュー、依頼者との面談（`practices-discovery-questions.md`）を統合した根拠の記録。

## 前提

- プロジェクト種別: 既存のコードあり（Brownfield）、スコープ `classic`、Depth `Standard`、Test Strategy `Standard`、Change Control `relaxed`（`aidlc/spaces/default/intents/260923-dsl-schema-loader/aidlc-state.md`）。
- 再実行（Re-run）: `aidlc/spaces/default/memory/team.md` の5節は前の Intent（`260922-auth-audit-base`）で依頼者が確定済み。その後に学び2件（内部DB は H2 でテスト／フロントエンドの依存関係の脆弱性の判定）が追記されている。これを現在の基準とし、依頼者の答えで決まった点だけを直した。学びの2行は1文字も変えずに残した。
- 会話言語: 日本語（依頼の指示の `Conversation language` の行）。
- この段の全員が、`git` の読み取りとファイルの読み取りだけを行った。`./gradlew`・`npm`・`docker` は誰も実行しておらず、件数・時間・メモリ・カバレッジの実測値は無い。`.env` と鍵ファイルは開いていない。

## 参加者ごとに調べたこと・推測したこと

### 主担当（aidlc-pipeline-deploy-agent）

| # | 対象 | 分かったこと |
|---|---|---|
| E1 | `git branch -a`、`git log` | 作業ブランチは `develop`（`origin/develop`・`origin/main` あり）。工程の承認ごとに日本語のメッセージでコミットしている。`team.md` の Way of Working と食い違う形跡は無い |
| E2 | `.github/workflows/ci.yml` | `develop` へのプッシュ、`v*` のタグ、手動の実行で `./gradlew verify` を動かす。`ubuntu-latest`、上限 60 分。サブモジュールは固定先、Actions はコミットのハッシュ、Gitleaks・OSV-Scanner は版と SHA-256 で固定。WAR はコミットのハッシュの名前で保存し、タグではリリースに添付する |
| E3 | `build.gradle.kts` | `verify` は段 0〜9 を順に実行する。段 6 は「結合テスト（組み込みの H2）」。道具が無いときは黙って飛ばさず失敗させる（`requireTool`）。`osvScan` は Gradle の lockfile と2つの `package-lock.json` を対象にする |
| E4 | `backend/build.gradle.kts` | `*Test` と `*IT` を名前で分けて実行する。JaCoCo は `test.exec` と `integrationTest.exec` を合わせ、全体（BUNDLE）の規則1つで判定している |
| E5 | `.pre-commit-config.yaml` | pre-commit で Gitleaks・Spotless・Prettier。pre-push は置かない（`team.md` の「置いてもよい」と矛盾しない） |
| E6 | `.github/dependabot.yml` | gradle・npm・github-actions・docker を監視する。docker は `Dockerfile` が対象（推測: テストのコードの中のイメージは監視の外） |
| E7 | `compose.yaml`、`README.md` | アプリの `mem_limit` は既定 1g（`.env` で 2g）、手元の監視は 900m。README は「U1 のテストはコンテナの実行環境を必要としない」と書いている。対象DB のサービスは無い |
| E8 | `.claude/scopes/` の classic | `skeleton: on`。`team.md` の「walking skeleton の特別な手順を行わない」が優先する。骨格は既にある |
| E9 | コード知識ベース（`aidlc/spaces/default/codekb/mastersmith2/`） | 対象DB・DSL の既存コードが無い（C-1）。`DataSource` は内部DB の1つだけ（C-2）。JDBC ドライバー・Testcontainers・JSON Schema の検証・YAML の部品が無い（C-3・C-4）。Problem Details の拡張は `code`・`traceId` だけ（C-6）。要求の本文の上限 1MB（C-7） |
| E10 | 昇格の道具（`.claude/tools/aidlc-state.ts` の `practices-promote`） | `discovered-rules.md` の `## Mandated`・`## Forbidden` の中の、空行・`<!--` で始まる行・`#` で始まる行以外をすべて日付つきで `project.md` に追記する。そのため既存の規則は書かず、保留の候補は1行のコメントにした |

推測: 対象DB の3種類を毎回起動すると `verify` の時間と colima の VM のメモリが増える。DB ごとに違う処理は実際の DB で確かめないと分岐のカバレッジが届かない見込みがある。

### 品質（aidlc-quality-agent）

- 調べたもの: `backend/build.gradle.kts`・`build.gradle.kts`・`frontend/vitest.config.ts`・`frontend/playwright.config.ts`・`backend/src/test/resources/junit-platform.properties`・`.github/workflows/ci.yml`、コード知識ベース。
- 分かったこと: Methodology（test-after）と Ordering は今回もそのまま当てはまる。カバレッジの道具・下限・除外は `team.md` と一致。JaCoCo の規則は全体（BUNDLE）の1つだけで、新しいパッケージが下限を下回っても既存のコードで補われて通りうる。JUnit の並列の設定は無い（既定は直列）。
- 推測・指摘: MySQL・MariaDB は DDL が暗黙に確定して巻き戻せないため、「巻き戻す」はテストごとの一意のスキーマに置き換える必要がある（→ Q2）。対象DB のテストを別のタスクにするならカバレッジの計算に含める必要がある。`verify` で1種類だけにする案は「CI と同じ検査をローカルで」と食い違う（→ Q1 の選択肢に明記）。

### 開発（aidlc-developer-agent）

- 調べたもの: `backend/src/main/java/cherry/mastersmith/` の構成、`ArchitectureTest.java` と機能ごとの構造検査、`common/error/`、`application.yaml` と各 `XxxProperties`、`frontend/src/app/i18n/`・`frontend/src/app/registry/types.ts`・`frontend/src/features/README.md`、ライセンスヘッダーの検査スクリプト。
- 分かったこと: 層・DTO・Lombok なし・Problem Details・`toString` の伏せ字は実態と一致。食い違いが2つ: 1つの機能だけに効く設定は機能の中にある（例: `audit/service/AuditConfig.java`）／画面の文言は日英の2つで既定は日本語（`ja.ts`・`en.ts`）（→ Q8）。YAML・SQL・XML にも `#`・`--` などでヘッダーを手で付けている。
- 推測・指摘: 対象DB 用の `DataSource` を普通の Bean で足すと、自動構成の内部DB の `DataSource` が作られなくなるおそれがある。確かめ方は ArchUnit ではなく結合テスト。パッケージの切り方・JSON Schema の置き場・画面の `featureId`・共通部品の変更の可否は設計より前に判断が要る（→ 要件・設計の段に回した）。

### セキュリティ（aidlc-devsecops-agent）

- 調べたもの: `.github/workflows/ci.yml`・`.github/dependabot.yml`・`.pre-commit-config.yaml`・`.gitleaks.toml`・`.gitignore`・`.dockerignore`・`config/npm-build-tools.txt`・`build.gradle.kts`・`backend/build.gradle.kts`・`backend/config/spotbugs-exclude.xml`・`gradle/libs.versions.toml`・`gradle/wrapper/gradle-wrapper.properties`・`backend/gradle.lockfile`（該当の行）・`.env.example`（行の名前だけ）。
- 分かったこと: Gitleaks・SpotBugs＋FindSecBugs・OSV・Dependabot・lockfile・Actions のハッシュ固定は基準と一致。`spotbugsGate` は確からしさ（priority 1）で判定しており、SQL インジェクション系は priority 2 の警告にとどまりうる（→ Q7）。Gradle の依存の中身のハッシュ検証と Wrapper の `distributionSha256Sum` は無い。`compose.yaml` の監視のイメージと Testcontainers の `ryuk` のイメージも Dependabot の外。WAR はリリースで公開しており、同梱の部品は配布に当たる（MySQL Connector/J は GPL v2＋FOSS 例外、MariaDB Connector/J は LGPL 2.1、PostgreSQL JDBC は BSD 2-Clause）。
- 推測・指摘: 投入される YAML（別名の爆発・深い入れ子・タグ・重複キー）、JSON Schema の外部 `$ref`、対象DB の接続情報の漏えい、部品の例外のメッセージの漏えいを STRIDE で整理（→ Q5・Q9）。JDBC の危険な接続の属性の固定、読み取り専用の接続、識別子の許可の一覧、Gitleaks の JDBC の URL の規則も提案したが、面談では問わなかった（設計の段で扱う）。

## 面談の決定

| # | 決定 | 反映先 |
|---|---|---|
| Q1: D | 対象DB のテストをどこで実行するかは Build and Test で時間とメモリを実測してから決める。それまでは3種類すべてを `./gradlew verify` の中で毎回実行する（CI も同じ）を仮に置く | Testing Posture |
| Q2: A | テスト（またはテストのクラス）ごとに名前の重ならないスキーマを作り、終わったら消す。「巻き戻す」の文言を補う | Testing Posture（DB を使うテストの行） |
| Q3: B ＋ F1: A | コンテナの実行環境が無いときは警告を出して対象DB のテストだけ飛ばす。飛ばした状態では統合しない（colima を起動してやり直す）。飛ばしてよいのは開発中の途中の実行だけ。CI では必ず実行する | Way of Working、Testing Posture から参照 |
| Q4: C ＋ F2: B | 全体の合計に加えてすべてのパッケージごとに行 80%・分岐 70%。ただし既存のパッケージに単独で下回るものがあれば、パッケージごとの下限は新しく作るパッケージだけに当てる。除外は増やさない | Testing Posture（カバレッジの行） |
| Q5: A | 投入 DSL を信頼できない入力として扱う決まりと、それを確かめる「必ず書くテスト」を基準に入れる。数値は設計の段で決める | Code Style（静的解析とセキュリティ検査）、Testing Posture（必ず書くテスト） |
| Q6: C | 新しい依存のライセンスを確かめ、Apache License 2.0 と異なるものは採用の理由を設計の記録に残す。依存のハッシュ検証・Wrapper のハッシュ固定・イメージの更新手順は足さない | Code Style（静的解析とセキュリティ検査） |
| Q7: B | SpotBugs は priority 1 に加えて、`SQL_` で始まる指摘を priority にかかわらず止める。誤検知は理由を書いて除外する | Code Style（静的解析とセキュリティ検査） |
| Q8: A | 全体に効く設定は `config`、1つの機能だけに効く設定はその機能の中。画面の文言は日英を用意し、既定は日本語 | Code Style（言語と命名、バックエンド） |
| Q9: A, B, C, D | 固い制約4件（接続情報を出さず受け取らない／実際の DB でテストする／部品の例外のメッセージを応答に含めない／投入 DSL を信頼しない） | `discovered-rules.md` |
| 変更の依頼 | 「F2をBに変更。」（当初の F2: A を B に変えた） | 上の Q4 ＋ F2 の行 |
| まとめの確認 | Looks correct | — |

## 統合での判断

- `team.md` は全 Intent に共通の基準なので、初稿に書いた今回の Intent だけの行（「今回の Intent では骨格は既にある」、E2E の2本目、Flyway `V5`、画面の新しい部品、対象DB の `DataSource` の足し方など）と、面談で問わなかった初稿の追加の行（対象DB の設定の型の `toString`、管理画面の API の 401／403／200、性質ベースのテストの当て先の追加など）は入れなかった。依頼者が確かめた内容だけを基準の差にするため。
- Q4 の「すべてのパッケージごと」は、質問の文脈（バックエンド全体の合計1つで判定している）に合わせてバックエンド（JaCoCo のパッケージ単位）に当てるものとして書いた。フロントエンドはディレクトリ単位の下限を足していない。
- F2: B の「下回るものがあれば」を判定する時期は、パッケージごとの下限を入れる Bolt での実測とし、その結果を記録すると書いた。
- Q5 の決まりの本文は Code Style に、テストの一覧は Testing Posture の「必ず書くテスト」に分けて置いた。質問の前置きにあった `yes`・`on` が真偽値に化ける危険は、選択肢 A の決まりの一覧に無かったため、決まりにもテストにも入れていない（設計の段で扱う）。
- Q7 の既存の「重大度 High 以上で止める」の行は残し、SpotBugs の判定が確からしさ（priority）であることを新しい行の中に明記した。
- Q9 の4件は依頼者が選んだ文言のとおりに規則の行にした。候補「NEVER 対象DB のスキーマやデータを変更しない」は保留のコメント1行にした。

## 残る不確かさ

- 「適用」が対象DB に書き込むのか、アプリの中の DSL を切り替えるだけなのか（要件定義で決める）。決まるまで「対象DB を変更しない」の規則は保留。書き込むなら、書き込みの範囲とトランザクションの決まり（名前付きのトランザクションなど）が要る。
- 対象DB の3種類のテストの時間と colima の VM のメモリの実測値が無い。Build and Test で `:backend:cleanTest :backend:cleanIntegrationTest` を付けて実測し、実行の場所（Q1）を決める。CI の上限 60 分に収まるかも同時に確かめる。
- 既存のパッケージごとのカバレッジの実測値が無い。パッケージごとの下限を当てる範囲（すべてか、新しく作るものだけか）はその実測で決まる。
- Spring Boot 4 で2つ目の `DataSource` を足す方法（既定の注入候補にしない Bean、`@Primary`、操作のたびに作るなど）と、既存の JPA・Flyway・ヘルスチェック・監査が内部DB を指し続けることの結合テストでの確かめ（設計の段で決める）。
- 対象DB の版の範囲と、テストで確かめる版（要件で決める）。対象DB のイメージの版を固定する置き場（Dependabot の外になる点は依頼者が承知のうえで手順を足さなかった）。
- 利用者の投入の上限の数値と、要求の本文の上限 1MB との関係。JSON Schema の検証の部品が Jackson 3 系と組み合わせられるか。
- パッケージの切り方、JSON Schema の置き場、画面の `featureId`、共通部品（`common/error`・`shared/api-client`）の変更の可否、検証エラーを行・項目ごとに返す形、E2E の2本目（設計の段で決める）。
- 対象DB の接続情報の漏えいを確かめるテストの形（Forbidden の規則を置いたので、それを破ったら落ちるテストを設計の段で決める）。
- README の「U1 のテストはコンテナの実行環境を必要としない」の記述は、対象DB のテストが入ると実態と合わなくなる。直すのは対象DB のテストを入れる Bolt。
