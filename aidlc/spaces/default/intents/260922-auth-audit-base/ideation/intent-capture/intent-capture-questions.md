# Intent Capture & Framing — Questions

## Sources

- [desc] Initial description: "MasterSmithマスタ管理アプリの最初のIntentとして、認証・認可基盤（ログイン、JWT発行/検証、管理者フラグによる管理画面アクセス制御）と監査ログ・構造化ログ基盤（内部DBへの監査ログ蓄積、SLF4J/logback構造化ログ、分散トレース、Actuator経由OTELエクスポート）を統合して開発する。要件は reference/master-mgmt-app-requirements.md のIntent B（5.7章, 7章）とIntent C（7章）に基づく。"
- [scope] Workflow-selected scope: `auth-audit-foundation`.

## Questions

### Q1. このIntentで解決するビジネス上の課題は何ですか？

MasterSmithの他の全機能（メニュー・一覧・詳細画面などIntent I/J/K以降）が依拠する認証・認可・監査ログの土台が、まだ存在しません。

- A. MasterSmithの他の全機能が依拠する認証・認可・監査ログの土台を、最初に確立する
- B. それとは別の課題がある（自由記述してください）
- C. Not yet defined
- X. Other (please specify)

[Answer]: A. MasterSmithの他の全機能が依拠する認証・認可・監査ログの土台を、最初に確立する

### Q2. 成功の定義・測定指標は何ですか？（何を達成すれば「このIntentは成功した」と言えますか）

- A. ログイン・JWT発行/検証・管理者権限ゲートが動作し、対象イベントが漏れなく監査ログに記録され、後続Intent（D以降）がこの基盤の上に問題なく構築できる
- B. それとは別の指標がある（自由記述してください）
- C. Not yet defined
- X. Other (please specify)

[Answer]: A. ログイン・JWT発行/検証・管理者権限ゲートが動作し、対象イベントが漏れなく監査ログに記録され、後続Intent（D以降）がこの基盤の上に問題なく構築できる

### Q3. 対象ユーザー（顧客）は誰ですか？また、その方々が抱えている課題やペインは何ですか？

- A. MasterSmithを導入・運用する組織の内部管理者・利用者（外部エンドユーザー向けではない）
- B. それとは別の対象がある（自由記述してください）
- X. Other (please specify)

[Answer]: A. MasterSmithを導入・運用する組織の内部管理者・利用者（外部エンドユーザー向けではない）

### Q4. このIntentに着手する理由（トリガー）は何ですか？

- A. 新規プロジェクト（MasterSmith）の立ち上げそのものであり、他の全機能の前提となる基盤が必要なため
- B. それとは別の理由がある（自由記述してください）
- X. Other (please specify)

[Answer]: A. 新規プロジェクト（MasterSmith）の立ち上げそのものであり、他の全機能の前提となる基盤が必要なため

### Q5. 主要なステークホルダーは誰で、それぞれ何を重視していますか？

- A. 依頼者（あなた）が唯一のステークホルダーであり、要件（reference/master-mgmt-app-requirements.md）通りの実装を重視する
- B. 他にもステークホルダーがいる（自由記述してください）
- C. Not identified
- X. Other (please specify)

[Answer]: A. 依頼者（あなた）が唯一のステークホルダーであり、要件（reference/master-mgmt-app-requirements.md）通りの実装を重視する

### Q6. スコープや優先度を決定するのは誰ですか？また、それに影響を与えるのは誰ですか？

- A. 依頼者（あなた）が単独で決定する
- B. それとは別の体制がある（自由記述してください）
- C. Not identified
- X. Other (please specify)

[Answer]: A. 依頼者（あなた）が単独で決定する

### Q7. コミュニケーション要件や定期報告のケイデンスはありますか？

- A. 特になし（このAI-DLCセッション内でのやり取りのみ）
- B. ある（自由記述してください）
- C. Not applicable
- X. Other (please specify)

[Answer]: A. 特になし（このAI-DLCセッション内でのやり取りのみ）

### Q8. このワークフローはカスタムスコープ `auth-audit-foundation`（22/33ステージ実行）で開始されました。これはあなたが意図するプロダクト境界と一致していますか？

- A. 一致している（認証・認可基盤＋監査ログ・構造化ログ基盤の範囲で問題ない）
- B. 一致していない（境界を修正したい — 自由記述してください）
- X. Other (please specify)

[Answer]: A. 一致している（認証・認可基盤＋監査ログ・構造化ログ基盤の範囲で問題ない）

### Q9. intent-statement.mdは、後続Intent（D、I、J、K）に言及する際、読者が別の資料（reference/配下、Git管理外）を参照していることを前提にできません。自己完結的に説明するため、これらのIntentが何を指すかを明記したいと考えています。以下の要約で正しいですか？

- A. D=対象DBのスキーマ読み込み・デフォルトDSL生成機能、I=メニュー・ナビゲーション（N階層）、J=一覧画面（検索＋一覧表示）、K=詳細・編集画面（バリデーション＋フォーム部品＋参照ピッカー子画面）
- B. 別の要約にしたい（自由記述してください）
- X. Other (please specify)

[Answer]: B. D、I、J、Kだけでなく、ロードマップ全体（Intent A〜K）を自己完結的に説明する

### Q10. Intent A〜K全体の要約は以下の内容で正しいですか？

- A: DSLスキーマ定義 — マスタ管理画面の項目設定（メニュー・検索条件・一覧表示・バリデーション・フォーム部品・表示名等）をYAML＋JSON Schemaとして定義する
- B: 認証・認可基盤 — ログイン・JWT発行/検証、管理者フラグによる管理画面アクセス制御（本Intentに含む）
- C: 監査ログ・構造化ログ基盤 — 内部DBへの監査ログ蓄積、SLF4J/logback構造化ログ、分散トレース、Actuator経由OTELエクスポート（本Intentに含む）
- D: スキーマ読み込み・デフォルトDSL生成機能 — 管理画面からの手動実行、初回のデフォルトDSL生成、既存DSLへの全上書きリセット
- E: DSLローダー／インタープリタ＋プレビュー・適用フロー — DSLを読み込み内部モデルへ変換するバックエンド基盤機能、プレビュー→適用の2段階反映フロー
- F: ロールベース権限管理 — ユーザー/グループ/ロールの管理、主権限・補助権限の設定画面、作業ロール切替UI、内部DB永続化、YAML export/import
- G: ユーザー登録・招待フロー — 管理者によるユーザー登録、言語指定付き招待メール送信、招待ユーザーによるプリファレンス設定での登録完了
- H: ユーザープリファレンス — 言語（ja/en）・テーマ（light/dark/system）・文字サイズの個人設定画面、内部DB永続化
- I: メニュー・ナビゲーション（N階層）
- J: 一覧画面（検索＋一覧表示） — DSL・権限駆動でのUI生成
- K: 詳細・編集画面（バリデーション＋フォーム部品＋参照ピッカー子画面） — DSL・権限駆動でのUI生成

- Looks correct
- Request changes

[Answer]: Looks correct

## Consolidated Summary Confirmation

- 課題: MasterSmithの他の全機能が依拠する認証・認可・監査ログの土台を、最初に確立する
- 成功基準: ログイン・JWT発行/検証・管理者権限ゲートが動作し、対象イベントが漏れなく監査ログに記録され、後続Intentがこの基盤の上に問題なく構築できる
- 対象ユーザー: MasterSmithを導入・運用する組織の内部管理者・利用者
- トリガー: 新規プロジェクト（MasterSmith）の立ち上げそのもの
- ステークホルダー: 依頼者（あなた）が唯一
- 意思決定者: 依頼者（あなた）が単独で決定
- コミュニケーション要件: 特になし
- スコープ確認: カスタムスコープ`auth-audit-foundation`はあなたの意図する境界と一致

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
