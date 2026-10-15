# 配備の流れの構成（cd-config）

Intent 261015-deps-ui-carryover（前の Intent の振り返りの第1の束と第2の束）の配備の流れです。

- **配備先**: これまでどおり、開発者の PC 上のコンテナ（`compose.yaml`、プロジェクト名 `mastersmith`）。
- **配備の仕方**: 自動の配備は持たず、依頼者の承認のうえで手で配備する（`team.md`・`project.md` の Deployment）。本番配備の承認者は依頼者。
- **この文書の元**: CI Pipeline・Infrastructure Design の段が無いため、前の Intent の配備の手順（`aidlc/spaces/default/intents/261004-role-menu/operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`。以下「前の手順」）と、リポジトリの `Dockerfile`・`compose.yaml`・README を正とし、今回の差だけを書く（`project.md` の Deployment の学び）。
- **決定**: `deployment-pipeline-questions.md`（Q1: A、Q2: B、まとめの確認 Looks correct）。

## 1. 配備する版と、今動いている版

| 項目 | 値 |
|---|---|
| 今動いている版 | イメージ `mastersmith:local`（`e0a5825d257c`、2026-10-15 07:03 JST に作成、healthy）。前の Intent（261004-role-menu）で配備した版 |
| 配備する版 | `develop` の先頭。アプリのコードは `776738e`（その後のコミット `fb6f5e7`・`2fe08ae` は `aidlc/` の下の記録だけ）。CI は `776738e` の2回目の試行で success（`construction/build-and-test/test-results.md` 3節） |
| 内部DB のスキーマ | 変わらない（V12 のまま。この Intent に移行は無い） |
| `.env`・`compose.yaml` の app | 変えない（メモリの上限 2g、`MaxRAMPercentage` 50。Build and Test の FR9.3 の決定で据え置き） |
| `Dockerfile` | 土台のイメージを `eclipse-temurin:25.0.4.1_1-jre-noble`（ダイジェストで固定）に上げた（Java 25.0.4 → 25.0.4.1） |
| 戻し先 | 入れ替えの前に今のイメージに付けるタグ `mastersmith:pre-deps-ui-carryover` |
| Mailpit | `axllent/mailpit` v1.31.2 → v1.31.5 に入れ替える（Q1: A。受け手の中身は消える） |
| 見本の対象DB・otel-lgtm | 対象DB（`mastersmith-targetdb-postgres-1`）は入れ替えない。otel-lgtm は profile で見たいときだけ起動し、次の起動で 0.36.0 になる |
| 実行環境 | colima の VM は CPU 4・メモリ 6GiB。`app` の上限は CPU 4・メモリ 2g（既定） |

## 2. 今回の変更と、配備への影響

| 変更 | 配備への影響 |
|---|---|
| 土台の JRE（25.0.4.1）・依存の更新（Gradle・npm の 23 件） | 起動とヘルスチェックで確かめる（Build and Test で同じ版のイメージを使い捨ての環境で起動し、負荷の試験も済ませた） |
| make-you-chic-ui `a9309c4`（Dropdown の収まり・畳んだサイドバーの印・トップバーの文言） | 画面の部品が替わるため、入れ替えの後に開いているタブを読み込み直す。スモークテストで見た目を確かめる |
| 1行の長さの上限（65,536） | 配備済みの内部DB の DSL が上限の内であることを、入れ替えの前の複写で測り直す（D-5: A。4節）。超えていたら配備を止めて依頼者に諮る（起動で適用中の DSL が読めなくなるため） |
| 漏えいの直し（DSL の誤り・navigation・`DslModel` の文字列） | 配備への手当ては無い |
| 起動時に落としたメニューの項目の数（状態の応答に項目を足した） | 配備への手当ては無い（前の版は項目を返さないだけ） |
| 書体 `@fontsource/noto-sans-jp` 5.3.1 | スモークテストで日本語の表示を確かめる（Build and Test の T-15） |
| 新しい jdk の運用の道具（`docker/hikari-pool.sh`） | 新しい JRE のアプリに対して動くことを確かめる（Build and Test の T-14） |

## 3. 流れ

| 順 | 段 | すること | 行う人 | 承認 |
|---|---|---|---|---|
| 1 | 関門 | アプリのソースに未コミットの変更が無いこと（`aidlc/` の下は除く）と、`git diff 776738e HEAD -- . ':!aidlc'` が空であることを確かめる。verify・CI・E2E は Build and Test の結果を正とし、流し直さない（3.1） | AI | — |
| 2 | WAR の作り直し | `./gradlew :backend:bootWar` で WAR を作り直し、SHA-256 を記録する。WAR の中に今回のクラス（`SafeYamlParser` の行の上限、`DslModel` の `toString` など）と画面のビルド（新しい書体のファイル）があることを、中身の一覧で確かめる（`Dockerfile` は手元の WAR を写すだけのため。`project.md` の学び） | AI | — |
| 3 | 戻し先のタグ | `docker tag mastersmith:local mastersmith:pre-deps-ui-carryover`。付けた後に ID が `e0a5825d257c` で始まることを確かめる | AI | — |
| 4 | 救済の見込み | 依頼者に、前の配備の後に初期管理者のパスワードを画面で変えた・止めた・印を外したことがあるかを尋ねる（前の手順の順 3） | AI と依頼者 | — |
| 5 | 入れ替えの前の複写（4節） | 監査の数え始めの時刻 T0 を控え、アプリを止め（`docker compose stop app`）、内部DB を `~/.mastersmith-backup/`（権限 700）へ複写する（配備の前のバックアップ）。複写を読み取りで開き、DSL の最も長い行と、初期管理者の停止と印の有無だけを数える | AI | 依頼者の承認（アプリが止まるため） |
| 6 | 入れ替え | `docker compose --profile targetdb-postgres up -d --no-build app` は使わず、`docker compose --profile targetdb-postgres up -d --build app` で順 2 の WAR からイメージを作り直してアプリだけを入れ替え、healthy を待つ。続けて `docker compose --profile mail up -d mailpit` で Mailpit を入れ替える（Q1: A） | AI | 順 5 の承認に含む |
| 7 | 確かめ | スモークテスト（`deployment-strategy.md` の 3節）と、配備の後の複写（監査の数え） | AI と依頼者 | 監査に残る操作は前もって伝える |
| 8 | `main` への取り込み | `main` を `develop` の先頭へ fast-forward する（取り込みの前に `main` が `develop` の祖先であることを確かめる）。タグは付けない | AI | 依頼者の承認 |
| 9 | プッシュ | `origin` へ `develop` と `main` をプッシュする | 依頼者 | — |
| 10 | Dependabot | `gh pr list` で、④ の #50〜#53 と開いたままの #47・#46・#34・#32 が閉じたかを確かめ、残ったものの一覧と `gh pr close` のコマンドを示す（D-12: A） | AI（閉じるのは依頼者） | — |

- 配備の完了は、healthy とスモークテストのすべてが通ったとき（`team.md` の Deployment）。
- アプリが止まるのは順 5 から順 6 の healthy まで（前の手順では約 1 分 40 秒）と、順 7 の配備の後の複写（約 10 秒）。
- 開いているタブは、順 6 の後に読み込み直す。
- どの段のコミットも、行う前に依頼者に提案して承認を得る（`project.md` の Change Control）。

### 3.1 配備の前の検査（流し直さないもの）

| 検査 | 結果 | 出典 |
|---|---|---|
| `./gradlew verify` | `develop` の先頭（`fb6f5e7`、コードは `776738e` と同じ）で通った | `construction/build-and-test/test-results.md` 1節 |
| CI | 4つの統合で success（`776738e` は2回目の試行） | 同 3節 |
| E2E | 232 件 passed（Intent の最後の1回。その後にアプリのコードの変更は無いため、`team.md` の「リリースの前に E2E」はこれで満たす） | 同 2節 |
| k6（負荷の試験） | **流さない**。Build and Test で、同じソースのイメージ（`mastersmith:deps-ui-carryover`）を配備と同じ上限で試験済み（`project.md` の学び）。`memory.peak` の Not Met は依頼者の決定で持ち越し | 同 4節 |

## 4. 入れ替えの前の内部DB の複写（順 5）

- 手順は README の「内部DBのバックアップと戻し方」のとおり。名前は `mastersmith-data-<日時>-pre-deps-ui-carryover.tgz`。置き場をリポジトリの外のホームの下（権限 700）にし、`mktemp -d` は使わない（colima の VM から見えない。`project.md` の学び）。
- スキーマは変わらないが、D-5: A の測り直しのために止めて複写するため、この複写を配備の前のバックアップに兼ねる。
- 複写を読み取り（`ACCESS_MODE_DATA=r`）で開き、次だけを数える。値（DSL の本文・メールアドレス）は表示しない。

| 数えるもの | 見込み | 使い道 |
|---|---|---|
| `dsl_applied_revisions` と `dsl_previews` の各行の最も長い行の長さ（コードポイント） | 65,536 以下（Build and Test の Step 32 では最大 58） | 超えていたら配備を止めて依頼者に諮る（D-5: A） |
| `.env` の初期管理者のメールアドレス（表示しない）の利用者の `suspended`・`admin_flag` | 停止していない・印あり | 当たらなければ救済が働く見込み。入れ替えの前に依頼者の了承を得る（前の手順） |

## 5. 決まりと上流との差（記録）

- **FR11.2（Q2: B）**: 要件は「1行の長さの上限を超える DSL が拒否されること」をスモークテストに入れるとしたが、依頼者の決定で確かめず、結合テスト（`DslAdminApiIT`・`RoleTransferLimitsApiIT`）で確かめ済みとする。要件の文書は書き換えない（`project.md` の学び）。
- **E2E**: ③・④ は統合の前に E2E を流していない（依頼者の決定）。配備の前の E2E は Build and Test の1回で満たす。
- **`memory.peak`**: Build and Test で Not Met（94.4%・93.1%）。依頼者の決定で設定を変えずに持ち越す。配備の後に性能の問題が出たときは、`rollback-runbook.md` の 2節で戻すかを依頼者に諮る。

## 6. 成果物と版の識別

- 成果物は順 2 で作り直した実行可能 WAR と、それを写したイメージ `mastersmith:local`。版はコミットのハッシュ（アプリのコードは `776738e`）で識別し、どのコミットをリリースしたかは `main` の先頭と Deployment Execution の記録で追う（`team.md` の Deployment）。

## Sources

- 前の手順: `aidlc/spaces/default/intents/261004-role-menu/operation/deployment-pipeline/`
- この Intent の要件・記録: `aidlc/spaces/default/intents/261015-deps-ui-carryover/inception/requirements-analysis/requirements.md`（FR11）、`construction/code-generation/code-summary.md`、`construction/build-and-test/test-results.md`・`build-and-test-summary.md`
- 質問と答え: `aidlc/spaces/default/intents/261015-deps-ui-carryover/operation/deployment-pipeline/deployment-pipeline-questions.md`
- リポジトリ: `Dockerfile`、`compose.yaml`、`README.md`（内部DBのバックアップと戻し方・監査ログの確かめ方）
- 決まり: `aidlc/spaces/default/memory/team.md`（Deployment・Way of Working）、`project.md`（Deployment の学び・Change Control）

## Assumptions & Open Questions

- 配備済みの DSL の最も長い行は、Build and Test の Step 32 の後に DSL の投入・適用が無ければ 58 のままの見込み（順 5 で確かめる）。
- 救済の見込みは順 4 で依頼者に確かめる。
