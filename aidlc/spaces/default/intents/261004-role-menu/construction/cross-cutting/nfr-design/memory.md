<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-05T22:58:23Z — 静的な検査と実行時の検査の口の集合は、道ではなくクラスと方法で比べると決めた; 静的な検査は ${server.error.path:/error} を既定値で読み、実行時は解決した値を読むため、設定を変えると道が食い違いうる。PUBLIC の一覧は実行時の検査の中で解決した道の型で比べる。
- 2026-10-05T22:58:23Z — NFR 要件の読み直しの Minor・Suggestion（R-02〜R-07）を、要件の文書は書き換えずにこの段の設計の中で手当てした; R-02 は試しの点、R-03 はアイコンの照合、R-04 は持ち主無し、R-05 は深さ、R-06 は ESLint のテスト、R-07 は広げ方として security-design.md に書いた。
- 2026-10-06T13:53:49Z — 承認の場の決定「推奨の案のとおり直す」で R-01 を直し、検査の検査を設計に足した; 規則を ApiAccessRules の1か所で作り、本番のクラスと違反の見本の両方に当てる。PUBLIC の一覧の比べは純粋な関数にして、4つの境界を Spring を起動しない単体のテストで確かめる。見本の @RestController は既存の TestFixtureEndpoints と同じく、決して設定しない条件で Bean にしない。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-05T22:58:23Z — 試しで判定の部品が使えたため、要件 NFR1.4・機能設計 BR1.5 の MockMvc への切り替えは発動しない形にした; 決まりは手順として残し、文書は書き換えずに security-design.md 8節に差を書いた。未ログインの主体は匿名のトークンで渡すと決めた（null でも同じ結果）。
- 2026-10-05T22:58:23Z — 試しの写しでは .env と aidlc・reference を写さなかった; 秘密の値と記録を外へ出さないため。試しの主体のメールアドレスは example.com の見本の値で、結果のファイルにも秘密の値は無い。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-05T22:58:23Z — 実行時の検査では内部DB に利用者を作らず、主体の値だけで判定する形にした; 判定は AuthenticatedUser の admin() だけで決まると試しで確かめたため。代わりに、停止中の利用者の拒否（アクセストークンの認証）はこの検査では見ず、既存の auth のテストに任せる。
- 2026-10-05T22:58:23Z — 共有の木に深さの上限を置かなかった; 深さは呼ぶ側のデータで決まり、開いた節だけを描くため。メニューの木の深さの上限は U2・U7 が持つ。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-05T22:58:23Z — 実行時の検査を、ほかの結合テストと同じ起動の文脈で使い回せるか; 試しは素の @SpringBootTest（MOCK）で約 32 秒だった。ほかの結合テストは RANDOM_PORT と固有の設定を持つものが多く、文脈を使い回せるかはコード生成で既存の設定の形をそろえて確かめる。
- 2026-10-05T22:58:23Z — 後の Intent（J・K）で分類を権限つきに広げるときの主体の作り方; U1 の検査は主体と期待を表で持ち、行を足すだけで広げられる形にした。その Intent の設計で決める。
