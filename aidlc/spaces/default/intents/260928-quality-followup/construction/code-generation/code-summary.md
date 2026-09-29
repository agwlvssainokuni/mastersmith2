# コード生成の要約（Intent 260928-quality-followup）

scope bugfix・深さ Minimal・Test Strategy Minimal・単位の分割なし。計画は `code-generation-plan.md`（Step 1〜28）、テストの手順は `unit-test-instructions.md`、生成の途中の依頼者の決定（G1〜G5）は `code-generation-questions.md`。手順ごとの細かい実測・確かめは `generation-notes.md` にあり、この要約はそれをまとめたもの。

- 作業ブランチ: `fix/260928-quality-followup`（`develop` の `f7902e7` から）。変更はコミットしていない（コミットの提案 Step 27 と統合 Step 28 は、この段の進行役が依頼者の承認を得て行う）。
- 変えていないもの: `vendor/` の中身、`backend/src/main/java` の Java のコード、カバレッジの下限と除外、SpotBugs・OSV-Scanner・Gitleaks の関門、既存の ArchUnit の境界テスト、`.github/workflows/ci.yml`、`Dockerfile`、`docker/hikari-pool.sh`、前の Intent の承認済みの記録。

## 1. 変えたファイル

| ファイル | 変更 | 要件 |
|---|---|---|
| `vendor/make-you-chic-ui`（サブモジュールの固定先） | `735ef04` → `310e1ec`（間に `7865c28`、一直線の履歴）。中身は変えていない | FR1.1 |
| `frontend/e2e/support/axe.ts` | 既知の違反の一覧を空にし、空になった専用の仕組み（アバター・誤りの文字）を外した | FR1.2 |
| `frontend/e2e/050`〜`080-*.e2e.ts` | 冒頭のコメントと失敗の知らせを今の一覧に合わせ、080 から外した仕組みの呼び出しを除いた | FR1.2 |
| `frontend/src/features/preferences/PreferencesForm.css` | `.preferences-choice-error` を `var(--color-danger-text)` へ | FR2.1 |
| `frontend/src/features/dsl/DslSubmitForm.css` | `.dsl-link` を `var(--color-primary-subtle-text)` へ | FR2.2 |
| `frontend/src/app/pages/Page.css` | `.page-link` を残し、`var(--color-primary-subtle-text)` へ（Q3: A） | FR2.3 |
| `frontend/e2e/100-app-text-contrast.e2e.ts`（新規） | アプリ独自の CSS の文字と primary のボタンの hover のコントラストを 20 組で検査（流れではない検査） | FR2・NFR1 |
| `frontend/e2e/support/preferencesFixtures.ts` | 100 が使う 400 の見本 `preferencesValidationProblem`（画面の側の型付き） | FR2 |
| `backend/src/test/java/cherry/mastersmith/config/H2CompactionByPoolSuspensionIT.java` | 0 本の待ち 10 秒 → 30 秒（4か所）、接続の数の待ち3か所に診断 | FR3.1・FR3.3 |
| `frontend/src/features/invitation/InvitationAdminPage.test.tsx` | 1件だけ上限 15 秒、失敗したときの時間の診断 | FR3.2・FR3.3 |
| `frontend/src/app/display-settings/DisplaySettingsProvider.test.tsx` | 負荷で再現した不安定なテストの直し（描画の後の効果を待つ `waitForEffects`、5件） | G5 |
| `backend/src/main/resources/application.yaml` | `management.metrics.distribution.slo`（`http.server.requests` 6 つ、`mastersmith.mail.send` 7 つ） | FR4.1・FR4.2 |
| `backend/src/test/java/cherry/mastersmith/common/observability/HistogramBucketsIT.java`（新規） | バケットの境界の結合テスト（3件） | FR4.1・FR4.2・NFR3 |
| `docker/monitoring/dashboards/mastersmith-overview.json` | メールの送信の p95 のパネル（34）を `mastersmith.mail.send` のバケットへ（Q2: A）、パネル 29 の説明文 | FR4.4 |
| `README.md` | 手元の監視・画面の既知の制約（U4〜U8）・E2E の一覧と 100・新しい節「警報と対応の手順」 | FR1.3・FR2・FR4.4・FR6.1 |
| `gradle/libs.versions.toml` | archunit 1.5.1・snakeyaml 2.7、Jackson の BOM 3.1.6（G4: A）。networknt は 3.0.6 のまま | FR5・G4 |
| `backend/build.gradle.kts` | Jackson の BOM（`tools.jackson:jackson-bom` 3.1.6）を platform として読み込む（G4: A） | G4 |
| `backend/gradle.lockfile`・`settings-gradle.lockfile` | 作り直し | FR5・G4 |
| `gradle/wrapper/gradle-wrapper.jar`・`gradle-wrapper.properties`・`gradlew.bat` | Gradle wrapper 9.8.0（`gradlew` は差なし） | FR5 |
| `frontend/package.json`・`frontend/package-lock.json` | prettier 3.9.9・vite 8.3.1・vitest 5.0.2・@vitest/coverage-v8 5.0.2 | FR5 |
| `compose.yaml` | postgres の digest・mysql 26.7.0・mariadb 13.0.2・grafana/otel-lgtm 0.34.0 | FR5.1・FR5.3 |
| `backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java` | 3つの対象DB の版と digest、Javadoc の決まり | FR5.2・FR5.3 |
| `docker/perf/compose.yaml` | 対象DB の3つのイメージを `compose.yaml` と同じ値に（依頼者の指示。計画の外） | FR5.3 |
| `.github/dependabot.yml` | `ignore`（logback-appender のすべて・eclipse-temurin の大きな版・networknt のすべて） | FR5.4・O1・G4 |

作った・変えたアプリのパスの一覧は `source-manifest.json`。

## 2. 実測（基準と最後）

どちらも `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（colima、`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` あり）と `./gradlew e2eTest`（Mailpit を起動して）の実測。

| 項目 | 基準（Step 2、変更の前） | 最後（Step 25） |
|---|---|---|
| `verify` | BUILD SUCCESSFUL（6m 56s） | BUILD SUCCESSFUL（7m 47s） |
| バックエンドの単体テスト | 1234（失敗 0・SKIPPED 0） | 1234（失敗 0・エラー 0・SKIPPED 0） |
| バックエンドの結合テスト | 562（失敗 0・SKIPPED 0） | 565（失敗 0・エラー 0・SKIPPED 0。対象DB を含む。`HistogramBucketsIT` の3件で +3） |
| 画面の単体テスト | 732（Test Files 91） | 732（Test Files 91。Vitest 5.0.2） |
| カバレッジ（バックエンド、全体の合計） | 行 98.78%（5600/5669）・分岐 94.38%（2050/2172） | 行 98.78%（5600/5669）・分岐 94.38%（2050/2172）。パッケージごとの下限の検証も通過 |
| カバレッジ（画面） | Lines 97.44%（2322/2383）・Branches 92.67%（1443/1557） | Lines 97.44%（2322/2383）・Branches 92.67%（1443/1557）・Statements 97.21% |
| `e2eTest` | 90 passed（010〜090） | 110 passed（3.9m。010〜090 の 90 件と 100 の 20 件）。秘密情報の確かめの報告の部品は「含まれていません（3 項目）」、json の文字列の検索で `admin@`・`Bearer`・`eyJ` は 0 件、メールアドレスの形は既存の送信元 `e2e-noreply@example.com` の1件だけ |

- 最後の `verify` の関門: Gitleaks は leaks なし、SpotBugs の関門（`spotbugsGate`）は通過（警告の行数 155、Step 2 と同じ）、OSV-Scanner は `verify` の中では入力が同じため UP-TO-DATE で、`./gradlew osvScan --rerun` で流し直して「失敗の条件に当たるもの 0 件、警告 1 件」（警告は既存の `vendor/make-you-chic-ui` の開発用の undici）。
- Step 2 の基準の `verify` は通ったが、その後に OSV のデータベースに載った jackson-databind の High（G4）のため、Step 19〜21 の `verify` は `osvScan` だけが失敗していた（`generation-notes.md` の Step 18 の節）。G4: A の直しの後の結果が上の「最後」の列。

## 3. 既知の違反の一覧の変化（Step 10、FR1.2）

| 一覧（`frontend/e2e/support/axe.ts`） | 変更の前 | 変更の後 |
|---|---|---|
| `KNOWN_VIOLATIONS`（050 のログイン画面の primary のボタン） | green・orange の light・dark の4組 | 空（仕組み `splitKnownViolations` は残す） |
| `INVITATION_KNOWN_VIOLATIONS`（060） | green・orange の4組の `list`・`inviteDialog` | すべての状態で空 |
| `REGISTRATION_KNOWN_VIOLATIONS`（070） | green・orange の4組の `ready` | すべての状態で空 |
| `PREFERENCES_KNOWN_VIOLATIONS`（080）・`AVATAR_KNOWN_COMBOS`・FormField の誤りの文字 | 20 組のうち 19 組（アバター）、green・orange（保存のボタン）、dark（誤りの文字） | 空。`withAvatarKnownViolation`・`withFormFieldErrorKnownViolation` などの専用の仕組みを外した |

- 固定先を上げた直後に一覧を変えずに流すと、050〜080 の 83 件のうち 31 件が「一覧にある違反が当たらなくなった」で失敗した（想定外の違反は 0 件）。一覧を空にした後は 83 件すべて通った。
- 050〜080 に残った既知の違反は無い。make-you-chic-ui の `310e1ec` の直しの外に残る2件（Tabs の選ばれたタブ、primary のボタンの hover）は、100 の中だけの既知の違反 `STATE_KNOWN_VIOLATIONS` として扱う（G2: C。4節）。

## 4. 選んだ色と理由（Step 11、FR2）

| クラス | 色 | 理由 |
|---|---|---|
| `.preferences-choice-error` | `--color-danger-text`（light は red-500、dark は red-400） | make-you-chic-ui の `310e1ec` が足した誤りの文字用の色（FR2.1 の指定）。計算で light 4.63/4.83・dark 6.92/5.31（背景 bg/surface） |
| `.dsl-link` | `--color-primary-subtle-text`（light は brand-700、dark は brand-400） | 計画の候補。計算ですべての組が 4.5:1 以上（light 6.83〜8.72、dark 5.56〜10.99）のため、代わりの `--color-text` は使わない |
| `.page-link` | `.dsl-link` と同じ | Q3: A（残して文字用の色に） |

- 100 の実際のブラウザの検査で、3つの状態（誤りの文字・DSL のリンク・見つからない画面のリンク）が 20 組すべてで違反なし。
- 100 がアプリの CSS の誤りを捕まえることを1回だけ確かめた（CSS を変更の前の色に戻すと 12 組が失敗。確かめの後に戻した）。

### make-you-chic-ui に残るコントラストの不足（G2: C）

- **Tabs の選ばれたタブ**（`.mycui-tab.active`、文字 `--color-primary`）: green・orange の light、blue・purple の dark の4組で `color-contrast`（計算 3.16・3.41・3.71・3.56）。
- **primary のボタンの hover**（背景 brand-600 に文字 gray-900）: green・orange の light・dark の4組で `color-contrast`（計算 3.54・3.43）。100 に状態 `primary-button-hover` を足して検査するようにした。
- `vendor/` はこのリポジトリから変えられないため、2件とも既知の制約として受け入れ（G2）、100 の `STATE_KNOWN_VIOLATIONS` に組・状態ごとに載せた（当たらなくなれば 100 が失敗して気づける）。README の U4・U8 の節に書いた。make-you-chic-ui のリポジトリ側への直しの依頼は次の Intent の候補。

## 5. Step 4 の確かめ（FR6.3・R-01）

README に書く前に、ソースと既存の結合テストで確かめた（Step 2 で `RegistrationApiIT` 5 件・`InvitationAuditIT` 3 件が通った）。

| 事柄 | 確かめた場所 | 結果 |
|---|---|---|
| 登録の完了の拒否 | `RegistrationService.complete`（152〜159 行）、`AuditEventListener.onRegistrationFailedEvent`（208 行）、`RegistrationController`（109・113〜115 行）、`GlobalExceptionHandler.log`（219〜233 行）。`RegistrationApiIT`「every rejection reason gives the same 404 body and the audit records the reason of BR7.6」 | 形の誤ったトークンでも `REGISTRATION_FAILED`（`FAILURE`）が残る。応答は 404 `REGISTRATION_LINK_INVALID`、ログは WARN 1件。入力の誤り（400）は監査に残らない |
| リンクの確かめの拒否（R-01） | `RegistrationService.verify`（118〜132 行、出来事を出さない）、`RegistrationController.verify`（82〜85 行）。`RegistrationApiIT`「verify returns only the email and language, changes nothing and is not audited」、`InvitationAuditIT`「refusals, send failures are not audited; verify, input errors and replacement are not audited」 | **監査に残らない**。応答は同じ 404、ログは WARN 1件 |
| 招待メールの送信の失敗 | `InvitationAdminController`（93・127 行）、`SmtpMailSender.log`（155〜171 行）、`InvitationMailDispatcher`（95〜101 行）、`AuditEventType`、警報の式 `ms-5xx-ratio`（75 行）・`ms-error-logs`（107 行） | 招待 201・送り直し 200 で `sendResult: FAILED`。ログは WARN と INFO。監査に送信の失敗の種類は無い。**既存の警報には当たらない** |

README の新しい節「警報と対応の手順」（Step 24）は、この結果と、3つの p95 の警報の式・しきい値（`docker/monitoring/provisioning/alerting/mastersmith.yaml`: `ms-login-p95`・`ms-refresh-p95` は 1000 ms、`ms-check-p95` は 300 ms、どれも 5 分）だけで書いた。登録の完了と確かめの拒否を見分けるログの問い合わせは、文言の絞り込みで項目 `code` を拾えないため、「項目 `code` の値で見分ける」「トレースの API の経路で見分ける」と書き、実際には流していない問い合わせの形は書いていない。

## 6. team.md の決まりとの差（FR3.4）

- **対象の決まり（`team.md`）**
  1. Testing Posture: 「不安定なテストは放置せず、原因を直すまで統合しない。」
  2. Way of Working: 「CI は統合後の再確認として動かす。CI が失敗したら、次の Bolt に進む前に原因を直す。」
- **今回の判断**: 前の Intent（260925-user-management）の CI（`66fe981`）で `verify` が2回とも別々のテストの時間切れで失敗した2件について、原因は確かめず、待ちの上限を延ばすことを直しとして統合する（要件の Q1・F1: B、FR3.4）。
  - `H2CompactionByPoolSuspensionIT`: 接続の数の待ちの上限 `ZERO_CONNECTIONS_WAIT` を 10 秒 → 30 秒（4か所すべて。FR3.1）。
  - `InvitationAdminPage.test.tsx` の1件: 上限を Vitest の既定の 5 秒 → 15 秒（この1件だけ。FR3.2）。
  - どちらも、次に落ちたときに原因を切り分ける手がかりとして、失敗の知らせに診断を入れた（FR3.3）。
- **差**: 決まり1は「原因を直すまで統合しない」、決まり2は「原因を直す」ことを求めるが、今回は原因を確かめないまま統合する。上限を延ばすことは、原因が「上限が CI の runner の速さに対して短すぎた」ことである場合に限って原因の直しになる。それ以外の原因であれば、失敗の頻度を下げるだけで原因は残る。
- **前提 A1（要件）**: CI の2件の時間切れは、上限が CI の runner の速さに対して短すぎたことによる、とみなす。原因は確かめない（F1: B）。確かめられるのは、上限を延ばした後に通ること（NFR2。持ち主は Build and Test）と、次に落ちたときの診断だけである。
- **直さない（確かめていない）原因の候補**
  - `H2CompactionByPoolSuspensionIT`（K-4、コード知識ベース `architecture.md` の Interaction Diagrams 3。どれも未検証）:
    - (a) 一時停止の直前に動いていた HikariCP の接続の補充が、破棄の後に接続を1本足し、0 本にならない。
    - (b) 同じ文脈の定期の処理や起動の後の処理が接続を借りていて、返すのが遅れた（破棄の印を付けられた接続は、返されるまで数に残る）。
    - (c) CI の runner が遅く、最後の接続を閉じるときの H2 の処理が 10 秒を超えた。
  - `InvitationAdminPage.test.tsx` の1件（K-5、`code-quality-assessment.md`。未検証）: 1つのテストの操作が多く（招待3回で 36 文字のメールアドレスを `user.type` で1文字ずつ入力・送り直し2回・取り消し2回・ページ送り）、CI の runner で既定の 5 秒すれすれになる（前の Intent の CI の1回目は 4,583 ms で通り、2回目で 5 秒を超えた。手元では該当の1件が 1,442 ms）。上限の延長のほかの直し方の候補（`user.paste` への置き換え、流れを複数のテストに分ける）は採っていない。
- **次に落ちたときの手がかり（FR3.3）**
  - `H2CompactionByPoolSuspensionIT`: 失敗の知らせ（`AssertionError` の文言。CI の Gradle の出力に FULL で出る）と ERROR のログに、待ちの上限（ミリ秒）・期待の説明と、プールの全体・使用中・空きの接続の数・接続を待っているスレッドの数が出る。見方の目安: 使用中が 1 以上なら (b)（借りたまま返っていない）、空きが 1 以上なら (a)（補充で足された接続が残った）、全体が 0 に近いのに時間切れなら (c) を疑う（どれも目安で、確かめたものではない）。
  - `InvitationAdminPage.test.tsx`: 失敗したときに標準エラーへ、テストの名前とかかった時間（ミリ秒）と上限が1行出る。15 秒に近い時間で落ちたなら、上限をさらに延ばすのではなく、操作の数を減らす直し（K-5 の候補）を検討する材料になる。
- **確かめの持ち主**: 診断がわざと時間切れのときに出ることの1回の確かめと、依頼者の `git push` の後の CI の `verify` が通ること（NFR2）は Build and Test（計画の 7節）。Build and Test の `test-results.md` にも同じ内容に CI の結果を足して書く。

## 7. FR6.2 前の記録との差

前の Intent（260925-user-management）の承認済みの記録は書き換えていない。どの記述をどう正したかを次に記録する（正しい形は README の「警報と対応の手順」）。

| 前の記録 | 記述 | 正しい形（5節の確かめ） |
|---|---|---|
| `operation/observability-setup/alarms.md` 7 行 | 「招待・登録・送信の失敗は、既存の `ms-5xx-ratio`（5xx の割合）と `ms-error-logs`（ERROR のログの増加）で拾います」 | 送信の失敗は 201・200 と WARN・INFO のため、どちらの警報にも当たらない。登録の拒否も 404 と WARN のため当たらない（`runbooks.md` 23 行が同じ誤りを既に指摘している） |
| `operation/observability-setup/log-queries.md` 41 行 | 「設計どおり、登録の失敗はトークンで招待を引いた後の拒否だけを残す」「形の正しくないトークンの総当たりは監査の問い合わせでは見えず」 | 登録の完了（`/complete`）では、形の誤ったトークンでも `REGISTRATION_FAILED`（理由 `INVITATION_NOT_FOUND`）が残る。監査に残らないのはリンクの確かめ（`/verify`）の拒否と入力の誤り（400）だけ。同じ行の「リンクの確かめの拒否（404）も残りません」は正しい |
| `operation/incident-response/runbooks.md` の RB-17（118 行） | 「形の誤ったトークンの 404 と入力の誤りの 400 は監査に残らないため、ここでしか見えない」 | 形の誤ったトークンの 404 は、`/complete` では監査に残る（`/verify` では残らない）。`/complete` の総当たりは監査の `REGISTRATION_FAILED` を数える問い合わせでも見える |

- `runbooks.md` 196 行の「メールの送信の失敗…には、警報がありません」と 27 行の表は正しい（直す対象ではない）。

## 8. 依存の更新の一覧（Step 23、FR5.5）と変えた決まり

Step 3 で確かめ直した `origin/dependabot/*` は 15 本（計画の 8節と同じ。`git fetch origin --prune` の前後で増減なし）。「関門」は、フォーマット・リンタ・ライセンスヘッダー・ビルド・全テスト（対象DB を含み SKIPPED なし）・カバレッジの下限・Gitleaks・SpotBugs が通り、OSV-Scanner にその更新が持ち込んだ新しい High が無いこと。

| ブランチ（`origin/dependabot/` を省く） | 更新 | 判定 | 理由・確かめた関門 |
|---|---|---|---|
| docker/eclipse-temurin-26.0.2_10-jre-noble | eclipse-temurin 25.0.4_7 → 26.0.2_10 | 見送り | JDK 25 のビルド・CI と版が分かれ、`verify` はイメージを作らず確かめられない（F2: C）。`ignore` に大きな版を入れた |
| docker_compose/grafana/otel-lgtm-0.34.0 | grafana/otel-lgtm 0.33.1 → 0.34.0 | 取り込み | Step 7 の手元の監視の確かめ（Grafana・警報 16 件・Prometheus・Loki・Tempo）が通った |
| docker_compose/mariadb-13.0.2 | mariadb 11.8.9 → 13.0.2 | 取り込み（決まりを変える） | 対象DB の結合テスト 50 件と `verify` の関門が通った。`compose.yaml`・`TargetDbImages`・`docker/perf/compose.yaml` を同じ値に |
| docker_compose/mysql-26.7.0 | mysql 8.4.11 → 26.7.0 | 取り込み（決まりを変える） | 同上 |
| docker_compose/postgres-18.6 | postgres 18.6 の digest | 取り込み | 同上（版は同じで digest だけ） |
| gradle/com.networknt-json-schema-validator-3.0.7 | networknt 3.0.6 → 3.0.7 | **見送り**（G4: A） | 試した `verify` の関門は通ったが、アプリ全体の Jackson を 3.2 系（3.2.1）に引き上げ、Spring Boot の管理の 3.1 系から外れる（`project.md` の学び「3.0.6 を選んだ理由」のとおり）。Jackson の High を 3.1 系の修正版 3.1.6 で直すため、3.0.6 に戻した。`ignore` に入れた（外す時期: Spring Boot の管理の Jackson が 3.2 系以上になったとき） |
| gradle/com.tngtech.archunit-archunit-junit5-1.5.1 | archunit 1.5.0 → 1.5.1 | 取り込み | Gradle の小さな更新とまとめた `verify` の関門が通った。テストだけの slf4j-api 2.0.18 → 2.0.19 の引き上げ |
| gradle/gradle-wrapper-9.8.0 | Gradle wrapper 9.7.1 → 9.8.0 | 取り込み | 同上。作り直した4つのファイルは Dependabot のブランチと同じ |
| gradle/io.opentelemetry.instrumentation-opentelemetry-logback-appender-1.0-2.31.1-alpha | logback-appender 2.28.1-alpha → 2.31.1-alpha | 見送り | 固定の理由（外部エクスポートの有効時の失敗）が `verify` では確かめられない（F2: C）。`ignore` に入れた |
| gradle/org.yaml-snakeyaml-2.7 | snakeyaml 2.6 → 2.7 | 取り込み | Gradle の小さな更新とまとめた `verify` の関門が通った |
| npm_and_yarn/frontend/prettier-3.9.9 | prettier 3.9.8 → 3.9.9 | 取り込み | npm の小さな更新とまとめた `verify` の関門が通った。フォーマットの差なし |
| npm_and_yarn/frontend/typescript-7.0.2 | typescript 6.0.3 → 7.0.2 | 通らずに見送り | リンタで止まった（typescript-eslint が TS 7 に未対応。最新の 8.71.0 も peer は `<6.1.0`）。元に戻した。`ignore` には入れない |
| npm_and_yarn/frontend/vite-8.3.1 | vite 8.3.0 → 8.3.1 | 取り込み | npm の小さな更新とまとめた `verify` の関門と `e2eTest`（110 件）が通った |
| npm_and_yarn/frontend/vitest-5.0.2 | vitest 4.1.11 → 5.0.2 | 取り込み | coverage-v8 と1組で `verify` の関門と `e2eTest` が通った。設定は変えず、下限の判定が効くことを確かめた |
| npm_and_yarn/frontend/vitest/coverage-v8-5.0.2 | @vitest/coverage-v8 4.1.11 → 5.0.2 | 取り込み | vitest と1組（同上） |

- 集計: 取り込み 11 本（うち決まりを変える 2 本）、見送り 3 本、通らずに見送り 1 本。
- GitHub で開いているプルリクエストの数は AI では確かめていない（10節）。プルリクエストを閉じる操作と `git push` は依頼者が行う。

### 依存の更新とは別に取り込んだもの: Jackson 3.1.6（G4: A）

- 既存の `tools.jackson.core:jackson-databind 3.1.5`（Spring Boot 4.1.1 の管理の版）に High の脆弱性 GHSA-q4xh-88c3-wmh7（CVSS 7.5、2026-09-28 公開。直った版は 3.1.6・3.2.2）があり、OSV-Scanner で `verify` が止まっていた（`develop` の上でも同じ）。
- 上げ方: このリポジトリは Spring Boot の BOM を Gradle の `platform()` で読み込んでおり（依存の管理のプラグインを使わない）、BOM の版のプロパティで上書きする口が無い。そこで、Spring Boot が Jackson の版を決めるのと同じ Jackson の BOM（`tools.jackson:jackson-bom`）を 3.1.6 で platform として読み込み、Jackson の部品の版をまとめて上げた（`gradle/libs.versions.toml` の `jackson = "3.1.6"`、`backend/build.gradle.kts`）。Gradle は高い方の版を選ぶため、Spring Boot の BOM の 3.1.5 より 3.1.6 が選ばれる。
- 依存の木（lockfile を作り直した結果）: networknt 3.0.7 を入れる前の lockfile と比べて、変わったのは `jackson-core`・`jackson-databind`・`jackson-bom` の3行（3.1.5 → 3.1.6）だけ。`jackson-annotations` は 2.21 のまま（3.1.6 の BOM も 2.21）、ほかの部品の版は変わらない。`dependencyInsight` で本番のクラスパスの `jackson-databind` が 3.1.6（Jackson の BOM による）であることを確かめた。
- OSV-Scanner: lockfile の直接の検査で「No issues found」（High と Medium 2件とも消えた）。`./gradlew osvScan` は「失敗の条件に当たるもの 0 件、警告 1 件」（警告は既存の `vendor/make-you-chic-ui` の開発用の undici）。
- Maven Central には 3.1.7 もある。依頼者の決定どおり 3.1.6 にした。

### 変えた決まり（FR5.2。学びの手順で `project.md` に反映するかを依頼者に確かめる）

- **`TargetDbImages` の「長く支援される版（8.4・11.8 の系列）」**: mysql 26.7.0・mariadb 13.0.2 を取り込み、Javadoc の決まりを、今の版の系列（26.7・13.0）と、長く支援される版に限る決まりを外して結合テストで確かめて上げたことに書き直した。26.7・13.0 が長く支援される版かどうかは確かめていない。
- **networknt の「3.0.6 を選んだ理由」**: G4: A で 3.0.7 を見送ったため、決まりは変えていない（生成の途中で一度「取り込み（決まりを変える）」と判定していたが取り消した）。
- **Jackson を Spring Boot の管理の版から上書きすること**: 新しい決まり（Spring Boot を上げるときに上書きが要るか見直す）。Tomcat の上書きと同じ扱い。

## 9. 計画との差

| 差 | 内容 | 根拠 |
|---|---|---|
| G1 | 処理中の数の指標（`http_server_requests_active_milliseconds_bucket`・`mastersmith_mail_send_active_milliseconds_bucket`）にも同じ境界のバケットが付く（系列 14 本・8 本）。Java の `MeterFilter` は足さず、README に書いた | 依頼者の決定 G1: A |
| G2 | make-you-chic-ui に残る2件（Tabs・hover）を既知の制約として受け入れ、100 に hover の状態の検査を足し、既知の違反を `STATE_KNOWN_VIOLATIONS` に一般化した | 依頼者の決定 G2: C |
| G3 | README を計画の Step 13 の列挙より広く直した（U5〜U8 の各節の「既知の制約」） | 依頼者の決定 G3: A |
| G4 | networknt 3.0.7 を見送り、Jackson の BOM 3.1.6 を読み込む変更（`backend/build.gradle.kts`・`gradle/libs.versions.toml`）と `dependabot.yml` の networknt の `ignore` を足した。計画の影響の範囲に `backend/build.gradle.kts` は無い | 依頼者の決定 G4: A |
| G5 | `DisplaySettingsProvider.test.tsx` の不安定を負荷をかけて再現し、原因を確かめて、テストの側を直した（11節）。計画に無い変更 | 依頼者の決定 G5: B |
| `docker/perf/compose.yaml` | 負荷の試験の使い捨ての環境の対象DB のイメージも同じ値にそろえ、`TargetDbImages` の Javadoc に書いた（計画の影響の範囲に無いファイル） | 依頼者の指示 |
| `.page-link` | 要件の FR2.3 は「使われていない `.page-link` を消す」だが、`NotFoundPage.tsx` の 31 行が使っていたため、残して文字用の色に直した。確定済みの要件とコード知識ベースの K-3 は書き換えていない（K-3 の検索が `.tsx` の中の文字列を見落としたと見られる） | 依頼者の決定 Q3: A |
| FR2 の確かめ | 要件の受け入れの基準は「vitest-axe か E2E」だが、`vitest.config.ts` の `css: false` で jsdom は色を計算しないため、E2E の 100（実際のブラウザ）で確かめた | 依頼者の決定 Q4 |
| `HistogramBucketsIT` の mail の Timer | 「登録先の `MeterFilter` を通る Timer を作る」のではなく、実際の送信の入口を1回通して記録された Timer を読む形にした（宛先は `example.test`、テストの既定では SMTP の接続先が無く送られない） | 実際の経路に近いため |
| 100 の状態の順 | ログインの状態が保存した設定に戻るため、先にプリファレンスの画面で組を当て、読み込み直さずに画面の中で移る形にした（状態の順は計画どおり） | Step 12 の実測 |
| lockfile の作り直しの入口 | 計画の `:backend:dependencies --write-locks` ではなく、`backend/build.gradle.kts` の注記どおり `:backend:resolveAndLockAll --write-locks` | 同じ役目の、このリポジトリの入口 |
| Step 21 の `verify` | 3つのイメージを1つずつ対象DB の結合テストで通した後、3つを入れた状態で `verify` を1回流した | 1つずつの切り分けは結合テストで済むため |
| Step 19〜21 の `verify` の判定 | 既存の jackson-databind の High で `osvScan` だけが失敗していたため、「その更新が新しい High を持ち込まない」ことと、`--continue` でほかの関門を確かめて判定した | 計画の関門の文言（新しい High）、G4 で解消 |
| Q7 | サブモジュールの固定先の更新（C1）は専用のコミットとし、確かめる E2E の一覧は直後の C2 に置く。`project.md` の Mandated「不具合を直すときは、再現するテストを同じコミットに含める」との差 | 依頼者の決定 Q7: A |

## 10. 依頼者に確かめたいこと

1. **GitHub で開いているプルリクエストの数**（R-03）: 手元の参照の `origin/dependabot/*` は 15 本。前の記録の 11 件との差を、GitHub の画面で確かめてください。閉じる操作は依頼者が行います（8節の一覧が手がかり）。
2. **変えた決まりを `project.md` に反映するか**（学びの手順）: (a) `TargetDbImages` の対象DB の版を長く支援される版に限る決まりを外したこと（mysql 26.7・mariadb 13.0）、(b) Jackson を Spring Boot の管理の版から Jackson の BOM で上書きしたこと（Spring Boot を上げるときに見直す）。networknt の「3.0.6 を選んだ理由」は変えていません。
3. **Jackson の BOM の大きな版の知らせ**: `dependabot.yml` は Jackson の BOM（`jackson = "3.1.6"`）の 3.2 系への更新も知らせます。3.1 系にとどめる決まりのため、`ignore`（`tools.jackson:jackson-bom` の `version-update:semver-minor`）に入れるかを確かめたい（今回は入れていません）。
4. **make-you-chic-ui への直しの依頼**（G2）: Tabs の選ばれたタブと primary のボタンの hover のコントラストの直しを、次の Intent の候補として扱う。
5. **`packagesJudgedByTotal` の数**（O4）: `team.md` の記述は 22 パッケージだが、今の一覧は 12 個。記述を直すかは学びの手順で。
6. **イメージ `mastersmith:quality-followup`**: Step 7 の手元の監視の確かめで作ったイメージが残っています（秘密情報は含まない）。消すかを確かめたい。
7. **G5 の直し**（11節）: 負荷で再現し原因を確かめたため、決定 B のとおりテストの側を直しました。直しを専用のコミットに分けるか（計画の C1〜C10 のどれにも当たらない。例: C4 の「CI の時間切れの直し」と同じテストの安定化としてまとめる、または C11 を足す）を確かめたい。

## 11. G5 `DisplaySettingsProvider.test.tsx` の再現の試み

依頼者の決定 G5: B（負荷をかけて再現を試み、再現すれば原因を確かめて直す）。**再現し、原因を確かめ、テストの側を直した**。

### 試した方法と結果

負荷は `yes > /dev/null` の数（この PC は 8 コア）で作り、`caffeinate -i` で全体を包んだ。対象は `frontend/src/app/display-settings/DisplaySettingsProvider.test.tsx`（15 件）。記録は scratchpad の `g5/` にある（コミットしない）。

| 試し | 負荷 | 回数 | 結果 |
|---|---|---|---|
| 1回目 | `yes` 16 | 1 回で止めた | 負荷が強すぎ、Vitest の作業プロセスの起動が 60 秒で時間切れ（テストが1件も流れない）。負荷を調整してやり直した |
| A | `yes` 6 | そのファイルだけを 40 回 | 40 回すべて 15 件通過 |
| B | `yes` 8 | そのファイルだけを 19 回（予定 30 回。再現したため 19 回で止めた） | **17 回目に1件失敗**: 「keeps previews unsaved, per axis, and drops them on clear or on a new login state」の 345 行で、3つの同期の `act`（見せ方の設定と言語）の直後に `en/dark/sm/dark/-` を期待し、`ja/light/md/light/-`（何も当たっていない）だった。前に `verify` で落ちた「applies saved preferences at once…」と同じ形（同期の `act` の直後に同期で確かめる） |
| 予定の C・D（画面のテスト全体を並行度を上げて5回ずつ） | — | 流していない | B で再現したため省いた |

### 原因の確かめ（一時の計測。コードに残していない）

- 2つのテストの確かめの直前に、画面が期待と違うときだけ、表示の設定の保存先（`getDisplaySettingsSnapshot()`）の値・`captured.value` の有無を標準エラーに出し、その後に画面が追い付くまでの時間を測る計測を一時的に入れ、`yes` 8 でそのファイルを流した（10 回。7 回目で当たったため止めた）。
- 7 回目: `dom=ホーム captured=true savedUser="en"`、**4 ms 後に画面が追い付いた**。つまり、操作は保存先に当たっている（`captured.value` もある）が、`act` を抜けた時点で画面が描き直されていなかった。
- 仕組み: ログイン状態の提供元を渡すと `LoginStateGate` は答え（Promise）を待ってから中身を描くため、`DisplaySettingsProvider` は Probe と同じ描画で作られ、保存先の購読（`useSyncExternalStore`）を描画の後の効果（passive effect）で始める。この描画は `act` の外（Promise の解決）で起きるため、描画の後の効果は React の Scheduler の後の仕事に回る。`findByTestId` は Probe が DOM に出た時点で解決するため、負荷が高いと、購読が始まる前にテストが同期の `act` で保存先を変え、購読者がいないため描き直しが起きず、同期の確かめが古い画面を見る。後で購読が始まると React が値の変化に気づいて描き直す（4 ms 後に追い付いたのはこれ）。
- 計測の後、ファイルを元の内容に戻したことを `git diff` で確かめた。

### 直し（計画に無い変更）

- `DisplaySettingsProvider.test.tsx` だけを直した（本番のコードは変えていない）。Probe に描画の後の効果（`useEffect`）で印 `captured.effectsFlushed` を立て、補助 `waitForEffects()` でそれを待つ。Probe の `useEffect` は、同じ描画の `DisplaySettingsProvider` の購読の効果とまとめて流れるため、印が立てば購読は始まっている。
- ログイン状態の提供元を渡して Probe を待つ5つのテスト（「uses the user settings after login even when the browser storage was cleared」「applies saved preferences at once…」「keeps previews unsaved…」「shows the login screen with the settings saved at registration, but not after login」「keeps only the three display settings in the browser storage and no secret」）で、Probe を待った直後に `waitForEffects()` を足した。確かめの中身（期待の値）と同期の確かめは変えていない。
- 提供元を渡さないテスト（`renderWithProviders` の `act` の中で描かれ、効果もその `act` で流れる）は変えていない。
- 確かめ: Prettier・`npm run typecheck`・`npm run lint` が通った。

### 直したことの確かめ（負荷をかけて）

- `yes` 8 で、そのファイルだけを 40 回流した（補助が実際に待ったときだけ待った時間を出す一時の計測を入れて。確かめの後に計測を外し、直した内容と `cmp` で一致を確かめた）。
- 結果: **39 回すべて 15 件通過、テストの失敗 0**。1 回（27 回目）は作業プロセスの起動が 60 秒で時間切れでテストが流れず、数から外した（負荷による Vitest の起動の失敗で、テストの結果ではない）。4 回目に補助が実際に 53 ms 待ち（直す前なら失敗しうる状態）、テストは通った。
- 最後の `verify`（2節）でも、このファイルを含む画面のテストが通った。

### 残ること

- `project.md` の Mandated「不具合を直すときは、その不具合を再現するテストを同じコミットに含める」: 直したのはテストの側の待ち方で、再現は負荷をかけた繰り返しの実行でしか起きないため、再現のテストをコードに足していない（再現の手順と結果はこの節）。
- 同じ形（ログイン状態の提供元を渡して待った直後に、同期の `act` と同期の確かめ）がほかのテストのファイルにあるかは調べていない（G5 の範囲の外）。

## 12. Build and Test に引き継ぐこと

- FR3.3 の診断の確かめ（待ちの上限を一時的に極端に短くして1回ずつ流し、決めた項目が失敗の知らせとログに出ることを確かめ、元に戻す）。
- FR3.4: `test-results.md` にも6節の差を書く（CI の結果を足して）。
- NFR2: 依頼者の `git push` の後の CI の `verify` が通ることを1回以上確かめる。
- O3（FR4 の受け入れの基準）: 警報が鳴ることの確かめ方を決めて行う（Step 7 の結果: 名前は `http_server_requests_milliseconds_bucket`・`mastersmith_mail_send_milliseconds_bucket`、`le` は決めた境界と `+Inf`、`ms-login-p95` 487.5・`ms-refresh-p95` 95・`ms-check-p95` 95）。
- 最後の実測と FR5 の受け入れの基準（取り込んだ更新をすべて入れた状態の `verify` と `e2eTest`）。
