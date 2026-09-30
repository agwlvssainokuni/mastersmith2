# Evidence — Practices Discovery

Intent `260930-user-admin`（利用者の管理の画面: 一覧・管理者の印・利用停止・ロックの解除）の Practices Discovery（再実行）の根拠の記録。この版は主担当の統合版（Step 5）で、主担当の初稿（Step 2）、支援役3名の独立レビュー（Step 3、`contributions/`）、依頼者との面談（Step 4、`practices-discovery-questions.md`、Q1〜Q13 とまとめの確認は Looks correct）を統合した。

## 前提

- プロジェクト種別: 既存のコードあり（Brownfield）、スコープ `classic`、Depth `Standard`、Test Strategy `Standard`、Change Control `relaxed`（`aidlc/spaces/default/intents/260930-user-admin/aidlc-state.md`）。
- 再実行（Re-run）: `aidlc/spaces/default/memory/team.md` の5節を今の基準とした。前回の確定は Intent `260925-user-management` の Practices Discovery（2026-09-25、記録のコミット `8a4e199`）。その後、`team.md` は `f5fc7ed`（Intent `260929-log-deps-cleanup` の Build and Test）で `packagesJudgedByTotal` の記述だけが直された。
- 前回の確定の後に `develop` に積まれたコミットは 79 件（`git rev-list --count 8a4e199..HEAD`）。この間に Intent が3つ完了した（`260925-user-management`・`260928-quality-followup`・`260929-log-deps-cleanup`）。
- 会話言語: 日本語（依頼の指示の `Conversation language` の行）。
- 記録のコミット: `31973af`（`develop` の HEAD）。
- 全員が読み取りだけで確かめた。主担当はファイルと git の読み取り、品質担当・開発担当はファイルの読み取りと `grep`・`find`、セキュリティ担当はファイルの読み取り・`git ls-files`・`git tag` と `gh api` による GitHub の設定の読み取り。`./gradlew`・`npm`・`docker` は誰も実行しておらず、件数・時間・カバレッジの新しい実測値は無い。`.env`・`.env.targetdb`・鍵ファイル・`reference/` は開いていない。`aidlc/spaces/default/memory/` は読んだだけで変えていない。

## 主担当が調べたもの

| # | 対象 | 分かったこと（確かめた事実） |
|---|---|---|
| E1 | `git log --oneline 8a4e199..HEAD`・`git log --merges` | マージのコミットは無く、`develop` の履歴は一直線。工程の承認ごとに日本語のメッセージでコミットしている |
| E2 | Intent `260925-user-management` の B1〜B5 のコミットと `construction/*/code-generation/code-generation-plan.md` | Bolt の作業は短命のブランチ（例: `feature/260925-user-management-b4`）で行い、サブモジュールの固定先の更新を含む Bolt は fast-forward、それ以外は squash で統合した。B5（画面の3単位）は依頼者の決定（U5 の計画の決定 1）で単位ごとに squash し、`develop` に3コミット（`dc21a5a`・`08c9183`・`d1c1505`）になった |
| E3 | 同 Intent の監査（`audit/`）・`aidlc-state.md`、`git worktree list` | `WORKTREE_*`・`BOLT_*` の監査の出来事は0件、`Worktree Path` と `Bolt Refs` は空。作業ブランチは worktree の道具ではなく、同じ作業フォルダの中で手で作っている |
| E4 | `git branch -a` | 統合済みの短命のブランチ `fix/260924-followup-fixes`・`fix/260925-storage-memory-fixes` が手元に残っている |
| E5 | `git log -3 main`・`git tag`・Intent `260928-quality-followup` と `260929-log-deps-cleanup` の `operation/deployment-pipeline/` | `main` は `develop` の先頭（`d748598`）へ fast-forward で取り込まれている。タグは0件。2つの Intent で依頼者が「`main` を fast-forward で取り込み、タグは付けない」（Q1: A）を選んだ（`v*` のタグのプッシュで CI が公開の GitHub のリリースを作り WAR を添付するため） |
| E6 | `git ls-remote --symref origin HEAD` | GitHub の既定のブランチは今も `develop` |
| E7 | `.github/workflows/ci.yml` | きっかけは `develop` へのプッシュ・`v*` のタグ・手動。`./gradlew verify` を流し、WAR を `mastersmith-<sha>` の名前で 30 日保存し、タグのときはリリースに添付する。前回の確定の後の変更は無い |
| E8 | `.github/dependabot.yml` の差 | gradle の `exclude-paths: vendor/**`、`ignore`（opentelemetry-logback-appender・networknt json-schema-validator・Jackson の BOM のすべての版、typescript の大きな版、eclipse-temurin の大きな版）、`docker-compose` の対象が足された。どの `ignore` にも見送る理由と外す時期がコメントで書かれている |
| E9 | `project.md` の学び（`260925-user-management:ci-pipeline`） | GitHub の Dependabot alerts は無効のままで、脆弱性の関門は OSV-Scanner だけ、と依頼者が決めた（Q2: B）。セキュリティ担当の S2 で今も無効と確かめた |
| E10 | `project.md` の学び（`260925-user-management:build-and-test`・`260929-log-deps-cleanup:build-and-test`） | CI の失敗の扱いが2通りあった（依頼者の決定で次の Intent へ持ち越し／手元で再現しない失敗を CI の再実行で通して進めた） |
| E11 | `backend/build.gradle.kts` の `packagesJudgedByTotal` | 12 個で、`team.md` の記述と一致 |
| E12 | `backend/config/spotbugs-exclude.xml` | 既存の `PREDICTABLE_RANDOM` の1件（`LoginAttemptStateRepository.lockDummyForUpdate`）は、Intent `260925-user-management` の B1 で誤検知と確かめ、理由を書いて外してある |
| E13 | `settings.gradle.kts`・`backend/gradle.lockfile`・Intent `260925-user-management` の `construction/u1-mail/code-generation/code-summary.md` | java-mustache-processor の推移依存 `slf4j-api` が lockfile に載り OSV-Scanner の対象になることを U1 で確かめた（部品そのものは lockfile に載らない） |
| E14 | ルートの `build.gradle.kts` の差 | `verify` の準備に `mustacheVendorUnchanged` が足された。ライセンスヘッダーの検査にメールのテンプレートが加わった。`e2eTest` は始める前に Mailpit に届くかを確かめる |
| E15 | `frontend/e2e/`・`README.md` の E2E の節 | E2E は 10 ファイル。代表の流れ `090-invitation-registration-flow` と、実際のブラウザの検査（050〜080 のアクセシビリティ、100 の文字のコントラスト）が足された |
| E16 | `.gitignore`・`.idea/`（`55377dd`） | IntelliJ IDEA のプロジェクトの設定を Git で管理するようになった |
| E17 | `Dockerfile` の差 | `-Dh2.compactThreads=1` が足された |
| E18 | コード知識ベース `aidlc/spaces/default/codekb/mastersmith2/`（記録のコミット `31b980b`） | 利用者の管理の4つの操作は、どの層にも無い。今回の Intent に関わる所見は K-1〜K-9 |

### 今回の Intent に関わる所見（コード知識ベースから、進め方に効くものだけ）

- K-1: 利用停止を表す状態が無い。止めるには認証の3つの入口（ログインの照合・トークンの更新・アクセストークンの認証）で状態を見る必要がある。
- K-2: ロックの状態は `auth` の表にあり、ロック中かは時刻の比べで決まる。行の無い利用者がありうる。時刻は `auth/testsupport/MutableClock` で動かせる。
- K-3: 境界の決まりで `user` は `auth` を知らない。利用者とロックの状態を合わせる処理は `user` の外になる。
- K-4: 管理者の印は要求ごとに DB から読まれるが、画面は古い値を持ち続ける。最後の管理者を守る仕組みが無い。
- K-6: 監査の列（操作した人・対象の利用者）は足りているが、管理の操作の種類と出来事の型は無い。
- K-7: 手を入れると下限を満たす作業が付くパッケージがある（`auth.repository` は一覧を作った時点で単独の分岐 50.0%）。
- K-8: 一覧の型と応答は、`TraceAspect` の TRACE のログとメールアドレスの決まり（`project.md` の Forbidden）に当たる。

## 支援役が調べたもの・推したもの

### 品質担当（`contributions/aidlc-quality-agent.md`）

- Testing Posture の決まりを設定と照らし、下限（JaCoCo の全体とパッケージごと、Vitest の `thresholds`）・計測の除外・`packagesJudgedByTotal` の 12 個・`*Test`／`*IT` の分け方・E2E の置き場・乱数の種・注入可能な時計（T1〜T8）がすべて文言どおりと確かめた。
- 一覧から外す理由に「説明文の書き直し」もあった（`build.gradle.kts` のコメント、U8 で `common.security`・`config`・`access.web`）。→ Q3 の根拠。
- E2E は1つの WAR・内部DB・初期管理者を全ファイルで共有し、`workers: 1`・`retries: 0` で順に流す。`020-auth.e2e.ts` は初期管理者でわざと誤ったパスワードを送る。→ Q4 の根拠。
- 必須テストの一覧に足す7項目（利用停止・管理者の印の変更・ロックの解除・最後の管理者の保護・管理の API の認可・監査・漏えい）を具体にした（2.1）。→ Q1。
- 足りない論点として、不安定なテストと CI の失敗の整理（Q-1 → Q6）、画面のテストの非同期の待ち方（Q-5 → Q7 A）、テストデータの予約ドメイン（Q-6 → Q7 B）を挙げた。

### 開発担当（`contributions/aidlc-developer-agent.md`）

- 構造の検査（`ArchitectureTest` と機能ごとの境界テスト9本）、エラー処理（`ProblemType`・`ProblemTypeRegistry`・`GlobalExceptionHandler`・`XxxProblemTypes`・`XxxProblemTypeCatalog`・`InvitationAdminController`）、`TraceAspect` と個人に関する値を持つ record の `toString`、`*SecretLeakIT`、認可の差し込み口、画面の ESLint・oxlint の設定と `src/features/` の構成を読んだ。
- 今のコードで守られているが `team.md` に無い形 C1〜C7 を挙げた（エラーの code の置き場、結果の型と controller の変換、record の `toString` と `XxxSecretLeakIT`、機能ごとの境界テスト、`testsupport`・移行ファイルの名前、ESLint の受け持ち、`src/features/` の置き方）。→ Q8。C5 の移行ファイルの名前は Q8 の7項目に入れず、PD-6 として設計に回した。
- 足りない論点 PD-1〜PD-7 を挙げた（下の「要件・設計の段に回す論点」）。PD-3（「手を入れる」の範囲）は Q3 に合わせて問うた。

### セキュリティ担当（`contributions/aidlc-devsecops-agent.md`）

- `gh api` で GitHub の設定を読んだ: secret scanning と push protection は有効（S1）、Dependabot alerts は無効（S2、初稿の「残る不確かさ」を事実にした）、`develop`・`main` にブランチの保護が無く、ルールセット `restrict-force-push-and-delete` は無効で対象も空（S3）。
- `ci.yml` の固定（Actions はハッシュ、Gitleaks・OSV-Scanner は版と SHA-256、`permissions: contents: read`、`release` だけが `contents: write`）（S4）、SpotBugs の関門（S5）、`osvScan` の対象と基準（S6）、oxlint・ESLint のセキュリティのルール（S11）が `team.md` と一致することを確かめた。
- 悪意のあるパッケージ（`MAL-`）の判定は `verify` の段 8 で、段 0 の `npm ci` と段 5〜6 のテストでそのパッケージを動かした後になる（S7）。→ Q12 C。
- Gradle のプラグイン（Spring Boot・Spotless・SpotBugs、java-mustache-processor のプラグイン）は lockfile にも OSV-Scanner にも載らない（S8）。→ 面談では問わず、下の「残る不確かさ」に置いた。
- `.gitleaks.toml` の除外は1件で、パスと値の形の両方で絞り理由を書いている（S9）。→ Q13 A。
- `.idea/.gitignore` が `dataSources.xml` を外しておらず、対象DB の接続先とユーザー名が公開のリポジトリに入りうる（S10）。→ Q13 B。
- 管理の API を `/api/admin/**` の下に置けば既存の 401／403 の判定と CSRF の前提を使える（S12）。`TraceAspect` と一覧の record のメールアドレス（S13）。
- P1 に足す点（Q-S1: 停止・印を外した後のアクセストークン、要求の改ざん、一覧の漏えい、監査の漏えい）と、P2 の直し（Q-S2: 成功の応答を含めて広げる、管理の操作の監査の候補）を挙げた。→ Q1・Q2。

## 面談の決定（Q1〜Q13）

| # | 論点 | 答え | 反映先 |
|---|---|---|---|
| Q1 | 認証・認可・監査の必須テストの一覧に利用者の管理の項目を足すか | A（すべて足す） | `team-practices.md` Testing Posture の一覧に8項目（利用停止・管理者の印の変更・ロックの解除・最後の管理者の保護・管理の API の認可・要求の改ざん・管理の操作の監査・利用者の管理の漏えい）。★の値は要件定義で決める |
| Q2 | 新しい固い制約 | A・B・C・D | `discovered-rules.md`（Mandated に B・C、Forbidden に A・D） |
| Q3 | `packagesJudgedByTotal` の「手を入れる」の範囲 | A（本体のソースの変更のすべて、説明文だけを含む。テストだけは含めない。見込みは計画で実測して見積もる） | Testing Posture の `packagesJudgedByTotal` の行 |
| Q4 | E2E で状態を変える操作 | A（自分で作った利用者だけ、初期管理者は変えない） | Testing Posture の E2E の行。今回の流れの中身は設計で決める |
| Q5 | 実際のブラウザのアクセシビリティの検査を定番にするか | B（決まりにせず Intent ごとに設計で決める） | `team.md` には入れない |
| Q6 | 不安定なテストと CI の失敗の扱い | A（1つの決まりにまとめる） | Testing Posture に新しい決まり（4つの枝）。時計の行から「不安定なテストは…」を外して移した。Way of Working の CI の行はこの決まりを指す形に直した |
| Q7 | テストの書き方の決まり | A・B | Testing Posture に `waitFor` と時間の上限の行、テストデータの予約ドメインの行 |
| Q8 | 今のコードの形7つを Code Style に書き足すか | A | Code Style のバックエンド（code の置き場・結果の型・record の `toString` と `XxxSecretLeakIT`・境界テスト・`testsupport`）とフロントエンド（ESLint・oxlint の受け持ち、`src/features/`） |
| Q9 | 複数の単位を持つ Bolt の統合の単位 | A（原則 1 Bolt 1 コミット、計画の承認で単位ごとの squash 可） | Way of Working の squash の行 |
| Q10 | 作業ブランチの作り方と後片付け | A（同じ作業フォルダの中の短命のブランチ、統合後に消す。残っている2本も消す） | Way of Working の統合先の行。2本の削除は下の「統合後に行う作業」 |
| Q11 | `main` への取り込みとタグ | A（fast-forward、配備先が決まるまで `v*` のタグを付けない） | Way of Working の `main` の行、Deployment の版の行（「リリース時に `main` にタグを付ける」を置き換えた） |
| Q12 | 依存の更新と脆弱性の知らせの受け方 | A・B・C | Way of Working の OSV-Scanner の行（A）、Code Style の java-mustache-processor の行（B）、Code Style の npm の `ignore-scripts` の行（C、実物は今回の Bolt で入れる） |
| Q13 | リポジトリと GitHub の守り | A・B | Code Style の静的解析に secret scanning・push protection と Gitleaks の除外の行（A）。`.idea/.gitignore` の直しは今回の Bolt で行う（B）。ルールセットの有効化（C）は選ばれず、`team.md` に入れない |

## team.md から変わる行と実態のずれの扱い

初稿の直し D1〜D5 は、答えで確かめたうえで取り込んだ。

| # | 節 | `team.md` の今の文言 | 実態（根拠） | 扱い |
|---|---|---|---|---|
| D1 | Way of Working | 「`develop` から `main` へ取り込む」 | fast-forward で取り込んでいる（E5） | Q11 A で確かめ、「fast-forward で取り込む」と明記 |
| D2 | Way of Working | 「重大度 High 以上の知らせは、次の Bolt に入る前に取り込む」だけ | Dependabot alerts は無効、関門は OSV-Scanner（E8・E9・S2） | Q12 A で確かめ、「関門は OSV-Scanner（`verify` と CI のときだけ働く）」「`ignore` には理由と外す時期を書く」の行を足した（セキュリティ担当の X3 を含む） |
| D3 | Deployment | 「リリース時に `main` にタグを付ける」 | 2回のリリースともタグを付けていない（E5） | Q11 A で確かめ、「配備先が決まるまで `v*` のタグを付けない」に置き換え |
| D4 | Code Style（静的解析） | `PREDICTABLE_RANDOM` の1件を「関門に入れる Bolt で確かめる」（未来の形） | 確かめて除外済み（E12・S5） | 実態の追認として済んだ形に書き直し（中身は変えていない） |
| D5 | Code Style（静的解析） | java-mustache-processor の推移依存が「載るかを確かめる。載らなければ…」（条件の形） | 載ることを確かめた（E13） | 確かめた結果に書き直し、Q12 B で「固定先を更新して推移依存が変わるときは確かめ直す」を足した |

面談で直したそのほかのずれ:

- 「Bolt ごとの作業ブランチ（worktree）」→ 同じ作業フォルダの中の短命のブランチ、統合後に消す（Q10 A、E3・E4）。
- 「1 Bolt が `develop` の1コミット」→ 原則のまま、計画の承認で単位ごとの squash を選べる（Q9 A、E2）。
- 「CI が失敗したら、次の Bolt に進む前に原因を直す」と「不安定なテストは原因を直すまで統合しない」→ Testing Posture の1つの決まりにまとめ、CI の行はそれを指す（Q6 A、E10）。これで `project.md` の学び（再現できなければ不安定と確かめられていない扱い、F3）と前例（持ち越し・再実行）は、決まりの枝として説明がつく。
- ESLint の受け持ちの文言（`export default`・`enum` の禁止、`no-implied-eval`）と oxlint のセキュリティのルール（Q8 A、C6・S11）。

Walking Skeleton の節は変えていない（骨格は既にあり、一式は `verify`・CI・E2E で維持されている）。Methodology（test-after）と Ordering も変えていない。

`team.md` に入れなかったもの（面談の答えによる）: 実際のブラウザのアクセシビリティの検査の定番化（Q5 B）、ルールセットの有効化（Q13 C は選ばれていない）、移行ファイルの名前（PD-6、Q8 の7項目の外）。

## 要件・設計の段に回す論点

### ★の値（Testing Posture の必須テストで要件定義に回したもの）

- 停止を応答から推測できてよいか、停止の前に出したリフレッシュトークン・アクセストークンの扱い（K-1、セキュリティ担当 Q-S1 (a)。`project.md` の DECIDED「アクセストークンの失効の仕組みは持たない」との関係）。
- 印を外す前に出したトークンの扱い、自分の印を外す操作の扱い（K-4）。
- 解除の後の失敗回数の扱い、ロックの状態の行が無いときの応答（K-2）。
- 最後の管理者の数え方（止めた管理者を数えるか）。
- 拒否した管理の操作（403・最後の管理者の拒否など）を監査に残すか（Q2 C の括弧書きと同じ）。

### 開発担当の PD-1〜PD-7

- PD-1: 利用者とロックの状態を合わせる処理の置き場。境界テストを緩めない (a) 新しい機能のパッケージ・(b) 既存の `access` を先に検討し、(c) 境界テストを緩めて `user` に置くときだけ計画で承認を得る（`team.md` の ArchUnit の行のとおり）。どれにするかは Domain Design で決める。
- PD-2: 一覧の型すべてに record の `toString` の伏せ字を当て、`XxxSecretLeakIT` で TRACE を有効にして一覧を読んでもメールアドレス・氏名がログに出ないことを確かめる（Q1・Q8 で決まりになった。設計では型の形を決める）。画面へメールアドレスを返すのは管理の目的の範囲で、`project.md` の Forbidden（アプリのログとエラー応答）には当たらない、という読み方を要件で確かめる。
- PD-3: 「手を入れる」の範囲（Q3 A で決まった）。Delivery Planning とコード生成の計画で、手を入れる一覧のパッケージと今の値を実測して見積もる。カバレッジの作業を避けるために設計を曲げない（品質担当 2.3）。
- PD-4: 新しい管理の API は `/api/admin/` の下に置き、`SecurityRuleContributor` を足さない（足すときは機能の名前で次の 100 台を割り当てる、`project.md` の学び）。
- PD-5: 画面の機能どうしの import を道具（ESLint の `no-restricted-imports`）で止めるか。決まりの文は Q8 A で入った。一覧のページ送り（`features/invitation/paging.ts`）を使うなら `src/shared/` へ移す。
- PD-6: DB の移行ファイルの名前を、単位の番号ではなく機能の名前（例: `V9__<機能>_<内容>.sql`）にするか。既存のファイルは名前を変えない。
- PD-7: 同時の操作の守りは、既存の `PESSIMISTIC_WRITE`・待ちの上限 3 秒にそろえることを既定とし、違う形にするときだけ設計で理由を書くか。守り方そのもの（行の排他か条件つきの更新か）は設計で決める。

### セキュリティ担当の要件・設計へ回す点

- 認証の3つの入口で状態を毎回 DB から読むか、リフレッシュトークンをまとめて無効にする問い合わせを足すか。
- 管理の操作の API は操作ごとに分けた要求の `record`（変える項目だけを持つ）にし、利用者の行を丸ごと受け取らない。`/api/admin/**` の下に置く。
- 一覧の応答の型: メールアドレス・氏名を、TRACE で文字列にしても値が出ない型（`EmailAddress`・`DisplayName`）で service の層を通すか、`TraceAspect` の対象の外に置くか。
- 自分自身を止める・自分の印を外す操作の扱いと、最後の管理者の守り方の同時の操作での確かめ。
- 管理の操作の回数の制限の要否。
- 配備の後の確かめで管理の一覧の画面を開くと実在の利用者の値が画面に出る。公開のリポジトリに置く記録には件数と有無だけを残す。

### 品質担当の 4節

- 停止・印の変更・解除の後に、画面が古い状態（管理者の印・一覧の行）を持ち続けたときの見せ方と、その画面のテスト（403 を受けたときの画面の動き）。
- 一覧のページ送りと並びの境界（0件・1ページちょうど・最後のページ）のテストの範囲（前例は `InvitationPagingTest`）。
- 性能の目標（一覧の応答時間など）を置くか。測れない値は Build and Test で `Unverified` にし持ち主の段を書く（`project.md` の学び）。
- 最後の管理者の判定が純粋な関数として切り出せるなら、性質ベースのテストの当て先にする（既存の決まりの当て先の確認）。

### 主担当の初稿から引き継ぐ点

- 今回の E2E の代表の流れの中身（例: 別の利用者を止める → ログインできない → 止めを解く → ログインできる）。前提の利用者は流れの中で作る（Q4 A）。
- 一覧の項目・並び・絞り込み・ページ送り（`invitation` の管理の API が前例、K-5）。
- 監査の出来事の種類と、`audit` の3ファイルの肥大を今回どこまで受け入れるか（K-6）。
- 確認の Modal の要否（前例は DSL の適用・破棄の Modal）。
- 実際のブラウザのアクセシビリティの検査を今回の画面に当てるか（Q5 B で Intent ごとに設計で決める）。
- 要求の文脈の読み取り（`MeRequestContextResolver` など）を `common` に寄せるか、画面の側の code の一覧をバックエンドと自動で突き合わせるか（開発担当の「気づいた点」）。

## 今回の Intent の Bolt で行う作業

- Q12 C: `frontend/.npmrc` に `ignore-scripts=true` を入れる。今の依存で install のスクリプトを持つのは `fsevents`（開発時・任意）だけ（S7）。入れた後に `./gradlew verify` が通ることを確かめる。`vendor/make-you-chic-ui` は変更できないため、そちらの install の扱いが要るかはその Bolt の計画で確かめる。
- Q13 B: `.idea/.gitignore` に `dataSources.xml`・`dataSources/` を足す（S10）。

どの Bolt に入れるかは Delivery Planning・コード生成の計画で決める。

## 統合後に行う作業

- Q10 A: 統合済みの `fix/260924-followup-fixes`・`fix/260925-storage-memory-fixes` を手元から消す。消す前に依頼者の承認を得て行う（この段では消していない）。以後、統合を終えた作業ブランチは統合の後に消す。

## 支援役の異論とその結末

| 支援役 | 異論 | 結末 |
|---|---|---|
| 開発担当 | P2 の「NEVER 最後の有効な管理者を無くす操作を受け付けない」は要件（FR）で、固い制約にしなくてよい | 依頼者は Q2 で A を選び、固い制約として `discovered-rules.md` の Forbidden に入れた。要件の FR と受け入れ基準にも書き、必須テスト（最後の管理者の保護）で守る |
| 開発担当 | P2 の「一覧・詳細の応答にパスワードのハッシュを含めない」は既存の Forbidden の直しで足りる | セキュリティ担当の Q-S2 の形（成功の応答を含む API の応答、招待のトークンのハッシュ値・ロックの内部の値に広げる）で Q2 D として問い、依頼者が選んだ。既存の行は直さず、新しい1行として足した |
| 品質担当 | P4 を「流れの選び方は設計に回してよいか」だけで問うこと | Q4 として「自分で作った利用者だけを操作し、初期管理者は変えない」の決まりを問い、A になった |
| 品質担当 | P10 を CI の失敗の扱いだけで問うこと | Q6 で不安定なテストと CI の失敗を1つの決まり（二度目と時間の上限を含む）として問い、A になった |
| 品質担当 | 画面のテストの非同期の待ち方が論点に無い | Q7 A として問い、決まりに入った |
| 品質担当・開発担当 | P5（実際のブラウザの検査を定番にする）に賛成 | 依頼者は Q5 で B（決まりにしない）を選んだ |
| セキュリティ担当 | Code Style に secret scanning・push protection と Gitleaks の除外の決まりが無い | Q13 A で入った |
| セキュリティ担当 | `.idea/` を面談で問わない扱い | Q13 B として問い、今回の Bolt で `.idea/.gitignore` を直すことになった |
| セキュリティ担当 | 悪意のあるパッケージの判定の順序が論点に無い | Q12 C として問い、`ignore-scripts` を入れることになった。`osvScan` を `npm ci` の前に動かす案（Q-S3 の B）は問うていない |
| セキュリティ担当 | 初稿の「残る不確かさ」の Dependabot alerts の点 | S2 で事実として確かめられたため、不確かさから外した（E9） |

## 残る不確かさ

- 作業ブランチを worktree の道具で作らないことが、AI-DLC の Bolt の境界の記録（`BOLT_*`・`WORKTREE_*` の出来事）に何を欠かせるかは確かめていない（E3）。Q10 A で今の形を決まりにした。
- `auth.repository` などの今のパッケージごとのカバレッジは測っていない（K-7。値は一覧を作った時点のもの）。Q3 A のとおり計画で実測する。
- Gradle のプラグイン（Spring Boot のプラグインを含む）が lockfile にも OSV-Scanner にも載っていないこと（セキュリティ担当 S8・Q-S5）と、OSV の見送りの書き方（Q-S6）、ルールセットの有効化（Q-S4、Q13 C は選ばれず）は面談で決めていない。既知の範囲の外として残る。
- `ignore-scripts=true` を入れたときに、`vendor/make-you-chic-ui` の install（`verify` の段 0）やビルドに影響が出るかは実行していない（Bolt で確かめる）。
- 開発担当の C4 の「既存の境界テストは変えない」の書き方は、`dsl`・`dslmanage`・`targetdb`・`mail` の境界テストの中身を読まずに見立てた。
- `TraceAspect` の対象の式が record の値を引数・戻り値のときに文字列にするという読みは、`TraceAspectIT` を読まず実行もしていない（開発担当・セキュリティ担当の見立て）。
