**Collaborator:** aidlc-devsecops-agent

## Contribution

セキュリティの支援役（aidlc-devsecops-agent）の独立レビュー。対象は初稿の3ファイル（`team-practices.md`・`discovered-rules.md`・`evidence.md`）で、リンタ・フォーマット、静的解析（SAST）と動的検査、秘密情報と依存関係の検査、供給網の管理を、実際の設定ファイルと照らし合わせた。読んだもの: `.github/workflows/ci.yml`、`.github/dependabot.yml`、`.pre-commit-config.yaml`、`.gitleaks.toml`、`.gitignore`、`.dockerignore`、`config/npm-build-tools.txt`、`build.gradle.kts`（`requireTool`・`gitleaksScan`・`osvScan`）、`backend/build.gradle.kts`（SpotBugs・`spotbugsGate`・`dependencyLocking`）、`backend/config/spotbugs-exclude.xml`、`gradle/libs.versions.toml`、`gradle/wrapper/gradle-wrapper.properties`、`backend/gradle.lockfile`（該当の行だけ）、`Dockerfile`・`compose.yaml` のイメージの行、`.env.example`（値の空の行の名前だけ）、`backend/src/main/resources/application.yaml` の本文の上限、`README.md` の該当の行、コード知識ベースの `code-quality-assessment.md`・`dependencies.md`・`technology-stack.md`。`git`・`./gradlew`・`npm`・`docker` は実行しておらず、`.env` と鍵ファイルは開いていない。

### 1. 初稿と実態の照合

| # | 初稿の記述 | 実態 | 判定 |
|---|---|---|---|
| D1 | Gitleaks をコミット前と `verify`（CI）で実行 | `.pre-commit-config.yaml` で v8.30.1、`gitleaksScan` が `--redact` で履歴全体を検査、CI は版と SHA-256 で固定して取得。`.gitleaks.toml` は既定の規則＋記録の目印のハッシュ値の除外1件（理由つき、対象を狭く絞っている） | 一致 |
| D2 | SpotBugs ＋ FindSecBugs、重大度 High で失敗 | effort MAX・報告は LOW から、除外は空、テストのコードは対象外。`spotbugsGate` は報告の XML の `priority="1"` の件数で失敗させる | 一致（ただし D2 の補足を参照） |
| D3 | OSV-Scanner の判定（Gradle は CVSS 7.0 以上、npm は実行時／開発用／成果物を作る道具／`MAL-`） | `osvScan` の判定のコードと `config/npm-build-tools.txt` がそのとおり。lockfile が無ければ失敗、判定できない結果も失敗 | 一致 |
| D4 | Dependabot（gradle・npm・github-actions・docker） | そのとおり。npm は `/frontend` だけ（`vendor/make-you-chic-ui` は相手のリポジトリ側で更新する、とコメントで明記） | 一致 |
| D5 | lockfile で版を固定、CI は lockfile どおり | Gradle は `lockAllConfigurations()`、npm は `package-lock.json`、Actions はコミットのハッシュ、`Dockerfile` のベースイメージは版の番号のタグ | 一致（ただし D5 の補足を参照） |
| D6 | CodeQL・OWASP ZAP・コンテナイメージの検査は採用しない | 設定に無い | 一致 |
| D7 | `.gitignore` に `.env`・鍵ファイル | `.env`・`.env.*`（`!.env.example`）・`*.pem`・`*.key`・`*.p12`・`*.jks`、内部DB のバックアップも除外。`.dockerignore` は WAR だけを送る | 一致 |
| D8 | CI は秘密情報を使わない | `permissions: contents: read`、リリースの job だけ `contents: write` | 一致 |

初稿の基準の引き継ぎ（`team.md` の5節をそのまま）は実態と食い違わない。

**D2 の補足（事実の精度）**: SpotBugs の `priority` は「確からしさ（confidence）」で、重大さ（severity）は別の `rank`（1〜20、小さいほど深刻）で表される。今の `spotbugsGate` は「確からしさが高いもの」を High と呼んで止めている。FindSecBugs の SQL インジェクション系（`SQL_INJECTION_JDBC` など）は、文字列の出どころが追えないと priority 2 になることが多く、今の関門では警告にとどまる。今回の Intent は対象DB に対して、メタデータや DSL から来た表名・列名を SQL に組み込む処理を初めて持つため、この差が効く（→ S-6）。

**D5 の補足（供給網）**: Gradle の lockfile は版だけを固定し、中身のハッシュ（チェックサム）は確かめない。`gradle/verification-metadata.xml`（依存の検証）は無く、`gradle/wrapper/gradle-wrapper.properties` に `distributionSha256Sum` も無い。npm の `package-lock.json` は `integrity` を持つので、この弱さは Gradle 側だけにある。今回 JDBC ドライバー3種類・YAML・JSON Schema の検証・Testcontainers と依存が大きく増えるので、見直しの機会になる（→ S-7）。

**E6 の補足**: Dependabot の `docker` の監視は Dockerfile を対象にする。`compose.yaml` のイメージ（`otel/opentelemetry-collector:0.161.0`・`grafana/otel-lgtm:0.33.1`）も今は監視の外にある（Dependabot には compose 用の別の種類があるが、今の設定では使っていない。対応の状況は設計の段で確かめる）。Testcontainers のイメージ（テストのコードの中の文字列）と、Testcontainers が裏で使う `testcontainers/ryuk` のイメージも同じく監視の外になる。

### 2. 今回の Intent の脅威（STRIDE で要点だけ）

| 入口・流れ | 脅威 | 必要な対策（決まりの案） |
|---|---|---|
| 利用者が投入する DSL（YAML） | サービス停止（D）: 別名（アンカー）の展開の爆発、深い入れ子、巨大な文字列。改ざん・権限昇格（T/E）: YAML のタグによる任意の型の生成。あいまいさ（T）: 同じキーの重複が後勝ちで黙って上書きされ、検証を通った内容と利用者の意図がずれる。YAML 1.1 の `yes`/`on` などが真偽値に化ける | 読み込みの上限（本文の大きさ・文字数・入れ子の深さ・別名の数）を明示して設定し、既定値に頼らない。グローバルタグ（`!!java...` 等）を拒否し、決まった DTO か汎用の木（ツリー）にだけ読み込む。重複キーはエラーにする。検証は JSON Schema と業務の決まりの2段で、サーバー側を正とする。SnakeYAML 2.6 は既に推移依存としてある（`backend/gradle.lockfile`）が、Jackson 3 の YAML の部品がどの読み込みの部品を使い、上の上限をどこで設定できるかは設計の段で確かめる |
| JSON Schema の検証 | 情報漏えい・SSRF（I）: `$ref`・`$schema` の外部 URL の取得。停止（D）: 正規表現の `pattern` の暴走（ReDoS） | スキーマはクラスパスからだけ読み、ネットワークからの取得を無効にする。DSL の中に利用者が正規表現を書ける項目を作るなら、その評価に時間の上限か安全な正規表現の実装を使う（→ S-3） |
| 対象DB の接続情報（`mastersmith.target-db.*`） | 漏えい（I）: パスワード、資格情報を含む JDBC の URL、ホスト・ユーザー名が、ログ・`TraceAspect` の TRACE・エラー応答・生成した DSL・監査ログに出る | 既存の Forbidden に加え、接続情報は設定（環境変数・`.env`）だけから受け取り、画面・API・DSL からは受け取らない。パスワードは URL に埋め込まず別の項目で渡す。設定の型の `toString` は伏せ字（初稿どおり） |
| JDBC の URL と接続の属性 | 権限昇格・情報漏えい（E/I）: ドライバーの危険な属性（MySQL Connector/J の `allowLoadLocalInfile`・`autoDeserialize`、MariaDB の `allowLocalInfile`、PostgreSQL の `socketFactory`・`sslfactory` によるクラスの生成。PostgreSQL JDBC では過去にこの経路の脆弱性 CVE-2022-21724 がある） | 接続の属性はコードで安全側に固定し（ローカルファイルの読み込み・自動の逆シリアル化を無効）、設定で上書きさせない。URL の形（許すドライバーの接頭辞）を起動時に検証する |
| 対象DB への読み取り | 改ざん（T）: 識別子の SQL インジェクション。権限（E）: 対象DB のアカウントが書き込み権限を持つ | 値はプレースホルダー、識別子はメタデータの一覧にあるものだけを許し、DB ごとの引用符で囲む。既定 DSL の生成は `DatabaseMetaData` の読み取りだけで行い、接続を読み取り専用（`setReadOnly(true)`）にする。対象DB のアカウントは最小権限（メタデータの参照だけ）を運用の前提として README に書く。「適用」が対象DB に書くかは未確定（初稿の懸念と同じ） |
| 対象DB から読んだメタデータ（表名・列名・コメント） | 改ざん（T）: 画面への注入（XSS）、DSL の書き出しでの YAML の注入 | メタデータも信頼できない入力として扱う。画面は React の既定のエスケープに任せ、`react/no-danger` の規則を維持。YAML の書き出しは部品の直列化を使い、文字列の連結で作らない |
| エラー応答とログ | 漏えい（I）: JDBC の例外のメッセージ（ホスト・ユーザー名・SQL）、YAML・Jackson の例外のメッセージ（内部のクラス名）がそのまま応答に出る。DSL の本文の丸ごとの記録でログが膨れる | 検証エラーは「行・列・項目のパス・安定した `code`」に写し替えて返し、部品の例外のメッセージは返さない。DSL の本文はログに出さず、大きさと要約の値（ハッシュなど）だけを記録する |
| 生成・リセット・適用 | 否認（R） | 監査の対象にするかは要件定義で決める（初稿の Q-G と同じ）。監査にするなら、DSL の本文ではなく版とハッシュを記録する |

### 3. 依頼者への質問の候補（初稿の Q-A〜Q-N に足す、または補う）

- **S-1（Q-L を具体化）**: 投入された DSL を信頼できない入力として扱う決まりを、基準（`team.md` の Code Style の「静的解析とセキュリティ検査」）に入れるか。案: (a) 入れる。内容は「本文の大きさ・入れ子の深さ・別名の数の上限を明示する、グローバルタグと任意の型の生成を拒否する、重複キーをエラーにする、検証はサーバー側を正とする」。具体的な数値は設計の段で決める。(b) 基準には入れず、今回の Intent の NFR 要件だけで扱う。推奨は (a)。後続の Intent でも利用者の投入を扱うため。
- **S-2**: 悪意のある DSL のテストを、認証・認可・監査と同じ形の「必ず書くテスト」の一覧に加えるか。案: 別名の展開の爆発（いわゆる billion laughs）、深い入れ子、上限を超える大きさ、`!!` のグローバルタグ、重複キー、`yes`/`on` などの型の揺れ、JSON Schema の `$ref` の外部 URL が取得されないこと。どれも上限で拒否され、応答が Problem Details で内部のメッセージを含まないこと。jqwik の性質ベースのテストで「どんな入力でも例外が漏れず 4xx で返る」を確かめる。
- **S-3**: DSL の中に、利用者が正規表現（入力の検証の規則など）を書ける項目を持たせるか。持たせるなら、評価の時間の上限か暴走しない正規表現の実装を要件に入れる。
- **S-4（Q-G を補う）**: 対象DB の接続情報の漏えいのテストに加え、「接続情報は設定からだけ受け取り、画面・API・DSL から受け取らない」「JDBC の危険な接続の属性をコードで固定し、設定で上書きできない」「接続は読み取り専用」を固い制約（Mandated・Forbidden）にするか。
- **S-5**: 対象DB のアカウントを最小権限（メタデータの参照だけ、「適用」が対象DB に書く場合はその範囲だけ）にすることを、配備の手順（README・`.env.example` の説明）の前提として書くか。配備先が開発者の PC 上のコンテナである間は、確認用の対象DB（Q-H）のアカウントもそれに合わせる。
- **S-6**: SpotBugs の関門の基準（今は確からしさ `priority 1` で止める）について、対象DB に SQL を組み立てる部品では、FindSecBugs の SQL インジェクション系の指摘を priority によらず止めるか。案: (a) 今の基準のまま。警告を Bolt の確認で必ず見て、誤検出なら理由つきで `backend/config/spotbugs-exclude.xml` に書く。(b) SQL インジェクション系（`SQL_INJECTION*`）だけ priority 2 以上で止める。(c) 関門の判定を `rank`（重大さ）に変える。推奨は (b)。基準の「重大度 High 以上」の言い換えにとどまり、関門を緩めない。
- **S-7（供給網）**: 依存が大きく増える今回、Gradle の依存の検証（`gradle/verification-metadata.xml` の SHA-256）と Gradle Wrapper の `distributionSha256Sum` を入れるか。案: (a) 両方入れる。依存を足すたびに検証の一覧の更新が要る（手間が増える）。(b) Wrapper のチェックサムだけ入れる。(c) 入れない（今のまま、lockfile の版の固定と OSV の関門だけ）。
- **S-8（Q-C を補う）**: Testcontainers の対象DB のイメージの固定と更新の見直し。案: (a) イメージ名をタグ＋ダイジェストでテストのコードの1か所に置き、更新は手で見直す（初稿どおり）。(b) イメージを Dockerfile の形のファイル（`FROM mysql:x.y.z@sha256:...` だけのファイル）に置いてテストから読み、`.github/dependabot.yml` の `docker` の監視の対象のディレクトリに足して、更新の知らせを受ける。あわせて `compose.yaml` の監視のイメージも Dependabot の対象に入れるか。テスト用のイメージはコンテナイメージの検査（採用しない）の対象外のままでよい（配る成果物に入らないため）。
- **S-9（Q-M を具体化）**: 新しい依存のライセンスの確かめ方。事実: WAR は GitHub のリリースに添付して公開しており（`.github/workflows/ci.yml` の `release`）、同梱した部品は「配布」に当たる。JDBC ドライバーは MySQL Connector/J が GPL v2（Universal FOSS Exception つき）、MariaDB Connector/J が LGPL 2.1、PostgreSQL JDBC が BSD 2-Clause。案: (a) 採用の前に手で確かめ、Apache License 2.0 と異なるものは採用の理由と義務（ライセンス文の同梱など）を設計の記録（ADR）に残す。(b) (a) に加えて、許すライセンスの一覧を決め、OSV-Scanner のライセンスの検査（2 系の `--licenses` の指定。実際の挙動は設計の段で確かめる）を `verify` に足して、一覧の外のライセンスで失敗させる。(c) 決まりを置かない。MariaDB の接続に MySQL のドライバーを使う（1つ減らす）かは Q-A の判断と関わる。
- **S-10**: Gitleaks に、資格情報を埋め込んだ JDBC の URL（`jdbc:...://user:password@...` や `password=` を含む URL）を検出する独自の規則を足すか。既定の規則には JDBC の URL の専用の規則が無い見込みで（設計の段で確かめる）、テスト用の DSL・設定・README の例に本物の接続情報が紛れる経路をふさぐ。テストの接続情報は Testcontainers が起動のたびに作る値を使い、固定の値を書かない。

### 4. discovered-rules の候補の追加（依頼者が固い制約として述べた場合だけ）

初稿の候補（対象DB を変更しない、接続情報を DSL・プレビュー・監査ログに含めない、実際の DB でテストする、ライセンスを確かめる）に賛成し、次を候補として足す。

- 候補: `ALWAYS 利用者が投入する DSL（YAML）は信頼できない入力として扱い、大きさ・入れ子の深さ・別名の数の上限を明示し、グローバルタグと任意の型の生成を拒否し、重複キーをエラーにする`
- 候補: `NEVER 対象DB の接続情報（接続先・ユーザー名・パスワード・JDBC の URL）を、画面・API・DSL から受け取らない（設定だけから受け取る）`
- 候補: `NEVER JDBC の例外・YAML や JSON Schema の部品の例外のメッセージを、そのままエラー応答に含めない`

既存の Forbidden（パスワードをログ・エラー応答に含めない）はパスワードだけを対象にしているため、「接続先・ユーザー名・JDBC の URL」まで広げるかは初稿の候補（Forbidden の2つ目）と合わせて1行にまとめるとよい。

### 5. 変えなくてよいこと

- 動的検査（OWASP ZAP）・CodeQL・コンテナイメージの検査を採用しない方針は、今回も変える必要は無い。代わりに S-2 の悪意のある入力のテストを結合テスト（`XxxIT`）で持つことで、投入の経路を確かめられる。
- OSV の関門の判定の決まりは、新しい Gradle・npm の依存にもそのまま当たる。JDBC ドライバーは実行時の依存なので CVSS 7.0 以上で止まる。

## Positions

- AGREE: `team.md` の5節の本文を変えずに引き継いだこと — Gitleaks・SpotBugs＋FindSecBugs・OSV・Dependabot・lockfile・Actions のハッシュ固定の実態と食い違いが無い。
- AGREE: Code Style の Q-L（投入された DSL を信頼できない入力として扱う） — 方向は正しい。重複キーの拒否とサーバー側を正とすることを足し、基準に入れる案（S-1）を推す。
- AGREE: 秘密情報を持つ設定の型の `toString` を伏せ字にする行 — `TraceAspect` の TRACE の経路（TD-4）への対策として必要。
- AGREE: discovered-rules の候補（対象DB を変更しない、接続情報を DSL・プレビュー・監査ログに含めない） — 既存の Forbidden はパスワードだけで、接続先・ユーザー名・JDBC の URL を覆わないため。
- OBJECT: Code Style の対象DB の `DataSource` の行 — 内部DB との取り違えだけを扱い、接続情報を設定だけから受け取ること、JDBC の危険な接続の属性の固定、読み取り専用の接続、識別子の許可の一覧と引用符の決まりが抜けている（S-4・S-6）。
- OBJECT: Q-M（ライセンス） — WAR を GitHub のリリースで公開しているため配布は仮定ではなく事実であることと、PostgreSQL JDBC（BSD 2-Clause）が書かれておらず、確かめ方の選択肢（手で／OSV-Scanner のライセンスの検査）も無い（S-9）。
- OBJECT: evidence.md の E6 — Testcontainers のイメージに加え、`compose.yaml` の監視のイメージと Testcontainers の `ryuk` のイメージも Dependabot の外にある点が抜けている。
- OBJECT: 「重大度 High 以上で止める」の SpotBugs の部分の読み方 — 実装は確からしさ（priority）で判定しており、対象DB に SQL を組み立てる今回は SQL インジェクション系が警告にとどまりうるため、面談で確かめるべき（S-6）。
- OBJECT: 供給網の不足点が初稿に無い — Gradle の依存の検証（チェックサム）と Wrapper の `distributionSha256Sum` が無く、依存が大きく増える今回は面談の論点にすべき（S-7）。
