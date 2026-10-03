# 配備の流れの構成（cd-config）

Intent 260930-user-admin（利用者の管理の画面、U1〜U5・Bolt B1〜B5）の配備の流れです。

- **配備先**: これまでどおり、開発者の PC 上のコンテナ（`compose.yaml`、プロジェクト名 `mastersmith`）。
- **配備の仕方**: 自動の配備は持たず、依頼者の承認のうえで手で配備する（`team.md`・`project.md` の Deployment）。検証環境・本番環境への昇格は、配備先が決まるまで作らない。
- **この文書の元**: CI Pipeline の記録（`construction/ci-pipeline/ci-config.md`・`quality-gates.md`）と、5単位の Infrastructure Design（`construction/u*/infrastructure-design/cicd-pipeline.md`、U3〜U5 は `infrastructure-specification.md` も）を正とし、前の Intent の配備の手順（`aidlc/spaces/default/intents/260929-log-deps-cleanup/operation/deployment-pipeline/`）と README の「コンテナでの起動と確認」「戻し方」「スキーマの変更（Flyway）」に沿って、今回の差だけを書く（`project.md` の Deployment）。
- **決定**: `deployment-pipeline-questions.md`（Q1 B・Q2 C・Q3 A・Q4 A・F1 A と、まとめの確認 Looks correct）。

## 1. 配備する版と、今動いている版

| 項目 | 値 |
|---|---|
| 今動いている版 | イメージ `mastersmith:local`（`c77c1bb247f9`、healthy）。前の Intent（260929-log-deps-cleanup）で配備した版で、アプリのソースは `8489241`（その段の `deployment-log.md`） |
| 配備する版 | `develop` の先頭。アプリのソースは `b126bdc`（Build and Test で手元の `verify` が通り、`7689ade` を含む push の CI run 37106195579 が success）。その後のコミット（`7689ade` 以降）は `aidlc/` の下のワークフローの記録だけ（`git diff origin/develop develop -- . ':!aidlc'` が空であることを読み取りで確かめた） |
| 内部DB のスキーマ | V8 → V9（`users.suspended` を足す） |
| `.env`・`compose.yaml`・`Dockerfile`・`application.yaml` | 変わらない（`8489241` から差が無いことを `git diff --stat` で確かめた） |
| 戻し先 | 配備の前に今のイメージに付けるタグ `mastersmith:pre-user-admin` |
| 見本の対象DB | `mastersmith-targetdb-postgres-1`（profile `targetdb-postgres`）。今回は触らない |
| Mailpit | `mastersmith-mailpit-1`（profile `mail`）が動いている。今回の配備は送信を使わないため触らない |
| 実行環境 | colima の VM は CPU 4・メモリ 6GiB。`app` の上限は CPU 4・メモリ 2g（既定） |

## 2. 今回の変更と、配備への影響

| 単位 | 変更 | 配備への影響 |
|---|---|---|
| U1 利用停止の状態と3つの入口 | `V9__u1_user_suspension.sql`（`ALTER TABLE users ADD COLUMN suspended BOOLEAN DEFAULT FALSE NOT NULL`）。ログインの照合・トークンの更新・アクセストークンの認証で停止を判定する | 起動のときに Flyway が V9 を当てる。前進のみで、既存の利用者は有効（false）のまま。**バックアップは取らない（Q1: B）** |
| U2 ページ送りの共通化 | 招待の一覧のページ送りを共通の部品に移した | WAR の中だけ。設定・スキーマは変わらない |
| U3 利用者の管理の API | 一覧・氏名と言語の変更・5つの操作の API（`/api/admin/users/**`）。監査の種類5つ・理由の値を足す（列は変えない） | WAR の中だけ。新しい設定・環境変数・秘密は無い |
| U4 管理の画面の 403 の共通の扱い | 画面の骨組みの「権限が無い」の表示 | イメージの中の画面が変わる |
| U5 利用者の管理の画面 | 画面と、make-you-chic-ui の固定先 `3d9521a`、`frontend/.npmrc` の `ignore-scripts=true` | イメージの中の画面が変わる。固定先と `.npmrc` はビルドのときだけに効く |

- `.env` の変更は無い。そのため `.env` の複写も取らない。
- 配備の前の k6 は流さない（Q3: A）。負荷の確かめは、この Intent の流れにある Performance Validation の段が、使い捨ての環境で行う。問題が出たら `rollback-runbook.md` の手順で戻す。

## 3. 流れ

| 順 | 段 | すること | 行う人 | 承認 |
|---|---|---|---|---|
| 1 | 関門 | アプリのソースに未コミットの変更が無いこと（`aidlc/` の下のワークフローの記録と監査ログは除く。`project.md` の Deployment）と、`develop` と `origin/develop` の差が `aidlc/` の下だけであることを確かめる。`verify`・E2E・CI は Build and Test・CI Pipeline の結果を正とし、流し直さない | AI | — |
| 2 | WAR の確かめ | イメージに入れる `backend/build/libs/mastersmith.war` が、アプリのソースの先頭（`b126bdc`）から作られたものであることを確かめる。`./gradlew :backend:bootWar` を流し、作り直しが要らない（UP-TO-DATE）ならそのまま使い、作り直されたら作り直した WAR を使う | AI | — |
| 3 | 戻し先のタグ | `docker tag mastersmith:local mastersmith:pre-user-admin`。付けた後に `docker image inspect mastersmith:pre-user-admin --format '{{.Id}}'` が `c77c1bb247f9` で始まることを確かめる | AI | — |
| 4 | 入れ替え | `docker compose --profile targetdb-postgres up -d --build app` でアプリだけを作り直し、healthy を待つ（`deployment-strategy.md` の 2節） | AI | 依頼者の承認（アプリが止まるため） |
| 5 | 確かめ | スモークテスト（`deployment-strategy.md` の 3節） | AI と依頼者 | ログインは監査に残るため、送る前に依頼者に伝える |
| 6 | `main` への取り込み | `main` を `develop` の先頭へ fast-forward する（`main` は `develop` の祖先であることを読み取りで確かめた）。タグは付けない | AI | 依頼者の承認 |
| 7 | プッシュ | `origin` へ `develop` と `main` をプッシュする | 依頼者 | — |

- バックアップ（Q1: B）と戻しの練習（U1 の決定）の段は置かない。
- 配備の完了は、healthy とスモークテストのすべてが通ったとき（`team.md` の Deployment）。

## 4. 決まりと上流の設計との差（記録）

承認済みの設計の文書は書き換えず、差をここに記録します（`project.md` の Way of Working）。

| 決まり・上流 | 今回の扱い | 理由 |
|---|---|---|
| U1 の NFR10.3（`construction/u1-user-suspension/nfr-requirements/security-requirements.md`）「戻す前に停止中の利用者を確かめる手順を戻しの手順に書く」と、README の戻しの節・スキーマの変更の節の同じ趣旨の記載 | **戻す前の確かめを置かない**。戻すときは確かめずにイメージだけを戻す（Q2: C）。戻している間は利用停止が効かず、1つ前の版では止め直せないことを、既知の制約として `rollback-runbook.md` の 3節に書く（F1: A） | 依頼者の決定。配備先は開発者の PC だけで利用者は依頼者だけ、停止中の利用者は今 0 人の見込み（配備の後のスモークテストの S4 で確かめる）。直した版を配備し直せば停止が戻る |
| Infrastructure Design の承認の場の決定（U1 R-03、U3 R-01。`construction/infrastructure-design/gate-decisions.md`）「戻す前に停止中の利用者がいるかを確かめ、いれば扱いを依頼者に確かめる手順を deployment-pipeline で必ず決める」 | この段で扱いを決めた。決めた中身は「確かめを置かない」（上の行） | 受け入れの条件は「この段で決める」ことで、決めた中身が確かめの手順でないことを差として残す |
| `team.md` の Deployment「1つ前の版のアプリが動く後方互換を保つ」 | 設計の上では保つ見込み（V9 は列を足すだけ）だが、自動のテストも戻しの練習も置かない（U1 の決定、Q1: B）。また、戻している間は停止を判定しない | U1 の NFR 設計・基盤の設計の決定（U1 の `cicd-pipeline.md` 6節・11節）を受け継ぐ |
| `project.md` の Mandated「利用者の状態（停止・管理者の印）の判定は、3つの入口のすべてでサーバー側で行う」 | 1つ前の版に戻している間は、停止の判定が効かない（管理者の印の判定は1つ前の版も行う） | 1つ前の版は `users.suspended` を読まない。受け入れた制約（U1 の `security-requirements.md` の R2） |
| `team.md` の Deployment「リリース時に `main` にタグを付ける」 | `main` へは取り込むが、タグは付けない | 前の Intent と同じ。`v*` のタグの push で公開の GitHub のリリースが作られるため、配備先が決まったときに改めて決める |
| U2・U4・U5 の `cicd-pipeline.md` が配備の段に回した確かめ（招待の一覧のページ送り、管理者でない利用者の 403 の表示 S6、5つの操作と氏名・言語の変更） | スモークテストに入れない（Q4: A） | どれも利用者を作る・状態を変える・監査に残る操作か、管理者でない利用者が要る。E2E（U5 の 110、U4 の 130 など）で確かめ済み |
| U3 の `infrastructure-specification.md` 8節「最後の管理者の拒否（LAST_ACTIVE_ADMIN）をスモークテストに入れるか」 | 入れない（Q4: A） | 初期管理者だけでは見せられず（自分自身の操作は先に `SELF_OPERATION` で拒否される）、別の管理者と監査の行が残る |

## 5. Build and Test から引き継いだ Unverified の扱い

`construction/build-and-test/build-and-test-summary.md` で持ち主を deployment-pipeline・deployment-execution とした3件です。目標は緩めません。

| ID | 目標 | この段の扱い | 判定の持ち主と時点 |
|---|---|---|---|
| U1-NFR10.1 | V9 だけを前進のみで足し、既存の利用者は有効になる | Deployment Execution のスモークテストで事後に裏付ける（`deployment-strategy.md` の 4節）。起動のログで Flyway が V9 を当てたこと、既存の初期管理者でログインできること、一覧で停止中の利用者が 0 人であること | deployment-execution |
| U1-NFR10.2 | 1つ前の版のアプリが V9 の後の内部DB で動く | **この Intent では確かめない**（戻しの練習を置かない U1 の決定、バックアップを取らない Q1: B）。根拠は見込みだけ: (1) V9 は列を足すだけ、(2) 前の Intent の戻しの練習で、古い版の Flyway は「スキーマの版が知っている最新より新しい」の WARN を出して起動を続け、Hibernate の `validate` は余分な列と表を許した（`aidlc/spaces/default/intents/260925-user-management/operation/deployment-execution/health-check-report.md`）、(3) 戻し先のソース `8489241` の本番のコードは監査の行を読まない（4節） | 未確認のまま残る。次に確かめる機会は、実際に戻したとき（`rollback-runbook.md` の 4節の確かめ）か、配備先が決まって戻しの練習を置くとき |
| U1-NFR10.3 | 戻している間は停止が効かない制約を受け入れ、戻す前に停止中の利用者を確かめる手順を戻しの手順に書く | 制約は `rollback-runbook.md` の 3節に書いた。戻す前の確かめの手順は置かない（F1: A、4節の差） | この段で扱いを決めた（差として記録）。満たしたことにはしない |

### 5.1 戻し先のイメージのソースの確かめ（U3 の申し送り）

U3 の `infrastructure-specification.md` 5.1 は、戻し先のイメージが確かめたときと別のコミットから作られていたら、そのコミットで「監査の行を読む本番のコードが無いこと」を確かめ直すよう求めています。戻し先 `c77c1bb247f9` のソースは `8489241` です（前の Intent の `deployment-log.md`）。この段で読み取りだけで次を確かめました。

- `8489241` の `backend/src/main` で `AuditEventRepository` を使うのは `AuditEventRecorder` だけで、呼ぶのは `repository.save(auditEvent)`（追記）だけ。U3 で足した監査の種類・理由の値の行を、戻し先の版は読まない。
- `8489241` の `backend/src/main` に `suspended` は無い（停止の列を読まない。4節の制約の根拠）。
- `8489241` は U1 の最初のコミット `ded2653` の祖先（この Intent の前の版にあたる）。

## 6. 成果物と版の識別

- 成果物は、フロントエンドの `dist` を同梱した実行可能 WAR から作るイメージ `mastersmith:local`（`team.md` の Deployment）。
- 版はコミットのハッシュで識別し、イメージの ID とともに deployment-execution の記録に残す。どのコミットをリリースしたかは `main` の先頭と deployment-execution の記録で追う。

## Sources

- `construction/ci-pipeline/ci-config.md`（CI の記録、run 37106195579）・`construction/ci-pipeline/quality-gates.md`（関門の基準）
- `construction/u1-user-suspension/infrastructure-design/cicd-pipeline.md`（6節 V9 の確かめと戻し、11節、承認の場の決定 R-03）
- `construction/u2-shared-paging/infrastructure-design/cicd-pipeline.md`（5節・6節）
- `construction/u3-user-admin-api/infrastructure-design/infrastructure-specification.md`（1節・3節〜5節・5.1・8節）・`cicd-pipeline.md`（6節、承認の場の決定 R-01）
- `construction/u4-admin-forbidden-ui/infrastructure-design/infrastructure-specification.md`（1節）・`cicd-pipeline.md`（6節・7節）
- `construction/u5-user-admin-ui/infrastructure-design/infrastructure-specification.md`（1節）・`cicd-pipeline.md`（5節・6節）
- `construction/infrastructure-design/gate-decisions.md`
- `construction/u1-user-suspension/nfr-requirements/security-requirements.md`（NFR10.1〜NFR10.3、R2）
- `construction/build-and-test/build-and-test-summary.md`（Unverified の表、引き継ぎ）
- `operation/deployment-pipeline/deployment-pipeline-questions.md`（Q1〜Q4・F1・まとめの確認）
- `README.md`（コンテナでの起動と確認、戻し方、スキーマの変更（Flyway））、`compose.yaml`・`Dockerfile`（読むだけ）
- `aidlc/spaces/default/intents/260929-log-deps-cleanup/operation/deployment-pipeline/`・`operation/deployment-execution/deployment-log.md`（前の Intent の配備の手順と、配備した版）
- `aidlc/spaces/default/intents/260925-user-management/operation/deployment-pipeline/`・`operation/deployment-execution/health-check-report.md`（スキーマの変更があった回の手順と戻しの練習の結果）
- `aidlc/spaces/default/memory/team.md`・`project.md`（Way of Working・Deployment・Mandated・Corrections）

## Assumptions & Open Questions

- [assumption] 手元の WAR（2026-10-03 14:43 作成）は `b126bdc` の verify で作られたものと見ているが、作成の時刻がコミット（14:45）より前のため、3節の順 2 で `bootWar` の作り直しの要否で確かめる。
- 依頼者の決定（2026-10-03、手順書の確かめ）: WAR の確かめ（3節の順 2）、スモークテストの S5（監査の確かめでアプリを数秒止める）、`/api/me` を確かめに含めないこと、`rollback-runbook.md` 3節の「戻している間に停止中の利用者が取ったトークン」の扱いの書き方（確かめていない見込み）を、すべて受け入れた。
