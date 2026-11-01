## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Iteration:** 1
**Verdict Dictated:** No
**Date:** 2026-10-31

### Prior findings

| ID | Now | Severity | Note |
|---|---|---|---|
| R-01 | Resolved | Minor | 保存前の参照の段の想定外の失敗は、`warnReference` が WARN を1行だけ出してから `BusinessDataUnexpectedException`（500）を投げる。キーは purpose・stage・reason・sqlState・vendorCode・exceptionClass・budgetExceeded で、値の無いキーは出さない。値・名前・メッセージ・例外オブジェクトは出さない（throwable は null をテストで確認）。拒否と 400 は WARN を出さず、応答と失敗の理由は変わらない。 |
| R-02 | Deferred | Minor | Build and Test で扱う（依頼者の決定）。このレビューでは再評価しない。 |
| R-03 | Resolved | Minor | 種の台本の末尾に、biz_refcreate が 0 行でないとき止める確かめを足した。PostgreSQL は DO ブロックの RAISE（ON_ERROR_STOP で psql が失敗）、MySQL・MariaDB は 0 行でなければ 2 行を返す副問い合わせで 1242 の誤りになり、道具が 0 でない終了になる。0 行なら NULL で通る。誤りの文に値も件数も出さない。 |
| R-04 | Deferred | Minor | Build and Test で扱う（依頼者の決定）。このレビューでは再評価しない。 |

### New findings

| Severity | Location | Finding | Required action |
|---|---|---|---|
| Minor | backend/src/main/java/cherry/mastersmith/businessdata/service/BusinessDataWriteService.java > referenceStep の catch BusinessDataUnexpectedException | 確かめの口を通らず直接投げられた手がかり無しの想定外は stage が null になり、WARN から stage キーが落ちる（purpose・reason・budgetExceeded は残る）。現状の経路（atStage 経由・detail の失敗）では stage が付くことをテストで確かめており、実害は小さい。また借りた接続を返してから WARN を出す順序は構造上守られているが、その順序を直接確かめるテストは無い。 | 対応は任意。必要なら Build and Test か次の機会に、stage 無しの WARN を許す旨を記録するか、順序を確かめるテストを足す。 |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| 読み取りでの確認（Read のみ） | 実装・テスト・種の台本を確認 | 指定どおり git・gradlew などは使わず、verify（単体 2,807・結合 1,652・失敗 0）・カバレッジ・E2E・CI は依頼文の報告に依る |

### Summary

R-01 と R-03 の直しは正しく、WARN は1行で値・名前・メッセージを含まず、応答と失敗の理由は変わらない。種の確かめも3種類の DB で成り立つ。R-02・R-04 は依頼者の決定で Build and Test に回るため、Critical・Major は無く READY とする。

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | backend/src/main/java/cherry/mastersmith/businessdata/service/BusinessDataWriteService.java > referenceStep（約 312〜315 行・355 行）と code-summary.md 4節 WARN の読み方 | ⑩ の想定外の失敗（INVALID_VALUE・UNEXPECTED）は、ラムダの中で BusinessDataUnexpectedException を握りつぶして空にし、口の外で orElseThrow して 500 にするだけで、診断の WARN（purpose=REFERENCE・stage・reason）を1行も出さない。⑪ の想定外は warn(..."UNEXPECTED"...) を出すのと非対称で、運用で 500 の原因の段（stage）と SQLState を追えない。 | 想定外の ⑩ でも値を含まない診断の WARN を1行（purpose=REFERENCE・stage・reason=UNEXPECTED・sqlState・vendorCode・exceptionClass）出すか、出さない判断を設計の差として明記して Observability Setup で受け止める。 | New |
| R-02 | Minor | backend/src/test/java/cherry/mastersmith/businessdata/web/ReferenceWriteConnectionUsageIT.java > start（80 行の対象は POSTGRESQL・MYSQL のみ）・Usage（読み取りと書き込みの借りた数のみ） | 接続の数の確かめは MariaDB を含まず、内部DB の接続の数は数えない（内部DB の使用中 0 は AbstractReferenceWriteIT の T-G1・T-G2 の止めた時点だけ）。team.md の (j) は対象DB と内部DB の両方の数を ConnectionUsageIT と同じ形で確かめるとしている。計画との差 1 は依頼者が受け入れ済みだが、MariaDB が抜けた点と、内部DB の借りた回数がどのテストにも無い点は差の記録に書かれていない。 | 差の記録に MariaDB を含めない理由と内部DB の借りた回数を数えない理由を足すか、MariaDB と内部DB の借りた回数を数える行を足す。 | New |
| R-03 | Minor | perf/biz/make-biz-seed.sh > COUNTS（299〜302 行）と perf/README.md の U2 の節 | 参照元 biz_refcreate が 0 行であること（基盤の設計 R-03）は COUNTS の出力に1行足しただけで、0 以外なら失敗にする確かめは無い。人が出力を読むことに頼るため、前の流しの残りの行があっても気づかれずに p95 の基準が悪い側または良い側へずれうる。 | 種の後に 0 行を assert する（0 でなければ非 0 で終わる）か、README の手順に「0 でなければ作り直す」を明記して Build and Test の記録の項目にする。 | New |
| R-04 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/code-generation/traceability.json > NFR5.21・NFR5.22・NFR9.21 の target | OK の target が backend/build.gradle.kts と backend/gradle.lockfile で、どちらもこの単位で変更していないファイル。「触れていない」ことの裏付けにはなるが、テストでも検査でもなく、NFR5.21 のカバレッジの値は note にも無い（Build and Test に回すとしている）。 | NFR5.21 は Build and Test が値を記録するまで Deferred にするか、note に実測（businessdata.domain 行 98.2%・分岐 91.5%、service 行 95.5%・分岐 88.8%）を書く。 | New |

> R-01 Not re-checked this round

> R-02 Not re-checked this round

> R-03 Not re-checked this round

> R-04 Not re-checked this round
