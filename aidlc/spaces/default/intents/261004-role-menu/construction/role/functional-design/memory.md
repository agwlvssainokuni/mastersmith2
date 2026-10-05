<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-05T11:10:15Z — 「最初のロール」をロールの ID の小さい順とし、一覧・選択肢・書き出しも同じ順にした; Q1 A。名前の変更や割り当ての順で揺れず、選択肢の先頭と読み替えの先が一致する。
- 2026-10-05T11:10:15Z — 存在しない利用者・グループの割り当ての外しは ROLE_NO_CHANGE と読んだ; 契約 C7 の外しの応答に相手の 404 が無く、group のメンバーの外しと同じ読み。グループの外しでグループが無いときだけ lockForAssignment の結果で GROUP_NOT_FOUND を返す。
- 2026-10-05T11:10:15Z — 一覧の利用者とグループの数と ROLE_IN_USE の数は直接の割り当てで数える; AC1.1.8 の「利用者 2・グループ 1」の読み。グループ経由の利用者は数えない。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-05T14:39:30Z — 承認の場の Request Changes（推奨の案のとおり直す）で R-01・R-02・R-03・R-10 と、ロール1件の読み取り・木の名前の問い合わせの引数を直した; group の memberUserIds を使い、保存が同じロールを指すときだけ何もしない切り替えにし、外しの存在しないグループは ROLE_NO_CHANGE にそろえた。契約 C4・C7・C8 との差を functional-spec.md の 8節に、直しを 11節に記録した。
- 2026-10-05T11:10:15Z — 確かめと適用は multipart ではなく application/yaml の本文で送り、指紋はヘッダー X-Role-Transfer-Fingerprint にした; Q8 A。契約 C7 は書き換えず functional-spec.md の 8節に差を書いた。
- 2026-10-05T11:10:15Z — 割り当ては assignmentId を持たない利用者用とグループ用の2つの表にした; Q2 A。components.md の RoleAssignment との差を entities.md に書いた。
- 2026-10-05T11:10:15Z — ロールの一覧は page だけを受け 20 件に固定し、code の表に ROLE_BUSY を足した; group と同じ扱い（Q3 A）。契約 C7 との差として 8節に記録した。
- 2026-10-05T11:10:15Z — 権限の保存（PUT）を載せた対象だけを置き換える差分の保存とし、TransferCheck に roleId と inCurrentDsl を足した; 契約に定めが無い点の補い。足すだけの互換の変更。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-05T11:10:15Z — 作業ロールの切り替えは排他を取らず、保存からロールへの外部キーも置かない; Q3 A。切り替えは軽いまま、正しさは読みのたびの読み替え（C5）で守る代わりに、保存が割り当ての外を指すことがある。
- 2026-10-05T11:10:15Z — また割り当てたら覚えていた作業ロールに戻る形を受け入れた; Q9 A。読み取りで状態を変えない決まりを保つ代わりに、利用者は自分で切り替えずに作業ロールが変わることがある。
- 2026-10-05T11:10:15Z — import の監査は1回1行の要約にした; Q6 A。detail の上限に必ず収まる代わりに、前後の値の全件は監査だけでは分からず、ファイルの SHA-256 で照らす。
- 2026-10-05T11:10:15Z — 一意の違反の後は巻き戻して新しいトランザクションで失敗の出来事を出し、名前の鍵は Java で作って通常の列に保存する; group のレビューの R-01・R-02・R-04 の手当てを先取りした。group の承認の場の決着が違えばそろえ直す。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-05T11:10:15Z — YAML の大きさの上限は仮に 10 MiB; NFR2.4 により NFR 要件の段で応答時間とあわせて確定する。書き出しの理論上の最大（約 40 MB）が読み込みの上限を超えうる点も引き継ぐ。
- 2026-10-05T11:10:15Z — 作業ロールを持ち管理者の印だけを欠く利用者は B5 まで作れない; B4 は管理者の印の無い利用者で 403 を確かめ、B5 で表に足す（group と同じ扱い）。
- 2026-10-05T11:10:15Z — 同じ名前の同時の作成・同じ組の同時の割り当てが、違反になるか待ちの上限切れになるかは未確かめ; NFR 設計の捨ての試しのコードで確かめる（project.md の学び）。
