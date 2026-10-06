# 監視の設計 — U7 app-frame-ui

U7 は画面だけの単位です。role-admin-ui と同じく、次のように扱います。

- 画面の側の監視（利用者のブラウザの誤りや時間を集める仕組み）は置きません。
- 画面が呼ぶ API は、group の Q1 A のダッシュボードの区画（navigation・role の道）で見ます。
- 画面の時間は、E2E の 160 の測りで記録します。
- 新しい指標・警報・ダッシュボードは足さず、`docker/monitoring/` に触れません。

## 出典

- 答え: `infrastructure-design-questions.md`（Q1: A、まとめの確認は Looks correct）
- 上流（どれも `aidlc/spaces/default/intents/261004-role-menu/construction/app-frame-ui/` の下）:
  - `nfr-design/performance-design.md`（1節・4節・5節・6節）
  - `nfr-design/security-design.md`（3節）
  - `nfr-requirements/performance-requirements.md`
  - ui の単位のため、`observability-design.md` は作られていない
- role-admin-ui の `monitoring-design.md`（1節の理由）、navigation・role の `monitoring-design.md`（5節の区画）
- 既存のもの（正とする。読むだけ）: `docker/monitoring/`、`frontend/scripts/check-bundle-size.mjs`

## 1. 画面の側の監視を置かない理由

role-admin-ui の `monitoring-design.md` 1節と同じです。

- 既存の画面に仕組みが無い。
- 集める先も通知の先も無い。
- 名前・氏名・トークンが混じりうる。

U7 の骨組みは画面全体に効くため、読み込みの失敗（4節の古いタブの遅延読み込みを含む）は利用者の画面の表示でしか分かりません。この点は、受け入れた制約として配備の段への引き継ぎ（`infrastructure-specification.md` 4節）で手順に入れます。配備先が決まり、利用者が増えたときに、画面の側の監視を入れるかを改めて決めます。

## 2. Metrics & KPIs（画面）

| Metric | Source | Threshold | Why it matters |
|---|---|---|---|
| 画面の時間（NFR2.5〜NFR2.7） | E2E の 160（案）の測りのテスト。画面の中の `performance.now()` の1つの時計 | 承認済みの目標（作業ロールの切り替え・開閉・置き場への移動）を 5 回の中央値で判定 | 目標を超えたら Build and Test が `Not Met` と値で記録し、承認の場で扱いを決める（`performance-design.md` 4.4） |
| 入口の JavaScript の量 | `frontendBundleSize`（`verify` の段 9、500KB で警告だけ）と、Vite の manifest | B9 の後、入口の静的な import の中に `features/tables`・`features/mypermissions`・`LogoutPage` の塊が無い（Q1: A）。前後の値は記録だけ | 骨組みが増えることは受け入れ、新しい画面が入口に入っていないことを確かめる（`cicd-pipeline.md` 6節） |
| 遅延読み込みの3つの塊の大きさ（gzip） | manifest | 記録だけ | 後の比べの基準の値 |
| 画面の移動ごとの要求の数 | 画面のテスト（`BusinessNavigationProvider.test.tsx` など） | 承認済みのきっかけのまとめのとおり | U5 の見積もり（毎秒 10 要求ほど）の前提（`performance-design.md` 1節・6節） |

## 3. 画面が呼ぶ API を見る所

| 見るもの | 置き場 |
|---|---|
| 業務のメニュー・置き場（`/api/me/navigation`・`/api/me/table-access`）の要求の数・p95・5xx | group が足すダッシュボードの区画の navigation の道（navigation の `monitoring-design.md` 5節） |
| 作業ロール（`/api/me/work-role`）・自分の権限（`/api/me/permissions/…`） | 同じ区画の role の道（role の `monitoring-design.md` 5節） |
| 5xx の割合・ERROR のログ・403 の件数 | 既存の警報 `ms-5xx-ratio`・`ms-error-logs`・`ms-forbidden`（どれも `uri` で絞らない全体の式） |

## 4. Alerts

足しません。

| Alert | Condition | Severity | Routes to |
|---|---|---|---|
| （足さない） | — | — | 画面の側の警報は置かない（1節） |

## 5. SLIs / SLOs

| SLI | SLO target | Measurement window |
|---|---|---|
| 画面の時間（2節） | 承認済みの目標。5 回の中央値で判定 | Build and Test の E2E の 160 の測りの1回（手元の1台・1つのブラウザ・既定の1組）。記録だけで統合は止めない |
| 画面が呼ぶ API の時間 | U4・U5 の SLO（p95 1 秒） | navigation・role の `monitoring-design.md` 3節（`Unverified` から始め、Performance Validation で判定） |

SLO の値は緩めません。手元の監視を常に動かしていない間、API の側の判定は `Unverified` のままです（`project.md` の学び）。

## 6. Logs & Tracing

| 項目 | 扱い |
|---|---|
| 画面のログ | コンソールに応答・要求の値を出さない。失敗は種類（`kind`・`status`・`code`）だけで扱う（`security-design.md` 3節。画面のテストで `console` を見張る） |
| ブラウザの保存 | `sessionStorage` の開閉の鍵だけ（項目の `id` の配列）。ログアウトと未ログインの知らせで消す |
| トレース | 画面からトレースは送らない。API の要求には既存のサーバー側のトレースIDが付く |
| E2E の報告 | 秘密の値と形を報告の部品が探し、見つかれば失敗にする。170 は書いた値を読み戻して揃わなければ失敗にする（`security-design.md` 6節） |

## 7. 上流との差

- 入口の量の判定を足しました（2節。中身は `infrastructure-specification.md` 6節）。
- それ以外に、承認済みの NFR 設計と違う作りはありません。
- 画面の側の監視を置かないことは、設計に書かれていない事項を、既存の画面と role-admin-ui と同じ扱いとして明記したものです。
