# CI/CD Pipeline — U5 テストと測り方の持ち越し（u5-carryover-tests）

この文書には、U5 の検査の流れ（CI と1コマンドの検査）、`perf/` の `memory.peak` の測り方の手順、秘密の扱い、B1 で確かめることを書きます。

U5 は library の単位で、B1 で作ります。中身は次のとおりです。

- 不安定なテスト2件の直し: `RoleConcurrencyIT` の #7（監査の行の ID の順に頼らない。入れ替えの待ち合わせ `AuditWriteReorderBarrier`）と `AdminForbiddenProvider.test.tsx`（効果の回数を `waitFor` で待つ）
- テストの補助: `TestRoleBarrier` のスレッドの記録、`RoleActors.Actor` の `toString` の伏せ字
- `perf/`: `dslMixed` の間の `memory.peak` と `memory.stat` の記録のループ、`PERF_DB_URL` の形の確かめの関数、`perf/README.md` の (b) 節の手順

本番のコードと配備物（`backend/src/main/`・`application.yaml`・`Dockerfile`・`compose.yaml`・`docker/perf/compose.yaml`）は変えません。新しい依存・API・スキーマ・設定・イメージ・秘密も足しません。そのため、CI と1コマンドの検査と配備の流れは **変えません**。この文書は既にある仕組みの記録として、どの段で何を確かめるかを書きます。library の単位のため、`infrastructure-specification.md`・`monitoring-design.md` は作りません（段の定義の `produces_kinds`）。

配備先は開発者の PC 上のコンテナだけです。クラウドの基盤・IaC・警報の通知先は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。

答え: `aidlc/spaces/default/intents/261016-business-data-views/construction/u5-carryover-tests/infrastructure-design/infrastructure-design-questions.md`（Q1: A、まとめの確認: Looks correct）。

## 1. CI（GitHub Actions）

| 項目 | 今の形（変えない） | 出典 |
|---|---|---|
| きっかけ | `develop` へのプッシュ・`v*` のタグ・手動 | `.github/workflows/ci.yml` 19〜23行 |
| 段の並び | 取得（サブモジュールは固定先のコミット）→ JDK 25 → Node.js 24 → Gradle → Gitleaks と OSV-Scanner → `./gradlew verify` → WAR の保存 | `ci.yml` 43〜92行 |
| 時間の上限 | `timeout-minutes: 60`。42 分を超えたら依頼者に諮る | `ci.yml` 42行、`team.md` |
| 秘密 | CI は秘密を使わない。U5 も渡さない | `ci.yml` 16行 |

`perf/` の手順は CI で流しません（4節）。

## 2. 1コマンドの検査（`./gradlew verify`）の段と関門

| verify の段 | 既存のタスク | この単位で足されるもの |
|---|---|---|
| フォーマット・ライセンスヘッダー | Spotless・Prettier・ヘッダーの検査 | 新しいファイル（`audit/testsupport` の待ち合わせとその設定）にも当たる。`perf/` の台本はヘッダーの検査の対象のまま |
| 単体テスト | `:backend:test`（`*Test`）・`frontendTest` | `RoleActors.Actor` の `toString` に email と token の値が含まれないことのテスト（説明文は英語）、`AdminForbiddenProvider.test.tsx` の直し（`waitFor`） |
| 結合テスト | `:backend:integrationTest`（`*IT`） | `RoleConcurrencyIT` の #7 を、自然の順と入れ替えた順の2つの場合にする。待ち合わせは `RoleConcurrencyIT` にだけ `@Import` で入れる |
| カバレッジ | JaCoCo・`@vitest/coverage-v8` | `src/test` とテストのファイルだけの変更のため、本体の行・分岐の値と `packagesJudgedByTotal` に響かない |
| 依存の脆弱性 | OSV-Scanner | 依存を足さないため対象は変わらない（TS1） |

- Gradle のタスク・vitest の設定は変えません。
- テストの補助は `src/test` にあり、WAR（`src/main` だけから作る）に入りません（`nfr-design/security-design.md` S4.3）。
- 直したテストに `sleep` を入れず、`TestRoleBarrier.WAIT_LIMIT`（20 秒）・Vitest の既定の上限・`waitFor` の既定の上限は延ばしません（NFR7.3）。

## 3. 統合と、統合の前の確かめ

- B1 の変更は、テストのファイル（`backend/src/test`・`frontend/src` の `*.test.tsx`）と `perf/` だけです。そのため、統合の前は `./gradlew verify` だけで、E2E は要りません（`team.md` の Way of Working）。E2E は Intent の最後とリリースの前に流します。
- 統合は `develop` へ squash で戻します。
- 不安定なテストの直しは二段で確かめ、記録します（`team.md` の Testing Posture、機能設計の BR1.6・BR2.4）。
  - 入れ替えた順の場合で、直す前のテストが落ち、直した後のテストが通ること。
  - 起こせないときは、試みの時間の上限を先に決め、見立てと試みの範囲を記録します。
- B1 の後に、verify と CI の時間を試行ごと（`gh run view --json attempt,jobs`）に記録します。件数は `:backend:cleanTest :backend:cleanIntegrationTest` を付けた実測だけを書きます（`project.md`）。CI が失敗したら、`team.md` の「不安定なテストと CI の失敗」の決まりで扱います。

## 4. `perf/` の `memory.peak` の手順（verify と CI の外）

### 4.1 環境の分け方（Q1: A）

| 回 | 環境 | 接続先 |
|---|---|---|
| 既定の1回目・2回目 | 1つの `KEEP=1` の環境（`KEEP=1 ./perf/dsl-timing.sh postgres` で作る）。各回の前に `--force-recreate app`。内部DB のボリュームは膨らんだまま流す（悪い側の条件。`project.md` の学び） | 既定（`application.yaml` の `DEFRAG_ALWAYS=TRUE`） |
| 試しの1回目・2回目 | 既定の環境を `down -v` と一時ディレクトリの削除で消し、`PERF_DB_URL` を付けて `dsl-timing.sh` で作り直す。同じ順で流す | `PERF_DB_URL`（`DEFRAG_ALWAYS=TRUE` と `RETENTION_TIME`）。使った値を `env.txt` の `db_url` に残す |

- 確かめの関数は `dsl-timing.sh` の1か所を通ります。`dslMixed` の手順に新しい上書きの口は足しません（8節の差）。
- どちらの環境も、終わりに手で片付け（`down -v` と一時ディレクトリの削除）、消えたことを確かめます。

### 4.2 1回の流れ

機能設計 3節の8手順のとおりです。

1. 配備したアプリを止める（測る間だけ。BR3.4）。
2. 使い捨てのアプリを作り直す（`--force-recreate`）。
3. 始める前に、cgroup v2 の `memory.peak`・`memory.current`・`memory.max` と、`memory.stat` の `anon`・`file`・`inactive_file`・`active_file`・`kernel` が読めるかを確かめる。読めなければその回を無効として記録し、合否に使わない（S3）。
4. 記録のループを始め、20 秒後に `dslMixed`（VUS 10・3 分・10 MiB の DSL）を流し、終わりの後 60 秒まで記録する。
5. `memory.peak`・`memory.max`・状態（OOMKilled）を読む。
6. WARN を数えてから片付ける。
7. `pmset -g log` で眠りが無かったかを確かめる。

台本の全体を `caffeinate -dims` で包みます（場面ごとに起こし直さない）。4回が終わったら、配備したアプリを起動し直します。

### 4.3 記録のループ

- アプリのイメージの bash の1つのループを、`docker exec` 1回で回します。1秒ごとに `docker exec` を起こしません（TS4）。`--privileged`・`--user root`・追加の権限は付けません（NFR4.9）。
- 出力はホームの側の `build/perf-results/<OUT_DIR>/` の TSV に書きます。ループ自身はコンテナの中に書きません。読むファイルの大きさは `/app/data/mastersmith.mv.db` だけです。
- ループには秒の上限を引数で渡し、自分で終わらせます。`-t` の無い `docker exec` は、手元の側を止めてもコンテナの中の処理が残りうるためです。手元の側でも止めます。
- 時刻の列は VM の時計の UTC にします（k6 も VM の中で動くためそろう）。始めと終わりに、VM とホームの時刻の差の範囲を記録します（`project.md` の時計のずれの学び）。
- 列は機能設計 3節の列（時刻・`memory.current`・`memory.peak`・`memory.stat` の5つ・内部DB のファイルの大きさ）に限ります（NFR4.6）。

形の例（説明のための断片。名前と細部はコード生成で確定する）:

```bash
docker exec mastersmith-perf-app-1 bash -c '
  end=$(( $(date +%s) + $1 ))
  while [ "$(date +%s)" -lt "$end" ]; do
    s=$(awk '\''$1~/^(anon|file|inactive_file|active_file|kernel)$/{printf "\t%s",$2}'\'' /sys/fs/cgroup/memory.stat)
    printf "%s\t%s\t%s%s\t%s\n" "$(date -u +%FT%TZ)" "$(cat /sys/fs/cgroup/memory.current)" \
      "$(cat /sys/fs/cgroup/memory.peak)" "$s" "$(stat -c %s /app/data/mastersmith.mv.db)"
    sleep 1
  done' _ "$LIMIT_SECONDS" > "$OUT_DIR/memory.tsv"
```

### 4.4 `PERF_DB_URL` の確かめ（NFR4.7、R-02）

- 確かめの関数は、`dsl-timing.sh` の一時ディレクトリを作る前（今の 113行の前）の1か所で呼びます。サブシェルの中で呼ばず、失敗したら親の処理を終了コード 1 で止めます。
- 受け付ける形は `nfr-design/security-design.md` S1.2 のとおりです。空白の扱い（`INIT=` の値の中の半角空白）と、空の要素・同じ設定の重なりを拒否する規則は、コード生成で確定します（R-02）。
- 止めたときは、受け取った値を表示しません。

### 4.5 指標と待ち受けの口（NFR4.8）

- 指標の公開（`MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics`）は、一時の `app.env` だけで使い捨てのアプリに当てます。配備したアプリの公開の範囲は変えません。
- 待ち受けの口は `127.0.0.1:18080` のままです（`docker/perf/compose.yaml` 28行）。

### 4.6 判定（Build and Test）

- 既定の2回で `memory.peak / memory.max < 0.9` を見ます。2回とも満たせば Met です（BR3.5）。
- あわせて、`anon` の最大 / `memory.max`、peak の時の内訳、`file` の伸びと内部DB のファイルの伸びを並べます（BR3.2）。物差しは変えません（`team.md` の Testing Posture）。
- 試しの2回は、起動の後に設定の値を読み取りで確かめた回だけを使います（BR4.1）。効いたら取り込むかを、効かなければ内訳と試した手を記録して依頼者に諮ります（BR4.2・BR4.3）。コンテナの上限 2g は上げません。

## 5. 秘密（NFR4.5・NFR4.6）

一時ファイルの一覧を、実物に合わせます（R-01）。

| 流れ | 一時ディレクトリのファイル | 持つ秘密 |
|---|---|---|
| `perf/dsl-timing.sh` | `app.env`・`targetdb.env`・`app-targetdb.env`（268行）・`login.json`・`ui.env`・`auth.header`（175行。アクセストークン） | 署名鍵・仮の管理者のパスワード・対象DB の資格情報・アクセストークン |
| `perf/README.md` の `dslMixed` の手順 | 上の一時ディレクトリ（`KEEP=1` で残る）に `PERF_USER_PASSWORD` を足した `ui.env` | 上に加えて試験用の利用者のパスワード |

- 置き場は、どれもホームの下の権限 700 の一時ディレクトリです（`dsl-timing.sh` 113〜114行）。書き足しは `umask 077` の中で行い、値は乱数で作って表示しません。
- `KEEP=1` は `dslMixed` の手順の前提のため、一時ディレクトリは残ります。この間は秘密が残ることを手順に明記し、終わりに手で片付け、消えたことを確かめます。
- `resp-*.body` は一時ディレクトリではなく結果の置き場（`KIND_DIR`、187行）に書かれます。秘密を含まない応答の本文で、結果の置き場の検索の対象に入れます。
- Build and Test の文字列の検索は、今回の `OUT_DIR` だけを対象にし、件数だけを記録します（検索する値は仮の管理者のパスワードと署名鍵）。
- 新しい記録には数値と時刻だけを書きます。`env.txt` の `db_url` は資格情報を含まない H2 のファイルの URL だけです。

## 6. 戻し方

本番のコードと配備物を変えないため、配備の戻しは当たりません。`perf/` とテストの変更は、`develop` の squash のコミットを戻せば元に戻ります。

## 7. DevSecOps と Compliance の視点

- 記録のループは読むだけで、追加の権限を付けません。
- テストの補助と失敗の文には、監査の行の `detail`・件数・真偽・スレッドの名前だけを出し、アクセストークン・メールアドレス・パスワードを出しません（NFR7.1、S4.1）。
- テストデータのメールアドレスは予約のドメインだけです（`team.md`）。
- 新しい依存を足さないため、ライセンスの確かめは当たりません。

## 8. 上流との差

- **`dslMixed` で `PERF_DB_URL` が渡る見込み**: `nfr-design/security-design.md` の Assumption は「`dslMixed` の使い捨ての環境には `PERF_DB_URL` が渡らない」としていました。実際には、`dslMixed` の手順は `KEEP=1 ./perf/dsl-timing.sh postgres` で環境を作ります（`perf/README.md` 171行）。そのため、`PERF_DB_URL` は `dsl-timing.sh` 141〜142行で一時ディレクトリの `app.env` の `MASTERSMITH_DB_URL` に書かれ、使い捨てのアプリに渡る見込みです。
  - Q1: A で試しの回の環境を `PERF_DB_URL` を付けて作り直すため、S1.1 の「`dslMixed` の手順に接続先の上書きの口を足す」は要らない見込みです。
  - 機能設計 4.1 の手順1の確かめ（渡るか）は、コード生成で行います。承認済みの文書は書き換えません。
- **一時ファイルの一覧**: `nfr-design/security-design.md` S2.1 の一覧に `auth.header` が無く、`KEEP=1` の扱いが「片付けで消す」と食い違っていました（NFR 設計のレビューの R-01）。5節で実物に合わせました。

## 9. 後の段に回すこと

- **B1（コード生成の計画）**
  - 機能設計 4.1 の手順1・2: `PERF_DB_URL` が渡ることの確かめと、`RETENTION_TIME` を URL の設定で渡せるか（S1.2 の一覧の `INIT` を残すか）。
  - R-02: 確かめの関数の空白・空の要素・重なりの規則と、止める位置。悪い入力の試し（S1.3）の記録。
  - R-01: 5節の一覧を手順書に写し、`KEEP=1` のときの片付けと確かめを書く。
  - NFR 設計のレビューの R-03〜R-05: `armFor` の状態の初期化と、`@Primary` を2つ同時に入れないこと（R-03）、`heldArrived()` が偽のときにテストを失敗にすること（R-04）、失敗の文を組み立てる箇所の洗い出し（R-05）。
  - NFR 要件のレビューの R-03〜R-05（画面のテストの写す位置、409 `ROLE_BUSY` の扱い、自然の順の書き方）。
  - 4.3 のループの名前と細部、`perf/README.md` の (b) 節の手順。
- **Build and Test**: 4.6 の判定、5節の文字列の検索の件数、起動の後の設定の値の確かめ。

## 10. U1・U2・U3 とそろえる点

- `docker/perf/compose.yaml` は U5 では変えません。U1・U2 が perf の対象DB の設定（`innodb_buffer_pool_size`・`shared_buffers`・メモリの上限）を記録・変更しても、U5 は postgres の profile だけを使い、app の cgroup の値を測るため、合否に響きません。測った版は `env.txt` の commit で追えます。
- 配備したアプリを止める時間は、U1・U2 の k6（Performance Validation）とは別の回にします。Build and Test で、U5 の測りの回をまとめて停止を1回に寄せられるかを計画で並べます。
- 結果の置き場 `build/perf-results/` は U1・U2 の k6 と共有します。秘密の検索と片付けの確かめは U5 の `OUT_DIR` に限ります。
- verify と CI の時間の 42 分の判定は、U1・U2 の結合テストの増えと合わせて1つの値で見ます。

## Sources

- 答え: `aidlc/spaces/default/intents/261016-business-data-views/construction/u5-carryover-tests/infrastructure-design/infrastructure-design-questions.md`（Q1: A、Looks correct）
- NFR 設計: `aidlc/spaces/default/intents/261016-business-data-views/construction/u5-carryover-tests/nfr-design/security-design.md`（S1〜S4）・`logical-components.md`（1〜4節）・`traceability.json`
- NFR 設計の承認の場の決定: `aidlc/spaces/default/intents/261016-business-data-views/construction/nfr-design/memory.md`（最後の行）
- NFR 設計のレビュー: `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/nfr-design/units/u5-carryover-tests/df13e747868fc8fa/1.json`（R-01〜R-06）
- NFR 要件: `aidlc/spaces/default/intents/261016-business-data-views/construction/u5-carryover-tests/nfr-requirements/security-requirements.md`（NFR4.5〜4.9・NFR7.1〜7.3）・`tech-stack-decisions.md`（TS1〜TS4）
- 機能設計: `aidlc/spaces/default/intents/261016-business-data-views/construction/u5-carryover-tests/functional-design/functional-spec.md`（3節・4節）・`rules.md`（BR1.5・BR1.6・BR3.x・BR4.x）
- Bolt の計画: `aidlc/spaces/default/intents/261016-business-data-views/inception/delivery-planning/bolt-plan.md`（B1）
- 既存のもの: `.github/workflows/ci.yml`、`perf/dsl-timing.sh`（47・113〜152・170〜193・268〜269・500〜503行）、`perf/README.md`（7・118〜121・155〜189・829〜906行）、`docker/perf/compose.yaml`（25〜40行）
- 読み取りの確かめ: `docker info`（`CgroupVersion=2`、kernel 6.8.0）、動いている `mastersmith-app-1` での cgroup のファイルの有無
- 前の Intent の形: `aidlc/spaces/default/intents/261004-role-menu/construction/cross-cutting/infrastructure-design/cicd-pipeline.md`
- 決まり: `aidlc/spaces/default/memory/team.md`・`aidlc/spaces/default/memory/project.md`

## Assumptions & Open Questions

- [assumption] perf のアプリのコンテナでも、配備したアプリと同じく cgroup のファイルが読める（同じイメージの系列、uid 10001）。4.2 の手順3で回ごとに確かめる。
- [assumption] `dslMixed` の手順で `PERF_DB_URL` は使い捨てのアプリに渡る（8節）。コード生成で確かめる。
- `RETENTION_TIME` を URL の設定で渡せるかは未確認。コード生成で確かめる。
