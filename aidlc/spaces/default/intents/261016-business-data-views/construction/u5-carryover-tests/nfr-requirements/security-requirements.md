# セキュリティの要件 — U5 u5-carryover-tests

U5 はテストと測り方の持ち越しを受け持つ単位で、種類は `library`。本番のコードと配備物（`backend/src/main/resources/application.yaml`・`Dockerfile`・`compose.yaml`）は変えない。この文書では次の2つを決める。

- 使い捨ての環境での測り（`dslMixed` の `memory.peak`、ページキャッシュを減らす手の試し）を安全に行う要件
- 直したテストとテストの補助の品質の要件

性能・規模・信頼性・観測の文書は作らない。段の定義の `produces_kinds` で、`library` は対象の外のため。

## 上流の ID との対応表

枝番は project.md の学びに従い、上流と同じ番号を同じ意味でだけ使う。足す要件は上流の最後の枝番の次から振る。

| この単位の ID | 上流 | 中身 |
|---|---|---|
| NFR4.5 | NFR4（上流の最後は NFR4.4）・FR16 | 使い捨ての環境の資格情報の扱い |
| NFR4.6 | NFR4・FR16.2 | 新しい記録に書いてよいもの |
| NFR4.7 | NFR4・FR16.3 | `RETENTION_TIME` を `INIT` で渡すときの縛り |
| NFR4.8 | NFR4・FR16 | 指標の公開と待ち受けの口の範囲 |
| NFR4.9 | NFR4・FR16.2 | 記録のループの権限 |
| NFR7.1 | NFR7（上流に枝番は無い）・FR15.1 | テストの補助と失敗の文に出さない値と、補助の置き場 |
| NFR7.2 | NFR7・FR15.1・FR15.2 | 待ち合わせの後始末と、放した理由の判定 |
| NFR7.3 | NFR7・FR15.1 | 時間に頼らないこと、足さないテスト |

上流の NFR4.1〜NFR4.4（業務データの SQL・行の値・書き込みの接続・問い合わせの上限）は、U5 に当たらない（理由は `traceability.json`）。

## 1. 使い捨ての環境のセキュリティ（NFR4.5〜NFR4.9）

### NFR4.5 資格情報の扱い

- **対象**: 使い捨ての環境の資格情報。仮の署名鍵、仮の管理者と試験用の利用者のパスワード、対象DB の管理者と読み取りのアカウントのパスワード。
- **要件**:
  - 乱数で作る。
  - ホームの下の権限 700 の一時ディレクトリに置く（`perf/README.md` 21〜29 行、`perf/dsl-timing.sh` 143〜152 行の今の形）。
  - 値を画面・ログに表示しない。
  - リポジトリ・`build/perf-results/`・Intent の記録・コミットに入れない。
  - 片付けで一時ディレクトリごと消す。
- **根拠**: `project.md` の Forbidden（秘密情報をソースや設定に直接書かない、`.env` をコミットしない）。
- **確かめ方**:
  - コード生成で、足した台本を読み合わせる。
  - Build and Test で、片付けの前に結果の置き場（`build/perf-results/` の今回の結果）を仮の管理者のパスワードと署名鍵の値で文字列検索し、0 件であることを件数だけで記録する（値は表示しない）。

### NFR4.6 新しい記録に書いてよいもの

- **対象**: 新しく足す記録。1秒ごとの TSV、`memory.stat` の内訳、内部DB のファイル（`/app/data/mastersmith.mv.db`）の大きさ、WARN の件数。
- **要件**:
  - 数値と時刻だけを書く。列は `aidlc/spaces/default/intents/261016-business-data-views/construction/u5-carryover-tests/functional-design/functional-spec.md` 3節の列に限る。
  - 一時ディレクトリの環境ファイル（`app.env`・`k6.env`・`ui.env`）の中身、トークン、対象DB の接続情報を含めない。
  - `env.txt` の `db_url` に書く内部DB の接続先は、資格情報を含まない H2 のファイルの URL だけにする（`perf/dsl-timing.sh` 139 行の注記と同じ）。
- **根拠**: `project.md` の Forbidden（対象DB の接続情報をログに含めない、トークンを含めない）。
- **確かめ方**:
  - 台本の読み合わせで、TSV の見出しの列が決めた列だけであることを確かめる。
  - Build and Test で、`env.txt` と TSV に `password`・`PASSWORD`・`jdbc:mysql`・`jdbc:mariadb`・`jdbc:postgresql` が無いことを件数で確かめる。

### NFR4.7 `RETENTION_TIME` を `INIT` で渡すときの縛り

H2 の `INIT` は、接続のたびに SQL を動かす。そのため、渡す形を次のとおり縛る。

- **要件**:
  - `INIT` の中身は、台本に固定した `SET RETENTION_TIME <数>` の1文だけにする。
  - `RUNSCRIPT` などのほかの文や、台本の外から受け取る文字列を入れない。数は台本の中の整数の定数にする。
  - 上書きは、使い捨ての環境の一時の環境ファイル（`app.env` の `MASTERSMITH_DB_URL`）だけで行う。
  - 配備物と `.env.example` には足さない。
  - `DEFRAG_ALWAYS=TRUE` を保つ（BR4.1）。
- **適用の範囲**: URL の設定（`;RETENTION_TIME=<数>`）で渡せるときも、数の扱いと置き場の決まりは同じとする。
- **確かめ方**:
  - コード生成で台本を読み合わせる。
  - Build and Test で、起動の後に設定の値を読み取りで確かめ、効いたことを記録する（BR4.1。確かめていない測りは試しの結果に使わない）。

### NFR4.8 指標の公開と待ち受けの口の範囲

- **要件**:
  - 指標の公開（`MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics`）は、使い捨てのアプリにだけ当てる。
  - 配備したアプリの公開の範囲は変えない（`project.md` の学び、`perf/README.md` 120 行）。
  - 使い捨てのアプリの待ち受けの口は `127.0.0.1:18080` のまま変えない（`docker/perf/compose.yaml`）。
- **確かめ方**:
  - コード生成の差分で、`compose.yaml`・`docker/perf/compose.yaml`・`backend/src/main/resources/application.yaml` に変更が無いことを確かめる。
  - 足した台本の中で、公開の範囲を指定しているのが一時の環境ファイルだけであることを確かめる。

### NFR4.9 記録のループの権限

- **要件**:
  - コンテナの中で回す記録のループは、cgroup のファイル（`memory.current`・`memory.peak`・`memory.stat`・`memory.max`）と `/app/data` のファイルの大きさを読むだけにする。
  - コンテナの中に書き込まない。`--privileged` や追加の権限、ボリュームの書き込みを使わない。記録はホームの側（`build/perf-results/`）に書く。
  - `memory.peak` を戻すのは、使い捨てのアプリの作り直し（`--force-recreate`）だけで行う。
- **確かめ方**: 台本の読み合わせ。`docker exec` の引数に権限を広げる指定が無いこと。

## 2. テストの品質（NFR7.1〜NFR7.3）

### NFR7.1 出さない値と補助の置き場

- **要件**:
  - テストの補助（監査の書き込みの順を入れ替える待ち合わせ、`TestRoleBarrier` に足すスレッドの記録）とテストの失敗の文には、監査の行の `detail`（前と後の権限の値）だけを出す。
  - `RoleActors` が持つアクセストークンとメールアドレスは、ログにも失敗の文にも出さない（`backend/src/test/java/cherry/mastersmith/role/testsupport/RoleActors.java` 38〜51 行）。
  - 補助は `backend/src/test/java/cherry/mastersmith/audit/testsupport/`・`backend/src/test/java/cherry/mastersmith/role/testsupport/` に置き、WAR に入れない。
- **根拠**: `project.md` の Forbidden（トークンを含めない、メールアドレスをアプリのログに含めない）、team.md の Code Style（`testsupport` の置き場）。
- **確かめ方**:
  - コード生成で、補助と失敗の文を組み立てる箇所を読み合わせる。
  - 補助のクラスが `src/main` から参照されないことを確かめる（WAR は `src/main` だけから作られる）。

### NFR7.2 待ち合わせの後始末と、放した理由の判定

- **要件**:
  - 待ち合わせは、テストが失敗しても、止めているスレッドを必ず放す（`disarm` を `finally` か後始末で呼ぶ）。
  - 同じ Spring の文脈を使うほかのテストに、止まったスレッドや準備した状態を残さない。
  - 待ちの上限は `TestRoleBarrier.WAIT_LIMIT` で、延ばさない。
  - 上限の時間で放されたのか、後の保存の監査が書かれて放されたのかを取り違えない。入れ替えた順の場合は `releasedByTimeout()` が偽であることを確かめ、真なら失敗にする（BR1.5）。
  - 放す条件の具体（後の保存の要求のスレッド、またはその要求の監査に限るか。レビューの R-06）は、コード生成の計画で決める。
- **確かめ方**:
  - #7 の自然の順と入れ替えた順の2つの場合を毎回流す（BR1.5）。
  - 二段の確かめの記録（BR1.6）を残す。
  - 後始末の位置を読み合わせる。

### NFR7.3 時間に頼らないこと、足さないテスト

- **要件**:
  - 直したテストに `sleep` を入れない。Vitest の既定の上限（5 秒）と `waitFor` の既定の上限は延ばさない（team.md、BR2.3）。
  - 性質ベースのテストと E2E は足さない。U5 は純粋な関数と画面の流れを足さないため。
- **確かめ方**: 差分の読み合わせと `./gradlew verify`。

## 3. 上流との差

無し。

## 4. 後の段に回すこと

- **コード生成の計画で決めること**:
  - R-03: 画面のテストの試しで写す位置と、「ちょうど 1」と読む前提
  - R-04: 入れ替えた順の場合に返る 409 `ROLE_BUSY` の扱い
  - R-05: 自然の順を確実に作るか
  - R-06: 放す条件の狭め方（NFR7.2）
  - あわせて、BR4.1 の確かめを行う（`PERF_DB_URL` が `dslMixed` の使い捨ての環境で効くか、`RETENTION_TIME` を URL で渡すか `INIT` で渡すか。`INIT` で渡す場合は NFR4.7 を当てる）。
- **Build and Test で行うこと**:
  - `dslMixed` の `memory.peak` の測り（2回とも 90% 未満で Met。BR3.5）
  - ページキャッシュを減らす手の試し（BR4.1〜BR4.3）
  - NFR4.5・NFR4.6 の結果の置き場の件数の確かめ
  - 効いた手を配備物に取り込むかを依頼者に諮ること（BR4.2）

## Sources

- 質問と確認: `aidlc/spaces/default/intents/261016-business-data-views/construction/u5-carryover-tests/nfr-requirements/nfr-requirements-questions.md`（質問なし、Consolidated Summary Confirmation: Looks correct）
- 要件: `aidlc/spaces/default/intents/261016-business-data-views/inception/requirements-analysis/requirements.md`（FR15・FR16、NFR1〜NFR8）
- 機能設計: `aidlc/spaces/default/intents/261016-business-data-views/construction/u5-carryover-tests/functional-design/functional-spec.md`（3節・4.1・7節・8.2）、`aidlc/spaces/default/intents/261016-business-data-views/construction/u5-carryover-tests/functional-design/rules.md`（BR1.5・BR1.6・BR2.3・BR3.5・BR4.1〜BR4.3）
- レビュー: `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/functional-design/units/u5-carryover-tests/7dacbaa3e842d0af/`（R-03〜R-06）
- コードと台本:
  - `perf/dsl-timing.sh`（139〜152 行）
  - `perf/README.md`（21〜29・120・152・900〜903 行）
  - `docker/perf/compose.yaml`
  - `backend/src/main/resources/application.yaml`（161〜166 行）
  - `backend/src/test/java/cherry/mastersmith/role/testsupport/RoleActors.java`
- 決まり: `aidlc/spaces/default/memory/project.md`（Forbidden・Testing Posture の学び）、`aidlc/spaces/default/memory/team.md`（Testing Posture・Code Style）
