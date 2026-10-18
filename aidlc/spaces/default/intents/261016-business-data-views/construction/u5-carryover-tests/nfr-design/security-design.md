# セキュリティの設計 — U5 u5-carryover-tests

U5 は、テストと測り方の持ち越しを受け持つ単位で、種類は `library`。この文書は、承認済みの NFR 要件（`aidlc/spaces/default/intents/261016-business-data-views/construction/u5-carryover-tests/nfr-requirements/security-requirements.md`）を満たす作りを、次の4つに分けて決める。

- S1: 使い捨ての環境の接続先の形の確かめ
- S2: 資格情報を持つ一時ファイルの扱い
- S3: 記録のループ
- S4: テストの補助と失敗の文

本番のコードと配備物（`backend/src/main/resources/application.yaml`・`Dockerfile`・`compose.yaml`・`docker/perf/compose.yaml`）は変えない。新しい依存も足さない（TS1）。

## 上流の ID との対応表

| この文書の節 | 当てる NFR 要件 | 持ち越した論点 |
|---|---|---|
| S1 `PERF_DB_URL` の形の確かめ | NFR4.7 | NFR 要件の承認の場の決定（U5 R-01） |
| S2 資格情報を持つ一時ファイル | NFR4.5・NFR4.6 | NFR 要件のレビューの R-04 |
| S3 記録のループ | NFR4.6・NFR4.8・NFR4.9 | NFR 要件のレビューの R-05・R-06 |
| S4 テストの補助と失敗の文 | NFR7.1・NFR7.3 | NFR 要件のレビューの R-02、この段の Q2: A |

待ち合わせの口の形（NFR7.2、Q1: A）は `logical-components.md` の4節に書く。

## S1 `PERF_DB_URL` の形の確かめ（NFR4.7）

### S1.1 方針

- `PERF_DB_URL` の口は残し、台本の中の1つの関数で形を確かめてから使う（承認の場の決定）。
- 呼び出し元は2つある。どちらも同じ関数を呼ぶ。
  - `perf/dsl-timing.sh`（141〜142 行で `MASTERSMITH_DB_URL` を書く所）
  - `dslMixed` の手順に足す接続先の上書きの口（「後の段に回すこと」）
- 確かめを通らなければ、使い捨ての環境を起動する前に終了コード 1 で止める。
- 止めたときは、受け取った値を表示しない。出すのは「どの決まりに外れたか」（前の部分・許さない設定・数の形）だけにする。

### S1.2 受け付ける形

| 部分 | 受け付けるもの |
|---|---|
| 前の部分 | `jdbc:h2:file:/app/data/mastersmith` ちょうど（後ろに `;` の設定が続くか、何も続かない） |
| `;` で区切った設定 | 許す一覧のものだけ: `DEFRAG_ALWAYS=TRUE`、`RETENTION_TIME=<整数>`、`INIT=SET RETENTION_TIME <整数>` |
| `<整数>` | `^[0-9]{1,9}$` に合うもの |
| 全体 | 空白・改行・`\`・`'`・`"` を含まない。同じ設定を2回書かない |

- `INIT` は、`RETENTION_TIME` を URL の設定で渡せないと、コード生成の確かめ（機能設計の 4.1 の手順2）で分かった場合だけ、一覧に残す。渡せると分かったら一覧から外す。
- `DEFRAG_ALWAYS=TRUE` は必須にしない。`--storage` の比べ方では、付けない形と比べるため（`perf/README.md` 152 行）。
- `RETENTION_TIME` の試し（BR4.1）では、`DEFRAG_ALWAYS=TRUE` を付けることを手順で決める。使った値は記録（`env.txt` の `db_url`）に残す。値は資格情報を含まない H2 のファイルの URL だけになる（NFR4.6）。

関数の形（説明のための断片。名前はコード生成で確定する）:

```bash
check_perf_db_url() {
  local url=$1 rest part
  case "$url" in jdbc:h2:file:/app/data/mastersmith|jdbc:h2:file:/app/data/mastersmith\;*) ;; *) echo "PERF_DB_URL: 前の部分が違う" >&2; return 1 ;; esac
  case "$url" in *[[:space:]\\\'\"]*) echo "PERF_DB_URL: 使えない文字を含む" >&2; return 1 ;; esac
  rest=${url#jdbc:h2:file:/app/data/mastersmith}; rest=${rest#;}
  IFS=';' read -r -a parts <<< "$rest"
  for part in "${parts[@]}"; do
    [[ $part == DEFRAG_ALWAYS=TRUE || $part =~ ^RETENTION_TIME=[0-9]{1,9}$ \
       || $part =~ ^INIT=SET\ RETENTION_TIME\ [0-9]{1,9}$ ]] || { echo "PERF_DB_URL: 許さない設定" >&2; return 1; }
  done
}
```

`INIT` の値は空白を含む。そのため、空白の確かめの扱いと、同じ設定を2回書いたときの確かめは、コード生成で確定する。

### S1.3 確かめ方

- コード生成で、関数に悪い入力を1件ずつ与え、止まることと値が表示されないことを記録する。入力は少なくとも次のもの。
  - `INIT=RUNSCRIPT FROM ...`
  - `;` を重ねた設定
  - 別の前の部分（`jdbc:h2:tcp:`・外部の URL）
  - 整数でない数
  - 改行を含む値
- 正しい入力（設定なし・`DEFRAG_ALWAYS=TRUE` だけ・`RETENTION_TIME` つき）が通ることも記録する。
- Build and Test では、起動の後に設定の値を読み取りで確かめる（BR4.1、NFR4.7）。

## S2 資格情報を持つ一時ファイル（NFR4.5・NFR4.6）

### S2.1 一覧

2つの流れの一時ファイルを、1つの一覧にそろえる（R-04）。

| 流れ | ファイル | 持つ秘密 |
|---|---|---|
| `perf/dsl-timing.sh` | `app.env`・`targetdb.env`・`app-targetdb.env`・`login.json`・`ui.env` | 署名鍵・仮の管理者のパスワード・対象DB の資格情報 |
| `perf/README.md` の手順（`dslMixed` を含む） | `app.env`・`k6.env`・`ui.env` | 署名鍵・仮の管理者と試験用の利用者のパスワード |

### S2.2 決まり

- 置き場は、どれもホームの下の権限 700 の一時ディレクトリにする。
  - `dsl-timing.sh` は 113〜114 行の `mktemp -d` と `chmod 700`。
  - README の手順は `$D`（`umask 077`）。
- 書き足しは `umask 077` の中で行う（README の今の形）。
- 値は乱数で作り、画面とログに表示しない。
- 片付けでは、一時ディレクトリごと消す。
- 上の一覧を、次の3か所で同じように使う。
  1. 新しい記録（TSV・`env.txt`・WARN の件数）に含めない対象
  2. Build and Test の文字列検索の対象。仮の管理者のパスワードと署名鍵の値で `build/perf-results/` の今回の結果を検索し、件数だけを記録する。
  3. 片付けで消えたことを確かめる対象
- 新しく足す記録には、数値と時刻だけを書く。列は機能設計の3節の列に限る（NFR4.6）。

## S3 記録のループ（NFR4.6・NFR4.8・NFR4.9）

- 書かない範囲は、記録のループに限る（R-05）。
  - 使い捨てのアプリ自身が `/app/data/gc.log` を書くのは、この範囲の外（`perf/dsl-timing.sh` 137 行）。
  - ループは読むだけにし、記録はホームの側の `build/perf-results/` に書く。
- ファイルの大きさは `/app/data/mastersmith.mv.db` だけを読む。`/app/data` 全体の大きさは読まないため、`gc.log` と混ざらない。
- ループは、`docker exec` で、アプリのイメージにある `bash` の1つのループとして回す（TS4）。
  - `--privileged`・`--user root`・追加の権限は付けない。
  - 1秒ごとに `docker exec` を起こさない。
- 始める前の確かめ（R-06）:
  - cgroup v2 の `/sys/fs/cgroup/memory.peak`・`memory.current`・`memory.max` と、`memory.stat` の `anon`・`file`・`inactive_file`・`active_file`・`kernel` が読めるかを確かめる。
  - どれかが読めなければ、その回を無効として記録し、合否（BR3.5）に使わない。
- 指標の公開（`MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics`）は、一時の `app.env` だけで使い捨てのアプリに当てる。
- 待ち受けの口は `127.0.0.1:18080` のまま変えない（`docker/perf/compose.yaml` 28 行）。
- `memory.peak` を戻すのは、`--force-recreate` だけで行う。

## S4 テストの補助と失敗の文（NFR7.1・NFR7.3）

### S4.1 失敗の文に出してよいもの

テストの補助と失敗の文に出すのは、次のものだけにする。

- 監査の行の `detail`（前と後の権限の値）
- 行の件数と判定の真偽（`releasedByTimeout`・`heldArrived`）
- スレッドの名前

出してはいけないもの: アクセストークン・メールアドレス・パスワード。

### S4.2 `Actor` の `toString`（Q2: A）

- `RoleActors.Actor`（`backend/src/test/java/cherry/mastersmith/role/testsupport/RoleActors.java` 53 行）の `toString` を上書きする。
  - 出すのは `userId` だけ。メールアドレスとトークンは伏せる。
  - team.md の Code Style（秘密を持つ record は `toString` を上書きする）にそろえる。
- 値を読む口（`email()`・`token()`）は変えない。

```java
public record Actor(long userId, String email, String token) {
    @Override
    public String toString() {
        return "Actor[userId=" + userId + ", email=***, token=***]";
    }
}
```

- 確かめ方: 単体テストを1件足し、`toString` に email と token の値が含まれないことを確かめる。テストの説明文は英語で書く。

### S4.3 置き場と依存の向き

- 入れ替えの待ち合わせは `backend/src/test/java/cherry/mastersmith/audit/testsupport/` に置く。
- スレッドの記録は `backend/src/test/java/cherry/mastersmith/role/testsupport/TestRoleBarrier.java` に足す。
- 補助は `src/main` から参照しない。WAR は `src/main` だけから作るため、WAR に入らない。
- `audit/testsupport` は `Thread` だけを受け取り、`role` を知らない。依存の向きは role → audit だけにする。

### S4.4 時間に頼らない（NFR7.3）

- 直したテストに `sleep` を入れない。
- `TestRoleBarrier.WAIT_LIMIT`（20 秒）・Vitest の既定の上限・`waitFor` の既定の上限は延ばさない。
- 性質ベースのテストと E2E は足さない。

## 上流との差

無し。

S1.2 の「`DEFRAG_ALWAYS=TRUE` を必須にしない」は NFR4.7 の要件と食い違わない。NFR4.7 の「`DEFRAG_ALWAYS=TRUE` を保つ」は `RETENTION_TIME` の試しに当てる決まりで、S1.2 ではそれを試しの手順で守る。

## 後の段に回すこと

コード生成の計画で行うこと:

- `PERF_DB_URL` の確かめ（機能設計の 4.1 の手順1・2）。
  - `RETENTION_TIME` を URL の設定で渡せるかを確かめ、S1.2 の一覧の `INIT` を残すか外すかを決める。
- S1.3 の悪い入力の試しと、その記録。
- `dslMixed` の手順（`perf/README.md` の (b) 節）に、接続先の上書きの口を足す。
  - `$D/app.env` に `MASTERSMITH_DB_URL` を書く前に、S1 の関数で確かめる。
- R-03: 画面のテストの試しで写す位置と、「ちょうど 1」と読む前提
- R-04: 入れ替えた順の場合の 409 `ROLE_BUSY` の扱い
- R-05: 自然の順の場合の書き方

Build and Test で行うこと:

- S2 の文字列検索の件数の記録
- 起動の後の設定の値の確かめ（S1.3）

## Sources

- 質問と確認: `aidlc/spaces/default/intents/261016-business-data-views/construction/u5-carryover-tests/nfr-design/nfr-design-questions.md`（Q1: A、Q2: A、Consolidated Summary Confirmation: Looks correct）
- NFR 要件:
  - `aidlc/spaces/default/intents/261016-business-data-views/construction/u5-carryover-tests/nfr-requirements/security-requirements.md`（NFR4.5〜NFR4.9・NFR7.1〜NFR7.3）
  - `aidlc/spaces/default/intents/261016-business-data-views/construction/u5-carryover-tests/nfr-requirements/tech-stack-decisions.md`（TS1〜TS4）
- NFR 要件の承認の場の決定: `aidlc/spaces/default/intents/261016-business-data-views/construction/nfr-requirements/memory.md`（Open questions。U5 R-01）
- NFR 要件のレビュー: `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/nfr-requirements/units/u5-carryover-tests/00d44a7a2452e6b7/1.json`（R-01〜R-06）
- 機能設計:
  - `aidlc/spaces/default/intents/261016-business-data-views/construction/u5-carryover-tests/functional-design/functional-spec.md`（3節・4.1・8.2）
  - `aidlc/spaces/default/intents/261016-business-data-views/construction/u5-carryover-tests/functional-design/rules.md`（BR1.5・BR3.5・BR4.1）
- コードと台本:
  - `perf/dsl-timing.sh`（45・113〜121・137・141〜152・164・268 行）
  - `perf/README.md`（26〜27・152・166〜187・902〜906 行）
  - `docker/perf/compose.yaml`（28・57 行）
  - `backend/src/main/resources/application.yaml`（166 行）
  - `backend/src/test/java/cherry/mastersmith/role/testsupport/RoleActors.java`（53 行）
- 決まり:
  - `aidlc/spaces/default/memory/team.md`（Code Style の `toString` と `testsupport`、Testing Posture）
  - `aidlc/spaces/default/memory/project.md`（Forbidden、Testing Posture の学び）

## Assumptions & Open Questions

- [assumption] `dslMixed` の使い捨ての環境には `PERF_DB_URL` が渡らない、と見込んでいる（`perf/README.md` の手順は `$D/app.env` を使い、`PERF_DB_URL` を読まない）。確定はコード生成で行う。
- [assumption] colima の VM は cgroup v2 で動いている（前の Intent で `memory.peak` を読めた）。読めなければ、S3 の確かめでその回を無効にする。
- `RETENTION_TIME` を URL の設定で渡せるかは未確認。S1.2 の `INIT` を一覧に残すかは、コード生成で決める。
