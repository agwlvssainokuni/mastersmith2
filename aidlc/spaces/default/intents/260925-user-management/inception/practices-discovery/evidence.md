# Evidence — Practices Discovery

Intent `260925-user-management`（Intent G・H）の Practices Discovery（再実行）の根拠の記録。主担当の初稿、支援役3名の独立レビュー、依頼者との面談（`practices-discovery-questions.md`）、主担当の統合の順に行った。

## 前提

- プロジェクト種別: 既存のコードあり（Brownfield）、スコープ `classic`、Depth `Standard`、Test Strategy `Standard`、Change Control `relaxed`（`aidlc/spaces/default/intents/260925-user-management/aidlc-state.md`）。
- 再実行（Re-run）: `aidlc/spaces/default/memory/team.md` の5節は依頼者が確認済みの基準（前回の確定は Intent `260923-dsl-schema-loader` の Practices Discovery）。これを今の基準とし、証拠と食い違う点と、今回の Intent で新しく決める必要がある進め方の点だけを面談で尋ねた。
- 会話言語: 日本語（依頼の指示の `Conversation language` の行）。
- 参加者が行ったのは、ファイルの読み取りと git の読み取り（`git log`・`git branch`・`git tag`・`git rev-list`・`git rev-parse`・`git worktree list`・`git ls-remote --symref origin HEAD`）だけ。`./gradlew`・`npm`・`docker` は誰も実行しておらず、件数・時間・カバレッジの新しい実測値は無い。`.env` と鍵ファイルは開いていない（セキュリティ担当は `.env.example` の項目名だけを見た）。`aidlc/spaces/default/memory/` のファイルは読んだだけで変えていない。

## 各担当が調べたもの

### 主担当（aidlc-pipeline-deploy-agent）

| # | 対象 | 分かったこと（確かめた事実） |
|---|---|---|
| E1 | `git log --oneline -40`・`git branch -a`・`git rev-list` | 作業ブランチは `develop`。`develop` は `origin/main` より 178 コミット先で、`origin/main` は最初のコミットだけ。マージのコミットは無く、履歴は一直線。工程の承認ごとに日本語のメッセージでコミットしている |
| E2 | Intent `260923-dsl-schema-loader` の Construction のコミット | 単位（Bolt）ごとに `develop` の1コミットになっている。squash と直接コミットの違いは履歴から見分けられない。`team.md` の「1 Bolt が `develop` の1コミット」と形は一致する |
| E3 | `git branch`、`project.md` の Change Control | 直近の2つの bugfix の Intent は短命のブランチから fast-forward で統合した（サブモジュールの固定先の更新を専用のコミットで残すため、計画の承認で受け入れた） |
| E4 | `git tag` | タグは0件。`main` へのリリースはまだ無い（食い違いではない） |
| E5 | `.github/workflows/ci.yml` | `develop` へのプッシュ・`v*` のタグ・手動で `./gradlew verify` を動かす。サブモジュール・Actions・Gitleaks・OSV-Scanner は版やハッシュで固定。`team.md` の Deployment と一致 |
| E6 | `.github/dependabot.yml` | gradle・npm・github-actions・docker を週1回。`target-branch` の指定は無い。向き先はセキュリティ担当の R1 で `develop` と確かめられた（初稿の「`main` に向く見込み」は誤り） |
| E7 | `.pre-commit-config.yaml` | pre-commit で Gitleaks・Spotless・Prettier。pre-push は無い（「置いてもよい」と矛盾しない） |
| E8 | `build.gradle.kts` | `verify` は フォーマット → リンタ → ライセンスヘッダー → ビルド → 単体 → 結合 → カバレッジ → 安全の検査 → 成果物 の順。道具が無ければ失敗させる。`e2eTest`（Playwright）は `verify` と CI の外 |
| E9 | `backend/src/test/java/cherry/mastersmith/targetdb/testsupport/ContainerRuntimeCheck.java` | コンテナが無いときは警告して対象DB のテストを飛ばし、`CI` の環境変数があれば飛ばさない。`team.md` の Way of Working と一致 |
| E10 | Intent `260923-dsl-schema-loader` の `construction/build-and-test/build-and-test-summary.md` | `verify` の時間とVM のメモリを実測し、依頼者の決定で対象DB の3種類とも `verify` の中で毎回実行することになった。`team.md` は「決まるまでは…」のままだった |
| E11 | `README.md` の E2E の節、`frontend/e2e/`、`frontend/package.json` | E2E は Playwright で4本（`010-skeleton`・`020-auth`・`030-admin-access`・`040-dsl-admin`）。`team.md` の「1〜2本に絞る」「道具は設計のステージで決める」と合わない |
| E12 | `.claude/tools/aidlc-state.ts` の `practices-promote` | `discovered-rules.md` の `## Mandated`・`## Forbidden` の中の、空行・`<!--` で始まる行・`#` で始まる行以外をすべて日付つきで `project.md` に追記する。統合版でもこの前提で書いた |
| E13 | `settings.gradle.kts` | 依存の取得元は `mavenCentral()` だけで、`RepositoriesMode.FAIL_ON_PROJECT_REPOS` |
| E14 | 隣のリポジトリ `../java-mustache-processor`（読み取りだけ） | `group = "cherry.mustache"`・`version = "0.1.0"`、Apache License 2.0。リリースは JAR を GitHub のリリースに添付するだけで、Maven への公開の設定は無い |
| E15 | `frontend/scripts/check-license-header.mjs`・`backend/build.gradle.kts` の Spotless | ヘッダーの検査は `frontend/` の中と Java・Gradle の Kotlin DSL だけ。`backend/src/main/resources/` のファイルはどの検査も見ない |
| E16 | `compose.yaml` | サービスは `app` と、profile で起動する監視と対象DB の見本。メールの受け手は無い |
| E17〜E22 | コード知識ベース `aidlc/spaces/default/codekb/mastersmith2/`（`business-overview.md`・`technology-stack.md`・`dependencies.md`・`code-quality-assessment.md`・`architecture.md`・`code-structure.md`） | 利用者を作る道は初期管理者の自動作成だけ。メール送信の仕組みと Mustache のエンジンは依存に無い（K-3）。`packagesJudgedByTotal` に `user.*` の3つを含む 22 パッケージがある（K-11）。ログインとトークンの認証は利用者の状態を見ない（K-2）。メール送信と接続の持ち続け（K-10、仮説） |

### 品質担当（aidlc-quality-agent）

- 確かめた事実: E8・E9・E20 は一致。`ContainerRuntimeCheck` の置き場と警告の文は「対象DB のテスト」に限られる。`user.web` は無く、新しく作るパッケージは自動でパッケージごとの下限の対象になる（F1）。java-mustache-processor は実行時のエンジンで、カバレッジの除外は要らない（F2）。エンジンの `{{ }}` は `&`・`<`・`>`・`"` の4文字だけを置き換え、`'` は置き換えない（F3、`ast/HtmlEscaper.java`）。E2E は `workers: 1`・ファイル名の順で1本ずつ流し、前のファイルの状態に依存する（F4、`frontend/playwright.config.ts`）。秘密情報の漏えいのテストは機能ごとに `*SecretLeakIT` を置く形が定着している（F5）。注入可能な時計は既に使われている（F6）。
- 推測: jsdom では配色のコントラストの検査が働かない（道具の説明による。確かめていない）。
- E10 の原本は開いていない（主担当の確かめに依る）。

### 開発担当（aidlc-developer-agent）

- 確かめた事実: make-you-chic-ui は npm の `file:` の依存で、Gradle の composite build はどこにも無い（V1）。バックエンドは `backend/gradle.lockfile` で版を固定し、OSV-Scanner はその lockfile と npm の2つの lockfile だけを見る（V2）。java-mustache-processor は Java 25、core の実行時の依存は `slf4j-api` だけ、ビルドは Gradle Plugin Portal のプラグインを使い lockfile は無い（V3）。全体の ArchUnit の決まりは5つで、DTO の名前と `record` は検査していない（V4）。機能ごとの境界の検査が6クラスあり、逆向きの知らせは Spring の出来事（V5）。4層に当たらない用途名の下位パッケージがある（V6）。エラーの種類は機能ごとの `XxxProblemTypeCatalog` に1つの code と1つの状態コードで登録し、日英の文言を持つ（V7）。`ProblemBaseUrlResolver` はベース URL の設定が無いと要求の Host から URL を組み立てる（V8）。リフレッシュトークンは SHA-256 のハッシュだけを保存（V9）。`backend/src/main/resources/` の SQL・YAML・XML はヘッダーの検査の外（V10）。
- 推測: Gradle は取り込んだビルドの部品を lockfile に書かない。Jakarta Mail・Eclipse Angus Mail は Apache License 2.0 と違うライセンス（EPL-2.0 など）。

### セキュリティ担当（aidlc-devsecops-agent）

- 確かめた事実: `git ls-remote --symref origin HEAD` が `refs/heads/develop` を返し、GitHub の既定のブランチは `develop`（R1）。java-mustache-processor は lockfile が無く、脆弱性の定期の検査は止めてあり、SpotBugs は入っておらず、リリースにチェックサム・署名・来歴の証明が無く、Actions はタグで指定している（R2）。SpotBugs の関門は priority 1 と `SQL_` だけで、直近の報告に `PREDICTABLE_RANDOM` が priority 2 で1件ある（`auth/repository/LoginAttemptStateRepository`）（R3）。除外の設定は0件。CI には定期の実行も `pull_request` も無い。前の Intent でメールアドレスをアプリのログと外へ送るログから伏せる実装をした（`access/domain/AdminAccessDeniedEvent.java`・`config/ObservabilityConfig.java`）が、規則には入っていない。テストのメールアドレスはほぼ `@example.com` だが、`EmailAddressTest.java` に実在しうるドメインが2件ある。
- 推測: `SMTP_HEADER_INJECTION` の priority は呼び方で変わり1にならないことがある。`mail.debug` は認証の内容を標準出力に出す。宛先の誤りの例外のメッセージには宛先のアドレスが入る。Dependabot は Gradle の lockfile を書き直さない見込み。

## 支援役の訂正と OBJECT の扱い

| 担当 | 指摘 | 扱い |
|---|---|---|
| 開発 C1・OBJECT | 「composite build で組む（make-you-chic-ui と同じ形）」は誤り。make-you-chic-ui は npm の `file:` の依存 | 取り入れた。Q1 の選択肢を直し、`team-practices.md` Code Style に「サブモジュールで取り込む点は同じだが composite build は初めての形」と書いた |
| 開発 C2・OBJECT | 条件「lockfile・脆弱性検査・更新の通知から外れない」を B・C は満たせない見込み。選択肢ごとに外れるものを示すべき | 取り入れた。Q1 の選択肢の説明に外れるものを書き、B が選ばれた後に追加の質問 F2 で条件の扱いを確かめた（F2 A） |
| セキュリティ R1・OBJECT | Dependabot の向き先は `main` ではなく `develop` | 取り入れた。E6 を直し、Q10 は受け方だけを問うた。`team-practices.md` に向き先を `develop` と書いた |
| セキュリティ OBJECT | リリースの JAR の固定（C）を他と同列に並べるべきでない | 取り入れた。Q1 の C に「条件を満たさない」と明記した。D（GitHub Packages）・E（隣のディレクトリの直接参照）は選択肢に入れなかった |
| セキュリティ OBJECT | 「既定は送らない（または手元の受け手）」で既定が決まっていない | 取り入れた。Q3 A を「接続先の設定が無ければ送らない」に決め、依頼者が選んだ |
| 品質 OBJECT | テスト用のメールの受け手の種類をすべて設計の段に回すと、コンテナが無いときに飛ばしてよいものの決まりとぶつかる | 取り入れた。Q2 で JVM の中の受け手かコンテナかを問い、A（JVM の中、コンテナ不要）が選ばれた。具体の道具だけを設計の段に回す |
| 品質 OBJECT | E2E の「統合の前に実行する」がすべての統合か画面・認証だけかが決まっていない | 取り入れた。Q5 の選択肢で問い、A（画面・認証に関わる変更の統合前とリリース前）が選ばれた |
| 開発 OBJECT | ヘッダーの検査の範囲をテンプレートだけでなく資源のファイル全体として問うべき | 選択肢 Q9 B として示した。依頼者は A（メールのテンプレートだけを加える）を選んだ。SQL・YAML・XML は今までどおり検査の外に残る |
| 開発 OBJECT | SMTP の資格情報を直書きしない、を別の Forbidden にすると既存の行と重なる | 取り入れた。別の行にしていない（既存の「秘密情報…など」に含まれる） |
| 開発 OBJECT | 用途名の下位パッケージ・依存の向きと出来事・code の一覧が `team.md` に無い | Q11 B・C として確認し、依頼者が選んだ。`team-practices.md` Code Style に書いた |

## 面談の決定

| 問い | 答え | 反映した場所と、なぜそう扱ったか |
|---|---|---|
| Q1 取り込み方 | B（サブモジュール ＋ Gradle の composite build） | Code Style の静的解析とセキュリティ検査の節。供給網の決まりに当たるため team の決まりにした |
| F1 置き場と直接の変更 | A（`vendor/java-mustache-processor`、直接変更しない） | 置き場は Code Style、禁止は `discovered-rules.md` の Forbidden（依頼者が明言した固い制約。make-you-chic-ui の既存の行と同じ形） |
| F2 lockfile・脆弱性検査の扱い | A（固定先のコミットで固定、取得元の固定は崩さない、推移依存も対象に含めることを条件にし最初の Bolt で確かめる） | Code Style。Q1 B が既存の決まり（lockfile・取得元の固定）と食い違いうるため追加で確かめた |
| Q2 メールのテスト | A（JVM の中の SMTP の受け手、送信の失敗のテスト必須、コンテナ不要） | Testing Posture。コンテナが無いときに飛ばしてよいのは対象DB のテストだけ、の決まりは変わらないことも書いた |
| Q3 固い制約 | A・B・C・D | `discovered-rules.md` に9行（F1 A を含む）。Deployment にも A の運用の形を書いた |
| Q4 必須テストの一覧 | A（すべて足す） | Testing Posture。招待と登録の完了・パスワードの変更は既存の認証・認可・監査の一覧に、メールの項目は DSL と同じ形の新しい一覧にした。支援役の追加（`'` を含むエスケープ、`{{{ }}}` を使わない、日英の全テンプレート、トークンと URL の漏えい、ヘッダーへの改行、送信の失敗時の漏えい、ベース URL の設定が無いときの Host）を統合し、具体の値は★で要件定義に回した |
| Q5 E2E | A（Intent ごとに代表の流れを1本まで、今回は招待から登録の完了までを1本、画面・認証に関わる統合前とリリース前に実行） | Testing Posture の E2E の行を置き換えた。道具（Playwright）と実行の場（`verify` と CI の外）は Q5 の問いで示した事実。「今回は招待から登録の完了までを1本足す」は今回の Intent だけの決定のため、`team.md` には入れず、下の「要件・設計の段に回す論点」に書いた |
| Q6 既存のパッケージの下限 | B（手を入れたら下限を満たすまでテストを足して必ず対象に戻す、一覧は増やさない） | Testing Posture。測る時点（テストを足した後に `cleanTest`・`cleanIntegrationTest` を付けた `verify`）は品質担当の提案と `project.md` の学びから足した |
| Q7 統合の仕方 | A（サブモジュールの固定先の更新を含む変更だけ fast-forward） | Way of Working。どちらの形でも統合前に1コマンドの検査を通すことを添えた（セキュリティ担当の提案。既存の決まりの言い直し） |
| Q8 SpotBugs | A（`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` も priority にかかわらず止める） | Code Style。既存の行は変えず、次の行に足した。既存の1件の扱いも書いた |
| Q9 テンプレートのヘッダー | A（`{{! ... }}`、検査にバックエンドのテンプレートを加える、本文に出ないことをテスト） | Code Style の言語と命名、Testing Posture のメールの一覧 |
| Q10 Dependabot | A（画面でマージしない、手元で lockfile ごと更新し verify を通して統合、High 以上は次の Bolt の前） | Way of Working |
| Q11 記録の更新 | A・B・C | A は Testing Posture の対象DB の行を置き換えた。B・C は Code Style のバックエンドに足した |
| 要約の確認 | Looks correct | — |

`team.md` の行で置き換えたのは2行だけ（対象DB の結合テストの実行の場＝Q11 A、E2E の本数と道具＝Q5 A）。ほかの行と学びの行は変えていない。初稿で面談の答えを待っていた行は、すべて答えに従って確定の文に書き直した。

## 残る不確かさ

- composite build で組んだ java-mustache-processor とその推移依存（`slf4j-api`）が `backend/gradle.lockfile` と OSV-Scanner の対象に実際に載るかは確かめていない（推測では部品そのものは載らない）。F2 A により、最初に取り込む Bolt で確かめ、載らなければ仕組みを足す。
- 部品の側のビルドが Gradle Plugin Portal のプラグイン（`org.owasp.dependencycheck`）を要するため、composite build で組むと Maven Central 以外の取得元に触れうる（推測）。取得元の固定を崩さない形で組めるかは同じ Bolt で確かめる。
- メールの送信の部品（Jakarta Mail・Eclipse Angus Mail など）とテスト用の SMTP の受け手の部品のライセンスは確かめていない。Apache License 2.0 と違えば、`team.md` の決まりどおり ADR に理由を残す。
- `SMTP_HEADER_INJECTION` がどの priority で出るか、実際に FindSecBugs が今回の書き方で検出するかは確かめていない。検出しない場合もテスト（ヘッダーへの改行の差し込み）で押さえる。
- 既存の `PREDICTABLE_RANDOM` の1件（`auth/repository/LoginAttemptStateRepository`）が誤検知かは確かめていない。関門に入れる Bolt で確かめる。
- java-mustache-processor の `{{! ... }}` の行を占めるコメントが出力から消えるかは、仕様への準拠を README から推測しただけ。設計かコード生成の最初に小さく確かめる。
- Dependabot が Gradle の lockfile を書き直すか、GitHub の画面の設定（依存関係のグラフ）が `develop` を見ているかは確かめていない。Q10 A で手元で lockfile ごと直すため、進め方には影響しない。
- CI に定期の実行が無く、コードに手を入れない間に公表された脆弱性は Dependabot の知らせに頼る（セキュリティ担当の S5、優先度が低く面談では問わなかった）。
- `EmailAddressTest.java` に実在しうるドメインのメールアドレスが2件ある。送信はしないため今は問題ないが、テストデータのメールアドレスを予約済みのドメインに限るかは面談で問わず、決まりにしていない。
- `project.md` の学び「再現できなければ不安定と確かめられていない扱い」と `team.md` の「不安定なテストは原因を直すまで統合しない」の関係は、今回の Intent に直接効かないため問わなかった。

## 要件・設計の段に回す論点

- 今回の Intent で E2E を1本足す（招待から登録の完了まで、自分で利用者を作る。Q5 A の決定）。メールを受けた後に招待の URL を取り出す口と、E2E で使うメールの受け手の用意。
- 利用者の状態の持ち方と、状態の確かめをログイン・トークンの更新・アクセストークンの認証のどこに置くか（K-1・K-2）。
- 招待の有効期限の値、再送・取り消し、招待の URL の形、トークンを URL のどこに置くかと画面が読んだ後に消すか、ベース URL の設定が無いときの動作（送らないか、起動を止めるか。K-9）。
- 招待のトークンの作り方（既存のリフレッシュトークンと同じ `SecureRandom` と SHA-256 のハッシュにするか、長さ）。
- 招待・登録の完了・パスワードの変更の API の回数の制限と、パスワードの変更の「今のパスワードの確かめ」の失敗をアカウントロックの回数に数えるか。
- パスワードの規則と、変更の後にほかの端末のリフレッシュトークンを無効にするか（K-8）。無効にするなら機能の間の出来事で行うか。
- メールの送信をトランザクションの外で行うか、失敗・遅延のときの業務の動作、送信の結果を監査に残すか（K-7・K-10）。送信を別のスレッドで行う場合のテストの待ち方（固定の `sleep` を使わない既存の決まりの中で）。
- テスト用の SMTP の受け手（JVM の中で動くもの）と手元の受け手（compose の profile）の具体の道具・版の固定・ライセンス。
- メールの送信の部品のライセンスの ADR と、同梱の文書の置き場（`backend/src/main/resources/META-INF/third-party-licenses/`）。
- メールの件名・本文の日英の持ち方と、どちらの言語で送るか。テンプレートの置き場とファイル名、件名の取り方。
- メールの送信とテンプレートの描画を置く機能と下位パッケージの名前、新しい機能を `user` に足すか別のパッケージにするか（カバレッジの下限の効き方が変わる。Q6 B）。
- テーマの `system`、ブランドカラーとフォントを画面に渡す道、make-you-chic-ui 側の変更が要るか（K-4）。要るなら元のリポジトリで直し、固定先の更新は専用のコミットで行い、Q7 A の fast-forward で統合する。
- 表示言語の切り替えの口とプリファレンスの保存先（K-5）、プリファレンス画面の配色のコントラストの確かめ方。
- ログインなしで呼ぶ API（招待の受け取り・登録の完了）のアクセスの決まり（K-6）。
- 配備先が決まったときの SMTP の接続の暗号化と、Spring Boot のメールのヘルスチェックを使うか。
- メール送信を含む経路の負荷の試験をするか、どの段が持つか。
