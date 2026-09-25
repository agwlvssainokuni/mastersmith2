**Collaborator:** aidlc-developer-agent

## Contribution

開発担当（aidlc-developer-agent）の独立の確かめ。主担当の初稿（`team-practices.md`・`discovered-rules.md`・`evidence.md`）を、コード・ビルドの設定・コード知識ベースと突き合わせた。行ったのはファイルの読み取りと `git log` の読み取りだけで、`./gradlew`・`npm`・`docker` は実行していない。隣のリポジトリ `../java-mustache-processor` も読み取りだけ。`team.md` の5節は依頼者が確認済みの基準として扱い、今回の Intent（招待メール・利用者の登録・プリファレンス画面）で進め方として決め直す必要がある点だけを挙げる。機能の中身は要件・設計の段に回す。

### 1. 確かめた事実（証拠）

| # | 対象 | 分かったこと |
|---|---|---|
| V1 | `frontend/package.json` の `dependencies`、`.gitmodules` | make-you-chic-ui は Git サブモジュール `vendor/make-you-chic-ui` を npm の `file:` の依存（`file:../vendor/make-you-chic-ui/packages/make-you-chic-ui`）で使っている。Gradle の composite build（`includeBuild`）はどこにも無い |
| V2 | `settings.gradle.kts`、`backend/build.gradle.kts` の `dependencyLocking`、`build.gradle.kts` の `osvLockfiles`、`gradle/libs.versions.toml` | 取得元は Maven Central だけ（`FAIL_ON_PROJECT_REPOS`）。バックエンドは `lockAllConfigurations()` で `backend/gradle.lockfile` に版を固定し、OSV-Scanner はその lockfile と npm の2つの lockfile だけを見る。依存の版は `gradle/libs.versions.toml`（版のカタログ）に書く形で、Java は `25`、Spring Boot は `4.1.1` |
| V3 | `../java-mustache-processor` の `build.gradle.kts`・`cherry-mustache-core/build.gradle.kts`・`settings.gradle.kts` | Java のツールチェーンは 25（mastersmith と同じ）。`cherry-mustache-core` の実行時の依存は `org.slf4j:slf4j-api:2.0.16` だけ。core のビルドは `org.owasp.dependencycheck` のプラグイン（10.0.4）を使い、`pluginManagement` の指定が無いため Gradle Plugin Portal から取る形。lockfile は無い。タグは `0.1.0`。README は「公式の Mustache 仕様にフル準拠」としている |
| V4 | `backend/src/test/java/cherry/mastersmith/ArchitectureTest.java` | 全体に当たる層の決まりは5つ: `..web..` は `..repository..` に依存しない、`@Transactional` は `..service..` の外に置かない、コントローラーは `@Entity` を返さない、フィールド・メソッドへの注入の注釈を使わない、Lombok に依存しない。パッケージの名前の形で当てるため、新しい機能のパッケージにも自動で当たる。DTO の名前（`XxxRequest`・`XxxResponse`）と `record` であることを確かめる決まりは無い |
| V5 | 機能ごとの境界の検査（`auth/AuthBoundaryArchitectureTest.java` ほか `audit`・`dsl`・`dslmanage`・`targetdb` の計6クラス） | `auth` は `user` の `@Entity` と `user.repository` に依存しない、`user` は `auth` に依存しない、`auth` と `user` は `audit` に依存しない、`User.getPasswordHash` は `user` の中だけで呼ぶ、が検査されている。逆向きの知らせは Spring の出来事（`UserCreatedEvent` を `auth` が受ける、各機能の出来事を `audit` が受ける）で行っている（`architecture.md`） |
| V6 | `backend/src/main/java/cherry/mastersmith/` の下位パッケージ | `team.md` は各機能を `web`・`service`・`domain`・`repository` に分けるとしているが、実際には `dsl/parse`・`dsl/validate`・`dslmanage/generate`・`targetdb/config` など、4層に当たらない用途の名前の下位パッケージがある。`user` には `web` が無い（今は API が無いため） |
| V7 | `common/error/domain/ProblemType.java`・`ProblemTypeCatalog`、`*ProblemTypeCatalog`（`auth`・`access`・`dslmanage`・`common` の service） | エラーの種類は `code`（`^[A-Z][A-Z0-9_]*$`）と状態コードを1つに固定し、題・説明を日英の `LocalizedText` で持つ。機能ごとに `XxxProblemTypeCatalog` を service の層に置いて登録する形。`team.md` の Code Style はこの形（機能ごとの一覧、1つの code に1つの状態コード、日英の文言）までは書いていない |
| V8 | `common/error/web/ProblemBaseUrlResolver.java`、`application.yaml` の `mastersmith.web.base-url` | 設定のベースURL（`MASTERSMITH_WEB_BASE_URL`、既定は空）が無いときは、要求のスキーム・`getServerName()`・ポートから組み立てる。つまり既存の部品は要求の Host から URL を作る道を持つ |
| V9 | `auth/domain/RefreshToken.java` | リフレッシュトークンは値を持たず、SHA-256 のハッシュ（`token_hash`、32 バイト）だけを内部DB に保存している |
| V10 | `backend/build.gradle.kts` の Spotless（`target("src/**/*.java")`）、ルートの Spotless（`*.gradle.kts`）、`frontend/scripts/check-license-header.mjs`（`.ts`・`.tsx`・`.js`・`.mjs`・`.cjs`・`.css`・`.html`、`frontend/` の中だけ） | ライセンスヘッダーを機械で確かめているのは Java・Gradle の Kotlin DSL・画面のファイルだけ。`backend/src/main/resources/` の SQL（`--`）・YAML（`#`）・XML（`logback-spring.xml` は `<!-- -->`）は、どれも手でヘッダーを書いているが検査の対象外。メールのテンプレートに限らず、バックエンドの資源のファイル全体が検査の外にある |
| V11 | `backend/src/main/resources/META-INF/third-party-licenses/README.txt` | Apache License 2.0 と違うライセンスの部品（MariaDB Connector/J の LGPL 2.1 など）を同梱するとき、jar にライセンスの文書が無いものは文書をここに置き、一覧を README に書く形が既にある |
| V12 | `frontend/src/features/README.md`、`git log -- frontend/src/app` | README は「後の単位は features の下に機能のディレクトリと `registration.ts` を置く。U1 のファイルは書き換えない」としているが、U2 の実装（`4b12fdb`）で `frontend/src/app` は既に変えられている。プリファレンス（テーマの `system`・表示言語の切り替え、K-4・K-5）は `App.tsx` の `ThemeProvider` と `src/app/i18n` に手を入れる見込み（推測） |
| V13 | `.gitattributes`・`.editorconfig` | すべてのファイルが UTF-8・LF・末尾の改行ありになる。新しい拡張子（`.mustache` など）も追加の設定なしで当たる |

### 2. 初稿への訂正

- **C1（`team-practices.md` Code Style の java-mustache-processor の行、`evidence.md` P1 の候補）**: 「Git サブモジュールとして取り込み Gradle の composite build で組む（make-you-chic-ui と同じ形）」は事実と違う。make-you-chic-ui は npm の `file:` の依存で、Gradle の composite build はこのリポジトリで前例が無い（V1）。「サブモジュールで取り込む点は make-you-chic-ui と同じだが、Gradle の composite build は新しい形」と書き直すのがよい。
- **C2（同じ行の条件「どの形でも lockfile・脆弱性検査・更新の通知の対象から外れないこと」）**: 候補の3つのうち、文字どおりこの条件を満たすのは Maven の置き場から取る形だけと見られる。選択肢ごとに何が外れるかを並べて依頼者に選んでもらうのがよい。

| 候補 | 良い点 | 外れるもの・手間（事実と推測を分ける） |
|---|---|---|
| A. 部品の側で Maven Central に公開してから使う | 今の決まり（Maven Central だけ・lockfile・OSV・Dependabot）がそのまま当たる | 部品の側に公開の流れ（署名・名前空間の確認）が要る。group の `cherry.mustache` は名前空間の確認のため `io.github.<利用者名>` などに変える必要が出る見込み（推測。Maven Central の決まりは確かめていない） |
| B. サブモジュールで取り込み Gradle の composite build で組む | 部品の変更をすぐ試せる。サブモジュールの固定先の更新は既存の Mandated（専用のコミット）で扱える | 部品そのものは `gradle.lockfile` に載らず OSV の対象外になる見込み（推測: Gradle は取り込んだビルドの部品を lockfile に書かない）。部品の側のプラグイン（dependencycheck など）が Gradle Plugin Portal から取られ、Maven Central だけの決まりの外に出る（V3 の事実からの推測）。Dependabot は今 `gitsubmodule` を見ていない |
| C. リリースの JAR をリポジトリに置き SHA-256 で固定する | 取得元の決まりを変えずに済む | ファイルの依存は lockfile・OSV・Dependabot のどれにも載らない（推測）。POM が無いため推移依存（`slf4j-api`）を手で書く必要がある |

  どれを選んでも、java-mustache-processor 自身は Apache License 2.0 なので ADR は不要（`team.md` の決まり）。Java 25 で mastersmith と合う（V3）。

### 3. 初稿への追加（Code Style・Testing Posture の候補）

以下は、今の実装で既に成り立っていて `team.md` に書かれていない決まりと、今回の Intent で初めて出る種類のファイルの扱い。A 群は事実を書き足すだけなので確認だけでよく、B 群は依頼者の判断が要る。

**A 群（実装の記録。面談では確認だけ）**

- **A1（Code Style・バックエンド）**: 4層に当たらない部品（読み込み・検証・生成・外部への送信など）は、機能の中の用途の名前の下位パッケージ（例: `dsl/parse`・`dslmanage/generate`）に置いてよい。ArchUnit の層の決まり（`web` は `repository` を呼ばない、トランザクションは `service` だけ）は下位パッケージにもそのまま当たる（V4・V6）。今回のメールの送信とテンプレートの描画もこの形に乗る見込み（どの機能のどの名前にするかは設計の段）。
- **A2（Code Style・バックエンド）**: 機能の間の依存の向きは機能ごとの境界の検査（`XxxBoundaryArchitectureTest`）で固定し、逆向きの知らせは Spring の出来事（`ApplicationEvent`）で行う（V5）。今回は、パスワードの変更の後に `auth` が持つリフレッシュトークンに触れる場合（K-8）や、登録の完了を監査に残す場合に、`user` から `auth`・`audit` へ直接呼べない決まりが効く。
- **A3（Code Style・バックエンド、エラー応答の行に足す）**: エラーの種類は機能ごとの `XxxProblemTypeCatalog`（service の層）に登録し、1つの `code` に1つの状態コードを固定し、題と説明を日英で持つ（V7）。前の Intent の Contract Design で同じ code を 404 と 409 に使う形がレビューで指摘された（`project.md` の学び）ため、決まりとして明記する価値がある。

**B 群（依頼者に決めてもらう隙間）**

- **B1（Code Style・既存の境界の検査を変えるとき）**: 既存の ArchUnit の決まり（全体・機能ごと）を緩める・消す・例外を足す変更は、コード生成の計画に明記して依頼者の承認を得る、を決まりにするか。今回は V5 の `user` → `auth`・`audit` の禁止に当たりうる。過去にも既存のテストを計画の外で変えて後から確かめた例がある（`project.md` の `AuditSecretLeakIT` の学び）。
- **B2（Code Style・言語と命名）**: `team.md` の「画面の文言は日本語と英語の両方を用意し、既定を日本語とする」は画面だけを言っている。メール（件名・本文）とエラー応答の文言も同じ扱い（日英を用意する）にするか。エラー応答は既に日英（V7）。どちらの言語で送るか（招待する人の言語か、受ける人の言語か）は要件の段。
- **B3（Code Style・ファイルの置き方とライセンスヘッダー、初稿の `{{! ... }}` の行への補足）**: 初稿の「送るメールに出ない形（Mustache のコメント `{{! ... }}`）」に賛成する。Mustache の仕様では、行を占めるだけのコメントは出力から行ごと消える（仕様の Comments の Standalone・Multiline Standalone の決まり。java-mustache-processor が仕様に準拠していることは README の記述からの推測で、実際に確かめるのは設計かコード生成の段）。そのうえで、検査の範囲は「メールのテンプレートだけ」ではなく「`backend/src/main/resources/` の資源のファイル全体（SQL・YAML・XML・テンプレート）」として問うのがよい。今もそれらは手で書いたヘッダーに頼っていて検査の外にある（V10）。受け手に届かないファイル（`logback-spring.xml` など）は今の `<!-- -->` のままでよい。テンプレートの置き場は、既存の `db/migration`・`dsl/` と同じく資源の直下の用途のディレクトリ（例: `mail/`）にするのが今の形に合う（名前と日英の持ち方は設計の段）。
- **B4（Testing Posture・メールの本文のテスト、初稿の 64 行目への補足）**: 初稿の「招待の URL は設定したベースURLから組み立て、要求のヘッダーから組み立てない」に賛成する。既存の `ProblemBaseUrlResolver` は設定が無いと要求の Host から組み立てる（V8）ので、招待の URL にこの部品をそのまま使うと決まりに反する。テストの項目に「ベースURL の設定が無いときに、要求の Host を変えても招待の URL に出ない」ことを足すのがよい（設定が無いときに送らないか・起動を止めるかは要件の段、K-9）。
- **B5（Code Style・新しい依存、既存の決まりの当てはめの確認）**: メールの送信の部品（Spring Boot のメールの仕組みが引く Jakarta Mail・Eclipse Angus Mail）は、Apache License 2.0 と違うライセンス（EPL-2.0 などの見込み。推測で、採用の前に確かめる）と見られ、`team.md` の決まりどおり ADR が要る。同梱の文書は V11 の置き場の形に従う。テスト用のメールの受け手の部品も同じく採用の前にライセンスを確かめる。決まり自体は足りているので新しい質問は不要で、設計の段の作業として引き継ぐ。

### 4. 質問にしないでよいと確かめたもの

- 命名（Java・TypeScript の慣習、`XxxRequest`・`XxxResponse` の `record`、`XxxTest`・`XxxIT`）: 既存の決まりで足りる。DTO の名前は ArchUnit で検査されていない（V4）が、今のコードは決まりどおり（`LoginRequest`・`ApplyRequest` など）で、今回の Intent に特有の問題ではない。
- 新しい画面の置き方: `frontend/src/features/<featureId>/registration.ts` の形で足りる。プリファレンスで `src/app` に手を入れることは U2 に前例がある（V12）。`features/README.md` の「U1 のファイルは書き換えない」の文言をどうするかは設計の段の作業でよい（`team.md` には書かれていない）。
- 文字コード・改行・フォーマッタ: 新しい拡張子にも `.editorconfig`・`.gitattributes` が当たる（V13）。
- 招待のトークンの保存: 既存のリフレッシュトークンが SHA-256 のハッシュだけを保存している（V9）ので、同じ形は前例から導ける。固い制約にするかは依頼者の判断（初稿の候補のとおり）。

### 5. 要件・設計の段に回す論点（初稿の一覧への追加）

- メールの送信とテンプレートの描画を置く機能と下位パッケージの名前（A1 の形の中で）。
- パスワードの変更の後のリフレッシュトークンの無効化を、出来事で行うか別の機能で束ねるか（A2 の境界の決まりの中で）。
- テンプレートのファイル名・日英の持ち方・件名の取り方（B3 の置き場の中で）。
- java-mustache-processor の `{{ }}` が HTML をエスケープすること、行を占めるコメントが消えることを、設計かコード生成の最初に小さく確かめる。

## Positions

- AGREE: `team.md` の5節の本文をそのまま引き継ぎ、「（確認待ち）」の行だけを足す初稿の進め方 — 再実行で依頼者が確認済みの基準を守れる。
- AGREE: java-mustache-processor の取り込み方をチームの決まりとして面談で問うこと（P1） — 取得元を Maven Central だけにした決まりと部品の公開の形が合わず、証拠だけでは決まらない（V2・V3）。
- OBJECT: 取り込み方の候補の「Gradle の composite build で組む（make-you-chic-ui と同じ形）」 — make-you-chic-ui は npm の `file:` の依存で、composite build は前例が無い（V1、訂正 C1）。
- OBJECT: 取り込み方の条件「どの形でも lockfile・脆弱性検査・更新の通知の対象から外れない」 — 候補 B・C はこの条件を満たせない見込みで、選択肢ごとに外れるものを示して問うべき（訂正 C2）。
- AGREE: メールのテンプレートのヘッダーを `{{! ... }}` で書き、HTML のコメントを使わない案 — HTML のコメントは受け手に届き、Mustache の行を占めるコメントは出力から消える（B3）。
- OBJECT: ヘッダーの検査の範囲を「バックエンドのテンプレート」だけで問うこと — SQL・YAML・XML も既に検査の外にあり、資源のファイル全体として問う方が決まりが1回で済む（V10、B3）。
- AGREE: 招待の URL を設定のベースURLから組み立て、要求のヘッダーから組み立てないテストの案 — 既存の `ProblemBaseUrlResolver` は要求の Host から組み立てる道を持つため、流用の防止まで含めて確かめる必要がある（V8、B4）。
- AGREE: 招待のトークン・招待の URL をログ等に出さない Forbidden の候補 — 今の Forbidden の列挙は閉じた一覧で、招待のトークンを含まない。
- OBJECT: 「SMTP の資格情報をソースコードや設定ファイルに直接書かない」を別の Forbidden として足す候補 — 既存の Forbidden「初期管理者のパスワードやトークンの署名鍵などの秘密情報を…直接書かない」が「など」で SMTP の資格情報も含み、重ねると同じ決まりが2行になる。
- OBJECT: Code Style に、4層に当たらない下位パッケージ・機能の間の依存の向きと出来事・`XxxProblemTypeCatalog` の決まりが無いこと — 実装では成り立っていて今回の Intent で直接効くため、A1〜A3 として書き足し、既存の境界の検査を変えるときの承認（B1）とメールの文言の日英（B2）を面談で問うべき。
- AGREE: 初稿の「要件・設計の段に回すべき論点」の分け方 — 利用者の状態・招待の有効期限・メールの送り方などは機能の中身で、この段で決めることではない。
