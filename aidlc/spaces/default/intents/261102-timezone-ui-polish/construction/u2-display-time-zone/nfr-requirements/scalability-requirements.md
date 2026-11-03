# 拡張の要件 — u2-display-time-zone

出典: 機能設計（`functional-design/rules.md` の BR2.1・BR2.3・BR2.14・BR2.16）、要件（`inception/requirements-analysis/requirements.md` の NFR2.1）、`performance-requirements.md`。

この単位は新しい負荷の経路を作らない（既存の `/api/me/preferences` の項目を1つ足し、画面がログイン後に1回読むだけ）。そのため拡張の要件は、負荷の見込み・データの増え方・同時の重なりに絞る。番号の付け方は `security-requirements.md` の冒頭のとおり。上流の NFR2 に当たる行が無い追加の要件は、上流の最後の枝番（NFR2.2）の次の NFR2.3 から振る。

| ID | 要件 | 根拠・決まり | 確かめ方 | 区分 |
|---|---|---|---|---|
| NFR2.3.1 | 負荷の見込み: 追加の読み取りはログイン1回あたり1回で、利用者数 × ログインの回数に比例するだけ（ポーリング・定期の更新なし）。保存は利用者が設定の画面で保存したときだけ。今の `preferencesGet`・`preferencesSave` の負荷の形（同時 10 VU）を超える見込みはなく、新しい拡張の仕組み（キャッシュ・非同期処理・キュー）を足さない | NFR2.1・BR2.14 | `performance-requirements.md` の NFR2.1.1・NFR2.1.3・NFR2.1.4 で確かめる | NFR2.1.1 はテスト、負荷は Deferred（Build and Test） |
| NFR2.3.2 | データの増え方: 利用者の行に NULL 可の文字列の列を1つ（最大 64 文字）足すだけで、行数は利用者数のまま。新しい表・索引を足さない。名前でその列を検索しない（索引は要らない）。内部DB（組み込みの H2）の大きさの増えは利用者数 × 最大 64 文字で、無視できる | NFR4.1・BR2.1 | 移行が列を1つ足すだけで、索引・表を足さないこと（移行のテスト） | テスト |
| NFR2.3.3 | 同じ利用者の保存が同時に重なったときは、後の保存の値が残る（今のプリファレンスと同じ後勝ち）。5項目は1つのトランザクションで確定するため、途中の値（一部の項目だけ新しい値）が残らない。他の利用者の行に影響しない | BR2.3・`team.md` Code Style（トランザクションの境界は業務処理の層） | 結合テスト: 同じ利用者の2つの保存を待ち合わせ（スレッドの数に頼らない）で重ね、保存後の5項目が片方の要求の値でそろっていること。別の利用者の行が変わらないこと | テスト |
| NFR2.3.4 | 複数のタブで一方が設定を変えても、もう一方は読み直す（ログインし直す・再読み込み）まで古い値のままでよい。ブラウザ間の同期は作らない | 機能設計の前提（言語の変更と同じ扱い） | 要件として明記するだけ（確かめるテストは置かない。画面が古い値で動いても、時差の数字が時刻に付くため誤読しない） | 受け入れた前提 |

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/functional-design/rules.md`
- 要件: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/requirements-analysis/requirements.md`
- 同じ段の文書: `performance-requirements.md`・`security-requirements.md`

## Assumptions & Open Questions

- [assumption] 想定の利用者数・同時の数は既存のプリファレンスと同じ規模（試験用の利用者 10 名程度の負荷の形）。規模が大きく変わる前提は置かない。
- 未解決の点は無い。
