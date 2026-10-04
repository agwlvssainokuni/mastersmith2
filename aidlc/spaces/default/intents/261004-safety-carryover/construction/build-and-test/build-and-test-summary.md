# Build and Test のまとめ（Intent 261004-safety-carryover）

上流: `aidlc/spaces/default/intents/261004-safety-carryover/construction/code-generation/code-generation-plan.md`（Testing Contract と 6節）、`unit-test-instructions.md`、`code-summary.md`。この Intent には NFR Requirements・NFR Design の段が無いため、測れる目標は要件（`aidlc/spaces/default/intents/261004-safety-carryover/inception/requirements-analysis/requirements.md`）の NFR と FR の判定、Testing Contract のカバレッジの下限から集めた。

## 1. 結論

- ビルドとすべてのテスト・検査は通った（単体 1,547・結合 707 件、失敗 0。`test-results.md` 1節）。
- 測れる目標 17 件はすべて Met。Unverified・Not Met は無い（下の表）。
- ログインの p95 は、依頼者が VM の時計を合わせて測り直しを求めた後の3回（7〜9回目）で、どれも 1 秒を下回った（最大 928 ms）。合わせる前の6回のうち2回（繰り返しの時間 1,013・1,073 ms）は 1 秒を超えていた。これを、FR2.2a の「条件がそろわない回」（VM の時計のずれ）として判定から外した。外した理由と値は `test-results.md` 2節に残している。この扱いでよいかを承認の場で確かめる。
- フィルターの中の例外の ERROR は、本番と同じ Tomcat が1つの環境で要求ごとに1行だけになった（R-01 の実機の確かめ、`test-results.md` 3節）。
- `team.md` の「12 パッケージ」を、依頼者の文言（Q4: A）で直した（FR6）。
- 持ち越し・引き継ぎ: 配備（FR8）と配備の前の E2E（Q3: B）は Deployment Pipeline・Deployment Execution の段。`develop` の CI は、この段の承認の後に依頼者が push して確かめる（Q6: B）。

## 2. 生成した手順の文書

| 文書 | 中身 |
|---|---|
| `build-instructions.md` | 前提、1コマンドの検査、アプリのイメージ、よくあるつまずき |
| `integration-test-instructions.md` | この Intent の結合テスト6クラスと流し方。E2E は配備の前 |
| `performance-test-instructions.md` | ログインの p95 の条件・判定の値（繰り返しの時間）・時計のずれの測り方、R-01 の確かめ |
| `security-test-instructions.md` | 関門の基準、STRIDE ごとの対策と確かめるテスト、受け入れたリスク |
| `test-results.md` | 実測の結果（1〜5節） |
| `cross-unit-traceability.md` | 要件の FR・NFR ごとの網羅 |

## 3. カバレッジの見込みと実測

- 単位の分割は無い（zero-Unit）。下限は行 80%・分岐 70%（全体とパッケージごと）。実測は全体 行 98.9%・分岐 94.8%、手を入れた5パッケージはどれも下限を上回る（`test-results.md` 1節）。

## Target Verification Matrix

| Target ID | Source | Expected | Actual | Evidence | Owning Stage | Verdict |
|---|---|---|---|---|---|---|
| TC-COV-TOTAL | code-generation-plan.md の Testing Contract（team.md の下限） | バックエンド・フロントエンドの全体で行 80%・分岐 70% 以上 | バックエンド 98.9%・94.8%、フロントエンド 97.44%・92.77% | test-results.md 1節（verify の jacoco と vitest） | build-and-test | Met |
| TC-COV-PKG | 同上（パッケージごとの下限、`packagesJudgedByTotal` を増やさない） | 手を入れたパッケージが行 80%・分岐 70% 以上、一覧は 7 個のまま | 5パッケージとも上回る（最小は audit.service の分岐 82.5%）、一覧は 7 個 | test-results.md 1節、backend/build.gradle.kts | build-and-test | Met |
| TC-SUITE | Testing Contract（bugfix の床: 既存のテストが通る） | 全テストが通る | 単体 1,547・結合 707、失敗 0・飛ばし 0 | test-results.md 1節 | build-and-test | Met |
| NFR1 | requirements.md NFR1 | 救済と作成の経路でメールアドレス・パスワード・トークンが出ない | テストで 0、使い捨ての環境のログでも既知の例外のほか 0 | InitialAdminSecretLeakIT・test-results.md 4節 | build-and-test | Met |
| NFR2 | requirements.md NFR2 | 救済の失敗の場合を含むテスト | 救済の後のトークンの拒否・設定のパスワードでのログイン・条件外・設定の不備・途中の失敗を確かめた | InitialAdminRescueIT（9 件） | build-and-test | Met |
| NFR3 | requirements.md NFR3 | 照合は起動ごとに1回、起動を目立って遅くしない | 照合1回（単体テスト）、起動 5.98 秒（前の版 5.46〜5.52 秒） | UserAccountServiceTest・test-results.md 2節 | build-and-test | Met |
| NFR4 | requirements.md NFR4 | 統合の前に verify が通る。E2E は統合の前の必須にしない | verify 通過（clean 付き）。E2E は配備の前に回した | test-results.md 1節、code-generation-questions.md D5・D7 | build-and-test | Met |
| NFR5 | requirements.md NFR5 | 下限を下回らない、一覧のパッケージに手を入れたら外す | 上の TC-COV-* のとおり。一覧のパッケージには手を入れていない | test-results.md 1節 | build-and-test | Met |
| NFR6 | requirements.md NFR6 | 二重の ERROR の直しに再現のテストを同じコミットに含める | `2087da5` に FilterExceptionErrorLogIT と application.yaml。G2 の後はテストが設定の鍵を確かめる形で、本番での効き目は R-01 の実機で確かめた | FilterExceptionErrorLogIT・test-results.md 3節 | build-and-test | Met |
| NFR7 | requirements.md NFR7・FR2.2 | ログインの p95 がすべての回で 1 秒未満 | 7〜9回目（時計を合わせた後）905.7〜915.3 ms（繰り返しの時間）、910.6〜927.8 ms（要求の時間）。1〜6回目は条件がそろわない回として外した（2・3回目は 1,013・1,073 ms） | test-results.md 2節 | build-and-test | Met |
| FR2.2a | requirements.md FR2.2a | 回ごとの条件を記録し、そろわない回は理由を書いて外す | 9回すべてを記録。1〜6回目を VM の時計のずれで外した | test-results.md 2節 | build-and-test | Met |
| FR3.1 | requirements.md FR3.1 | BUSY の L3 の各行に同じ traceId の L4 がちょうど1行 | 2行の L3 にそれぞれ1行 | UserAdminBusyLogTraceIT | build-and-test | Met |
| FR4.2 | requirements.md FR4.2 | 1つの要求の1つの例外について ERROR が1行だけ | テストで1行。実機で同じ traceId の重なり 0・Tomcat の ERROR 0 | FilterExceptionErrorLogIT・test-results.md 3節 | build-and-test | Met |
| R-01 | Code Generation のレビュー（code-generation の review 記録）、この段の Q5: A | 本番と同じ Tomcat が1つの環境で設定が効く | Tomcat の ERROR 0 行（前の Intent は 239 行） | test-results.md 3節 | build-and-test | Met |
| FR6.1 | requirements.md FR6.1 | team.md の数を今の 7 個に直す | 「2026-10-04 の時点で 7 パッケージ（7つの名前）」に直した | aidlc/spaces/default/memory/team.md の Testing Posture | build-and-test | Met |
| FR7.2・FR7.4 | requirements.md FR7.2・FR7.4 | 5つ（と D4: A の同じイメージの出現）にダイジェスト、index に arm64・amd64 | 20 か所、どの index も両方を含む。ダイジェスト付きの FROM でイメージを作れて healthy | code-summary.md、test-results.md 1節 | build-and-test | Met |
| FR1-ALL | requirements.md FR1.1〜FR1.8 の判定 | 救済・作成・監査の行の形・全部か無しか・WARN | 結合テストで確かめ、使い捨ての環境でも作成の監査の行の形を確かめた | InitialAdminIT・InitialAdminRescueIT・test-results.md 4節 | build-and-test | Met |

## 4. 準備の状態

- ビルド: 可（`develop` の `b084c07`）。テスト: 可。配備: 可（配備の前の確かめ（FR8.2）と E2E は配備の段で行う）。

## 5. 既知の点と引き継ぎ

- k6 の要求の時間（`http_req_duration`）は、colima の VM の時計のずれで一部が崩れる。`loginSuccess` の判定は繰り返しの時間を使った。ほかの場面（1回に複数の要求）では繰り返しの時間を使えないため、時計のずれを防ぐ手立ては次に負荷を測るときの課題として残す。
- SpotBugs の priority 2 の新しい警告3件は、基準に当たらないため直していない（code-summary.md の 5節）。
- Code Generation のレビューの R-02〜R-05 は、Code Generation の承認で受け入れた（R-05 の E2E の件数の確かめは配備の前に行う）。
- `develop` の CI の確かめ（Q6: B）、配備の前の E2E（Q3: B）、配備の前の初期管理者の状態の確かめ（FR8.2）は後の段へ。
