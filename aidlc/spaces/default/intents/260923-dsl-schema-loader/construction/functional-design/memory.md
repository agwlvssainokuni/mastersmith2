<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-23T13:21:32Z — U2・U3・U4 のレビューで、契約（C6・C7・C8）や前の単位の決まりとの食い違い（カラムの違いの区分の値、受け付けなかった理由の名前、DSL の識別の欠け、別のスキーマの外部キーの扱い）が指摘された。単位の設計を書く前に、その単位が触れる契約の列挙値・項目と、前の単位の決まりを突き合わせる。


- 2026-09-23T12:33:18Z — U1 は要件・ADR-006・契約 C1 で論点がほぼ決まっていたため、質問を作らず設計の要点の確認（Looks correct）で進めた。接続先の設定は Spring Boot の設定の値としてエンティティにせず、決まり（BR1.x）として書いた。
<!-- aidlc-wave-memory:u1-target-db:0067ddd8569a6a6b58a425800f3732313d690d4dea9d4b8c78640f2b1dd833c8 -->
## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-23T13:21:32Z — U5 の要約では部品の構成を functional-spec.md に含めるとしたが、ui の単位には frontend-components.md が求められる成果物だったため、レビューの後に部品の構成・画面の状態・API の受け渡しを frontend-components.md に移した（決まりの中身は変えていない）。要約の前に、段の定義で単位の種別ごとの成果物を確かめる。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-23T13:21:32Z — AC6.2.1〜AC6.2.3 の一括の確かめ（11 本の API のサーバー側の結合テスト）を、単位の分割表のとおり U5 に置いた。U5 の境界（画面と ApiClient）とずれるため、どの Bolt・どのパッケージで書くかはコード生成の計画で決める（レビューの R-01）。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
