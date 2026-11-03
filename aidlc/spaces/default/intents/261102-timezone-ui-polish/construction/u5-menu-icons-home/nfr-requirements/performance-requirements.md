# 性能の要件 — u5-menu-icons-home

出典: 要件（`inception/requirements-analysis/requirements.md` の NFR2.1・NFR2.2・NFR3.2・FR7.5）、機能設計（`construction/u5-menu-icons-home/functional-design/functional-spec.md` の D3・D6・D16・D17・D20・D27、`frontend-components.md`）、契約（`inception/contract-design/contract-summary.md` の C5）、前の Intent の業務のメニューの目標（`261004-role-menu` の `construction/navigation/nfr-requirements/performance-requirements.md` の NFR2.2・NFR2.3・NFR2.5）、コード知識ベースの技術の構成（`codekb/mastersmith2/technology-stack.md`）。

U5 は、メニューの応答に `tableKind` を 1 項目足し、画面でアイコンの決め方とホームのカードを足し、ロゴを足す単位。新しい API・表・問い合わせ・依存は足さない（`functional-spec.md` D6・D20）。そのため、性能の要件は「今の目標を変えない」「問い合わせを増やさない」「画面の計算を重くしない」に絞る。技術の選択は `tech-stack-decisions.md`。

## 番号の付け方

inception の NFR と同じ番号は、同じ意味でだけ使う。NFR2.1（日時の変換で問い合わせを増やさず、性能の目標を変えない）は、この単位ではメニューの応答に当たる範囲を NFR2.1.1・NFR2.1.2 に、NFR2.2（ホームが新しい API を呼ばない）を NFR2.2.1・NFR2.2.2 に細かくする。ロゴの読み込みの重さは NFR2.2.3 に置く（上流の NFR に当たる行が無いため、近い NFR2.2 の枝番に寄せた）。

## 要件の一覧

| ID | 要件 | 根拠・決まり | 確かめ方 |
|---|---|---|---|
| NFR2.1.1 | メニューの API の 1 要求が使う読み出しの数を、`tableKind` を足す前と同じにする。`NavigationService` が呼ぶのは、適用中の DSL の取り出し（`current()`）1 回と、権限の写しの解決（`snapshotFor`）1 回だけ。種類は、1 回読んだ DSL の `findTable`（スキーマが 1 つの一覧と名前引き。項目ごとに木を走査し直さない）から決め、対象DB・内部DB の問い合わせ、権限の解決、DSL の読み直しを項目ごとに足さない。1 要求の対象DB・内部DB の接続の数も今のまま | NFR2.1・`functional-spec.md` D3・D6・前の Intent の NFR2.3 | 既存の単体テスト `NavigationCallCountTest`（menus 1 項目と 1,000 項目で、呼ばれた回数が上のとおり）と、結合テスト `NavigationConnectionUsageIT`・`NavigationQueryExposureIT` が、変更なしで通ること。`tableKind` を足した応答を作る場合も回数が同じであることを `NavigationCallCountTest` の 1,000 項目の場面で確かめる |
| NFR2.1.2 | メニューの API の応答時間の目標は変えない。95 パーセンタイルで 1 秒以内、k6 の `checks` の率は 1（前の Intent の NFR2.2。緩めない）。応答の大きさの増加は、1 項目あたり最大 `"tableKind":"TABLE",` の約 22 バイト（フォルダは `"tableKind":null,` の約 18 バイト）で、1,000 項目で約 22 KB の増加（圧縮前）にとどまる。応答を作る処理（`MenuFilter`）は、項目 1 つにつき種類の問い合わせ 1 回の定数時間を足すだけ | NFR2.1・`project.md` の学び（目標を緩めず、測れないときは Unverified で持ち主の段へ引き継ぐ） | 既存の k6 の場面 `navMenu`（悪い側の規模）と `navMenuBaseline`（目安の規模）を、台本を変えずに流し、`iteration_duration` の p95 < 1000 ms と `checks` の率 1 を判定する。前の Intent の値と並べて記録する。流す段の持ち主: この Intent に Performance Validation の段が無いため Build and Test（`project.md` の学び）。測れなければ `Unverified`（持ち主の段を明記） |
| NFR2.2.1 | ホームは、業務のメニューの API・作業ロールの API・ログイン後に 1 回読む既存の API のほかに、新しい API を呼ばず、対象DB への新しい問い合わせ（行の数など）もしない。ホームを開く操作で増える HTTP 要求は 0 | NFR2.2・FR7.5・`functional-spec.md` D20 | 画面の部品のテスト: ホームを描いて、API の呼び出し（`apiRequest` の替え物）が増えないこと（呼び出しの数がホームを描く前と変わらないこと）。実際のブラウザの確かめ（E2E 030 の書き換え）で、ホームを開いたあとの `/api/` への要求が、業務のメニュー・作業ロール・プリファレンスの既存の要求だけであること |
| NFR2.2.2 | ホームのカードを作る処理（`buildHomeCards`・`firstNavigablePath`）は、メニューの項目の数 N に対して O(N) で、1 回の呼び出しで各項目を高々 1 回だけ訪れる（フォルダの移り先は、各最上位の項目の部分木を 1 回たどるだけ）。同じ入力で再描画のたびに作り直さず、業務のメニューの応答・表示の言語・ログイン状態・登録が変わったときだけ作り直す | `functional-spec.md` D16・D17 | `fast-check` の性質ベースのテスト: 任意の形の木（深さの上限 5、最大 1,000 項目）で、`firstNavigablePath` の訪問した項目の数が木の項目の数以下であること（訪問を数える替え物で決定的に確かめる）。部品のテスト: 同じ入力で再描画しても `buildHomeCards` を呼び直さないこと（`useMemo` の依存の確かめ） |
| NFR2.2.3 | 上のバーのロゴは、追加の HTTP 要求を足さない（`AppIcon` はソースに書いた固定の図柄を描く部品で、画像の読み込みや SVG の取得をしない）。大きさは固定（24 px）で、描画の後に配置がずれない。ホーム・業務データの画面の初期表示の JavaScript の大きさを、新しい依存なしで足す部品（カード・純粋な関数）の分にとどめる | NFR2.2・`functional-spec.md` D27・U6 の `AppIcon` | 画面の部品のテスト（ロゴの描画に `fetch`・`img`・`use` が無いこと）。ビルドの結果の確かめ: 新しい依存が `package.json` に増えていないこと（`tech-stack-decisions.md`） |

## 目標の置き場の確認

- 応答時間の目標は、前の Intent のメニューの API の目標（95 パーセンタイル 1 秒以内）を引き継ぎ、この Intent で緩めない。負荷の試験の場面と閾値は前の Intent で作ったものを変えずに使う（台本の変更は無い）。
- 画面の描画時間の目標値は置かない。理由: ホームは既に読み込み済みの値から作る純粋な組み立てで、重くなる要因は項目の数だけであり、O(N) の上限（NFR2.2.2）を決定的に確かめる形が、時間に頼る測りより安定するため。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 要件: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/requirements-analysis/requirements.md`（NFR2.1・NFR2.2・FR7.5）
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/functional-design/functional-spec.md`・`frontend-components.md`
- 契約: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/contract-design/contract-summary.md`（C5）
- 規則（rules）: U5 は ui の単位で `rules.md` を作らないため無い。決まりは `functional-spec.md` の D 番号
- 前の Intent の目標: `aidlc/spaces/default/intents/261004-role-menu/construction/navigation/nfr-requirements/performance-requirements.md`（NFR2.2・NFR2.3・NFR2.5）
- 既存のコード（読み取りだけ）: `backend/src/main/java/cherry/mastersmith/dsl/domain/DslModel.java`（`findTable`）、`navigation/service/NavigationService.java`
- コード知識ベース: `aidlc/spaces/default/codekb/mastersmith2/technology-stack.md`
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] メニューの項目の数の上限は 1,000（前の Intent の規模の前提 NFR2.1 のまま）。この単位で規模の前提は変えない。
- [assumption] 手元のこの Intent の負荷の試験は、前の Intent の `navMenu` 台本をそのまま使う。台本が今の `perf/` に残っていることは、Build and Test で確かめる。
- 未解決の点: 無い。
