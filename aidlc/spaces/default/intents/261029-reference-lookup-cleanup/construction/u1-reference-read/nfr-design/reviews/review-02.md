## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-10-30T07:41:09Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-design/reliability-design.md > 1.2 定義の取得の全体の上限・T-T4 | `referenceReadTotal` が 1 秒未満のとき「設定の結び付けの時点で誤りにして起動を止める」とあるが、既存の対象DB の設定は不正でも起動を続ける作りになっている。`TargetDbProperties` の注記は「数や列挙で受けると結び付けで起動が止まるため、点検（`TargetDbSettings.inspect`）で判定して起動は続ける（BR1.3）」とあり、`addTimingProblems` は `connectTimeout`・各 `queryTimeout`・プール値の範囲外を `Invalid`（対象DB を使わず項目名だけ WARN）にしている。「既存の時間の設定の誤りが起動を止めるのと同じ扱い」は、`Duration` として読めない値だけに当たり、範囲外の値には当たらない。新しい設定だけ起動を止めると、1 つの環境変数の打ち間違いでログイン等を含むアプリ全体が上がらなくなる。T-T4 が「1 秒未満で起動が止まる」ことをテストとして固めてしまう | `referenceReadTotal` の範囲の確認を `TargetDbSettings.addTimingProblems` に足し、1 秒未満は対象DB を使わない（項目名だけ WARN、起動は続く）に改める。1.2 節の理由の文と T-T4 の期待を同じ内容に直す | Resolved (reviewer) |
| R-02 | Major | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-design/reliability-design.md > 1.1 問い合わせ1回の上限 | `QueryTimeout.reference` を足すとあるが、1 秒未満の値の点検を足す記述が無い。既存は `generate`・`compare`・`list`・`detail` のみ `addTimingProblems` が 1 秒未満を弾き、`write` は含まれない。`TargetReadSession` は秒が 1 未満だと `IllegalArgumentException` を投げるため、`reference` を 0 や 500ms にすると定義の取得のたびに 500 になるか、秒への切り捨てで上限が 0 になる。あわせて `TargetDbSettings` の `queryTimeout(ReadPurpose)` の `switch` に `REFERENCE` を足す記述が無い（足さなければコンパイルできない） | `reference` を `addTimingProblems` の対象に足し（1 秒未満は点検の誤り）、`queryTimeout(ReadPurpose)` の `switch` に `REFERENCE` を足すことを 1.1 節と `logical-components.md` の `TargetDbProperties` 行に明記する。T-T4 に `reference` の 1 秒未満の点検の期待を足す | Resolved (reviewer) |
| R-03 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-design/observability-design.md > 2 ログの設計 | WARN を `ReferenceLookup`（service）で出し、web 層で `BusinessException` に写して既存の `@RestControllerAdvice` を通す形のため、同じ失敗のログが 1 回で済むかが書かれていない（Code Style は「例外のログは変換する境界で 1 回だけ」）。`budgetExceeded` は真偽値だけで値を出さず、漏えいの面では問題が無い | 既存の 503（`BUSINESS_TARGET_DB_*`）が変換の境界で別のログを出すかを確認し、出すなら WARN を 1 か所にまとめるか、出す側を `ReferenceLookup` の 1 行に一本化する旨を書く | Resolved (reviewer) |
| R-04 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-design/logical-components.md > 後の段に回すこと（F2: A） | 既存の業務データの一覧で `sortable` にした長い文字のカラムを MySQL で並べると 1038 で 500 になりうる件を「見立て」のまま後の段へ送っている。担当の段が Feedback & Optimization とされ、直すかは後の Intent の要件で決めるとなっており、持ち主と期限が弱い | Build and Test などで一覧の再現を 1 回確かめる担当を決めて記録する（直すかの判断は後の Intent のままでよい） | Resolved (reviewer) |
| R-05 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-design/reliability-design.md > 1.2・5.1 値の受け取り | TargetReadAccess.referenceReadTotal() は Usable のときだけ値を持つ（Usable.referenceReadTotal() を返す）。NotConfigured・Invalid のときにこの口が何を返すか（例外か既定の 10 秒か）が書かれていない。現行の withRead は Usable でないと work を呼ばずに戻るため、withRead の中で呼べば問題にならないが、その呼び方の制約は文書にない。U2 の ⑩ も同じ口を呼ぶ。 | referenceReadTotal() は withRead の work の中でだけ呼ぶこと、Usable でないときは IllegalStateException を投げることを 1.2 か 5.1 に 1 行足し、T-T4 に単体テストを 1 件足す。 | New |

> R-01 Reviewer note: reliability-design.md 1.2 と T-T4 が、referenceReadTotal の 1 秒未満を addTimingProblems の点検の誤り（REFERENCE_READ_TOTAL、Invalid、項目名だけの WARN、起動は続く）に直している。既存の addTimingProblems（TargetDbSettings.java 192 行以降）が範囲外の値で起動を止めず Invalid にする扱いと一致する。

> R-02 Reviewer note: 1.1 に reference の 1 秒未満の点検（QUERY_TIMEOUT_REFERENCE）と Usable.queryTimeout の switch への case REFERENCE を足し、logical-components.md 1節と T-T4 にも書いた。現行の switch（130〜136 行）は列挙を尽くす形で、REFERENCE を足さないとコンパイルできないという記述も正しい。JdbcTargetReadAccess.withRead の LIST・DETAIL 限定（92 行）に REFERENCE を足す点も 1.1 に入っている。

> R-03 Reviewer note: observability-design.md 2.1 が、診断の WARN は ReferenceLookup のロガーでちょうど 1 行、GlobalExceptionHandler の変換の WARN は別の行、と読みを明記し、確かめ方も ReferenceSecretLeakIT に置いた。U2 の observability-design.md 2.2 と同じ読みで、upstream の差の表にも載っている。

> R-04 Reviewer note: logical-components.md 6節で、MySQL の 1038 の再現を確かめる持ち主を Build and Test、直すかを後の Intent の要件定義と決め、確かめの中身（値とメッセージを記録しない）も書いた。

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| 実行できる検証ツール | 実行していない | この審査は読み取り専用の指定で、検証ツールの指定も無かった。cross-reference は TargetDbSettings.java・TargetReadSession.java・JdbcTargetReadAccess.java の読み取りと、U2 の指定された 3 ファイルの照合で行った。 |

### Summary

前回の 4 件はすべて文書で解消され、既存コードの扱い（Invalid で起動は続く、switch は列挙を尽くす）とも整合している。U1 と U2 の継ぎ目の名前・形も一致した。残るのは referenceReadTotal() を Usable 以外で呼んだときの扱いが未記述という軽微な点だけで、READY とする。
