# 技術の選択 — u4-business-data-time

出典: 機能設計（`construction/u4-business-data-time/functional-design/functional-spec.md`・`frontend-components.md`）、契約（`inception/contract-design/contract-summary.md` の C1・C2・C4）、コード知識ベースの技術の構成（`codekb/mastersmith2/technology-stack.md`）、U1・U2・U5 の技術の選択、`team.md` の Code Style、質問と答え（`nfr-requirements-questions.md`: Q1: A）。

## 結論

新しい言語・フレームワーク・依存は足さない。画面は今の React + TypeScript と make-you-chic-ui の部品、変換は U1 の関数（`Intl` の上の純粋な関数）、サーバーは今の Spring Boot の `businessdata` の応答の record に1項目を足すだけ。テストも今の道具で行う。

## 選択の一覧

| 対象 | 選択 | 理由 |
|---|---|---|
| 時刻の変換 | U1 の `formatInstant`・`parseLocalDateTime`・`compareInstants`・`preserveInstant`（`frontend/src/shared/format/timeZone/`）と、`frontend/src/shared/format/` に足す薄い関数（入力の初めの値・時差を持たない日時の送る形） | 変換を画面の側の1か所に閉じる（FR4.2・ADR-003・`team.md`）。ブラウザの既定のタイムゾーンを暗に使わない |
| 表示のタイムゾーン | U2 の `useDisplayTimeZone`（`frontend/src/app/display-settings/`）と `DisplayTimeZoneErrorBar` | 配布は U2 の持ち物（契約 C2）。読み込みの失敗の帯は管理の画面と同じ部品を使う |
| 入力の部品 | make-you-chic-ui の `TextInput`・`FormField`・`RadioGroup`（今の固定先） | 画面イメージの対応（`design-system-mapping.md`）。ブラウザの `datetime-local` は、選んだタイムゾーンと食い違い・秒の端数を入れられないため使わない（Q2: A） |
| 案内の1行のリンク | react-router の `Link` と、プリファレンスの登録の道の定数 | 部品は不要（`design-system-mapping.md`） |
| サーバーの応答 | `ReferenceFieldResponse` の record に `labelValueType`（既存の `ValueType` の列挙）を足し、Jackson の `NON_NULL` で `AVAILABLE` でないときは出さない（今の `options`・`picker` と同じ） | 足すだけの変更（NFR4.2.1）。新しい型・依存を作らない |
| 画面のテスト | Vitest・Testing Library（jsdom）・user-event・vitest-axe・fast-check | `team.md` の Testing Posture |
| サーバーのテスト | JUnit・既存の結合テストの部品（`*ConnectionUsageIT` の形） | 今のまま |
| E2E | Playwright（`./gradlew e2eTest`）と、今の axe の部品（`frontend/e2e/support/axe.ts`） | `team.md`。流れは1本（Q1: A） |
| 負荷の試験 | 既存の k6 の業務データの場面を流し直すだけ（新しい場面は足さない） | サーバーの読み書き・問い合わせを変えないため（NFR2.1.2） |

## 採らなかった選択（Alternatives Rejected）

| 選択 | 採らなかった理由 |
|---|---|
| 日時のライブラリ（Temporal の polyfill・date-fns-tz・Luxon など）を足す | U1 が `Intl` だけで変換と夏時間の判定を持ち、U1 の性質ベースのテストと性能の測りで確かめ済み。依存とライセンスの確かめが増えるだけ |
| ブラウザの `datetime-local` を使い続ける | 選んだ表示のタイムゾーンでなくブラウザの壁時計として扱われ、秒の端数を入れられない（Q2: A） |
| 日時の選び方の部品（カレンダーのポップアップ）を自前で作る | make-you-chic-ui に無く、上流への依頼と取り込みの待ちが増える。画面イメージは文字の入力に決めている（Q3 A） |
| サーバーで表示のタイムゾーンに直して返す | ADR-003 で採らなかった（応答の形・往復・性能に影響） |

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/functional-design/functional-spec.md`・`frontend-components.md`
- 契約・決定: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/contract-design/contract-summary.md`・`inception/domain-design/decisions.md`（ADR-002・ADR-003）
- 画面イメージ: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/refined-mockups/design-system-mapping.md`
- コード知識ベース: `aidlc/spaces/default/codekb/mastersmith2/technology-stack.md`
- 質問と答え: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/nfr-requirements/nfr-requirements-questions.md`（[Q1] A）
- 進め方: `aidlc/spaces/default/memory/team.md`

## Assumptions & Open Questions

- [assumption] make-you-chic-ui の今の固定先の `RadioGroup` が legend と `disabled` を受け、`TextInput` が `aria-describedby` を `FormField` 経由で結ぶ前提（今のフォームが同じ使い方をしている）。口の細部は、コード生成の前に部品のソースで確かめる（`project.md` の決まり）。
