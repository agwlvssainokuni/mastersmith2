## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-10-30T07:44:04Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/nfr-design/reliability-design.md > 1.2 と「上流との差」・observability-design.md > 2.1 と「上流との差」・logical-components.md > 上流との差 | U2 の ⑩ は U1 に依存する 2 つの変更を前提にしている。(1) 締め切りの部品を `ReferenceLookup` の外から呼べる `businessdata.domain` の純粋な値にすること。(2) `check`・`countState` の `FAILED` に `sqlState`・`vendorCode`・`exceptionClass` を持たせること。承認済みの U1 の設計は逆で、`logical-components.md` 1節は締め切りを `ReferenceLookup` の中に置き、`reliability-design.md` 2節末は `FAILED` を理由だけにしている。差は U2 側の表にしか書かれておらず、U1 側にも契約にも拘束する記録がない。U1 のコード生成が U1 の文書どおりに進むと、U2 は締め切りを共有できず、診断の WARN に `sqlState` などを出せない。observability-design.md 2.1 は「持たせないときは出せない」と退路を書くだけで、NFR1.32 の項目を満たせなくなる。境界テストは緩まない（`BusinessDataBoundaryArchitectureTest` は `DO_NOT_INCLUDE_TESTS` で、`businessdata.domain` から `targetdb.domain` への依存は既存の許可の範囲）ため、緩和の懸念はない。 | U1 のコード生成の計画に、(1) と (2) を受け入れる項目として渡す記録を残す（U1 の承認済みの文書は書き換えず、差を U1 の成果物か Intent の決定の記録に書く）。U2 の「上流との差」に、U1 が受けない場合に U2 が取る手（締め切りを U2 の中に別に置く・`sqlState` などを出さず `reason` だけを出し NFR1.32 のキーを緩める）を依頼者の承認つきで書く。 | Resolved (reviewer) |
| R-02 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/nfr-design/observability-design.md > 2.2・traceability.json > NFR1.32 | NFR1.32 は「同じ失敗について WARN の行がちょうど1行」と書き、重なるなら1行になる形を NFR 設計で決めるとしていた。設計は決めずに、診断の WARN（`BusinessDataWriteService` のロガー）だけを1行と数え直し、`GlobalExceptionHandler` の変換の WARN（`handleBusiness` の `log`）は別の種類として残した。実コードでは `GlobalExceptionHandler` が全 `BusinessException` に WARN を出すため、全ロガーの合計では 2 行になる。既存の書き込みの 503 も同じ 2 行で、`BusinessDataWriteSecretLeakIT` もロガーで絞って数えているため、読み替えは既存の実態と整合し、全機能共有の部品を変えない理由も妥当。ただし要件の文言とは違い、判断は承認の場に委ねられている。traceability.json は NFR1.32 を差のない `OK` にしている。 | 承認の場で、読み替えを受け入れるかを依頼者に明示して尋ねる。受け入れるなら NFR1.32 の確かめ方の文言を「診断の WARN がちょうど1行」と記録する。traceability.json の NFR1.32 の target に、読み替えを含む旨を書く。 | Resolved (reviewer) |
| R-03 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/nfr-design/reliability-design.md > 4.1・4.2・4.4 | 差し込み口の設計は、コードと整合している。`JdbcTargetWriteAccess` は `@Service` で Bean 名 `jdbcTargetWriteAccess`、注入は interface 型、`product()` は包む側が任せられる。`withWrite` は `BusinessDataWriteService` の ⑨ の後の 1 か所だけで呼ばれる。`CountingDataSourceConfig` と同じ static な `BeanPostProcessor` の形が前例にある。本番のクラス・公開の口・境界テストの許可の一覧は変わらない。ただし詰めが足りない。(a) 待ちの上限は「例 20 秒」で、超えたときに要求を進めるのか、テストを失敗させて解放するのかが書かれていない。(b) 「合図は1回の要求だけに効かせる」の仕組み（一度きりの合図・テストごとの解除）が書かれていない。(c) 読み取りの接続が返っていること（使用中 0・借りた回数 1）は 4.2 の文章にだけあり、4.4 の T-G1・T-G2 の期待に入っていない。 | 4.1 に、上限切れはテストの失敗にして必ず解放すること、合図を一度で消費してテストごとに戻すことを書く。4.4 の T-G1・T-G2 の期待に、止めた時点の読み取りの使用中 0・借りた回数 1 の確かめを足す。 | Resolved (reviewer) |
| R-04 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/nfr-design/reliability-design.md > 4.4 T-G2 | T-G2 は「止める前に check の文が流れていない（detail だけ）」と書く。変わらないと判定した参照だけを送る更新の場合に限る前提が表に明記されていない。countState を流す参照が混ざると文の記録の期待が崩れる。 | T-G2 の手順に、送る参照は更新前と同じ値のものだけで他の参照を含まない、と一言足す。 | New |
| R-05 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/nfr-design/reliability-design.md > 1.2 と 6節 T-T2 | ReferenceReadDeadline.narrow の単体テストは U1 の T-T2 に任せ、U2 では重ねない。コード生成で U1 の部品が先に入らない順になると、U2 の T-T3 が参照先を欠く。 | コード生成の計画で、U1 の ReferenceReadDeadline・ReferenceReadFailure と TargetReadAccess.referenceReadTotal() が U2 より先に入る順であることを明記する（U1 の「後の段に回すこと」と同じ依存）。 | New |

> R-01 Reviewer note: U1 の reliability-design.md 5節と logical-components.md 5節に ReferenceReadDeadline（start・narrow・MIN_REMAINING）・ReferenceReadFailure・ReferenceFailureReason・Failed(ReferenceReadFailure)・TargetReadAccess.referenceReadTotal() が記録済みで、U2 の reliability-design.md 1.2・2節、observability-design.md 2.1、logical-components.md の名前と形が一致する。代わりの手も置かれていない。businessdata.domain の依存は targetdb.domain と java.. だけで境界テストも緩まない。

> R-02 Reviewer note: observability-design.md 2.2 と「承認の場の決定」に、診断の WARN は U2 のロガーでちょうど1行、GlobalExceptionHandler の変換の WARN は別の行という読み方が書かれた。traceability.json の NFR1.32 の target にも同じ読み方があり、2.3 の確かめも診断の WARN の1行に揃っている。

> R-03 Reviewer note: reliability-design.md 4.1 に、上限切れはテストを失敗にして必ず放す・一度きりの合図（getAndSet(null)、@AfterEach で外す）が書かれた。4.4 の T-G1・T-G2 に、止めた時点の読み取りの使用中 0・読み取りの借りた回数 1・書き込みの借りた回数 0 が入った。差し込み先の Bean 名 jdbcTargetWriteAccess は JdbcTargetWriteAccess（@Service）の既定名と一致し、CountingDataSourceConfig も BeanPostProcessor 方式で同じ形のため、実現できる。

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| 読み取りの突き合わせ（U1 の 5節、Bean 名、traceability.json） | 継ぎ目の名前・形は一致、Bean 名一致 | R-01 と R-03 の修正が実体と整合する。ビルドや検証ツールは指示により実行していない。 |

### Summary

3件の前回の指摘はいずれも解消され、U1 が受け入れた継ぎ目の名前で U2 が一貫して使っている。残るのは軽微な書き足しだけで、実装に進める。
