# 危険と順序の根拠 — timezone-ui-polish

Bolt は、1つ以上の作る単位（Unit of Work）を、設計から統合まで通して動くものにする1回の作り込みのこと。この文書は、Bolt を B1〜B6 の順に並べた理由を書く。

出典: Bolt の計画 `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/delivery-planning/bolt-plan.md`、この段の答え `delivery-planning-questions.md`（Q1: A 危険を先に）、単位の依存 `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/units-generation/unit-of-work-dependency.md`、設計の決定 `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/domain-design/decisions.md`（ADR-009）。

## 使った考え方

危険を先にする（risk-first）: 分からないこと・後で手戻りが大きいことを先に確かめ、待ち時間のあるものを最後に置く。点数による順位づけ（WSJF: 価値と緊急度とリスクの低減を大きさで割った点）は使わなかった。依存がほぼ決めてくれる順序で、候補の入れ替えが少ないため（前の Intent と同じ判断）。

## 危険の一覧

| # | 危険 | 影響する単位 | 見立て | 対応する順序 |
|---|---|---|---|---|
| 1 | PostgreSQL の接続のタイムゾーンを UTC に明示すると、時差を持たない型の値が変わるか（K-95。未検証） | U3・U4 | 変わらない見込みだが、動かすまで分からない | B1 を先頭にする。結果が U4 の設計に効く |
| 2 | MySQL・MariaDB の秒の端数の上限が 0 になるか、桁つきの時差つきの型が誤って扱われるか（K-98。未検証） | U3・U4 | 不具合の見込み | B1 で確かめ、直す。U4 の往復の受け入れ基準に効く |
| 3 | `timetz` の応答の変更（UTC への正規化をやめる）が、前の版の画面に影響するか | U3 | 時差つきの文字として読めるため壊れない見込み | B1 で結合テストと前の版との互換の確認 |
| 4 | ブラウザの `Intl` による夏時間の判定が、環境で揺れるか | U1・U4 | ブラウザの IANA の版による違いが出うる | B2 で性質ベースのテストと単体テストを先に作る（U2・U4 の土台） |
| 5 | 「項目が無い」と `null` の区別を、今の `record` のままでは受けられない（契約 C3） | U2 | 3状態の型で受ける仕組みを足す | B3 で、本文に無い・`null`・名前・不正値のテストを必須にする |
| 6 | 上流（make-you-chic-ui）の取り込みの時期が分からず、承認済みのストーリーの Must（AC4.3.1・AC4.4.2）に影響する | U5 | 取り込みの時期は上流次第 | B6 を最後に置き、待ち時間を最も長く取る。取り込まれないときは依頼者が決める（Q3: A） |
| 7 | 管理の仮の画面を外すと、既存の E2E 030・090・130 と単体テストの書き換えが要る | U5 | 手戻りが大きい | B6 の中で行い、移す前と後の件数を記録する |
| 8 | E2E を統合の前に流す回数が多い（`frontend/index.html`・`frontend/public/`・`frontend/src` の本体、業務データ・`navigation` の本体に触れるため、ほぼすべての Bolt が当たる） | 全単位 | 1回の E2E に時間がかかる | Bolt ごとに計画し、CI の上限（`timeout-minutes: 60` の7割・約42分）に近づいたら依頼者に諮る（`team.md`） |

## 順序の根拠

1. **B1 U3 を先頭に置く**: 危険 1・2・3 は、動かして確かめるまで分からず、結果が U4 の設計（往復・端数の受け入れ基準）に効く。単位ごと（unit-major）に設計からコード生成まで通すため、B1 の確かめの結果を B4 の設計に使える（Q4: A）。
2. **B2 U1 を次に置く**: 純粋な関数で小さく、早く終わる。U2・U4 の土台で、危険 4 を早く確かめる。
3. **B3 U2 → B4 U4**: U2 は U1 に、U4 は U1・U2・U3 に頼るため、依存の順。利用者に最も効く日時の機能は B4 で揃い、N6 の代表の E2E を足す。
4. **B5 U6 → B6 U5**: アイコン素材（U6）を先に作り、メニュー・アイコン・ホーム（U5）を最後に置く。U5 は上流の取り込みを待つ部分があり、時間を最も長く取れる。U5 の Bolt の直前に、取り込みの状態を確かめて依頼者に諮る。

## 価値の面からの確かめ

利用者への価値の大きい順は、おおむね 業務データの日時（U4 と、その土台の U1・U2・U3）→ ホーム（U5）→ アイコン（U6・U5）。この並びは、危険を先にした順（B1〜B4 が日時、B5・B6 がアイコンとホーム）と同じ向きで、価値を後回しにしていない。

## トポロジー順からの逸脱

なし。依存の図（U2→U1、U4→U1・U2・U3、U5→U6）のトポロジー順に従う。

## 考えた別の順序（採らなかったもの）

- 価値を先にする（Q1 の B）: 日時（U1→U2→U3→U4）を先にし、U3 の確かめを U4 の直前にする。U3 の結果が U4 の設計に効くため、手戻りの危険が大きい。
- 薄い通しを先にする（Q1 の C）: 設定の保存と業務データ1画面の表示を先に作る。アプリの骨格は既にあり、薄い通しで確かめる新しい統合の経路が少ない。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/delivery-planning/delivery-planning-questions.md`
- `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/units-generation/unit-of-work-dependency.md`
- `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/domain-design/decisions.md`

## Assumptions & Open Questions

- [assumption] 危険 1・2・3 の見立て（変わらない・不具合・壊れない）は未検証。B1 で確かめ、外れたら B4 以降の計画を見直す。
- 上流の取り込みの時期は、外部の依存の地図（`external-dependency-map.md`）に書く。
