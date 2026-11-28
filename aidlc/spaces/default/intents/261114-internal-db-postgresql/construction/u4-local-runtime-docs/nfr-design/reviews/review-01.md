## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u4-local-runtime-docs/nfr-design/security-design.md > DD-4 healthcheck | healthcheck の `pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}` はホストを指定していない。公式の postgres イメージは、初めて起動するときの初期化の間、TCP を聞かない一時的なサーバーを unix ソケットだけで動かす。そのため、ソケット経由の `pg_isready` は初期化の途中で成功しうる。そうなるとアプリの `depends_on`（service_healthy）が先に通り、アプリが TCP で接続して失敗する。アプリ側に再試行は無く、`restart: "no"` のため、止まったままになる。また、`$$` の展開は `CMD-SHELL` の形でだけ効くが、DD-4 に形の指定が無い。 | healthcheck を `CMD-SHELL` の形で書き、`pg_isready -h 127.0.0.1`（TCP）を使うと決める。初めて起動する場合（空のボリューム）でも、アプリが接続に失敗しないことを、Code Generation か Deployment Execution の確かめに入れる。 | New |
| R-02 | Major | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u4-local-runtime-docs/nfr-design/security-design.md > DD-6 | 手順の例 `docker compose exec internal-db-postgres pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB" > …` の `$POSTGRES_USER`・`$POSTGRES_DB` は、`"` で囲んだまま手元のシェルで展開される。手元のシェルにこれらの変数は無い（`.env` は手元のシェルに入らない）ため、空になって失敗する。「コンテナの中の環境変数を使う」という設計の意図が、この書き方では成り立たない。さらに、`exec` は端末があると TTY を割り当てるため、`>` で受けるバイナリの出力（`-Fc`）が壊れうる（`-T` が要る）。戻しも同様に、標準入力から渡す形（`-T` と `<`）が決まっていない。 | 例を `docker compose exec -T internal-db-postgres sh -c 'pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB"' > …`（変数をコンテナの中で展開する単一引用符の形）に直す。戻しも `-T` と標準入力の形で書く。ソケットの信頼設定で、パスワード無しで動くことを、使い捨てのコンテナで確かめる（設計の前提の確かめを Code Generation の最初に置くとした仮定を、確かめの手順に明記する）。 | New |
| R-03 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u4-local-runtime-docs/nfr-design/traceability.json > upstream_ids・coverage | 上流の一覧が NFR2.1〜NFR2.6 の 6 件だけで、NFR 要件の他の ID（NFR1.1・3.1・4.1・4.2・5.1・6.1・7.1・8.1・9.1）が載っていない。設計の文書（DD-1・DD-4・DD-6・DD-8〜DD-11）はこれらを扱っているが、対応の記録に出ていないため、網羅を機械で確かめられない。 | `tech-stack-decisions.md` の ID も上流に含め、DD への対応の行を足す。足せないなら、含めない理由を成果物に書く。 | New |
| R-04 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u4-local-runtime-docs/nfr-design/security-design.md > DD-2 | 資格情報を `${…}`（既定値なし）で渡すと、変数が未設定のとき、Compose は profile を選んでいないサービスの分も含めて、すべての `docker compose` の実行で未設定の警告を出す。既定の H2 の利用者の画面が毎回ノイズになる。「空なら起動を拒む」という意図は、`${VAR:-}` でも同じに保てる。 | `${VAR:-}` と書くか、警告が出ることを既知の制約として文書に書くか決める。DD-11 の `docker compose config`（profile なし）の確かめに、警告が出ないことも入れる。 | New |
| R-05 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u4-local-runtime-docs/nfr-design/security-design.md > DD-3・DD-5 | DD-3 は「H2 に戻すときは 3 行をコメントに戻す」とするが、`.env.example` の `MASTERSMITH_DB_PASSWORD=` は今コメントではなく有効な行で、`MASTERSMITH_DB_URL`・`MASTERSMITH_DB_USERNAME` だけがコメントである。DD-5 は審査の「N-5」に言及するが、NFR 要件のレビューには R-01〜R-04 しかなく、本文の N-1〜N-4 とも対応表が無い。 | 戻し方を実際の `.env.example` の形に合わせて書く。審査の指摘の呼び名（R-01〜R-04 か N-1〜N-4 か）を揃え、存在しない N-5 の参照を直す。 | New |
| R-06 | Info | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u4-local-runtime-docs/nfr-design/security-design.md > DD-1・DD-3 | DD-1 の「ログは json-file（他のサービスと同じ）」は、見本の `targetdb-postgres` には当てはまらない（app・lgtm・mailpit と同じ）。DD-3 のスーパーユーザーの利用者は、手元・単一インスタンスの前提として明示して受け入れており、既知の制約として文書に書く決定も付いているので、了承できる。 | 対応は不要。文書に書くときの参照先を app・lgtm・mailpit と直すとよい。 | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| 読み取りによる確かめ（compose.yaml・.env.example・dependabot.yml・application.yaml） | targetdb-postgres の形（イメージ・profile・ports 無し・512m・ボリューム `/var/lib/postgresql`）と MASTERSMITH_DB_* の名前は設計の記述と一致。アプリのサービスに今 depends_on は無い | 設計の事実は正しい。`required: false` の足し方も成り立つ |

### Summary

NFR 要件の NFR1.1・NFR2.1〜2.6・NFR3.1・NFR4.1・NFR4.2・NFR5.1・NFR6.1・NFR7.1・NFR8.1・NFR9.1 は DD-1〜DD-11 で漏れなく扱われ、審査の 4 件への答えも正しく、U4 の境界とも矛盾しない（上流との差の記録も正しい）。Major は 2 件（healthcheck の TCP と初回の初期化・バックアップ手順のシェルの展開と `-T`）で、どちらも設計の決定を直すだけで済むため、READY とするが、承認の場で直すことを勧める。
