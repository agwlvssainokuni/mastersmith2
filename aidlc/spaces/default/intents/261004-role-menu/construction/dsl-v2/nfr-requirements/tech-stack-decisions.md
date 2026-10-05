# 技術の選択と性能・信頼性の要件 — U2 dsl-v2

## 出典

この単位の機能設計 `functional-spec.md`・`rules.md`、要件 `requirements.md`、契約 `contract-summary.md`（C3）、コード知識ベース `technology-stack.md`、`backend/gradle.lockfile`、前の Intent `260923-dsl-schema-loader` の U3〜U5 の NFR 要件（DSL の時間の目標）、`perf/README.md`、この段の答え `nfr-requirements-questions.md`（Q1: A）。

## ID の振り方と上流の枝番との対応（2つの成果物に共通）

- 上流の枝番（`requirements.md` の NFR1.1〜NFR6.4）と **同じ番号は同じ意味** でだけ使う。上流の要件をこの単位に当てはめたものは上流と同じ ID にする。この単位で新しく足す要件は、上流の最後の枝番の次から振る（NFR1.7〜・NFR2.5〜・NFR3.3〜・NFR5.3〜・NFR6.5〜）。group・role・navigation と同じ形。
- この単位は library のため、性能・拡張性・信頼性・観測の文書は作らない（段の定義の `produces_kinds`）。セキュリティ（NFR1）とログの決まり（NFR5）は `security-requirements.md`、性能（NFR2）・信頼性（NFR3）・画面（NFR4）・テスト（NFR6）は `tech-stack-decisions.md` に書く。1つの要件は1つの成果物にだけ書き、下の表を両方の冒頭に置く。

| 上流の枝番 | この単位での扱い | この単位の ID と置き場 |
|---|---|---|
| NFR1.1 API ごとのサーバー側の判定 | 当たらない（この単位は API を足さず、既存の DSL の API の道と守りも変えない） | — |
| NFR1.2 401・403・200 のテスト | 当たらない（同上。足す・変える API が無い） | — |
| NFR1.3 API の分類の網羅 | 当たらない（既存の DSL の API への印は U1 cross-cutting） | — |
| NFR1.4 昇格・一括代入・IDOR | 当たらない（役割・権限を扱わない） | — |
| NFR1.5 権限の YAML の守り（FR7.4） | 部分（守りの部品 SafeYamlReader と、FR7.4 が同じ守りを求める DSL の守り。上限の値と YAML の中身の検証は U4 role） | NFR1.5、足す NFR1.7〜NFR1.12・NFR1.14（security） |
| NFR1.6 個人に関する値と TRACE | 部分（個人に関する値は扱わない。TRACE・ログに値を出さない決まりの部分） | 足す NFR1.11・NFR1.13（security）・NFR5.3〜NFR5.5（security） |
| NFR2.1 規模の前提 | 部分（100 テーブル × 100 カラムと上限の 10 MiB の DSL を時間と大きさの条件に使う。利用者・ロール・グループの数は U3・U4） | 足す NFR2.5〜NFR2.7・NFR2.9（tech-stack-decisions） |
| NFR2.2 権限の木・メニュー・作業ロールの時間 | 当たらない（U4 role・U5 navigation） | — |
| NFR2.3 要求ごとの権限の読み出しの重さ | 当たらない（U4 role・U5 navigation。この単位の提供口はメモリの中のモデルを返すだけ） | — |
| NFR2.4 import の時間と YAML の大きさの上限 | 当たらない（U4 role。この単位は上限を受け取る口だけ） | — |
| NFR3.1 割り当て・削除などの同時の重なり | 当たらない（U4 role・U3 group） | — |
| NFR3.2 import の一括確定 | 当たらない（U4 role） | — |
| NFR4.1 画面の WCAG | 部分（S10 で変える部分だけ） | NFR4.1（tech-stack-decisions） |
| NFR4.2 N 階層のメニューの画面 | 当たらない（U7 app-frame-ui） | — |
| NFR4.3 日本語と英語 | 当たる（S10 で足す文言） | NFR4.3、足す NFR2.8（tech-stack-decisions） |
| NFR5.1 監査（FR12） | 当たらない（DSL の操作の監査は既存のまま。役割・権限の監査は U3・U4） | — |
| NFR5.2 管理の API の指標 | 当たらない（新しい API が無い。DSL の指標は既存のまま） | — |
| NFR6.1 役割・権限と N 階層のメニューの必須のテスト | 部分（N 階層のメニューの深さの上限の境界だけ） | NFR6.1、足す NFR6.5・NFR6.6（tech-stack-decisions） |
| NFR6.2 継承の解決・メニューを絞る関数の性質ベースのテスト | 当たらない（U4・U5・U7。この単位の性質ベースのテストは足す NFR6.6） | — |
| NFR6.3 E2E は最大2本 | 部分（新しい E2E は足さない。既存の DSL の E2E の版 2 への書き換えだけで、本数に数えない） | 足す NFR6.7（tech-stack-decisions） |
| NFR6.4 カバレッジの下限 | 部分（`dsl`・`dslmanage` のパッケージ。`access.service` は U1） | NFR6.4（tech-stack-decisions） |
| （足す）NFR1.7〜NFR1.14 | この単位で足す（DSL の守り・TRACE・深さ・接続情報・境界） | security |
| （足す）NFR2.5〜NFR2.9 | この単位で足す（DSL の時間・大きさ・英語の文言） | tech-stack-decisions |
| （足す）NFR3.3 | この単位で足す（500 にしない・起動を止めない）。上流の NFR3.1・NFR3.2 は当たらないため、FR7.4 の「拒否は Problem Details で返す」と同じ守りとして NFR1.5 の行に載せる | tech-stack-decisions |
| （足す）NFR5.3〜NFR5.5 | この単位で足す（起動時のログ・照合の警告） | security |
| （足す）NFR6.5〜NFR6.7 | この単位で足す（DSL の必須のテスト・性質ベースのテスト・書き換え） | tech-stack-decisions |

## 1. 技術の選択

新しい依存は足さない。版は `backend/gradle.lockfile`・`technology-stack.md` で確かめた。

| 用途 | 部品と版 | ライセンス | 決定 |
|---|---|---|---|
| YAML の読み込み・書き出し | SnakeYAML 2.7 | Apache 2.0 | そのまま。節の木を作る部品だけを使い、型を作る仕組みは使わない（既存の境界テスト）。上限を定数から引数へ移すだけで、守りの実装は1か所のまま |
| JSON Schema の検証 | networknt json-schema-validator 3.0.6 | Apache 2.0 | そのまま。版 2 の書式も 2020-12 で書き、外部の `$ref` を解決しない今の設定を使う。Jackson を引き上げないため版を上げない（`project.md` の学び） |
| 性質ベースのテスト | jqwik 1.10.1（サーバー）・fast-check（画面。今の版） | EPL 2.0・MIT（既存） | そのまま。メニューの深さの数え方と枝の落とし方に当てる |
| 画面のテスト | Vitest・Testing Library・user-event・vitest-axe（今の版） | 既存 | そのまま |
| 負荷・時間の道具 | `perf/dsl-timing.sh`・`perf/make-large-dsl.mjs`・`perf/make-pattern-dsl.mjs`・`perf/ui/*`・k6 の `perf/k6/scenarios.js` | リポジトリの中 | 版 2 の DSL を作る・送る形に直す（NFR2.5・NFR2.6） |

内部DB の表と Flyway の移行は変えない（3節の決定。枝番を振らない）。

## 2. 性能（NFR2）

前の Intent で決めた DSL の時間の目標を、版 2 でも保つ。目標の値は変えず、緩めて「満たした」ことにしない（`project.md` の学び）。

| ID | 要件 | 条件 | 確かめ方と持ち主の段 | 出典 |
|---|---|---|---|---|
| NFR2.5 | 1回ずつの時間の目標を保つ。生成の全体 30 秒以内、投入・戻し・プレビューの表示（照合を含む）10 秒以内、ダウンロード 2 秒以内、画面の表示 3 秒以内（照合を含む表示は 11 秒以内）、違いの表の行を開いてから 0.5 秒以内、10 MiB のファイルを選んでから送り始めるまで 2 秒以内 | 想定の規模（100 テーブル × 100 カラム）と上限の 10 MiB の DSL。PostgreSQL の1種類 | Build and Test。`perf/dsl-timing.sh`（と `--ui`）を版 2 に直して流し、値を記録する。時間は統合の関門のテストにしない（前の Intent と同じ） | Q1: A・前の Intent の U3 NFR1.6・U4 NFR1.8・NFR1.11・U5 NFR1.18〜NFR1.20 |
| NFR2.6 | 95 パーセンタイルと同時の実行の目標を保つ。今の状態・履歴・破棄・適用の 95% が 1 秒以内、10 MiB の投入とプレビューの表示にログインを重ねても失敗しない | k6 の `dslLight`・`dslCycle`・`dslMixed`（版 2 の DSL） | Performance Validation。測れないときは `Unverified` とし、持ち主の段を明記して引き継ぐ | Q1: A・前の Intent の U4 NFR1.10・NFR1.12 |
| NFR2.7 | 既定の DSL の生成の出力は、上限 10 MiB の内に収める。版 2 では字下げが2段深くなる（1行あたり 4 バイト）ことと、メニューの組の分が増え、想定の規模で約 6.3 MB の試算（前の Intent の約 5.3 MB から） | 想定の規模、コメントが無い対象DB | Build and Test で生成して大きさを記録する（`perf/dsl-timing.sh` の生成の内訳の `bytes`）。上限は変えない。超えたときは生成の失敗（既存の扱い）で、上限を上げるかは依頼者に諮る | `project.md` の学び（上限と最大の出力の突き合わせ）・Q1: A |
| NFR2.8 | 英語の文言が届く（版の誤り・深さの誤り・`SCHEMA_MISMATCH`） | 英語のロケールの画面 | Build and Test で `perf/dsl-timing.sh --lang`（`perf/ui/dsl-ui-lang.mjs` を版 2 に直す） | Q1: A・NFR4.3 |
| NFR2.9 | 深さの判定と起動時の枝の落とし方は、メニューの木を1回たどるだけにする（本文の大きさに比例し、重ねて読み直さない） | — | 単体テストと NFR2.5 の時間で確かめる | BR2.1・BR2.3 |

`--pattern`・`--storage` は版 2 で変わる経路に当たらないため、時間の確かめには使わない。ただし道具は版 2 の DSL を作れるように直す（`make-pattern-dsl.mjs`）。

## 3. 信頼性（NFR3）

上流の NFR3.1（割り当て・削除などの同時の重なり）・NFR3.2（import の一括確定）はこの単位に当たらない。足す NFR3.3 は、FR7.4 の「拒否は Problem Details で返す」と同じ守りとして、`traceability.json` の NFR1.5 の行に載せる。

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR3.3 | 版 1 の DSL・深すぎる DSL・読めないプレビューは、どの入口（投入・復元・プレビューの表示・適用・ダウンロード・今の状態）でも 500 にしない（422 か、本文をそのまま返す）。起動も止めない。適用中の DSL が読めなければ Absent で、深すぎれば枝を落として Present で起動する。内部DB を読めないとき（想定外）だけは今までどおり止める | 結合テスト。拒否の後に内部DB を読み直し、適用中・プレビュー・履歴が要求の前と同じことを確かめる（状態コードだけで合格にしない）。版 1 の適用中の DSL と深さ 6 段の適用中の DSL を内部DB に入れて起動し、ヘルスチェックが UP で提供口が Absent・Present になることを確かめる | BR2.3・BR3.1〜BR3.6・AC5.2.3〜AC5.2.5・AC6.1.6・AC6.1.7 |

決定（枝番を振らない）: 内部DB の表と Flyway の移行は変えない。前の版のアプリは今の表のまま動く（`team.md` の「前進のみ・前の版が動く」、要件 C2）。前の版へ戻すと版 2 の DSL を読めず DSL が無い状態で起動する（AC6.1.8）点は、配備の段（deployment-pipeline・deployment-execution）の戻しの手順で扱う。移行のファイルが増えないことはコード生成の計画で確かめる。

## 4. 画面（NFR4）

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR4.1 | 上流の NFR4.1（足す画面の WCAG）のうち S10 で変える部分。S10 の変更（今の状態の注意・読めないプレビュー・違いの表の見出しの行）は WCAG 2.1 AA に沿い、状態を色だけにしない | 画面部品ごとに vitest-axe を1件（注意あり・読めないプレビュー・見出しの行の状態） | 上流の NFR4.1・`frontend-components.md` の 5節 |
| NFR4.3 | 足す文言は ja・en の対で置く | 文言の鍵の対の検査（既存）と NFR2.8 | 上流の NFR4.3 |

## 5. テスト・品質（NFR6）

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR6.1 | 上流の NFR6.1 のうち、`team.md` の N 階層のメニューの必須のテストの「深さの上限」（上限ちょうどは受け付け、上限を超えると拒否）。深さ 5 は受け付け、6 は拒否 | 投入と復元の結合テスト（境界の2点） | 上流の NFR6.1・TP・AC5.2.1・AC5.2.2 |
| NFR6.4 | 上流の NFR6.4（カバレッジの下限）のうち、この単位のパッケージ。カバレッジの下限（行 80%・分岐 70%）を、`dsl`・`dslmanage` の手を入れるパッケージごとに満たす（どれも `packagesJudgedByTotal` に入っておらず、すでにパッケージごとの下限の対象）。画面も `@vitest/coverage-v8` の下限を満たす | B1 の `./gradlew verify` を `:backend:cleanTest :backend:cleanIntegrationTest` を付けて流し、実測の値を記録する | TP・NFR6.4・`project.md` の学び |
| NFR6.5 | `team.md` の DSL の必須のテスト（大きさ・深さ・別名・タグ・重複キー・`$ref`・拒否の応答）を版 2 の形で満たす | `security-requirements.md` の必須のテストとの対応表のテスト | TP・AC6.1.10 |
| NFR6.6 | 深さの数え方と起動時の枝の落とし方を純粋な関数にし、jqwik の性質ベースのテストを当てる（落とした木は深さ 5 以下、残った項目の親子と並びは元と同じ、テーブルも子も持たないまとまりが無い）。失敗時の乱数の種を記録する | jqwik | TP（上流の NFR6.2 は継承とメニューを絞る関数のため当たらず、この単位の性質ベースのテストとして足す） |
| NFR6.7 | 上流の NFR6.3（E2E は最大2本）に関わる部分。新しい E2E は足さない。版 1 の DSL を持つ既存のテスト・E2E（`040-dsl-admin.e2e.ts`）・負荷の道具を版 2 に書き換える。E2E の書き換えは `team.md` の本数に数えない | 書き換えの一覧は `functional-spec.md` の 8節 | AC6.1.10・TP |
