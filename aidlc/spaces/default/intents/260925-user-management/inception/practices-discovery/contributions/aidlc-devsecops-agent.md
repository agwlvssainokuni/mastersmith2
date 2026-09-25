**Collaborator:** aidlc-devsecops-agent

## Contribution

セキュリティ担当（aidlc-devsecops-agent）の独立レビュー。主担当の初稿（`team-practices.md`・`discovered-rules.md`・`evidence.md`・`practices-discovery-timestamp.md`）を、フォーマット・リンタ、静的解析、秘密情報の検出、依存関係の脆弱性検査、供給網、招待のトークン・メールアドレス・SMTP の資格情報の観点で確かめた。`team.md` は依頼者が確認済みの基準として扱い、今回の Intent（ユーザー登録・招待メール・招待のトークン・パスワードの初期設定と変更・プリファレンス画面）で進め方として決め直す点だけを挙げる。機能の中身は「要件・設計の段に回す論点」に分けた。

### 1. 確かめた範囲と方法

- 読んだもの: `settings.gradle.kts`・`settings-gradle.lockfile`・`build.gradle.kts`・`backend/build.gradle.kts`・`backend/gradle.lockfile`・`backend/config/spotbugs-exclude.xml`・`backend/build/reports/spotbugs/main.xml`（手元にある直近のビルドの報告）・`gradle/libs.versions.toml`・`.github/workflows/ci.yml`・`.github/dependabot.yml`・`.pre-commit-config.yaml`・`.gitleaks.toml`・`.gitignore`・`.gitmodules`・`.env.example`（項目名だけ）・`backend/src/main/resources/application.yaml`・`backend/src/main/resources/db/migration/V3__u2_authentication.sql`・`V4__u4_audit_event.sql`・`backend/src/main/java/cherry/mastersmith/config/SecurityConfig.java`・`ObservabilityConfig.java`・`access/domain/AdminAccessDeniedEvent.java`・`auth/domain/RefreshTokenValues.java`、コード知識ベース `aidlc/spaces/default/codekb/mastersmith2/`（`technology-stack.md`・`dependencies.md`・`code-quality-assessment.md`・`api-documentation.md`・`business-overview.md`）、隣のリポジトリ `../java-mustache-processor`（読み取りだけ）。
- 実行したのは読み取りだけ（`grep`・`git log`・`git ls-remote --symref origin HEAD`）。`./gradlew`・`npm`・`docker` は実行していない。`.env` と鍵ファイルは開いていない（`.env.example` は値を除いて項目名だけ見た）。
- 以下、「事実」は上のファイル・コマンドで確かめたこと、「推測」は確かめていないことを示す。

### 2. 初稿の訂正

- **R1（E6・D4・P10 の前提の訂正）** 事実: `git ls-remote --symref origin HEAD` は `ref: refs/heads/develop HEAD` を返した。GitHub のリポジトリの既定のブランチは `develop` である。手元の `origin/HEAD` が `main` を指すのは、クローンしたときの古い記録が残っているため（推測: 既定のブランチを後から `develop` に変えた）。したがって、`target-branch` の指定が無くても Dependabot の更新のプルリクエストは `develop` に向き、Dependabot の脆弱性の通知（依存関係のグラフ）も `develop` の lockfile・定義を見ている見込み（推測: GitHub の画面の設定そのものは見ていない）。E6 の「`main` に向く見込み」と、`team-practices.md` の Way of Working の（確認待ち）行の「リポジトリの既定のブランチ（手元の `origin/HEAD` は `main`）に向く見込み」は直す必要がある。論点は「向き先」ではなく「受け方」だけになる（4.2 を参照）。
- **R2（E14 の補足）** java-mustache-processor について、初稿に無い事実を足す。
  - lockfile が無い（`*.lockfile` は0件）。依存は `cherry-mustache-core` の実行時が `org.slf4j:slf4j-api:2.0.16` だけで、ほかはテスト用（JUnit・jqwik・SnakeYAML）。
  - 脆弱性検査は OWASP Dependency-Check のプラグイン（`org.owasp.dependencycheck` 10.0.4）で、定期の実行はコミット `d8e833f` で止めてある（cron をコメントにした）。SpotBugs・FindSecBugs は入っていない。
  - リリースの流れ（`.github/workflows/release.yml`）は JAR を GitHub のリリースに添付するだけで、チェックサム・署名・来歴の証明（attestation）は無い。Actions はタグ（`@v7` など）で指定し、このリポジトリのようなコミットのハッシュでの固定はしていない。
  - `{{ }}` は HTML をエスケープし（`cherry/mustache/ast/VariableNode.java` の `HtmlEscaper.escape`）、エスケープしない `{{{ }}}`・`{{& }}` も持つ（`UnescapedVariableNode`）。部分テンプレートは `FilePartialResolver`・`MapPartialResolver` で、値の読み出しはリフレクション（`render/PojoResolver.java`）。technology-stack.md の「エスケープするかはエンジンによる（仮説）」は、`{{ }}` については事実として確かめられた。
- **R3（E15 の補足）** 事実: SpotBugs の関門（`backend/build.gradle.kts` の `spotbugsGate`）は priority 1 と `SQL_` だけで止め、それ以外は警告。直近の報告では `PREDICTABLE_RANDOM`（予測できる乱数）が priority 2 で1件あり、警告として通っている（`auth/repository/LoginAttemptStateRepository`。中身は誤検知かどうか確かめていない）。つまり、招待のトークンを `java.util.Random` で作っても今の関門は止めない。4.3 の論点につながる。

### 3. 観点ごとの所見

#### 3.1 フォーマット・リンタ・ライセンスヘッダー

- 既存の決まり（Spotless・Prettier・oxlint・ESLint・Stylelint、`react/no-danger`・`no-eval` 系）で今回の画面（登録の完了・パスワードの変更・プリファレンス）は足りる。質問は要らない。
- メールのテンプレートのヘッダー（初稿 P9）に賛成。`{{! ... }}` は java-mustache-processor の構文にある（R2 の `COMMENT`）。加えて、セキュリティの面から、同じ検査で「利用者が入れる値にエスケープしない `{{{ }}}`・`{{& }}` を使っていない」ことも見られる。P9 の質問に「テンプレートの検査の範囲にエスケープしない差し込みの検出を含めるか」を1つの選択肢として足すことを勧める（道具・書き方は設計の段）。

#### 3.2 静的解析（SpotBugs ＋ FindSecBugs）

- 事実: `effort = MAX`・`reportLevel = LOW`・除外の設定は0件（`backend/config/spotbugs-exclude.xml` は空）。関門は R3 のとおり。
- 今回の Intent で初めて出てくる危険と、FindSecBugs の対応するパターン:
  - 招待のトークンの乱数 → `PREDICTABLE_RANDOM`（事実: 今の報告では priority 2 で警告止まり）
  - メールのヘッダー（宛先・件名）への改行の差し込み → `SMTP_HEADER_INJECTION`（推測: priority は呼び方と値の出どころで変わり、1 にならないことがある）
  - SMTP の資格情報の直書き → `HARD_CODE_PASSWORD`（既存の Forbidden で禁止済み。Gitleaks でも見る）
- 提案（面談で決めてもらう）: `SQL_` と同じく、`PREDICTABLE_RANDOM` と `SMTP_HEADER_INJECTION` を priority にかかわらず統合を止める対象に加えるか。`team.md` の「重大度 High 以上で止める」基準の上乗せで、前回 `SQL_` を足したのと同じ判断になる。誤検知は既存の決まりどおり `backend/config/spotbugs-exclude.xml` に理由を書いて外す。既存の1件は、加えるなら入れる Bolt で中身を見て、誤検知なら理由を書いて外す。
- CodeQL・OWASP ZAP を採用しない決まりはそのままでよい（テンプレートの XSS は3.1の検査とテストで押さえる）。

#### 3.3 秘密情報の検出（Gitleaks）

- 事実: pre-commit（v8.30.1）と `verify` の `gitleaksScan`（履歴全体、`--redact`）と CI（版と SHA-256 を固定）の3か所。`.gitignore` は `.env`・`.env.*`（見本2つを除く）・鍵ファイル。既存の決まりで SMTP の資格情報（`.env` に入る）も足りる。
- 注意点（進め方ではなく作業の注意）: テストで SMTP の資格情報のダミーの値を書くと Gitleaks の既定の規則に当たることがある。除外は `.gitleaks.toml` の既存の書き方（理由を書き、秘密情報でないことを確かめたものに限る）で足りる。質問は要らない。
- Jakarta Mail のデバッグ出力（`mail.debug`）は SMTP の認証の内容（Base64 の資格情報）を標準出力に出す（推測: 一般に知られた振る舞いで、このリポジトリでは未確認）。これは固い制約の候補 4.4 の F3 に含めた。

#### 3.4 依存関係の脆弱性検査（OSV-Scanner）と新しい依存

- 事実: `osvScan` の対象は固定の3つ（`backend/gradle.lockfile`・`frontend/package-lock.json`・`vendor/make-you-chic-ui/package-lock.json`）。lockfile の無い部品は見ない。
- メール送信の仕組み（推測: Spring Boot の `spring-boot-starter-mail` を使うと Jakarta Mail の実装の Eclipse Angus Mail が入る）は、ライセンスが Apache License 2.0 ではない（推測: EPL 2.0・GPL 2.0 with Classpath Exception・EDL 1.0 の選択制）。`team.md` の「Apache License 2.0 と異なるものは ADR に理由を残す」がそのまま当てはまる。既存の決まりで足りるので質問は要らないが、設計の段への引き継ぎに書いておくとよい。テスト用のメールの受け手（GreenMail・Mailpit など）も同じ扱い。
- 定期の検査の隙間（事実）: CI のきっかけは `develop` へのプッシュ・`v*` のタグ・手動だけで、定期の実行（schedule）も `pull_request` も無い。コードに手を入れない間に公表された脆弱性は、次の `verify` まで OSV-Scanner では見つからない。R1 のとおり Dependabot の通知が `develop` を見ているなら、その間は Dependabot の通知が補う形になる。進め方として「週1回の定期の CI（または `osvScan` だけ）を足すか」は面談の候補にできるが、優先度は低い（4.6 の S5）。

#### 3.5 供給網

- **java-mustache-processor の取り込み方（初稿 P1）** に賛成で、最優先の質問にすべき。候補ごとの評価を足す（リードの条件「lockfile・脆弱性検査・更新の通知の対象から外れない」に照らした）。

| 候補 | 条件を満たすか | セキュリティの面の評価 |
|---|---|---|
| A. 部品の側で Maven Central に公開してから使う | 満たす | 取得元の固定（`FAIL_ON_PROJECT_REPOS`）を変えずに済み、版は `backend/gradle.lockfile` で固定され、Dependabot・OSV の対象になる。Maven Central は署名とチェックサムを求める。手間は部品の側の公開の準備（名前空間・署名鍵）で最も大きい |
| B. Git サブモジュールとして取り込み、Gradle の composite build で組む | ほぼ満たす | 固定先のコミットで中身を確かめて取り込め、make-you-chic-ui と同じ形で既存の Mandated（固定先の更新は承認を得た専用のコミット）がそのまま使える。部品の推移依存（`slf4j-api`）はこのリポジトリの構成で解決され lockfile に載る見込み（推測。設計の段で `dependencies` の出力で確かめる）。部品そのものは OSV・Dependabot の対象外になり、`vendor/` に置くと `team.md` の決まりでこのリポジトリの静的解析・カバレッジの対象からも外れる。部品の側には SpotBugs が無く、依存の定期の検査も止まっている（R2） |
| C. リリースの JAR をリポジトリに置き SHA-256 で固定する | 満たさない | `files()` の依存は lockfile に載らず、OSV・Dependabot から見えない。推移依存は手で書く必要がある。部品の側のリリースにはチェックサム・署名が無い（R2）ため、SHA-256 は最初に取り込んだものを信じるだけの固定になる。Gradle の依存の検証（`gradle/verification-metadata.xml`）は今は使っていない |
| D. GitHub Packages（Maven）から取る | 満たすが別の問題がある | 公開のパッケージでも取得に認証が要るため、CI に秘密情報（トークン）を渡すことになり、`ci.yml` の「CI は秘密情報を使わない」と食い違う。取得元の固定も広げる必要がある |
| E. 隣のディレクトリ（`../java-mustache-processor`）や `mavenLocal()` を直接参照する | 満たさない | CI で再現できず、何が入ったかを記録できない |

- セキュリティ担当としての意見: A か B を選び、C・D・E は選択肢から外すか「条件を満たさない」と明記して示す。B を選ぶときは次の2点を同じ質問の中で決めておくとよい。(1) 置き場（`vendor/` の下にするか）と、`project.md` の Forbidden「`vendor/make-you-chic-ui` の中身を直接変更しない」を java-mustache-processor にも当てるか。(2) 部品の中の HTML のエスケープはメールの XSS の要なので、このリポジトリ側のテスト（初稿の Testing Posture の（確認待ち）「メールの本文」）で必ず確かめる。
- **Gradle のプラグインの取得元（既存の状態、今回は質問にしない）** 事実: `settings.gradle.kts` に `pluginManagement` が無く、プラグイン（Spring Boot・Spotless・SpotBugs）は Gradle の既定の取得元（Gradle Plugin Portal）から来る。`FAIL_ON_PROJECT_REPOS` と `backend/gradle.lockfile` は依存だけに効き、プラグインの解決は lockfile で固定していない（`settings-gradle.lockfile` は版の一覧の読み込みだけ）。版は `libs.versions.toml` で固定しているので、今回の Intent で決め直す必要は無い。B（composite build）を選ぶと、部品の側のプラグイン（`org.owasp.dependencycheck`）も組み込みのときに解決される点だけ、設計の段で確かめる。
- **Dependabot（初稿 P10）** R1 のとおり向き先は `develop` の見込みなので、論点は受け方だけになる。事実: CI は `pull_request` で動かないため、Dependabot のプルリクエストは CI の検査を経ずに画面でマージできてしまい、統合前の関門（ローカルの `verify`）を通らない。また、Gradle の lockfile（`backend/gradle.lockfile`）を Dependabot が書き直すかは確かめていない（推測: 書き直さないと、`libs.versions.toml` だけが変わり lockfile と食い違って `verify` が落ちる）。セキュリティ担当としての意見: Dependabot のプルリクエストは「知らせ」として扱い、画面でマージしない。取り込みは手元で版と lockfile を直し、`verify` を通して統合する（プルリクエストを使わない方針と両立する）。重大度 High 以上の知らせをいつまでに取り込むか（次の Bolt の前に取り込む、など）は依頼者に決めてもらう。

### 4. 固い制約の候補（`discovered-rules.md` の候補行の補強）

初稿の候補（`<!--` の1行）の書き方と、面談で依頼者が選んだものだけを規則の行にする方針に賛成する。以下は初稿の候補の言い直しと、足す候補。

#### 4.1 招待のトークン

- 初稿の Forbidden 候補（ログ等に含めない）に賛成。範囲を既存の Forbidden とそろえ、「招待のトークン（招待の URL に含まれる値を含む）を、ログ・監査ログ・トレースの属性・外部へのエクスポート・エラー応答に含めない」とすることを勧める。事実: トレースは既定でサンプリング 1.0（`application.yaml` の `management.tracing.sampling.probability`）で、外部への送信は既定で無効。トークンを URL のパスに置くと、要求の URL がトレースの属性に入りうる（推測: Spring の観測の既定の項目）。置き場（パス・クエリ・フラグメント）は設計の段だが、この制約があれば設計が置き場を選ぶ理由になる。
- 初稿の Mandated 候補（推測できない乱数・ハッシュだけを保存・有効期限・1回だけ）に賛成。事実: 既存のリフレッシュトークンは `SecureRandom` で作り（`auth/domain/RefreshTokenValues.java`）、内部DB には SHA-256 のハッシュ（`refresh_tokens.token_hash BINARY(32)`）だけを保存している。招待のトークンも同じ作りにそろえる、と書くと具体的になる。乱数の長さ（例: 128 ビット以上）を規則に入れるかは依頼者の判断。

#### 4.2 SMTP の資格情報と外への送信

- 初稿の「SMTP の資格情報を直書きしない」に賛成。ただし既存の Forbidden「初期管理者のパスワードやトークンの署名鍵などの秘密情報を…直接書かない」は「など」で SMTP の資格情報も含む読み方ができる。新しい行にするか、既存の行の例示の追加で済ませるかは依頼者に選んでもらう（固い制約の重複を避けるなら後者。ただし昇格の道具は追記だけなので、既存の行を書き換えることはできない点に注意）。
- 初稿の「実在の宛先・外部の SMTP へ送らない」に賛成。補強として、テストデータ・見本・文書のメールアドレスは予約済みのドメイン（`example.com`・`example.org`・`.test`・`.invalid` など）に限る、を同じ行か別の候補にすることを勧める。事実: 今のテストのメールアドレスは `@example.com` が166件でほぼそろっているが、`backend/src/test/java/cherry/mastersmith/user/domain/EmailAddressTest.java` に `@example.co.jp`・`@b.jp`（実在しうるドメイン）が2件ある。今は形式の検証だけで送信しないため問題ないが、送信の機能ができると、設定の誤りで実在のドメインへ届く危険が生まれる。

#### 4.3 メールアドレス（個人情報）とメールの部品の例外

- 新しい候補 F3: 「メールアドレスを、アプリのログ・エラー応答に含めない。メールの部品（Jakarta Mail など）の例外のメッセージと SMTP サーバーの応答を、そのままログ・エラー応答に含めず、デバッグ出力（`mail.debug`）を有効にしない」。
  - 根拠（事実）: 前の Intent で「アプリのログにメールアドレスを出さない」（`access/domain/AdminAccessDeniedEvent.java` の NFR10.3）と「外部へ送るログからメールアドレス・IP・User-Agent を伏せる」（`config/ObservabilityConfig.java`、Intent `260924-followup-fixes` の NFR1）を実装したが、`team.md`・`project.md` の規則には入っていない。監査ログの `entered_email` は設計上の記録で、ここでは対象にしない。
  - 根拠（推測）: 宛先の誤りのときの Jakarta Mail の例外（`SendFailedException` など）のメッセージと SMTP サーバーの応答には宛先のアドレスが入る。既存の Forbidden「JDBC の例外や YAML・JSON Schema の部品の例外のメッセージを、そのままエラー応答に含めない」と同じ形の制約で、今回の Intent で最もメールアドレスが流れる経路になる。
- 新しい候補 M3: 「メールの本文に差し込む利用者の値は、エスケープする差し込み（Mustache の `{{ }}`）で入れ、エスケープしない差し込み（`{{{ }}}`・`{{& }}`）を使わない。招待の URL は設定したベース URL から組み立て、要求のヘッダー（Host など）から組み立てない」。初稿では Testing Posture の（確認待ち）「メールの本文」のテストとして書かれているが、テストだけでなく固い制約にするかを依頼者に選んでもらう。根拠: K-9（`api-documentation.md`）、R2。

#### 4.4 候補の一覧（統合用。`<!--` の1行の形のまま使える）

```
<!-- 候補（面談で確かめる）: NEVER 招待のトークン（招待の URL に含まれる値を含む）を、ログ・監査ログ・トレースの属性・外部へのエクスポート・エラー応答に含めない -->
<!-- 候補（面談で確かめる）: ALWAYS 招待のトークンは SecureRandom で作り、内部DB には SHA-256 のハッシュだけを保存し、有効期限と1回だけの使用を強制する（リフレッシュトークンと同じ作り） -->
<!-- 候補（面談で確かめる）: NEVER メールアドレスを、アプリのログ・エラー応答に含めず、メールの部品の例外のメッセージと SMTP サーバーの応答をそのまま出さず、mail.debug を有効にしない -->
<!-- 候補（面談で確かめる）: ALWAYS メールの本文に差し込む利用者の値はエスケープする差し込みで入れ、招待の URL は設定したベース URL だけから組み立てる -->
<!-- 候補（面談で確かめる）: NEVER 開発・テスト・負荷の試験で、実在の宛先や外部の SMTP へメールを送らず、テストデータ・見本のメールアドレスは予約済みのドメイン（example.com・.test など）に限る -->
```

### 5. 必須テストの一覧（Testing Posture の（確認待ち）行）への追加の提案

初稿の「招待と登録の完了」「パスワードの変更」「メールの本文」に賛成し、次を足すことを勧める（面談で一覧に入れるかを一緒に確かめる）。

- 招待のトークンの漏えい: ログ・監査ログ・トレースの属性に招待のトークンの値が含まれないこと（既存の「秘密情報の漏えい」の項目の対象に招待のトークンを足す形でもよい）。
- メールのヘッダーの差し込み: 宛先・件名・差し込む氏名に改行（CR・LF）を含む値を入れても、ヘッダーが増えない、または拒否されること。
- メールの送信の失敗: 送信が失敗したときの応答・ログに、宛先のメールアドレス・SMTP サーバーの応答・資格情報が含まれないこと。
- 招待・登録の完了の API の総当たり: 存在しない・使用済みのトークンで何度呼んでも、応答から利用者の有無が分からないこと（初稿の項目に含まれている。回数の制限を置くかは★として要件で決める）。

### 6. 面談で決めてもらう隙間（セキュリティの面からの優先順）

- **S1（最優先）** java-mustache-processor の取り込み方（初稿 P1）。3.5 の評価を添え、C・D・E は条件を満たさないと示す。B を選ぶ場合は置き場と Forbidden の扱いも同時に。
- **S2** 固い制約の候補（初稿 P3・P4 と 4.3 の F3・M3）。1問で複数選べる形にするとよい。
- **S3** SpotBugs の関門に `PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` を priority にかかわらず加えるか（3.2）。
- **S4** Dependabot の知らせの受け方（初稿 P10 を R1 で直した形）。画面でマージしない、手元で lockfile ごと直して `verify` を通す、High 以上の取り込みの期限。
- **S5（低）** 依存の脆弱性の定期の検査（週1回の CI など）を足すか（3.4）。
- **S6** メールのテンプレートの検査の範囲（初稿 P9 に、エスケープしない差し込みの検出を加えるか）。

### 7. 要件・設計の段に回す論点（セキュリティの面から追加）

- 招待のトークンを URL のどこに置くか（パス・クエリ・フラグメント）と、画面が読み取った後に URL から消すか（`history.replaceState`）。事実: `Referrer-Policy` は `same-origin`（`SecurityConfig.java`）、フォントは自己ホスティングで、外への Referer は今は出ない。
- 招待・登録の完了・パスワードの変更の API の回数の制限と、パスワードの変更の「今のパスワードの確かめ」の失敗をアカウントロックの回数に数えるか。
- 配備先が決まったときの SMTP の接続の暗号化（STARTTLS・SMTPS と証明書の検証）。今は手元の受け手だけなので、配備先が決まるまでの前提として記録する。
- Spring Boot のメールのヘルスチェック（SMTP へ接続して確かめる仕組み）を使うか。使うと SMTP が無い手元の環境で健全性が DOWN になり、compose の健全性の確かめに効く（推測）。
- 手元とテストのメールの受け手のイメージ・部品の版の固定（既存の compose は対象DB の見本をダイジェストで固定。E16）とライセンス。
- メール送信の部品（Angus Mail など）のライセンスの ADR（3.4）。

## Positions

- OBJECT: E6・D4・P10 の「Dependabot は `main` に向く見込み」— `git ls-remote --symref origin HEAD` で既定のブランチは `develop` と確かめた。手元の `origin/HEAD` は古い記録で、論点は向き先ではなく受け方だけになる（R1）。
- OBJECT: P1 の候補「リリースの JAR をリポジトリに置き SHA-256 で固定する」を他と同列に並べること — 主担当自身の条件（lockfile・脆弱性検査・更新の通知の対象から外れない）を満たさず、部品の側にチェックサム・署名も無いため、示すなら「条件を満たさない」と明記すべき（3.5）。
- OBJECT: Deployment の（確認待ち）行の「既定は送らない（または手元の受け手）」— 「または」で既定が決まっていない。安全な既定は「接続先の設定が無ければ送らない」の1つに決め、手元の受け手は compose の profile と `.env` で明示的に選ぶ形にすべき。
- AGREE: P1 を質問にし、取り込み方を供給網の決まりとして依頼者に決めてもらう — 取得元の固定と部品の公開の形が合わず、証拠だけでは決まらない。
- AGREE: P2 メールのテストをテスト用の受け手で実際に受けて確かめる — ヘッダーの差し込み・エスケープ・宛先の誤りはモックでは確かめられない。
- AGREE: P3・P4 の固い制約の候補と、`<!--` の1行で候補を置く書き方 — 昇格の道具の振る舞い（E12）に合い、依頼者が選んだものだけが規則になる。4.4 の言い直しと追加を併せて示すことを勧める。
- AGREE: P5 認証に関わる必須テストに招待・登録の完了・パスワードの変更・メールの本文を加える — 前回 DSL の一覧を足したのと同じ判断で、5 の追加を併せて示すことを勧める。
- AGREE: P9 テンプレートのヘッダーを `{{! ... }}` にする — HTML のコメントは受け手に届き、構文は部品にある（R2）。
- AGREE: P8・D3 サブモジュールの固定先の更新を含む変更の fast-forward — セキュリティの面では中立。どちらの形でも統合の前の `verify` は必ず通すことを行に明記するとよい。
- AGREE: P7・D5 既存のパッケージを実測し直して下限の対象に戻す向きだけを認める — 除外を増やさない既存の決まりと合う。
- AGREE: D1・P11 対象DB の結合テストの記録の更新 — 依頼者の過去の決定の反映だけで、セキュリティの面の影響は無い。
- AGREE: 「要件・設計の段に回すべき論点」の切り分け — チームの進め方と機能の中身の境目が妥当。7 の追加を併せて回すことを勧める。
