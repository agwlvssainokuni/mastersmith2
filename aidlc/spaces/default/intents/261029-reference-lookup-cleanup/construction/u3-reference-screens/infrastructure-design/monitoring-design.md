# 監視の設計 — U3 u3-reference-screens

U3 は画面だけの単位で、画面の側には監視を足さない（`infrastructure-design-questions.md` の「決まっていること」）。画面は `console` に出さず（`security-design.md` 1.2）、ブラウザから外へ送る仕組み（画面の誤りの収集・RUM）も持たない。画面の失敗は、画面が呼んだ API の状態コードとしてサーバー側に現れるため、U1 の基盤の設計で足すパネル（id 46・47）と既存の警報で見る。この文書は、その対応を表にした記録である。

文章で ID を指すときは、単位の接頭辞を付ける（例 `U3-NFR2.42`）。

## 1. Metrics & KPIs

画面の側の指標は足さない。U3 の振る舞いが現れるサーバー側の指標を、U1 の `monitoring-design.md` から引く（式はそちらが持ち主）。

| Metric | Source | Threshold | Why it matters |
|---|---|---|---|
| 参照の API の要求の数（道・方法・状態コードごと） | U1 の新しいパネル id 46（`http_server_requests_milliseconds_count`、`uri=~"/api/tables/(definition\|reference/rows)"`） | なし（量を見る） | U3 の画面の要求の量（作成・更新の画面を開くたびの定義の取得1回、ラベルの1件読み、ピッカーの検索）がここに入る。ラベルの1件読みとピッカーの検索は同じ道のため指標では分けられない（U1 の決め） |
| 参照の API の 503 の数 | U1 の新しいパネル id 47 | 1 件でも（警報は既存の `ms-biz-target-db`） | ピッカーの「検索できません」・ラベルが値だけになる・作成と更新の画面が開けない、の原因になる |
| 参照の API の 4xx（400・403）の数 | U1 のパネル id 46 の `status` の内訳 | なし（調べるときに見る） | 403 は権限を欠く利用者の検索（画面は1つの文）・ラベルの1件読み（画面は値だけ）。400 は画面の確かめを通った後のサーバーの拒否で、平常では少ないはず |
| 作成・更新の 409 の数 | 既存の業務データの区画（`uri=~"/api/tables/.*"` の要求の数の状態コードの内訳） | なし | 保存の拒否（U2。問題の code は `BUSINESS_REFERENCE_NOT_FOUND`、項目の誤りの `reason` は `REFERENCE_NOT_FOUND`）。code は指標のラベルに無いため、見分けは監査とログで行う（3節） |
| 初回の JavaScript（入口）と業務データの画面の chunk の大きさ | ビルドの時点（`check-bundle-size.mjs`、一時の手順の manifest の測り。`infrastructure-specification.md` 4節） | 入口は 500 KB で警告（既存）。chunk は線なし | 実行の時点の監視ではなく、ビルドの記録で見る（U3-NFR2.44） |

## 2. Alerts

新しい警報は足さない。

| Alert | Condition | Severity | Routes to |
|---|---|---|---|
| `ms-biz-target-db`（既存・変えない） | 業務データの API（`uri=~"/api/tables/.*"`）の 503 が 5 分に1件以上、`for: 0s` | 中 | 手元の Grafana の警報の一覧だけ（通知の先は配備先が決まるまで置かない）。U3 の画面が呼ぶ参照の道と定義の道の 503 もこの式に入る（U1 の 3節） |
| `ms-5xx-ratio`・`ms-error-logs`・`ms-pool-pending`（既存・変えない） | 既存のまま | 既存のまま | 同上 |

画面の失敗のうち 4xx（400・403・409）は警報にしない。利用者の操作と権限による想定内の拒否で、画面は決めた文を出して入力を保つ（`logical-components.md` 2節）。

## 3. 画面の失敗がサーバー側でどう見えるか

| 画面で起きること | サーバー側の道・状態コード | 見る場所 | 監査に残るか |
|---|---|---|---|
| 作成・更新の画面が開けない（定義の読み込みの失敗） | `POST /api/tables/definition` の 503 | U1 のパネル id 47、警報 `ms-biz-target-db`、ログの `ReferenceLookup` の WARN（`reason`） | 残らない（読み取り） |
| ラベルが値だけになる（ラベルの1件読みの失敗） | `POST /api/tables/reference/rows` の 403・503 | パネル id 46・47 | 残らない（参照の読み取りは監査に残さない。U1 の BR1.7） |
| ピッカーの「検索できません」 | `POST /api/tables/reference/rows` の 403・503・400 | パネル id 46・47 | 残らない |
| 保存の「選べない値です」 | `POST /api/tables/rows`・`PATCH /api/tables/rows` の 409（問題の code `BUSINESS_REFERENCE_NOT_FOUND`、項目の誤りの `reason` `REFERENCE_NOT_FOUND`） | 業務データの区画の状態コードの内訳と、監査の行（U2 の契約 C4） | 残る（U2 の決め。監査の見分けは Incident Response の手順書に合わせる） |
| 画面の写しの失敗（応答の形の崩れ）・ブラウザの中の例外 | サーバー側には現れない（応答は 200） | 見えない。画面は通信の失敗か入力不可に倒す（`security-design.md` 1.5） | 残らない |
| 画面の側の待ち（同時 3 本の列） | 要求の数が短い間に3本ずつ並ぶだけ | パネル id 46 の量 | 残らない |

画面の写しの失敗とブラウザの中の例外は、受け入れた見えない範囲とする。確かめは画面の部品のテスト（写しの単体テスト、知らない値の倒し方）と E2E の `shapeCompare`（差し替えの見本と本物の応答の形の比べ）で行う。

## 4. SLIs / SLOs

画面の側の SLO は置かない（NFR 要件の Q1: A、`performance-design.md` 1節で画面の時間の目標を置かない）。参照の API の SLO の目安と判定は U1 の `monitoring-design.md` 4節のとおりで、手元の監視を常に動かしていない間は `Unverified` とする（`project.md` の学び）。

| SLI | SLO target | Measurement window |
|---|---|---|
| （画面の側）待つ間と答えの後の表示の移り変わり | 時間の目標なし。決めた移り変わりを画面の部品のテストで確かめる | Code Generation・Build and Test のテストの実行ごと |
| （参照の API）U1 の4つの SLI | U1 の 4節のまま | U1 の 4節のまま |
| E2E の全体の時間 | 変更の前の 1.5 倍を超えたら依頼者に諮る（上限で落とさない。U3-NFR2.45） | Code Generation の最初の実行（前）と、U3 の Bolt の後・Build and Test |

## 5. Logs & Tracing

| 項目 | 決めたこと |
|---|---|
| 画面のログ | 出さない（`console` を使わない。既存の lint の決まりのまま）。ブラウザから外へ送らない |
| サーバーのログ | U1 の診断の WARN（ロガー `ReferenceLookup`、キー `purpose=REFERENCE`・`reason` など）と既存の `GlobalExceptionHandler` の変換の WARN。行の値・ラベル・名前は出さない（U1 の 5節） |
| トレース | 足さない。道は固定で値は本文で運ぶため、トレースの道の属性に参照先の値は入らない（U3-NFR1.43、契約 C2） |
| ダッシュボード | 足さない。U1 のパネル id 46・47 と既存の業務データの区画を使う |
| E2E の報告 | 監視ではないが、行の値と資格情報を残さない仕組み（`playwright-secret-check-reporter.ts`、`recordSecretValues` の `businessValue`）を 190・195 にも当てる（U3-NFR1.47） |

## 上流との差

| 上流 | 差 | 扱い |
|---|---|---|
| 無し | U3 の NFR 設計には監視の文書が無い（種類 `ui`）。この文書は U1 の監視の設計の参照と、画面の失敗の現れ方の記録だけ | 新しい決めは無い |

## 後の段に回すこと

- **Observability Setup**: U1 のパネル id 46・47 を確かめるときに、画面からの操作（作成・更新の画面を開く・ピッカーで検索する）でも系列が入ることを1回見る（任意。要求は待ちを入れた少ない数で送る）。
- **Incident Response**: 利用者からの「選べない」「検索できない」の問い合わせを、3節の表で道と状態コードに結び付ける手順を、U1・U2 の見分け方と合わせて書く。

## 承認の場の決定

依頼者は Infrastructure Design の承認の場で Request Changes を選び、レビュー（`reviews/review-01.md`）の指摘 R-01〜R-04 の直し方をすべてそのまま当てると決めた。この文書には R-04 を当て、1節と3節で問題の code `BUSINESS_REFERENCE_NOT_FOUND`（応答の `code`）と項目の誤りの `reason` `REFERENCE_NOT_FOUND` を分けて書いた。

## Sources

- この段の質問（質問なし・決まっていること）: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/infrastructure-design/infrastructure-design-questions.md`
- U1 の監視の設計（統合の先、読み取りだけ）: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/infrastructure-design/monitoring-design.md`（1節〜5節、パネル id 46・47、警報 `ms-biz-target-db`）
- NFR 設計: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/nfr-design/performance-design.md`（1節・2節・5節）・`security-design.md`（1.2・1.5・1.6・1.7）・`logical-components.md`（2節）
- 機能設計: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u3-reference-screens/functional-design/functional-spec.md`（D18・D27・D31）・`frontend-components.md`（3.3）
- 部品: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/domain-design/components.md`（BusinessDataScreens）
- 契約: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/contract-design/contract-summary.md`（C2・C3・C4）
- 決まり: `aidlc/spaces/default/memory/project.md`（Deployment の学び: 手元の監視・SLO の `Unverified`・監視の式の確かめ方）

## Assumptions & Open Questions

- [assumption] 作成・更新の 409 は、既存の業務データの区画の状態コードの内訳で見える（U1 の文書が挙げる id 40〜44 の式が `uri=~"/api/tables/.*"` で状態コードを持つため）。Observability Setup で式を流して確かめる。
