# NFR Requirements の質問（U4 u4-business-data-time）

対象の単位: U4 `u4-business-data-time`（種類 ui）。出典は機能設計（`construction/u4-business-data-time/functional-design/functional-spec.md` の D1〜D44・W5、`frontend-components.md`）、要件（`inception/requirements-analysis/requirements.md` の NFR1〜NFR6）、契約（`inception/contract-design/contract-summary.md` の C1・C2・C4）、コード知識ベースの技術の構成（`codekb/mastersmith2/technology-stack.md`）、U1・U2・U5 の NFR 要件、既存の負荷の試験の場面（`perf/k6/scenarios.js`）と E2E（`frontend/e2e/180`・`185`）。

## 決まっていること（質問にしない）

| 項目 | 決まっている内容 | 出どころ |
|---|---|---|
| 問い合わせの数 | 日時の変換は画面の側だけで、業務データの API の対象DB・内部DB の問い合わせを増やさない。定義の応答に足す `labelValueType` は DSL から決め、問い合わせを足さない | NFR2.1・D30・D42 |
| サーバーの性能の目標 | 業務データの API の目標（定義・一覧・詳細・参照の選択肢の p95 1 秒、`checks` の率 1 など）は変えない・緩めない。新しい k6 の場面は足さず、既存の場面（`bizDefinition` と参照の場面）を Build and Test で流し直す | NFR2.1・AC5.3.3・`perf/README.md` |
| 画面の変換の費用 | 1つの時刻の変換は U1 の目標（平均 0.05 ミリ秒、1 万個で 500 ミリ秒）の中。一覧の1ページ（最大 100 行 × 100 列）の時刻の整形の回数は、表示する時刻の数を超えない | U1 の NFR2.1.1・NFR2.1.2 |
| 漏えい | 業務データの値（主キーを含む）をログ・トレースの属性・エラー応答に出さない。`labelValueType` は型の名前だけで、値・ラベルを足さない。画面は値を文字として描く | `project.md` の Forbidden・`team.md` の業務データの必須のテスト |
| 依存 | 新しい依存を足さない（U1 の変換・make-you-chic-ui の `TextInput`・`RadioGroup`・`FormField`・既存の fast-check・vitest-axe・Playwright だけを使う） | `team.md` の Code Style |
| アクセシビリティ | 実際のブラウザの axe を、誤りの状態・2択を出した状態・長い名前で、ブランドカラーとテーマのすべての組について流し、違反 0 件。既存の `185-business-data-accessibility` に状態を足す（流れの本数に数えない） | NFR3.1・D44・`project.md` |
| E2E の本数 | N6 の日時の代表の流れを1本だけ足す。前のテストの状態に頼らず、流れが自分で作った利用者・ロール・専用のスキーマの行だけを使う。テストの時差は Playwright の `timezoneId` で固定する | NFR5.3・`team.md` |

## Q1. [テスト] N6 の日時の代表の E2E（1本）で、夏時間の地域と重なる時刻の2択まで通すか

文脈: 足せる E2E は1本だけです（`team.md`）。夏時間の存在しない時刻・重なる時刻の判定は U1 の単体テストと画面の部品のテスト（jsdom）で確かめますが、実際のブラウザの `Intl` で、2択の画面の操作と保存した時点が正しいかは E2E でしか確かめられません。今の E2E の見本の表（`e2e_item`）には日時のカラムが無いため、どちらでも、流れの専用のスキーマに時点を表す型のカラムを持つ表を足します（細部はコード生成の計画）。

**推奨: A。** 理由: 1本で「表示のタイムゾーンの保存 → 入力 → 時差つきの表示 → 往復」に加えて、この Intent でいちばん壊れやすい夏時間の扱い（存在しない時刻の誤り・重なる時刻の2択・選んだ側の時差での表示）を実際のブラウザで通せます。B は流れが短く安定しますが、夏時間の扱いは jsdom のテストだけになり、実際のブラウザの `Intl` との違いを拾えません。

- A. `America/New_York` を選び、存在しない時刻（`2026-03-08 02:30:00`）で誤りが出ることを確かめてから、重なる時刻（`2026-11-01 01:30:00`）を「後（-05:00）」で保存し、一覧で `-05:00` の時刻を見る。別の欄だけ直して更新しても日時が変わらないことを確かめる。
- B. `Asia/Tokyo` を選び、日付の変わり目（UTC の 15:00 が翌日の 0:00）の入力と表示、別の欄だけの更新の往復を確かめる（夏時間は通さない）。
- X. Other (please specify)

[Answer]: A

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u4-business-data-time/functional-design/functional-spec.md`・`frontend-components.md`
- 要件・契約: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/requirements-analysis/requirements.md`・`inception/contract-design/contract-summary.md`
- コード知識ベース: `aidlc/spaces/default/codekb/mastersmith2/technology-stack.md`
- 今のコード（読み取りだけ）: `perf/k6/scenarios.js`・`frontend/e2e/180-business-data-flow.e2e.ts`・`frontend/e2e/185-business-data-accessibility.e2e.ts`・`frontend/e2e/support/businessDataSchema.sql`・`frontend/src/shared/format/timeZone/conversionPerformance.test.ts`
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] 一覧の1ページの最大（100 行 × 100 列）は、既存の k6 の `bizDefinition` の幅の広い表（一覧の列 100）に合わせた見積もり。
