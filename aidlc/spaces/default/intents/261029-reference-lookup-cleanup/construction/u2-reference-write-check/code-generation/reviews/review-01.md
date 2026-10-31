## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-10-31T00:25:44Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | backend/src/main/java/cherry/mastersmith/businessdata/service/BusinessDataWriteService.java > referenceStep（約 312〜315 行・355 行）と code-summary.md 4節 WARN の読み方 | ⑩ の想定外の失敗（INVALID_VALUE・UNEXPECTED）は、ラムダの中で BusinessDataUnexpectedException を握りつぶして空にし、口の外で orElseThrow して 500 にするだけで、診断の WARN（purpose=REFERENCE・stage・reason）を1行も出さない。⑪ の想定外は warn(..."UNEXPECTED"...) を出すのと非対称で、運用で 500 の原因の段（stage）と SQLState を追えない。 | 想定外の ⑩ でも値を含まない診断の WARN を1行（purpose=REFERENCE・stage・reason=UNEXPECTED・sqlState・vendorCode・exceptionClass）出すか、出さない判断を設計の差として明記して Observability Setup で受け止める。 | New |
| R-02 | Minor | backend/src/test/java/cherry/mastersmith/businessdata/web/ReferenceWriteConnectionUsageIT.java > start（80 行の対象は POSTGRESQL・MYSQL のみ）・Usage（読み取りと書き込みの借りた数のみ） | 接続の数の確かめは MariaDB を含まず、内部DB の接続の数は数えない（内部DB の使用中 0 は AbstractReferenceWriteIT の T-G1・T-G2 の止めた時点だけ）。team.md の (j) は対象DB と内部DB の両方の数を ConnectionUsageIT と同じ形で確かめるとしている。計画との差 1 は依頼者が受け入れ済みだが、MariaDB が抜けた点と、内部DB の借りた回数がどのテストにも無い点は差の記録に書かれていない。 | 差の記録に MariaDB を含めない理由と内部DB の借りた回数を数えない理由を足すか、MariaDB と内部DB の借りた回数を数える行を足す。 | New |
| R-03 | Minor | perf/biz/make-biz-seed.sh > COUNTS（299〜302 行）と perf/README.md の U2 の節 | 参照元 biz_refcreate が 0 行であること（基盤の設計 R-03）は COUNTS の出力に1行足しただけで、0 以外なら失敗にする確かめは無い。人が出力を読むことに頼るため、前の流しの残りの行があっても気づかれずに p95 の基準が悪い側または良い側へずれうる。 | 種の後に 0 行を assert する（0 でなければ非 0 で終わる）か、README の手順に「0 でなければ作り直す」を明記して Build and Test の記録の項目にする。 | New |
| R-04 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/code-generation/traceability.json > NFR5.21・NFR5.22・NFR9.21 の target | OK の target が backend/build.gradle.kts と backend/gradle.lockfile で、どちらもこの単位で変更していないファイル。「触れていない」ことの裏付けにはなるが、テストでも検査でもなく、NFR5.21 のカバレッジの値は note にも無い（Build and Test に回すとしている）。 | NFR5.21 は Build and Test が値を記録するまで Deferred にするか、note に実測（businessdata.domain 行 98.2%・分岐 91.5%、service 行 95.5%・分岐 88.8%）を書く。 | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| 実行（git・gradlew・docker・npm） | 未実行 | 指示により Read だけで確かめた。テストの件数・カバレッジ・CI は code-summary.md の記録（verify 成功 26分48秒・単体 2,804 件・結合 1,652 件・E2E 256 件・CI 1回目 success）を引用したもので、再実行していない。 |
| 接続の順序（⑩ は読み取りを返した後に書き込みを借りる） | 確認 | BusinessDataWriteService.write は ⑩ の withRead が戻ってから withWrite を呼ぶ。内部DB のトランザクションは開かない。拒否の監査（publish）は読み取りの口が戻った後。 |
| 締め切りと失敗の写し | 確認 | createStep・updateStep・check が各読み取りの前に deadline.narrow し、足りなければ ReferenceReadFailure.ofBudgetExceeded() で止める。診断の WARN は warnReference の1か所（値・名前・メッセージなし）。 |
| 更新は変わった参照だけ | 確認 | updateStep が ReferenceWritePolicy.unchanged で変わらない参照を除き、更新前の行が無ければ参照の拒否より先に RowMissing。 |
| 境界テスト・SpotBugs の除外・lockfile | 変更なし | source-manifest.json の 37 パスに ArchUnit のテスト・spotbugs-exclude.xml・lockfile・libs.versions.toml が無い。 |
| テスト専用の PausingWriteAccessConfig | 本番に漏れない | src/test の @TestConfiguration で、BeanPostProcessor が jdbcTargetWriteAccess を包むのは BusinessDataTestApp.start の sources に足したテストだけ。 |
| perf の閾値 | 既存は不変 | thresholds-test.sh は既存 12 場面の行を変えず refCreateChecked|p(95)<1000 を1行足しただけ。表は種の GRANT より前に biz_refcreate を作る（GRANT ON ALL TABLES に入る）。 |

### Summary

保存前の参照の段は、読み取りの接続を返してから書き込みを借りる順序・全体の締め切り・変わった参照だけの確かめ・診断の WARN 1行・拒否の監査の置き場が、設計と計画のとおり実装から読み取れ、既存の境界テストと SpotBugs の除外も動かしていない。残るのは診断・接続の数の確かめ・種の確かめ・追跡の行に関する Minor の4件で、いずれも統合を止める理由にはならない。
