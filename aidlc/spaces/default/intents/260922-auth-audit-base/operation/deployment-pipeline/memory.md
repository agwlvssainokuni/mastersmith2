<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-23T02:52:00Z — 配備先が未定のため、本段を「開発者の PC 上のコンテナへの手作業の配備」の記録と整理として扱った。既にある Dockerfile・compose.yaml・README の手順を正とし、決まっていなかった点（WAR の出どころ、戻すときの WAR の入手、版の見分け方、スモークテストの方法）だけを質問した。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-23T02:52:00Z — 承認済みの設計（U1 の cicd-pipeline.md 5章）の「イメージのタグにコミットのハッシュを付ける」と「戻しに CI の成果物・リリースの WAR を使う」を、依頼者の回答（Q3・Q5・Q6）に従って採らなかった。設計の文書は確定済みのため書き換えず、差を cd-config.md 8節に明記し、README の手順を回答に合わせて直した。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-23T02:52:00Z — 手元の WAR（Q2）とタグ local（Q3）の組み合わせでは、戻しの WAR の入手と版の見分け方が決まらないため、追加の質問（Q5・Q6）で埋めた。依頼者は、速さ（タグ付きイメージを残す）より手順の単純さ（手元で作り直す・ハッシュを控えるだけ）を選んだ。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-23T02:52:00Z — README の手順では内部DBのバックアップ（mastersmith-data-<日時>.tgz）がリポジトリの直下にでき、.gitignore に入っていない。メールアドレスと接続元IPを含むため、公開リポジトリへ誤ってコミットしない手当てが要る（依頼者の判断待ち）。
- 2026-09-23T02:52:00Z — 直前の版のアプリが、新しい版の当てたスキーマの変更を含む DB で起動できること（Flyway が未知の新しい変更を既定で無視すること）は未確認。deployment-execution で戻しを一度通して確かめる。
