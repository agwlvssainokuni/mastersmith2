# CI/CD の流れ — U5 u5-container-perf-docs（Intent 261029-reference-lookup-cleanup）

- 単位: U5 `u5-container-perf-docs`（種類 `packaging`、Bolt B6）。
- CI（`.github/workflows/ci.yml`）は既にあるものを変えない。この文書は、新しい流れの設計ではなく、B6 の変更が既存の関門をどう通るかと、手元のコンテナの入れ替えの確かめ・戻しの記録である（`project.md` の Deployment「CI が既にある段は既にあるものの記録として書く」）。
- 配備先は開発者の PC 上のコンテナだけ。Mailpit・lgtm の入れ替えは Deployment Execution の段ではなく、この単位の作業として行う（`unit-of-work.md` の U5、要件 FR6）。アプリの成果物（WAR・`mastersmith:local`）の配備は無い。

## 1. 変更の形とブランチ

| 項目 | 形 | 根拠 |
|---|---|---|
| 作業ブランチ | `develop` から作る短命のブランチ（名前はコード生成の計画で決める。例 `feature/261029-reference-lookup-cleanup-b6`） | `team.md` の Way of Working |
| 変わるファイル | `perf/README.md`・`perf/k6/scenarios.js`（宣言の場所とコメント）・必要なら `perf/k6/thresholds-test.sh`・`perf/dsl-mixed-memory.sh` のコメント・`aidlc/` の下の記録 | infrastructure-specification.md 5節 |
| 変わらないファイル | `compose.yaml`・`docker/perf/compose.yaml`・`perf/versions.env`・lockfile・`.github/`（`dependabot.yml`・`workflows/`）・`README.md` | security-design.md 6節（NFR9.81）。統合の前に `git diff --stat` とファイルの名前で確かめる |
| コンテナの入れ替え | リポジトリのファイルを変えない手元の操作。記録だけを `aidlc/` の下に置く | `compose.yaml` はすでに 0.38.0・v1.32.0 に固定済み |
| 統合 | `develop` へ squash（1 Bolt が 1 コミット、日本語の件名）。サブモジュールの固定先は変えないため fast-forward の例外は使わない | `team.md` の Way of Working、`bolt-plan.md` の B6、security-design.md 7節 |
| push | 依頼者が行う。AI は push しない | `team.md` の Way of Working |

### 1.1 入れ替えを専用の区切りにすること

- コンテナの入れ替え（A）と perf の整理（B）は互いに頼らないため、記録とコミットを分ける。A の記録（前後の `docker ps -a`・`docker image ls`・照合の判定・了承・戻しの結果）は A だけのコミットにし、B の README・台本の変更と混ぜない。コミットの区切りと提案はコード生成の計画で決め、積む前に依頼者の承認を得る（`project.md` の Change Control。段の初稿と承認の時のコミットだけは尋ねずに行う）。
- A はリポジトリのイメージの行を変えないため、`team.md` の「固定先の更新を専用のコミットにする」の対象（サブモジュール）ではない。squash の統合で `develop` の 1 コミットになり、細かい区切りは作業ブランチと監査ログに残る。

## 2. Dependabot の固定先の一覧との関係

| 確かめ | 形 | 結果の扱い |
|---|---|---|
| 固定先の一覧の決まり | `.github/dependabot.yml` の「ダイジェストでのイメージの固定」のコメントが、Dependabot が見ない固定先（`docker/perf/compose.yaml`・`TargetDbImages`・`perf/versions.env`・`perf/README.md`（`grafana/otel-lgtm`）・`README.md`（Mailpit）・`docker/hikari-pool.sh`）を書き、イメージを上げるときは同じ版とダイジェストに手で揃えると決めている（`team.md` の Deployment） | U5 はイメージを上げないため、一覧も固定先も変えない |
| 今のそろい具合（2026-10-30 の読み取り） | lgtm: `compose.yaml` 83 行と `perf/README.md` 433 行が `0.38.0@sha256:75cdf892…` で一致。Mailpit: `compose.yaml` 121 行・`docker/perf/compose.yaml` 150 行・`README.md` 731 行が `v1.32.0@sha256:0790692b…` で一致 | そろっている。U5 は手元のコンテナをこの値にそろえるだけ |
| 整理の後 | `perf/README.md` の整理で行が動いても、`grafana/otel-lgtm` の版とダイジェストの文字列が `compose.yaml` と一致すること（security-design.md 5.6節 Q6） | 一致しなければ統合しない |

## 3. 統合の前の関門（stage → gate）

| 順 | 関門 | 合格 | 根拠 |
|---|---|---|---|
| G1 | A の完了（了承 A0 → 照合 → 確かめ → lgtm を止める → 後の記録） | security-design.md 2.1節の各関門。戻した（R1〜R5）ときは理由と段ごとの結果が記録にある | NFR8.81〜NFR8.85 |
| G2 | 記録の秘密の検査 | 記録のファイルに `gitleaks dir` が 0 件、`Config.Env` の語と `eyJ` の形の検索が 0 件 | NFR8.86、security-design.md 3.3節 |
| G3 | B0: U4（B5）が `develop` に統合済み | `perf/README.md` に U4 の伏せ字の節がある | NFR8.89、5.1節 |
| G4 | 線の前後の比べ | `k6 inspect --include-system-env-vars`（`perf/versions.env` の `K6_IMAGE` を `docker run --rm`、`--env-file` なし）の `thresholds` と場面の名前の集合の差が空。grep の線の組の差が空 | FR7.4・NFR8.87・NFR8.88、5.3節・5.4節 |
| G5 | 1 か所との一致 | `bash perf/k6/thresholds-test.sh`・`perf/dsl-mixed-memory-test.sh` を手で流して終了コード 0 | NFR8.88、5.5節 |
| G6 | README の事後の検索と行の集合 | 5.6節 Q1〜Q7（`gitleaks dir` の指摘 0 件を含む）と 5.7節 L1〜L4 が通る | NFR8.89・NFR8.90・NFR9.82 |
| G7 | 差の範囲 | 作業ブランチの差にイメージの行・`perf/versions.env`・lockfile・`.github/` が無い | NFR9.81、6節 |
| G8 | 1 コマンドの検査 | `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を付けた `./gradlew verify` が通る（U4 の `perfEvidenceScan` も中で流れる）。長い実行は `caffeinate -dims` で包み、眠りが無かったことを確かめる | NFR5.81、`team.md`・`project.md` の学び |

- E2E は流さない。この Bolt は文書・台本のコメント・`aidlc/` だけを変え、frontend の本体と認証・権限・業務データの `src/main` に触れないため（`team.md` の Way of Working、`bolt-plan.md`）。E2E は Intent の最後（Build and Test）とリリースの前に流す。
- `perf/k6/thresholds-test.sh` は `./gradlew verify` に入れない（新しい関門を足さない。tech-stack-decisions.md 2節）。G5 で手で流して記録する。

## 4. CI（統合の後の再確認）

| 項目 | 形 |
|---|---|
| きっかけ | 依頼者が `develop` を push したとき（既存の `ci.yml`） |
| 中身 | `./gradlew verify` の 1 job（フォーマット → リンタ → ライセンスヘッダー → ビルド → 単体 → 結合 → カバレッジ → セキュリティ検査）。変えない |
| U5 に関わる点 | `gitleaksScan` は全ブランチの履歴を見る（`project.md` の学び）。記録に秘密を置かないこと（G2）がここでも効く。`perfEvidenceScan`（U4）が `aidlc/` の下の記録の JWT の形を見る |
| 失敗したとき | `team.md` の Testing Posture の「不安定なテストと CI の失敗」の決まりで扱う。試行ごとの結果（`gh run view --json attempt,jobs`）を記録する（`project.md` の学び） |
| 成果物 | WAR の保存は既存のまま。U5 は WAR の中身を変えない |

## 5. 入れ替えの確かめと戻し（手元の操作）

### 5.1 前と後の確かめ

| 時点 | 確かめること | 記録の形 |
|---|---|---|
| 前（A1） | `docker ps -a` の名前と状態、`grafana/otel-lgtm`・`axllent/mailpit` の `docker image ls`、コンテナの `--format` の取り出し | security-design.md 3.1節の項目だけ |
| 作り直しの直後（A4・A6） | `RepoDigests` の照合（`team.md` の Deployment）、Mailpit の受信、lgtm の `/api/health` とファイルの `uid` | 判定・状態コード・件数 |
| 後（A9） | A1 と同じ項目、0.34.0 の lgtm のイメージが残っていること、lgtm が止まっていること、A2・A5a で止めたものが戻っていること | 前後の比べの判定 |

### 5.2 戻し

| 対象 | 戻し方 | 失うもの |
|---|---|---|
| lgtm | R1: 手元の 0.34.0 で既存のボリュームのまま作り直す → R3: ボリュームを名前の完全一致で確かめて消し 0.34.0 で作り直す → R5: 止めて記録し依頼者に諮る（security-design.md 4.1節）。`compose.yaml` は書き換えず、一時の上書きのファイル（リポジトリの外）で 0.34.0 を指し、戻しの後に消す | R3 のとき溜まった監視のデータ（了承済み）。ダッシュボードと警報の決まりは失わない |
| Mailpit | 戻さない。照合が一致しなければ原因を記録して作り直しをやり直す | 溜まったメール（了承済み） |
| perf の整理（B） | 統合の前なら作業ブランチを捨てる。統合の後なら squash の 1 コミットを `git revert` する（文書・コメントだけのためアプリの戻しは要らない） | 無い（整理の前の README の写しはリポジトリの外にも置く） |
| アプリ | 戻しの対象外（作り直さない。起動したときは確かめの後に元の止まった状態へ戻す） | 起動で救済が働いたときの監査の行と状態（A0 (iii) の了承の範囲。止まっていた状態へ戻しても残る） |

- この単位では `docker image rm`・`docker image prune`・`docker system prune` を行わない（戻し先を残す。FR6.3）。

## 6. 秘密の扱い

- CI に秘密を足さない（新しい GitHub の Secrets も環境も無い）。
- 手元の操作では `.env`・`.env.*` を開かず、`docker compose config` の全体と `docker inspect` の全体の JSON を表示しない。Mailpit の確かめのメールは予約のドメインだけで、件名と本文の値を記録しない（security-design.md 2.3節・2.5節・3.2節）。
- 整理の前後の写しと k6 inspect の取り出しは、リポジトリの外（ホームの下の権限 700 の置き場）に置き、コミットしない。

## 7. 上流との差

- security-design.md 7節は統合を squash とだけ書く。この文書は、A の記録と B の変更をコミットで分けることを足した（squash の後は 1 コミットになるため、作業ブランチの上の区切り）。区切りの提案はコード生成の計画で行う。
- G8 の `caffeinate -dims` は NFR 設計に無いが、長いテストの決まり（`project.md` の Testing Posture の学び）を当てたもので、新しい関門ではない。
- perf の整理の戻し（`git revert`）は NFR 設計に書かれていない。文書と台本のコメントだけの変更のため、通常の戻しの形を書いた。

## 8. 後の段に回すこと

| 段 | 回すこと |
|---|---|
| コード生成（計画） | 作業ブランチの名前、A と B のコミットの区切り、G1〜G8 の実際のコマンドの列、0.34.0 を指す一時のファイルの形 |
| コード生成 | G1〜G8 の実行と記録、依頼者の承認の後の squash の統合 |
| Build and Test | CI の試行ごとの結果の記録、E2E の全件、整理した `thresholdsFor` への新しい場面の線の追加 |

## Assumptions & Open Questions

None.

## Sources

- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u5-container-perf-docs/nfr-design/security-design.md`（2節〜7節・11節）
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u5-container-perf-docs/nfr-requirements/security-requirements.md`・`tech-stack-decisions.md`
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u5-container-perf-docs/infrastructure-design/infrastructure-design-questions.md`
- performance-design・scalability-design・reliability-design・observability-design・logical-components: U5 は種類 `packaging` のため NFR 設計でこれらを作っていない。戻しと確かめは security-design.md を正とした
- functional-spec: U5 は種類 `packaging` のため機能設計を作っていない
- components: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/domain-design/components.md`（FR6・FR7 は部品なし）
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`（FR6・FR7）
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/units-generation/unit-of-work.md`（U5）
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/delivery-planning/bolt-plan.md`（B6・E2E の扱い・引き継ぎ）
- 読み取りで確かめたもの（2026-10-30）: `.github/dependabot.yml`（固定先の一覧の決まり）、`.github/workflows/ci.yml`（job の形）、`compose.yaml`・`docker/perf/compose.yaml`・`perf/README.md`・`README.md`（Mailpit・lgtm の固定先）
