<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-27T02:48:18Z — 質問は応答時間の目標の1問だけにし、ほかは NFR の要点（案）13 件として要約で確かめる形にした; キャッシュ（BR3.6）・値の検証とログ（BR1・BR2.1）は承認済みの機能設計で決まっており、回数の制限は既存の公開の /api/problems と前の Intent のログインの判断（設けない）の前例で決まると読んだ。NFR6 は U8 の API の値を決めていないため、数値の目標だけを新しく決める論点とした。
- 2026-09-27T02:48:18Z — HEAD は公開に足さず、未認証の HEAD は既存の 401 のままとする案にした; 公開の決まりをメソッド GET に限って足すため HEAD は当たらない見込みで、画面は GET しか使わない。実際の扱いはサーバー側のテストで確かめ、違えばコード生成で記録する。
- 2026-09-27T02:48:18Z — U4 の W2（見た目の設定の答えが出るまで描かず、待ちに上限なし）を U8 の信頼性の要件の根拠にした; U8 の応答が内部DB の接続を借りないこと（プールが尽きたときや詰め直しの一時停止でも待たされない）を要点 2 に置き、ArchUnit で確かめる案にした。

- 2026-09-27T03:11:02Z — 枝番はこの単位の中で .1 から振った; 同じ Intent の U2 の成果物が単位ごとに .1 から振っているのに合わせた（前の Intent の dsl-schema-loader は単位の間で通しで振っていた）。各成果物の冒頭に「枝番はこの単位の中で振る」と書いた。
- 2026-09-27T03:11:02Z — 要件に拡張性・信頼性・観測性の NFR が無いため、同時の要求と指標・トレースを NFR6、起動・応答・警告のログ・監査・健全性を NFR9 の枝番に寄せた; 前の Intent の U4 が同時の数と指標を NFR1（性能）に寄せた前例と同じ考え方。寄せ先は各成果物の冒頭と traceability.json の target に書いた。
- 2026-09-27T03:11:02Z — 応答に元の文字列を載せない決まり（BR3.4）は NFR4.5、警告のログに設定された値を出さない決まり（BR2.1）は NFR9.4 に置いた; 公開の API が出してよい範囲は認可（公開の範囲）と一緒に確かめ、ログの中身はログの形と一緒に確かめるため。NFR2（個人情報とメール）は U8 が扱わないため N/A とし、理由に NFR4.5・NFR9.4 を示した。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-27T03:11:02Z — 内部DB に触れないことを構造の検査（NFR5.1）に加え、接続を借りた回数が増えない結合テスト（NFR5.2）でも確かめる形にした; U4 の W2 で U8 の応答が全画面の最初の描画を待たせるため、認証の段階を含めて確かめる。測り方（HikariCP の指標など）の具体はコード生成で決める余地を残した。
- 2026-09-27T03:11:02Z — 回数の制限を設けない判断（NFR4.6）を、受け入れる危険（大量の要求でスレッドが占められる）と見直しの時点（配備先が決まったとき）つきで記録した; 設けないことを確かめるテストは無いため、記録だけにした。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-27T02:48:18Z — 観測性の要件に当てる要件の NFR の ID が無い; 起動時の警告のログ（BR2.1）や指標は FR8.2 由来で、NFR1〜NFR11 に合う ID が無い。成果物の段で、NFR9 などに寄せるか、FR8.2 を出典とした注記にするかを決める。
