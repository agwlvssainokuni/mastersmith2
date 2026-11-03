# 監視の設計 — u6-app-icon-assets

出典: NFR Design（`construction/u6-app-icon-assets/nfr-design/security-design.md`）、`project.md`（Deployment: 手元の監視、警報は新しく足す理由があるときだけ）。

U6 は静的な素材の単位で、新しい経路・状態・負荷を持たない。実行時に監視する新しいものは無いため、新しい指標・警報・ダッシュボードは足さない。素材の欠けと不正は、実行時の監視ではなく、統合の前の検査（`cicd-pipeline.md` 2節）と配備の後のスモークテスト（同 3.2）で止める。監視の「見えない」点は下の表に残す。

## Metrics & KPIs

| Metric | Source | Threshold | Why it matters |
|---|---|---|---|
| 新しい指標 | なし | なし | 静的なファイルの配信は、既存のアプリの HTTP の指標（`http.server.requests`）に含まれる。専用の指標は置かない |
| 素材の大きさ | ビルドの検査（Vitest の素材のテスト） | SVG 4 KiB・`.ico` 32 KiB・`.png` 16 KiB 以内 | 肥大を防ぐ。実行時でなく統合の前に守る |

## Alerts

| Alert | Condition | Severity | Routes to |
|---|---|---|---|
| 新しい警報 | なし | なし | 配備先が決まるまで、通知の先も作らない（`project.md`） |

素材の欠け（`/favicon.ico` が HTML を 200 で返す）は、状態コードが 200 のため既存の 5xx の警報（`ms-5xx-ratio` など）では拾えない。実行時の警報で拾えないことを理由に、検査の関門（`dist:check`・`verifyFaviconInWar`・E2E）を置いている。

## SLIs / SLOs

| SLI | SLO target | Measurement window |
|---|---|---|
| 素材の可用性 | 新しい SLO は置かない（アプリ全体のヘルスチェックの SLO に含まれる） | 配備のたび（スモークテスト S1〜S4） |

## Logs & Tracing

- ログ: 変更なし。素材の要求は静的なファイルの配信で、アプリのログに新しい項目を足さない。素材の中身・パス以外の値をログに出さない。
- トレース: 変更なし。静的なファイルの要求は既存の HTTP の計測に含まれる。外へ送る設定は既定で無効のまま。
- ダッシュボード: 新しい区画を足さない。
- 見えない点: ブラウザの favicon の表示は、サーバーから観測できない。依頼者が実際の画面で確かめる（スモークテスト S5。Unverified、持ち主: Deployment Execution）。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- この単位には functional-spec・rules が無い（素材と検査だけの単位）
- NFR Design: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u6-app-icon-assets/nfr-design/security-design.md`（観測の独立した文書は、新しい経路が無いため作っていない）
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] 素材の配信は既存の HTTP の指標に含まれ、専用の指標が無くても運用に困らない。
- 未解決の点は無い。
