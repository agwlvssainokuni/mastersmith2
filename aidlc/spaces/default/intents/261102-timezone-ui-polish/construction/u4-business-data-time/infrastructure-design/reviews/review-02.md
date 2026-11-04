## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-04
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/infrastructure-design/cicd-pipeline.md > 3.3 スモークテスト S1〜S5 | S1〜S5 は「時点を表す型の列を持つ表」で読み書きするとあるが、配備したアプリの見本の対象DB のどの表か、適用中の DSL にその表が含まれるか、書き込みのアカウントと権限が揃っているかが書かれていない。見本の `docker/targetdb/postgres/01-sample-schema.sql` で時点を表す型を持つのは `sales.customers.created_at` だけで、この表を DSL に載せていなければ S1〜S5 は成り立たない。また S3・S4 の作成・更新は共有の見本の対象DB に行を残すが、後始末の手順がない | S1〜S5 が使う表（例: `sales.customers`）、DSL に載っているかの確認、書き込みのアカウントの有無を前提として書く。作った行の後始末（または残してよい旨）を手順に足す。載っていなければ、Deployment Execution で使い捨ての表と DSL を用意する手順に切り替える | New |
| R-02 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/infrastructure-design/cicd-pipeline.md > 3.1 手順 1 | WAR の新しさの確かめが「`ReferenceFieldResponse` のクラスの日時が新しい」という弱い見分け方になっている。WAR は `verify` の `bootWar` で作られ、Dockerfile は手元の WAR を写すだけのため、古い WAR を入れる事故は日時では確実に防げない | 配備するコミットの `verify` が作った WAR であることを SHA-256 の記録で対応づけ、中身は `labelValueType` の文字列がクラスや `static/assets/` の JS に入っていることを確かめる、という内容の確かめに変える | New |
| R-03 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/infrastructure-design/infrastructure-specification.md > Shared Infrastructure と cicd-pipeline.md > 4 H2 | 「共有の DSL は変えない」と書く一方、流れ 200 は専用の DSL を適用する。適用中の DSL はアプリ全体の状態なので、200 の後に流れるファイルは前提が変わる。H2 の「表を足した後も 180・185・190・195 が通る」は、200 が最後に流れる順だと実質的に確かめにならない（表の追加は DSL が表を明示列挙しているため既存の流れに影響しないことは `businessDataDsl.ts` で確認できた）。 | 200 が適用中の DSL を置き換えること、200 より後の番号を使う流れを足さないこと（足すなら先頭で自分の DSL を適用すること）を明記し、H2 の文を、表の追加の影響の確認は DSL が表を明示していることで済むという内容に直す | New |
| R-04 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/infrastructure-design/traceability.json > NFR5.2 | NFR5.2 を N/A とするが、`team.md` は CI の機械が UTC で手元が `Asia/Tokyo` という差を前提に、画面のテストと性質ベースのテストが時差を固定することを求めている。検査の関門の表（cicd-pipeline.md 2 節）にこの確かめの言及がなく、CI だけで落ちる危険の扱いが見えない | 2 節の単体テストの行に、時差を固定し CI の UTC でも同じ結果になること（Vitest の `TZ` 非依存）を確かめる旨を足し、NFR5.2 の target をそこに向ける | New |
| R-05 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/infrastructure-design/cicd-pipeline.md > 1 節 E2E の行と 4 節 H2 | 実物と矛盾はない（`e2eTest` が `businessDataSchema.sql` を読んで `GRANT ... ON ALL TABLES` を流すので `e2e_event` に権限が付き、片付けもスキーマごと消えること、`timezoneId` が `Asia/Tokyo` で固定されていることを確認した）。ただし、表の追加は `-1`（単一トランザクション）の準備の SQL に入るため、SQL の誤りは全 E2E を止める。この点が引き継ぎにない | H2 に、`e2e_event` の SQL が PostgreSQL で通ることを、200 より前に準備だけ（または 180 を単独）で確かめる手順を足す | New |

> R-01 Not re-checked this round

> R-02 Not re-checked this round

> R-03 Not re-checked this round

> R-04 Not re-checked this round

> R-05 Not re-checked this round

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| sensors | 実行していない（Read だけの制約） | 実物の突き合わせ（SQL・書き込みのアカウント・E2E のコメント・DSL）で代えた |

### Summary

前の指摘 5 件はすべて解け、直しで機能設計・NFR 設計との新しい食い違いは見当たりません。スモークテストの前提も実物と合うため READY です。残る不確かさは、配備したアプリの DSL に `sales.customers` が載っているかで、これは Deployment Execution で確かめる前提として書かれています。
