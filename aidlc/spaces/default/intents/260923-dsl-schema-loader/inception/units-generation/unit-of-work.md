# Unit of Work — dsl-schema-loader

Domain Design の部品（`aidlc/spaces/default/intents/260923-dsl-schema-loader/inception/domain-design/components.md`）を、Construction で1つずつ設計・実装する単位にまとめた。確定した答えは `units-generation-questions.md` の Q1〜Q4。どの単位から作るかは Delivery Planning で決める（この文書は順序を決めない）。

## 単位の一覧

| Unit ID | Directory | 名前 | 種別（kind） | 含む部品 | 対応する要件 | 規模 |
|---|---|---|---|---|---|---|
| U1 | u1-target-db | 対象DB | library | TargetDatabase | FR2.1〜FR2.5、FR3.2（読み取り）、NFR4、NFR6、NFR7、NFR12 | M |
| U2 | u2-dsl-definition | DSL の定義 | library | DslDefinition、ActiveDslModel | FR1.1〜FR1.7、FR4.2〜FR4.4、FR6.4（モデルと提供口）、NFR2（読み込みの上限）、NFR3、NFR5 | L |
| U3 | u3-default-dsl-generation | 既定の DSL の生成 | library | DefaultDslGeneration | FR3.3〜FR3.5、NFR1（生成） | M |
| U4 | u4-dsl-management | DSL の管理 | service | DslLifecycle、DslPreviewAnalysis、AuditLog（拡張） | FR3.1、FR3.6、FR4.1、FR4.3、FR4.5、FR5.1〜FR5.3、FR6.1〜FR6.3、FR7.1〜FR7.3、FR8.1、FR9.1、FR9.2、FR10.1〜FR10.3、NFR1（照合）、NFR2（投入の API の上限）、NFR8 | XL |
| U5 | u5-dsl-admin-ui | DSL の管理画面 | ui | DslAdminUi、ApiClient（拡張） | FR9.1〜FR9.3（画面）、NFR9、NFR10 | L |

すべての単位は、画面を同梱した実行可能 WAR 1つのアプリに組み込まれる（配備の形: embedded。独立して配備する単位は無い）。

## 単位の定義

### U1 対象DB（u1-target-db）

- **種別**: library（アプリの中で使う部品。単独では動かない）
- **境界**: パッケージ `cherry.mastersmith.targetdb`。対象DB の接続の設定・接続・メタデータの読み取りだけを持つ。DSL を知らない。
- **受け持ち**:
  - `mastersmith.target-db.*` の設定の受け取りと起動時の点検（問題のある項目の名前だけを WARN、値は出さない、パスワードの伏せ字）
  - 対象DB の接続を「既定の候補にしない」指定で足す（ADR-006）。読み取り専用・小さなプール・接続と問い合わせの待ち時間
  - 設定が無い・接続できない・応答しないを区別した結果
  - 設定したスキーマのテーブル・ビュー・カラム・型・主キー・外部キー・NOT NULL・既定値・コメントの読み取りと、3種類の DB の違いの吸収
- **実装の注記・制約**:
  - 対象DB を実際に読む最初の単位なので、Testcontainers の導入（依存・結合テストの段への組み込み・colima と CI の前提の文書化・README の見直し）をここに含める（`aidlc/spaces/default/memory/team.md` の Testing Posture）。
  - 「既定の候補にしない」指定が Spring Boot 4.1 系で働くことと、ログイン・監査・Flyway・ヘルスチェックが内部DB を向き続けることを、結合テストで確かめる。働かなければ ADR-006 の切り替え先（内部DB を明示して既定にする）に替える。
  - 識別子は許可の一覧と引用符で扱う。スキーマ・データを変更する SQL を発行しない。
  - JDBC ドライバー3種類のライセンスを確かめ、採用の理由を記録する（team.md の Code Style）。

### U2 DSL の定義（u2-dsl-definition）

- **種別**: library
- **境界**: パッケージ `cherry.mastersmith.dsl`。DSL の書式・読み込み・検証・解釈・モデル・適用中のモデルの保持と提供口を持つ。内部DB・対象DB・管理の操作を知らない（ADR-009）。
- **受け持ち**:
  - DSL の書式（JSON Schema と書式の版）の定義と同梱。後続の Intent I・J・K が使うモデルの形
  - YAML の安全な読み込み（大きさ・入れ子の深さ・別名の数の上限、タグの拒否、重複キーの誤り）と、位置の対応表（ADR-008）
  - 構文と意味の検証、誤りの一覧（種類・行・列・場所・内容）、DSL の識別（本文のバイト列のハッシュ値）
  - 適用中のモデルの保持と、一度に切り替わる差し替え、後続の Intent 向けの提供口（ActiveDslModel）
- **実装の注記・制約**:
  - YAML と JSON Schema の部品の選定（Jackson 3 系との組み合わせ、位置の対応表が作れること、ライセンス）は NFR 要件の段で小さく試して決める（ADR-008・ADR-010）。
  - 投入された DSL の「必ず書くテスト」（大きさ・深さ・別名・タグ・重複キー・`$ref`・拒否の応答）をこの単位で書く（team.md の Testing Posture）。性質ベースのテスト（jqwik）の当て先（どんな入力でも例外が漏れない など）もここ。
  - 最も技術的に不確かな単位（行・列の対応）。

### U3 既定の DSL の生成（u3-default-dsl-generation）

- **種別**: library
- **境界**: パッケージ `cherry.mastersmith.dslmanage` の中の生成の部品。U1 のメタデータを受け、U2 の書式の DSL（YAML の本文）を作る。保存・画面を知らない。
- **受け持ち**:
  - 表示名（ja・en に物理名、コメントがあれば ja にコメント）、1階層のメニュー（物理名の順）、ビューの読み取り専用
  - カラムの DB 上の型の情報（ADR-005）と、型・制約からフォーム部品・バリデーションへの対応（DB から導いたことの記録）
  - YAML の本文への書き出しと、U2 による検証
- **実装の注記・制約**:
  - 型の対応の規則（3種類の DB の型の違い）と、メニューの並べ方の規則は機能設計で決める。
  - 100テーブル×100カラムで 30 秒以内（読み取りの時間は U1 と合わせて測る）。
  - 性質ベースのテストの当て先: 既定の表示名の導き方、メニューの並び順、生成した DSL が必ず検証を通ること。

### U4 DSL の管理（u4-dsl-management）

- **種別**: service（管理者だけの API を持つ）
- **境界**: パッケージ `cherry.mastersmith.dslmanage` の管理の部品と、既存の `audit` の拡張。U1・U2・U3 を使う。
- **受け持ち**:
  - 管理者だけの API（`/api/admin/` の下）: スキーマの読み込み・投入（アップロード・貼り付け）・プレビューの取得・適用・破棄・履歴の一覧・履歴からの戻し・ダウンロード（プレビュー中・適用中）
  - 内部DB のプレビューの表と適用の履歴の表（Flyway の移行、ADR-002）、YAML の本文のままの保存（ADR-003）
  - 見たプレビューを指定する適用、同時の適用の1件だけの成功、一度に切り替わる確定、件数の上限（既定20件）、同じ内容の再適用も履歴に足す
  - 起動時の適用中のモデルの読み込みと、確定の後の差し替え（U2 の ActiveDslModel）
  - 要約・違い・対象DB との照合（DslPreviewAnalysis）
  - 監査の出来事（生成・投入・受け付けなかった投入・適用・破棄）と、AuditLog の拡張（監査の表の列の追加、出来事の種類の追加）
  - 投入の API だけ本文の上限を 5MB にする作り（今は全要求に 1MB）
- **実装の注記・制約**:
  - 誤りの一覧を返す応答の形（Problem Details の拡張）と `code` の一覧は、契約の設計で決める。バックエンドの共通のエラー応答（`common/error`）の拡張もここに含める。
  - 性質ベースのテストの当て先: 違いの計算（同じ DSL どうしなら違いは0件、入れ替えると増減が逆）、履歴の件数の切り詰め。
  - 規模が最も大きい。Delivery Planning で Bolt を分けるかを検討する。

### U5 DSL の管理画面（u5-dsl-admin-ui）

- **種別**: ui
- **境界**: 画面の機能 `frontend/src/features/dsl` と、既存の `frontend/src/shared/api-client` の拡張。U4 の API を呼ぶ。
- **受け持ち**:
  - サイドバーの「DSL」、1つの画面の今の状態とタブ（プレビュー・投入・履歴）、確かめる表示、処理中、誤りの一覧（先頭100件）、違いの表、メニューの木（`inception/refined-mockups/`）
  - make-you-chic-ui に無い部品（ファイルの選択・行の開閉・メニューの木）を画面の側で作る
  - 誤りの一覧を含む応答を受け渡す ApiClient の拡張
  - ja・en の文言、WCAG 2.1 AA、部品ごとの axe の検査
- **実装の注記・制約**:
  - 画面では DSL を検証しない（ADR-007）。送る前のファイルの大きさの確かめは使いやすさのための案内とする。
  - make-you-chic-ui の中身は変更しない（project.md の Forbidden）。

## 採らなかった分け方

- 3つにまとめる案（Q1 の B）: DSL の管理の単位が生成・画面まで抱え、1つの単位が大きくなりすぎる。
- ストーリーで縦に切る案（Q1 の C）: 同じ部品（DslLifecycle・DslDefinition）を複数の単位が触り、部品の持ち主が曖昧になる（components.md の「部品の持ち主は1つ」に反する）。
