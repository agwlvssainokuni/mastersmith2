# 配備の流れ（CI/CD）— u1-time-zone-conversion

出典: NFR Design（`construction/u1-time-zone-conversion/nfr-design/security-design.md`・`logical-components.md`）、機能設計（`functional-design/functional-spec.md`）、部品の一覧（`inception/domain-design/components.md`）、契約（`inception/contract-design/contract-summary.md`）、`team.md`（Way of Working・Testing Posture・Deployment）、`project.md`（Deployment）。

U1 は、画面の側の純粋な関数群（種類 library）。独立した配備の対象ではなく、フロントエンドのビルド（Vite）に入り、実行可能 WAR に同梱される（埋め込み）。サーバー・対象DB・内部DB・ネットワーク・コンテナ・クラウドの資源を持たないため、インフラの仕様（`infrastructure-specification.md`）と監視の設計（`monitoring-design.md`）は、種類 library の決まりで作らない（`cicd-pipeline.md` と `traceability.json` だけを作る）。配備先は開発者の PC 上のコンテナまでで、クラウドの基盤は作らない（`project.md` Deployment）。新しい設計の論点が無いため、質問は作らず、既にある流れの記録として書く（`project.md`: CI の仕組みが既に実装されている段は、既にあるものの記録として書く）。

## 1. 流れの中の位置

| 段 | 内容 | U1 で変わること |
|---|---|---|
| 作業ブランチ | `develop` から短命のブランチ（`feature/261102-timezone-ui-polish-<bolt>`）を作って作業する | なし |
| 統合前の1コマンドの検査 | `./gradlew verify`（フォーマット → リンタ → ライセンスヘッダー → ビルド（型検査を含む）→ 単体テスト → 結合テスト → カバレッジ下限 → 秘密情報の検出・静的解析・依存関係の脆弱性検査） | U1 のソースとテストが、フロントエンドの検査（Prettier・oxlint・ESLint・tsc・ライセンスヘッダー・Vitest・カバレッジ）に入る |
| E2E | `./gradlew e2eTest`（`verify` と CI の外） | `frontend/src` の本体（テストを除く）に触れるため、統合の前に流す（`team.md` Way of Working）。U1 は画面を持たず E2E の流れは足さない。既存の E2E が通ることを確かめる |
| 統合 | `develop` へ squash マージ（1 Bolt が1コミット。統合コミットのメッセージは日本語） | なし |
| CI | GitHub Actions が `develop` へのプッシュ時に、`verify` と同じ検査を流す。`origin` への `git push` は依頼者自身が行う | なし |
| 成果物 | フロントエンドのビルド結果（`dist`）を同梱した実行可能 WAR。版はコミットのハッシュで識別 | U1 は `dist` の中の JavaScript に入る。単体では配備しない |
| 配備 | 開発者の PC 上のコンテナ。配備のたびに起動確認（ヘルスチェック）とスモークテスト | U1 単体の確かめは、U2・U4 が使う画面（Deployment Execution）で行う |

## 2. 検査の関門と、U1 の確かめの対応

| 関門 | U1 の確かめ | 根拠 |
|---|---|---|
| フォーマット・リンタ（Prettier・oxlint・ESLint） | ソースが検査を通る。ESLint の上書きの設定（`frontend/src/shared/format/` の U1 のファイルに、通信・保存・DOM・`console` の使用を禁じる）を足す。既存の `formatDateTime.ts` は対象に入れない | SD3.2・SD6.2 |
| ライセンスヘッダー（`frontend/scripts/check-license-header.mjs`） | U1 のソースの先頭に Apache License 2.0 の `/* ... */` のヘッダー（2026、`agwlvssainokuni`） | SD6.2 |
| ビルド（型検査・Vite） | `strict`・`any` の禁止・`export default` の禁止・`enum` の禁止。公開の入口は名前付きのエクスポート | SD6.2 |
| 単体テスト（Vitest・jsdom） | 日付の変わり目・夏時間・端数・往復の性質ベース（`fast-check`。既に入っている依存）・異常な入力・キャッシュの有無・`console` が 0 回・整形器の生成回数 | SD7.1〜SD7.7・NFR5.1.1・NFR2.1.3 |
| 性能の測り（Vitest の単体テストの中） | 1万の値の変換が 500 ミリ秒以内・平均 0.05 ミリ秒以内。測った環境（Node の版・CPU）を成果物に書く | NFR2.1.1・NFR2.1.2 |
| カバレッジ下限 | フロントエンドは全体の合計に行 80%・分岐 70%（今の形のまま）。U1 のフォルダの値は Build and Test で記録する | `team.md` Testing Posture |
| 依存関係の脆弱性（OSV-Scanner） | 新しい依存を足さない。`package.json`・lockfile を変えない | SD6.1・SD7.8 |
| 秘密情報の検出（Gitleaks） | U1 は秘密を扱わない。テストデータに実在の値を置かない | `project.md` Forbidden |

## 3. 戻し方と、配備の安全

- **戻し方**: U1 は WAR に同梱されるため、直前の版の WAR（またはコンテナのイメージ）での再配備で戻す（`team.md` Deployment）。スキーマ・データの変更が無い（U1 は保存するものを持たない）ため、前進のみの決まり・内部DB の複写は要らない。
- **段階的な切り替え**: U1 は呼ぶ側（U2・U4）が使い始めるまで、画面には影響しない（関数を足すだけ。既存の `formatDateTime.ts` のふるまいは変えない）。
- **スモークテスト**: 配備の後に、U2・U4 の画面で日時が表示のタイムゾーンで出ることを確かめる。Firefox・Safari の確かめは未確認（Unverified）で、持ち主は Deployment Execution のスモークテスト（NFR Design の Q3: A）。

## 4. 秘密情報の扱い

U1 は秘密情報を扱わず、環境変数・資格情報を持たない。CI の秘密情報の扱いは、既存の流れのまま変えない（`team.md` Deployment）。

## 5. 要注意の点（NFR Design のレビューから）

- **性能の測りの不安定さ**: NFR2.1.1（500 ミリ秒）を jsdom の単体テストの実時間で判定するため、負荷の高い CI で不安定になりうる（レビューの助言）。目標は緩めず、Code Generation の計画で、複数回の測りの中央値で判定し、失敗時は測った値と環境を出す形にする。落ちたときは `team.md` の「不安定なテストと CI の失敗」の決まりに従う。
- **実時間の測りの置き場**: 測りは `./gradlew verify` の単体テストに入る（CI の上限 `timeout-minutes: 60` の7割（約 42 分）に影響する量ではない見込み。Build and Test で `verify` と CI の時間を記録する）。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- NFR Design: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u1-time-zone-conversion/nfr-design/security-design.md`・`logical-components.md`
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u1-time-zone-conversion/functional-design/functional-spec.md`
- 部品の一覧: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/domain-design/components.md`
- 契約: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/contract-design/contract-summary.md`

## Assumptions & Open Questions

- [assumption] U1 の単体テストの追加は、`./gradlew verify` の時間を数十秒以内しか増やさない見立て（性質ベースのテストの回数を抑える）。実際の値は Build and Test で測る。
- 未確認（Unverified）: Firefox・Safari での動作（Deployment Execution のスモークテストで確かめる）。
- 未解決の点は無い。
