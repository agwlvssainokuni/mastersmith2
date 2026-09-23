<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-23T07:30:00Z — 単位の分割が無い bugfix のため、VM の作り直し・F3 の内訳の測定・k6 の試験は Build and Test、.env の変更は Deployment Execution に回し、この段は設定・確かめのスクリプト・文書に限った; 今の VM（2GiB）で配備したアプリを止めずに確かめられる範囲にするため。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-23T07:30:00Z — レビューの依頼の後にレビュー役が git diff を実行し、作業フォルダが変わったと判定されて結果を記録できなかった。確認の回数も尽きたため、依頼者の Request Changes（traceability.json の R-01・R-02 の修正）を経て、git・ビルドを触らない指示で再レビューした; 再レビューでは git・gradlew・docker build を使わず Read だけで確かめるよう指示する。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-23T07:30:00Z — JVM の設定の口に JAVA_TOOL_OPTIONS ではなく独自の MASTERSMITH_JAVA_OPTIONS を ENTRYPOINT の既定の引数の後ろに置く形を選んだ; 標準の変数はコマンド行の 75% に負け、Picked up の1行で JSON のログを崩すため。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-23T07:30:00Z — mastersmith:pre-fix のタグのイメージは確かめ用に残している。不要になったら消す。
