## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-10-30T14:56:58Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/nfr-design/reliability-design.md > 6節 T-T1（117行目）と backend/src/test/java/cherry/mastersmith/businessdata/web/ReferenceTimeoutIT.java > silentTarget・silentRows（277〜305・358〜383行目） | U2 の設計の T-T1 は「応答しない対象DB（ServerSocket）で参照つきの作成・更新が 503 BUSINESS_TARGET_DB_TIMEOUT」と期待している。U1 の実装では、接続を受けて何も返さない相手は接続を借りる段で止まり 503 BUSINESS_TARGET_DB_UNAVAILABLE になる（code-summary.md 4節の差 4、ReferenceTimeoutIT が実証）。U2 は ⑩ の読み取りに同じ口を使うため、設計のままの期待で U2 のテストを書くと落ちる。 | U2 のコード生成の計画で T-T1 の期待を UNAVAILABLE に直す。TIMEOUT の経路は U1 と同じく、眠る関数を含むビューを参照先にして確かめる。 | New |
| R-02 | Minor | backend/src/test/java/cherry/mastersmith/businessdata/web/ReferenceTimeoutIT.java > narrowedByTheTotal（255〜257行目）・slowTarget（235行目） | 実際の DB の眠りを使い、経過時間の下限と上限（1,500 ms 以上かつ 4,500 ms 未満、4 秒以上かつ 10 秒未満）で合否を決めている。全体の上限 2 秒は切り上げ秒に絞られるため、負荷の高い CI では上限 4.5 秒を超えて不安定になるおそれがある（project.md の「時間の境に合否を預けない」とも方向が違う）。手元と CI の1回目は通過済み。 | 当面は直さなくてよい。CI で不安定になった時点で、上限を緩めるか、締め切りの絞りは単体テスト（ReferenceReadDeadlineTest）に任せて結合テストでは 503 TIMEOUT の code だけを見る形にする。 | New |
| R-03 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/code-generation/traceability.json > NFR2.2・NFR5.1・NFR5.2 | NFR2.2 は「短い試し走り（各場面 1 VU・10 秒）で閾値の指標が出ること」を Build and Test の確かめとしているが、行は OK（target は perf/k6/scenarios.js）。NFR5.1 は B1 のパッケージごとの実測値を記録していない（code-summary.md 3.2節、差 11）のに OK。 | NFR2.2 は台本の用意までを OK とし、試し走りは Build and Test へ引き継ぐ旨を target に足す。NFR5.1 は Build and Test の実測値（B1・B2 のパッケージごとの値）で補う。 | New |
| R-04 | Minor | perf/README.md > 「他テーブル参照の読み取りの場面」の流す順 (3) と perf/k6/scenarios.js > ピッカーの場面（page: 1 固定） | 「深いページのピッカー（PostgreSQL で 1,000 ページ目）を参考に1回」に当たる場面も環境変数も台本の手順も無く、手順書が実行できない（code-summary.md 6節で自己申告済み）。 | Build and Test の質問で、場面に足すか手順から外すかを決め、README を直す。 | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| 検証ツール・テストの実行 | 実行せず（Read のみの指示のため） | code-summary.md の記録（verify 成功、単体 2,748・結合 1,523、E2E 256 件通過、CI 1回目で success）を引用するだけで、実測の再確認はしていない。 |
| 静的読み取りでの確認 | 問題なし | 下の「確認した点」のとおり。 |

**確認した点（すべてソースで読んだ）**

- SQL: 行の SQL を作って実行するのは RowSqlBuilder・RowSqlExecutor.search だけ。識別子は column() で引用し、値は bindAll でバインドし、参照先は DSL に照らした判定の材料（accessOf・usableSearchFields）を通る。search の本体の中の prepareStatement は 1 つ増えたが、メソッド・クラスの単位では変わらない。spotbugs-exclude と ArchUnit の境界テストは manifest に無く、BusinessDataBoundaryArchitectureTest は prepareStatement・createStatement・prepareCall を見張ったまま。
- 同じ 403: Denied は名前・権限・DSL・参照元が FULL でない・REFERENCE への検索・行が無い・型として読めない（ラベル）を区別せず、コントローラーが ACCESS_DENIED に写す。条件のカラムの照合（403）は値の型の読み取り（400）より先。
- 漏えい: 値・ラベル・セルを持つ型は toString を伏せ字にしてある。警告は purpose・reason・sqlState・vendorCode・exceptionClass・budgetExceeded だけ。ReferenceSecretLeakIT は TRACE を有効にして 5 経路を見ている。
- 設定: 参照先の文の上限と全体の上限が 1 秒未満なら Invalid（項目名だけ）で起動は続く。TargetReadAccess.referenceReadTotal() は Usable でないとき例外。
- U2 との継ぎ目: ReferenceReadDeadline の形（record・start・narrow・MIN_REMAINING 1 秒）は U2 の reliability-design.md 1.2 と一致。ofBudgetExceeded() は依頼者の決定で、U2 は docs の budgetExceeded() をこの名前に読み替える必要がある（コード生成の計画に書いてある）。
- 画面: 型 reference?: unknown と parseValidation の写しだけで、描画の変更は無い。E2E 見本に reference: null を足しただけで安全。
- perf: 既存の閾値は変えず、参照の場面だけ REF_P95_LIMIT_MS を足している。未測定の NFR2.1・2.3・2.4・2.5・AC3.5.4 は Deferred。

### Summary

U1 の B1・B2 は、(a)〜(k) の必須のテスト、同じ 403、伏せ字、設定の扱い、U2 との継ぎ目のどれも設計と読み合って矛盾が無く、Critical・Major は見つからなかった。残るのは U2 の設計の期待の食い違い（応答しない相手は UNAVAILABLE）、時間に合否を預ける結合テスト、traceability の OK の言い過ぎ、perf の手順書の宙に浮いた1手順の4件の Minor で、いずれも後の段・U2 で扱える。
