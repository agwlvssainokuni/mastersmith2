# Evidence — Practices Discovery

主担当（aidlc-pipeline-deploy-agent）の初稿、支援エージェント3者（quality / developer / devsecops）の独立レビュー、依頼者との面談（Q1〜Q33）をもとに、チームの進め方を確定した根拠をまとめる。

## 前提

- プロジェクト種別: 新規（Greenfield）。アプリのコード・テスト・CI・ビルドファイルはまだない（`aidlc/spaces/default/intents/260922-auth-audit-base/aidlc-state.md`）。
- 既定値の出どころ: `aidlc/spaces/default/memory/org.md` の5節を推奨の既定値として扱い、面談で依頼者が確定した。
- `aidlc/spaces/default/memory/team.md`: 面談前は全節が空（今回が初回の確定）。
- `aidlc/spaces/default/memory/project.md`: 既存のルール（コミットの進め方、相対パス表記、`reference/` の扱い、Apache License 2.0 ヘッダー、make-you-chic-ui の採用、DECIDED 3項目）と矛盾しないようにし、重複する制約は作っていない。
- 会話言語: 日本語。

## 参加者ごとに調べたこと・推測したこと

### 主担当（aidlc-pipeline-deploy-agent）

| # | 対象 | 分かったこと |
|---|---|---|
| E1 | `git branch -avv` | 作業ブランチは `develop`（`origin/develop` を追跡）。ローカルに `main` はなく、リモートの `origin/main` は初回コミットのみ |
| E2 | `git log` | 初回コミット以降はすべて `develop` に直接積まれ、機能ブランチやマージコミットはない |
| E3 | コミットの粒度とメッセージ | 回答確定・成果物作成・承認などの区切りごとに細かくコミットし、メッセージは日本語（project.md の Change Control と一致） |
| E4 | `git remote -v` | リモートは GitHub |
| E5 | `.gitignore` | AI-DLC の既定、Node 系、エディタ系、`reference/` を除外 |
| E6 | `.gitmodules`、`git submodule status` | make-you-chic-ui を `vendor/make-you-chic-ui` にサブモジュールとして取り込み、特定コミットに固定 |
| E7 | CI・ビルド・テスト | いずれも存在しない |
| E8 | IDE の設定（Git管理外の `.idea/`） | IntelliJ IDEA の Java モジュール、JDK は Temurin 25 |
| E9 | `.claude/scopes/aidlc-auth-audit-foundation.md` | `skeleton: on`、`change_control: strict` を宣言 |
| E10 | `aidlc-state.md` | Test Strategy は `Standard`、Change Control は `relaxed (set by you)` |
| E11 | Ideation の成果物（intent-statement.md、scope-document.md、initiative-brief.md） | Java 系のバックエンド（JWT 認証、構造化ログ、分散トレース、OTEL エクスポート）と make-you-chic-ui を使う最小限の画面。依頼者1名＋AI、期限なし。「テスト・CI がないため品質を確かめる仕組みを一から作る必要がある」ことが認識済みのリスク |
| E12 | make-you-chic-ui の設定（読み取りのみ） | Prettier、oxlint＋ESLint（react-hooks）、Stylelint、Vitest＋Testing Library＋vitest-axe。カバレッジの設定なし |
| E13 | make-you-chic-ui のコミット履歴（読み取りのみ） | `main` のみで運用、日本語のコミットメッセージ、ライセンスヘッダーを `/*` 形式に統一する修正あり |

推測: 実態は「`develop` に直接コミットし、`main` は未使用」で、org.md の既定（`main` のトランク）と統合先が異なる（I1）。依頼者は細かい履歴を好む（I2）。このスコープは org.md のスコープ別テスト下限の一覧になく、明示しない限り下限がかからない（I5）。スコープの `change_control: strict` と状態ファイルの `relaxed` が食い違う（I4）。

### 品質担当（aidlc-quality-agent）

出典: `aidlc/spaces/default/intents/260922-auth-audit-base/inception/practices-discovery/contributions/aidlc-quality-agent.md`

- 調べたこと: make-you-chic-ui の Vitest 設定とセットアップ、テストの配置（`*.test.tsx` 25件＋`*.test.ts` 3件）、画面部品のテスト25件中21件で vitest-axe によるアクセシビリティ検査、fast-check による純粋関数の性質ベースのテスト、テスト名は英語・テストデータは日本語、不具合修正のコミットに再現テストを同梱している慣行、カバレッジ設定と CI がないこと。
- 推測と指摘: 依頼者の慣行は test-after。下限はスコープに依存しないチームの下限として数値で確定すべきで、分岐カバレッジの下限も必要。「統合前に CI」と「プッシュ後に CI」は両立しない。必ず書くテストの一覧を拡充し、要件が未定のもの（★）は要件定義へ申し送る。
- 推奨したが採用されなかったもの: Methodology を `custom`（重要ロジックのみ先にテスト）にする案（依頼者は test-after を選択）。

### 開発担当（aidlc-developer-agent）

出典: `aidlc/spaces/default/intents/260922-auth-audit-base/inception/practices-discovery/contributions/aidlc-developer-agent.md`

- 調べたこと: make-you-chic-ui の部品ごとのディレクトリ構成、名前付きエクスポートのみ（`export default` が0件）、`enum` 不使用、tsconfig の `strict` 系設定、素の CSS と接頭辞付きのクラス名、コメントは英語・文言とドキュメントは日本語、fail-soft のエラー処理、`docs/integration-guide.md` の `resolve.dedupe` の必要性、`.editorconfig`・`.gitattributes` がないこと。
- 推測と指摘: `backend/`・`frontend/` の分離、ビルドツールを最初の Bolt より前に決める必要、機能別パッケージと層の境界、RFC 9457 Problem Details＋`code`、Lombok 不使用、ArchUnit による境界の検査、`/*` 形式のライセンスヘッダー（JDK 23 以降の `dangling-doc-comments` 警告という技術的根拠あり）、Java のテストは `src/test/java`。
- 推奨したが採用されなかったもの: コード中のコメントを英語にする案（依頼者は日本語を選択）。

### セキュリティ担当（aidlc-devsecops-agent）

出典: `aidlc/spaces/default/intents/260922-auth-audit-base/inception/practices-discovery/contributions/aidlc-devsecops-agent.md`

- 調べたこと: 管理対象のファイルに秘密情報の検出・SAST・依存関係の検査がないこと、`.gitignore` に `.env`・鍵ファイルの除外がないこと、サブモジュールの URL と固定先、make-you-chic-ui は CI も脆弱性検査もないプロトタイプであること、その lockfile（取得元は `registry.npmjs.org` のみ）、lint 設定に `react/no-danger`・`no-eval` 系がないこと、テーマ設定の `localStorage` 保存、リポジトリの公開・非公開はリポジトリ内から判断できないこと。
- 推測と指摘: 最初の Bolt で秘密情報の検出と `.gitignore` の補強を入れるのが最も安い。make-you-chic-ui の安全性はこのリポジトリのパイプラインでしか確かめられない。プッシュ契機の CI では統合を止められない。検査の段階的な導入計画（土台／機能と並行／配備先の決定後）と、統合を止める基準（High 以上）を提案。
- 推奨したが採用されなかったもの: CodeQL、OWASP ZAP、コンテナイメージの検査（Trivy）、候補F6（検査の抑止に理由と期限を必須にする）、候補M7（GitHub Actions の第三者製アクションの SHA 固定と最小権限）。

## 面談での決定（Q1〜Q33）

出典: `aidlc/spaces/default/intents/260922-auth-audit-base/inception/practices-discovery/practices-discovery-questions.md`（依頼者が要約の確認で「Looks correct」と回答）

| 領域 | 質問 | 決定 |
|---|---|---|
| 進め方 | Q1 | 統合先は `develop`。`main` はリリース版の置き場で、リリース時に承認のうえ `develop` から取り込む |
| 進め方 | Q2 | Bolt の作業ブランチは squash マージで戻す |
| 進め方 | Q3 | プルリクエストは使わない。CI と同じ検査をローカルの1コマンドで通してから統合し（pre-push フックも可）、CI は統合後の再確認 |
| 進め方 | Q4 | `git push` は依頼者自身が行い、AI はプッシュしない |
| 進め方 | Q5 | GitHub リポジトリは公開 |
| 最初の土台 | Q6・Q30 | walking skeleton の特別な手順は設けない（最初の Bolt も通常どおり）。ただし Q7 の一式は最初の Bolt に含める |
| 最初の土台 | Q7 | 一式（起動・DB接続・ヘルスチェック、構造化ログ、フロントエンドのビルドとログイン画面の枠、1コマンドの検査とテスト、カバレッジ下限の検証、DB テスト1件以上、共通のエラー応答、CI でも同じ検査が通る） |
| テスト | Q8 | test-after |
| テスト | Q9 | 行カバレッジ 80% 以上・分岐カバレッジ 70% 以上（全Intent共通。起動クラス・設定値だけのクラス・自動生成コード・`vendor/` は除外） |
| テスト | Q10 | DB テストは本番と同じ種類の DB をコンテナで起動する |
| テスト | Q11 | E2E は「ログイン → 管理画面 → ログアウト」の1〜2本を入れる |
| テスト | Q12 | 性質ベースのテストを純粋な関数に一部適用する |
| テスト | Q13 | テスト名は英語、テストデータは日本語でよい |
| 配備 | Q14 | 当面はローカルのコンテナのみ。クラウドは後で決める |
| 配備 | Q15 | CI は GitHub Actions |
| 配備 | Q16 | 版はコミットのハッシュ＋リリース時のタグ |
| コード規約 | Q17 | `backend/` と `frontend/` に分ける。成果物はフロントエンドの `dist` を同梱した実行可能 WAR。開発時は dev server のプロキシ、成果物では同じサーバーの API を呼び、CORS 設定は置かない |
| コード規約 | Q18 | Gradle（Kotlin DSL） |
| コード規約 | Q19・Q31 | ルートパッケージは `cherry.mastersmith` |
| コード規約 | Q20 | 機能ごとに分け、機能の中を層で分ける |
| コード規約 | Q21 | palantir-java-format（インデント4、1行120文字） |
| コード規約 | Q22 | コード中のコメントも日本語 |
| コード規約 | Q23 | 開発規約一式を採用（フロントエンド設定の複製＋セキュリティ系ルール、名前付きエクスポートのみ、`enum` 不使用、Problem Details＋`code`、Lombok 不使用、層の境界の自動検査、`.editorconfig`・`.gitattributes`、`/*` 形式のヘッダーの CI 検査、`vendor/` の除外） |
| セキュリティ検査 | Q24・Q32 | 最初の Bolt で Gitleaks＋`.gitignore` の補強、SpotBugs＋FindSecBugs、依存関係の検査（Dependabot＋OSV-Scanner 等、`vendor/make-you-chic-ui` も対象）。CodeQL は採用しない |
| セキュリティ検査 | Q33 | SBOM は配備先が決まってから入れる。統合を止める基準は High 以上。OWASP ZAP・コンテナイメージの検査は採用しない |
| セキュリティ検査 | Q25 | pre-commit フックを入れる |
| 制約 | Q26 | ALWAYS として A（統合前の全検査）、B（認証・認可・監査の失敗系テスト）、C（不具合の再現テスト）を明言。D（本番配備の手動承認）は制約にしない |
| 制約 | Q27 | NEVER として A（秘密情報の直書き）、B（秘密情報のログ等への出力）、C（秘密情報ファイルのコミット）を明言。D（検査の抑止）は制約にしない |
| 制約 | Q28 | NEVER として A（サブモジュールの直接変更）、ALWAYS として B（サブモジュール更新は専用コミット）、C（lockfile による版の固定）を明言。D（テストの無効化）は制約にしない |
| 制約 | Q29 | ALWAYS として A（秘密情報の検出をコミット前と CI の両方で）を明言。B（Actions の SHA 固定・最小権限）は制約にしない |

## 解消した食い違い

- **統合前の関門**: 初稿は「統合前に CI で検査」と「プルリクエストなしでプッシュ後に CI」を同時に提案していた。プッシュ契機の CI は統合を事後に知らせるだけで止められない（品質担当・セキュリティ担当が指摘）。面談（Q3）で、ローカルの1コマンド検査を統合前の関門とし、CI は統合後の再確認とすることに決めた。
- **統合先ブランチ**: org.md の既定（`main` のトランク）と実態（`develop`）が異なり、ローカルに `main` がないため、既定のままでは worktree の作成に失敗する。面談（Q1）で `develop` を統合先とし、Construction の worktree は `develop` から作成して `develop` へ戻すことを team.md に明記した。
- **walking skeleton**: Q6（作らない）と Q7（一式を含める）の意味を Q30 で確認した。特別な手順（最初の Bolt だけ単独で承認を待つ）は設けず、一式は最初の Bolt に含める。スコープファイルの `skeleton: on` よりこの方針が優先することを team-practices.md に明記した。
- **カバレッジ下限の適用範囲**: スコープ名に結びつけると後続Intentで下限が外れる（品質担当の指摘）。スコープに依存しないチーム共通の下限として書いた。
- **ライセンスヘッダーの記法**: `/*` 形式に統一する（make-you-chic-ui の修正履歴と、JDK 23 以降の警告という開発担当の根拠による）。project.md の Mandated（ヘッダーの挿入）とは矛盾せず、記法を具体化するだけである。
- **秘密情報に関する候補の分割**: 初稿の候補F1（直書きとログ出力の禁止）を、セキュリティ担当の提案どおり「直書きの禁止」と「出力の禁止（トレース属性・外部エクスポート・エラー応答を含む）」に分けて面談で確認した。

## 後続ステージへの申し送り

本ステージのルールにはせず、要件・設計として後続ステージで扱う。

### 要件定義（requirements-analysis）で決める（品質担当 §2.5 の★）
- アカウントロックのしきい値、失敗回数を数える期間、ロックの解除方法（時間経過／管理者操作）
- アクセストークン・リフレッシュトークンの有効期限の値、リフレッシュトークンを使うたびに作り直すか
- 初期管理者の設定がないときに起動を止めるか続けるか
- 監査ログの書き込みに失敗したときに操作自体を失敗させるか

### NFR 要件・NFR 設計（nfr-requirements / nfr-design）で扱う（セキュリティ担当 §6）
- 初期管理者に既定のパスワードを持たせず、設定がない・規定を満たさないときは起動を失敗させること。パスワード変更は Intent H の範囲のため、本Intentでは初期管理者の資格情報が設定値のまま残るリスク
- パスワードの保存方式（bcrypt / Argon2id などの適応型の一方向ハッシュ）
- トークンの署名方式と鍵の保管・ローテーション、アクセストークンの有効期限を短くすること（DECIDED 済み）
- トークンの画面側の保存場所（テーマ設定と同じ `localStorage` は避ける方向で検討）
- アカウントロックによるサービス妨害（第三者による意図的なロック）への対策と、ログイン試行の回数制限
- STRIDE による脅威分析

### 設計・Construction で扱う
- E2E テストの道具（Playwright 等）、API 呼び出しの差し替え方法（MSW 等）
- 画面側の API クライアントの共通化（型付きエラーへの変換、401 時のトークン更新と再試行）とアプリ用 CSS クラスの接頭辞（開発担当 §3）
- null の扱い（JSpecify 注釈の採否）、`javac` の警告をエラー扱いにするか（開発担当 §4）
- 性能検証の道具（k6 / Gatling 等）は performance-validation ステージで決める

## まだ残っている不確かなこと

- 配備先（クラウド）は未定。決まった時点で配備の流れと SBOM の生成を追加する。
- Change Control の記録の食い違い（スコープファイルは `strict`、状態ファイルは `relaxed (set by you)`）は本ステージの5節の対象外として残っている。依頼者への確認を勧める。
- 各検査の道具の具体的な設定値（Gitleaks の許可リスト、OSV-Scanner の実行形態など）は最初の Bolt で決める。
