**Collaborator:** aidlc-devsecops-agent

## Contribution

支援エージェント（DevSecOps）として、主担当の初稿（`team-practices.md`、`discovered-rules.md`、`evidence.md`、`practices-discovery-timestamp.md`）を独立に読み、lint・フォーマット規約、SAST/DAST、秘密情報の検出、依存関係の脆弱性検査、サプライチェーン（Gitサブモジュールを含む）の観点で補足する。新規プロジェクトのため、ここに書く検査の組み合わせや閾値はすべて **提案** であり、チームで確定した事実ではない。確定は面談での人間の判断による。

### 1. 調べたものと分かったこと（主担当の E1〜E13 への追加）

| # | 対象 | 分かったこと |
|---|---|---|
| S1 | リポジトリ全体（`git ls-files`） | 管理対象のファイルは `.gitignore`、`.gitmodules`、`LICENSE`、`aidlc.settings.json`、`vendor/make-you-chic-ui`（サブモジュール）と AI-DLC の作業領域のみ。CI（`.github/`）、秘密情報の検出、SAST、依存関係の検査は **いずれも存在しない**。`.env` や鍵ファイルらしきものは管理対象にない |
| S2 | `.gitignore` | `*.local`、`.idea`、`node_modules` 等は除外しているが、`.env` / `.env.*`、`*.pem`、`*.key`、`*.p12`、`*.jks` など、秘密情報を置きがちなファイルの除外パターンがない。本Intentでは初期管理者のパスワードとトークンの署名鍵を設定ファイル／環境変数で渡す（`aidlc/spaces/default/intents/260922-auth-audit-base/ideation/scope-definition/scope-document.md` S3・S4）ため、手元で作る設定ファイルを誤ってコミットする経路が開いている |
| S3 | `.gitmodules`、`git submodule status` | サブモジュールの URL は `https://github.com/agwlvssainokuni/make-you-chic-ui`（HTTPS）。コミット `cde0e8b`（`heads/main`）に固定されている。固定先コミットに署名はない（`%G?` が `N`） |
| S4 | `vendor/make-you-chic-ui/docs/integration-guide.md`（読み取りのみ） | 同リポジトリは「検証・サンプル用のプロトタイプ」で、npm registry への発行も CI/CD もない（「本番運用には未対応」と明記）。組み込みの推奨手順は、サブモジュールの中で `npm install` と `npm run build` を実行し、利用側から `file:vendor/make-you-chic-ui/packages/make-you-chic-ui` として参照する方式 |
| S5 | `vendor/make-you-chic-ui/package-lock.json` | lockfileVersion 3 がコミットされている。取得元の410件はすべて `https://registry.npmjs.org`。配布パッケージ本体の依存は `peerDependencies`（react / react-dom `^19.0.0`）のみで、残りはビルド・テスト用の開発依存。ただし組み込み手順で `npm install` を実行するため、これらの開発依存（とそのインストールスクリプト）は **利用側のビルド環境で実行される** |
| S6 | `vendor/make-you-chic-ui/.oxlintrc.json`、`eslint.config.js` | correctness カテゴリを error とし、react / jsx-a11y / typescript 系のルールを明示的に有効化。`react/jsx-no-target-blank` と `react/no-danger-with-children` は有効だが、`react/no-danger`（`dangerouslySetInnerHTML` の使用そのものを禁止）や `no-eval` 系のルールは設定にない。セキュリティ専用のルールセットは入っていない |
| S7 | `vendor/make-you-chic-ui/packages/make-you-chic-ui/src/theme/storage.ts` | デザインシステムはテーマ設定を `localStorage` に保存する。同じ画面上の JavaScript から読める保存先であり、トークンの置き場所を決める際の比較対象になる（トークンは同じ場所に置くべきではない。詳細は後続の NFR 設計で扱う） |
| S8 | `git remote get-url origin` | 本リポジトリは GitHub 上にある。公開（public）か非公開（private）かはリポジトリの中からは判断できない。GitHub の CodeQL、秘密情報のスキャンとプッシュ保護は、公開リポジトリなら無料、非公開なら有償機能（GitHub Advanced Security）になるため、選べる道具が変わる |

### 2. 推測したこと

- D1（S1, S2）: 秘密情報の混入を防ぐ仕組みが何もない状態で、パスワード・署名鍵を扱う機能から作り始める。最初の Bolt（土台）の時点で、秘密情報の検出と `.gitignore` の補強を入れておくのが最も安い。
- D2（S3, S4, S5）: make-you-chic-ui は依頼者自身のリポジトリで信頼度は高いが、CI も脆弱性検査もないため、そのコードと開発依存の安全性は **本リポジトリのパイプラインで確かめる以外に確かめる場所がない**。サブモジュールの更新は、外部コードの取り込み（サプライチェーンの変更）として扱う必要がある。
- D3（S6）: フロントエンドの lint 設定を make-you-chic-ui に揃える主担当の提案（P16）は妥当だが、ログイン画面とトークンを扱う利用側アプリには、いくつかのセキュリティ系ルールを追加する価値がある。
- D4（主担当 I8, 初稿の Deployment・Way of Working）: 初稿は「プルリクエストを使わず、ローカルで統合して `origin` へプッシュし、統合先へのプッシュで CI を実行し、失敗したら統合を止める」としている。**プッシュを契機とする CI は、プッシュ済みの統合を止められない**（事後に失敗を知らせるだけ）。検査で統合を本当に止めたいなら、(a) プルリクエスト＋ブランチ保護（必須チェック）にするか、(b) 同じ検査をローカルの pre-push フックで実行するか、のどちらかが必要。これは面談で決める判断事項である。

### 3. 提案: セキュリティ検査の組み込み（Deployment 節・Code Style 節への追加案）

1名体制・期限なし・GitHub 上という前提で、無料で導入でき、道具の数を増やしすぎない組み合わせを提案する。段階は「最初の Bolt（土台）で入れる」「機能の Bolt と並行して入れる」「配備先が決まってから入れる」の3つに分ける。

| 検査 | 対象 | 道具（候補） | 実行する場所 | 失敗時の扱い（提案） | 導入時期 |
|---|---|---|---|---|---|
| 秘密情報の検出 | 全ファイル・コミット履歴 | Gitleaks（代替: GitHub の秘密情報スキャン＋プッシュ保護） | コミット前（pre-commit フック）＋ CI | 検出したら統合しない。誤検知は理由付きで許可リストに登録 | 土台 |
| フォーマット・lint | Java / TypeScript / CSS | 主担当の案（Prettier、oxlint＋ESLint、Stylelint、Java 側は Spotless＋Checkstyle 等） | ローカル＋ CI | 違反があれば統合しない（org.md の既定どおり） | 土台 |
| SAST（静的解析） | Java | SpotBugs＋FindSecBugs（ビルドに組み込む） | ローカルのビルド＋ CI | 重大度 High 以上で統合しない。Medium 以下は警告 | 土台（ビルドツール確定後） |
| SAST（静的解析） | Java / TypeScript | CodeQL（公開リポジトリの場合）、代替: Semgrep（OSS 版） | CI | 重大度 High 以上で統合しない | 機能の Bolt と並行 |
| 依存関係の脆弱性 | Java の依存、利用側アプリの `package-lock.json`、`vendor/make-you-chic-ui/package-lock.json` | Dependabot（通知と更新 PR）＋ OSV-Scanner または `npm audit --audit-level=high`、Java 側は OWASP Dependency-Check 等 | CI（ビルドごと）＋定期実行（週1回） | 既知の悪用手段がある Critical / High で統合しない。修正版がない場合は期限付きの例外として記録 | 土台 |
| 依存の更新 | Maven/Gradle、npm、GitHub Actions、Git サブモジュール | Dependabot の version updates（`gitsubmodule` と `github-actions` を含める） | GitHub | 更新 PR を依頼者が確認して取り込む | 土台 |
| SBOM（部品表） | 配布する成果物 | CycloneDX（Java のビルドプラグイン、`@cyclonedx/cyclonedx-npm`） | CI（リリース時） | 生成できなければリリースしない | 配備先が決まってから |
| DAST（動的検査） | 起動したアプリの API・画面 | OWASP ZAP のベースラインスキャン | 検証環境（またはローカルで起動したコンテナ） | High の指摘があれば本番へ進めない | 配備先が決まってから（Operation フェーズ） |
| コンテナイメージ | イメージを作る場合 | Trivy | CI | Critical があれば配備しない | コンテナ化を決めた時点 |
| IaC | インフラ定義を書く場合 | Checkov（CDK なら cdk-nag） | CI | High で配備しない | IaC を書く時点 |

補足:

- **例外（抑止）の扱い**: 検査の指摘を抑止する場合は、理由と見直し期限をリポジトリ内の設定（抑止ファイルやアノテーション）に書き、依頼者が承認する。抑止の理由を書かない一括無効化はしない。
- **GitHub Actions 自体の安全対策**（CI を GitHub Actions に置く場合）: ワークフローの `permissions` を既定で `contents: read` に絞る。第三者製のアクションはタグではなくコミットの SHA で固定し、Dependabot で更新する。外部からのプルリクエストでは秘密情報を使うジョブを動かさない。
- **統合を止める仕組み**: 上の「統合しない」を実際に効かせるには、D4 のとおり (a) プルリクエスト＋ブランチ保護の必須チェック、または (b) pre-push フックでの同じ検査、のいずれかを選ぶ必要がある。1名体制で PR を使わない場合は (b) を最低限とし、CI は取りこぼしを後から知らせる安全網と位置づける。
- **`.gitignore` の補強（土台の Bolt で行う提案）**: `.env`、`.env.*`（`.env.example` は除く）、`*.pem`、`*.key`、`*.p12`、`*.jks`、および Java 側でローカル専用の設定ファイル名を決めたらその名前を追加する。

### 4. 提案: サプライチェーンの管理（make-you-chic-ui サブモジュール）

主担当の候補 F3（サブモジュールの中身を直接変更しない）に加え、次の運用を提案する。

1. **固定と更新**: サブモジュールは常に特定のコミットに固定する（現状どおり）。固定先の更新（`git submodule update --remote` 等）は、依頼者の承認を得た専用のコミットで行い、コミットメッセージに更新前後のコミットハッシュを書く。更新時は `git diff --submodule=log` で取り込む変更を確認する。
2. **固定先の一致確認**: CI ではサブモジュールを固定先のコミットで取得し、`git submodule status` の先頭に `+`（固定先と作業ツリーの不一致）や `-`（未取得）がないことを確かめる。
3. **再現できるインストール**: サブモジュール内・利用側ともに `npm install` ではなく `npm ci`（lockfile どおりに入れる）を CI で使う。利用側アプリの `package-lock.json` もコミットする。インストールスクリプトの実行を抑止する（`--ignore-scripts`）かどうかは、ビルドに必要なスクリプトの有無を確かめてから決める。
4. **検査範囲に含める**: 依存関係の脆弱性検査の対象に `vendor/make-you-chic-ui/package-lock.json` を含める。make-you-chic-ui には CI がない（S4）ため、サブモジュールを更新したときは、同リポジトリの lint とテストも本リポジトリの CI で1回実行する。
5. **取得元**: URL は HTTPS のまま維持する（CI から追加の鍵なしで取得できる）。取得元の依存は `registry.npmjs.org` のみ（S5）であり、別のレジストリを追加しない。
6. **ライセンス**: make-you-chic-ui は Apache License 2.0 で、本リポジトリのライセンス方針と整合する。依存のライセンス検査は必須とはしないが、SBOM を作る段階で一覧を確認できるようにする。

### 5. 面談で確認する「必ず守る／絶対にしない」の候補（discovered-rules.md への提案）

主担当の候補 M1〜M3、F1〜F3 への意見と、追加の候補を示す。**いずれも人間が明言した場合にだけ採用する**（初稿の方針に同意）。

**主担当の候補への意見**

- M1（統合前に lint・フォーマット・ビルド・全テスト）: 同意。**秘密情報の検出と依存関係の脆弱性検査（Critical/High）も同じ条件に含める**ことを提案する。また、D4 のとおり、何によってこの条件を強制するか（PR の必須チェックか pre-push フックか）を同時に決める必要がある。
- M2（認証・認可・監査に関わる変更には失敗系のテスト）: 同意。
- M3（本番配備は依頼者の手動承認）: 同意。org.md の Deployment 既定と矛盾しない。
- F1（秘密情報のハードコードとログ出力の禁止）: 趣旨に同意するが、**2つの制約に分ける**ことを提案する。
  - 前半の「ハードコードしない」は `aidlc/spaces/default/memory/phases/construction.md` の Security（"Never hardcode credentials..."）と重なる。重複して記録すること自体は害がないが、本プロジェクト固有の対象（初期管理者のパスワード、トークンの署名鍵）を明記すると意味が増す。
  - 後半の「出力しない」は出力先を広げる。本Intentには構造化ログ・分散トレース・OTEL エクスポート（scope-document.md S10）があるため、ログと監査ログだけでなく、**トレースの属性・OTEL で外部へ送るデータ・エラー応答**も対象に含める。
- F2（テストが失敗した状態で統合しない）: M1 と裏表の関係で内容が重なる。どちらか一方に統合してよい。
- F3（サブモジュールの中身を直接変更しない）: 同意。上の4.の運用とあわせて採用を提案する。

**追加の候補**

- 候補F4: NEVER `.env` や鍵ファイルなど、秘密情報を含む設定ファイルをコミットしない（`.gitignore` で除外し、見本は値を空にした `.env.example` 等として置く）
- 候補F5: NEVER パスワード（平文・ハッシュ値とも）、アクセストークン、リフレッシュトークン、署名鍵を、ログ・監査ログ・トレースの属性・OTEL のエクスポート・エラー応答に含めない（F1 後半の拡張版）
- 候補F6: NEVER セキュリティ検査（秘密情報の検出・SAST・依存関係の検査）の指摘を、理由と見直し期限を書かずに抑止・無効化しない
- 候補M4: ALWAYS 秘密情報の検出をコミット前（pre-commit フック）と CI の両方で実行する
- 候補M5: ALWAYS 依存関係は lockfile（`package-lock.json`、Java 側は採用するビルドツールの仕組み）で版を固定し、CI では lockfile どおりに入れる（`npm ci` 等）
- 候補M6: ALWAYS サブモジュールの固定先の更新は、依頼者の承認を得た専用のコミットで行い、更新前後のコミットハッシュを記録する
- 候補M7（CI を GitHub Actions に置く場合）: ALWAYS 第三者製のアクションはコミットの SHA で固定し、ワークフローの権限は必要最小限にする

### 6. 後続ステージへ引き継ぐ事項（本ステージのルールにはしない）

次は「チームの進め方」ではなく、本Intentの要件・設計として扱うべき内容である。discovered-rules.md に入れず、requirements-analysis・nfr-requirements・nfr-design で扱うよう evidence.md に記録することを提案する。

- 初期管理者（scope-document.md S3）: 既定のパスワードを持たせない。設定がない、または規定を満たさないパスワードのときは起動を失敗させる。パスワード変更は Intent H の範囲（scope-document.md X3）のため、本Intentでは初期管理者の資格情報が設定値のまま残る点をリスクとして記録する。
- パスワードの保存方式（適応型の一方向ハッシュ。bcrypt / Argon2id 等）、トークンの署名方式と鍵の保管・ローテーション、アクセストークンの有効期限（scope-definition の DECIDED により短く設定する）。
- トークンの画面側の保存場所（S7 のとおり、テーマ設定と同じ `localStorage` は避ける方向で検討する）。
- アカウントロックによるサービス妨害（第三者が他人のアカウントを意図的にロックできる）への対策と、ログイン試行の回数制限。
- STRIDE による脅威分析は、設計のステージ（nfr-design 等）で実施する。

## Positions

- AGREE: 新規プロジェクトのため discovered-rules.md の Mandated / Forbidden を `None.` とし、人間が明言した候補だけを移す方針 — ハードコードされた制約はチームの意思表示なしに確定すべきでない。
- AGREE: 候補 F3（サブモジュールの中身を直接変更しない） — 外部コードの変更経路を1つに絞れ、サプライチェーンの管理にもなる。
- AGREE: 候補 M2（認証・認可・監査の失敗系のテスト）と M3（本番配備の手動承認） — セキュリティ上重要な経路の検証と、org.md の Deployment 既定に整合する。
- AGREE: フロントエンドの lint・フォーマットを make-you-chic-ui の設定に揃える提案（P16） — 依頼者自身の既存の規約で一貫性が高い。ただし `react/no-danger` と `no-eval` 系のルールの追加を提案する。
- OBJECT: 初稿の Deployment 節・Code Style 節の CI の検査項目に、秘密情報の検出・SAST・依存関係の脆弱性検査が一切含まれていない — パスワードと署名鍵を扱う認証基盤では、lint・テストと同じく統合の条件にすべきである。
- OBJECT: 「PR を使わずローカルで統合してプッシュし、プッシュ時の CI が失敗したら統合を止める」という記述 — プッシュ契機の CI は統合を事後にしか検知できず止められないため、PR＋必須チェックか pre-push フックかを面談で決める必要がある。
- OBJECT: 候補 F1 が「ハードコード禁止」と「ログ出力禁止」を1つにまとめ、出力先をログ・監査ログに限っている — 分けたうえで、トレース属性・OTEL エクスポート・エラー応答まで対象に含めるべきである（候補 F5）。
- OBJECT: サブモジュールのサプライチェーン管理が F3 だけで、更新手順・`npm ci`・vendor の lockfile の検査が扱われていない — make-you-chic-ui には CI がなく、本リポジトリのパイプラインでしか安全性を確かめられない。
- OBJECT: `.gitignore` に `.env`・鍵ファイル等の除外がないことが初稿の論点に挙がっていない — 初期管理者のパスワードと署名鍵を設定で渡す本Intentでは、最初の Bolt で補強すべきである。
- AGREE: 初稿の I4（スコープの `change_control: strict` と状態ファイルの `relaxed (set by you)` の食い違い）を記録した点 — セキュリティ上重要な基盤であり、承認後の入力変更の扱いに影響するため、本ステージの対象外であっても依頼者に確認を促すべきである。
