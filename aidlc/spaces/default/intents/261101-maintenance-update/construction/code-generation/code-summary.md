# コード生成のまとめ（Intent 261101-maintenance-update）

計画 `code-generation-plan.md` の ①〜⑤ の結果をまとめる。細かい確かめの記録は `generation-notes.md` にある。

## 1. ブランチと統合

| まとまり | ブランチ | 統合 | develop のコミット |
|---|---|---|---|
| ① サブモジュールの固定先 | `fix/261101-submodules` | fast-forward | `28dd694b`（固定先の更新）・`6cbf5cfb`（記録） |
| ② 実行環境と依存 | `feature/261101-runtime-deps` | squash | `064be35a` |
| ③ CI・コンテナのイメージ・道具 | `feature/261101-ci-images` | squash | `a29f6dbf` |
| ④ 小さな直し（P1・T1〜T3） | `fix/261101-small-fixes` | squash | `5950d786` |
| ⑤ README の書き直しと docs/ への分割 | `docs/261101-readme` | squash（指揮役が依頼者の承認を得て行う） | — |

## 2. 版の前後

| 対象 | 前 | 後 |
|---|---|---|
| Java（toolchain・CI） | 25 | 25（変えず。最新のパッチ 25.0.5+8 が入る） |
| JRE のイメージ（Dockerfile・`PERF_JRE_IMAGE`） | `25.0.4.1_1-jre-noble` | `25.0.5_8-jre-noble`@`a98128a5…` |
| JDK のイメージ（`docker/hikari-pool.sh`） | `25.0.4.1_1-jdk-noble` | `25.0.5_8-jdk-noble`@`95e350ad…` |
| Node | 24 | 26（v26.13.0） |
| Gradle の wrapper | 9.8.2 | 9.8.2（最新） |
| Spring Boot | 4.1.2 | 4.1.2（最新の安定版） |
| palantir-java-format | 2.98.0 | 2.103.0（整形の変化 27 ファイル。折り返しと字下げだけ） |
| SpotBugs | 4.10.4 | 4.10.5 |
| JaCoCo | 0.8.15 | 0.8.16 |
| cherry-mustache（`libs.versions.toml`） | 0.1.0 | 0.1.1 |
| eslint・i18next・react-i18next | 10.13.0・26.5.11・17.1.9 | 10.14.0・26.6.0・17.1.10 |
| make-you-chic-ui | `cab4207` | `cae5fed` |
| java-mustache-processor | `8d44c36`（0.1.0） | `d61ba6d`（0.1.1） |
| gradle/actions/setup-gradle | v6.4.0 | v6.4.1 |
| OSV-Scanner | 2.6.0 | 2.7.0 |
| Gitleaks | 8.30.1 | 8.30.1（最新） |
| Mailpit | v1.32.0 | v1.33.0 |
| otel-lgtm | 0.38.0 | 0.39.0 |
| otel-collector | 0.163.2 | 0.164.0 |
| postgres・mysql・mariadb・k6 | 18.6・26.10.0・13.0.2・2.3.0 | 版は同じ（最新）。組み直しの新しいダイジェストにそろえた |

## 3. 見送ったもの・持ち越し

- **typescript 7.0.2**: 判定基準 3（上流の未対応）。`@typescript-eslint/*` 8.72.1 の peer が `<6.1.0`。`.github/dependabot.yml` の `ignore` に試した日と基準を書いた。
- **A2（make-you-chic-ui の `braces`）**: 上流でも 3.0.3 のままで、直った版が無い。開発時だけの依存で警告の扱い。
- **A3（依頼（5）の1ページの件数の選択と横のスクロールの領域）**: 上流の `cae5fed` に口が無く、frontend の自前の `ScrollRegion`・`PageSizeSelect` の置き換えを見送った。
- **OSV の Moderate 3件**（統合を止めない重大度。依頼者の決定 A で上書き・`overrides` をせず持ち越し）: `io.opentelemetry:opentelemetry-api` 1.62.0（Spring Boot の管理の版）、`org.mariadb.jdbc:mariadb-java-client` 3.5.10（同じ）、frontend の `minimatch` 10.2.6（開発時だけ、typescript-eslint の範囲に縛られる）。Spring Boot が管理の版を上げたとき、typescript-eslint が範囲を広げたときに取り込む。

## 4. 連動する固定の結果（FR3.2）

| 固定 | 結果 | 理由 |
|---|---|---|
| `opentelemetry-logback-appender` 2.28.1-alpha | 残す | 管理の OpenTelemetry が 1.62.0 のまま（最新の appender は 1.67.0 を求める） |
| networknt 3.0.6 | 残す | 管理の Jackson が 3.1.7 のまま（3.0.8 は Jackson 3.2.1 を求める） |
| H2 の `-Dh2.compactThreads=1`（3か所） | 残す | 管理の H2 が 2.4.240 のまま |

## 5. 小さな直し（④）

- **P1（FR9.1）**: MySQL 26.10.0 で、漢字などの長い値（約 8,000 字を超える）のカラムで一覧を並べ替えると 1ページ目から 1038（Out of sort memory）になることを確かめた。依頼者の決定 案 A で、一覧の並び（`RowSqlBuilder.orderBy`）を参照先と同じ `RowDialect.sortExpression`（文字の型を先頭 100 字で切る）にそろえ、内側と外側の両方に当てた。再現のテストは `*DeepPageSortIT`（3種類の DB）で、直す前は MySQL の 3 件が 500、直した後は 9 件とも通過。先頭 100 字が同じ値どうしは主キーの順で並ぶ。
- **T1（FR9.2）**: `perf/redact-k6-evidence.mjs` を Vitest の `coverage.include` に足した（行 94.23%・分岐 90.65%）。`coverage.allowExternal: true` を足し、下限と除外は変えていない。
- **T2（FR9.3）**: 同じ道具に ESLint の規則（`frontend/eslint.config.js` の `perfToolRule`）と、`// @ts-check` と JSDoc の型の検査（`frontend/tsconfig.perf-tool.json`）を当てた。Gradle のタスク `perfToolEslint`・`perfToolTypecheck` が verify と CI から流れる。
- **T3（FR9.4）**: `BusinessDataWriteService.warnReference` は `stage` を必ず出し、null のときは固定の名前 `UNKNOWN` にする。直す前に落ちるテストを足してから直した。

## 6. README の書き直し（⑤、FR8）

- README.md（1,678 → 1,660 行）と perf/README.md（1,753 → 1,558 行）から、Intent・単位・Bolt の名前と経緯を外し、perf/README.md の「## 履歴」（H1〜H76）と、本文からその節を指す参照を消した。
- README.md の make-you-chic-ui の固定先を `cae5fed`（java-mustache-processor は `d61ba6d`）に直し、固定先の移り変わりの列を消した。
- コマンドの行と大文字の名前の一覧の前後の比べ: README.md は一致。perf/README.md の差は、イメージのタグの見本 `<Intent の名前>` → `<試験の名前>`（2行）、例のタグ `user-admin-followup` → `perf-run`、Bolt の名前を含むコメント（2行）、履歴の参照の削除（1行）だけ。名前の一覧は両方とも一致。
- perf/README.md の合否の線と物差しの値（「今の基準の一覧」）は変えていない。
- **依頼者の決定 B（2026-11-02）**: README.md を入口（概要・前提の道具・取得と準備・開発時の起動・コンテナでの起動と確認・文書の目次・ライセンス）にし、ほかの節を `docs/` の主題ごとの 12 ファイル（`verification`・`deployment`・`internal-db`・`configuration`・`mail`・`target-db`・`dsl`・`users`・`roles-and-permissions`・`operations`・`appearance`・`extension-points`）へ移した。README.md は 1,660 行・304,503 バイト → 161 行・21,853 バイト。中身は変えず、移った先で切れる節の参照だけを直した。
- README.md と docs/ を合わせたコマンドの行（236 行）と大文字の名前（247 個）を `5950d786` の README.md と比べ、違いは参照の直しの1行（戻し方のコメント「上のスモークテスト」→「README.md のスモークテスト」）だけ。
- README の節を参照するほかのファイル（`.env.example`・`.env.targetdb.example`・`.gitignore`・`compose.yaml`・`.github/dependabot.yml`・`build.gradle.kts`・`application.yaml` のコメント・`docker/`・`perf/`・`frontend/e2e/`・`TargetDbSampleInitScriptIT` のコメント）を移った先に直した。Flyway の移行のファイルの「README の「戻し方」」は触らず、README.md の目次から `docs/deployment.md` の「戻し方」へたどれるようにした。`frontend/src` の1か所（`useUnsavedGuard.ts` のコメント）と `team.md` の1か所（書き込みのアカウントを「README と見本の初期化に書く」）は直さずに指揮役へ報告した。

## 7. 計画との差

1. ① の verify と E2E は、手元の Node を一時的に 24 で流した（依頼者の決定 A。Node を 26 にそろえたのは ②）。
2. ② で palantir-java-format 2.103.0 の整形の変化のため `spotlessApply` を当てた（27 ファイル、折り返しだけ）。`businessdata` の `src/main` に触れたため E2E も流した（計画どおり）。
3. ② の verify は眠りの入った回を使わず流し直した（4回目を基準）。
4. ③ で版の変わらない postgres・mysql・mariadb・k6 も、組み直しの新しいダイジェストにそろえた。verify の出力を一時ファイルに受けて件数を読み、読んだ後に消した。
5. ④ の P1 は 1038 が起きたため、計画の分岐どおり生成を止めて依頼者に諮り、案 A で直した。再現のテストを MySQL だけでなく3種類の DB の子のクラスにした。T1 で `coverage.allowExternal` を足した。T2 の ESLint は Gradle のタスクから根で呼ぶ形にした。T2 の試しで一度 `sed -i.bak` を使って直ちに戻した。
6. ⑤ の書き直しは、節の並びを大きく入れ替えず、今の並び（前提・準備 → 検査・E2E → 起動と配備 → 運用 → 環境変数 → 機能ごとの設定と API → 監視 → 監査 → 画面 → 差し込み口 → ライセンス）のまま中身を書き直した。並べ替えは差が大きくなり、コマンドの行の前後の比べと参照の確かめが難しくなるため。
7. ⑤ の Step 24 の「書き直しの前の一覧の保存」は、一覧の全文を貼らず、取り出し方のコマンドと、前の版（`5950d786`）の件数と SHA-256、大文字の名前の一覧を `generation-notes.md` の付録に置いた（前の版は git から同じ一覧を取り直せる）。
8. ⑤ で `backend/src/main/resources/application.yaml` のコメント2行を直した（参照の切れの直し。文書だけのブランチの例外）。
9. ⑤ の書き直しの途中で、空に置き換えた行末の断片の後の改行が一緒に消え、見出しの後の空行が13か所・コードブロックの1行が隣とつながった。すべて戻し、前後の比べで確かめた。
10. **依頼者の決定 B（2026-11-02）**: README.md の書き直しの後も重いままだったため、計画の「節の並びを大きく変えない」と違い、README.md を入口にして詳しい節を `docs/` の 12 ファイルへ移した（中身は変えない。説明の文を短く書き直す案 C は選ばれていない）。参照の直しは文書だけのブランチの例外として、設定のコメント・失敗の案内の文・テストのファイルのコメントと文言にも及んだ（`generation-notes.md` の ⑤ の「決定 B」の表）。

## 8. Build and Test に引き継ぐこと

計画の「Build and Test に引き継ぐこと」の1〜5のとおり（E2E の全件と axe の違反 0 件、`:backend:cleanTest :backend:cleanIntegrationTest` 付きの verify でのカバレッジの実測、統合の後のすべてのコミットの CI の試行ごとの確かめと検査の job の時間、負荷の試験（FR10）、配備と片付けと `main` への取り込み（FR11）は Deployment の段）。あわせて次を確かめる。

- CI の記録で、`actions/setup-java` が入れた Java のパッチが Dockerfile の JRE と同じ 25.0.5+8 であること（計画の Step 6）。
- 見本の PG は E2E の上乗せのまま `127.0.0.1:15432` を開けている。Build and Test の E2E の後に、上乗せ無しで作り直して閉じる（③ の依頼者の決定）。
- Dependabot の PR #83・#84・#85 を、依頼者の push の後に閉じる（③ の依頼者の決定）。
