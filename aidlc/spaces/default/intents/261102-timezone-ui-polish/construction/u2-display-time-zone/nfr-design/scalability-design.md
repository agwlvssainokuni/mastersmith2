# 拡張の設計 — u2-display-time-zone

出典: NFR Requirements の `scalability-requirements.md`（NFR2.3.1〜NFR2.3.4）・`performance-requirements.md`、機能設計（`rules.md` の BR2.1・BR2.3・BR2.14・BR2.16）、同じ段の `performance-design.md`・`reliability-design.md`・`logical-components.md`、既存のコード（読み取りだけ: `UserPreferencesService`・`WorkRoleProvider`）。

この単位は新しい負荷の経路を作らない（既存の `/api/me/preferences` の項目を 1 つ足し、画面がログイン後に 1 回読むだけ）。そのため拡張の設計は、負荷の見込み・データの増え方・同時の重なり・複数のタブに絞る。**新しい拡張の仕組み（キャッシュ・非同期処理・キュー・分割）は足さない**。決定の番号は `SC<n>`。

## SC1. 負荷の見込み（NFR2.3.1）

- 追加の読み取りは **ログイン 1 回あたり 1 回** で、利用者数 × ログインの回数に比例するだけ。ポーリング・定期の更新を作らない。保存は利用者がプリファレンスの画面で保存したときだけ。
- 内部DB の接続は短い読み取りの 1 本（読み取り専用のトランザクション）。ログインの集中（多数の利用者が同時にログインする）で増える読み取りは、ログインと同数の短い読み取りで、プール（上限 30）の使い方は既存の「ログイン後に画面が読む」読み取りと同じ形。接続を 2 本使う経路（確定の後の監査の書き込みなど）は、この読み取りにも保存にも無い（`PD2`）。
- 今の `preferencesGet`・`preferencesSave` の負荷の形（同時 10 VU）を超える見込みは無い。確かめは性能の設計の `PD6`（負荷の測りは Deferred）。

## SC2. データの増え方（NFR2.3.2）

- 利用者の行に NULL 可の文字列の列を 1 つ（最大 64 文字）足すだけ。行数は利用者数のまま。新しい表・索引を足さず、名前でこの列を検索しない（索引は要らない）。内部DB（組み込みの H2）の大きさの増えは「利用者数 × 最大 64 文字」で無視できる。
- 確かめ: 移行が列の追加 1 文だけで、索引・表を足さないこと（`RD1` の移行のテスト）。

## SC3. 同時の重なり（NFR2.3.3）

- 同じ利用者の保存が重なったときは、後の保存の値が残る（今と同じ後勝ち）。5 項目は 1 つの文で確定し（`RD2`）、途中の値は残らない。他の利用者の行に影響しない。行の排他を使い、読んでから書くやり方（確かめと更新の間に他の保存が入る）は採らない。
- 確かめ: 結合テストで、同じ利用者の 2 つの保存を待ち合わせで重ね、保存後の 5 項目が片方の要求の値でそろっていること。別の利用者の行が変わらないこと。

## SC4. 複数のタブ（NFR2.3.4。受け入れた前提）

- 複数のタブで一方が設定を変えても、もう一方は読み直す（ログインし直す・再読み込み・プリファレンスの画面を開く）まで古い値のままでよい。ブラウザ間の同期（ストレージの変更の通知・サーバーからの通知）は作らない。
- 古い値のままでも、時刻には時差の数字が付く（どのタイムゾーンで出しているかが時刻自体から分かる）ため、誤読しない。確かめるテストは置かない（要件として明記するだけ）。

## 水平・垂直の拡張

- アプリは単一インスタンスの組み込みの H2 の前提のまま（既存の決定）。この単位が拡張の前提を変えない。複数のインスタンスにするときの表示のタイムゾーンの扱い（列は内部DB にあり、サーバーの状態を持たない）は変わらない。名前の集合は JVM ごとの不変の集合で、インスタンス間で共有する状態ではない（JDK の版が違うと集合が異なりうる点は、保存だけに効き、読み取りには効かない。`SD1`）。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- NFR Requirements: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/nfr-requirements/scalability-requirements.md`・`performance-requirements.md`
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/functional-design/rules.md`
- 同じ段の文書: `performance-design.md`・`reliability-design.md`・`logical-components.md`・`security-design.md`

## Assumptions & Open Questions

- [assumption] 想定の利用者数・同時の数は既存のプリファレンスと同じ規模（試験用の利用者 10 名程度の負荷の形）。規模が大きく変わる前提は置かない。
- [assumption] ログインの集中のときの内部DB のプールの余裕は、既存の読み取りと同じ形のため、新しい試験の場面は足さない（`PD6` の場面が保存の経路を負荷の下で確かめる）。
- 未解決の点は無い。
