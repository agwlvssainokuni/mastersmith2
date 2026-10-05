# NFR Requirements の質問 — U2 dsl-v2

単位 U2 dsl-v2（library。画面 `features/dsl` の小さな変更を含む）の NFR 要件の前に、決まっていない点を確かめます。読んだもの:

- この単位の承認済みの機能設計 `construction/dsl-v2/functional-design/`（`functional-spec.md`・`rules.md` の BR1.1〜BR8.2・`entities.md`・`frontend-components.md`）
- 要件 `inception/requirements-analysis/requirements.md`（NFR1〜NFR6）、契約 `inception/contract-design/contract-summary.md`（C3）、コード知識ベース `aidlc/spaces/default/codekb/mastersmith2/technology-stack.md`
- 前の Intent `260923-dsl-schema-loader` の DSL の NFR 要件（U2〜U5 の `performance-requirements.md`・`tech-stack-decisions.md`）と、負荷の試験の手順 `perf/README.md`
- コード: `dsl/parse/LimitingParser.java`・`YamlTreeConverter.java`・`SafeYamlParser.java`、`dslmanage/generate/DslYamlWriter.java`、`role` の機能設計（SafeYamlReader を呼ぶ側の上限の値。読み取りだけ）

library の単位のため、成果物は `security-requirements.md`・`tech-stack-decisions.md`・`traceability.json` の3つです（段の定義の `produces_kinds`）。性能・信頼性・観測の要件は、性能と信頼性を `tech-stack-decisions.md`、ログの決まりを `security-requirements.md` に書き、その扱いを各成果物の冒頭に書きます。

## 決まっていること

### ID の振り方

- 枝番はこの単位の中で `.1` から振り、要件の NFR の並び（NFR1 セキュリティ・NFR2 性能・NFR3 信頼性・同時性・NFR4 アクセシビリティ・画面・NFR5 監査・観測・NFR6 テスト・品質）に寄せる（`project.md` の学び）。寄せたことを成果物の冒頭に書く。
- 要件の NFR の多くは役割・権限の API について書いている。DSL の信頼できない入力・漏えいは NFR1、前の Intent の DSL の時間の目標の維持は NFR2、読めない DSL で 500 にしないことは NFR3、S10 は NFR4、起動時のログは NFR5、必須のテストとカバレッジは NFR6 に寄せる。当たらないもの（NFR1.1〜NFR1.4 の API の認可、NFR5.2 の新しい API の指標など）は `traceability.json` に理由を書いて N/A にする。

### セキュリティ（信頼できない入力・漏えい）

- **DSL の上限の値は変えない**: 大きさ 10 MiB・入れ子の深さ 50・コレクションを指す別名 100・展開後の節 1,000,000（`DslFormat`）。SafeYamlReader に上限を引数で渡す形に変えても、DSL の読み込みは同じ値を部品へ渡す（BR6.6）。
- **既存の上限のテストは版 2 で書き換えて残す**: `team.md` の「利用者が投入する DSL（YAML）」の必須のテスト（大きさ・入れ子の深さ・別名・タグ・重複キー・`$ref`・拒否の応答）のこと。
- **SafeYamlReader のテストは小さな上限で行う**: 4つの上限のそれぞれで「ちょうどは受け付け、1つ超えると拒否」、6つの区分、例外の文が出ないこと、TRACE で木の中身が出ないことを確かめる。上限の値は呼ぶ側の持ち物（role は深さ 10・別名 0・展開後の節 1,000,000・大きさ 10 MiB（仮）。role の NFR 要件で確定）。そのため U2 のテストは小さな値で境界を確かめ、10 MiB の本文を何度も作らない。
- **別名の上限の意味は今のまま**: 「対応表・並びを指す別名の数」で、文字の値を指す別名は数えない（`LimitingParser`）。展開の爆発は展開後の節の数で止める。role の答え（別名 0 は「対応表・並びを指す別名が1つでもあれば拒否」）と合っている。
- **メニューの深さの上限 5 段は DoS の守りでもある**: 起動時に枝を落とす処理の入れ子は、YAML の深さの上限 50 の内に収まる。落とす処理は木を1回たどるだけで、本文の大きさに比例する。
- **ログ**:
  - 起動時に深すぎる枝を落としたときの WARN は1件。出すのは識別の先頭12文字・落とした項目の数・上限の値だけで、DSL の本文・メニューの表示名・テーブルの名前・接続情報は出さない（BR2.3）。
  - 読めない適用中の DSL の ERROR は既存のまま。
  - 照合の警告 `SCHEMA_MISMATCH` には DSL のスキーマ名だけを入れ、対象DB の設定のスキーマ名・接続先は入れない（BR5.1）。
  - スキーマ名は接続情報に当たらないと読む（機能設計の Q7: A）。接続先・ポート・ユーザー名・パスワード・JDBC の URL を出さないことは、既存の漏えいのテスト（`DslTargetDbIT`・`AbstractDefaultDslGeneratorIT` の `assertNoConnectionInfo` など）で確かめる。
- **公開する JSON Schema には秘密を含めない**: `dsl-schema-v2.json` は今と同じくログインなしで配る。

### 技術の選択

- **新しい依存は足さない**: SnakeYAML 2.7・networknt json-schema-validator 3.0.6（`technology-stack.md`）・jqwik 1.10.1 のまま。版 2 の JSON Schema は今と同じ 2020-12 の書き方で書き、外部の `$ref` を解決しない設定も今のまま。
- **内部DB の表と Flyway の移行は変えない**: `team.md` の「前進のみ・前の版が動く」に触れない。前の版のアプリへ戻すと版 2 の DSL を読めないこと（AC6.1.8）は、配備の段の論点として引き継ぐ。

### 生成する DSL の大きさ（上限と最大の出力の突き合わせ、`project.md` の学び）

- 前の Intent の試算（100 テーブル × 100 カラム、コメント無しで約 5.3 MB、1カラム約 550 バイト）に、版 2 で増える分を足す。
  - テーブル以下の行は `schemas` → スキーマ → `tables` の2段だけ深くなり、字下げは2文字のため、1行あたり 4 バイト増える。
  - メニューの項目は `table: {schema, name}` の分だけ増える。
- 試算では約 6.3 MB で、上限 10 MiB の内に収まる。実際の大きさは Build and Test で生成して測り、記録する。上限は変えない。

### 信頼性

- 版 1 の DSL・深すぎる DSL・読めないプレビューは、どの入口でも 500 にしない（422 か Absent。BR3.1〜BR3.5）。起動は止めない。
- これらは結合テストで確かめる。拒否の後に内部DB を読み直し、適用中・プレビュー・履歴が変わらないことを見る。

### テスト・品質

- テストの決まりは機能設計の `functional-spec.md` の 8節・`frontend-components.md` の 6節のとおり。性質ベースのテスト（jqwik）は、深さの数え方と枝の落とし方に当てる。
- カバレッジ: `dsl`・`dslmanage` の下のパッケージはすでにパッケージごとの下限（行 80%・分岐 70%）の対象（`packagesJudgedByTotal` に入っていない）。B1 の `./gradlew verify`（`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実測）で下限を割らないことを確かめる。

質問は1問です。

---

## Q1 版 2 にした後の DSL の時間の目標の確かめ方

背景: 版 2 では DSL の読み込み・検証・照合・違い・要約・生成に手が入ります。前の Intent で決めた DSL の時間の目標は次の5つで、この単位でも保ちます。

- 生成の全体 30 秒
- 投入・戻し・プレビューの表示（照合を含む）10 秒（100 × 100 と上限の 10 MiB）
- 今の状態・履歴・破棄・適用の 95% が 1 秒
- ダウンロード 2 秒
- 画面の表示 3 秒

この Intent の流れには Performance Validation の段があります（`aidlc-state.md`）。どこまでを、どの段で測るかを決めます。

A. 1回ずつの時間（生成・投入・戻し・表示・ダウンロード・画面）は、Build and Test で測る。`perf/dsl-timing.sh`・`make-large-dsl.mjs`・`make-pattern-dsl.mjs`・`perf/ui/*` を版 2 に直し、PostgreSQL の1種類で流す。あわせて生成した DSL の大きさを記録し、英語の文言の確かめ（`--lang`。版の誤りと `SCHEMA_MISMATCH` の文言が届く）も流す。95 パーセンタイルと同時の実行（k6 の `dslLight`・`dslCycle`・`dslMixed`）は、Performance Validation で版 2 の DSL で流す。測れない段では `Unverified` とし、持ち主の段を明記して引き継ぐ（推奨: 版 2 で変わるのは DSL の形と検証で、対象DB の読み取りは変わらないため、DB の種類ごとの差は出にくい。3種類で流すより短い時間で、前の Intent と同じ目標を同じ道具で確かめられる）
B. A と同じだが、1回ずつの時間を3種類の DB（PostgreSQL・MySQL・MariaDB）すべてで流す（前の Intent と同じ。時間は約3倍）
C. 時間は測らない。版 2 の変更は形と検証だけとみなし、単体・結合テストと、Performance Validation の k6 だけで確かめる（`perf` の道具の版 2 への書き換えは k6 に要る分だけ）
X. Other (please specify)

[Answer]: A **Mode:** guided

## Consolidated Summary Confirmation

答えのまとめ（dsl-v2 の NFR 要件）:

- Q1 A: 版 2 にした後の 1 回ずつの時間は Build and Test で、perf の道具（dsl-timing.sh・make-large-dsl.mjs・make-pattern-dsl.mjs・ui）を版 2 に直して PostgreSQL の1種類で流し、生成した DSL の大きさ（試算 約 6.3 MB）と英語の文言の確かめ（--lang）も記録する。95 パーセンタイルと同時の実行（k6 の dslLight・dslCycle・dslMixed）は Performance Validation で流す。
- 決まっていること（作る成果物は security-requirements.md・tech-stack-decisions.md・traceability.json で、性能と信頼性は tech-stack-decisions.md、ログの決まりは security-requirements.md に書く。DSL の上限は今のまま（10 MiB・深さ 50・別名 100・展開後 100 万節）で既存の必須のテストは版 2 に書き換えて残す。SafeYamlReader は小さな上限の値で4つの上限の境界・6つの区分・例外の文を出さない・TRACE で木の中身を出さないを確かめる。別名は対応表・並びを指すものだけを数える。起動時の WARN は識別の先頭12文字・落とした項目の数・上限の値だけ。新しい依存は足さず、内部DB の表と移行も変えない。版 1・深すぎる DSL・読めないプレビューはどの入口でも 500 にせず起動も止めない）のとおり。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
