# Scalability Design — U4 監査ログ（u4-audit-log）

U4 の拡張性の要件（`scalability-requirements.md` の NFR1.4〜NFR1.6）を満たす設計。前提は Domain Design の ADR-008（当面はアプリを1台で動かす）。処理の流れは U4 の `functional-spec.md`、技術は U4 の `tech-stack-decisions.md` に従う。性能・安全・信頼性・観測性の要件（`performance-requirements.md`・`security-requirements.md`・`reliability-requirements.md`・`observability-requirements.md`）は各設計書で扱う。`contract-summary.md` は、本ワークフローで Contract Design を行わないため無い。

## 1. 量の見積もり（NFR1.4）

| 項目 | 見積もり | 出典 |
|---|---|---|
| 1日の件数 | 約 500 件（ログイン・ログアウト・ログインの失敗・アクセス拒否） | U1 の NFR1.9 |
| 1件の大きさ | 約 1KB（要求のパスを含め、切り詰めの上限の値でも 3KB 以内） | U1 の NFR1.9、U4 の決まり 2.1 |
| 1年の大きさ | 約 180MB（索引を含めても 250MB 程度） | U1 の NFR1.9 |
| 保存 | 無期限（削除しない） | NFR1.6 |

開発者の PC 上のコンテナのボリュームで、数年分を扱える。U1 の `nfr-design/scalability-design.md` の見直しの目安（見積もりの2倍を超えたら見直す）に従う。

## 2. 表と索引（NFR1.5）

| 対象 | 設計 |
|---|---|
| 主キー | DB の連番（挿入の前に読み取りをしない） |
| 索引 | 発生の日時に1つだけ付ける。後続Intentで監査ログを見る画面を作るときの、日時での絞り込みに備える |
| そのほかの索引 | 付けない（追記の費用を増やさない）。種類・メールアドレスでの絞り込みが要るようになったら、後続Intentで加える |

スキーマは Flyway の SQL のファイルで作り、前進のみ・後方互換を保つ。

## 3. 保存期間と複数台（NFR1.6）

- 保存期間と古い記録の扱い（要件定義の未解決の論点 OQ1）は後続Intentで決める。それまでは削除の処理を持たない（`security-design.md` 2章とも一致する）。
- U4 は状態をメモリに持たず、記録は内部DBに追記するだけのため、別サーバーの H2 に切り替えて複数台にしても、そのまま動く。連番の主キーは DB が振るため、台の間で重ならない。
