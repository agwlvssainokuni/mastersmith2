# 信頼性の要件 — U1 u1-reference-read

出典: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/functional-spec.md`・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/rules.md`・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`

## 出典

- 要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`（NFR3・NFR6・NFR7、FR1.5・FR4.1、前提 A7）
- ストーリー `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/user-stories/stories.md`（AC1.1.4・AC2.1.13・AC3.4.x・AC3.5.3）
- この単位の機能設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/`（`functional-spec.md` 3節・4節・8節の D2・D5・10節、`rules.md` の BR1.5・BR1.6・BR8.1・BR8.4・BR8.5・BR9.5・BR12）
- 契約 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/contract-design/contract-summary.md`（C2・C5）
- この段の答え `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-requirements/nfr-requirements-questions.md`（Q1: A・Q2: A）
- コード（読み取りだけ）: `backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDbProperties.java`（`QueryTimeout` の既定 生成 20 秒・照合・一覧・詳細・書き込み 5 秒、`longest()`）、`targetdb/config/TargetDataSourceConfig.java`（読み取りのプールの通信の待ち = `longest()` ＋ 余裕）、`backend/src/main/resources/application.yaml`（`MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_*` の環境変数）、`.env.example`、`targetdb/domain/ReadPurpose.java`

## ID の対応表

全体の表は `security-requirements.md` の「ID の振り方」にある。

| 上流の ID | この単位での扱い | この単位の ID |
|---|---|---|
| NFR3 接続 | 当たる（時間の上限と失敗の扱い） | NFR3.4〜NFR3.7 |
| NFR6 多言語 | 部分（誤りの code の決まり。文言は U3） | NFR6.1 |
| NFR7 互換性 | 当たる | NFR7.1〜NFR7.3 |

## 要件

| ID | 要件 | 確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR3.4 | `ReadPurpose` に `REFERENCE` を足し、問い合わせ1回の時間の上限を既定 5 秒にする（Q1: A、BR8.1）。既存と同じく `TargetDbProperties.QueryTimeout` に値を足し、環境変数（既存の名前の並びに合わせた `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_REFERENCE`）で変えられるようにし、`application.yaml` と `.env.example` に書く。選択肢・検索・ラベル・保存前の確かめの4つの読み取りで共通にする。読み取りのプールの通信の待ちを決める `longest()` の計算に `REFERENCE` を含める（既定は 5 秒で、今の最長の 20 秒を変えないが、値を変えたときに通信の待ちが問い合わせの上限より短くならないようにする） | 設定の結び付けの単体テスト（既定 5 秒・環境変数で変えた値・`longest()` に入ること）。応答しない対象DB（`ServerSocket`）で、定義・検索・ラベルのそれぞれ1件ずつ 503 `BUSINESS_TARGET_DB_TIMEOUT` になる結合テスト（AC2.1.13・AC3.5.3。保存前の確かめの分は U2） | Code Generation |
| NFR3.5 | 定義の取得で参照先を読む全体の時間の上限を 10 秒にする（Q2: A、BR8.4）。注入した時計で最初の選択肢を読む前に締め切りを決め、各問い合わせの前に残りを秒に切り上げ、1 秒未満なら流さず定義の取得ごと 503 `BUSINESS_TARGET_DB_TIMEOUT`、そうでなければ上限を min(`REFERENCE` の上限, 切り上げた残り) に絞った `TargetReadSession` で流す。切り上げのため1文が締め切りを最大 1 秒未満超えうるため、選択肢の読み取りにかかる時間の最悪は約 11 秒（これに権限の写しの読み取りと接続を借りる待ちが前に乗る）。この幅は受け入れる | 注入した時計の単体テストで、残り 0.5 秒・1.0 秒・2.3 秒がそれぞれ「流さず 503」「上限 1 秒」「上限 min(5, 3) = 3 秒」になること。REFERENCE が複数で、途中で締め切りに達したときに残りを読まずに 503 になる結合テスト。上限の値を定数にするか設定にするかは NFR 設計で決める（既定 10 秒は変えない） | NFR 設計、Code Generation |
| NFR3.6 | 時間切れ・接続の失敗の後は、同じ接続で続けて読まない。既存の壊れた印を立て、残りの項目を読まずに定義の取得ごと失敗にする。項目ごとの 503 にはしない（BR8.5、AC1.1.4）。`check`・`countState` の失敗は理由つきの `FAILED` で U2 に返し、時間切れ・断絶では同じく壊れた印を立てる（BR9.5） | 3つの REFERENCE の2つ目で時間切れにした結合テストで、3つ目の問い合わせが流れず、接続がプールへ壊れたものとして返ること | Code Generation |
| NFR3.7 | 失敗の分け方は既存の業務データの読み取りと同じにする（BR1.5）: 時間切れと全体の上限の超過は 503 `BUSINESS_TARGET_DB_TIMEOUT`、接続の失敗・読み取りの設定が無い・借りられないは 503 `BUSINESS_TARGET_DB_UNAVAILABLE`、DSL と対象DB の型の食い違いは 409 `BUSINESS_TARGET_DB_MISMATCH`、想定外は既存の 500。対象DB の不調の間は作成・更新の画面全体が開けない（契約 C2 の受け入れた制約。画面の表示は U3） | 失敗の種類ごとの結合テストと、分ける関数の単体テスト | Code Generation |
| NFR6.1 | 新しい誤りの `code` は作らず、既存の code を使い回す（BR1.6）。1つの code に1つの状態コードを固定する決まり（`team.md`・NFR6）を崩さない。誤りの文言（日本語と英語）は画面（U3）が code から出す | 既存の `BusinessDataProblemTypeCatalog` の一覧が変わらないこと（code の数と状態コードの組）を確かめる単体テスト | Code Generation |
| NFR7.1 | DSL の書式の版と内部DB のスキーマ（移行ファイル）を変えない（NFR7、前提 A7）。この単位は移行ファイルを足さない | コード生成の差分に移行ファイルと DSL の JSON Schema の変更が無いこと | Code Generation |
| NFR7.2 | 定義の応答は足すだけにする。`purpose` は任意で既定 `VIEW`、各カラムの `reference` は `VIEW` では `null`、`formPart` は `VIEW` では今のまま `lookup` を `text` に置き換えて返す（BR12.1〜BR12.3、機能設計の D2）。U3 が入るまでの間、既存の画面は変えずに動く | 既存の定義の API のテストを変えずに通すこと。`purpose` を送らない要求の応答が、`reference: null` を除いて今と同じであることを比べる結合テスト。前の版の画面（E2E）が変えずに通ること | Code Generation、Build and Test |
| NFR7.3 | 一覧・詳細は参照先を読まない（FR4.1、BR12.4）。一覧・詳細の対象DB の接続の数と文の数は今と同じ | 既存の `BusinessDataConnectionUsageIT` の一覧・詳細の数が変わらないこと。参照の項目を持つ表の一覧・詳細で、参照先への問い合わせが流れないこと（AC3.4.x） | Code Generation |

## 補足

- 可用性の目標（SLO）は、この単位で新しく置かない。配備先が開発者の PC 上のコンテナで、手元の監視を常に動かしていないため（`project.md` の学び）。対象DB の不調は既存の警報 `ms-biz-target-db`（`/api/tables/` の 503）で拾う（`observability-requirements.md`）。
- 参照先の読み取りは保存しないため、バックアップと戻しの新しい手順は要らない。戻しは前の版のイメージだけで済む（移行が無い）。

## 後の段に回すこと

- **NFR 設計**: 全体の上限 10 秒の置き方（定数か設定か）、`longest()` への入れ方、`TargetReadSession` を絞って作り直す置き場（機能設計で決めた `ReferenceLookup`）の確認。
- **Code Generation**: 上のテスト。
