# セキュリティの設計 — U2 dsl-v2

## 1. 出典

- この単位の承認済みの NFR 要件 `construction/dsl-v2/nfr-requirements/security-requirements.md`・`tech-stack-decisions.md`・`traceability.json`。その読み直しの記録 `.aidlc-reviews/nfr-requirements/units/dsl-v2/0921d0fe911cb972/1.json`（直していない R-03〜R-09）
- この単位の承認済みの機能設計 `construction/dsl-v2/functional-design/functional-spec.md`・`rules.md`（BR1.1〜BR8.2）
- 契約 `inception/contract-design/contract-summary.md`（C3）、部品 `inception/domain-design/components.md`（DslDefinition・DslManagement・DslAdminUi）、ADR-005・ADR-007
- この段の答え `nfr-design-questions.md`（Q1: A・Q2: A、まとめの確認は Looks correct）
- `team.md`（DSL の必須のテスト・Code Style の TraceAspect）、`project.md`（Forbidden・学び）
- コード（読み取りだけ）: `dsl/parse/SafeYamlParser.java`・`LimitingParser.java`・`YamlTreeConverter.java`、`dsl/service/ActiveDslModelStore.java`・`DefaultDslReader.java`、`dslmanage/service/DslStartupLoader.java`・`DslLifecycle.java`

## 2. 成果物の割り当て

この単位は library のため、作る成果物は3つで、性能・信頼性・観測の設計は次のように割り当てる（段の定義の `produces_kinds`）。ID は NFR 要件の ID（上流の枝番と同じ番号は同じ意味）をそのまま使う。

| 文書 | 扱う ID |
|---|---|
| `security-design.md`（この文書） | NFR1.5・NFR1.7〜NFR1.14・NFR3.3・NFR5.3〜NFR5.5 |
| `logical-components.md` | 部品の境界と置き方、NFR2.5〜NFR2.9（ヒープの見積もりと同時の扱いは、この文書の 4.9）・NFR4.1・NFR4.3・NFR6.1・NFR6.4〜NFR6.7、配備の段への引き継ぎ |

## 3. 捨ての試しの結果

リポジトリの外（作業用の一時の場所。終わった後に消した）で、`backend/gradle.lockfile` と同じ版の部品を使い、`dsl.parse`・`dsl.validate` の写しを使って確かめた。上限は、設計のとおり引数で渡せる形に写しを直して渡した。

- **部品の版**: SnakeYAML 2.7、networknt json-schema-validator 3.0.6、Jackson（tools.jackson）3.1.7。
- **実行環境**: Temurin 25.0.4、ヒープの上限 512 MB（試し1・2）と 1 GB（試し3）。
- **時間**: 合わせて約 15 分。

| # | 確かめたこと | 結果 | 設計への意味 |
|---|---|---|---|
| T1 | 別名の上限 0 を `LoaderOptions.setMaxAliasesForCollections` と `LimitingParser` の両方に渡したとき、対応表を指す別名1つで、どちらが先に止めるか | `LimitingParser` が先に止める。区分 `ALIAS_LIMIT`、文言の鍵 `dsl.limit.alias`、2 行目の位置つき。文字の値を指す別名（`a: &s v` と `b: *s`）は通る | 別名の数え方は今のまま（対応表・並びを指す別名だけ）で、role の「別名 0」の意図と合う（NFR1.5） |
| T1' | `LoaderOptions` の上限（0）を `LimitingParser` の上限（100）より小さくしたとき | 部品の `YAMLException`（位置なし）が先に出る。今の作りでは構文の誤り（`SYNTAX`・位置なし）に写ってしまう | 2つの上限には必ず同じ値を渡す（4.1 の決まり）。どちらか片方だけを引数にすると、区分と位置を失う |
| T2 | 別名の爆発する入力（9 段 × 9 個、約 3.9 億の節に展開する形、本文 540 バイト）を、DSL の上限（別名 100・展開後の節 1,000,000）で読ませたとき | 展開後の節の上限で止まる（区分 `ALIAS_LIMIT`、`dsl.limit.expandedNodes`）。拒否まで 0.17〜0.21 秒、ヒープの増え 約 130 MB | 数秒の内に止まる。テストの時間の上限は 5 秒とし、余裕を持たせる（4.2）。テストの JVM のヒープ（今は 1 GB）で足りる |
| T2' | 同じ入力を、小さな上限（展開後の節 1,000）と、role の値（別名 0・展開後の節 1,000,000・深さ 10）で読ませたとき | 小さな上限: 約 1 ミリ秒で展開後の節の上限で拒否。role の値: 最初の別名で別名の上限で拒否（約 0 ミリ秒） | 呼ぶ側の上限が効き、狭い上限ほど早く止まる |
| T2'' | 上限の内の入力（5 段、59,049 の節）| 読める（約 0.1 秒、ヒープの増え 約 90 MB） | 上限の内の別名の展開は今までどおり受け付ける |
| T3 | 想定に近い形（100 カラムのテーブルを並べた本文 約 8.2 MB）の版 1 と版 2（`schemas` → スキーマ → `tables`、メニューの組）で、読み込みと JSON Schema の検証の時間を比べる（3回ずつ） | 版 1: 読み込み 0.64〜0.89 秒・検証 0.15〜0.26 秒。版 2: 読み込み 0.57〜0.62 秒・検証 0.13〜0.18 秒。誤りはどちらも 0 件 | 版 2 にしても読み込みと検証の時間は変わらない。10 MiB でも合わせて 1〜2 秒の見込みで、投入・表示の 10 秒（NFR2.5）の内訳に収まる。実測は Build and Test（PostgreSQL の1種類）で行う |
| T4 | 承認の場の直し（R-01）で足した試し。上限ちょうどに近い版 2 の本文（10,485,581 バイト。100 テーブル × 100 カラムの本体 約 5.4 MB と、`perf/make-large-dsl.mjs` と同じ YAML のコメントの埋め草）を、読み込みと JSON Schema の検証で通す。ヒープの上限を変えて、1件と、待ち合わせで重ねた2件同時を流す | 1件は 384 MB で通り、256 MB で OutOfMemoryError になった。2件同時は 1 GB で通り（0.7〜0.9 秒）、512 MB では片方が・384 MB では両方が OutOfMemoryError になった。ヒープの区画ごとの最大の合計（上から見た目安）は、1件で 455〜539 MB、2件で 816〜836 MB | 1件の読み込みと検証には、ヒープを 256〜384 MB 使う（本文の約 25〜37 倍）。本番のヒープ 約 1 GiB（`compose.yaml` の既定の `mem_limit` 2g と Dockerfile の `MaxRAMPercentage` 50）では、1件は収まる。2件同時は、アプリの土台の分を引くと余裕が小さい。同時の扱いは 4.9 |

- T3 の版 2 の書式は、試しのために版 1 の書式から写して作ったもので、成果物の `dsl-schema-v2.json` ではない。形は機能設計の Q1: A のとおりで、`schemas` の対応表・スキーマの `label` と `tables`・メニューの `table` の組。
- T3 の本文は大きさを抑える作りのため 10 MiB に届かず、約 8.2 MB で比べた。T3 ではヒープの使い方を記録していなかったため、承認の場の直しで T4 を足して測った（リポジトリの外、約 10 分、終わった後に場所を消した）。
- T4 のヒープの値は、試しの JVM（Temurin 25.0.4・G1、アプリの土台を含まない）での値で、本番のアプリの中の値ではない。本番と同じヒープでの実測は Build and Test に引き継ぐ（`logical-components.md` の 4節）。

## 4. 設計

### 4.1 上限つきの安全な YAML の読み込みの口（NFR1.5・NFR1.11）

- **置き場**: `dsl.service` に口（インターフェース）と実装を置く。上限の型（大きさ・入れ子の深さ・対応表と並びを指す別名の数・展開後の節の数の4つ）も `dsl.service` に置き、作るときに範囲を確かめる（大きさ・深さ・節の数は 1 以上、別名は 0 以上。外れたら呼ぶ側の誤りとして例外）。
- **上限を引数にする**: 既存の `SafeYamlParser`・`LimitingParser`・`YamlTreeConverter` は、上限を `DslFormat` の定数ではなく、上限の型で受ける。DSL の読み込み（`DefaultDslReader`）は `DslFormat` の値で作った上限を渡す。
- **同じ値を2か所に渡す**: 深さと別名の上限は、`LoaderOptions`（`setNestingDepthLimit`・`setMaxAliasesForCollections`）と `LimitingParser` の両方に同じ値を渡す（T1・T1'）。大きさは、読む前のバイト数の判定と `setCodePointLimit` の両方に渡す。
- **入力と結果**:
  - 本文は `byte[]` で受け、文字列の型で受けない（読み直しの記録 R-05。`TraceAspect` が引数を文字列にしても `[B@…` の形で中身が出ない）。
  - 結果は sealed な型で返す。読めたときは、JSON の木と、位置を引く口（JSON Pointer から行・列を引く `dsl.service` の型）を返す。拒否のときは、区分（6つ）と位置だけを返す。
  - 部品の例外の文は捨て、区分に写す（T1' のように部品の例外が出ても、区分と位置は既存の写し方のまま）。
- **文字列にしたとき**: 結果を文字列にしたときは、区分・位置・節の数だけを出す（NFR1.11）。
- **テスト**:
  - TRACE を有効にした結合テストで、入力と結果の両方について、目印の文字列がアプリのログに出ないことを確かめる。

```java
// dsl.service（形だけ。名前は Code Generation で確定）
public interface SafeYamlReader {
    SafeYamlResult read(byte[] yamlBytes, SafeYamlLimits limits);
}
public record SafeYamlLimits(int maxBytes, int maxDepth, int maxAliases, int maxExpandedNodes) {
    // 作るときに範囲を確かめる（大きさ・深さ・節の数は 1 以上、別名は 0 以上）
}
```

### 4.2 DSL の上限・タグ・重複キー（NFR1.7・NFR1.8）

- 値は今のまま（10 MiB・深さ 50・別名 100・展開後の節 1,000,000）。`DslFormat` に置き、4.1 の上限の型で部品へ渡す。
- 既存の境界・タグ・重複キーのテストは、版 2 の本文に書き換える（NFR6.5）。スキーマ名とテーブル名の重複キーのテストを足す。
- **別名の爆発のテスト**（読み直しの記録 R-06）:
  - DSL の上限の値と小さな上限の値のそれぞれで、別名の爆発する入力（T2 と同じ形）が 5 秒の内に、展開後の節の上限の区分で拒否されることを確かめるテストを1件ずつ置く。
  - 時間は、テストの中で測った時間で判定する（`@Timeout` 相当）。5 秒は T2 の実測（0.2 秒）に余裕を持たせた値。

### 4.3 書式と外部の参照（NFR1.9）

- 版 2 の書式 `dsl-schema-v2.json` を同梱し、ビルドで画面の静的なファイルの置き場へ写して `/dsl/dsl-schema-v2.json` で配る（今の v1 と同じ仕組み）。v1 のファイルは置かない。
- JSON Schema の部品は、今の設定（外部の資源を取りに行かない `fetchRemoteResources(false)`、起動時に1回だけ組み立てる）のまま使う。

### 4.4 拒否の応答と、500 にしないこと（NFR1.10・NFR3.3）

- **拒否の応答**: 既存の Problem Details（`DSL_INVALID` 422・`DSL_TOO_LARGE` 413）で返す。版 1・深さ・スキーマの数の誤りも、誤りの一覧の項目として同じ形で返す。部品の例外の文は、DslError の文言の鍵と埋める値にだけ写る（今のまま）。
- **プレビューの表示と適用の前の読み直し**:
  - 読み直すのは、キャッシュにモデルが無いとき（再起動の後）だけ（BR3.3・BR3.4）。
  - 適用では、本文を履歴へ移す内部DB の更新の前に読み直す。
  - 読み直しが通らないときは `DSL_INVALID` を投げ、内部DB には触れない。
- **起動時の読み直し**:
  - 深さ以外の誤りなら、既存の ERROR を出して Absent にする。
  - 深さだけなら、枝を落として Present にする（4.5）。
  - 内部DB を読めないときだけ、今までどおり起動を止める。

### 4.5 メニューの深さと起動時の枝の落とし方（NFR1.12）

- 深さを数える関数と枝を落とす関数は、`dsl.domain` の純粋な関数にする（`DslMenuItem` の木を受けて、新しい木と落とした数を返す）。どちらも木を1回たどるだけ。
- 入れ子は YAML の深さの上限 50 の内に収まるため、再帰でたどってもスタックを使い尽くさない。
- 意味の検証（`dsl.validate`）は、深さの確かめを入れるかどうかを受ける。起動時の読み方（`DslReader` に足す口）だけが、深さの確かめを外す。

### 4.6 接続情報（NFR1.13）

- 既定の DSL の生成は、写し（`TargetSchema`）のスキーマ名だけを DSL に書く。接続先・ポート・ユーザー名・パスワード・JDBC の URL と PostgreSQL の database の項目は、生成の部品に渡さない（今のまま、写しにしか触れない）。
- 照合の警告 `SCHEMA_MISMATCH` の文には DSL のスキーマ名だけを埋め、写しのスキーマ名を埋めない（NFR5.5）。
- 既存の漏えいのテストのうち「スキーマ名を含まない」の確かめは「スキーマ名を含む」に書き換え、接続の項目を含まない確かめは残す。

### 4.7 境界（NFR1.14）

- `role` などの外の機能は、`dsl.service` の口（`SafeYamlReader`・`ActiveDslModelProvider`）と `dsl.domain` の型だけを使う。
- `DslBoundaryArchitectureTest`（`parseAndValidateStayInside`・`dslStaysIndependent`）は変えずに通す。`dslmanage` の判定の置き場（4.8 の読めなかった版の ID）は `dslmanage.service` に置き、`dsl` に `dslmanage` の知識を持ち込まない。

### 4.8 ログと今の状態の印（NFR5.3〜NFR5.5、Q1: A）

- **起動時に深すぎる枝を落としたとき**: `DslStartupLoader` が WARN を1件出す。値は SLF4J のキーと値で渡し、出すのは `dsl.hash`（識別の先頭12文字）・`dsl.prunedMenuItems`（落とした項目の数）・`dsl.menuDepthLimit`（上限の値）の3つだけ（NFR5.3。キーの名前は Code Generation で確定）。
- **起動時に読めないとき**: 既存の ERROR（識別の先頭12文字・誤りの種類）のままにする（NFR5.4）。
- **読めないプレビューの 422**: 既存の変換の境界で WARN 以下・スタックトレースなしで1回だけ出す。
- **今の状態の印（Q1: A）**:
  - `DslStartupLoader` は、起動時に読めなかった適用中の版の ID（revision の ID）を、`dslmanage.service` の中の小さな置き場（メモリ）に覚える。読めたとき・適用中が無いときは空にする。
  - 今の状態の `appliedUnreadable` は、「今の適用中の版の ID が、覚えた ID と同じ」ときだけ真にする。
  - 新しい版を適用すると版の ID が変わるため、置き場を消さなくても偽になる。適用の確定から提供口の差し替えまでの間に読んでも、誤って真にならない。
  - 提供口と `ActiveDsl` の形、内部DB の表は変えない。

```java
// dslmanage.service（形だけ）
final class UnreadableAppliedRevision {
    private final AtomicReference<UUID> revisionId = new AtomicReference<>();
    void remember(UUID id) { revisionId.set(id); }   // 起動時に読めなかったとき
    void clear() { revisionId.set(null); }           // 起動時に読めた・適用中が無いとき
    boolean isUnreadable(UUID currentRevisionId) {
        return currentRevisionId != null && currentRevisionId.equals(revisionId.get());
    }
}
```

### 4.9 10 MiB の読み込みのヒープと、同時の投入の扱い（NFR2.5・NFR2.6、承認の場の直し R-01）

- **見積もり**:
  - 1件の読み込みと検証のヒープは 256〜384 MB（T4）。本番のヒープ 約 1 GiB に対し、1件は収まる。
  - 2件が重なると、アプリの土台（起動の後の使用・接続のプール・ほかの要求）と合わせて余裕が小さく、足りなくなるおそれがある。
- **今ある排他**:
  - DSL の重い操作（投入・既定の DSL の生成・履歴の復元・プレビューの表示（照合））は、既存の `DslHeavyOperationGate`（`Semaphore(1)`、本文を読む前に取る）で、アプリ全体で同時に1つしか通らない。2つ目は待たずに 503 `DSL_BUSY` になる。
  - そのため、管理者が同時に投入しても、DSL の読み込みは重ならない。
- **新しい制限は足さない（受け入れた制約）**:
  - (a) 適用（`POST /api/admin/dsl/apply`）はこの排他に入らない。BR3.4 の適用の前の読み直しは、キャッシュに無いとき（再起動の後）だけ本文を読む。そのため、再起動の直後の適用が投入と重なると、2件分のヒープを使いうる。
  - (b) role の権限の YAML の確かめと適用（`RoleTransferSlot`、role の NFR 設計）は別の排他で、DSL の重い操作とは重なりうる。role の NFR 設計も、DSL の管理の適用はその入口に入らないことを、受け入れた制約としている。
  - どちらも起きる場面が狭い（再起動の直後の適用、2種類の管理の操作の重なり）。
  - 重なって OutOfMemoryError になったときは、既存の想定外の失敗（500）として扱う。内部DB の確定の前なら、DSL の状態は変わらない。
  - 新しい排他を足すかは、Build and Test の実測で足りなかったときに依頼者に諮る。

## 5. 上流との差（承認済みの文書は書き換えない）

| 上流 | 上流の書き方 | この設計 | 理由 |
|---|---|---|---|
| 機能設計 BR3.6 | 内部DB に適用中の版があり、提供口が Absent、または提供口の識別が適用中の識別と違うときに真 | 起動時に読めなかった版の ID を覚え、今の適用中の版の ID と同じときだけ真（4.8） | Q1: A。識別を比べる形では、適用の確定から提供口の差し替えまでの間に、正しい DSL でも一瞬真になる。判定は「起動時に読めなかった」という BR3.6 の意味そのもの |
| NFR 要件 NFR1.5 | 4つの上限を渡す | 深さと別名の上限は `LoaderOptions` と `LimitingParser` に同じ値を渡すことを足した（4.1） | T1'。片方だけを引数にすると、区分と位置を失う |
| NFR 要件 NFR1.8（読み直しの記録 R-06） | 別名の爆発で止まらないことの時間の上限が無い | 5 秒の内に拒否されるテストを、DSL の上限と小さな上限で1件ずつ足す（4.2） | T2 の実測 0.2 秒に余裕を持たせた |
| NFR 要件 NFR1.11（読み直しの記録 R-05） | 結果を文字列にしたときだけを縛る | 入力も `byte[]` で受け、TRACE のテストで入力の側も確かめる（4.1） | 入力が文字列だと 10 MiB の本文がそのまま TRACE に出うる |
| NFR 要件 `tech-stack-decisions.md` の対応表（読み直しの記録 R-09） | NFR1.5 の行に「足す NFR1.7〜NFR1.12・NFR1.14」と範囲で書く | 正しい並びは NFR1.7〜NFR1.10・NFR1.12・NFR1.14（`security-requirements.md`・`traceability.json` と同じ）。NFR1.13 は上流の NFR1.6 の行に、接続情報の守りとして含める | 振り直しの表記の不整合。中身は変わらない |

## 6. 承認の場の決定と直し

- **決定**: 依頼者は承認の場で Request Changes を選び、決定の文は「推奨の案のとおり直す」（直す範囲は、各単位の読み直しの Major と、単位の間でそろえる3点）。この単位で直したのは、読み直しの記録（`aidlc/spaces/default/intents/261004-role-menu/.aidlc-reviews/nfr-design/units/dsl-v2/6f07949191bc1397/1.json`）の R-01 の1件。Minor・Suggestion（R-02〜R-06）は直していない。
- **R-01（Major）**:
  - 指摘: 10 MiB の DSL を読み込んで検証するときのヒープの最大と、管理者が同時に投入したときの扱いが書かれていなかった。T3 はヒープの使い方を記録していなかった。
  - 直し:
    - 捨ての試し T4 を足して、1件と2件同時のヒープを測った（3節）。
    - 4.9 に、ヒープの見積もり・既存の `DslHeavyOperationGate` による同時の扱い・受け入れた制約を書いた。受け入れた制約は、適用の前の読み直しと、role の `RoleTransferSlot` との重なり。新しい制限は足さない。
    - `logical-components.md` の 4節に、Build and Test で本番と同じヒープで 10 MiB の投入を測ること（2つ同時の投入を含む）と、判定の値を書いた。
    - `traceability.json` の NFR2.5・NFR2.6 の行に 4.9 を足した。
