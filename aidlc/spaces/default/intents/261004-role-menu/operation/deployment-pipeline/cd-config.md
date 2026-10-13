# 配備の流れの構成（cd-config）

Intent 261004-role-menu（F: ロールベースの権限、I: N 階層のメニュー。U1〜U7、Bolt B1〜B9）の配備の流れです。

- **配備先**: これまでどおり、開発者の PC 上のコンテナ（`compose.yaml`、プロジェクト名 `mastersmith`）。
- **配備の仕方**: 自動の配備は持たず、依頼者の承認のうえで手で配備する（`team.md`・`project.md` の Deployment）。本番配備の承認者は依頼者。
- **この文書の元**: 前の Intent の配備の手順（`aidlc/spaces/default/intents/261004-safety-carryover/operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`。以下「前の手順」）と、この Intent の CI の記録（`construction/ci-pipeline/ci-config.md`・`quality-gates.md`）、基盤の設計（各単位の `infrastructure-design/`）、リポジトリの `Dockerfile`・`compose.yaml`・README を正とし、今回の差だけを書く（`project.md` の Deployment の学び）。
- **決定**: `deployment-pipeline-questions.md`（Q1〜Q6 すべて A、まとめの確認 Looks correct。版 1 の DSL に手で直した点は無い）。

## 1. 配備する版と、今動いている版

| 項目 | 値 |
|---|---|
| 今動いている版 | イメージ `mastersmith:local`（`fefeafbd299b`、healthy）。前の Intent で配備した `273c021` の版（2026-10-14 に `docker ps`・`docker image inspect` で読み取りだけで確かめた） |
| 配備する版 | `develop` の先頭。アプリのコードは `005730a`（その後のコミットは `aidlc/` の下の記録と README の1行だけ）。`005730a` の push の CI（run 38432556919）は1回目で success（`construction/ci-pipeline/ci-config.md` 6.1） |
| 内部DB のスキーマ | V9 → V12。V10（グループの表と監査の3列）・V11（ロールと権限の設定の表）・V12（割り当てと作業ロールの表）。表と空を許す列を足すだけで、既存の列と行は変えない。`users.admin_flag` は残す |
| `.env` | 変えない（`.env.example` と `compose.yaml` は前の版から変わっていない） |
| `Dockerfile` | JVM の既定の引数に `-XX:+ExitOnOutOfMemoryError`（Build and Test の Q2: A。6節） |
| `application.yaml` | Tomcat の `server.tomcat.max-swallow-size: 11MB`、`StatementCreatorUtils` のログを OFF。イメージの中にあり、`.env` では変えない |
| DSL の書式 | 版 2 だけを受け付ける。版 1 で適用中の DSL は、新しい版の起動で「適用中の DSL が無い」扱いになる（5節） |
| 戻し先 | 入れ替えの前に今のイメージに付けるタグ `mastersmith:pre-role-menu` |
| 見本の対象DB・Mailpit | `mastersmith-targetdb-postgres-1`・`mastersmith-mailpit-1`。入れ替えない |
| 実行環境 | colima の VM は CPU 4・メモリ 6GiB。`app` の上限は CPU 4・メモリ 2g（既定） |

## 2. 今回の変更と、配備への影響

| 変更 | 配備への影響 |
|---|---|
| 移行 V10〜V12 | 起動のときに3つ当たる。H2 は DDL を巻き戻せないため、入れ替えの前の内部DB の複写を省かない（4節）。前の版は足した表と列を見ない（`rollback-runbook.md`） |
| DSL の書式の版 2（U2） | 版 1 の適用中の DSL は読めず、起動のログに ERROR「適用中の DSL を読めないため、適用中の DSL が無い状態で起動します」が1件出る（想定どおりの1件。`deployment-strategy.md` の S1）。入れ替えの後に版 2 の既定の DSL を生成し直して適用する（5節、Q2: A） |
| ロール・グループ・権限・業務のメニュー（U3〜U7） | 管理の画面の可否はこれまでどおり `users.admin_flag` で決まる。ロールは業務データの権限だけに使う（要件 C1）。業務のメニューは版 2 の DSL を適用するまで空 |
| 画面の骨組み（U7） | 画面の部品が替わるため、入れ替えの後に開いているタブを読み込み直す |
| `ExitOnOutOfMemoryError` | ヒープが尽きると JVM が止まり、`restart: "no"` のため起き直さない（6節、Q6: A） |
| Tomcat の読み捨ての上限 | 本文を読まずに断った要求でも 11MB まで読み捨ててから応答する。配備への手当ては無い |

## 3. 流れ

| 順 | 段 | すること | 行う人 | 承認 |
|---|---|---|---|---|
| 1 | 関門 | アプリのソースに未コミットの変更が無いこと（`aidlc/` の下は除く。`project.md` の学び）と、配備する `develop` のコードが `005730a` と同じこと（`git diff 005730a HEAD -- . ':!aidlc'` が README の1行だけ）を確かめる。verify・CI・E2E は Build and Test と CI Pipeline の結果を正とし、流し直さない（3.1） | AI | — |
| 2 | 戻し先のタグ | `docker tag mastersmith:local mastersmith:pre-role-menu`。付けた後に ID が `fefeafbd299b` で始まることを確かめる | AI | — |
| 3 | 救済の見込み | 依頼者に、前の配備の後に初期管理者のパスワードを画面で変えた・止めた・印を外したことがあるかを尋ねる（救済は前の版から同じ仕組みで、起動のたびに働きうる。前の手順の順 3） | AI と依頼者 | — |
| 4 | 版 1 のプレビューの破棄（Q2: A） | 依頼者が今の版の DSL の画面を開き、プレビューがあれば「破棄する」（`DSL_PREVIEW_DISCARDED`）。監査に残るため、行う前に伝える。監査の数え始めの時刻 T0 をこの前に控える | 依頼者（AI は時刻を控える） | 依頼者の了承 |
| 5 | 入れ替えの前の複写（4節） | アプリを止め（`docker compose stop app`）、内部DB を `~/.mastersmith-backup/`（権限 700）へ複写する（配備の前のバックアップ）。複写を読み取り（`ACCESS_MODE_DATA=r`）で開き、DSL の表の行の数と、初期管理者の停止と印の有無だけを数える | AI | 依頼者の承認（アプリが止まるため） |
| 6 | 入れ替え | `docker compose --profile targetdb-postgres up -d --build app` で WAR とイメージを作り直し、アプリだけを入れ替えて healthy を待つ。`ExitOnOutOfMemoryError` が効いていることを確かめる（6節） | AI | 順 5 の承認に含む |
| 7 | DSL の移し替え（5節） | 依頼者が版 2 の既定の DSL を生成し、プレビューを確かめて適用する | 依頼者 | 監査に残るため、行う前に伝える |
| 8 | 確かめ | スモークテスト（`deployment-strategy.md` の 3節、Q5: A）と、配備の後の複写（監査の数えと戻しの練習の元を兼ねる） | AI と依頼者 | 監査に残る操作は前もって伝える |
| 9 | 戻しの練習 | `rollback-runbook.md` の 6節（Q3: A）。配備したアプリは止めない | AI と依頼者 | 依頼者の承認 |
| 10 | `main` への取り込み | `main` を `develop` の先頭へ fast-forward する（2026-10-14 の時点で `main` は `develop` の祖先で、fast-forward できる）。タグは付けない | AI | 依頼者の承認 |
| 11 | プッシュ | `origin` へ `develop` と `main` をプッシュする | 依頼者 | — |

- 配備の完了は、healthy とスモークテストのすべてが通ったとき（`team.md` の Deployment）。戻しの練習は Build and Test から引き継いだ確かめ（`U3-NFR3.6b`・`U4-NFR3.6b`、G-19、AC6.1.8）で、`main` への取り込みの前に済ませる。
- アプリが止まるのは順 5 から順 6 の healthy まで（前の手順では約 1 分 40 秒）と、順 8 の配備の後の複写（約 10 秒）。
- 開いているタブは、順 6 の後に読み込み直す。
- どの段のコミットも、行う前に依頼者に提案して承認を得る（`project.md` の Change Control）。

### 3.1 配備の前の検査（流し直さないもの）

| 検査 | 結果 | 出典 |
|---|---|---|
| `./gradlew verify` | Build and Test で通った（作業ブランチの `005730a` と同じ中身） | `construction/build-and-test/test-results.md` |
| CI | `005730a` の push の CI（run 38432556919）が1回目で success | `construction/ci-pipeline/ci-config.md` 6.1 |
| E2E（`./gradlew e2eTest`） | 010〜170 の 18 ファイル・230 件が通った（2026-10-14、10.4 分）。その後にアプリのコードの変更は無いため、`team.md` の「リリースの前に E2E」はこれで満たす | `construction/build-and-test/test-results.md` 1節 |
| k6（負荷の試験） | **流さない**（Q4: A。3.2） | — |

### 3.2 配備の前の k6 を省くこと（Q4: A）と、決まりとの差

- Build and Test では、権限の YAML の上限の測り（`BT-Q1c`。配備と同じ上限の使い捨ての環境で、上限ちょうどの確かめ 1.11〜5.42 秒・適用 2.35〜11.41 秒、OutOfMemoryError・OOMKilled なし）だけを行い、k6 の場面と DSL の時間の測り（Unverified 20 件）は Performance Validation に回した（`construction/build-and-test/build-and-test-questions.md` の Q4: A）。
- `project.md` の学びは「配備するものと同じソースのイメージで、配備と同じ上限で負荷の試験済みのときは、配備の前の k6 を省く」で、今回は全部は試験済みではない。依頼者の決定（Q4: A）で配備の前には流さず、この Intent の流れで Deployment Execution の後にある Performance Validation に任せる（前の Intent `260925-user-management` の Q2: B と同じ扱い）。
- 配備の後に性能の問題が出たときは、`rollback-runbook.md` の 2節のイメージだけの戻しで戻すかを依頼者に諮る。

## 4. 入れ替えの前の内部DB の複写（順 5）

- 手順は README の「内部DBのバックアップと戻し方」のとおり。名前は `mastersmith-data-<日時>-pre-role-menu.tgz`。置き場をリポジトリの外のホームの下（権限 700）にし、`mktemp -d` は使わない（colima の VM から見えない。`project.md` の学び）。
- スキーマが変わるため省かない（`construction/group/infrastructure-design/infrastructure-specification.md` 4節）。この複写は、移行が途中で失敗したとき・データが壊れたときの戻し先（`rollback-runbook.md` の 3節）。
- 複写を読み取り（`ACCESS_MODE_DATA=r`）で開き、次だけを数える。値（DSL の本文・メールアドレス）は表示しない。

| 数えるもの | 見込み | 使い道 |
|---|---|---|
| `dsl_applied_revisions` の行の数 | 1 以上なら、版 1 の適用中の DSL がある | 1 以上なら、S1 で ERROR「適用中の DSL を読めない…」1件を見込む。0 なら 0 件 |
| `dsl_previews` の行の数 | 0（順 4 で破棄したため） | 1 なら順 4 の破棄が漏れている。入れ替えの後の生成でプレビューは置き換わるため配備は続けてよいが、記録する |
| `.env` の初期管理者のメールアドレス（表示しない）の利用者の `suspended`・`admin_flag` | 停止していない・印あり | 当たらなければ救済が働く見込み。入れ替えの前に依頼者の了承を得る（前の手順） |
| `users` の `admin_flag` が真の行の数 | 記録する | 戻しの練習の「管理の可否が戻す直前と同じ」の比べの元（`rollback-runbook.md` の 6節） |

## 5. DSL の移し替え（順 7、Q2: A）

承認済みの設計（`construction/dsl-v2/nfr-design/logical-components.md` 7節の 1）と README の「書式の版を上げたとき（版 1 → 版 2）」のとおりです。版 1 の適用中の DSL は既定の DSL の生成から作ったもので、手で直した点は無い（依頼者の答え）。

1. 入れ替えの前に、版 1 のプレビューがあれば依頼者が画面で破棄する（3節の順 4）。
2. 入れ替えの後、DSL の画面（`/admin/dsl`）に「適用中の DSL は使われていません」の注意が出ていることを依頼者が見る（版 1 の適用中の DSL があったとき）。
3. 依頼者が「スキーマを読み込む」で版 2 の既定の DSL を生成し（`DSL_GENERATED`）、プレビューのタブで検証を通ったこと・スキーマの数・メニューの木を見てから「適用する」（`DSL_APPLIED`）。
4. 適用の後、DSL の画面の注意が消え、業務のメニューの区画に、作業ロールで見てよいテーブルの項目だけが出る状態になる（作業ロールが無い間は何も出ない）。
- 生成は対象DB（見本の PostgreSQL）を読む。`TARGET_DB_UNAVAILABLE` のときは見本の対象DB が動いているかを確かめて、やり直す。
- 監査に残る操作（生成・適用）は、行う前に依頼者に伝える。

## 6. `ExitOnOutOfMemoryError`（Q6: A）

- `compose.yaml` の `restart: "no"` は変えない。ヒープが尽きたら JVM は止まり、起き直さない（Build and Test の確かめで終了コード 3・OOMKilled false）。止まったときの手順は `rollback-runbook.md` の 7節。
- 入れ替えの後（順 6）に、動いているコンテナで効いていることを確かめる。値（環境変数）は表示せず、件数だけを出す。

```bash
docker compose exec app sh -c "tr '\0' ' ' < /proc/1/cmdline" | grep -c -- '-XX:+ExitOnOutOfMemoryError'   # 1 であること
docker compose exec app sh -c "tr '\0' ' ' < /proc/1/cmdline" | grep -c -- '-XX:MaxRAMPercentage=50.0'      # 1 であること
```

## 7. 決まりと上流との差（記録）

| 決まり・上流 | 今回の扱い | 理由 |
|---|---|---|
| `project.md` の学び「同じソースのイメージで負荷の試験済みのときは配備の前の k6 を省く」 | 試験済みではないが省く（3.2） | Q4: A。Performance Validation に任せる |
| `team.md` の Deployment「リリース時に `main` にタグを付ける」 | `main` へは取り込むが、タグは付けない | 前の Intent と同じ。`v*` のタグの push で公開の GitHub のリリースが作られるため、配備先が決まったときに改めて決める |
| `team.md` の Testing Posture「リリースの前に E2E を手元で実行」 | この段では流し直さず、Build and Test の結果を正とする | `005730a` の後にアプリのコードの変更が無いため（3.1） |
| Q5 の答えの「確かめ用のロールとグループを1つずつ」 | 確かめ用のロールを2つにし、作業ロールの切り替え（`WORK_ROLE_SWITCHED`）も確かめる（`deployment-strategy.md` の S5〜S8） | ロールが1つだと作業ロールは自動で選ばれ切り替えられないため、依頼者の答え（確かめたい点 1: A）で2つにした |

## 8. 成果物と版の識別

- 成果物は、フロントエンドの `dist` を同梱した実行可能 WAR から作るイメージ `mastersmith:local`（`team.md` の Deployment）。
- 版はコミットのハッシュで識別し、イメージの ID とともに Deployment Execution の記録に残す。

## Sources

- 前の手順: `aidlc/spaces/default/intents/261004-safety-carryover/operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`、`aidlc/spaces/default/intents/261004-safety-carryover/operation/deployment-execution/deployment-log.md`
- `deployment-pipeline-questions.md`（決まっていること、Q1〜Q6、まとめの確認）
- `construction/ci-pipeline/ci-config.md`（2.2・6節）・`quality-gates.md`
- `construction/build-and-test/test-results.md`・`build-and-test-summary.md`（`BT-Q1c`・`BT-Q2`・引き継ぎ）・`build-and-test-questions.md`（Q2・Q4）・`build-instructions.md`
- `construction/dsl-v2/infrastructure-design/cicd-pipeline.md`（8節・9節）、`construction/dsl-v2/nfr-design/logical-components.md`（7節）
- `construction/group/infrastructure-design/infrastructure-specification.md`（4節）、`construction/role/infrastructure-design/infrastructure-specification.md`（4節）・`cicd-pipeline.md`（3節・9節）
- `inception/requirements-analysis/requirements.md`（C1）、`inception/user-stories/stories.md`（AC6.1.6・AC6.1.8）
- リポジトリ（読むだけ）: `Dockerfile`、`compose.yaml`、`.env.example`、`backend/src/main/resources/application.yaml`、`backend/src/main/resources/db/migration/`、`backend/src/main/java/cherry/mastersmith/dslmanage/service/DslStartupLoader.java`、README（コンテナでの起動と確認、戻し方、内部DBのバックアップと戻し方、DSL の管理の API、骨組みの画面、スキーマの変更（Flyway）、監査ログの確かめ方）、`git diff 273c021 HEAD`・`git diff 005730a HEAD`・`git merge-base --is-ancestor main develop`
- 実行環境（読み取りだけ、2026-10-14）: `docker ps`、`docker images`、`docker image inspect mastersmith:local`、`colima list`
- `aidlc/spaces/default/memory/team.md`・`project.md`（Way of Working・Testing Posture・Deployment・Change Control）

## Assumptions & Open Questions

- [assumption] 今の内部DB に版 1 の適用中の DSL がある（前の Intent までのスモークテストで適用した）。順 5 の数えで確かめ、無ければ S1 の ERROR の見込みを 0 件にする。
