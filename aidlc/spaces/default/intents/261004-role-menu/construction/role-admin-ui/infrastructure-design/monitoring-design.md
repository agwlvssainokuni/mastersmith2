# 監視の設計 — U6 role-admin-ui

U6 は画面だけの単位です。画面の側の監視（利用者のブラウザの誤りや時間を集める仕組み）は置きません。画面が呼ぶ API は、group・role の監視（ダッシュボードの区画、group の Q1 A）で見ます。画面の時間は、手元の1台・1つのブラウザの測りで記録します。新しい指標・警報・ダッシュボードは足さず、`docker/monitoring/` に触れません。

## 出典

- 答え: `infrastructure-design-questions.md`（質問なし、設計の要点の 5・6、まとめの確認は Looks correct）
- 上流（どれも `aidlc/spaces/default/intents/261004-role-menu/construction/role-admin-ui/` の下）:
  - `nfr-design/performance-design.md`（1節・4節・5節・7節）
  - `nfr-design/security-design.md`（4節）
  - `nfr-requirements/performance-requirements.md`
  - ui の単位のため、`observability-design.md` は作られていない。
- group・role の `monitoring-design.md`（5節の区画）
- 既存のもの（正とする。読むだけ）: `docker/monitoring/`、`frontend/scripts/check-bundle-size.mjs`

## 1. 画面の側の監視を置かない理由

| 観点 | 判断 |
|---|---|
| 今までの画面との一致 | 既存の画面（ログイン・利用者の管理・DSL など）にも、ブラウザの誤りや時間を集める仕組みは無い。U6 だけに置くと、仕組みと置き場（集める先・保存・個人に関する値の扱い）を新しく決めることになり、この Intent の範囲を超える |
| 配備先 | 手元の1台のコンテナで、画面を使うのは依頼者だけ。集める先も通知の先も無い |
| 秘密と個人に関する値 | 画面の誤りの報告には、名前・氏名・メールアドレス・YAML の本文が混じりうる。画面は値をコンソール・ブラウザの保存・URL に出さない決まり（`security-design.md` 4節）で、外へ送る仕組みは足さない |
| 代わりの見方 | 画面の失敗の多くは API の応答（4xx・5xx）として現れ、サーバーの指標とログで見える（3節）。画面の時間は E2E の測りで記録する（2節） |

配備先が決まり、利用者が増えたときに、画面の側の監視を入れるかを改めて決めます（後の Intent）。

## 2. Metrics & KPIs（画面）

| Metric | Source | Threshold | Why it matters |
|---|---|---|---|
| 画面の時間（NFR2.5〜NFR2.8・NFR2.12） | E2E の 140（案）の測りのテスト。画面の中の `performance.now()` の1つの時計 | 木の選び 1 秒、保存 1 秒、確かめの表示 3 秒、次のページ 0.5 秒（5 回の中央値）。1,000 カラムの表と1回の Select は記録だけ | 承認済みの目標。目標を超えたら Build and Test が `Not Met` と値で記録し、承認の場で扱いを決める（`performance-design.md` 4.4） |
| 入口の JavaScript の量 | `frontendBundleSize`（`verify` の段 9、500KB を超えたら警告だけ） | B8 の前と比べて増えていない | 新しい画面が入口に入っていないこと（NFR2.13、`performance-design.md` 5節） |
| 遅延読み込みの3つの塊の大きさ（gzip） | `dist/.vite/manifest.json` | 記録だけ | 後の比べの基準の値 |
| 確かめの表示の時のページのヒープ | 140 の測りのテスト（使えるブラウザで） | 記録だけ | 大きな応答と確かめた本文を画面に持つ受け入れた制約の大きさ（`performance-design.md` 7節） |

## 3. 画面が呼ぶ API を見る所

| 見るもの | 置き場 |
|---|---|
| ロール・権限・割り当て・受け渡しの API の要求の数・p95・5xx | group が足すダッシュボードの区画「ロール・グループ・メニュー（Intent 261004-role-menu）」の role の道（role の `monitoring-design.md` 5節） |
| グループの API の要求の数・p95・5xx | 同じ区画の group の道（group の `monitoring-design.md` 5節） |
| 利用者のロールの読み取り（`/api/admin/users/{userId}/roles`） | 同じ区画の role の道 |
| 5xx の割合・ERROR のログ・403 の件数 | 既存の警報 `ms-5xx-ratio`・`ms-error-logs`・`ms-forbidden`（どれも `uri` で絞らない全体の式） |
| `ROLE_BUSY`・`GROUP_BUSY` の多さ | role・group の WARN のログの問い合わせ（各 `monitoring-design.md` 1節） |

## 4. Alerts

足しません。画面の失敗は、3節の既存の警報が API の側で拾える範囲で拾います。

| Alert | Condition | Severity | Routes to |
|---|---|---|---|
| （足さない） | — | — | 画面の側の警報は置かない（1節） |

## 5. SLIs / SLOs

| SLI | SLO target | Measurement window |
|---|---|---|
| 画面の時間（2節の4項目） | 承認済みの目標（1 秒・1 秒・3 秒・0.5 秒）。5 回の中央値で判定 | Build and Test の E2E の 140 の測りの1回（手元の1台・1つのブラウザ）。記録だけで統合は止めない（NFR2.9） |
| 画面が呼ぶ API の時間 | U3・U4 の SLO（p95 1 秒、確かめ 15 秒・適用 30 秒） | group・role の `monitoring-design.md` 3節（`Unverified` から始め、Performance Validation で判定） |

SLO の値は緩めません。手元の監視を常に動かしていない間は、API の側の判定は `Unverified` のままです（`project.md` の学び）。

## 6. Logs & Tracing

| 項目 | 扱い |
|---|---|
| 画面のログ | コンソールに応答の値・検索の文字・YAML の本文・指紋を出さない（`security-design.md` 4節。画面のテストで spy を使って確かめる） |
| トレース | 画面からトレースは送らない。API の要求には既存のサーバー側のトレースIDが付く |
| E2E の報告 | 秘密の値と形を報告の部品（`playwright-secret-check-reporter.ts`）が探し、見つかれば失敗にする（`security-design.md` 5節） |

## 7. 上流との差

承認済みの NFR 設計と違う作りはありません。画面の側の監視を置かないことは、承認済みの設計に書かれていない事項を、既存の画面と同じ扱いとして明記したものです。
