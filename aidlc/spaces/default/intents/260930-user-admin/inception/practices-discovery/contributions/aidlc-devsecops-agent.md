**Collaborator:** aidlc-devsecops-agent

## Contribution

セキュリティ担当（aidlc-devsecops-agent）の独立レビュー。対象は、リンタ・フォーマット、静的解析（SpotBugs＋FindSecBugs の関門）、秘密情報の検出、依存関係の脆弱性の検査と更新の知らせ、サプライチェーンの管理。読み取りだけで確かめた（ファイルの読み取り、`git ls-files`・`git tag`、`gh api` による GitHub の設定の読み取り）。`./gradlew`・`npm`・`docker` は実行しておらず、`.env`・`.env.targetdb`・鍵・`reference/` は開いていない。

### 1. 確かめた事実（初稿の根拠に足せるもの）

| # | 対象 | 分かったこと |
|---|---|---|
| S1 | `gh api repos/<repo>` の `security_and_analysis` | GitHub の secret scanning と push protection は**有効**。Dependabot security updates は無効。`team.md` には push protection の記述が無い（Gitleaks の pre-commit・`verify`・CI に加えて、3つ目の守りとして働いている） |
| S2 | `gh api repos/<repo>/vulnerability-alerts` | 404（**Dependabot alerts は無効**）。初稿 `evidence.md` の「残る不確かさ」の3点目（今も無効かは確かめられない）は、読み取りで確かめられた。前の Intent の決定（Q2: B）のとおり |
| S3 | ブランチの保護・ルールセット | `develop`・`main` とも保護なし。ルールセット `restrict-force-push-and-delete`（削除・強制プッシュの禁止）はあるが、`enforcement: disabled` で、対象の ref も空（実質働いていない）。Actions は `allowed_actions: all`・`sha_pinning_required: false`、既定のトークンは `read` |
| S4 | `.github/workflows/ci.yml` | Actions はすべてコミットのハッシュで固定、Gitleaks・OSV-Scanner は版と SHA-256 で固定、`permissions: contents: read`。`release` のジョブだけが `v*` のタグで `contents: write` を持ち、公開のリリースに WAR を添付する（初稿 D3 の理由と一致）。pre-commit の Gitleaks も CI と同じ v8.30.1 |
| S5 | `backend/build.gradle.kts` の `spotbugsGate` | `team.md` の決まりどおり（priority 1、`SQL_` で始まるもの、`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` は priority によらず失敗）。`spotbugsTest` は無効（テストのコードは関門の外）。除外は理由つきの1件だけ（`backend/config/spotbugs-exclude.xml`）。初稿 D4 の直しと一致 |
| S6 | ルートの `build.gradle.kts` の `osvScan` | 対象の lockfile は `backend/gradle.lockfile`・`frontend/package-lock.json`・`vendor/make-you-chic-ui/package-lock.json` の3つ。npm の実行時の依存は High 以上で失敗、開発時は警告、ただし `config/npm-build-tools.txt` の道具の High 以上と `MAL-` は失敗。`team.md` の Deployment の学びの行と一致。OSV の見送りの設定ファイル（`osv-scanner.toml`）は無い |
| S7 | `verify` の段の順 | 段 0（準備）で `vendorInstall`・`vendorBuild`・`frontendInstall`（`npm ci`）を実行し、依存の脆弱性と悪意のあるパッケージ（`MAL-`）の判定は段 8（`osvScan`）。**悪意のあるパッケージを見つけるのは、そのパッケージを入れてビルドとテストで実行した後**になる。`frontend/.npmrc` は `engine-strict=true` だけで `ignore-scripts` は無い。今の lockfile で install のスクリプトを持つのは `fsevents`（開発時・任意）だけ |
| S8 | Gradle のプラグイン | Spring Boot・Spotless・SpotBugs のプラグイン（Gradle Plugin Portal から取る）はビルドのクラスパスで、lockfile に固定されず（`settings-gradle.lockfile` は空）、`osvScan` の対象にも入らない。composite build の `vendor/java-mustache-processor` のプラグインも同じ。Spring Boot のプラグインは配る WAR を組み立てる道具。Gradle の依存の検証（`gradle/verification-metadata.xml`）と、`gradle-wrapper.properties` の `distributionSha256Sum` は無い（CI では `setup-gradle` が wrapper の jar を確かめる） |
| S9 | `.gitleaks.toml` | 既定の規則を引き継ぎ、除外は AI-DLC の記録の目印のハッシュ値の1件だけ。パスと値の形の両方（`condition = "AND"`）で絞り、理由を書いている。除外の足し方の決まりはファイルのコメントにだけあり、`team.md` には無い |
| S10 | `.idea/`（初稿 E16） | Git の管理の対象は `compiler.xml`・`gradle.xml`・`misc.xml`・`vcs.xml` など6ファイルで、秘密は含まない。`.idea/.gitignore` は `workspace.xml`・`dataSources/`・`dataSources.local.xml` を外すが、**`dataSources.xml` は外していない**。IntelliJ で対象DB・内部DB の接続を登録すると、JDBC の URL とユーザー名が `dataSources.xml` に入り、公開のリポジトリにコミットされうる（パスワードは `dataSources.local.xml` 側でも、接続先とユーザー名は `project.md` の Forbidden「対象DB の接続情報」に当たる）。Gitleaks はこの形を拾わない見込み |
| S11 | `frontend/.oxlintrc.json`・`frontend/eslint.config.js` | `react/no-danger`・`no-eval`・`no-new-func`・`no-script-url`・`react/jsx-no-target-blank` が error、`no-implied-eval` は ESLint 側で error。`team.md` の Code Style と一致 |
| S12 | 認可と送り元の確かめ | 管理の API は `/api/admin/**` を `access/web/AdminSecurityContributor.java` で管理者だけにし、アクセストークンは Bearer（CSRF の仕組みは無効、Cookie を使う更新とログアウトだけ `auth/web/OriginVerifier.java` で Origin を確かめる）。今回の管理の操作を `/api/admin/**` の下に置けば、既存の 401／403 の判定と CSRF の前提をそのまま使える |
| S13 | `common/observability/TraceAspect.java` | `web`・`service`・`domain`・`repository` の層の引数と戻り値を TRACE で文字列にして出す（`record` 自体は対象外だが、`record` を引数・戻り値に持つメソッドは対象）。`user/domain/EmailAddress.java`・`DisplayName.java` は `toString` を上書きしない型のため値は出ないが、一覧の要素を `String` のメールアドレスを持つ `record` にすると、TRACE を有効にしたときにメールアドレスがアプリのログに出る（コード知識ベース K-8 の具体の形） |

### 2. team.md と実態のずれ（初稿に足りないもの）

初稿の D1〜D5 に加えて、次の点を直す案として出す。

- **X1（Code Style・静的解析）**: GitHub の secret scanning と push protection が有効なこと（S1）を1行足す。案: 「GitHub の secret scanning と push protection を有効のままにする（Gitleaks の pre-commit・`verify`・CI に加える守り）。」実態の追認。
- **X2（Code Style・静的解析）**: Gitleaks の除外の決まり（S9）を SpotBugs の除外の行と同じ形で足す。案: 「Gitleaks の誤検知の除外は、秘密情報でないことを確かめたものに限り、`.gitleaks.toml` に理由を書き、対象のパスと値の形の両方で絞る。」今のファイルのコメントを決まりにするもの。
- **X3（Way of Working の D2 の行の補足）**: 初稿の新しい行は S2 で事実を確かめられた。あわせて、OSV-Scanner の関門は `verify` と CI を流したときにだけ働く（新しい脆弱性の公表の時点では知らせない）ことを同じ行に書くと、`dependabot.yml` のコメントとそろう。
- **X4（Code Style・リポジトリ構成）**: `.idea/` を Git で管理するなら（初稿 E16、面談で問わない予定のもの）、`.idea/.gitignore` に `dataSources.xml`（と `dataSources/`）を足すことを条件にしたい（S10）。`team.md` に書かないとしても、`project.md` の Forbidden（対象DB の接続情報）と公開のリポジトリの前提に当たるため、面談で問わない扱いのまま進めるなら、Construction のどこかの Bolt の小さな直しとして記録しておく必要がある。

### 3. 面談で確かめたい論点（初稿の P1〜P10 に足す・直す）

優先度は、今回の Intent に直接効くものを「高」、進め方の穴を埋めるものを「中」、記録だけでよいものを「低」とした。

| # | 優先度 | 見出し | 問いたいこと | 根拠 |
|---|---|---|---|---|
| Q-S1 | 高 | P1 の必須テストへの追加（セキュリティの観点） | 初稿 P1 の項目に次を足す。(a) **停止・印を外した後のアクセストークン**: `project.md` の DECIDED（アクセストークンの失効の仕組みは持たない）のもとで、停止した利用者・印を外した利用者の今のアクセストークンが、次の要求から拒否されるのか、有効期限まで通るのかを、決めた側の動作を明示したテストにする（ログアウトの「有効期限まで使えること（決定済みの仕様として明示する）」と同じ形）。どちらにするかは★で要件に回す。(b) **権限の変更の要求の改ざん**: 管理者でない利用者が本文の値を変えて `/api/admin/**` の外の API（`/api/me` など）から自分の印・状態を変えられないこと（一括代入の防止）。(c) **一覧の漏えい**: TRACE を有効にしたときに一覧の要素のメールアドレス・氏名がアプリのログに出ないこと（既存の `*SecretLeakIT` の形。S13）。(d) **監査の漏えい**: 管理の操作の監査の行と応答に、パスワードのハッシュ・リフレッシュトークン・ロックの内部の値が入らないこと | S12・S13、K-1・K-4・K-8、`team.md` Testing Posture、`project.md` DECIDED と Forbidden |
| Q-S2 | 高 | P2 の固い制約の候補の直し | 初稿の3つ目「NEVER 利用者の一覧・詳細の応答にパスワードのハッシュ…」は、既存の Forbidden（パスワードのハッシュを**ログ・監査ログ・トレースの属性・外部へのエクスポート・エラー応答**に含めない）に**成功の応答が入っていない**穴を埋める意味がある。範囲を今回の一覧だけに限らず、「NEVER API の応答（成功の応答を含む）に、パスワードのハッシュ値・招待のトークンのハッシュ値・ロックの判定の内部の値を含めない」と広げる案を足す。あわせて候補を1つ足す: 「ALWAYS 利用者の権限・状態を変える管理の操作（管理者の印の付け外し・利用停止と再開・ロックの解除）は、操作した人・対象の利用者・結果を監査に残す（拒否した操作を含めるかは要件で決める）」 | 既存の Forbidden の文言、K-6、`team.md` の「監査記録をログ出力で代用しない」 |
| Q-S3 | 中 | 悪意のあるパッケージの判定の順序（サプライチェーン） | `team.md` は `MAL-` を統合を止める条件にしているが、判定は `verify` の段 8 で、段 0 の `npm ci` とビルド・段 5〜6 のテストでそのパッケージを実行した後になる（S7）。Dependabot の知らせを手元で取り込むとき（`npm install`）も同じ。次のどれにするか: A. `frontend/.npmrc`（と make-you-chic-ui は変えられないため `verify` の呼び出し）で `ignore-scripts=true` にする（今の lockfile で install のスクリプトを持つのは `fsevents` だけ）、B. `osvScan` は lockfile だけを読むため、段 0 の `npm ci` の前に動かす（または lockfile を更新した直後に `osv-scanner` を流す手順を Way of Working の Dependabot の行に足す）、C. A と B の両方、D. 今のまま（判定が後になることを受け入れて記録する） | S6・S7、`team.md` Deployment の学び（`MAL-` は止める） |
| Q-S4 | 中 | GitHub のルールセット | `develop`・`main` に保護が無く、ルールセット（削除・強制プッシュの禁止）は無効のまま（S3）。プッシュは依頼者だけが行う運用で、`main` は fast-forward で進める（初稿 D1）。誤った強制プッシュ・削除を防ぐため、このルールセットの対象に `develop`・`main` を入れて有効にするか、今のままにするか。変えるのは依頼者（AI は GitHub の設定を変えない） | S3、`team.md` Way of Working（`git push` は依頼者） |
| Q-S5 | 低 | ビルドのプラグインの検査の範囲 | Gradle のプラグイン（配る WAR を組み立てる Spring Boot のプラグインを含む）は lockfile にも OSV-Scanner にも載っていない（S8）。npm では成果物を作る道具の High を止めている（`config/npm-build-tools.txt`）のと釣り合わない。A. ビルドのクラスパスも lockfile で固定して OSV-Scanner の対象にする（どの Bolt で入れるかは計画で決める）、B. 今は入れず、既知の範囲の外として記録する（Spring Boot を上げるときに合わせて見直す）。Gradle の依存の検証（ハッシュ）と `distributionSha256Sum` も同じ扱いでよいか | S8、`team.md` Code Style（取得元は Maven Central だけ）、`project.md` の学び（プラグインは Gradle Plugin Portal から取ってよい） |
| Q-S6 | 低 | OSV の見送りの書き方 | 今は OSV の見送りの設定が無く、誤検知や直せない High が出ると統合が止まるだけになる（S6）。見送りを入れるときの決まり（`osv-scanner.toml` の `IgnoredVulns` に理由と `ignoreUntil` の期限を書く、実行時の依存の High は見送らない、など）を先に決めておくか、必要になった Intent で決めるか | S6、初稿 D2（`dependabot.yml` の `ignore` に理由と外す時期を書く）と同じ考え方 |

P9 は X3 の補足つきで、Dependabot alerts を無効のままにすることを問い直さない扱いに同意する。Dependabot alerts を無効のまま、公表の時点の知らせの穴を小さくしたいなら、週に1回 `osvScan` だけを流す CI の予定の実行を足す手もある（S2・X3）。問うかは主担当の判断に任せる（面談の問いの数を増やしたくなければ要件・設計にも回さず、記録だけでよい）。

### 4. 要件・設計の段に回す論点（セキュリティの観点で足すもの）

- 停止・印を外した後の今のアクセストークンとリフレッシュトークンの扱い（Q-S1 (a) の★の中身）。`auth` の3つの入口で状態を毎回 DB から読むか（管理者の印は既に要求ごとに DB から読む、K-4）、リフレッシュトークンをまとめて無効にする問い合わせを足すか。
- 管理の操作の API の形: 操作ごとに分けた要求の `record`（変える項目だけを持つ）にし、利用者の行を丸ごと受け取らない。`/api/admin/**` の下に置く（S12）。
- 一覧の応答の型: メールアドレス・氏名を、TRACE で文字列にしても値が出ない型（`EmailAddress`・`DisplayName`）で service の層を通すか、`TraceAspect` の対象の外に置くか（S13、`project.md` の学び「TraceAspect は service の層の引数と戻り値を TRACE のログに文字列で出す」）。
- 自分自身を止める・自分の印を外す操作の扱いと、最後の管理者の守り方の同時の操作での確かめ（行の排他か条件つきの更新か）。
- 管理の操作の回数の制限の要否（今は管理の API に回数の制限が無い見込み。管理者だけの API のため優先度は低い）。
- 配備の後の確かめ（Deployment Execution）で管理の一覧の画面を開くと、実在の利用者のメールアドレス・氏名が画面に出る。公開のリポジトリに置く記録（スクリーンショット・E2E の報告・段の成果物）には件数と有無だけを残す（`project.md` Corrections の「個人に関する値は表示せず、値の有無だけを確かめる」をこの画面にも当てる）。

## Positions

- AGREE: D1（`main` へ fast-forward）— `git` の履歴と2つの Intent の決定どおりで、ルールセットを有効にしても両立する。
- AGREE: D2（脆弱性の関門は OSV-Scanner、`ignore` に理由と外す時期を書く）— S2 で Dependabot alerts が無効なことを読み取りで確かめた。X3 の補足（関門は `verify`・CI のときだけ働く）を同じ行に足したい。
- AGREE: D3（配備先が決まるまで `v*` のタグを付けない）— タグは `contents: write` のジョブで公開のリリースを作るため（S4）、付けない方が意図しない公開を避けられる。
- AGREE: D4・D5（済んだことの書き直し）— `spotbugs-exclude.xml` と lockfile の実態に合う（S5）。
- AGREE: P1・P2 を高い優先度で問うこと — 今回の Intent は権限と状態を変える操作で、Q-S1・Q-S2 の足しと直しを加えたい。
- OBJECT: 初稿の Code Style（静的解析）に GitHub の secret scanning・push protection と Gitleaks の除外の決まりが無い — 実態で働いている守りと除外の基準が `team.md` から読めない（X1・X2）。
- OBJECT: `.idea/` を「面談で問わない」とした扱い — `dataSources.xml` が外されておらず、対象DB の接続先とユーザー名が公開のリポジトリに入りうる（S10）。問わないなら、直しを記録として残す必要がある（X4）。
- OBJECT: 初稿の論点に悪意のあるパッケージの判定の順序が無い — `MAL-` の判定が、そのパッケージを入れて実行した後になる（S7、Q-S3）。
- OBJECT: 初稿の「残る不確かさ」の Dependabot alerts の点 — `gh api` の読み取りで無効と確かめられたため、不確かさから外して事実（S2）として書ける。
