## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-10-30T04:46:51Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/nfr-design/reliability-design.md > 1.2 と「上流との差」・observability-design.md > 2.1 と「上流との差」・logical-components.md > 上流との差 | U2 の ⑩ は U1 に依存する 2 つの変更を前提にしている。(1) 締め切りの部品を `ReferenceLookup` の外から呼べる `businessdata.domain` の純粋な値にすること。(2) `check`・`countState` の `FAILED` に `sqlState`・`vendorCode`・`exceptionClass` を持たせること。承認済みの U1 の設計は逆で、`logical-components.md` 1節は締め切りを `ReferenceLookup` の中に置き、`reliability-design.md` 2節末は `FAILED` を理由だけにしている。差は U2 側の表にしか書かれておらず、U1 側にも契約にも拘束する記録がない。U1 のコード生成が U1 の文書どおりに進むと、U2 は締め切りを共有できず、診断の WARN に `sqlState` などを出せない。observability-design.md 2.1 は「持たせないときは出せない」と退路を書くだけで、NFR1.32 の項目を満たせなくなる。境界テストは緩まない（`BusinessDataBoundaryArchitectureTest` は `DO_NOT_INCLUDE_TESTS` で、`businessdata.domain` から `targetdb.domain` への依存は既存の許可の範囲）ため、緩和の懸念はない。 | U1 のコード生成の計画に、(1) と (2) を受け入れる項目として渡す記録を残す（U1 の承認済みの文書は書き換えず、差を U1 の成果物か Intent の決定の記録に書く）。U2 の「上流との差」に、U1 が受けない場合に U2 が取る手（締め切りを U2 の中に別に置く・`sqlState` などを出さず `reason` だけを出し NFR1.32 のキーを緩める）を依頼者の承認つきで書く。 | New |
| R-02 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/nfr-design/observability-design.md > 2.2・traceability.json > NFR1.32 | NFR1.32 は「同じ失敗について WARN の行がちょうど1行」と書き、重なるなら1行になる形を NFR 設計で決めるとしていた。設計は決めずに、診断の WARN（`BusinessDataWriteService` のロガー）だけを1行と数え直し、`GlobalExceptionHandler` の変換の WARN（`handleBusiness` の `log`）は別の種類として残した。実コードでは `GlobalExceptionHandler` が全 `BusinessException` に WARN を出すため、全ロガーの合計では 2 行になる。既存の書き込みの 503 も同じ 2 行で、`BusinessDataWriteSecretLeakIT` もロガーで絞って数えているため、読み替えは既存の実態と整合し、全機能共有の部品を変えない理由も妥当。ただし要件の文言とは違い、判断は承認の場に委ねられている。traceability.json は NFR1.32 を差のない `OK` にしている。 | 承認の場で、読み替えを受け入れるかを依頼者に明示して尋ねる。受け入れるなら NFR1.32 の確かめ方の文言を「診断の WARN がちょうど1行」と記録する。traceability.json の NFR1.32 の target に、読み替えを含む旨を書く。 | New |
| R-03 | Minor | aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/nfr-design/reliability-design.md > 4.1・4.2・4.4 | 差し込み口の設計は、コードと整合している。`JdbcTargetWriteAccess` は `@Service` で Bean 名 `jdbcTargetWriteAccess`、注入は interface 型、`product()` は包む側が任せられる。`withWrite` は `BusinessDataWriteService` の ⑨ の後の 1 か所だけで呼ばれる。`CountingDataSourceConfig` と同じ static な `BeanPostProcessor` の形が前例にある。本番のクラス・公開の口・境界テストの許可の一覧は変わらない。ただし詰めが足りない。(a) 待ちの上限は「例 20 秒」で、超えたときに要求を進めるのか、テストを失敗させて解放するのかが書かれていない。(b) 「合図は1回の要求だけに効かせる」の仕組み（一度きりの合図・テストごとの解除）が書かれていない。(c) 読み取りの接続が返っていること（使用中 0・借りた回数 1）は 4.2 の文章にだけあり、4.4 の T-G1・T-G2 の期待に入っていない。 | 4.1 に、上限切れはテストの失敗にして必ず解放すること、合図を一度で消費してテストごとに戻すことを書く。4.4 の T-G1・T-G2 の期待に、止めた時点の読み取りの使用中 0・借りた回数 1 の確かめを足す。 | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| (stage に検証ツールの指定なし。読み取りだけの照合) | 照合済み | R-06 への対応は、コードに照らして成立する。`BusinessDataWriteService` の `withWrite` は ⑨ の後の 1 回だけ。⑩ を `withRead` の中で終えて接続を返してから `withWrite` を呼ぶ順は、作成でも更新でも `withWrite` の入口が「確かめ・判定の後で書き込みの接続を借りる前」に当たる。包む対象を `ReferenceLookup` から `TargetWriteAccess` に変えたため、更新の「変わらない」の経路でも止められる。`src/test` の部品のため、ArchUnit（`ArchitectureTest`・`BusinessDataBoundaryArchitectureTest` とも `DO_NOT_INCLUDE_TESTS`）の許可の一覧、SpotBugs の除外、本番のクラスは変わらない。(j)(k) は満たす設計。(g) は ⑩ が写しの DSL から参照先を引き、`NOT_ALLOWED` で足した名前を先に拒否するため満たす。行の値・JDBC のメッセージは、型の `toString` の伏せと `sqlState` などだけを出すログの設計で守られる。 |

### Summary

U2 の NFR 設計は、R-06 の対応（`TargetWriteAccess` の Bean を包む差し込み口）を含め、コードに照らして実装できる水準にある。残る論点は、U1 に頼る 2 つの変更が U2 側にしか記録されていないこと、NFR1.32 の読み替えの承認、差し込み口の細部の 3 点で、いずれも承認の場で扱える。
