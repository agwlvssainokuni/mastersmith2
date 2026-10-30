# 信頼性の要件 — U2 u2-reference-write-check

出典: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/functional-design/functional-spec.md`・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/functional-design/rules.md`・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`

## 出典

- 要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`（NFR3・NFR6・NFR7、FR3.4）
- ストーリー `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/user-stories/stories.md`（AC3.1.6・AC3.1.8・AC3.5.3）
- この単位の機能設計（`rules.md` の BR1.4・BR4.1・BR4.4・BR4.5・BR6.1・BR6.2、`functional-spec.md` の 4.4節・9節）
- 契約 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/contract-design/contract-summary.md`（C3・C5）
- U1 の信頼性の要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-requirements/reliability-requirements.md`（`REFERENCE` の目的の問い合わせ1回の上限 5 秒と、定義の取得の全体の上限 10 秒の仕組み。継ぎ目だけを使う）
- この段の答え `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/nfr-requirements/nfr-requirements-questions.md`（Q2: A）

## ID の対応表

全体の表は `security-requirements.md` の「ID の振り方」にある。

| 上流の ID | この単位での扱い | この単位の ID |
|---|---|---|
| NFR3 接続 | 当たる（時間の上限と失敗の扱い） | NFR3.24〜NFR3.27 |
| NFR6 多言語 | 部分（誤りの code の決まり。文言は U3） | NFR6.21 |
| NFR7 互換性 | 当たる | NFR7.21・NFR7.22 |

## 要件

| ID | 要件 | 確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR3.24 | 保存前の段（⑩）の読み取り（更新前の行・件数・確かめ）は、U1 が足す `REFERENCE` の目的の接続で行い、問い合わせ1回の時間の上限は U1 と同じ既定 5 秒にする（設定と環境変数の置き場は U1 の受け持ち。U2 は新しい設定を足さない） | 応答しない対象DB（`ServerSocket`）で、保存前の確かめが 503 `BUSINESS_TARGET_DB_TIMEOUT` になり、行が保存されないことを確かめる結合テスト1件（AC3.5.3 の保存前の確かめの分、AC3.1.8） | Code Generation |
| NFR3.25 | 保存前の段の全体に時間の上限 10 秒を置く（Q2: A）。仕組みは U1 の定義の取得と同じ: 注入した時計で ⑩ の最初の読み取りの前に締め切りを決め、各問い合わせの前に残りを秒に切り上げ、1 秒未満なら流さず 503 `BUSINESS_TARGET_DB_TIMEOUT`（何も保存せず、書き込みの接続を借りない）、そうでなければ上限を min(`REFERENCE` の上限, 切り上げた残り) に絞って流す。切り上げのため1文が締め切りを最大 1 秒未満超えうるため、⑩ の最悪は約 11 秒（これに権限の写しの読み取りと接続を借りる待ちが前に乗る）。この幅は受け入れる。上限の値と仕組みの置き場（定数か設定か、U1 と共有する部品）は U1 と同じく NFR 設計で決める（既定 10 秒は変えない） | 注入した時計の単体テストで、残り 0.5 秒・1.0 秒・2.3 秒がそれぞれ「流さず 503」「上限 1 秒」「上限 min(5, 3) = 3 秒」になること。参照が複数の作成で、途中で締め切りに達したときに残りを読まず、書き込みの接続を借りずに 503 になり、行が増えない結合テスト | NFR 設計、Code Generation |
| NFR3.26 | ⑩ で対象DB の失敗が起きたら、その場で止め、以後の読み取りをせず、何も保存せず、書き込みの接続を借りない（BR4.5）。時間切れ・断絶では U1 の壊れた印を立てて接続を返す。写し方は既存と同じ（BR1.4・BR4.4）: 時間切れと全体の上限の超過は 503 `BUSINESS_TARGET_DB_TIMEOUT`、接続の失敗・読み取りの設定が無い・借りられないは 503 `BUSINESS_TARGET_DB_UNAVAILABLE`、型の食い違いは 409 `BUSINESS_TARGET_DB_MISMATCH`。503 は「保存されていない」ことが確かな失敗で、利用者はそのまま送り直せる | 失敗の種類ごとの結合テストで、行が増えない・変わらないことを別の接続で読み直し、書き込みの接続を借りた回数が 0 であること。3つの参照の2つ目で時間切れにしたとき、3つ目の問い合わせが流れないこと | Code Generation |
| NFR3.27 | 確かめの後・保存の前に参照先が消えたときは、既存の外部キー違反の変換を最後の守りにする（BR6.1）。書き込みの段（⑪）の失敗の扱い（時間切れ・確定の段の扱いを含む）は既存のまま変えない | `security-requirements.md` の NFR1.29 の待ち合わせの結合テスト。既存の書き込みの失敗のテストを変えずに通すこと | Code Generation |
| NFR6.21 | 新しい誤りの `code`・項目の誤りの理由・監査の理由は作らず、既存のものを使い回す（BR1.4）。1つの code に1つの状態コードを固定する決まり（`team.md`・NFR6）を崩さない。誤りの文言（日本語と英語）は画面（U3）が code と理由から出す | 既存の `BusinessDataProblemTypeCatalog` の一覧が変わらないこと（code の数と状態コードの組）を確かめる単体テスト | Code Generation |
| NFR7.21 | DSL の書式の版と内部DB のスキーマ（移行ファイル）を変えない（NFR7）。作成・更新の API の道と本文の形は変えず、拒否の応答だけが増える（契約 C3） | コード生成の差分に移行ファイルと DSL の JSON Schema の変更が無いこと | Code Generation |
| NFR7.22 | 参照の無い作成・更新、削除、一意の事前の問い合わせ・違反の変換は、今の振る舞いを変えない（BR6.2）。参照の値を送らない作成・更新は対象DB の読み取りの接続を借りない（BR1.2） | 既存の書き込みの結合テストと E2E を変えずに通すこと。既存の `BusinessDataConnectionUsageIT` の作成・更新・削除の数が変わらないこと | Code Generation、Build and Test |

## 補足

- 可用性の目標（SLO）は、この単位で新しく置かない。配備先が開発者の PC 上のコンテナで、手元の監視を常に動かしていないため（`project.md` の学び）。対象DB の不調は既存の警報 `ms-biz-target-db` で拾う（`observability-requirements.md`）。
- 対象DB の不調の間は、参照の値を入れる作成・更新が 503 になり保存できない。参照の値を入れない作成・更新は今のとおり書き込みの接続だけで動く。
- 保存前の確かめは読み取りだけで、新しく持つデータが無いため、バックアップと戻しの新しい手順は要らない。戻しは前の版のイメージだけで済む（移行が無い）。
