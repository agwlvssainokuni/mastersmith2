# Code Generation Plan — U2 DSL の書式の版 2（dsl-v2）

- 単位: U2 dsl-v2（kind: library、大きさ L。画面 `features/dsl` の小さな変更を含む）
- Bolt: B1（承認済みの Bolt の計画の番号）。依頼者の決定で cross-cutting の B2 と順を入れ替え、B2 の後に作る（B2 は `develop` へ統合済み: 65a76c5）。番号は振り直さない（B2 の計画の Q-A: A と同じ扱い）。
- 範囲と量: スコープ classic、Test Strategy Standard（部品ごとに 5〜8 件、要所の結合テスト）、方法は test-after（10節の後の Testing Contract）。
- この計画は Part 1（計画）だけで、計画の承認の前にコード・テスト・設定を書かない。計画の承認の前に確かめた依頼者の答えは 10節（Q1〜Q5、どれも A）。

## 1. 入力にした設計

| 段 | 文書（どれも `aidlc/spaces/default/intents/261004-role-menu/` の下） | 使う所 |
|---|---|---|
| Functional Design | `construction/dsl-v2/functional-design/functional-spec.md`（1〜10節）・`rules.md`（BR1.1〜BR8.2）・`entities.md`・`frontend-components.md`（F1〜F6）・`functional-design-questions.md`（Q1〜Q7 すべて A と「設計で決める細部」） | 作るものの形と決まり |
| NFR Requirements | `construction/dsl-v2/nfr-requirements/security-requirements.md`（NFR1.5・NFR1.7〜NFR1.14・NFR5.3〜NFR5.5、承認の場の直しの ID の振り替え）・`tech-stack-decisions.md`（NFR2.5〜NFR2.9・NFR3.3・NFR4.1・NFR4.3・NFR6.1・NFR6.4〜NFR6.7） | 確かめの要件、新しい依存を足さないこと |
| NFR Design | `construction/dsl-v2/nfr-design/security-design.md`（3節の試し T1〜T4、4.1〜4.9、5節の差、6節の承認の場の直し R-01）・`logical-components.md`（2〜8節）・`nfr-design-questions.md`（Q1: A・Q2: A） | 部品の置き場、上限を2か所に渡す、読めなかった版の ID の置き場、ヒープの見積もり |
| Infrastructure Design | `construction/dsl-v2/infrastructure-design/cicd-pipeline.md`（1〜12節）・`infrastructure-design-questions.md`（Q1: A） | ビルドの JSON Schema の名指しの直し、負荷の道具の版 2 と同時の投入の口、E2E、統合 |
| 共通の設定（他の単位の基盤の設計） | `construction/role/infrastructure-design/infrastructure-specification.md`（2節・5節の `server.tomcat.max-swallow-size`）・`cicd-pipeline.md`（6節） | B1 で足す共通の設定 |
| Inception | `inception/units-generation/unit-of-work.md`（U2）・`unit-of-work-story-map.md`・`unit-of-work-dependency.md`、`inception/requirements-analysis/requirements.md`（FR2.3・FR7.4・FR9.4・C2・C6・NFR1.5）、`inception/user-stories/stories.md`（US5.2・US6.1、土台として AC3.1.7・AC3.1.13）、`inception/contract-design/contract-summary.md`（C3）、`inception/domain-design/components.md`（DslDefinition・DslManagement・DslAdminUi）・`decisions.md`（ADR-004〜ADR-007）、`inception/delivery-planning/bolt-plan.md`（B1） | 範囲・ID・終わりの条件 |
| 先に作った B2 の記録（読み取りだけ） | `construction/cross-cutting/code-generation/code-generation-plan.md`・`code-summary.md` | 計画の形、`DslAdminController` の印（B2 で付いた） |
| 読み直しの記録 | `.aidlc-reviews/functional-design/units/dsl-v2/3e4e36495e58fb8d/1.json`・`.aidlc-reviews/nfr-requirements/units/dsl-v2/0921d0fe911cb972/1.json`・`.aidlc-reviews/nfr-design/units/dsl-v2/a0741e66bbcee445/1.json`・`.aidlc-reviews/infrastructure-design/units/dsl-v2/6c988a88edfc1ecc/1.json` | 2.2 の残った指摘の扱い |
| 監査ログ | `audit/sakura-local-4e42a93f87ce.md` の GATE_APPROVED・GATE_REJECTED（Functional Design・NFR Requirements・NFR Design・Infrastructure Design） | 2.1 |
| 既存のコード（読み取りだけで確かめた） | `backend/src/main/java/cherry/mastersmith/dsl/**`（52 ファイル）・`dslmanage/**`（49 ファイル）、`backend/src/test/java/cherry/mastersmith/dsl/**`・`dslmanage/**`、`backend/src/test/resources/cherry/mastersmith/dsl/valid-sample.yaml`・`dslmanage/generate/dept_mst.yaml`、`backend/build.gradle.kts`（`packagesJudgedByTotal` 7 パッケージ・`dslSchemaSource`・`verifyDslSchemaInWar`・テストの JVM 1g）、`backend/src/main/resources/application.yaml`（`server:` に Tomcat の設定が無い）、`frontend/src/features/dsl/**`・`frontend/src/shared/api-client/apiClient.download.test.ts`、`frontend/e2e/040-dsl-admin.e2e.ts`（E2E は 13 ファイル）、`perf/**`、README | 影響の範囲 |

## 2. 承認の場の決定と、この計画での読み方

### 2.1 承認の場の決定（監査ログの GATE_APPROVED・GATE_REJECTED と各文書の「承認の場の決定」の節から洗い出したもの）

| 段（日付） | 監査ログの User Input・Feedback | この単位に当たる中身 | この計画での扱い | 手順 |
|---|---|---|---|---|
| Delivery Planning（2026-10-04） | Approve | B1 の終わりの条件（版 2 の投入・プレビュー・適用・ダウンロード、版 1 は 4xx、既定の DSL の生成が版 2、深さの上限の境界と起動時の扱い、版 1 の環境の起動と復元の 4xx、安全な YAML の読み込みの口と境界テスト、既存の DSL のテストを版 2 で、`verify`）と「見せるもの」 | そのまま終わりの条件にする。「見せるもの」は Step 27 の E2E（040）と Step 24 の短い試走で示す | 全体 |
| Functional Design（2026-10-05） | GATE_REJECTED「推奨の案のとおり直す」→ Approve | この単位の成果物は直していない（2回目の読み直しで「前回の判定から変わっていない」と確かめられた）。Minor・Suggestion R-01〜R-08 は Unresolved のまま承認 | 2.2 で手当てするものと先送りするものを分けた | 2.2 |
| NFR Requirements（2026-10-05） | GATE_REJECTED「Major 11 件だけを直す」→ Approve | R-01（上流の枝番と同じ番号は同じ意味）・R-02（traceability の行を枝番ごとに）の直しで ID が振り替わった（NFR1.7〜NFR1.14・NFR2.5〜NFR2.9・NFR3.3・NFR5.3〜NFR5.5・NFR6.5〜NFR6.7） | この計画と traceability.json は振り替えた後の ID だけを使う | 7節・Step 25 |
| NFR Design（2026-10-06） | GATE_REJECTED「推奨の案のとおり直す」→ Approve | R-01: 10 MiB の読み込みのヒープ（T4: 1件 256〜384 MB）と同時の投入の扱い（既存の `DslHeavyOperationGate` で1つ、受け入れた制約 (a) 適用の前の読み直し・(b) role の `RoleTransferSlot` との重なり）。確かめは Build and Test で本番と同じヒープの使い捨ての環境 | 新しい排他は足さない。Build and Test が流す道具（5.2 の同時の投入の口）を B1 で作る | Step 20・24 |
| Infrastructure Design（2026-10-06） | Approve（まとめの確認は Looks correct。承認のコミット c97f760「Major 5 件の直し方は承認の場の決定としてコード生成の計画へ」） | 決定 (1): dsl-v2 の読み直しの R-01（503 `DSL_BUSY` が 10 MiB の本文の送り手に届かない回がありうる）は、role の Q1: A の `server.tomcat.max-swallow-size`（11MB、B1 で共通に足す）で解き、B1 の短い試走で 503 が届くことを確かめる。順の入れ替えの後もこの Bolt で足す（依頼者の決定、指揮役から伝達）。あわせて dsl-v2 の Q1: A（同時の投入の口を `perf/dsl-timing.sh` に足す） | `application.yaml` に `server.tomcat.max-swallow-size: 11MB` を足す（Step 14）。短い試走（Step 24）で、成功1つと 503 `DSL_BUSY` 1つの状態コードが送り手に届くことを確かめる。role の B6 は同じ設定を前提に結合テストを足す | Step 14・20・24 |
| Code Generation の始め（依頼者の決定、指揮役から伝達） | cross-cutting（B2）を先に作り、dsl-v2（B1）を後にする | dsl-v2 の設計の「API の分類の印は U1 が B2 で付ける（この単位の後）」は「前」に変わった。B2 で `dslmanage/web/DslAdminController.java` に `@ApiAccess(ADMIN)` が付いた（B2 の `code-summary.md` 1.1）。B1 は DSL の API の道と数を変えないため、印を保つだけ（外すと `ApiAccessArchitectureTest` で落ちる）。PUBLIC の口を足さないため `PublicApiInventory` も変えない。`/dsl/dsl-schema-v2.json` は `/api/**` の外の静的なファイルで、印と一覧の対象外 | 4.8 の「変えないもの」。Step 16 で B2 の検査（`ApiAccessArchitectureTest`・`ApiAccessConsistencyIT`）が通ることを確かめる | Step 16・26 |

### 2.2 読み直しで残った指摘の扱い（承認の場で直していないもの）

| 読み直し | ID | 中身 | この計画での扱い | 手順 |
|---|---|---|---|---|
| Functional Design | R-01 | 新しい警告 `SCHEMA_MISMATCH` を「照合できなかった」（`NOT_COMPARED_KINDS`）と「食い違い N 件」のどちらに数えるかが決まっていない | **この Bolt で手当てする**（10節 Q1: A）。「照合できなかった」（`NOT_COMPARED_KINDS`）に入れ、`DslPreviewPanel` の食い違いの件数と `useDslAdmin` の適用の確かめの件数に数えない。`DslPreviewPanel.test.tsx`・`DslWarningList.test.tsx` に境界を足す | Step 17・18 |
| Functional Design | R-02 | 適用の前の読み直しが、要求の `previewId` と今のプレビューの一致より先に本文を読みうる。確定の後に履歴の本文を読み直す経路（今の `DslLifecycle.apply` の `readValid`）が 500 になりうる | **この Bolt で手当てする**（9節 D-4）。順を「今のプレビューの識別が `previewId` と一致するときだけ本文を読み直す。一致しないときは確かめず `store.apply` に任せて従来どおり 409 `DSL_PREVIEW_CHANGED`」にし、確定の後は事前に得たモデル（キャッシュか事前の読み直し）を引き回して、履歴の本文を読み直さない | Step 12・15 |
| Functional Design | R-03 | `functional-spec.md` 8節の書き換えの一覧が、DSL の形を持つテストを一部落としている | **この Bolt で手当てする**。計画の時点で `tables:`・`menus:`・`version: 1`・`DslModel(`・`DslMenuItem(` を検索して洗い出し、4.4・4.6 に並べた。Step 2 で同じ検索を流し直し、増減があれば記録する | Step 2 |
| Functional Design | R-04 | 実際のブラウザの axe（誤りを出した状態、ブランドカラーとテーマのすべての組、画面の幅）が書かれていない（`project.md` の学び 2026-09-28） | **この Bolt で手当てする**（10節 Q2: A）。DSL の画面の検査の E2E `135-dsl-admin-accessibility.e2e.ts` を1つ足し、API の答えを見本で差し替えて F2・F3・F5 の状態を、ブランドカラーとテーマのすべての組・3つの幅で axe とはみ出しにかける。操作の流れではないため E2E の本数に数えない（`project.md` の学び）。番号は 9節 D-13 | Step 19・27 |
| Functional Design | R-05 | 読めないプレビューの状態 `previewInvalid` の優先と消す契機、適用の 422 のときの `preview` の扱い、適用の確かめの件数にスキーマの増減が出ない理由 | **この Bolt で手当てする**（9節 D-6）。`previewInvalid` は `preview` より優先し、投入・生成・復元の成功・破棄・プレビューの読み込みの成功で消す。適用の 422 では `preview` を null に落とし `previewInvalid` に誤りの一覧を入れる。適用の前の確かめの件数にスキーマの増減も出す（10節 Q5: A、9節 D-15） | Step 17・18 |
| Functional Design | R-06 | 「`role="alert"` のため一度読み上げられる」は保証ではない | **この Bolt で手当てする**。読み上げを前提にせず、テストは見える文字（題・本文）と `role` の有無だけを確かめる | Step 18 |
| Functional Design | R-07 | 起動時に深すぎる枝を落としても管理者の画面に出ない（AC5.2.3 の範囲内） | **先送り**。後の Intent の候補として `code-summary.md` の「後の Intent に回すこと」に書く | Step 25 |
| Functional Design | R-08 | traceability の AC6.1.8 の Deferred の target が段、BR1.5 の N/A の理由 | **この Bolt で手当てする**。コード生成の traceability.json で AC6.1.8 の target を「U2 の配備の段への引き継ぎ（ADR-005 の影響、deployment-pipeline・deployment-execution）」にし、BR1.5 は確かめるテストを target にして OK にする | Step 25 |
| NFR Requirements | R-03〜R-09 | 試算の式・道具の持ち主・入力の型・別名の爆発・配備への引き継ぎ・画面の道具の版・対応表の表記 | NFR 設計（4.1・4.2・5節、`logical-components.md` 4・6・7節）で手当て済み。そのとおりに作る | Step 4〜5・20 |
| NFR Design | R-02 | T3 の値に冷えた状態の1回目が別に書かれていない | **先送り**（Build and Test）。`perf/dsl-timing.sh` の既存の「first」と「second」の測りがそのまま1回目を残すことを、Build and Test に引き継ぐ | — |
| NFR Design | R-03 | 戻す前の破棄ができないときの代わりの手順と、前の版で版 2 の履歴の行を復元しないこと | **先送り**（deployment-pipeline・deployment-execution）。「配備の段へ引き継ぐこと」に書く | — |
| NFR Design | R-04 | NFR2.8（英語の文言）の確かめる中身が無い | **この Bolt で手当てする**。`perf/ui/dsl-ui-lang.mjs` を版 2 に直すとき、版の誤り・深さの誤り・`SCHEMA_MISMATCH` の英語の文が届き、画面と応答に日本語の文字が無いことを判定する項目にする | Step 20 |
| NFR Design | R-05 | 深さと別名の上限ちょうど（N）と N+1 で、`LoaderOptions` と `LimitingParser` が同じ側で止まるかの境界のテスト | **この Bolt で手当てする**。`SafeYamlReaderTest` に、深さ・別名のそれぞれで N は読め、N+1 は区分と位置つきで拒否されること（`SYNTAX`・位置なしに写らないこと）を足す | Step 5 |
| NFR Design | R-06 | 読めなかった版の ID の置き場を書き換えるのが `DslStartupLoader` だけであることと、Spring の部品としての渡し方 | **この Bolt で手当てする**。`UnreadableAppliedRevision` を `dslmanage.service` のパッケージの中だけで使える `@Component`（書き換えの口はパッケージの中だけ）にし、`DslStartupLoader` だけが `remember`・`clear` を呼び、`DslLifecycle` は判定だけを使うことを Javadoc に書く | Step 12 |
| NFR Design | R-07 | OutOfMemoryError は Error で 500 の整形を保証しない、アプリ全体に響きうる、(a)(b) の重なりの合計の見積もり | **一部この Bolt**（`code-summary.md` の既知の制約に、OOM は 500 の整形の保証が無くアプリ全体に響きうることと、(b) の合計 約 490〜710 MB の見積もりを書く）。重ねる確かめは **先送り**（Build and Test の任意の項目） | Step 25 |
| NFR Design | R-08 | ヒープの実測（`jvm.memory.used`）を記録する指定が無い | **この Bolt で手当てする**（10節 Q4: A）。`perf/dsl-timing.sh` が使い捨ての環境のアプリにだけ指標を公開し、10 MiB の投入・表示と同時の投入の後のヒープの使用（`jvm.memory.used` の heap）を記録する（9節 D-14） | Step 20 |
| Infrastructure Design | R-01 | 503 を返す側の 10 MiB の本文が読まれず、Tomcat の既定の読み捨て 2 MB を超えると送り手に応答が届かない回がありうる | **この Bolt で手当てする**（2.1 の決定 (1)）。`server.tomcat.max-swallow-size: 11MB` を足し、`--busy` は curl の終了コードと状態コードを両方記録する。短い試走（Step 24）で 503 が届くことを確かめる。届かなかった回があれば、終了コード・アプリのログの `DSL_BUSY` の指標（`recordBusy`）・OOMKilled の有無を並べて記録し、止めて依頼者に諮る | Step 14・20・24 |
| Infrastructure Design | R-02 | 「決めた回数の内でやり直す」の回数と、尽きたときの扱い | **この Bolt で決める**（9節 D-9）。重ならなかった回（2つとも成功）は最大 5 回までやり直す。5 回とも重ならなければ不合格にせず、記録して依頼者に諮る | Step 20 |
| Infrastructure Design | R-03 | `--busy` の `memory.peak` の比べ先は1件の投入の値で、2件分のヒープを測るものではない | **この Bolt で手当てする**。`perf/README.md` の `--busy` の説明に書く | Step 20 |
| Infrastructure Design | R-04 | `perf/k6/scenarios.js` に DSL の本文は無く、渡す DSL（`DSL_FILE`）と README の手順を版 2 にするだけ | **この Bolt で手当てする**。`scenarios.js` はコードを変えない見込みとし、Step 20 で `version` の文字列が無いことを確かめる。渡す DSL を `make-large-dsl.mjs` の版 2 の出力にし、`perf/README.md` を直す | Step 20 |
| Infrastructure Design | R-05 | 正本のファイルの中身の版の記述（`dsl-schema-v1.json` の title「書式の版 1」）と、README の取り残しを機械で見つける手段 | **この Bolt で手当てする**。正本を `git mv` で `dsl-schema-v2.json` に改め、title と中身を版 2 にする。Step 23 で `dsl-schema-v1` の検索が 0 件（`aidlc/` と `vendor/` を除く）であることを記録する | Step 6・23 |
| Infrastructure Design | R-06 | 基盤の traceability.json の OK と N/A の基準の不揃い | **この Bolt で手当てする**。コード生成の traceability.json では、確かめるテストを持つ要件はすべて OK にし、target にテストのファイルを書く | Step 25 |
| Infrastructure Design | R-07 | `--busy` は既存の `measure` を流用できない（1回ごとにログインし、共通の応答のファイルを書き直す） | **この Bolt で手当てする**。ログインを先に1回行い、要求ごとに別の応答のファイルを使う小さな関数を `measure` と別に作る | Step 20 |

### 2.3 この計画での読み方

- **設計の文書どおりに作るもの**: 版 2 の書式とモデル（`entities.md`、BR1.1〜BR1.7）、深さの上限と起動時の枝の落とし方（BR2.1〜BR2.3、`security-design.md` 4.5）、版を上げた後の扱い（BR3.1〜BR3.6、4.4・4.8）、既定の DSL の生成（BR4.1〜BR4.4）、照合・違い・要約（BR5.1〜BR5.3）、安全な読み込みの口（BR6.1〜BR6.6、4.1・4.2）、画面（F1〜F6、BR7.1〜BR7.5）、提供口（BR8.1・BR8.2）、JSON Schema の v2 への替え（`cicd-pipeline.md` 3節）、負荷の道具（5節）。
- **層の順（test-after）**: Testing Contract の `ordering` のとおり、層ごとに実装を書き、同じ Bolt の中でその層のテストを書いて流し、通ってから次の層へ進む。この単位の層は「読み込みの部品と安全な読み込みの口（dsl.parse・dsl.service）→ 版 2 の書式・モデル・検証（dsl.domain・dsl.validate・dsl.service）→ 既定の DSL の生成（dslmanage.generate）→ 業務処理（dslmanage.service・domain）→ API（dslmanage.web と設定）→ 画面（features/dsl）→ E2E と負荷の道具」の順とする。データの形（内部DB の表）と DB アクセスの層（repository）は変えない（Testing Contract の `plan_profile` の該当しない層。`DslManageRepositoryIT` の版 2 の見本への書き換えだけ）。
- **型の変更が次の層へ及ぶこと（9節 D-1）**: `DslModel`・`DslMenuItem` の形を変えると、Gradle はテストの前に本体と全テストのソースをコンパイルするため、dsl の層のテストを流す時点で dslmanage の本体とテストもコンパイルが通っている必要がある。そのため Step 8 で、dslmanage の本体とテストを **型だけ追従** させる（振る舞いは版 1 の写しのまま置き換え、`TODO(B1 Step 10/12/14)` の印と理由を付ける）。dslmanage の振る舞いの作り直しとテストの実行はそれぞれの層の Step で行い、Step 16 までに `TODO(B1` が 0 件であることを検索で確かめる。dslmanage のテストは、その層の Step まで流さない（dsl の層の Step では `cherry.mastersmith.dsl.*` だけを流す）。
- **守りの実装は1か所のまま**: SnakeYAML を使うのは `dsl.parse` だけ（`DslBoundaryArchitectureTest` の `noOtherYamlReaders`・`noObjectConstruction`）。`SafeYamlReader`（`dsl.service`）は `dsl.parse` の部品を包み、`dsl.parse` の型を外へ出さない（`parseAndValidateStayInside`）。
- **内部DB の表・Flyway の移行・API の道と数・依存・lockfile・CI・コンテナの設定は変えない**（4.8）。足す設定は `application.yaml` の `server.tomcat.max-swallow-size` の1行だけ。

## 3. Bolt・ブランチ・統合・コミットの区切り

| 項目 | 扱い | 出典 |
|---|---|---|
| Bolt の番号 | 承認済みの番号 B1 のまま使い、実行の順を入れ替えたこと（B2 → B1 → B3 …）を記録する | 依頼者の決定、`bolt-plan.md` |
| 作業ブランチ | `develop` の先頭（計画の時点で 65a76c5）から `feature/261004-role-menu-b1`。同じ作業フォルダで作り、worktree は使わない。作るのは依頼者の承認の後 | `team.md` の Way of Working、`cicd-pipeline.md` 8節 |
| 統合の前の関門 | colima を起動し、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡して `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（対象DB の3種類のテストを飛ばさない。SKIPPED が出たら統合しない）。続けて E2E の全体（`docker compose --profile mail up -d mailpit` の後に `./gradlew e2eTest`）。どちらも通るまで統合しない | `team.md`・`project.md` の学び、`cicd-pipeline.md` 2・7節 |
| 統合の形 | `develop` への squash（1 Bolt が `develop` の1コミット）。サブモジュールの更新を含まないため fast-forward の例外に当たらない。単位は1つのため単位ごとの squash の選択は無い | `team.md` |
| 統合コミットの件名（案） | `B1 DSL の書式の版 2: スキーマの階層・メニューの深さの上限・安全な YAML の読み込みの口・版 2 の生成と画面`（日本語） | `team.md` |
| 統合の後 | 作業ブランチを消す。`origin` への push は依頼者が行う。push の後の CI の失敗は `team.md` の「不安定なテストと CI の失敗」の決まりで扱う | `team.md` |
| 作業ブランチの上のコミットの区切り（案） | C1 バックエンド: 読み込みの部品の上限の引数化と安全な読み込みの口（Step 4・5）／C2 バックエンド: 版 2 の書式・モデル・検証・深さと dsl のテストの書き換え、JSON Schema の v2 への替え（Step 6〜9）／C3 バックエンド: 既定の DSL の生成・業務処理・API・`max-swallow-size` と dslmanage のテスト（Step 10〜16）／C4 画面: `features/dsl` とそのテスト（Step 17・18）／C5 E2E・負荷の道具・README（Step 19〜21）。C1〜C5 はそれぞれの時点でビルドと流したテストが通る区切り（Step 8 の型の追従は C2 に入り、C3 で作り直す） | `project.md` の Change Control |
| コミットの進め方 | 生成の担当はコミットしない。生成の後に指揮役が C1〜C5 の区切りを依頼者に提案し、承認を得てから行う（段の記録のコミットも別に提案する）。`aidlc/` の未コミットの変更（監査ログを含む）は squash の前にコミットしておき、`git restore` で消さない | `project.md` の Change Control の学び |
| 承認の場の時点 | コード生成の段の承認の場は、最後のコードのコミットの後、記録だけのコミット（`aidlc/` の下だけ）を積む前に開く | `project.md` の学び |

## 4. 作るもの・手を入れるもの

### 4.1 バックエンドの本体 `dsl`（`backend/src/main/java/cherry/mastersmith/dsl/`）

| ファイル | 種類 | 中身 | 出典 |
|---|---|---|---|
| `domain/DslFormat.java` | 変える | `CURRENT_VERSION = 2`、`MAX_MENU_DEPTH = 5` を足す、`SCHEMA_RESOURCE`・`SCHEMA_PUBLIC_PATH` を v2 に。上限の値（10 MiB・深さ 50・別名 100・展開後の節 1,000,000）は変えない | BR1.1・BR1.7・BR2.1・BR6.6、NFR1.7・NFR1.8 |
| `domain/DslSchema.java` | 足す | `record DslSchema(String name, DisplayName label, Map<String, DslTable> tables)`（テーブルは DSL の順を保つ、0 件を許す） | ENT DslSchemaV2、契約 C3 との差（`label`） |
| `domain/TableRef.java` | 足す | `record TableRef(String schema, String name)`（どちらも1文字以上） | ENT TableRef、BR1.6 |
| `domain/DslMenuItem.java` | 変える | `table` を `String` から `TableRef` に（テーブルか子の少なくとも一方の決まりは保つ） | BR1.6、C3 |
| `domain/DslModel.java` | 変える | `DslModel(String dslHash, int formatVersion, List<DslSchema> schemas, List<DslMenuItem> menus)`。版 1 の平らな `tables` を持たない。`findTable(TableRef)`（大文字・小文字を区別）と `schema()`（ちょうど1つのスキーマ）を足す | BR8.1・BR8.2、ENT DslModel |
| `domain/MenuDepth.java`（名前は案） | 足す | 純粋な関数: `overDepth(List<DslMenuItem>, int limit)`（上限を超えた最初の段の項目の道の一覧）と `prune(List<DslMenuItem>, int limit)`（深すぎる枝を子ごと落とし、テーブルも子も持たなくなったまとまりを上へさかのぼって落とした木と、落とした項目の数）。木を1回たどるだけ | BR2.1・BR2.3、NFR1.12・NFR2.9・NFR6.6、`security-design.md` 4.5 |
| `domain/DslMessageKeys.java` | 変える | メニューの深さ（`dsl.semantic.menuDepth`、上限の値を埋める）・スキーマの数（`dsl.semantic.schemaCount`）の鍵を足す（名前は案） | BR1.3・BR2.1 |
| `domain/DslReadResult.java` | 変える（必要なら） | 起動時の読み方の結果（`DslStartupReadResult`、名前は案。`Valid(model, prunedMenuItems)`・`Invalid(errors)`）を足す。置き場は `dsl.domain` か `dsl.service` のどちらか今の `DslReadResult` と同じ所 | `functional-spec.md` 2節 |
| `parse/SafeYamlParser.java`・`parse/LimitingParser.java`・`parse/YamlTreeConverter.java` | 変える | 上限を `DslFormat` の定数ではなく引数（`dsl.parse` の中の上限の値の型、名前は案 `YamlLimits`）で受ける。深さと別名は `LoaderOptions` と `LimitingParser` の両方に、大きさは `setCodePointLimit` に同じ値を渡す（T1'）。守りの中身（タグ・重複キー・再帰の別名・文字の誤り）は変えない | BR6.6、`security-design.md` 4.1・4.2 |
| `service/SafeYamlReader.java`・`service/DefaultSafeYamlReader.java`（名前は案） | 足す | 口と実装。`read(byte[] yamlBytes, SafeYamlLimits limits)`。大きさは読む前にバイト数で判定（ちょうどは受け付ける）。部品の拒否を6つの区分に写し、例外の文は捨てる | BR6.1〜BR6.4、NFR1.5、C3 |
| `service/SafeYamlLimits.java`・`service/SafeYamlResult.java`・`service/SafeYamlRejectionKind.java`・`service/YamlPositions.java`（名前は案） | 足す | 上限の型（作るときに範囲を確かめる: 大きさ・深さ・節は 1 以上、別名は 0 以上、外れたら `IllegalArgumentException`）、結果の sealed な型（`Parsed(tree, positions)`・`Rejected(kind, line, column, path)`）、区分、JSON Pointer から行・列を引く口（`dsl.parse.PositionMap` を包み、型は外へ出さない）。`toString` は区分・位置・節の数だけ | BR6.4・BR6.5、NFR1.11・NFR1.14 |
| `service/DslReader.java`・`service/DefaultDslReader.java` | 変える | `DslFormat` の値で上限を作って部品へ渡す。段の順は今のまま（BR6.6）。`readAtStartup(byte[])` を足す（意味の検証から深さだけを外し、モデルを作る前に `MenuDepth.prune` で枝を落とし、落とした数を添える） | BR2.2・BR2.3・BR6.6 |
| `service/DslModelMapper.java` | 変える | 版 2 の木（`schemas` → スキーマ → `tables`、メニューの `table` の組）からモデルを作る | BR1.2・BR1.6 |
| `validate/DslSchemaValidator.java` | 変える（読む資源の名前だけ） | `DslFormat.SCHEMA_RESOURCE`（v2）を読む。外部の資源を取りに行かない設定は変えない | BR1.7、NFR1.9 |
| `validate/DslSemanticValidator.java` | 変える | 段の順（BR1.3 スキーマの数 → BR1.5 外部キーと選択肢の参照を同じスキーマの中で → BR1.6 メニューの組 → BR2.1 深さ → 既存のテーブル・カラムの決まり）。深さの確かめを入れるかを引数で受ける。場所は `schemas.<スキーマ>.tables.<テーブル>…`・`menus…/table` の道 | BR1.3・BR1.5・BR1.6・BR2.1・BR2.2 |
| `validate/DslErrors.java` | 変える（必要なら） | 深さ・スキーマの数の誤りを作る口 | 同上 |

### 4.2 バックエンドの本体 `dslmanage`（`backend/src/main/java/cherry/mastersmith/dslmanage/`）

| ファイル | 種類 | 中身 | 出典 |
|---|---|---|---|
| `generate/DslTreeBuilder.java` | 変える | `version: 2`、`schemas` に写しのスキーマ名を1つ（表示名は ja・en ともスキーマ名）、その下に今の規則でテーブル、メニューは平らに `table: {schema, name}` | BR4.1・BR4.2 |
| `generate/TargetSchemaDslGenerator.java` | 変える（必要なら） | スキーマ名を木の部品へ渡す。接続先・ポート・ユーザー名・パスワード・JDBC の URL・PostgreSQL の `database` は渡さない（今のまま写しにだけ触れる）。生成した本文を通常の読み方で確かめる（通らないのは想定外） | BR4.3・BR4.4、NFR1.13・NFR2.7 |
| `service/DslReconciler.java` | 変える | DSL のスキーマ名と写しのスキーマ名が同じときだけテーブル・カラム・型を比べる。違えば `SCHEMA_MISMATCH` を1件（文に DSL のスキーマ名だけを埋める）。場所の道を `schemas.<スキーマ>.tables.<テーブル>(.columns.<カラム>)` に | BR5.1、NFR5.5 |
| `service/DslDiffCalculator.java` | 変える | スキーマの階層の違い（スキーマを名前で突き合わせ、ADDED・REMOVED・CHANGED（表示名だけ）・UNCHANGED、その下のテーブルは既存の規則） | BR5.2 |
| `service/DslSummaryCalculator.java` | 変える | `schemaCount`、表示名の未設定にスキーマの表示名を数える（順はスキーマ・メニュー・テーブル・カラム・固定の選択肢）、メニューの木の節を `TableRef` に | BR5.3 |
| `service/DslErrorMessages.java` | 変える | `VERSION_UNSUPPORTED`・`VERSION_MISSING` の文を版 2 の案内に（ja・en）、深さ・スキーマの数の文を足す、`SCHEMA_MISMATCH` の警告の文 | BR7.1・BR2.1・BR5.1、`frontend-components.md` 4節 |
| `service/UnreadableAppliedRevision.java` | 足す | 起動時に読めなかった適用中の版の ID の置き場（メモリ）。`remember`・`clear` は `DslStartupLoader` だけが呼ぶ（パッケージの中の口）。`isUnreadable(currentRevisionId)` | BR3.6 の差（Q1: A）、`security-design.md` 4.8、NFR 設計 R-06 |
| `service/DslStartupLoader.java` | 変える | `readAtStartup` で読む。深さ以外の誤り（版 1 を含む）は既存の ERROR を1件と Absent と `remember`。通れば Present と `clear`、落とした項目が1つ以上なら WARN を1件（キー `dsl.hash`・`dsl.prunedMenuItems`・`dsl.menuDepthLimit` の3つだけ）。適用中が無ければ `clear` | BR2.3・BR3.1、NFR5.3・NFR5.4 |
| `service/DslLifecycle.java` | 変える | `showPreview`: キャッシュに無ければ読み直し、通らなければ `DSL_INVALID`（422、誤りの一覧つき）でプレビューは残す（今の 500 の経路を無くす）。`apply`: 2.2 の FD R-02 の順で確かめ、確定の後は事前に得たモデルを使う。`status`・適用の応答に `appliedUnreadable` | BR3.3・BR3.4・BR3.6、NFR3.3 |
| `domain/PreviewView.java` | 変える | `Summary` に `schemaCount`、`MenuNode.table` を `TableRef` に、`Diff` を `schemas: List<SchemaDiff>` に、`SchemaDiff` を足す、`WarningKind` に `SCHEMA_MISMATCH` | ENT PreviewSummaryV2・PreviewDiffV2・PreviewWarningKindV2 |
| `domain/DslStatus.java` | 変える | `appliedUnreadable` を足す | ENT DslStatusV2 |
| `web/DslResponses.java`（と応答の record） | 変える | `appliedUnreadable`・スキーマの階層の違い・メニューの組を応答へ写す。道・方法・状態コード・`@ApiAccess(ADMIN)`・`@HeavyDslOperation` の印は変えない | BR3.6・BR5.2・BR5.3 |

### 4.3 リソース・ビルド・設定

| ファイル | 種類 | 中身 | 出典 |
|---|---|---|---|
| `backend/src/main/resources/dsl/dsl-schema-v1.json` → `dsl-schema-v2.json` | `git mv` して中身を版 2 に | 根は `version`（const 2）・`menus`・`schemas`、スキーマ名をキーにした対応表（`propertyNames` で1文字以上）、スキーマは `label`（ja・en）と `tables`（今の形）、メニューの `table` は `{schema, name}`。title を「書式の版 2」に。外部の `$ref` を持たない | BR1.2・BR1.6・BR1.7、Infrastructure Design R-05 |
| `backend/build.gradle.kts` | 変える（名指しだけ） | `dslSchemaSource` と説明のコメント（383〜386 行）、`verifyDslSchemaInWar` の2つの道（404 行）を v2 に。`packagesJudgedByTotal`・計測の除外・タスク・テストの JVM は変えない | `cicd-pipeline.md` 3節 |
| `backend/src/main/resources/application.yaml` | 変える（1行と説明のコメント） | `server.tomcat.max-swallow-size: 11MB`（本文を読む前に断る応答 `DSL_BUSY`・413・後の `ROLE_BUSY` を、10 MiB までの本文の送り手に届けるため。読み捨てるだけでヒープに貯めない） | 2.1 の決定 (1)、role の `infrastructure-specification.md` 5節 |

### 4.4 バックエンドのテスト（書き換え・足す。`backend/src/test/java/cherry/mastersmith/` と `backend/src/test/resources/cherry/mastersmith/`）

計画の時点の検索（`tables:`・`menus:`・`version: 1`・`DslModel(`・`DslMenuItem(`・`.tables()`・`.table()`）で洗い出した。`targetdb` の下の当たり（`TargetSchema` の `tables`）は DSL ではないため対象外。

| ファイル | 種類 | 中身 |
|---|---|---|
| `resources/.../dsl/valid-sample.yaml`・`resources/.../dslmanage/generate/dept_mst.yaml` | 書き換え | 版 2 の形 |
| `dsl/testsupport/DslSamples.java`・`dslmanage/testsupport/DslYaml.java`・`dslmanage/testsupport/DslYamlHashes.java` | 書き換え | 見本の DSL を版 2 に（識別の値は本文から求め直す） |
| `dsl/parse/SafeYamlParserTest.java`・`YamlTreeConverterTest.java`・`SafeYamlParserPropertyTest.java` | 書き換え | 上限を引数で渡す形に、見本を版 2 に（上限・タグ・重複キーの確かめは今のまま通す） |
| `dsl/service/SafeYamlReaderTest.java`（名前は案） | 足す（`*Test`） | 4つの上限の「ちょうど・1つ超え」（小さな上限の値）、深さと別名は N と N+1 で区分と位置つき（NFR 設計 R-05）、別名 0（対応表を指す別名1つで拒否・文字の値を指す別名は通る）、6つの区分、例外の文が無い、上限の範囲外は例外、別名の爆発が DSL の上限と小さな上限のそれぞれで 5 秒の内に `TOO_MANY_ALIASES`、`toString` に木の中身が無い |
| `dsl/service/DefaultDslReaderTest.java` | 書き換え・足す | 版 1・版が無いは `UNSUPPORTED_VERSION` 1件、スキーマ 0・1・2、スキーマ名とテーブル名の重複キー、メニューの組の無い先、深さ 5 は受け付け・6 は拒否（位置と上限の値）、`readAtStartup` が深さだけ外し落とした数を返す、深さ以外の誤りは起動時の読み方でも Invalid、`$ref` を取りに行かない（`CountingHttpServer`） |
| `dsl/validate/DslSchemaValidatorTest.java`・`DslSemanticValidatorTest.java`・`ValidationTestSupport.java` | 書き換え・足す | 版 2 の構文、同じスキーマの中の外部キー・選択肢の参照、場所の道 |
| `dsl/domain/DslModelTest.java`・`DslResultTypesTest.java` | 書き換え・足す | 版 2 のモデルの形、`findTable` の当たり・外れ・大文字と小文字、`TableRef`・`DslSchema` の必須の値 |
| `dsl/domain/MenuDepthTest.java`・`MenuDepthPropertyTest.java`（名前は案） | 足す | 例での深さの数え方（まとまりかテーブルかによらず1段）と枝の落とし方（さかのぼりを含む）。jqwik の性質: 落とした木は深さ 5 以下、残った項目の親子と並びは元と同じ、テーブルも子も持たないまとまりが無い、落とした数＋残った数＝元の数、上限内の木は変わらない。名前は `*PropertyTest`（Gradle の `*Test` に当たる） |
| `dsl/service/ActiveDslModelStoreTest.java` | 書き換え | 版 2 のモデル |
| `dsl/DslSchemaPublicationIT.java` | 書き換え | `static/dsl/dsl-schema-v2.json` |
| `dsl/service/SafeYamlReaderSecretLeakIT.java`（名前は案） | 足す（`*IT`） | TRACE を有効にして、目印の文字列を含む YAML を読ませ、入力と結果のどちらもアプリのログに目印が出ない（既存の `*SecretLeakIT` と同じ形） |
| `dslmanage/generate/DslTreeBuilderTest.java`・`TargetSchemaDslGeneratorTest.java`・`DslYamlWriterTest.java`・`DslGenerationPropertyTest.java` | 書き換え | 版 2・スキーマ名を書く・メニューの組。「スキーマ名を含まない」の確かめは「含む」に、接続の項目を含まないことは残す |
| `dslmanage/generate/AbstractDefaultDslGeneratorIT.java`（MySQL・MariaDB・PostgreSQL の3つ） | 書き換え | 生成した版 2 の DSL がそのまま検証を通る、スキーマ名を含み接続の項目を含まない、想定の規模の大きさの記録は Build and Test |
| `dslmanage/service/DslReconcilerTest.java`・`DslSummaryAndDiffTest.java`・`DslStartupLoaderTest.java`・`DslLifecycleTest.java` | 書き換え・足す | 4.2 の振る舞い（`SCHEMA_MISMATCH` の文に設定のスキーマ名が無い、スキーマの区分、`schemaCount`、起動時の WARN・ERROR と `remember`・`clear`、読めないプレビューの 422、適用の順） |
| `dslmanage/service/UnreadableAppliedRevisionTest.java` | 足す | 覚えた ID と同じときだけ真、別の ID・null・`clear` の後は偽 |
| `dslmanage/domain/DslManageDomainTest.java` | 書き換え | 版 2 |
| `dslmanage/service/DslStartupIT.java` | 書き換え・足す | 版 1 の適用中の DSL で起動すると Absent・ERROR 1件（本文なし）・ヘルスチェック UP・`appliedUnreadable` 真。深さ 6 の版 2 の適用中で起動すると Present（深すぎる枝が無い）・WARN 1件（キー3つ、目印の表示名・テーブル名がログに無い） |
| `dslmanage/web/DslAdminApiIT.java` | 書き換え・足す | 版 2 の投入・プレビュー・適用・ダウンロード・履歴。版 1 と深さ 6 とスキーマ 0・2 の投入と復元は 422（応答に部品の例外のクラス名と文が無い、適用中・プレビュー・履歴を読み直して変わらない）、深さ 5 は受け付け。読めないプレビュー（内部DB に版 1 の本文を直接置く）の表示と適用は 422 で、内部DB が変わらない（読み直して確かめる）。版 1・深すぎる本文のダウンロードは本文をそのまま返す。適用の `previewId` の不一致は 409 が先 |
| `dslmanage/repository/DslManageRepositoryIT.java`・`dslmanage/web/DslTargetDbIT.java`・`DslConcurrencyIT.java`・`DslAccessControlIT.java`・`DslAuditWriteFailureIT.java` | 書き換え（見本が版 2 になる分だけ） | 振る舞いの確かめは変えない。`DslTargetDbIT` は3種類の DB でスキーマ名を書き接続の項目を書かないこと、名前の違うスキーマの照合が `SCHEMA_MISMATCH` 1件 |

- 既存の境界テスト（`DslBoundaryArchitectureTest`・`DslManageBoundaryArchitectureTest`・`DslManageGenerateBoundaryArchitectureTest`・`ArchitectureTest`・B2 の `ApiAccessArchitectureTest`）は変えない。
- テストの手伝いは各機能の `testsupport` に置く（`dsl/testsupport`・`dslmanage/testsupport`）。

### 4.5 画面の本体（`frontend/src/features/dsl/`）

| ファイル | 種類 | 中身 | 出典 |
|---|---|---|---|
| `api/types.ts` | 変える | `DslStatus.appliedUnreadable`、`MenuNode.table` を `{ schema; name } \| null`、`PreviewSummary.schemaCount`、`DslDiff.schemas: SchemaDiff[]`、`WarningKind` に `'SCHEMA_MISMATCH'` | `frontend-components.md` 2節 |
| `DslStatusPanel.tsx` | 変える | `appliedUnreadable` のとき `Alert variant="warning"`（閉じるボタンなし）。ダウンロードは残す | F2、BR7.2 |
| `useDslAdmin.ts` | 変える | `previewInvalid: DslErrorReport \| null`（`DSL_INVALID` のプレビューの読み込みと適用で入れ、2.2 の FD R-05 の契機で消す。`preview` より優先）。適用の確かめの件数に `SCHEMA_MISMATCH` を数えない（10節 Q1: A）。適用の確かめにスキーマの増減の件数（増えた・減った・変わった（表示名））を渡す（Q5: A） | F3、BR7.3、9節 D-15 |
| `DslPreviewPanel.tsx` | 変える | `invalidReport` を受け、`Alert variant="danger"` と `DslErrorList` を出し、操作はダウンロードと破棄だけ（適用は出さない）。要約に「スキーマ {count}」。食い違いの件数に `SCHEMA_MISMATCH` を数えず「照合できなかった」の表示にする（Q1: A） | F3・F6、BR7.3・BR7.5 |
| `DslWarningList.tsx` | 変える | `NOT_COMPARED_KINDS` に `SCHEMA_MISMATCH` を足す（Q1: A） | FD R-01 |
| `diffCounts.ts` の `countSchemaChanges`（名前は案）・`DslConfirmDialog.tsx` | 変える | 違いの `schemas` の区分を数える関数を足し、適用の確かめの区画にスキーマの件数の行（「スキーマ 増 {added}・減 {removed}・変 {changed}」）をテーブルの件数の前に出す。`DslConfirm` の `apply` に `schemas: ChangeCounts` を足す（Q5: A） | 9節 D-15 |
| `DslMenuTree.tsx` | 変える | テーブルを指す節は「（スキーマ名.テーブル名）」（文字の差し込みだけ） | F4、BR7.4 |
| `DslDiffTable.tsx`・`diffCounts.ts` | 変える | スキーマごとの `tbody` と見出しの行（`th scope="rowgroup"`、全列、区分の `Badge`）、開閉の鍵と `data-testid` を `{schema}/{table}`、「すべて表示」の切り替えの決まり、件数はすべてのスキーマのテーブルの合計 | F5、BR7.5 |
| `messages.ts` | 変える | `frontend-components.md` 4節の7つの鍵（ja・en）と `dsl.diff.tableLabel` の直し。適用の確かめのスキーマの件数の鍵 `dsl.confirm.schemaCounts`（名前は案、ja「スキーマ 増 {{added}}・減 {{removed}}・変 {{changed}}」、en「Schemas: +{{added}} / -{{removed}} / changed {{changed}}」、文は既存の `dsl.confirm.tableCounts` にそろえる）を足す（Q5: A） | NFR4.3、9節 D-15 |
| `submitInput.ts` | 変える | `DSL_SCHEMA_PATH = '/dsl/dsl-schema-v2.json'` | F6、BR1.7 |

### 4.6 画面のテスト（対象と同じ場所の `*.test.ts(x)`）

| ファイル | 種類 | 中身 |
|---|---|---|
| `testing/fixtures.ts` | 書き換え | 版 2 の見本（`schemas` の違い・`schemaCount`・メニューの組・`appliedUnreadable`） |
| `DslStatusPanel.test.tsx` | 足す | 注意の有無（真偽）、注意があってもダウンロードできる、axe（注意あり） |
| `DslPreviewPanel.test.tsx` | 足す | 読めないプレビュー（誤りの一覧・案内・ダウンロードと破棄・適用が無い）、スキーマの数、`SCHEMA_MISMATCH` だけのときは「照合できなかった」で食い違いの件数が 0、`SCHEMA_MISMATCH` とほかの警告が並んでも食い違いの件数に入らない（Q1: A）、axe（読めないプレビュー） |
| `DslAdminPage.test.tsx` | 書き換え・足す | プレビューの読み込みと適用が `DSL_INVALID` のときの状態、破棄で「プレビューはありません」に戻る、投入の成功で `previewInvalid` が消える（`waitFor` で待つ） |
| `DslMenuTree.test.tsx` | 足す | 「スキーマ名.テーブル名」、名前に `<`・`>`・`&`・`"`・`'` を含めても文字として出る |
| `DslDiffTable.test.tsx` | 足す | 見出しの行と区分、2つのスキーマに同じ名前のテーブルがあっても開閉が混ざらない、「すべて表示」の切り替え、axe（見出しの行） |
| `DslConfirmDialog.test.tsx` | 書き換え・足す | 適用の確かめにスキーマの件数の行が出る（増・減・変の値）、件数が 0 でも行が出る、日本語と英語（Q5: A） |
| `diffCounts.test.ts`（名前は案） | 足す | スキーマの区分の数え方（ADDED・REMOVED・CHANGED・UNCHANGED、適用中が無いときはすべて増えた）と、テーブルの件数がすべてのスキーマの合計であること（Q5: A） |
| `useDslAdmin` の適用の確かめ（`DslAdminPage.test.tsx` の中） | 足す | 適用の確かめにスキーマの件数が渡り、`SCHEMA_MISMATCH` は食い違いの件数に入らない（Q1: A・Q5: A） |
| `DslWarningList.test.tsx` | 書き換え・足す | `SCHEMA_MISMATCH` が「照合できなかった」の種類に入る（Q1: A） |
| `DslErrorList.test.tsx`・`DslHistoryTable.test.tsx` | 書き換え（見本が版 2 になる分だけ） | 振る舞いの確かめは変えない |
| `submitInput.test.ts`・`DslSubmitForm.test.tsx`・`api/dslApi.test.ts`・`api/saveFile.test.ts` | 書き換え | 版 2 の本文と v2 の道 |
| `frontend/src/shared/api-client/apiClient.download.test.ts` | 書き換え | 版 2 の本文 |

### 4.7 E2E・負荷の道具・文書

| ファイル | 種類 | 中身 | 出典 |
|---|---|---|---|
| `frontend/e2e/040-dsl-admin.e2e.ts` | 書き換え | 版 2 の DSL（貼り付け・投入・プレビュー・適用・破棄・ダウンロード・履歴）。版 1 を投入すると版の誤りの文が出ることを1か所足す。新しい流れは足さない（本数に数えない） | NFR6.7、AC6.1.2・AC6.1.9 |
| `frontend/e2e/135-dsl-admin-accessibility.e2e.ts`（10節 Q2: A。番号は 9節 D-13） | 足す | API の答えを見本で差し替え（見本は1つにして画面の側の型を付ける、`project.md` の学び）、F2・F3・F5（長いスキーマ名・表示名）の状態を、ブランドカラーとテーマのすべての組・幅 360・768・1280 で実際のブラウザの axe にかける。はみ出しも確かめる。操作の流れではないため E2E の本数に数えない（`project.md` の学び） | FD R-04、NFR4.1 |
| `perf/make-large-dsl.mjs` | 書き換え | 生成した版 2 の DSL の、スキーマの下のテーブルを写して 10 MiB 近くにする（誤りを含むものも版 2） | `cicd-pipeline.md` 5.1 |
| `perf/make-pattern-dsl.mjs`・`perf/ui/dsl-ui-lang.mjs`・`perf/ui/dsl-ui-timing.mjs` | 書き換え | 版 2 の DSL。`dsl-ui-lang.mjs` は NFR 設計 R-04 の判定（版の誤り・深さの誤り・`SCHEMA_MISMATCH` の英語の文、日本語の文字が無い） | 5.1、NFR2.8 |
| `perf/dsl-timing.sh` | 書き換え・足す | 版 2。同時の投入の口 `--busy`（名前は案、5.2 と 2.2 の Infrastructure Design R-01〜R-03・R-07、9節 D-9）。ヒープの使用（`jvm.memory.used` の heap）の記録（10節 Q4: A、9節 D-14） | 5.2、NFR2.5・NFR2.6 |
| `perf/k6/scenarios.js` | 変えない見込み | 渡す DSL（`DSL_FILE`）が版 2 になるだけ（Infrastructure Design R-04） | 5.1 |
| `perf/README.md` | 変える | 版 2 であること、`--busy` の使い方と判定（`memory.peak` の比べ先は1件の投入、R-03）、ヒープの使用の記録の読み方（Q4: A。T4 の 256〜384 MB と比べる） | 5.1・5.2 |
| `README.md` | 変える | DSL の書式の節（v2 の正本・URL・`yaml-language-server` の例、v1 の URL は無くなりエディターの補完の設定を v2 に替えること、版 2 の例、スキーマは1つ、メニューの深さ 5 段）、DSL の画面の節（v2 の道、今の状態の注意、読めないプレビュー）、版を上げたときの扱い（版 1 の適用中は無い扱い、既定の DSL を生成し直して適用） | `cicd-pipeline.md` 3節、`logical-components.md` 7節 |

### 4.8 変えないもの（Step 23 で差が無いことを確かめる）

`.github/workflows/ci.yml`・`.github/dependabot.yml`、`build.gradle.kts`（ルート）、`backend/build.gradle.kts` の `packagesJudgedByTotal`・計測の除外・タスク・テストの JVM（JSON Schema の名指し以外）、`frontend/vitest.config.ts`・`vitest.setup.ts`・`eslint.config.js`、`gradle/libs.versions.toml`・`backend/gradle.lockfile`・`frontend/package.json`・`frontend/package-lock.json`、`compose.yaml`・`docker/perf/compose.yaml`・`Dockerfile`・`.env.example`、Flyway の移行（`backend/src/main/resources/db/migration/`）、`docker/monitoring/`、`dslmanage/repository/` の本体、`dslmanage/web/DslAdminController.java`・`DslAdminPaths.java`・`DslHeavyOperationGate.java`・`HeavyDslOperation.java`（道・印・排他）、`common/web/`（`RequestSizeLimitFilter` の道ごとの上限）、`common/security/`、`common/testsupport/PublicApiInventory.java`、`vendor/`。

## 5. カバレッジの一覧と境界テスト

| 確かめ | 結果（計画の時点の読み取り） | 作業 |
|---|---|---|
| 手を入れるパッケージが `packagesJudgedByTotal`（7 パッケージ）にあるか | 手を入れる `dsl.domain`・`dsl.parse`・`dsl.service`・`dsl.validate`・`dslmanage.domain`・`dslmanage.generate`・`dslmanage.service`・`dslmanage.web` はどれも無い（`application.yaml` はパッケージではない。`common.web` には手を入れない） | 一覧の作業（下限を満たして外す）は付かない。一覧は変えない（増やさない） |
| パッケージごとの下限（行 80%・分岐 70%） | 上の8つはすでに対象 | 変更の前の基準（Step 3、B2 と同じく行う）と変更の後（Step 26 の実測）の値を並べて記録する。下がったパッケージは理由を書き、下限を割ればテストを足す（除外は足さない） |
| `dslmanage.repository` | 本体に手を入れない | 値の記録だけ（見本の書き換えの影響を見る） |
| 画面の全体の下限 | `thresholds` 行 80%・分岐 70% | `features/dsl` の足した分をテストで覆う。計測の除外を足さない |
| 境界テスト | `dsl`・`dslmanage`・`dslmanage.generate` に既にある。新しいパッケージは作らない | 足さない・変えない（緩めない）。Step 16 で流して通ることを確かめる。`SafeYamlReader` の公開の型に `dsl.parse` の型が出ないことは `parseAndValidateStayInside` で確かめる |

## 6. 手順

各 Step の終わりに、その Step で流したコマンドと結果（件数・通過）を `code-generation/generation-notes.md`（この段の記録の置き場）に書く。

### Step 1: 作業の場の用意（ブランチの作成は依頼者の承認を得てから）

- [x] `develop` の先頭のハッシュを `git rev-parse HEAD` で記録する。アプリのソースに未コミットの変更が無いことを `git status` で確かめる（ワークフローの記録 `aidlc/` は外して判断する）。
- [x] `aidlc/` の未コミットの変更（監査ログ・この段の記録）があれば、ブランチを作る前に記録のコミットを依頼者に提案し、承認を得て行う（`project.md` の学び）。
- [x] `frontend/playwright-report`・`frontend/test-results` が無いことを確かめる（あれば消す）。
- [x] 依頼者の承認を得て `develop` から `feature/261004-role-menu-b1` を作る。

### Step 2: テストの実行の準備と洗い出しの確かめ（最初のテストより前）

- [x] バックエンドの単位のコマンドが動くことを、既存のテストで確かめる: `./gradlew :backend:test --tests 'cherry.mastersmith.dsl.*'`、colima の環境変数を付けて `./gradlew :backend:integrationTest --tests cherry.mastersmith.dsl.DslSchemaPublicationIT`。
- [x] 画面の単位のコマンドが動くことを確かめる: `cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/dsl`。
- [x] 4.4・4.6 の洗い出しの検索を流し直し（`tables:`・`menus:`・`version: 1`・`DslModel(`・`DslMenuItem(`・`.tables()`・`.table()`・`testing/fixtures`）、計画の一覧との増減を記録する（FD R-03）。
- [x] 確かめたコマンドを `unit-test-instructions.md` のコマンドと照らし、違いがあれば記録する（承認済みの文書は書き換えない）。

### Step 3: 変更の前の基準

- [x] colima の環境変数を付けて `./gradlew :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport` を流し、`backend/build/reports/jacoco/test/jacocoTestReport.xml` から 5節の9パッケージの行・分岐の値と、テストの件数を記録する（`project.md` の学び）。
- [x] `dsl-schema-v1` の検索の結果（計画の時点で6か所と README の言及）を記録する。

### Step 4: 読み込みの部品の上限の引数化と安全な読み込みの口 — 実装（dsl.parse・dsl.service）

- [x] `dsl.parse` の3つの部品が上限を引数で受ける形にする。深さと別名は `LoaderOptions` と `LimitingParser` に同じ値を、大きさは `setCodePointLimit` に渡す（T1'）。`DefaultDslReader` は `DslFormat` の値（版 1 のまま）を渡し、DSL の読み込みの結果（誤りの種類・文言・順）を変えない（BR6.6）。
- [x] `dsl.service` に `SafeYamlReader` と上限・結果・区分・位置の型を足す（4.1）。入力は `byte[]`。部品の拒否（`DslError` の種類と文言の鍵）を6つの区分に写し、文は捨てる。位置を引く口は `dsl.parse.PositionMap` を包む `dsl.service` の型にする。
- [x] `./gradlew :backend:compileJava :backend:spotlessCheck` を通す。

### Step 5: 読み込みの部品と安全な読み込みの口 — テスト

- [x] `SafeYamlReaderTest`（4.4 の中身。別名の爆発は T2 と同じ形の入力で、テストの中で測った時間を 5 秒で判定）を書く。既存の `SafeYamlParserTest`・`YamlTreeConverterTest`・`SafeYamlParserPropertyTest` は上限を引数で渡す形にだけ直す（見本の版 2 への書き換えは Step 9）。
- [x] `./gradlew :backend:test --tests 'cherry.mastersmith.dsl.*'` で流す（既存の `DefaultDslReaderTest`・`DslBoundaryArchitectureTest` が版 1 のまま通ることで、BR6.6 の「結果を変えない」を確かめる）。

### Step 6: 版 2 の書式・モデル・深さの関数 — 実装（dsl.domain、JSON Schema、ビルド）

- [x] 4.1 の `DslFormat`・`DslSchema`・`TableRef`・`DslMenuItem`・`DslModel`・`MenuDepth`・`DslMessageKeys` を書く。
- [x] `git mv backend/src/main/resources/dsl/dsl-schema-v1.json backend/src/main/resources/dsl/dsl-schema-v2.json` の後、中身を版 2 にする（4.3）。
- [x] `backend/build.gradle.kts` の名指し（`dslSchemaSource`・`verifyDslSchemaInWar`・説明のコメント）を v2 にする。

### Step 7: 版 2 の検証と読み方 — 実装（dsl.validate・dsl.service）

- [x] `DslSemanticValidator`（段の順と、深さの確かめを入れるかの引数）、`DslSchemaValidator`（v2 の資源）、`DslModelMapper`（版 2）、`DefaultDslReader`（`readAtStartup`）を書く（4.1）。

### Step 8: 次の層の型の追従（コンパイルを通すだけ、9節 D-1）

- [x] dslmanage の本体（`DslTreeBuilder`・`DslReconciler`・`DslDiffCalculator`・`DslSummaryCalculator`・`PreviewView`・`DslResponses` など、`DslModel.tables()`・`DslMenuItem.table()` を使う所）を、新しい型でコンパイルが通るだけの置き換えにする（例: `model.schema().tables()`、`TableRef.name()`）。振る舞いは作り直さず、各所に `TODO(B1 Step 10)`・`TODO(B1 Step 12)`・`TODO(B1 Step 14)` の印と理由を付ける。
- [x] dslmanage のテストのうち、`DslModel`・`DslMenuItem` を直接組み立てる所（`DslSummaryAndDiffTest`・`DslManageDomainTest`・`DslGenerationPropertyTest` など）を型だけ直す。
- [x] `./gradlew :backend:compileJava :backend:compileTestJava :backend:spotlessCheck` を通す。dslmanage のテストはこの Step では流さない（2.3）。

### Step 9: 版 2 の書式・モデル・検証 — テスト（dsl の層）

- [x] 4.4 の dsl の分（見本の版 2 への書き換え、`DefaultDslReaderTest`・`DslSchemaValidatorTest`・`DslSemanticValidatorTest`・`DslModelTest`・`DslResultTypesTest`・`ActiveDslModelStoreTest`・`SafeYamlParserTest`・`YamlTreeConverterTest` の版 2、`MenuDepthTest`・`MenuDepthPropertyTest`、`DslSchemaPublicationIT` の v2）を書く。jqwik の失敗時の乱数の種は既存の設定（`exceptionFormat = FULL`）で出力に残る。
- [x] `./gradlew :backend:test --tests 'cherry.mastersmith.dsl.*'` と `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.dsl.*'` で流す（`SafeYamlReaderSecretLeakIT` は Step 15 で足す）。

### Step 10: 既定の DSL の生成 — 実装（dslmanage.generate）

- [x] `DslTreeBuilder`・`TargetSchemaDslGenerator` を版 2 で作り直し（4.2）、Step 8 の `TODO(B1 Step 10)` を消す。

### Step 11: 既定の DSL の生成 — テスト

- [x] `DslTreeBuilderTest`・`TargetSchemaDslGeneratorTest`・`DslYamlWriterTest`・`DslGenerationPropertyTest`・`dept_mst.yaml` と、`AbstractDefaultDslGeneratorIT` の3種類を書き換える（4.4）。
- [x] `./gradlew :backend:test --tests 'cherry.mastersmith.dslmanage.generate.*'` と、colima の環境変数を付けて `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.dslmanage.generate.*'` で流す（対象DB のテストが SKIPPED になっていないことを確かめる）。

### Step 12: 業務処理 — 実装（dslmanage.service・dslmanage.domain）

- [x] 4.2 の `DslReconciler`・`DslDiffCalculator`・`DslSummaryCalculator`・`DslErrorMessages`・`UnreadableAppliedRevision`・`DslStartupLoader`・`DslLifecycle`・`PreviewView`・`DslStatus` を書き、`TODO(B1 Step 12)` を消す。
- [x] `DslLifecycle.apply` の順（2.2 の FD R-02、9節 D-4）: (1) キャッシュに `previewId` のモデルがあれば使う。(2) 無ければ今のプレビューの本文と識別を読み、識別が `previewId` と同じときだけ通常の読み方で確かめ、通らなければ内部DB を変えずに `DSL_INVALID`（422、誤りの一覧つき）。識別が違えば確かめずに (3) へ進む（`store.apply` が 409 を返す）。(3) `store.apply` で確定。(4) 確定の後は (1)・(2) のモデルで差し替える（履歴の本文を読み直さない）。(2) で確かめていない場合（識別が違ったのに確定できた、想定外）だけ、今までどおり履歴の本文を読む。
- [x] `./gradlew :backend:compileJava :backend:spotlessCheck` を通す。

### Step 13: 業務処理 — テスト

- [x] `DslReconcilerTest`・`DslSummaryAndDiffTest`・`DslStartupLoaderTest`・`DslLifecycleTest`・`DslManageDomainTest`・`UnreadableAppliedRevisionTest` を書く（4.4）。ログの確かめは既存の `DslStartupLoaderTest` と同じ形（キーと値）。
- [x] `./gradlew :backend:test --tests 'cherry.mastersmith.dslmanage.*'` で流す。

### Step 14: API と設定 — 実装（dslmanage.web・application.yaml）

- [x] `DslResponses` ほか応答の型に 4.2 の変更を写し、`TODO(B1 Step 14)` を消す。道・方法・状態コード・印は変えない。
- [x] `application.yaml` の `server:` の下に `tomcat.max-swallow-size: 11MB` と説明のコメント（日本語）を足す（2.1 の決定 (1)）。

### Step 15: API と結合 — テスト

- [x] 4.4 の結合テスト（`DslAdminApiIT`・`DslStartupIT`・`DslManageRepositoryIT`・`DslTargetDbIT`・`DslConcurrencyIT`・`DslAccessControlIT`・`DslAuditWriteFailureIT` の版 2、`SafeYamlReaderSecretLeakIT`）を書く。拒否の後は内部DB を読み直して、適用中・プレビュー・履歴が要求の前と同じことを確かめる（状態コードだけで合格にしない、NFR3.3）。
- [x] colima の環境変数を付けて `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.dsl.*' --tests 'cherry.mastersmith.dslmanage.*'` で流す。
- [x] `max-swallow-size` の結合テストはこの Bolt では足さない（10節 Q3: A。確かめは Step 24 の短い試走。実際の Tomcat で 10 MiB の2つ目に応答が届く結合テストは role の B6 が足す）。

### Step 16: バックエンドの区切りの確かめ

- [x] `TODO(B1` の検索が 0 件であることを確かめる。
- [x] `./gradlew :backend:test`（単体の全体。既存の `ArchitectureTest`・境界テスト・B2 の `ApiAccessArchitectureTest` を含む）と `./gradlew :backend:spotlessCheck :backend:spotbugsGate` を流す。`ApiAccessConsistencyIT` は結合の全体として Step 26 の `verify` に任せる。
- [x] 境界テストのファイルに差が無いことを `git diff develop --stat -- 'backend/src/test/java/**/*BoundaryArchitectureTest.java' backend/src/test/java/cherry/mastersmith/ArchitectureTest.java` で確かめる。

### Step 17: 画面 — 実装（features/dsl）

- [x] 4.5 を書く（10節 Q1: A で `SCHEMA_MISMATCH` を「照合できなかった」に入れ、Q5: A で適用の確かめにスキーマの件数の行を足す）。`data-testid` を足す要素（見出しの行・注意・読めないプレビューの区画と操作）に付ける。
- [x] `cd frontend && npm run typecheck && npm run lint:css` を通す。

### Step 18: 画面 — テスト

- [x] 4.6 を書く。描画の後の値は `waitFor` で待つ。`role="alert"` は読み上げを前提にせず、見える題・本文と `role` の有無だけを確かめる（FD R-06）。
- [x] `cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/features/dsl src/shared/api-client/apiClient.download.test.ts` で流す。

### Step 19: E2E — 書き換えと DSL の画面の検査（Q2: A）

- [x] `040-dsl-admin.e2e.ts` を版 2 に書き換え、版 1 の投入で版の誤りの文が出ることを1か所足す。
- [x] `135-dsl-admin-accessibility.e2e.ts` を足す（10節 Q2: A、番号は 9節 D-13）。API の答えを見本で差し替える（見本は1つにし、`frontend/src/features/dsl/api/types.ts` の型を付け、本物の応答と項目の名前・型が合うことは 040 の流れで確かめる、`project.md` の学び）。状態は F2（`appliedUnreadable` の注意）・F3（読めないプレビューの誤りの一覧と案内）・F5（長いスキーマ名・表示名の見出しの行）で、ブランドカラーとテーマのすべての組・幅 360・768・1280 で実際のブラウザの axe にかけ、画面全体と開いた部品のはみ出しを確かめる。既存の検査の E2E（`120-user-admin-accessibility.e2e.ts` など）の組と幅の作り方にそろえる。初期管理者の状態は変えない。
- [x] `./gradlew :backend:bootWar` の後に `cd frontend && npx playwright test e2e/040-dsl-admin.e2e.ts e2e/135-dsl-admin-accessibility.e2e.ts` で流す（Mailpit を起動しておく）。全体は Step 27。

### Step 20: 負荷の道具 — 書き換え

- [x] 4.7 の `perf/` を書き換える。`--busy`: (1) ログインを1回して見出しのファイルを作り、(2) 10 MiB の版 2 の DSL の投入を2つ並べて送り（要求ごとに別の応答のファイル、curl の `-w '%{http_code}'` と終了コードを両方記録）、(3) 組の判定は成功1つと 503 `DSL_BUSY` 1つで合格、2つとも成功は重ならなかった回として最大 5 回までやり直し、尽きたら記録して依頼者に諮る、どちらも 503・`DSL_BUSY` 以外の 5xx・状態コードが取れない回は不合格の候補として記録する（9節 D-9）、(4) `memory.peak`・OOMKilled・終了の状態を記録、(5) 結果を `build/perf-results/dsl-<日時>/` に1行の表で置く。ヒープの使用の記録（10節 Q4: A）: 使い捨ての環境のアプリにだけ `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics` を渡し、`/actuator/metrics/jvm.memory.used?tag=area:heap` の値を投入・表示・`--busy` の後に記録する（配備したアプリの公開の範囲は変えない、`project.md` の学び）。
- [x] `node --check` と `bash -n` で構文を確かめ、`perf/k6/scenarios.js` に `version` の文字列が無いことを確かめる。

### Step 21: 文書

- [x] README・`perf/README.md` を 4.7 のとおり直す。`SafeYamlReader`・`MenuDepth`・`UnreadableAppliedRevision` の Javadoc に使い方と守りの決まり（日本語）を書く。

### Step 22: 画面の区切りの確かめ

- [x] `cd frontend && npm run format:check && npm run lint && npm run typecheck && npm run license:check` を流す。

### Step 23: 取り残しと変えないものの確かめ

- [x] `git grep -n 'dsl-schema-v1' -- ':!aidlc' ':!vendor'` が 0 件、`git grep -n 'version: 1' -- backend/src frontend/src frontend/e2e perf` に DSL の見本が残っていない（版 1 を拒否するテストの見本は除く）ことを記録する（Infrastructure Design R-05）。
- [x] 4.8 のファイルに差が無いことを `git diff --stat develop` で確かめる。Flyway の移行のファイルが増えていないことを確かめる（NFR3.3 の決定）。

### Step 24: 短い試走（使い捨ての環境、2.1 の決定 (1)・Infrastructure Design (iv)）

- [x] 配備したアプリとは別の使い捨ての環境（`docker/perf/compose.yaml`、仮の署名鍵・仮の利用者、終わったら消す）で、`perf/dsl-timing.sh` の版 2 の投入・プレビュー・適用が流れることと、`--busy` を1回流して 503 `DSL_BUSY` の状態コードが送り手に届くこと（成功1つと 503 1つ）を確かめる。台本全体を `caffeinate -i` で包む。colima の VM（CPU 4・メモリ 6GiB）に収まるかを先に確かめ、配備したアプリを止めるかを記録する。監査に残る操作は使い捨ての環境の中だけ。
- [x] 時間・ヒープの判定は Build and Test に任せる（ここでは道具が動くことと 503 が届くことだけ）。503 が届かない回があれば、2.2 の Infrastructure Design R-01 のとおり並べて記録し、止めて依頼者に諮る。

### Step 25: 記録（コード生成の段の成果物）

- [x] `code-summary.md`（作ったもの、計画との差、依頼者に確かめたいこと、承認の場で確かめること、既知の制約（NFR 設計 R-07）、後の Intent に回すこと（FD R-07））、`traceability.json`（7節の対応。2.2 の FD R-08・Infrastructure Design R-06 の扱い）、`source-manifest.json`（作った・変えた・消したアプリのソースの道のすべて。`dsl-schema-v1.json` の削除を含む）を書く。

### Step 26: 1コマンドの検査（統合の前の関門）

- [x] colima を起動し、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡して `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` を流す。対象DB のテストが SKIPPED になっていないことを確かめる。
- [x] 記録: テストの件数、5節の9パッケージの行・分岐（Step 3 の基準と並べ、下がったパッケージは理由）、画面の全体の行・分岐、`verify` の時間、WAR の中の `dsl-schema-v2.json` の2か所の照合（段 9）、4.8 のファイルに差が無いこと。
- [x] 落ちたら、原因を直して流し直す。検査・下限・除外・テストの JVM のヒープを緩めない（ヒープが足りなければ止めて依頼者に諮る、`cicd-pipeline.md` 4節）。計画の影響の範囲に無い既存のテストが落ちたら、止めて依頼者に諮る。

### Step 27: E2E（統合の前に手元で）

- [x] `docker compose --profile mail up -d mailpit` の後に `./gradlew e2eTest` を流し、すべてのファイル（今の 13 に 135 を足した 14）が通ることを確かめる（特に `040-dsl-admin.e2e.ts`・`135-dsl-admin-accessibility.e2e.ts`）。
- [x] json の報告から結果を記録し、`frontend/playwright-report`・`frontend/test-results` を消す。報告に資格情報・メールアドレス・トークンが無いことを確かめる（`project.md` の学び）。

### Step 28: コミットの提案・承認の場・統合の提案

- [ ] 3節の C1〜C5 の区切りを依頼者に提案し、承認を得てコミットする（コミットのメッセージは日本語）。
- [ ] 最後のコードのコミットの後に、コード生成の段の承認の場を開く（記録だけのコミットはその後）。
- [ ] 承認の後、`aidlc/` の未コミットの変更をコミットしてから、`develop` への squash の統合を提案し、承認を得て行う。作業ブランチを消す。push は依頼者が行う。

## 7. ストーリー・要件と手順の対応

| ストーリー・要件 | 決まり・要件（単位の ID） | 手順 |
|---|---|---|
| US6.1 AC6.1.1（版 2 のスキーマの階層を投入・適用し、権限の対象の最上位に出る土台） | BR1.2・BR1.6・BR8.1・BR8.2、NFR1.14 | Step 6・7・9・12〜15 |
| US6.1 AC6.1.2（版 1 は版の誤りで 4xx、版 2 で書く案内） | BR1.1・BR7.1、NFR1.10 | Step 7・9・12・15・17〜19 |
| US6.1 AC6.1.3（既定の DSL を版 2 で生成、スキーマ名を書き接続情報を書かない、そのまま検証を通る） | BR4.1〜BR4.4、NFR1.13・NFR2.7 | Step 10・11・15 |
| US6.1 AC6.1.4（2つ目・名前の違うスキーマ） | BR1.3・BR5.1、NFR5.5 | Step 7・9・12・13・15 |
| US6.1 AC6.1.5（スキーマ名・テーブル名の重なり） | BR1.4、NFR1.8 | Step 9 |
| US6.1 AC6.1.6（版 1 の適用済みで版を上げると無い扱いで起動） | BR3.1・BR3.6・BR7.2・BR8.1、NFR3.3・NFR5.4 | Step 12・13・15・17・18 |
| US6.1 AC6.1.7（版 1 の履歴の復元は 4xx、ダウンロードは 500 にならない） | BR3.2・BR3.5、NFR3.3 | Step 15 |
| US6.1 AC6.1.8（前の版への戻し） | 配備の段への引き継ぎ（`logical-components.md` 7節） | —（引き継ぎ） |
| US6.1 AC6.1.9（画面で今までどおり使え、照合は設定のスキーマ、ダウンロードは版 2） | BR5.1〜BR5.3・BR7.4・BR7.5、NFR4.1・NFR4.3 | Step 12〜19 |
| US6.1 AC6.1.10（既存の DSL のテストを版 2 で） | BR6.6、NFR1.7〜NFR1.10・NFR6.5・NFR6.7 | Step 5・9・11・15・19・20 |
| US5.2 AC5.2.1・AC5.2.2（深さの上限の境界） | BR2.1・BR2.2、NFR1.12・NFR6.1 | Step 6・7・9・15 |
| US5.2 AC5.2.3（起動時は深さだけ外し枝を落として警告） | BR2.3、NFR1.12・NFR5.3・NFR6.6 | Step 6・7・9・12・13・15 |
| US5.2 AC5.2.4（深すぎる履歴の復元は 4xx） | BR3.2、NFR3.3 | Step 15 |
| US5.2 AC5.2.5（深すぎる適用中のダウンロードは 500 にならない） | BR3.5 | Step 15 |
| 版を上げる前のプレビュー（Q4） | BR3.3・BR3.4・BR7.3、NFR3.3 | Step 12・13・15・17・18 |
| US3.1 AC3.1.7・AC3.1.13 の土台（安全な読み込みの口） | BR6.1〜BR6.5、NFR1.5・NFR1.11・NFR1.14 | Step 4・5・15 |
| US1.2・US5.1 の土台（提供口の版 2 のモデル） | BR8.1・BR8.2（契約 C3） | Step 6・7・9 |
| 要件 C2（前進のみ、表を変えない） | NFR3.3 の決定（Flyway の移行が無い） | Step 23 |
| 要件 C6（接続情報を受け取らない・出さない） | BR4.3、NFR1.13 | Step 11・15 |
| 503 `DSL_BUSY` が届く（2.1 の決定 (1)） | NFR2.5・NFR2.6 の確かめの土台 | Step 14・20・24 |
| 時間・ヒープ・大きさ・英語の文言 | NFR2.5〜NFR2.9 | Step 20（道具）、Build and Test |
| カバレッジ | NFR6.4 | Step 3・26 |
| 統合・E2E | `team.md`・`cicd-pipeline.md` 7・8節 | Step 26〜28 |

## 8. テストの量（Standard）

| 部品 | テスト | 件数の目安 |
|---|---|---|
| 安全な読み込みの口 | `SafeYamlReaderTest`（4つの上限のちょうどと超え 8・深さと別名の N と N+1 の区分と位置 2・別名 0 の2つ・区分の残り 3・例外の文 1・上限の範囲外 2・別名の爆発 2・`toString` 1） | 15〜20（守りの中心で、`team.md` の DSL の必須のテストが当たるため Standard の上限を超える） |
| 安全な読み込みの漏えい | `SafeYamlReaderSecretLeakIT`（入力・結果） | 2 |
| 読み込み（版 2） | `DefaultDslReaderTest` に足す分（版 1・版なし・スキーマ 0/1/2・重複キー 2・組の無い先・深さ 5/6・`readAtStartup` 2） | 8〜11 |
| 深さの関数 | `MenuDepthTest`（例 5）・`MenuDepthPropertyTest`（性質 5） | 10 |
| モデル | `DslModelTest`・`DslResultTypesTest` に足す分 | 5 |
| 生成 | 既存の生成のテストの書き換え（件数は変えない）＋スキーマ名を書く・組の確かめ | 既存＋3 |
| 業務処理 | `DslReconcilerTest`（`SCHEMA_MISMATCH` 2）・`DslSummaryAndDiffTest`（区分 4・`schemaCount`・未設定）・`DslStartupLoaderTest`（WARN・ERROR・`remember`・`clear` 4）・`DslLifecycleTest`（読めないプレビュー・適用の順 4）・`UnreadableAppliedRevisionTest` 4 | 部品ごとに 2〜6 |
| API・結合 | `DslAdminApiIT` に足す分（版 1・深さ 5/6・スキーマ 0/2 の投入と復元、読めないプレビューの表示と適用、ダウンロード、`appliedUnreadable`、409 が先）・`DslStartupIT`（版 1・深さ 6） | 12〜15 |
| 画面 | `DslStatusPanel`（3）・`DslPreviewPanel`（6）・`DslAdminPage`（4）・`DslMenuTree`（2）・`DslDiffTable`（4）・`DslWarningList`（1） | 部品ごとに 1〜6（既存の件数に足す） |
| E2E | 040 の書き換え（本数に数えない）、135 の DSL の画面の検査（F2・F3・F5 × ブランドカラーとテーマのすべての組 × 3つの幅、本数に数えない） | 状態ごとに 1（組と幅はテストの中で回す） |
| 適用の確かめの件数（Q5: A） | `diffCounts.test.ts`（スキーマの区分 4・合計 1）・`DslConfirmDialog.test.tsx`（スキーマの行 2） | 7 |

既存のテストはすべて通ったままにする。新しい E2E の流れ・性能の新しいテストは要件が求めていないため足さない（性能は Build and Test が道具で測る。Testing Contract の `strategy_volume`）。

## 9. この計画で決めたこと・承認済みの文書との差

| # | 差 | 理由 |
|---|---|---|
| D-1 | Step 8 で dslmanage の本体とテストを型だけ追従させ、`TODO(B1 Step n)` の印を付け、それぞれの層の Step で作り直す（印は Step 16 までに 0 件） | Gradle はテストの前に全ソースをコンパイルするため、モデルの形を変えた時点で dsl の層のテストを流すには dslmanage のコンパイルが要る。test-after の層の順（Testing Contract の `ordering`）を保つための作りの順 |
| D-2 | 読み込みの部品の上限を引数にする（Step 4）を、版 2（Step 6）より先に置く | 版 1 のままの既存のテストで、引数化が DSL の読み込みの結果を変えないこと（BR6.6）を先に確かめるため |
| D-3 | 名前を案として置いた型（`MenuDepth`・`SafeYamlLimits`・`SafeYamlResult`・`SafeYamlRejectionKind`・`YamlPositions`・`DslStartupReadResult`・`YamlLimits`・`UnreadableAppliedRevision`）と、メッセージの鍵・ログのキー（`dsl.prunedMenuItems`・`dsl.menuDepthLimit`） | 設計が「名前は Code Generation で確定」とした範囲。生成で名前を変えたら `code-summary.md` に記録する |
| D-4 | 適用の確かめの順を「今のプレビューの識別が `previewId` と同じときだけ読み直す。違えば確かめず `store.apply` の 409 に任せる。確定の後は事前に得たモデルを使い、履歴の本文を読み直さない」にした（上流との差: `functional-spec.md` 3.4・BR3.4 は順を書いていない） | Functional Design R-02。409 を 422 より先にし、確定の後の 500 の経路と 10 MiB の二重の読み込みを無くす |
| D-5 | `UnreadableAppliedRevision` の書き換えの口をパッケージの中だけにし、呼ぶのは `DslStartupLoader` だけとした | NFR 設計 R-06 |
| D-6 | 画面の `previewInvalid` は `preview` より優先し、投入・生成・復元の成功・破棄・プレビューの読み込みの成功で消す。適用の 422 では `preview` を null に落とし `previewInvalid` に誤りの一覧を入れる（上流との差: `frontend-components.md` F3 は消す契機と優先を書いていない） | Functional Design R-05 |
| D-7 | `SafeYamlReaderTest` に深さと別名の N と N+1 の境界を足し、別名の爆発の 5 秒をテストの中で測った時間で判定する | NFR 設計 R-05、`security-design.md` 4.2 |
| D-8 | `perf/ui/dsl-ui-lang.mjs` の判定に、版の誤り・深さの誤り・`SCHEMA_MISMATCH` の英語の文が届くことを足す | NFR 設計 R-04 |
| D-9 | `--busy` の重ならなかった回のやり直しは最大 5 回、尽きたら不合格にせず記録して依頼者に諮る。状態コードと curl の終了コードを両方記録する。ログインは先に1回、要求ごとに別の応答のファイル | Infrastructure Design R-01・R-02・R-07。5 回は、先の投入の処理が秒単位で重ならない回はほぼ起きない（R-02 の指摘）ことに余裕を持たせた値 |
| D-10 | `server.tomcat.max-swallow-size: 11MB` を `application.yaml` に足す（dsl-v2 の承認済みの設計の文書には無い。role の基盤の設計の Q1: A と、この単位の基盤の設計の承認の場の決定 (1)） | 2.1。設計の文書は書き換えず、差を `code-summary.md` に書く |
| D-11 | 版 1 の投入で版の誤りの文が出る確かめを、既存の E2E 040 に1か所足す | B1 の「見せるもの」（版 1 を投入すると誤りの文言が出る）。新しい流れではないため本数に数えない |
| D-12 | 新しい警告 `SCHEMA_MISMATCH` を「照合できなかった」（`NOT_COMPARED_KINDS`）に入れ、プレビューの食い違いの件数と適用の確かめの件数に数えない（上流との差: `frontend-components.md` は数え方を書いていない） | 10節 Q1: A（Functional Design R-01） |
| D-13 | DSL の画面の検査の E2E を `135-dsl-admin-accessibility.e2e.ts` として1つ足す（上流との差: `frontend-components.md` 5・6節は vitest-axe だけ）。番号は 140 を使わない。role-admin-ui が 140・150、app-frame-ui が 160・170 を予定しているため、それらと今の 010〜130 のどれとも重ならず、DSL の画面の後ろ（130 の後）に並ぶ 135 にした。操作の流れではないため `team.md` の E2E の本数に数えない（`project.md` の学び） | 10節 Q2: A（Functional Design R-04、`project.md` の学び 2026-09-28） |
| D-14 | `perf/dsl-timing.sh` が使い捨ての環境のアプリにだけ `health,metrics` を公開し、10 MiB の投入・表示と `--busy` の後のヒープの使用（`jvm.memory.used` の heap）を記録する（上流との差: `logical-components.md` 4節の判定は OOM・OOMKilled・`memory.peak` だけ。判定の値は変えず、記録を足す） | 10節 Q4: A（NFR 設計 R-08） |
| D-15 | 適用の前の確かめの件数に、スキーマの増減（増えた・減った・変わった（表示名））の行を足す（上流との差: `frontend-components.md` F5 は件数をテーブルの合計だけとしていた。文言の鍵を1つ足す） | 10節 Q5: A（Functional Design R-05 の後半） |
| D-16 | `max-swallow-size` の確かめは Step 24 の短い試走だけにし、結合テストは足さない | 10節 Q3: A（結合テストは role の B6） |

承認済みの設計の文書は書き換えず、差は `code-summary.md` にも書く。

## 10. 依頼者の答え（計画の承認の前に確かめたこと）

5つの問いに、依頼者はどれも A と答えた（指揮役から伝達）。問いの背景と選んだ中身を残し、反映した所を右の列に書く。

| 問い | 背景 | 答え（確定） | 反映した所 |
|---|---|---|---|
| Q1 `SCHEMA_MISMATCH` の数え方（Functional Design R-01） | 名前の違うスキーマの DSL では、テーブルを1つも比べない。今の画面は `TARGET_UNCONFIGURED`・`TARGET_UNAVAILABLE` だけを「照合できなかった」とし、ほかを「食い違い N 件」と数えるため、何もしなければ「食い違い 1 件」と出る | **A**. 「照合できなかった」に入れる（`NOT_COMPARED_KINDS` に足し、プレビューの食い違いの件数と適用の確かめの件数に数えない） | 2.2、4.5・4.6、Step 17・18、9節 D-12 |
| Q2 実際のブラウザの axe（Functional Design R-04、`project.md` の学び 2026-09-28） | 足す画面の状態（F2 の warning の Alert、F3 の danger の Alert と誤りの一覧、F5 の見出しの行）は、前に dark の誤りの文字でコントラスト不足が出た種類に当たる。vitest-axe だけでは色の組を確かめられない | **A**. DSL の画面の検査の E2E を1つ足し、API の答えを見本で差し替えて F2・F3・F5（長い名前）を、ブランドカラーとテーマのすべての組・幅 360・768・1280 で axe とはみ出しにかける（操作の流れではないため本数に数えない）。番号は 140 を使わない（role-admin-ui が 140・150、app-frame-ui が 160・170 を予定）よう指示があり、今の 010〜130 とも重ならない `135-dsl-admin-accessibility.e2e.ts` にした | 2.2、4.7、Step 19・27、8節、9節 D-13 |
| Q3 `max-swallow-size` の確かめ方（2.1 の決定 (1)） | 決定は「B1 の短い試走で 503 が届くことを確かめる」。role の B6 は、同じ設定を前提に実際の Tomcat で 10 MiB の2つ目に応答が届く結合テストを足す予定 | **A**. 決定どおり短い試走（Step 24 の `--busy`）だけで確かめ、結果を `generation-notes.md` と `code-summary.md` に記録する。結合テストは role の B6 が足す | Step 15・24、9節 D-16 |
| Q4 ヒープの実測の記録（NFR 設計 R-08） | 承認済みの判定は OOM・OOMKilled・`memory.peak` だけで、`mem_limit` 2g はヒープの上限 1 GiB より大きいため、ヒープの見積もり（T4 の 256〜384 MB）と比べる根拠が残らない | **A**. `perf/dsl-timing.sh` が使い捨ての環境のアプリにだけ指標を公開し、10 MiB の投入・表示と `--busy` の後のヒープの使用（`jvm.memory.used` の heap）を記録する（配備したアプリの公開の範囲は変えない） | 2.2、4.7、Step 20、Build and Test に引き継ぐこと、9節 D-14 |
| Q5 適用の確かめの件数にスキーマの増減を出すか（Functional Design R-05 の後半） | 適用の確かめの件数（`diffCounts.ts`）はテーブルの合計だけ。スキーマはちょうど1つで、名前の違うスキーマへの入れ替えはテーブルの増減としても出る。表示名だけの変化は違いの表の見出しの行で見える | **A**（指揮役の伝達の文言: 「適用の前の確かめの件数にスキーマの増減も出す」）。適用の確かめにスキーマの件数の行（増えた・減った・変わった（表示名））を足し、文言の鍵を1つ足す。注: 計画の初版の選択肢の表記では A が「出さない」・B が「出す」だったが、伝達の文言（出す）のとおりに反映した。承認の場で依頼者に確かめる | 2.2、4.5・4.6、Step 17・18、8節、9節 D-15 |

## Testing Contract

```json
{
  "version": 1,
  "methodology": "test-after",
  "source": "team",
  "ordering": "テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。",
  "scope": "classic",
  "test_strategy": "standard",
  "project_type": "brownfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: test-after\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる。ただし、既存のパッケージに単独で下限を下回るものがあれば、パッケージごとの下限は新しく作るパッケージだけに当てる（既存のパッケージは全体の合計で判定する）。どちらになるかは、パッケージごとの下限を入れる Bolt で既存のパッケージのカバレッジを実測して決め、結果を記録する。\n- 全体の合計で判定している既存のパッケージ（`backend/build.gradle.kts` の `packagesJudgedByTotal` の一覧。2026-10-04 の時点で 7 パッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）。`user.domain`・`user.repository`・`user.service` は Intent 260925-user-management で一覧から外れ、含まない）に手を入れる Bolt では、テストを足してそのパッケージの下限（行 80%・分岐 70%）を満たし、一覧から外してパッケージごとの下限の対象に必ず戻す。「手を入れる」は、そのパッケージの本体のソース（`src/main`）の変更のすべて（説明文だけの直しを含む）を指し、テストだけの変更は含めない。手を入れる見込みのパッケージとそれに伴う作業は、Delivery Planning とコード生成の計画で、各パッケージの今の値を実測して見積もる。カバレッジは、その Bolt のテストを足した後に `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、値を記録する。一度外したパッケージは一覧に戻さず、一覧を増やす変更はしない。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。\n- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストは、3種類すべてを `./gradlew verify` の中で毎回実行する（CI も同じ）。Intent `260923-dsl-schema-loader` の Build and Test で `verify` の時間と colima の VM のメモリを実測したうえで、この形に決めた。コンテナの実行環境が無いときの扱いは Way of Working のとおり。\n- 外へ送るメール（SMTP）を使う機能のテストは、送信の部品をモックで置き換えて済ませず、テストの中で JVM の中に起動するテスト用の SMTP の受け手で実際に受け、宛先・件名・本文（HTML）・言語を確かめる。受け手にコンテナは使わない（コンテナの実行環境が無いときに飛ばしてよいテストは、対象DB のテストだけのままとする）。送信の失敗（受け手が接続を拒む・応答しない）のテストも必ず入れる。実在の宛先へは送らない。受け手の具体の道具は、採用の前のライセンスの確認とあわせて設計の段で決める。\n- 画面からの一連の操作を確かめるテスト（E2E）は Playwright（`./gradlew e2eTest`）で書き、`./gradlew verify` と CI の外に置く。本数は代表的な流れに絞り、機能の Intent ごとに代表の流れを1本まで足す。新しく足す流れは、前のテストが作った状態に頼らず、利用者などの前提を自分で作る。複数の機能の Intent を1つに束ねた Intent では、束ねた元の Intent ごとに数える（Intent 261004-role-menu は最大2本）。既存の E2E を新しい形（権限の形など）に合わせて書き換えることは、本数に数えない。利用者の状態を変える操作（利用停止・管理の権限の変更・ロックなど）は、その流れで自分で作った利用者だけを対象にし、初期管理者の状態は変えない。役割・権限を変える操作も、その流れで自分で作った利用者と役割だけを対象にし、初期管理者は変えない（E2E は1つのアプリ・内部DB・初期管理者を全ファイルで共有して順に流すため）。E2E は、画面・認証に関わる変更を統合する前と、リリースの前に手元で実行する。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- リポジトリは公開のため、テストデータのメールアドレスは予約のドメイン（`example.com` など）だけにし、実在しそうな氏名・宛先を置かない。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- 画面のテストで、描画の後（`useEffect` などの効果）に反映される値は、操作の直後に同期で確かめず、`waitFor` で待って確かめる。テストの時間の上限（Vitest の既定 5 秒など）は、原因を確かめずに延ばさない。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。\n- 不安定なテストと CI の失敗は、次の1つの決まりで扱う。\n  - 手元で再現した不安定なテストは、原因を直すまで統合しない（次へ進まない）。\n  - 手元で再現しないものは、再現の試みに先に時間の上限を決め、見立てと試みの範囲を記録したうえで、CI の再実行で通れば進めてよい（不安定と確かめられていない扱い）。\n  - 同じテストが二度目に落ちたら、原因を直すまで次へ進まない。\n  - 失敗を直さずに次の Intent へ持ち越すのは、依頼者の決定があるときだけとし、決まりとの差を記録する。\n- 認証・認可・監査に関わる機能では、次のテストを必ず書く。★印の項目は要件（しきい値や動作）が未確定のため、要件定義で決めてからテストを書く。この一覧の「管理者」は管理の権限を持つ利用者を、「管理の権限」はその判定に使う権限を指し、権限の形（真偽値1つか役割・権限か）によらずに読む。権限の具体の形（役割・権限の名前など）は、要件で決まった後に書き足す。\n  - アカウントロック: 失敗回数のしきい値の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロック中は正しいパスワードでも拒否、ログイン成功時の失敗回数の扱い。★しきい値、回数を数える期間、ロックの解除方法\n  - ログイン: 存在しないユーザーとパスワード誤りで、応答からユーザーIDの存在を推測できないこと\n  - トークン: 有効期限の境界（直前は有効／直後は無効）、署名の改ざん、署名方式の指定を悪用した改ざん（`alg: none` 等）、ログアウト後のリフレッシュトークンの拒否、ログアウト後もアクセストークンが有効期限まで使えること（決定済みの仕様として明示する）。★各トークンの有効期限の値、リフレッシュトークンを使うたびに作り直すか\n  - 初期管理者の自動作成: 2回目以降の起動で重複作成しない、設定が無い／不正なときの動作、パスワードがログに出ない。★設定が無いときに起動を止めるか\n  - 認可: 未認証（401）、その権限を持たない（403）、持つ（200）をサーバー側のテストで確かめる（画面で管理メニューを隠すことはサーバー側の検査の代わりにしない）\n  - 監査ログ: 対象イベントごとに必須項目が記録されること、存在しないユーザーIDでのログイン失敗も記録されること。★監査ログの書き込みに失敗したときに操作を失敗させるか\n  - 秘密情報の漏えい: ログ・監査ログの出力にパスワード・トークンの値が含まれないこと\n  - 構造化ログ・分散トレース: 決めた形式で出る、トレースIDがログに含まれる、外部エクスポートが既定で無効であること\n  - 画面: ログイン画面のアクセシビリティ検査、ロック時のメッセージ表示、ログアウトでトークンが破棄されること\n  - 招待と登録の完了: 招待の有効期限の境界（直前は有効／直後は無効）、使い終えた招待・取り消した招待の再使用の拒否、改ざんした招待・存在しない招待の拒否（応答から利用者の存在を推測できないこと）、招待中（登録が終わっていない）の利用者はログイン・トークンの更新・アクセストークンの認証のどれでも拒否されること。★招待の有効期限の値、再送・取り消しの扱い\n  - パスワードの変更: 今のパスワードの確かめ、パスワードの規則の境界、変更の後のリフレッシュトークンの扱い（決めた側の動作を明示したテストにする）。★パスワードの規則、変更の後にほかの端末のリフレッシュトークンを無効にするか\n  - 利用停止: 停止中の利用者は、ログインの照合・トークンの更新・アクセストークンの認証の3つの入口のすべてで拒否されることを、入口ごとにサーバー側のテストで確かめる。停止を解いた直後は3つの入口のすべてで受け付けること。停止の前に出したリフレッシュトークン・アクセストークンの扱いは、決めた側の動作を明示したテストにする。★停止を応答から推測できてよいか、停止の前に出したトークンの扱い\n  - 管理の権限の変更: 管理の権限を与えた直後・外した直後の次の要求で、管理の API の 403／200 がサーバー側で切り替わること（画面が持つ古い権限に頼らない）。権限を外す前に出したトークンの扱いと、自分の管理の権限を外す操作の扱いは、決めた側の動作を明示したテストにする。★権限を外す前に出したトークンの扱い、自分の管理の権限を外す操作の扱い\n  - ロックの解除: 解除の直後に正しいパスワードで入れること、解除の後の失敗回数の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロックの状態の行が無い利用者の解除。時刻は注入した時計で動かし、実時刻と `sleep` に頼らない。★解除の後の失敗回数の扱い、ロックの状態の行が無いときの応答\n  - 最後の管理者の保護: 最後の有効な管理者（管理の権限を持つ利用者）から管理の権限を外す・利用を止める操作は拒否され、拒否の後に状態が変わっていないこと。2人の管理者が同時に互いの管理の権限を外す・利用を止めても、有効な管理者が 0 人にならないこと。同時の重なりはスレッドの数に頼らず、待ち合わせで確実に作る。★最後の管理者の数え方（止めた管理者を数えるか）\n  - 管理の API の認可: 足す管理の API のすべてについて、未認証（401）・その権限を持たない（403）・持つ（200）をサーバー側のテストで確かめる。停止中の管理者は管理の API を呼べないこと。\n  - 要求の改ざん: 管理の API の外（`/api/me` など）から、要求の本文の値を変えて自分の管理の権限や利用者の状態を変えられないこと（一括代入の防止）。\n  - 管理の操作の監査: 利用者の権限・状態を変える操作（管理の権限の付け外し・利用停止と再開・ロックの解除）ごとに、操作した人・対象の利用者・結果が記録されること。★拒否した操作（403・最後の管理者の拒否など）を記録するか\n  - 利用者の管理の漏えい: 一覧・詳細の応答と監査の行に、パスワードのハッシュ値・リフレッシュトークン・ロックの判定の内部の値が含まれないこと。TRACE のログを有効にして一覧・詳細を読んでも、メールアドレス・氏名がアプリのログに出ないこと（既存の `*SecretLeakIT` と同じ形で確かめる）。\n- 役割・権限の機能では、次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 権限ごとの API の認可: 足す・変える API のすべてについて、未認証（401）・その権限を持たない（403）・持つ（200）をサーバー側のテストで確かめる。403 を確かめる利用者は、権限が何も無い利用者ではなく、要る権限だけを欠く利用者（ほかの権限は持つ）にする。API と権限の組は、パラメーターを使うテストで表として書く。停止中の利用者は、権限があっても通らないこと。\n  - API の分類の網羅: アプリが持つすべての API が「公開・ログインだけ・権限が要る（どの権限か）」のどれかに明示して分類されていることを確かめるテストを置き、分類の無い API があれば失敗させる（`/api/**` の既定は「ログインだけ」で、権限の決まりを書き忘れるとログイン中の誰でも呼べるため）。足した API を分類に足し忘れると落ちる形にする。分類の持ち方と仕組みの置き場は設計の段で決める。\n  - 割り当ての変更の反映: 役割・権限を付けた直後・外した直後の次の要求で、403／200 がサーバー側で切り替わること（画面が持つ古い権限に頼らない）。★変更の前に出したトークンの扱い\n  - 権限の昇格・一括代入・IDOR の防止: 操作する人が持たない権限を、自分や他人に与えられないこと。要求の本文の値を変えて自分の役割・権限を変えられないこと、役割の作成・変更の本文に許していない項目（ID・組み込みの印・作成者など）を足しても反映されないこと。役割・割り当ての ID を差し替えた要求（他人の割り当て・存在しない役割など）は拒否され、状態が変わらないこと。★組み込みの役割の有無と、その削除・権限の取り上げの保護\n  - 自分自身への操作と最後の管理者の読み替え: 自分の役割を外す操作と、最後の有効な管理者を役割でどう数えるかは、決めた側の動作を明示したテストにする。同時の重なりはスレッドの数に頼らず、待ち合わせで確実に作る。★自分の役割を外す操作の扱い、最後の有効な管理者の役割での数え方\n  - 監査: 割り当ての変更ごとに、操作した人・対象・変えた中身・結果が記録されること。★役割そのものの作成・変更・削除を記録するか、拒否した操作（403・昇格の拒否など）を記録するか\n  - 画面の出し分け: メニュー・画面を隠すことを、サーバー側の検査の代わりにしない。権限の無い画面へ直接移ったときに 403 の表示になること。\n  - 性質ベースのテスト: 役割から権限の集合を求める関数（サーバー側）と、権限でメニューの枝を落とす関数（画面側）は純粋な関数にし、jqwik・fast-check で性質（持たない権限の API・項目が出ない、役割を足しても権限は減らない など）を確かめる。\n- N 階層のメニュー（ナビゲーションの木）の画面では、次のテストを必ず書く。深さの上限の具体的な値は設計の段で決め、決めた値の境界で確かめる。\n  - 開閉と今の項目: 開閉の状態（`aria-expanded`）と今の項目（`aria-current`）が正しく出ること、キーボードだけでたどれること。\n  - 深さの上限: 上限ちょうどは受け付け、上限を超えると拒否されること。\n  - 表示の木を作る関数: 定義の木から表示の木を作る純粋な関数（権限で枝を落とす・並べる）に、fast-check の性質ベースのテストを当てる（例: 権限の無い項目が出ない、子の無い枝が残らない）。\n  - 信頼できない入力: メニューの定義（`label`・`icon`・`table`）を信頼できない入力として扱う。表示名に `<`・`>`・`&`・`\"`・`'` を含めても文字として出る（HTML として描かない）こと、`icon` を許す名前の一覧と照らすこと、`table` の名前から組み立てる道をエンコードし、アプリの中の決めた形から外れない（外部の URL・`javascript:` の道を作らない）こと。★一覧に無い `icon` を拒否するか既定のアイコンにするか\n  - make-you-chic-ui の部品に頼る振る舞い: 開閉・`aria-current`・キーボードなど、make-you-chic-ui の部品に頼る振る舞いも、このリポジトリの画面のテストと実際のブラウザの axe で確かめる（make-you-chic-ui のテストはこのリポジトリの CI の対象外のため）。axe は、展開した状態・深い階層・長い名前で、ブランドカラーとテーマのすべての組について行う。\n- 利用者へメールを送る機能では、Code Style と `project.md` のメールの決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 本文のエスケープ: 差し込む利用者の値（氏名・メールアドレスなど）に `<`・`>`・`&`・`\"`・`'` を含めても、タグや属性としてそのまま出ないこと。利用者の値をエスケープしない差し込み（`{{{ }}}`・`{{& }}`）で入れていないこと、属性の値を二重引用符で囲んでいること（エンジンの `{{ }}` は `'` を置き換えないため）\n  - テンプレートの描画: 日本語と英語のすべてのテンプレートを描けて、件名が空でないこと、差し込み漏れ（`{{` の残り）が無いこと、ライセンスヘッダーが描いた本文に出ないこと\n  - 招待の URL: 設定したベース URL だけから組み立て、要求の Host ヘッダーから組み立てないこと。ベース URL の設定が無いときに要求の Host を変えても、招待の URL に出ないこと。★ベース URL の設定が無いときの動作\n  - トークンと URL の漏えい: 招待のトークンと招待の URL が、アプリのログ・監査ログ・トレースの属性・エラー応答に含まれないこと（既存の `*SecretLeakIT` と同じ形で確かめる）\n  - ヘッダーへの差し込み: 宛先・件名・差し込む値に改行（CR・LF）を含めても、メールのヘッダーが増えず拒否されること\n  - 送信の失敗: 送信が失敗したとき（受け手が接続を拒む・応答しない）に、宛先のメールアドレス・SMTP の応答・資格情報が応答とログに含まれないこと。★送信が失敗したときの業務の動作（招待を失敗させるか、再送に回すか）\n- 利用者が投入する DSL（YAML）を読み込む機能では、Code Style の「信頼できない入力」の決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。上限の具体的な数値は設計の段で決め、決めた値の境界で確かめる。\n  - 大きさ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 入れ子の深さ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 別名（アンカー）: 展開の数が上限を超えると拒否され、別名の展開の爆発で処理が止まらない\n  - タグ: 任意の型を作るタグ（`!!` など）を含む DSL は拒否され、型が作られない\n  - 重複キー: 同じキーが重なる DSL はエラーになり、後の値で黙って上書きされない\n  - JSON Schema の `$ref`: 外部の URL を取りに行かない\n  - 拒否の応答: Problem Details の形で返り、YAML・JSON Schema の部品の例外のメッセージを含まない\n\n- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22)"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直し、実測の数字だけを報告する。 (learned 2026-09-23) \n- 負荷の環境や配備先が決まらないと測れない目標（応答時間のパーセンタイル、運用の指標、ファイルの権限など）は、Build and Test で `Unverified` とし、持ち主の段（performance-validation・observability-setup・deployment-execution）を明記して引き継ぐ。目標を緩めて「満たした」ことにはしない。 (learned 2026-09-23) \n- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない。手順は perf/README.md。 (learned 2026-09-23) \n- 負荷の試験で、アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する。 (learned 2026-09-23) \n- 同時の重なりを確実に作るため、本番のコードを変えずに、監査の書き込みの時間を測る LongSupplier（AuditEventListener で2本目を借りる直前に呼ばれる）をテストで差し替えて待ち合わせる方式にした。既存の LoginConcurrencyIT は 8 スレッドでプールの 10 に届かず、前回の失敗のログインで尽きなかった理由の1つと見られる。 (learned 2026-09-23) \n- Intent の流れに Performance Validation の段が無く、負荷の環境（使い捨ての環境）を手元で用意できるときは、k6 の試験と NMT の測定の持ち主を Build and Test とし、Unverified で引き継がずにその段で実行する。 (learned 2026-09-23) \n- 修正の前の設定（例: 上限 1g）も修正の後の環境（例: CPU 4 の VM）で流し（pre1g）、要件の前提（VM を上げても F3 が起きる）を実測で裏付ける。 (learned 2026-09-23) \n- パッケージごとのカバレッジの下限と SpotBugs の SQL_ の関門を U1 の計画に入れた。team.md の決まりだが今のビルドに無く、この Intent で最初に作る単位のため。U1 の設計の文書には無い作業。 (learned 2026-09-24) \n- U4 で既存の AuditSecretLeakIT の列の一覧に V6 の4列を足し、テストの JVM のヒープを 1g にした。前者は承認済みの V6 と必ず食い違うため、後者は構造の検査がクラスを持ち続け U3 の 10MB 超えのテストでヒープが尽きたため。どちらも計画に無い変更で、依頼者に確かめる。 (learned 2026-09-24) \n- 応答しない対象DB の TIMEOUT の確かめに、テストの中で開いた ServerSocket（受け付けて何も返さない）を使う。外の端末に頼らず確実に再現できる代わりに、本物の DB の遅延ではない。 (learned 2026-09-24) \n- パッケージごとのカバレッジの下限は新しいパッケージだけに当てた。実測で audit.service（行 77.2%）・common.health（行 79.2%）・auth.repository（分岐 50.0%）が単独で下回ったため（team.md の決まりどおり）。既存の 22 パッケージを一覧で外し、新しいパッケージは自動で対象になる。 (learned 2026-09-24) \n- 負荷の試験で接続プールが尽きたかを確かめるときは、使い捨てのアプリにだけ MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics を渡して /actuator/metrics の hikaricp の値を読み、数秒ごとの使用中の数ではなく、待ちの時間切れの累計と借りるまでの待ちの最大で判断する（配備したアプリの公開の範囲は変えない）。 (learned 2026-09-24) \n- k6 などの長い試験は caffeinate -i を付けて流し、PC の自動のスリープで要求が止まって結果が崩れるのを防ぐ（バッテリー駆動のまま 414 秒スリープし、要求が 6分52秒止まった）。内部DB に SQL で直接入れた試験用の利用者はロックの状態の行が無いため、同時のログインを流す前に1人ずつログインさせて行を作る。 (learned 2026-09-24) \n- 軽い API の性能は、投入を重ねて内部DB のファイルが膨らんだ状態（悪い側の条件）のまま測る。メモリの最大（memory.peak）を比べる試験の前は、アプリのコンテナを作り直して前の最大の値を消す。 (learned 2026-09-24) \n- Performance Validation の段が無いため、2件目の直しの負荷の試験での確かめ（Q5: A）の持ち主を Build and Test とした（project.md の学びどおり）。 (learned 2026-09-24) \n- colima の PC で ./gradlew verify を流すときは、README の DOCKER_HOST と TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE をシェルに渡さないと対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗した。付けて流し直した結果を基準とした。 (learned 2026-09-25) \n- unit-test-instructions.md の k6 inspect のコマンドは --include-system-env-vars が無いと場面の名前が undefined になり確かめにならなかった。承認済みの文書は変えず、実際には付けて流し、code-summary.md に差を記録した。 (learned 2026-09-25) \n- 1回目の verify で AccessTokenApiIT の 6 件が接続の失敗で落ちた。変更の経路に触れず、同じ時刻に Gradle の作業プロセスとの接続も時間切れだったため PC の負荷による一時的な失敗と見立て、段の中の直しの1回目としてコードを変えずに verify を流し直して通した。原因は確かめていない。 (learned 2026-09-25) \n- 依頼者の判断で、試験のイメージを mastersmith:local ではなく mastersmith:followup-fixes で作り、perf/README の手順（local を作り直す）から外れた。配備したアプリは k6 のあいだ止め、約 17 分後に同じコンテナで起動し直した。 (learned 2026-09-25) \n- 試験の後にロックの状態の行の数を数える問い合わせの列の名前を誤り、使い捨ての環境を消した後で取り直せなかった。合格の条件（ログインの checks と 500 の件数）で判定した。消す前に確かめの結果を見てから片付けるべきだった。 (learned 2026-09-25) \n- Q4（確かめられたときだけ直す）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違いうるため、追加の質問 F3 で、再現できなければ不安定と確かめられていない扱いとして統合してよいことを確かめた。 (learned 2026-09-25) \n- 要件 FR2.2（目標は計画の承認の場で決める）を満たすため、依頼者の決定（Q1: A）で、計画を書く前に dslMixed でメモリの内訳を測ることにした。測る間は配備したアプリを止める。 (learned 2026-09-25) \n- 生成で、テストの既定で MBean の登録を無効にする置き場を、計画の候補の TestDatabase ではなくテストの EnvironmentPostProcessor にした。TestDatabase を使わずに Spring を起動するテストのクラスが 15 あり、漏れるため。再現の結合テストは 12 回では伸びが小さく判定がはっきりしないため、24 回・履歴の上限 3 にした。 (learned 2026-09-25) \n- AccessTokenApiIT は、同じ例外の文言を仕組み（colima の IPv4 の転送と Java の IPv6 の待ち受けの番号の重なり）ごと再現できたことを原因の確認とみなし、依頼者の決定（D7）でテストの JVM に preferIPv4Stack を付けて直した。実際の1回目がこれで起きたかは確かめられず、今回の繰り返しでは重なりは起きなかった。 (learned 2026-09-25) \n- Test Strategy は Minimal だが、要件 FR5 と計画の「Build and Test に引き継ぐこと」のため、結合・性能・セキュリティの手順書も作り、この段で dslMixed・--storage --compact（40 回）・詰め直しの最中のログイン・refresh・dslCycle を流した。 (learned 2026-09-25) \n- 計画の dslCycle を最初の台本に入れ忘れ、配備したアプリをもう一度（約 4 分）止めて流した。負荷の試験の台本を書く前に、計画の「Build and Test に引き継ぐこと」の項目を一つずつ台本の手順と突き合わせる。 (learned 2026-09-25) \n- 実際のブラウザのアクセシビリティの検査（axe）は、誤りを出した状態と、現実に近いデータ（2語の氏名など）で、ブランドカラーとテーマのすべての組について行う。user-management の U7 の 080 で初めて、make-you-chic-ui の Avatar（頭文字が2文字のとき）と dark の FormField の誤りの文字のコントラスト不足が出た（050〜070 は誤りの状態を見ず、氏名が1語だったため出なかった）。 (learned 2026-09-28) \n- Playwright の webServer.env に置いた値は json の報告に残る。E2E の仮の資格情報はプロセスの環境変数で渡し、報告の部品の確かめと json の文字列の検索で、パスワード・トークン・メールアドレスが入っていないことを確かめる（user-management の U4 で見つかった）。 (learned 2026-09-28) \n- CI（66fe981）の verify が2回とも別々のテストの時間切れ（H2CompactionByPoolSuspensionIT の接続の待ち 10 秒、InvitationAdminPage.test.tsx の既定 5 秒）で失敗し、U4〜U7 の NFR7.1 が Not Met だったが、依頼者の決定「次のintentでコントラストと一緒に直す。このintentではこのまま進める。」で受け入れた失敗とした。team.md の「CI が失敗したら次に進む前に直す」と食い違うため test-results.md 8.1 に差を記録した。 (learned 2026-09-28) \n- k6 の登録の完了と取り消しの場面は、決定の文言（招待を VU の数だけ）ではなく既定 100 回・招待 100 件とした。トークンは1回しか使えず 10 件では p95 の意味が薄いため、U3 の NFR6.4 の「流す回数以上の招待を用意する」に合わせた。承認の場で確かめる。 (learned 2026-09-28) \n- caffeinate -i は場面ごとではなく、試験の台本全体を包む形で付ける。場面ごとに起こし直すと切れ目で守りが外れ、PC が眠る（user-management の Performance Validation で 92 秒眠り、要求が 1 分 29 秒遅れた）。遅れが出たときは pmset -g log でスリープを確かめる。 (learned 2026-09-28) \n- 応答しないメールの受け手は、Mailpit を docker pause で一時停止して作る（接続は受け付け、何も返さない）。コンテナを止めると接続の失敗になり、時間切れの確かめにならない（user-management の Performance Validation）。 (learned 2026-09-28) \n- Q1 で原因を確かめずに上限を延ばす答え（C）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違うため追加の質問 F1 で確かめ、延ばしたうえで失敗時の診断を足し、差を記録する形（B）になった。Q3 の「すべて試す」は verify で判定できない Temurin 26・logback-appender の扱いを F2 で確かめ、見送り（C）になった。 (learned 2026-09-29) \n- 診断の確かめで、上限 1 ミリ秒は Awaitility の問い合わせの間隔（100 ミリ秒）より短く設定の誤りで弾かれ、150 ミリ秒では手元で接続がすぐ 0 本になり時間切れにならなかった。上限 500 ミリ秒と、待ちの条件を一時的に満たせない形（== -1）にして時間切れを起こした。確かめた後に git checkout で戻した。 (learned 2026-09-29) \n- 警報が鳴ることは、しきい値を 50 ms に下げた警報の決まりの写しを使い捨ての環境に読み込ませて確かめた（Q1: A）。本当に遅い応答を作るより早く PC の負荷も小さいが、しきい値の値そのもので鳴ることは確かめていない。途中で ms-check-p95 のしきい値が 1000 ms ではなく 300 ms（バケットの境界に無い）と分かり、要件の前提 A3 と食い違った。 (learned 2026-09-29) \n- 表示の設定を画面の操作の直後に同期で確かめるテストが、負荷の高い CI で2件（ShellLayout・PreferencesPage）落ちた。描画の後の効果（useEffect）で反映される値は waitFor で待って確かめる。同じ形の RegistrationPage も合わせて直した。 (learned 2026-09-29) \n- Delivery Planning でのカバレッジの実測は、./gradlew verify 全体ではなく :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport で行い、jacocoTestReport.xml から手を入れる見込みのパッケージの値を読む（user-admin で約 5 分）。 (learned 2026-10-01) \n- 接続プールの見積もりを確かめる負荷の試験には、上限に届く形（プールの上限を下げた場面など）を含める。上限に届かない負荷では、見積もりが誤っていても合格する（user-admin の U3 の NFR 要件のレビュー R-02）。 (learned 2026-10-01) \n- 画面のはみ出しの確かめ（E2E・axe）では、画面全体の横のスクロールだけでなく、開いたメニュー・ポップアップなど画面に固定で置く部品が画面の中に収まることも確かめる。表の右端に置く make-you-chic-ui の Dropdown は placement に bottom-end を指定する（user-admin の配備の後のスモークテストで、行の「操作」のメニューが右へはみ出していたのを E2E 120 が拾えなかった）。 (learned 2026-10-03) \n- k6 の http_req_duration が、colima の VM の時計が負荷の間にホストより約 2 秒ずれて合わせ直されるため一部崩れた（待ちの最小 0 ms、繰り返しより長い約 3 秒の値）。1回の繰り返しが要求1つの loginSuccess では、単調な時計の iteration_duration とサーバー側の最大が合うため、判定は iteration_duration で行い、http_req_duration は並べて記録した。前の Intent の 939.6 ms も同じ影響を受けていた可能性がある。 (learned 2026-10-04) \n- 1〜6回目で繰り返しの時間の p95 が2回 1 秒を超え、依頼者が VM の時計を合わせて測り直しを求めた（F1・F2 の Other）。測り直しの7〜9回目は 1 秒を下回ったため、1〜6回目を FR2.2a の条件がそろわない回として外した（承認の場で確かめる）。時計のずれを ssh の前後のホスト時刻の中間と比べる誤った測り方で 2.2 秒と読み、測り方を直して範囲（VM−終わり、VM−始め）で記録し直した。配備したアプリは2回止めた（約 12 分と約 4 分）。 (learned 2026-10-04) \n- k6 の判定は1回の繰り返しに要求1つの場面の iteration_duration で行い、トークンは setup() で取って場面を 3 分にした。時計のずれに強い代わりに、操作が2つ以上の場面は op のタグが iteration_duration に付くかを台本で確かめる必要がある（group のレビューの R-01 を先に避けた）。 (learned 2026-10-05) \n- 上限を下げた k6 の場面を置かず、接続の数は結合テスト NavigationConnectionUsageIT で決定的に確かめる。読み取りだけで1要求1本のため詰まりが起きず、role のレビューの R-02 の「k6 では見積もりの誤りを見分けにくい」を避けられる。代わりに、負荷の下での待ちの長さは測らない。 (learned 2026-10-05) \n- 違反のテストは先の側を確定させてから書く待たない違反、上限切れは H2 の上限まで放さない形にした（Q3: A）。時間の境に合否を預けない代わりに、待った後の違反の経路は区分の単体テストだけで確かめる。 (learned 2026-10-06)"
    }
  ],
  "obligations": {
    "strategy": "standard",
    "strategy_volume": [
      "Five to eight tests per component.",
      "Unit tests plus integration tests for key boundaries.",
      "Add E2E, performance, or security tests when requirements demand them."
    ],
    "scope_floor": [
      "Keep the existing test suite green.",
      "This scope adds no extra new-test floor beyond the selected test strategy."
    ],
    "combination_rule": "Apply every selected-strategy obligation and every scope-floor obligation; neither replaces the other, and a targeted scope regression may add the narrowest necessary test type beyond the strategy default."
  },
  "plan_profile": {
    "methodology": "test-after",
    "runner_step": "Verify the existing test runner/configuration and record the exact unit-scoped command.",
    "runner_ready_before_first_test": true,
    "testable_layers": [
      "Data model / database behavior",
      "Repository / data access",
      "Business logic",
      "API / endpoint",
      "Frontend behavior"
    ],
    "steps": [
      "Project structure and production configuration skeleton.",
      "Verify the existing test runner/configuration and record the exact unit-scoped command.",
      "Data model / database behavior - implement.",
      "Data model / database behavior - write and run its tests after implementation.",
      "Repository / data access - implement.",
      "Repository / data access - write and run its tests after implementation.",
      "Business logic - implement.",
      "Business logic - write and run its tests after implementation.",
      "API / endpoint - implement.",
      "API / endpoint - write and run its tests after implementation.",
      "Frontend behavior - implement.",
      "Frontend behavior - write and run its tests after implementation.",
      "Environment/build configuration.",
      "Documentation and traceability."
    ]
  },
  "input_sha256": "sha256:1b1210c2e61bcacd9398989674a4d7c1a18abbfde6a25bf393014ab8e8f2f363",
  "contract_sha256": "sha256:0800cfde8b8915bab9d374a3c5479c6961cc23a534522745ba4170d1b16e7349"
}
```

## Build and Test に引き継ぐこと

- 本番と同じヒープ（`docker/perf/compose.yaml` の `mem_limit` 2g・イメージの `MaxRAMPercentage=50.0`）の使い捨ての環境で、上限ちょうどに近い 10 MiB の版 2 の DSL の投入とプレビューの表示（照合を含む、PostgreSQL の1種類）を `perf/dsl-timing.sh` で流し、10 秒以内・OutOfMemoryError と OOMKilled が無い・`memory.peak` が `mem_limit` を超えないことを判定する（NFR2.5、`logical-components.md` 4節）。ヒープの使用（`jvm.memory.used` の heap、10節 Q4: A）も記録し、T4 の 256〜384 MB と比べる。
- `--busy` で2つの投入を同時に送り、成功1つと 503 `DSL_BUSY` 1つ・OOMKilled が無いことを判定する（NFR2.5・NFR2.6、`cicd-pipeline.md` 5.2・6節）。`memory.peak` の比べ先は1件の投入の値（Infrastructure Design R-03）。
- 想定の規模の既定の DSL（版 2）の大きさ（試算 約 6.3 MB、上限 10 MiB の内。超えたら上限を上げるかを依頼者に諮る。NFR2.7）。
- 英語の文言（`perf/dsl-timing.sh --lang`、NFR2.8。版の誤り・深さの誤り・`SCHEMA_MISMATCH`）。
- 冷えた状態の1回目の時間（NFR 設計 R-02。既存の「first」の測りを記録する）。
- 任意: 再起動の直後の適用と投入の重なり、または DSL の投入と role の権限の YAML の確かめの重なり（NFR 設計 R-07。role の B6 の後）。足りなければ新しい排他を足すかを依頼者に諮る（`security-design.md` 4.9）。
- `verify` の全体の時間（B2 の 13 分 56 秒との比べ）と、対象DB の3種類の結合テストが毎回流れていること。
- Performance Validation: k6 の `dslLight`・`dslCycle`・`dslMixed` を版 2 の DSL（`DSL_FILE` に `make-large-dsl.mjs` の出力）で流す（NFR2.6）。

## 配備の段へ引き継ぐこと

- 版を上げる入れ替え（`logical-components.md` 7節の 1、AC6.1.6）: 版 1 の適用中の DSL がある内部DB で入れ替えると、起動時に DSL が無い扱いになり、DSL の画面に「適用中の DSL は使われていません」の注意が出る。入れ替えの後に、版 1 のプレビューが残っていれば先に破棄し（表示は 422）、版 2 の既定の DSL を生成してプレビューを確かめて適用する。監査に残る操作（生成・適用・破棄）は行う前に依頼者に伝える。
- 前の版への戻し（7節の 2、AC6.1.8・Q2: A）: 戻す前に DSL の画面で版 2 のプレビューを破棄する。戻した後は前の版で既定の DSL（版 1）を生成し直して適用する。戻しの練習で、前の版の起動・DSL が無い状態・生成し直しの3つを確かめる。NFR 設計 R-03 の残り（破棄できないときの代わりの手順、前の版で版 2 の履歴の行を復元しないこと）を手順に足す。
- `server.tomcat.max-swallow-size`（11MB）は Dockerfile・`.env` を変えずにイメージの中の `application.yaml` で効く。戻すと前の版の既定（2 MB）に戻る。
- 前の版のイメージへ戻すと `/dsl/dsl-schema-v1.json` が配られ、v2 は配られなくなる（`cicd-pipeline.md` 3節）。
