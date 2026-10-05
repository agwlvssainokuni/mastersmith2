<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-05T10:43:51Z — USER_NOT_FOUND は既存の定義を user.domain へ移して使う; 既存の定義は useradmin.domain にあり、ProblemTypeRegistry は同じ code の二重の定義で起動を止め、UserAdminBoundaryArchitectureTest は audit の外から useradmin への依存を禁じる。境界テストを緩めず、code・状態コード・文言を変えない形として、まとめの確認で承認を得た。
- 2026-10-05T10:43:51Z — メンバーでない利用者の外しは、存在しない利用者でも GROUP_NO_CHANGE と読んだ; 契約 C6 の外しの応答に USER_NOT_FOUND が無いため。追加だけが利用者の有無を確かめる。
- 2026-10-05T10:43:51Z — 問う口の名前は契約のまま GroupDeletionGuard とし、数の方法 assignedRoleCounts を足した; Q3 の答え A の「広げる」を、名前を変えずに方法を足す形と読んだ。契約の名前を変えないことで U4 の設計の読み替えを減らす。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-05T10:43:51Z — 一覧は契約 C6 の size を受けず 20 件に固定した; 既存の common.paging.Paging に合わせるため（まとめの確認で承認）。契約は書き換えず functional-spec.md の 8節に差を書いた。
- 2026-10-05T10:43:51Z — 契約 C4 に lockForAssignment と assignedRoleCounts を、code の表に GROUP_BUSY を足した; Q2・Q3 の答え A による足すだけの互換の変更。承認済みの contract-summary.md は書き換えず、functional-spec.md の 8節に記録した。
- 2026-10-05T10:43:51Z — user.service にメンバーの要約をまとめて読む口を足す設計にした; 今の findById を 1 人ずつ呼ぶと詳細でメンバーの数だけ読み取りが起きるため。質問とまとめの確認には無かった点で、承認の場で伝える。
- 2026-10-05T14:39:43Z — 承認の場の Request Changes（推奨の案のとおり直す）で R-01・R-02・R-03・R-04 と memberUserIds の口を直した; DB の違反の後は巻き戻して書き込みの無い新しいトランザクションで失敗の出来事だけを出す形（BR5.7）、一意の鍵・主キーの待ちの上限切れも GROUP_BUSY、名前の鍵は Java の1か所で作り通常の列に保存した。R-05〜R-10 は直していない（functional-spec.md の 10節）。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-05T10:43:51Z — 同時の重なりはグループの行の排他を順番の決め手にし、外部キーは最後の守りにした（Q2: A）; 利用者の管理と同じ形で勝ち負けが確かめやすい代わりに、GROUP_BUSY の code と U4 が呼ぶ排他の口が増える。H2 の振る舞いは NFR 設計で捨ての試しのコードで確かめる。
- 2026-10-05T10:43:51Z — B3 は role のパッケージに仮の問う口の実装を置く（Q4: A）; develop がどの時点でも起動する代わりに、B5 で置き換え忘れる危険が残るため、B5 の終わりの条件に仮の実装が残っていないことを入れる。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-05T10:43:51Z — グループの名前は管理者が自由に入れるため、名前にメールアドレスなどが入ると監査の detail にそのまま残る; 伏せる規則は入れる項目を型で限ることで守り、名前の中身までは見ない。承認の場で受け入れてよいかを確かめる。
- 2026-10-05T10:43:51Z — 認可の 403 を確かめる「作業ロールを持ち管理者の印だけを欠く利用者」は B5 まで作れない; B3 では管理者の印を持たない利用者で確かめ、B5 で作業ロールつきの利用者を表に足す形にした。
