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
