<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-04T00:52:14Z — 既存の知識ベースが STALE（前回の Intent 261003-user-admin-followup の後に 20 パスが変更）のため再利用の選択肢は出さず、依頼者は Full rescan を選んだ。スナップショットは ./ 全体（source git:641a651a…、develop の HEAD は 47ec27b）。深さ Minimal のため、深く読む範囲は今回の Intent（初期管理者の作成と救済・監査、ログインの停止の判定と性能、利用者の管理の BUSY と traceId、Tomcat の ERROR、言語の欄のフォーカス、対象DB のイメージの固定先と Dockerfile、team.md の記述）に関わる部品に絞り、実際に深く読んだものだけを analyzed に記録する方針（前回までと同じ）。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-04T01:09:04Z — 依頼者は Full rescan を選んだが、深さ Minimal のため深く読んだのは今回の Intent に関わる 42 パス・13 部品だけで、記録上の範囲は kind: partial とし ./ を analyzed.paths に入れなかった（前回までと同じ扱い）。比べた結果は NARROWER で、前回の深い範囲のうち利用者の管理の画面・E2E・invitation・mail・perf/README・警報の決まりの 16 パスは流し読みに下がった。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-04T01:09:04Z — 部品 ID に common-persistence を足して 39 個にした（BUSY の L4 のログ RowLockFailures の持ち主で、6つの部品から使われる）。開発担当の記録から、common-observability を analyzed.components から外し config を入れた（読んだのが logback-spring.xml だけのため）。アーキテクトは記録するコミットの確かめに読み取りだけの git rev-parse HEAD を1回実行した（指示では git を使わないとしていた）。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
