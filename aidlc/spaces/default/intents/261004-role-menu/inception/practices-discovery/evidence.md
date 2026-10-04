# 証拠と推定（Intent 261004-role-menu）

> Practices Discovery の再実行の記録。リード（aidlc-pipeline-deploy-agent）の下書きに、支援役3名（品質・開発・セキュリティ）の独立した確かめと、依頼者の面談の答え（`practices-discovery-questions.md`、まとめの確認は Looks correct）を取り込んだ。基準は `aidlc/spaces/default/memory/team.md` の 5 つの節（前の Intent までに承認済み）。今回の Intent は F（ロールベースの権限の管理）と I（メニュー・ナビゲーション（N 階層））を束ねたもの。

## 1. リードが調べたもの

| 種類 | 調べたもの | 読み方 |
|---|---|---|
| 段の定義 | `.claude/aidlc-common/stages/inception/practices-discovery.md` | 全文 |
| 決まり | ルールの束（`org.md`・`team.md`・`project.md`・`phases/inception.md`） | 全文 |
| 状態 | `aidlc/spaces/default/intents/261004-role-menu/aidlc-state.md` | 先頭（Brownfield・classic・深さ Standard・Test Strategy Standard・Change Control relaxed） |
| コード知識ベース | `aidlc/spaces/default/codekb/mastersmith2/` の `business-overview.md`・`code-quality-assessment.md`・`technology-stack.md`・`dependencies.md` は全文、`code-structure.md`・`component-inventory.md` は該当箇所の検索 | 所見 K-32〜K-38 |
| CI | `.github/workflows/ci.yml` | 全文 |
| 依存の更新の知らせ | `.github/dependabot.yml` | 全文 |
| コミットの直前の検査 | `.pre-commit-config.yaml` | 全文（Gitleaks・Spotless・Prettier） |
| カバレッジ | `backend/build.gradle.kts`（`packagesJudgedByTotal` の 215〜233 行）、`frontend/vitest.config.ts`（`thresholds`） | 該当箇所 |
| ログの設定 | `backend/src/main/resources/application.yaml` の 354〜363 行（Tomcat のロガー） | 該当箇所 |
| DB の移行 | `backend/src/main/resources/db/migration/`（V1〜V9）、`V2__u2_user_account.sql` の `admin_flag` | ファイルの一覧と検索 |
| E2E | `frontend/e2e/`（010〜130 の 13 本） | ファイルの一覧 |
| git の履歴（読み取りだけ） | `git log`（直近 40 件）、`git rev-list --count main..develop`、`git log --merges`、`git branch -a`、`vendor/make-you-chic-ui` の直近のコミット | 読み取りだけ |

gradlew・docker・npm は実行していない。`.env` などの秘密情報は開いていない。

## 2. 基準（team.md）と今のコード・設定の突き合わせ（リード）

| 項目 | 基準の記述 | 今のコード・設定 | 判定 |
|---|---|---|---|
| 統合の形 | `develop` へ squash（サブモジュールの固定先の更新を含むときは fast-forward でよい） | 直近の `b084c07`（safety-carryover）は1コミット。user-admin-followup は `a272fd3`（make-you-chic-ui の固定先の更新）を含み、個別のコミットのまま並ぶ（fast-forward の例外どおり）。マージのコミットは無い | 一致 |
| リリース | `develop` から `main` へ fast-forward、`v*` のタグは付けない | `main` は `d5aea52`（前の Intent の完了）で、`develop` より1つ後ろ（この Intent の Reverse Engineering の承認だけ）。CI は `tags: ["v*"]` で公開のリリースを作る仕組みを持つが、タグは付けていない | 一致 |
| CI | `develop` へのプッシュで1コマンドの検査と同じ検査 | `ci.yml` は `push: develop`・`tags: v*`・手動で `./gradlew verify`。Actions はコミットのハッシュで固定、Gitleaks・OSV-Scanner は版と SHA-256 で固定 | 一致 |
| コミット前の検査 | Gitleaks とフォーマット検査を pre-commit で | `.pre-commit-config.yaml` に Gitleaks・Spotless・Prettier | 一致 |
| Dependabot の見送り | 見送る理由と外す時期をコメントに書く | `dependabot.yml` の `ignore` 5件（opentelemetry-logback-appender・networknt・Jackson の BOM・typescript の大きな版・eclipse-temurin の大きな版）すべてに理由と外す時期がある | 一致 |
| 全体の合計で判定するパッケージ | 2026-10-04 の時点で 7 パッケージ | `packagesJudgedByTotal` は同じ 7 個（`access.service` を含む） | 一致 |
| 画面のカバレッジ | 行 80%・分岐 70% | `vitest.config.ts` の `thresholds` は lines 80・branches 70 | 一致 |
| 例外のログは境界で1回 | Code Style | `application.yaml` で Tomcat の `dispatcherServlet` のロガーを OFF にし、フィルターの中の例外の ERROR の二重の出力を止めた（前の Intent の D3: A） | 基準に沿う設定。基準の書き換えは要らない（前の Intent の学びは `project.md` にある） |
| 初期管理者の救済 | Testing Posture の「初期管理者の自動作成」の必須のテスト | 前の Intent で救済（起動のたびに停止・印なし・パスワードの不一致を戻す）と監査の種類 `INITIAL_ADMIN_RESCUED` が入った | 必須のテストの枠の中。配備の手順は `project.md` の Deployment の学びにある。基準の書き換えは要らない |
| イメージの固定 | 基準に記述が無い | 前の Intent（FR7.2・FR7.3、D4: A）で、Dockerfile・compose.yaml のイメージを「版のタグ@sha256:複数アーキテクチャの index のダイジェスト」で固定し、Dependabot が見ない固定先を手で揃える決まりを `dependabot.yml` のコメントに書いた | 基準に無い運用（P9） |
| 画面の機能の分離 | 機能どうしは直接 import し合わない（Code Style） | ESLint・oxlint に import の制限が無く、本番のコードに違反が1件（`features/registration/useRegistration.ts` が `../auth/authSession` の `logout` を読む）。開発担当の確かめで分かった | 決まりはあるが強制が無い（D1 → Q7） |
| npm のスクリプト | npm でパッケージを入れるときはスクリプトを動かさない（Code Style、`frontend/.npmrc`） | ルートの `build.gradle.kts` の `vendorInstall` は `vendor/make-you-chic-ui` で `npm ci` を `--ignore-scripts` なしで実行する。セキュリティ担当の確かめで分かった | 文言と食い違って読める（G1 → Q11・F1） |
| 機能の境界テスト | 新しく作る機能は `<機能>BoundaryArchitectureTest` を置く | 境界テストは 10 個で、`access` には無い（決まりより前に作られた。K-38） | 基準の範囲外の既存の欠け（P6） |
| 認可の必須のテスト | 「未認証（401）・管理者フラグなし（403）・管理者（200）」 | 権限は `users.admin_flag` の真偽値1つ（K-32）、URL の決まりは `/api/admin/**` だけ（K-33） | 今は一致。今回の Intent で権限の形が変わると、文言が前提を失う（P1・P2） |
| E2E の扱い | Intent ごとに代表の流れを1本まで、初期管理者の状態は変えない | 13 本。管理者・一般の利用者の2種類の前提（`030`・`110`・`130`） | 今は一致。F と I を束ねた Intent での本数と、役割を変える操作の扱いが未定（P3） |
| make-you-chic-ui | 中身を直接変えない（Forbidden）、足りない機能は上流に諮る（`project.md` の学び） | `AppShell` の `Sidebar` は平ら、子の項目・開閉・`aria-current` が無く、`aria-label` は日本語の固定、`Sidebar` は外へ出していない（K-36）。固定先は `e82b651` | N 階層の前提を満たさない（P5） |
| DB の後方互換 | スキーマの変更は前進のみ、1つ前の版のアプリが動く後方互換を保つ（Deployment） | 移行は V1〜V9。`admin_flag` は `V2` で `NOT NULL` | 今回 `admin_flag` を役割に置き換えると、戻しの前提と食い違いうる（P7） |

下書きの時点では食い違いは見つからなかったとしたが、支援役の確かめで2点（画面の機能の分離の強制が無いこと、vendor の `npm ci` でスクリプトが動くこと）が分かり、上の表に足した。ほかは今の team.md の記述と今のコード・設定が合っている。

## 3. リードが推したこと（推定）

- 進め方の大枠（依頼者1名と AI、`develop` への短命のブランチ、squash、プルリクエストを使わず `./gradlew verify` を関門にする、push は依頼者、配備先は手元のコンテナ）は、git の履歴・CI・pre-commit・Dependabot の設定のどれからも変わっていない（推定。根拠は 2節の表）。
- Walking Skeleton は骨格が既にあるため、今回も通常どおり進める形で当てはまる（基準の最後の行）。
- 今回の Intent は権限の形（真偽値1つ）と画面のナビゲーション（平らな一覧）の両方を作り替えるため、既存の必須のテスト（認可・管理者の印の変更・最後の管理者の保護・E2E の前提）の文言が「管理者の印」に寄りかかっている点が、進め方の上の主な論点になる（推定。根拠は K-32〜K-35 と team.md の Testing Posture の文言）。

表と本文の P1〜P9 は、リードの下書きで挙げた面談の候補の番号である。面談では P1→Q1、P2→Q2、P3→Q3、P4→Q4、P5→Q5、P6→Q6、P7→Q9、P8→Q10、P9→Q12 として尋ねた。

## 4. 支援役が調べた・推したこと

### 4.1 品質担当（`contributions/aidlc-quality-agent.md`）

- 調べたもの: `team.md`・`project.md`、コード知識ベースの `code-quality-assessment.md`・`architecture.md`・`api-documentation.md`、`backend/build.gradle.kts` のカバレッジの節、`frontend/vitest.config.ts`・`frontend/playwright.config.ts`・`frontend/package.json`、`frontend/e2e/` と `backend/src/test/java/cherry/mastersmith/access/` の一覧。gradlew・docker・npm は実行していない。
- 結果: カバレッジの rule・`packagesJudgedByTotal`（7 個）・計測の除外・画面の `thresholds`・E2E の設定（`workers: 1`・`retries: 0`）・性質ベースのテストの道具が基準と一致。Methodology（test-after）と Ordering を変える根拠は無い。
- 推したこと: 2 値の権限のままでは、役割ごとの 403 の誤りと新しい API の認可の書き忘れを見逃す。

### 4.2 開発担当（`contributions/aidlc-developer-agent.md`）

- 調べたもの: K-32〜K-38、ArchUnit のテスト（境界テスト 10 個と全体の `ArchitectureTest`）、画面の骨組み（`frontend/src/app/registry/types.ts`・`navigationItems.ts`・`decideRoute.ts`）、make-you-chic-ui の `AppShell.tsx`・`Sidebar.tsx`、`audit/domain/AuditEvent`・`V4__u4_audit_event.sql`、`access/domain/AccessProblemTypes`。
- 結果: `access` に境界テストが無い。`AppShell` にサイドバーを差し替える口が無く、`Sidebar` は外へ出していない（自前の案は DECIDED「ログイン後の画面は AppShell の中」と食い違う）。画面の機能の分離は強制が無く違反が1件。`AccessLevel`・`VisibleWhen`・`LoginState.admin` は「書き換えない」と注記された差し込み口の型で、今回必ず変わる。`ACCESS_DENIED` は管理者に限らない名前で使い回せる見込み。
- 推したこと: 作り替えの主な危険は「管理者」を前提にした型と名前が骨組みと内部DB の両方にあること。

### 4.3 セキュリティ担当（`contributions/aidlc-devsecops-agent.md`）

- 調べたもの: SpotBugs の関門と除外、Gitleaks（pre-commit・`gitleaksScan`・CI）、`osvScan`・`dependabot.yml`、CI の Actions の固定と `permissions`、画面のリンタと `frontend/.npmrc`、`SecurityConfig`・`ApiDefaultAccess`・`AdminAuthorizationManager`・`AdminSecurityContributor`・`AdminPaths`・`PublicApiTestRules`、`dsl-schema-v1.json` の `menuItem`。
- 結果（F1〜F5）: `/api/**` の既定は「ログインだけ」で、権限の決まりを書き忘れるとログイン中の誰でも呼べる。管理者の判定は要求ごとに内部DB から作る主体だけを見る（トークン・画面の値は使わない）。画面の出し分けは `CurrentUserResponse` の `admin` の真偽値。メニューの定義は DSL（信頼できない入力）にあり、`icon` は許可の一覧と照らしていない。`PublicApiTestRules` は order 250。
- 推したこと: `vendorInstall` の `npm ci` で make-you-chic-ui の依存のスクリプトが動く（G1）。

## 5. 支援役の追加の候補とその扱い

| 出した人 | 候補 | 扱い |
|---|---|---|
| 品質 | Q-1 認可の網羅の検査（すべての API を数え上げ、表に無い API で落ちる） | Q1 の「API の分類の網羅」に入れた（A で採用） |
| 品質 | Q-2 要る権限だけを欠く利用者での 403、組をパラメーターのテストの表で書く | Q1 の「権限ごとの API の認可」に入れた（採用） |
| 品質 | Q-3 権限の解決とメニューの絞り込みの性質ベースのテスト | Q1 の「性質ベースのテスト」と Q4 の「表示の木を作る関数」に入れた（採用） |
| 品質 | Q-4 既存の 2 値のテストを「そのまま・読み替え・置き換え」に振り分けた表 | 要件・設計に回した（コード生成の計画で作る。6節） |
| 品質 | Q-5 既存の E2E の書き換えは本数に数えない | Q3 に入れた（採用） |
| 品質 | Q-6 make-you-chic-ui に頼る振る舞いもこちらの画面のテストと axe で確かめる | Q4 に入れた（採用） |
| 品質 | Q-7 行を移す移行の結合テストで、前の版が使う列が一貫して読めること | Q9 に入れた（採用） |
| 開発 | D1 画面の機能どうしの import を lint で止め、今の1件を直す | Q7 にした（A で採用） |
| 開発 | D2 画面の骨組みの差し込み口の型を変えるときの承認と同じ Bolt での直し | Q8 の A にした（C で不採用。その都度計画で扱う） |
| 開発 | D3 内部DB に文字で残す列挙の値の名前を変えない | Q8 の B にした（C で不採用） |
| セキュリティ | G1 vendor の `npm ci` に `--ignore-scripts` を付け、決まりの文言を広げる | Q11 にした（C で今のまま）。F1（B）で team.md の文言は変えず、vendor が対象外であることを `project.md` の学びとして残す |
| セキュリティ | G2 権限で守る API の網羅のテスト | Q1 の「API の分類の網羅」に入れた（Q-1 と同じ。採用） |
| セキュリティ | G3 IDOR・組み込みの役割の保護・役割の本文の一括代入 | Q1 の「権限の昇格・一括代入・IDOR の防止」に入れた（採用。★組み込みの役割の有無） |
| セキュリティ | G4 メニューの項目を信頼できない入力として確かめる（エスケープ・`icon` の許可の一覧・道のエンコード） | Q4 に入れた（採用） |
| セキュリティ | G5 NEVER 権限の判定にトークンの中の値や画面の値を使わない | Q10 の D にした（選ばれず。要件の一文として扱う） |
| セキュリティ | 役割を変える E2E でも自分で作った役割と利用者だけを対象にする | Q3 に入れた（採用） |
| セキュリティ | P7 に、前の版へ戻したときの権限のずれを戻しの確かめに入れる | Q9 に入れた（採用） |
| セキュリティ | 「要求の改ざん」の例の `/api/me` は今のコードに無い | 基準の文言は Q2 の範囲（管理者の印の一般化）だけを直し、例の書き換えはしなかった。要件・設計で既存の API に読み替える（6節） |

## 6. 面談の決定

| 問い | 答え | team-practices.md・discovered-rules.md への反映 |
|---|---|---|
| Q1 役割・権限の必須のテスト | A（API の分類の網羅を含めてすべて足す） | Testing Posture に「役割・権限の機能では、次のテストを必ず書く」の一覧を足した |
| Q2 「管理者フラグ」「管理者の印」の文言 | A（この段で一般化） | 認証・認可・監査の一覧の「管理者フラグなし（403）」「管理者（200）」を「その権限を持たない（403）」「持つ（200）」に、「管理者の印」を「管理の権限」に直し、一覧の頭に読み方と「具体の形は要件で決まった後に足す」の1行を添えた。`project.md` の Forbidden・Mandated は書き換えていない |
| Q3 E2E の本数と役割を変える操作 | A（束ねた元の Intent ごとに最大2本） | E2E の箇条に、束ねた Intent の数え方、既存の E2E の書き換えは数えないこと、役割・権限を変える操作は自分で作った利用者と役割だけを対象にし初期管理者は変えないことを足した |
| Q4 N 階層のメニューの画面のテスト | A | Testing Posture に「N 階層のメニューの画面では、次のテストを必ず書く」の一覧を足した |
| Q5 make-you-chic-ui の入れ子のサイドバー | A（上流を先に） | Way of Working のサブモジュールの箇条の後に足した |
| Q6 既存の機能に手を入れたときの境界テスト | A | Code Style のバックエンドの境界テストの箇条に足した |
| Q7 画面の機能どうしの import | A（`no-restricted-imports`、今の1件は最初に手を入れる Bolt で直す） | Code Style のフロントエンドの機能の分離の箇条に足した |
| Q8 差し込み口の型・列挙の値 | C（決まりにしない） | 足していない |
| Q9 DB の後方互換と戻し方 | A（広げてから縮める二段） | Deployment に1つの箇条を足した（`admin_flag` は例） |
| Q10 新しい固い制約 | A だけ | `discovered-rules.md` の Mandated に1つ |
| Q11 vendor の `npm ci` のスクリプト | C（今のまま） | 足していない |
| Q12 イメージのダイジェストでの固定 | A | Deployment に1行足した |
| F1 Q11 と team.md の npm の文言の関係 | B（文言は変えず、vendor が対象外であることを `project.md` の学びとして残す） | team.md の文言は変えていない。学びとして残すのはこの段の学びの手続き（§13）で行う |
| まとめの確認 | Looks correct | — |

### 6.1 基準（team.md）からの差

- Way of Working: make-you-chic-ui に無い部品は上流への追加を先に依頼する箇条を1つ足した（Q5）。
- Testing Posture: E2E の箇条に3つの文を足し、「管理者の印の変更」を「管理の権限の変更」に直した（Q3・Q2）。認証・認可・監査の一覧の頭に読み方の1文を添え、「認可」「管理者の印の変更」→「管理の権限の変更」「最後の管理者の保護」「管理の API の認可」「要求の改ざん」「管理の操作の監査」の文言を一般化した（Q2）。役割・権限の必須のテストの一覧（8項目）と、N 階層のメニューの画面のテストの一覧（5項目）を足した（Q1・Q4）。Methodology・Ordering は変えていない。
- Deployment: 広げてから縮める二段の箇条と、イメージのダイジェストでの固定の箇条を足した（Q9・Q12）。
- Code Style: 境界テストの箇条に「既存の機能に手を入れる Bolt で無ければ足す」を、機能の分離の箇条に「`no-restricted-imports` で止め、今の1件を直す」を足した（Q6・Q7）。
- Walking Skeleton: 変えていない。
- 上に挙げた箇所のほかは、基準の文言・`(learned …)` の注記・cid をそのまま残した。`## Change Control`・`## Forbidden`・`## Mandated`・`## Corrections` は5つの節の外のため、この文書には置いていない（昇格の道具は5つの節だけを置き換える）。

## 7. P2 への異論とその決着

- リードの下書きの推奨: この段では文言を変えず、要件定義で権限の形が決まった後の Build and Test で読み替えを書き足す（それまでは「管理者」を「管理の権限を持つ利用者」と読む）。
- 開発担当（OBJECT）: Code Generation の Testing Contract は計画の承認の時点の team.md から作られるため、Build and Test まで待つとコード生成の担当は「管理者フラグなし（403）」のままの契約で書く。少なくとも読み方の1行をこの段で足すべき。
- セキュリティ担当（OBJECT）: 同じ理由で、この段で文言を一般化する（B）。一般化した文言は今の真偽値の形でも成り立つ。
- 品質担当（AGREE、条件つき）: P1 をこの段で足すなら A でよく、足さないなら B を推す。
- 決着: 依頼者は Q2 で A（この段で一般化し、具体の形は要件で決まった後に足す）を選んだ。開発・セキュリティ担当の異論の趣旨（Testing Contract に間に合わせる）に沿う形で、リードの元の推奨は採らなかった。
- セキュリティ担当の別の OBJECT（vendor の `npm ci` が決まりの趣旨から外れる）は、Q11 で C・F1 で B となり、今の動きを続け、team.md の文言は変えずに `project.md` の学びとして対象外を残す形で決着した。

## 8. 要件・設計の段に回す論点

チームの進め方ではなく、今回の Intent の機能の中身に当たるため、要件定義・ドメイン設計・機能設計・NFR 設計・コード生成の計画で扱う。

- 役割の持ち方（固定の役割か、画面で作る役割か）、権限の粒度（API ごと・画面ごと・操作ごと）、利用者と役割の関係、組み込みの役割の有無と範囲、`admin_flag` との関係と移行の中身・列の扱い（同期して持ち続けるか、読まないだけにするか）（K-32・K-34）。
- 主体 `AuthenticatedUser` の `admin`・応答 `CurrentUserResponse` の `admin`・画面の `LoginState.admin` も広げてから縮めるかどうか。
- 権限を要求ごとに DB から読むか、トークンに入れるか（今は DB から読む。K-32）。変更の前に出したトークンの扱い。Q10 の B〜D（割り当ての変更の監査・昇格の防止・判定の材料）は固い制約にしなかったため、要件の一文として決める。
- 認可の入口の置き場（`AdminAuthorizationManager` を広げるか、`@PreAuthorize` か）、API の分類の一覧の持ち方、`SecurityRuleContributor` の order（機能の名前で 100 台。テスト用の 250 との前後に注意）、403 の理由の値（今は `NOT_ADMIN`）、画面の 403 の判定（今は `/api/admin/` の接頭辞。K-33・K-35）。
- `access` の `Admin` の付くクラスを改名するか、役割・権限を新しい機能のパッケージにするか。
- 既存の 2 値のテスト（`AdminTestUsers`・`PublicApiTestRules`・認可・管理の権限の変更・最後の管理者の保護の結合テスト、E2E の `030`・`110`・`130`）を「そのまま・読み替え・置き換え」に振り分けた表（品質の Q-4）。テストを消して網羅を下げない。
- 画面の骨組みの差し込み口の型（`AccessLevel`・`VisibleWhen`・`SidebarItemRegistration`）の変え方と、全機能の `registration.ts` の直し（Q8 で決まりにせず、計画で扱う）。
- 監査の種類・理由の名前（列は `VARCHAR(32)`）と、既存の値（`USER_ADMIN_GRANTED`・`USER_ADMIN_REVOKED`・`NOT_ADMIN`）を残すか（Q8 で決まりにせず、設計で扱う）。役割そのものの作成・変更・削除と、拒否した操作を監査に残すか。
- メニューの出どころ（DSL の `menus` か、`registration.ts` か、両方か）とメニューと権限の結び付け、メニューを返す API が権限の無い項目を返すか（K-37）。メニューが指すテーブルの画面がまだ無いことの扱い。
- メニューの深さの上限の値と `icon` の許可の一覧、一覧に無い `icon` の扱い。
- make-you-chic-ui に足してもらう部品の中身（子の項目・開閉・`aria-current`・言語に合わせた `aria-label` または `Sidebar` を外へ出すこと・アイコンの種類）と、依頼が間に合わないときに諮る時点（開発担当は Delivery Planning を推した）。
- 画面の機能どうしの import を止める決まりの除外の範囲（テストのファイルが他の機能の `registration.ts` を読むことなど）と、今の1件の `logout` の移し先（`src/shared/` か `src/app/` か）。
- 「要求の改ざん」の例の `/api/me` は今のコードに無いため、確かめる API（ログイン・トークンの更新・表示の設定の保存など）は要件・設計で読み替える。
- `access.service`（`packagesJudgedByTotal` に入っている）に手が入るか。入るならカバレッジの作業が付くため、Delivery Planning で今の値を実測して見積もる。
- 権限を要求ごとに読む形の性能（応答時間の目標と k6 の場面、持ち主の段）。
- 新しく足す依存があれば、採用の前のライセンスの確認と OSV-Scanner（成果物を作る道具なら `config/npm-build-tools.txt` への追加）。

## 9. 未解決の点

- `git branch -a` で `remotes/origin/HEAD -> origin/main` と出た。team.md は GitHub の既定のブランチを `develop` と書いている。手元の `origin/HEAD` は clone の時点の値のまま古い見込みで、GitHub の設定は確かめていない（ネットワークを使うため行っていない）。
- Dependabot の知らせのブランチが6つ残っている（`mailpit-v1.31.3`・`setup-gradle-6.4.0`・`oxlint-1.86.0`・`typescript-eslint/parser-8.71.0`・`vitest-5.0.3`・`coverage-v8-5.0.3`）。重大度は確かめていない。重大度 High 以上なら team.md の決まりどおり次の Bolt に入る前に取り込む。Construction の前に確かめる。
- vendor の `npm ci` に `--ignore-scripts` を付けたときに make-you-chic-ui のビルドが通るかは確かめていない（Q11 で今のままとしたため、今回は確かめない）。
- GitHub の secret scanning・push protection の設定は、ネットワークを使わない約束のため確かめていない。
- 件数（境界テスト 10 個、jqwik のファイル 25、fast-check のファイル 18、E2E 13 本）はファイルの名前と検索で数えた値で、テストを実行した値ではない。
