# セキュリティの要件 — U2 dsl-v2

## 出典

- この単位の機能設計 `aidlc/spaces/default/intents/261004-role-menu/construction/dsl-v2/functional-design/functional-spec.md`・`rules.md`（BR1.1〜BR8.2）
- 要件 `aidlc/spaces/default/intents/261004-role-menu/inception/requirements-analysis/requirements.md`（NFR1〜NFR6・FR7.4・FR9.4・C6）
- 契約 `aidlc/spaces/default/intents/261004-role-menu/inception/contract-design/contract-summary.md`（C3）
- コード知識ベース `aidlc/spaces/default/codekb/mastersmith2/technology-stack.md`
- この段の答え `nfr-requirements-questions.md`（Q1: A、まとめの確認は Looks correct）、`team.md`（Testing Posture の DSL の必須のテスト・Code Style）、`project.md`（Forbidden・学び）

## ID の振り方と上流の枝番との対応（2つの成果物に共通）

- 上流の枝番（`requirements.md` の NFR1.1〜NFR6.4）と **同じ番号は同じ意味** でだけ使う。上流の要件をこの単位に当てはめたものは上流と同じ ID にする。この単位で新しく足す要件は、上流の最後の枝番の次から振る（NFR1.7〜・NFR2.5〜・NFR3.3〜・NFR5.3〜・NFR6.5〜）。group・role・navigation と同じ形。
- この単位は library のため、性能・拡張性・信頼性・観測の文書は作らない（段の定義の `produces_kinds`）。セキュリティ（NFR1）とログの決まり（NFR5）は `security-requirements.md`、性能（NFR2）・信頼性（NFR3）・画面（NFR4）・テスト（NFR6）は `tech-stack-decisions.md` に書く。1つの要件は1つの成果物にだけ書き、下の表を両方の冒頭に置く。

| 上流の枝番 | この単位での扱い | この単位の ID と置き場 |
|---|---|---|
| NFR1.1 API ごとのサーバー側の判定 | 当たらない（この単位は API を足さず、既存の DSL の API の道と守りも変えない） | — |
| NFR1.2 401・403・200 のテスト | 当たらない（同上。足す・変える API が無い） | — |
| NFR1.3 API の分類の網羅 | 当たらない（既存の DSL の API への印は U1 cross-cutting） | — |
| NFR1.4 昇格・一括代入・IDOR | 当たらない（役割・権限を扱わない） | — |
| NFR1.5 権限の YAML の守り（FR7.4） | 部分（守りの部品 SafeYamlReader と、FR7.4 が同じ守りを求める DSL の守り。上限の値と YAML の中身の検証は U4 role） | NFR1.5、足す NFR1.7〜NFR1.10・NFR1.12・NFR1.14（security）・NFR3.3（tech-stack-decisions） |
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

## 脅威と守り

| 脅威 | 入口 | 守る要件 |
|---|---|---|
| 大きい・深い・別名の爆発する YAML による資源の枯渇（DoS） | DSL の投入・復元、権限の YAML（U4 が SafeYamlReader で読む） | NFR1.5・NFR1.7・NFR1.8・NFR1.12 |
| 任意の型を作るタグ・重複キーによる値の差し替え | 同上 | NFR1.5・NFR1.8 |
| JSON Schema の外部の参照の取得（SSRF） | 構文の検証 | NFR1.9 |
| 部品の例外の文・本文・接続情報の漏えい | 誤りの応答・ログ・TRACE | NFR1.10・NFR1.11・NFR1.13・NFR5.3〜NFR5.5 |
| 境界の外からの読み込みの部品の直接の利用（守りの迂回） | `dsl.parse`・`dsl.validate` | NFR1.14 |

## 要件

| ID | 要件 | 確かめ方 | 出典 |
|---|---|---|---|
| NFR1.5 | 権限の YAML の守り（上流の NFR1.5・FR7.4）のうち、この単位が持つ守りの部品。上限つきの安全な YAML の読み込みの口（SafeYamlReader）は、呼ぶ側が渡す4つの上限（大きさ・入れ子の深さ・対応表と並びを指す別名の数・展開後の節の数）で守る。大きさは読む前に判定する。拒否は6つの区分（TOO_LARGE・TOO_DEEP・TOO_MANY_ALIASES・TAG_NOT_ALLOWED・DUPLICATE_KEY・SYNTAX）と位置だけを返し、部品の例外の文を含めない。上限の値が範囲の外（大きさ・深さ・節の数が 1 未満、別名が 0 未満）なら呼ぶ側の誤りとして例外にする | 単体テスト（小さな上限の値、例: 大きさ 64 バイト・深さ 3・別名 0 と 1・節 20）で、4つの上限のそれぞれの「ちょうどは受け付け、1つ超えると拒否」と、6つの区分・位置・例外の文が無いことを確かめる。別名 0 では対応表を指す別名が1つで拒否、文字の値を指す別名は数えない。10 MiB の本文は作らない | ADR-007・C3・BR6.1〜BR6.4・AC3.1.7・AC3.1.13 |
| NFR1.7 | DSL の本文の大きさの上限は今のまま 10 MiB（10,485,760 バイト）。読む前にバイト数で判定し、超えたら既存の `DSL_TOO_LARGE`（413）または `SIZE_LIMIT` の誤りで拒否する | 既存の境界のテスト（ちょうどは受け付け、1バイト超えると拒否）を版 2 の本文に書き換えて流す | TP・PM（Mandated）・AC6.1.10・BR6.6 |
| NFR1.8 | DSL の読み込みの上限は今のまま（入れ子の深さ 50・対応表と並びを指す別名 100・展開後の節 1,000,000）。任意の型を作るタグ（`!!` など）と独自のタグは拒否して型を作らず、重複キーは誤りにして後の値で上書きしない。スキーマ名と、同じスキーマの中のテーブル名の重なりも重複キーで落ちる | 既存の上限・タグ・重複キーのテストを版 2 に書き換える。スキーマ名とテーブル名の重複キーのテストを足す | TP・BR1.4・BR6.6・AC6.1.5・AC6.1.10 |
| NFR1.9 | 構文の検証は同梱の `dsl-schema-v2.json`（JSON Schema 2020-12）で行い、外部の `$ref`（URL）を取りに行かない。公開する書式のファイルに秘密を含めない（ログインなしで配る） | 既存の `$ref` のテストを版 2 の書式で流す。公開の道の結合テスト（`DslSchemaPublicationIT`）を v2 の道に書き換える | TP・BR1.7 |
| NFR1.10 | 拒否の応答は Problem Details（既存の `DSL_INVALID` 422・`DSL_TOO_LARGE` 413）で、YAML・JSON Schema の部品の例外の文とスタックトレースを含まない。版 1・深すぎるメニュー・スキーマの数の誤りも同じ形で返す | 版 1・版が無い・深さ 6 段・スキーマ 0 と 2 の投入と復元の結合テストで、応答に部品の例外のクラス名と文が無いことを確かめる | PM（Forbidden）・TP・BR1.1・BR1.3・BR2.1 |
| NFR1.11 | SafeYamlReader の結果を文字列にしたときは、区分・位置・節の数だけを出し、木の中身（利用者が書いた名前や値）を出さない（`TraceAspect` が `dsl.service` の戻り値を TRACE に出すため） | TRACE を有効にした結合テスト（既存の `*SecretLeakIT` と同じ形）で、目印の文字列を含む YAML を読ませ、ログに目印が出ないことを確かめる | TP（Code Style の TraceAspect）・BR6.5 |
| NFR1.12 | メニューの深さの上限は 5 段（`menus` の直下を 1 段目）。投入・復元・プレビューの読み直しでは 6 段目があれば拒否する。起動時に枝を落とす処理は木を1回たどるだけで、入れ子は YAML の深さの上限 50 の内に収まる | 深さ 5 は受け付け、6 は拒否の境界テスト。枝の落とし方に jqwik の性質ベースのテスト（NFR6.6） | FR9.4・TP（N 階層のメニューの深さ）・BR2.1〜BR2.3 |
| NFR1.13 | 生成した DSL・プレビューの応答・照合の警告・ログに、対象DB の接続先・ポート・ユーザー名・パスワード・JDBC の URL と PostgreSQL の database の項目を含めない。スキーマ名（MySQL・MariaDB ではデータベース名と同じ値）は接続情報に当たらないと読み、3種類の DB とも DSL に書く | 既存の漏えいのテスト（`DslTargetDbIT`・`AbstractDefaultDslGeneratorIT` の `assertNoConnectionInfo` と、ポート・ユーザー名・パスワード・`jdbc:` を含まないことの確かめ）を残す。「スキーマ名を含まない」の確かめは「スキーマ名を含む」に書き換える | PM（Forbidden）・C6・機能設計 Q7: A・BR4.3 |
| NFR1.14 | `dsl.parse`・`dsl.validate` は `dsl` の外から使わせない。外の機能は `dsl.service` の口と `dsl.domain` の型だけを使い、SafeYamlReader の結果の位置も `dsl.service` の型で返す | 既存の `DslBoundaryArchitectureTest`（`parseAndValidateStayInside` ほか）を緩めずに通す | ADR-007・C3・BR6.4 |
| NFR5.3 | 起動時に深すぎる枝を落としたときは、WARN を1件だけ出す。出す値は DSL の識別の先頭12文字・落とした項目の数・上限の値だけで、DSL の本文・メニューの表示名・テーブルの名前・接続情報は出さない。値は SLF4J のキーと値で渡す | 起動の結合テストで、WARN が1件、キーが3つで、目印の表示名・テーブル名がログに無いことを確かめる | AC5.2.3・BR2.3・TP（Code Style のログ） |
| NFR5.4 | 起動時に適用中の DSL を読めないとき（版 1 を含む）の ERROR は既存のまま（識別の先頭12文字と誤りの種類だけ）。読めないプレビューの表示・適用の 422 は想定内の業務の誤りとして、既存の変換の境界で WARN 以下・スタックトレースなしで1回だけ出す | 版 1 の適用中の DSL での起動の結合テストで、ERROR が1件・本文が無いことを確かめる | AC6.1.6・BR3.1・BR3.3・BR3.4・TP |
| NFR5.5 | 照合の警告 `SCHEMA_MISMATCH` の文には DSL のスキーマ名だけを入れ、対象DB の設定のスキーマ名と接続先を入れない。DSL の操作の監査（既存の `DslOperationEvent`）と指標は変えない | 名前の違うスキーマの DSL の照合の単体テストで、警告の文に設定のスキーマ名が無いことを確かめる | BR5.1・C6 |

## 必須のテストとの対応（`team.md` の「利用者が投入する DSL（YAML）」）

| 必須のテスト | 要件 |
|---|---|
| 大きさ（ちょうど・超え） | NFR1.5・NFR1.7 |
| 入れ子の深さ（ちょうど・超え） | NFR1.5・NFR1.8 |
| 別名（展開の数の超過、爆発で止まらない） | NFR1.5・NFR1.8 |
| タグ（拒否し型を作らない） | NFR1.5・NFR1.8 |
| 重複キー（誤りにし上書きしない） | NFR1.5・NFR1.8 |
| JSON Schema の `$ref`（外部を取りに行かない） | NFR1.9 |
| 拒否の応答（Problem Details、部品の例外の文を含まない） | NFR1.5・NFR1.10 |

## 承認の場の決定と直し

- **決定**: 依頼者は承認の場で Request Changes を選び、直す範囲を「Major 11 件だけを直す」とした。この単位で直したのは、レビュー（`aidlc/spaces/default/intents/261004-role-menu/.aidlc-reviews/nfr-requirements/units/dsl-v2/4e9510ac79c4db11/1.json`）の R-01・R-02 の2件。Minor・Suggestion（R-03〜R-08）は直していない。
- **R-01（Major）**:
  - 指摘: 上流の枝番と同じ番号を、この単位で別の意味に使っていた（例: NFR1.6 を SafeYamlReader の TRACE に使っていた）。
  - 直し: 上流と同じ番号は同じ意味でだけ使い、足す要件は上流の最後の枝番の次から振り直した。両方の成果物の冒頭に、上流の枝番との対応表を置いた。
- **R-02（Major）**:
  - 指摘: `traceability.json` の行が NFR1〜NFR6 の6行だけで、当たらない上流の枝番が OK に混ざっていた。
  - 直し: 上流の NFR{n} と枝番（NFR1.1〜NFR6.4）ごとに行を立てた。当たらない枝番は理由と持ち主の単位つきの N/A、当たるものと当たらないものが混ざる枝番は「部分」にした。成果物で定めた ID が、すべて枝番の行に現れることを確かめた。
- **ID の振り替え**（直す前 → 直した後）:
  - NFR1: NFR1.1 → NFR1.7、NFR1.2 → NFR1.8、NFR1.3 → NFR1.9、NFR1.4 → NFR1.10、NFR1.5 → NFR1.5（上流と同じ意味の「部分」）、NFR1.6 → NFR1.11、NFR1.7 → NFR1.12、NFR1.8 → NFR1.13、NFR1.9 → NFR1.14。
  - NFR2: NFR2.1 → NFR2.5、NFR2.2 → NFR2.6、NFR2.3 → NFR2.7、NFR2.4 → NFR2.8、NFR2.5 → NFR2.9。
  - NFR3: NFR3.1 と NFR3.2 → NFR3.3（1つにまとめた）。NFR3.3 → 枝番を振らない決定（`tech-stack-decisions.md` の 3節。Flyway の移行が無い）。
  - NFR4: NFR4.1 → NFR4.1、NFR4.2 → NFR4.3。
  - NFR5: NFR5.1 → NFR5.3、NFR5.2 → NFR5.4、NFR5.3 → NFR5.5。
  - NFR6: NFR6.1 → NFR6.1（深さの上限の境界）と NFR6.5（DSL の必須のテスト）に分けた。NFR6.2 → NFR6.6、NFR6.3 → NFR6.4、NFR6.4 → NFR6.7。
- 要件の中身（値・確かめ方・持ち主の段）は変えていない。
