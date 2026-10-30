<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->


- 2026-10-30T00:00:00Z — 伏せ字・入口・検査の要件を上流の NFR8（運用）に、仕組みのテストを NFR5 に、版と依存を NFR9 に、C7 の互換を NFR7 に、終わりのコードの固定を NFR6（code を変えない）に寄せた。NFR1〜NFR4 は業務データの API・DB・画面の要件のため N/A。
<!-- aidlc-wave-memory:u4-perf-evidence-redactor:87d4e1e3a69d40d6f66e5cf84e0d62c0aff1bc9e8bdd918b24d45b42ecbafab8 -->


- 2026-10-30T00:00:00Z — 入力の上限 64 MiB を evidence の検査にも当て、上限を超えるファイルは黙って飛ばさず失敗にした（飛ばすと穴になるため）。機能設計に無い扱いで、security-requirements.md 4節に書いた。
<!-- aidlc-wave-memory:u4-perf-evidence-redactor:cc150de55a7375363159e064030968426e56c4745f997b8ae7abfae96a311328 -->


- 2026-10-30T00:00:00Z — 要件に監視の NFR が無く、NFR8（運用）はコンテナと JWT の要件のため、監視の要件をログは NFR1.15、p95 の区画は NFR2.10、503 とプールは NFR3.8 に寄せ、NFR8 は N/A とした（project.md の「当たる NFR が無い要件は近い NFR に寄せ、寄せたことを書く」）。
<!-- aidlc-wave-memory:u1-reference-read:65a8a999612d67624eae81ec434d2bbb55a01d314026c91c3c516275319ce984 -->


- 2026-10-30T00:00:00Z — k6 の場面は Build and Test で書いて最初の値を記録し、合否の持ち主は Performance Validation（AC3.5.4）と読んだ。この Intent には Performance Validation の段がある。
<!-- aidlc-wave-memory:u1-reference-read:89012469b30285b1af8fa9e983a16025fbcbb3fcdd6b920e84925ea6233374ae -->


- 2026-10-30T00:00:00Z — packaging の単位のため functional-spec.md・rules.md は無く、要件 FR6・FR7・NFR5・NFR8・NFR9 と US4.2・US4.3 を直接の出典にした。セキュリティは NFR8.81〜.90、供給網は NFR9.81〜.82、統合の前の検査は NFR5.81 に寄せた。
<!-- aidlc-wave-memory:u5-container-perf-docs:b7e766b3edda90422ee3df4f7dad92dbe3cdc38bed3f1b0078b9505994319e4f -->


- 2026-10-30T00:00:00Z — `k6 inspect --include-system-env-vars` はシェルの環境変数を出力に含むため、資格情報を k6 に渡さず、出力は外に置き記録には差の有無だけを書く要件（NFR8.87）にした。
<!-- aidlc-wave-memory:u5-container-perf-docs:cf20279417dd52ecfcec3a6f8a832d0ea9071cefe14eb373fd4f4dfd8ce1a44e -->


- 2026-10-30T00:00:00Z — 保存前の確かめつきの作成の k6 の場面は、必須の参照項目（NOT NULL、参照先ちょうど 200 行）を1つ持つ専用の参照元の表への作成とし、countState と check の両方を毎回通すいちばん重い道を測る形と読んだ。更新の道は読む量が少ないため場面を置かず、結合テストで接続と時間の上限を確かめる。
<!-- aidlc-wave-memory:u2-reference-write-check:88d6bd56c8deee06ddc49d88e7e038b26bb4c1b21dcbade4456db14bea7a4c42 -->


- 2026-10-30T00:00:00Z — 全体の上限 10 秒の締め切りは、U1 と同じく ⑩ の最初の読み取りの前に決め、接続を借りる待ちは含めない（最悪約 11 秒＋前の待ち）と読んだ。
<!-- aidlc-wave-memory:u2-reference-write-check:2cd382bdca349cfd2fdefaa861805e7d57f54e20046b9935e0aa312a42828763 -->


- 2026-10-30T00:00:00Z — ui の単位のため成果物は performance・security・tech-stack・traceability の4つ。NFR1・NFR4〜NFR7・NFR9 を security-requirements.md に節で分けて書き、NFR3（接続）と NFR8（運用）は N/A とした。枝番は .41 から（NFR1.41〜1.47・2.41〜2.45・4.41〜4.45・5.41〜5.43・6.41〜6.42・7.41・9.41、計 29）。
<!-- aidlc-wave-memory:u3-reference-screens:932cc04f0f5a8934ddaa46834f94cb844c553d2dbceae885a8360c334063e465 -->


- 2026-10-30T00:00:00Z — Q1: A により画面の側の時間の目標は置かず、NFR2.41 は「検索中」→結果の切り替えを画面のテストで確かめる要件にした。
<!-- aidlc-wave-memory:u3-reference-screens:ebaae2a89bc6976b84add7af056c98954465773e024e7ec88be279b780370b8b -->

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->


- 2026-10-30T00:00:00Z — 機能設計の段の承認で依頼者が「直してほしい」とした点に従い、承認済みの functional-spec.md の 6節 3項と 10節、rules.md の BR6.2（文言と要約の行）に、標準出力・ログの JWT の形でない秘密の穴と台本の決まりを書き足した。
<!-- aidlc-wave-memory:u4-perf-evidence-redactor:56b013dc844d18b792e9b680bb08eac3aaf5b79a0406a7186b70d5f158e5f04a -->


- 2026-10-30T00:00:00Z — 「決まっていること」は値の長さが MAX_VALUE_LENGTH（10,000 文字）に従うとするが、コードを読むとそれは書き込みの側の上限で、読み取りでは valueColumn を切らずに読む。見積もり（200 × 10,000 文字）はそのまま書き、10,000 文字を超える値の扱いを NFR 設計の未決として scalability-requirements.md の補足に書いた。
<!-- aidlc-wave-memory:u1-reference-read:1d2b731ecc5a0cf29c490df5839676f6b9c7cf5857480e2f5b7675afbc3755f9 -->


- 2026-10-30T00:00:00Z — 依頼の文の `perf/thresholds-test.sh` は実際には `perf/k6/thresholds-test.sh` にあり、`./gradlew verify` に入っていないため、整理の後に手で流して記録する要件（NFR8.88）にした。
<!-- aidlc-wave-memory:u5-container-perf-docs:d215da2bef991d6083a255b988d7270707e59c61a22dfadcdc65b0fe37c00f93 -->


- 2026-10-30T00:00:00Z — 機能設計の traceability.json の AC3.5.1〜AC3.5.4 の行の追加は、まずシェルの JSON の書き換えで行い、その後に Edit で触り直した（依頼の「Write・Edit だけ」との差。中身は同じ）。センサーは通過。
<!-- aidlc-wave-memory:u2-reference-write-check:93b857c99c53b390ae756da5d04c33e4a6192a87352217d44abaf0511b8fcc5b -->


- 2026-10-30T01:00:00Z — NFR 要件の承認の場の Request Changes（依頼者の決定: 指摘の示した直しを採る）で、NFR5.62・NFR5.63 にテストの置き場と vitest.config.ts の include・coverage.include の扱いをコード生成の計画で明記することと Build and Test での passed の件数の記録を、NFR8.68 に NUL で飛ばしたファイルのパスの表示とテキストの拡張子（.json・.md・.txt・.log・.tsv）なら失敗にすることを足した。後者は機能設計の BR4.2 を狭める変更で、機能設計の文書は書き換えていない。
<!-- aidlc-wave-memory:u4-perf-evidence-redactor:e6e9f0be1e85f8dca88925bd10a00391ff122bcd2d025c1fd70ae3ae84fe3bed -->


- 2026-10-30T00:20:00Z — 承認の場の Request Changes（依頼者の決定: 各指摘の提案どおり）で直した: 読み取り側の値の長さの上限（10,001 文字に切って読み、10,000 文字を超える行を選択肢・ピッカーから外し check で無いとする。F1: A）を NFR2.8 の要件にし先送りをやめた（機能設計 BR7.3 との差を補足に記録）、perf の読み取りのプールを前の Intent と同じ 5 に固定し MySQL・MariaDB の部分一致が線を超えうることと緩めずに諮ることを NFR2.3・NFR3.3 に書いた、NFR1.1 の 403 の利用者にラベルのカラム NONE・使える検索の条件なし・valueColumn がバイナリか password を足した、NFR3.5 に時間切れの最悪約 11 秒と k6 の p95 の対象外を書いた。
<!-- aidlc-wave-memory:u1-reference-read:cb8a0d412691260de03c22ab3565e06d740b40474f602239e36bb52b1324ebb5 -->


- 2026-10-30T00:30:00Z — 承認の場の Request Changes（依頼者の決定: 各指摘の提案どおり）で R-01〜R-05 を直した。新しい ID は足さず、既存の NFR1.22・NFR1.29・NFR2.22・NFR1.32 と「測らない範囲」「後の段に回すこと」を直した。ログの出し場所は、対象DB の例外を結果の型に変える境界の1か所に決めた（NFR1.32 の持ち主に NFR 設計を足した）。
<!-- aidlc-wave-memory:u2-reference-write-check:38cb994c743cc5fc873707d715ff68e960d8fb730201f662dd5dbc3f25a4bab8 -->


- 2026-10-30T00:30:00Z — 承認の場の Request Changes（依頼者の決定: 指摘ごとの提案どおりに直す）で、k6 inspect の流し方を K6_IMAGE の docker run（--env-file なし）に固定し閾値と場面の定義だけを比べる形（NFR8.87）、dsl-mixed-memory.sh と README の線の値の前後の差（NFR8.88）、作り直しの前後の docker image ls（NFR8.83）、入れ替えの前のコンテナの確かめと停止・lgtm のボリュームの引き継ぎの確かめと 0.34.0 への戻し（NFR8.82）、README の整理の後の検索（NFR8.89）、アプリが止まっていたときは起動して確かめる既定（NFR8.84）を足した。ID は増やしていない。
<!-- aidlc-wave-memory:u5-container-perf-docs:9208f4d66014389662295de30feaccdaed86940cf63013fde72a16cdfb2920f1 -->


- 2026-10-30T01:00:00Z — 承認の場の Request Changes（依頼者の決定: 各指摘の提案どおり）で直した。NFR5.43 を D39 に合わせ（共有の見本の DSL に参照の表を足す・E2E 180 の全件の通過・本数に数えない・初期管理者と見本の行は変えない）、NFR2.44 に業務データの画面の chunk の測り方（manifest.json の `src/features/tables/` の項目と imports を gzip で合計）を足し、NFR4.45 に読み上げの決めの後で NFR4.44 の状態の一覧を確かめ直すことを足した。枝番は変えていない。
<!-- aidlc-wave-memory:u3-reference-screens:855c2d4ac651458daeb752ca1acab2ab34b7a91fb912f416d9efd1b7d1104782 -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->


- 2026-10-30T00:00:00Z — vitest の coverage の include は src/ だけで、perf/ の道具のファイルは今の形では計測されない。下限と除外は下げず、入れ方は ADR-006 の切り替え先とあわせてコード生成で決める（NFR5.63）。
<!-- aidlc-wave-memory:u4-perf-evidence-redactor:d87c64a2e6b0ab667363d1f63d2a589e92a6e0acf004565efb2830faa4d22974 -->


- 2026-10-30T00:00:00Z — プールの上限を下げた k6 の場面を置かず、接続の数は *ConnectionUsageIT で決定的に確かめる形にした（NFR3.3）。負荷の下での借りる待ちの長さは測らない代わりに、見積もりの誤りを決定的に見分けられる。
<!-- aidlc-wave-memory:u1-reference-read:6bfa05ccc328389b13100e38ca33c2046017a024cf103a2ffa6eff5651879300 -->


- 2026-10-30T00:00:00Z — 部分一致の線 3,000 ms は http.server.requests のバケットの境界に無いため、サーバーの側の p95 は参考とし、合否は k6 で判定する形にした（バケットを足すと系列が増える）。
<!-- aidlc-wave-memory:u1-reference-read:c0145efc173bfb22a51bbea59da19ddc893deed2cd5cf56d26427c390443f69d -->


- 2026-10-30T00:00:00Z — 確かめは参照ごとに順に行い前の結果を持ち続けない前提で、1要求の確かめのメモリを約 4 MB と見積もった。値の違う check を束ねる形を NFR 設計で選ぶと、1文で読む行が増えて見積もりが変わる。
<!-- aidlc-wave-memory:u2-reference-write-check:fcc6913c0ff3d269d8e90a2d7e8320ab201f3d4bde45255bbe70ceb021a22b29 -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->

- 2026-10-30T00:00:00Z — 10,000 文字を超える値（アプリの外で入れた行）を valueColumn に持つ参照先の読み取りの扱い（読むまま・落とす・NO_PERMISSION と同じ）を NFR 設計で決める。
<!-- aidlc-wave-memory:u1-reference-read:47ad8954efac7f7e50cc6700fc91e6c6185494e3e624f634f1de1b5472d81c20 -->

- 2026-10-30T00:00:00Z — 定義の全体の上限 10 秒を定数にするか設定にするか、longest() に REFERENCE を入れる形を NFR 設計で確かめる。
<!-- aidlc-wave-memory:u1-reference-read:958ffb610f331fc5a0bbc694fe51d9f2c5a8d99694397180240cacf6bbb0a21d -->

- 2026-10-30T00:00:00Z — 外部キーの無い参照先では確かめと保存の間のすき間を守るものが無い。既知の制約として受け入れる形で書いたため、承認の場で確かめたい。
<!-- aidlc-wave-memory:u2-reference-write-check:2dd77e552fbac5f8e6d22a265d8c0a02eda5d4df2cebbb125cd4db2897733d2e -->

- 2026-10-30T00:00:00Z — NFR 設計に回した論点: 作成の画面を開いた直後の D13 の誤りの role="alert" の読み上げ（NFR4.45）、ラベルの1件読みの同時の数、200 件の radio の描画、検索の「時間がかかっています」の知らせ（NFR2.42・NFR2.43）。
<!-- aidlc-wave-memory:u3-reference-screens:dbbf099b230dfe566d98bdf3cdf16bab9d72906ed600f1e39bc3068cf768b63b -->
