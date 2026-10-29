<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-30T00:00:00Z — 契約 C3 の 400「入力不可の参照の項目の値を変えようとした」を、参照元のカラムが書けないとき（既存の NOT_ALLOWED）だけと読んだ。参照先を読めない・多すぎる参照の値は 409（AC3.1.3・AC1.5.2 に合わせた。functional-spec.md の D2）。
- 2026-10-30T00:00:00Z — 必須の参照項目は NOT NULL（nullable が偽）で作成で入力できるカラムと定義した。DSL の required だけで NULL を許すカラムは既存の REQUIRED のまま。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-30T00:00:00Z — Q1: A の TOO_MANY を判定するため、必須の参照項目が使えて空のときの 400 が、対象DB を読んだ後の ⑩ で返る（C3 の「400 は業務の失敗より前」との差 D3）。⑨ の 400 では対象DB に触れない決まりを保つため、混じった 400 では TOO_MANY の項目が REQUIRED で並ぶ（D4）。
- 2026-10-30T00:00:00Z — U1 の確かめの口の決まり（9.4・9.6）は Q2: A の文字どおり一致と件数の判定を持たないため、U1 のファイルは変えずに H1・H2 として引き継いだ。
- 2026-10-29T23:24:58Z — 承認の場の Request Changes（依頼者の決定）で直した: U1 の check（200 行まで読み型つきの完全一致で FOUND）と countState（201 行目まで読み AVAILABLE・TOO_MANY・NO_PERMISSION・FAILED）を U1 と共通の形として使い、暫定の安全側と H1・H2 の引き継ぎを外した（R-01）。`""` は既存の ⑨ の検証の結果にそのまま従う1つの扱いに決め、期待結果の表を置いた（R-02）。同じ参照先の countState を要求内で1回にする BR4.7 を足し、check の束ね方は NFR 設計に回した（R-03）。NOT_EDITABLE_NOT_NULL の field が空とカラム名の2通りになる影響の範囲をコード生成への引き継ぎに書いた（R-04）。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-30T00:00:00Z — 件数（TOO_MANY）による拒否を作成の必須の参照項目だけに限った（D5）。保存のたびの 201 行の読み取りを避ける代わりに、NULL を許す項目と更新では、入力不可の項目に正しい値を直接送れば保存される。
- 2026-10-30T00:00:00Z — 更新前の行の読み取りは既存の detail を使う（新しい SQL のメソッドを作らない）。読めるカラムをすべて読む重さと、上限で切られた値を「変わった」と扱う安全側を受け入れた。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-30T00:00:00Z — H1（文字どおり一致の比べ方の置き場と、照合順序で重なる行の取りこぼし対策）と H2（件数の判定の口）は、U1 の承認の場かコード生成の計画で確定する。
