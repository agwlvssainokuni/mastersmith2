# 配備の記録（deployment-log）

Intent 261015-deps-ui-carryover の配備の記録です。手順は `operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md` のとおり行いました。

## 1. 配備した版

| 項目 | 値 |
|---|---|
| 配備したコード | `develop` の `776738e`（その後のコミットは `aidlc/` の下の記録だけ。順 1 で `git diff 776738e HEAD -- . ':!aidlc'` が空と確かめた） |
| WAR | `backend/build/libs/mastersmith.war`、SHA-256 `71bcb69d389de093b97588e14c5ba0db4cc436b02788d8abdf0b89dcfadaae40`（順 2 で作り直した。中に `dsl/parse/SafeYamlParser.class`・`dsl/domain/DslModel.class`・`static/assets/noto-sans-jp-japanese-400-normal-*.woff2` があることを確かめた） |
| 新しいイメージ | `mastersmith:local`（`sha256:f103aee1702a…`、土台 `eclipse-temurin:25.0.4.1_1-jre-noble`） |
| 戻し先 | `mastersmith:pre-deps-ui-carryover`（`sha256:e0a5825d257c…`、前の Intent の版） |
| Mailpit | `axllent/mailpit:v1.31.5` に入れ替えた（Q1: A、受け手の中身は消えた） |
| 内部DB のスキーマ | 変わらない（版 12。Flyway「No migration necessary」） |

## 2. 時刻と停止の時間（UTC）

| 時刻 | 出来事 |
|---|---|
| 2026-10-15 23:49:32 | T0（監査の数え始め）。`docker compose stop app` |
| 23:49:32〜 | 内部DB を `~/.mastersmith-backup/mastersmith-data-202610160849-pre-deps-ui-carryover.tgz` へ複写（配備の前のバックアップ）。複写を読み取りで開いて数えた（4節） |
| 23:49:54 | `docker compose --profile targetdb-postgres up -d --build app` の後に healthy（停止から **22 秒**） |
| 23:50 頃 | `docker compose --profile mail up -d mailpit`（v1.31.5、healthy） |
| 23:50〜00:00 | スモークテスト S1〜S9（`smoke-test-results.md`） |
| 2026-10-16 00:00:22〜00:00:33 | S10 の配備の後の複写（`mastersmith-data-202610160900-after-deps-ui-carryover.tgz`）。停止から healthy まで **11 秒** |

## 3. 判断

- **救済の見込み**（Q1: A）: 依頼者は前の配備の後に初期管理者を変えていないと答え、入れ替えの前の複写でも停止なし・印ありだった。起動のログは「初期管理者は既にいるため、作成しませんでした」の INFO 1件で、救済は働いていない。
- **DSL の最も長い行**（D-5: A）: 入れ替えの前の複写で `dsl_applied_revisions` 6 行の最も長い行が 58、`dsl_previews` 0 行。上限 65,536 の内のため入れ替えを続けた。
- **配備の完了**: healthy とスモークテスト S1〜S10 がすべて通った（`team.md` の Deployment）。

## 4. 入れ替えの前の複写で数えたもの（値は表示していない）

| 数えたもの | 結果 |
|---|---|
| `dsl_applied_revisions` の行・最も長い行（コードポイント） | 6 行・58 |
| `dsl_previews` の行 | 0 |
| `.env` の初期管理者の利用者の `suspended`・`admin_flag` | false・true（1 行） |

- 数え方: 複写をホームの下の権限 700 の作業用のディレクトリに展開し、H2 2.4.240 の JDBC で `ACCESS_MODE_DATA=r` で開いた。リポジトリの外の小さな Java の道具で数だけを出し、作業用のディレクトリは消した。

## 5. `main` への取り込み・push・Dependabot

- `main`（`c20dffa`）は `develop` の祖先で、fast-forward できることを確かめた。依頼者の決定で、この段の記録のコミットの後に取り込む（結果は 6節に追記する）。
- push は依頼者が行う。push の後に Dependabot のプルリクエストの状態を確かめる。

## 6. 取り込みの結果

（取り込みと push の後に追記する）

## Sources

- `operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`
- `operation/deployment-execution/deployment-execution-questions.md`（Q1: A、Q2: A）
- `construction/build-and-test/test-results.md`（配備の前の検査の結果）

## Assumptions & Open Questions

- `memory.peak` の Not Met（Build and Test）は持ち越し。配備の後に性能の問題が出たときは `rollback-runbook.md` の 2節で戻すかを依頼者に諮る。
