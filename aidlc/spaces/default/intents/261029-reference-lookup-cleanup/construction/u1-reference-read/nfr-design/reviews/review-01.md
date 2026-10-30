## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-10-30T02:52:46Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-design/reliability-design.md > 1.2 定義の取得の全体の上限・T-T4 | `referenceReadTotal` が 1 秒未満のとき「設定の結び付けの時点で誤りにして起動を止める」とあるが、既存の対象DB の設定は不正でも起動を続ける作りになっている。`TargetDbProperties` の注記は「数や列挙で受けると結び付けで起動が止まるため、点検（`TargetDbSettings.inspect`）で判定して起動は続ける（BR1.3）」とあり、`addTimingProblems` は `connectTimeout`・各 `queryTimeout`・プール値の範囲外を `Invalid`（対象DB を使わず項目名だけ WARN）にしている。「既存の時間の設定の誤りが起動を止めるのと同じ扱い」は、`Duration` として読めない値だけに当たり、範囲外の値には当たらない。新しい設定だけ起動を止めると、1 つの環境変数の打ち間違いでログイン等を含むアプリ全体が上がらなくなる。T-T4 が「1 秒未満で起動が止まる」ことをテストとして固めてしまう | `referenceReadTotal` の範囲の確認を `TargetDbSettings.addTimingProblems` に足し、1 秒未満は対象DB を使わない（項目名だけ WARN、起動は続く）に改める。1.2 節の理由の文と T-T4 の期待を同じ内容に直す | New |
| R-02 | Major | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-design/reliability-design.md > 1.1 問い合わせ1回の上限 | `QueryTimeout.reference` を足すとあるが、1 秒未満の値の点検を足す記述が無い。既存は `generate`・`compare`・`list`・`detail` のみ `addTimingProblems` が 1 秒未満を弾き、`write` は含まれない。`TargetReadSession` は秒が 1 未満だと `IllegalArgumentException` を投げるため、`reference` を 0 や 500ms にすると定義の取得のたびに 500 になるか、秒への切り捨てで上限が 0 になる。あわせて `TargetDbSettings` の `queryTimeout(ReadPurpose)` の `switch` に `REFERENCE` を足す記述が無い（足さなければコンパイルできない） | `reference` を `addTimingProblems` の対象に足し（1 秒未満は点検の誤り）、`queryTimeout(ReadPurpose)` の `switch` に `REFERENCE` を足すことを 1.1 節と `logical-components.md` の `TargetDbProperties` 行に明記する。T-T4 に `reference` の 1 秒未満の点検の期待を足す | New |
| R-03 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-design/observability-design.md > 2 ログの設計 | WARN を `ReferenceLookup`（service）で出し、web 層で `BusinessException` に写して既存の `@RestControllerAdvice` を通す形のため、同じ失敗のログが 1 回で済むかが書かれていない（Code Style は「例外のログは変換する境界で 1 回だけ」）。`budgetExceeded` は真偽値だけで値を出さず、漏えいの面では問題が無い | 既存の 503（`BUSINESS_TARGET_DB_*`）が変換の境界で別のログを出すかを確認し、出すなら WARN を 1 か所にまとめるか、出す側を `ReferenceLookup` の 1 行に一本化する旨を書く | New |
| R-04 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-design/logical-components.md > 後の段に回すこと（F2: A） | 既存の業務データの一覧で `sortable` にした長い文字のカラムを MySQL で並べると 1038 で 500 になりうる件を「見立て」のまま後の段へ送っている。担当の段が Feedback & Optimization とされ、直すかは後の Intent の要件で決めるとなっており、持ち主と期限が弱い | Build and Test などで一覧の再現を 1 回確かめる担当を決めて記録する（直すかの判断は後の Intent のままでよい） | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| 読み取りのみの確認（`TargetDbProperties`・`TargetDbSettings`・`TargetReadSession`・`spotbugs-exclude.xml`） | 起動を止めない作りと、4 引数のコンストラクターの存在を確認 | 指摘 1・2 の根拠。`TargetReadSession` の 4 引数のコンストラクターは設計どおり使える |

U1 R-05 の解き方、新しい口と境界、漏えい、(a)〜(k) の対応については、判定を次に記す。

- R-05（長い行を読んだ後に落とすと `TOO_MANY`・次のページがずれる）は、閉じている。値の長さの条件（`CHAR_LENGTH(値) <= 10000`）を SQL の WHERE に置き、数える対象を条件を通った行だけにしたため、読んだ行の数がそのまま使える行の数になる（`performance-design.md` 1.3）。T-B1（199/200/201/10,000）・T-B2/T-B3（長い行が混ざる 200・201 行）・T-B4（`countState`）・T-B5（ピッカー 20/21/40/41 行と長い行）・T-B6（10,000・10,001・2,000,000 字）が両側の境界を押さえている。
- 新しい口 `sortExpression`・`lengthWithin` は、照合後の引用済み参照と定数（10,000・100）だけを受け、値はバインドのままである。`prepareStatement`・`createStatement` を呼ぶ場所は `RowSqlExecutor.search` のまま増えず、`spotbugs-exclude.xml` の `search` 向けの既存の除外の範囲で足りる見込みである（コード生成で SpotBugs を流して確かめる、と明記済み）。`RowDialect` に口を足す点は、BR11.3 のとおりコード生成の計画で承認を得る前提である。
- 行の値・JDBC 例外のメッセージは、`toString` の伏せ字、固定の道、`RowQueryFailure`（メッセージを持たない）、WARN のキーを限る形で守られる。`budgetExceeded` は値を含まない。(d) は `ReferenceSecretLeakIT` と外部エクスポートを有効にした結合テストで確かめる計画になっている。
- (a)〜(k) は `security-design.md` 6 節、`scalability-design.md` 2 節、`reliability-design.md` 4 節で網羅されている。(j) の接続の数は `BusinessDataConnectionUsageIT` 相当の結合テストで決定的に確かめる。

### Summary

長い行の数え違い（R-05）は SQL 側の除外と境界の結合テストで閉じており、SQL の組み立ても既存の境界と除外を緩めない。ただし新しい 2 つの時間の設定の扱いが既存の「不正でも起動を続ける点検」の作りと食い違う（Major 2 件）。指摘の許容数の内（Major は 2 件以下）のため READY とし、2 件はコード生成の前に設計の文を直すことを勧める。
