<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-04T23:09:22Z — make-you-chic-ui から入れ子のサイドバーの対応完了の報告（コミット 5bf1ffe）が届いた; AppShell に navSections・navExpandedIds・onNavExpandedChange・navLabels が足され、既存の navItems はそのまま動く。木の部品・Dropdown の menuitemradio・Table の行の入れ子は対象外のため、共有の木は計画どおり frontend の側で作る。固定先の更新は B9 の専用のコミットで行い、今は e82b651 のまま。cross-cutting の登録の型の設計の材料として担当に伝えた。


- 2026-10-04T23:32:42Z — mockups.md の「木と表に1段足す」をメニューの木の括弧と違いの表の見出しの行と読んだ; メニューの木は menus を描きスキーマの節を持たないため、そのままでは実装できない。Q6: A でこの読み方を確かめた。
<!-- aidlc-wave-memory:dsl-v2:403688182f56786860ae361a4debe616fcf602c0f35654f2252de2a8af0a2f4c -->

- 2026-10-04T23:32:42Z — 起動時の深すぎる枝は子ごと落とし、空になったまとまりはさかのぼって落とす; 既存の DslMenuItem の「テーブルか子を持つ」決まりを保つため。WARN は1件で識別の先頭12文字と落とした数と上限だけを出す。
<!-- aidlc-wave-memory:dsl-v2:927dea2b5fe0bae40b7b089501c31262d0142d104ac7f1e5bc9bdf68323f2012 -->

- 2026-10-04T23:32:11Z — 既存の 34 の口の分類を今の安全の決まりから読み、PUBLIC 9・AUTHENTICATED 3・ADMIN 22 とした; /error の2つは Q2 A で PUBLIC に数えた。10 のコントローラーはどれも中の口が同じ分類のため、印はクラスの単位で付ける形にした。
<!-- aidlc-wave-memory:cross-cutting:71cca9c8c36f893d200c8532639cb0e545ef23ac94055ce24d2e3844e2e355d7 -->

- 2026-10-04T23:32:11Z — team.md の「既存の機能に手を入れたら境界テストを足す」を機能のパッケージに当てると読んだ; access と user に境界テストを足し、common は共通部品として全体の ArchitectureTest に任せる。まとめの確認でこの読みを含めて承認を得た。
<!-- aidlc-wave-memory:cross-cutting:9e9fa94eb359eea4039e342940370177f3bba1c41ee2ef540f6075fa6eac2adc -->

- 2026-10-05T10:43:51Z — USER_NOT_FOUND は既存の定義を user.domain へ移して使う; 既存の定義は useradmin.domain にあり、ProblemTypeRegistry は同じ code の二重の定義で起動を止め、UserAdminBoundaryArchitectureTest は audit の外から useradmin への依存を禁じる。境界テストを緩めず、code・状態コード・文言を変えない形として、まとめの確認で承認を得た。
<!-- aidlc-wave-memory:group:c2f273609777e06acf6d494602d0087d327a2282070da3d620b59bb56aed157e -->

- 2026-10-05T10:43:51Z — メンバーでない利用者の外しは、存在しない利用者でも GROUP_NO_CHANGE と読んだ; 契約 C6 の外しの応答に USER_NOT_FOUND が無いため。追加だけが利用者の有無を確かめる。
<!-- aidlc-wave-memory:group:e465164e4f2dd6d62f94753fbe90727d3bb9bfc76ba42a71776cd240910d5ad0 -->

- 2026-10-05T10:43:51Z — 問う口の名前は契約のまま GroupDeletionGuard とし、数の方法 assignedRoleCounts を足した; Q3 の答え A の「広げる」を、名前を変えずに方法を足す形と読んだ。契約の名前を変えないことで U4 の設計の読み替えを減らす。
<!-- aidlc-wave-memory:group:bd0642746cea335c1d8f3b93547000708d988c40560878b8a858ae9582508c4f -->

- 2026-10-05T11:10:15Z — 「最初のロール」をロールの ID の小さい順とし、一覧・選択肢・書き出しも同じ順にした; Q1 A。名前の変更や割り当ての順で揺れず、選択肢の先頭と読み替えの先が一致する。
<!-- aidlc-wave-memory:role:0e83018c9be2b1316005d3db273b144aac913407bc22e6c15e299df9edda728b -->

- 2026-10-05T11:10:15Z — 存在しない利用者・グループの割り当ての外しは ROLE_NO_CHANGE と読んだ; 契約 C7 の外しの応答に相手の 404 が無く、group のメンバーの外しと同じ読み。グループの外しでグループが無いときだけ lockForAssignment の結果で GROUP_NOT_FOUND を返す。
<!-- aidlc-wave-memory:role:c9c93ea4a01d26895a2c37d0a0482f7022b141a8f4d9964c6f16525c45d76546 -->

- 2026-10-05T11:10:15Z — 一覧の利用者とグループの数と ROLE_IN_USE の数は直接の割り当てで数える; AC1.1.8 の「利用者 2・グループ 1」の読み。グループ経由の利用者は数えない。
<!-- aidlc-wave-memory:role:3e00e0a324c4906e719874aee836e4d4f348b79728fbaf2dee0851ca1e812bf3 -->

- 2026-10-05T13:50:00Z — team.md の★「一覧に無い icon を拒否するか既定にするか」は要件で決まっていると読んだ; FR9.6・AC5.1.7 が「既定のアイコンにする」と書いているため、質問にせず「決まっていること」に置いた。サーバーは list に置き換え、拒否しない（BR4.2）。
<!-- aidlc-wave-memory:navigation:26f0de797e70b2386326e79f6cdf8e275b54d749919fb73b5ecba6c61f0c0cfa -->

- 2026-10-05T13:50:00Z — AC5.1.14（DSL に無いテーブルを指す項目）は、絞る関数を単独で確かめるための入力と読んだ; dsl-v2 の BR1.6 で適用中のモデルの組は必ず DSL にあるため、本番の流れでは起きない。解決の口が NONE を返して落ちる形で、特別な分岐は持たない（BR2.6）。
<!-- aidlc-wave-memory:navigation:6ce690c6ebdb8d6a75f7f9e2e97e8c1f9074288dd085a7f323a7aee477d575df -->

- 2026-10-05T13:50:00Z — 置き場の問い合わせの「その権限を持たない 403」は、要るテーブルの READ だけを欠く利用者で確かめると読んだ; 2本ともログインだけの API で管理者の印の組は無いため、team.md の「要る権限だけを欠く利用者」をテーブルの単位に当てた。
<!-- aidlc-wave-memory:navigation:41030000a82e1559c75c625a36144535b9d5a406ce6516bc2f3eee3539b3fc69 -->

- 2026-10-05T13:47:26Z — ui の単位の traceability の target は BR ではなく functional-spec.md の D1〜D32 と流れの番号を指した; rules.md を作らない単位のため、BR を書くと sensor が rules.md に無い id として扱う。前の Intent の ui の単位（user-admin の U5）も functional-spec の節と D の番号を指していた。
<!-- aidlc-wave-memory:role-admin-ui:9302c0cd0e8bfb2c4d3fec47cf2e92a0722bcd2a41470dbf1a555272058b785b -->

- 2026-10-05T13:47:26Z — US5.3 の AC のうち、この単位が持つのは新しい管理の画面の登録だけと読んだ; AC5.3.1・AC5.3.2 を「部分」で OK にし、サイドバーの組み立て・見出し・出し分けの AC は U7 app-frame-ui へ Deferred にした。
<!-- aidlc-wave-memory:role-admin-ui:408f6ee2b89be2f6788ccb9a98d087fa460efaf2a3e96b2db4b31dfcc2de63af -->

- 2026-10-05T13:47:26Z — 詳細の画面は道でタブを決める形にした（/admin/roles/:roleId と .../assignments）; 拒否の文言・S6・S9 から割り当てのタブへ直接移れるようにするため。引数つきの道の登録は今のアプリで初めてになる。
<!-- aidlc-wave-memory:role-admin-ui:851d87fb5da5ef5371f22375c040fd9df15e25ae4940c309043c3ec083d9462c -->

- 2026-10-05T14:18:04Z — 5bf1ffe の部品の口は報告ではなくソースで確かめた; 上流のリポジトリで git show を読み取りだけで使い、Sidebar・AppShell・Topbar・CSS を読んだ。報告の記述は data として扱い、口の形（navSections・Set<string> の開閉・current・labels）と data-testid の変化をソースの行で裏付けた。
<!-- aidlc-wave-memory:app-frame-ui:6913ec544402eb77290248b3c2d443918a8859fe0ff2700ef3a006a3f985446f -->

- 2026-10-05T14:18:04Z — 業務のメニューの今の項目は道の引数 item で押した項目にする（Q3 A）; DSL は同じテーブルを複数の項目が指すことを禁じず、aria-current は1つに絞るのが望ましい。item が当たらないとき（ブックマーク・DSL の適用し直し）は行きがけの順で最初の項目にする。
<!-- aidlc-wave-memory:app-frame-ui:9f57ed012f8db0d52df2f1a21df96161b748d5a1be80db260484a07f4558e63d -->
## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->


- 2026-10-04T23:32:42Z — 契約 C3 と3点違う形にした; スキーマの表示名を ja・en の label に、安全な読み込みの上限に展開後の節の数を足し、位置を引く口を dsl.service の型にした。承認済みの契約は書き換えず functional-spec.md の 9節に差を書いた。
<!-- aidlc-wave-memory:dsl-v2:819e961d55f8a2ece975b4d2696817314f1fff9f4d3d6a00a601c7403c8593e7 -->

- 2026-10-04T23:32:42Z — ストーリーに無い場合を2つ足した; 版を上げる前のプレビューの読み直し（Q4: A）と今の状態の appliedUnreadable（Q5: A）。どちらも今のコードのままでは 500 や誤解を招くと分かったため。
<!-- aidlc-wave-memory:dsl-v2:ac1768d476b218fef95087847b578ef0ebbaa00c60cab6150a7b14f63568ca45 -->

- 2026-10-04T23:32:42Z — 前の Intent の BR5.3（生成した DSL にスキーマ名を書かない）を覆した; D1 と Q7: A のとおり3種類の DB ともスキーマ名を書く。スキーマ名が無いことを確かめる既存のテスト4つを書き換える。
<!-- aidlc-wave-memory:dsl-v2:f310454000069e63085096bb3792bc50af23877c7b444b6b1d7af1f5a42d039c -->

- 2026-10-04T23:32:11Z — 単位の一覧と C1 の「フィルターが受けるログイン」はコードと違った; ログインは AuthController の口で受けており、フィルターだけが受ける入口は無い。ログインの口には PUBLIC の印を付け、functional-spec.md 9節に差として書いた。
<!-- aidlc-wave-memory:cross-cutting:650338ce738c6d0904c28cbc137c92be5b751ecad5fe50cc22ee1cde12b6104d -->

- 2026-10-04T23:32:11Z — 契約 C2 の共有の木の props に labels を足し、onToggle を (id, 次の状態) の形にした; どちらも足すだけの互換の変更で、まとめの確認で承認を得た。LoginStateProvider への logout の追加も C2 に無い変更（Q4 A）として差に書いた。
<!-- aidlc-wave-memory:cross-cutting:e8442011363c5f1189f4da7daf9fa6b9c1c42d56104bfe669085d32edf5672a5 -->

- 2026-10-05T10:43:51Z — 一覧は契約 C6 の size を受けず 20 件に固定した; 既存の common.paging.Paging に合わせるため（まとめの確認で承認）。契約は書き換えず functional-spec.md の 8節に差を書いた。
<!-- aidlc-wave-memory:group:63a3c84142e6a0082675833c5e5f9933e90460e0d098005102401f89e2301297 -->

- 2026-10-05T10:43:51Z — 契約 C4 に lockForAssignment と assignedRoleCounts を、code の表に GROUP_BUSY を足した; Q2・Q3 の答え A による足すだけの互換の変更。承認済みの contract-summary.md は書き換えず、functional-spec.md の 8節に記録した。
<!-- aidlc-wave-memory:group:12b6c09bfbad15ee3bcbdcc8b3376f007ef366c844c2a250160bff1437129b0f -->

- 2026-10-05T10:43:51Z — user.service にメンバーの要約をまとめて読む口を足す設計にした; 今の findById を 1 人ずつ呼ぶと詳細でメンバーの数だけ読み取りが起きるため。質問とまとめの確認には無かった点で、承認の場で伝える。
<!-- aidlc-wave-memory:group:70c24d1b6f74e9958a6c84d0450f02da1417755f591298720e83b0ddbc060c6e -->

- 2026-10-05T11:10:15Z — 確かめと適用は multipart ではなく application/yaml の本文で送り、指紋はヘッダー X-Role-Transfer-Fingerprint にした; Q8 A。契約 C7 は書き換えず functional-spec.md の 8節に差を書いた。
<!-- aidlc-wave-memory:role:64c47d4bbd42c7eace4a1e447dac01114110ccbe73959ce9ad1efc4b968ccc35 -->

- 2026-10-05T11:10:15Z — 割り当ては assignmentId を持たない利用者用とグループ用の2つの表にした; Q2 A。components.md の RoleAssignment との差を entities.md に書いた。
<!-- aidlc-wave-memory:role:a871b209d2cef6d9668add743418a3cb7bd8faddaf46422fb329e9fe06ff4e61 -->

- 2026-10-05T11:10:15Z — ロールの一覧は page だけを受け 20 件に固定し、code の表に ROLE_BUSY を足した; group と同じ扱い（Q3 A）。契約 C7 との差として 8節に記録した。
<!-- aidlc-wave-memory:role:5611ef12aba90cb790b09f5aece92d96bd27c8abdc99f0e115a09dd85898f31b -->

- 2026-10-05T11:10:15Z — 権限の保存（PUT）を載せた対象だけを置き換える差分の保存とし、TransferCheck に roleId と inCurrentDsl を足した; 契約に定めが無い点の補い。足すだけの互換の変更。
<!-- aidlc-wave-memory:role:9faaef79919c4b5e2197e2f40a94f4388a1bc3f7d1f1cb0a9c0e940d8a075147 -->

- 2026-10-05T13:50:00Z — 契約 C9 の形を Q1〜Q5 の答えで変えた（表示名を {ja, en}、id と emptyReason を足す、置き場の道を引数に、アイコンの一覧をファイルに）; 承認済みの contract-summary.md は書き換えず、functional-spec.md の8節に差の表を置いた。型と道の変更は U7 の機能設計で受け入れを確かめる。
<!-- aidlc-wave-memory:navigation:9935e91e379672629c2ac01ac750a87b5bff7c4e0be477a41bd604fbcf35ff65 -->

- 2026-10-05T13:50:00Z — 置き場の問い合わせに 400 VALIDATION_FAILED（引数が無い・空・257 文字以上）を足した; 契約 C9 には 200 と 403 しか無いが、引数で受ける形（Q4 A）にしたため入力の検証が要る。長さの上限 256 はまとめの確認の要点 SP 6 の例の値をそのまま使った。
<!-- aidlc-wave-memory:navigation:20460d08437205581b383a7920c97ad4552b50d8e04ec22184cfe5a2ccaf752a -->

- 2026-10-05T13:50:00Z — 依存の先が components.md（dsl・role）より広い（access.domain・auth.domain）; ACCESS_DENIED の code と要求の文脈の主体を読むためだけで、useradmin と同じ使い方。まとめの確認（SP 2）で承認済み。
<!-- aidlc-wave-memory:navigation:3b467c5cac57bac0e3be545050d1d82bbad2a244412253776b1f57d42f29abfd -->

- 2026-10-05T13:47:26Z — 契約 C7 に無い GET /api/admin/roles/{roleId} を足す前提で設計した; Q1 A。role の機能設計は承認の場の Request Changes で直してもらい、それまでは「直した後の形」として functional-spec.md の 9節 (a) と 10節に記録した。
<!-- aidlc-wave-memory:role-admin-ui:f3878c26e7d43542928d78d9458001c4ac1f9f66cd651949752f2414df1b3872 -->

- 2026-10-05T13:47:26Z — S9 は mockups の「利用者の詳細」ではなく、行の操作の「ロールを見る」と読み取りの Modal にした; 利用者の管理に詳細の画面が無かったため（Q5 A）。表の列と既存の操作は変えない。
<!-- aidlc-wave-memory:role-admin-ui:9446bac68615fdff738c0db669a06ae8ed1fdc560b73473ff76ba96d906c4cba -->

- 2026-10-05T13:47:26Z — AC2.2.9 は条件つきの確かめにした; 管理の API は他人の作業ロールを返さず、FR6.2 の線引きを保つため（Q4 A）。差は functional-spec.md 9節 (c)。
<!-- aidlc-wave-memory:role-admin-ui:f87060d56149d42f4f1fd39bf79a43db327b0a8081f87a547f87695a0991e303 -->

- 2026-10-05T13:47:26Z — design-system-mapping.md の「Table に行の開閉が無い」は今の固定先と違った; e82b651 の Table は renderDetail を持つが、開閉のボタンの名前が全行で同じでページ送りが常に出るため、S7 は素の table のままにした（9節 (e)）。
<!-- aidlc-wave-memory:role-admin-ui:eba55338a356c6de1fa262873a9b4c0ec4d795aec7639e79216e809dfdb4b540 -->

- 2026-10-05T14:18:04Z — 画面の段の道 /tables/{スキーマ}/{テーブル} を /tables?schema=&table=&item= に替えた; 道の中の %2F などが Spring Security の要求の検査で 400 になり、直接開く・再読み込みで画面を返せないため。functional-spec.md 7節 (b) に差として記録した。
<!-- aidlc-wave-memory:app-frame-ui:eb0f2fd59f42ad6453fe80db480a3cad2cf2ab0c28fe4fd57dd83fb17a7effbe -->

- 2026-10-05T14:18:04Z — mockups 1.1a のサイドバーの文と NavTree の error の［もう一度］をサイドバーに置かない; 5bf1ffe の navSections は項目しか描けないため、案内と［もう一度］はホームの画面に置いた（Q1 A）。7節 (a)・(d) に記録した。
<!-- aidlc-wave-memory:app-frame-ui:c4ee9f8a8eba7528b07503da6f42aa5e3464be417db2db19336de3f7c5cf6868 -->

- 2026-10-05T14:18:04Z — features/auth（ログアウトの道 /logout）に手を入れる; U7 の持ち物の外だが、U6 の S4 の保存していない変更の確かめをログアウトにも当てるため（Q6 B）。7節 (e) に記録した。
<!-- aidlc-wave-memory:app-frame-ui:82c4582ab5ba2bc3ab7f797a59c149e1de40ae631f3be4daedd878cd1929aa85 -->

- 2026-10-05T14:18:04Z — 10a 節の 768px 未満の重ねたサイドバーは部品に無い; e82b651・5bf1ffe とも AppShell の CSS に @media が無く、畳むか開くかの2つだけのため、360px のはみ出しを axe の検査で確かめる形にした（7節 (c)）。
<!-- aidlc-wave-memory:app-frame-ui:7def3b840a5796980f314b13aec87f46dc83fb728c7229214cc25122c79bb90a -->

- 2026-10-05T14:38:57Z — 承認の場の決定「推奨の案のとおり直す」で、実行時の検査の主体に管理者を足した（R-01）; AuthenticatedUserToken に admin() が真の AuthenticatedUser を入れた主体で、ADMIN の口は通ることを確かめる。あわせて、実行時の検査の口の集合を本番のクラスだけに絞り、静的な検査と一致させた（R-02）。
<!-- aidlc-wave-memory:cross-cutting:f643f1c6d6d347d1536cfc0e7a931f477d480caffd16b689796a07287d332819 -->

- 2026-10-05T14:39:43Z — 承認の場の Request Changes（推奨の案のとおり直す）で R-01・R-02・R-03・R-04 と memberUserIds の口を直した; DB の違反の後は巻き戻して書き込みの無い新しいトランザクションで失敗の出来事だけを出す形（BR5.7）、一意の鍵・主キーの待ちの上限切れも GROUP_BUSY、名前の鍵は Java の1か所で作り通常の列に保存した。R-05〜R-10 は直していない（functional-spec.md の 10節）。
<!-- aidlc-wave-memory:group:ccfb9fb14e6e79f0c8431247f38898a306f0ede5fb8ae5d1e2cf3aa196511999 -->

- 2026-10-05T14:39:30Z — 承認の場の Request Changes（推奨の案のとおり直す）で R-01・R-02・R-03・R-10 と、ロール1件の読み取り・木の名前の問い合わせの引数を直した; group の memberUserIds を使い、保存が同じロールを指すときだけ何もしない切り替えにし、外しの存在しないグループは ROLE_NO_CHANGE にそろえた。契約 C4・C7・C8 との差を functional-spec.md の 8節に、直しを 11節に記録した。
<!-- aidlc-wave-memory:role:b851f47013a6eeae11db69974a672a23e3868924af64b27a23e16bad7996a8ff -->

- 2026-10-05T14:20:00Z — 承認の場の Request Changes（推奨の案のとおり直す）で、置き場の名前の長さの上限 256 をやめた（R-01）; DSL の名前に長さの上限が無く、メニューに出るのに開けないテーブルができるため、無い・空だけを 400 にした。あわせて契約 C9 との差の表に 400 と BR4.3（一覧のファイルの誤りで起動を止める）を足した（R-02）。functional-spec.md の10節に記録した。
<!-- aidlc-wave-memory:navigation:07562747ba08a294a64ecb85406d36a191e2680b6faefb6bfac830d89db63f7f -->

- 2026-10-05T14:30:00Z — 承認の場の Request Changes（推奨の案のとおり直す）で、R-01 と権限の木の道を直した; GET /api/admin/roles/{roleId} が role の設計に入り前提が確定になった。権限の木の API は名前を問い合わせの引数で受ける形に変わり、role の直しが読めなかったため例の形で書いて確定の形に合わせると functional-spec.md 13節に記録した。
<!-- aidlc-wave-memory:role-admin-ui:63353fd5bf6680ee92e35d23d19253098782ec8ff3b13195b3648ff8f7ad9b74 -->

- 2026-10-05T14:40:22Z — 承認の場の決定「推奨の案のとおり直す」で R-01〜R-03 と S8 の道を直した; 作業ロールの Dropdown を work-role-switcher の入れ物で包み既存の E2E の開き口の探し方を B9 で書き換える（R-01）、開閉の照らし合わせを木を読み終えたときだけにし祖先は木が届いたときにも足す（R-02）、ロールが1つの説明を押せない項目の description にした（R-03）。S8 の道は問い合わせの引数の形の例で書き、role の確定の形に合わせる（functional-spec.md 11節）。
<!-- aidlc-wave-memory:app-frame-ui:a9b9c498b12c7dbb376d0911efe8b7fc8263192f23232b43f74e8f75c163f9d5 -->
## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->


- 2026-10-04T23:32:42Z — スキーマの数は意味の検証で1つに絞り、名前の違いは照合の警告にした（Q2: A）; dsl が対象DB の設定を知らずに済み結果が設定の有無に依らない代わりに、名前の違う DSL も適用できてしまう。後で複数を受け付けるときは決まりを緩めるだけで済む。
<!-- aidlc-wave-memory:dsl-v2:c158e7c8903ae75726d1f8d8d827d791c2027884c6ea9c554cdb81d6abae6e9f -->

- 2026-10-04T23:32:42Z — 外部キーと選択肢の参照は同じスキーマの中の名前のままにした（Q1: A）; 書く量と既存の検証の変更が少ない代わりに、スキーマの間の参照は後で項目を足して広げる必要がある。
<!-- aidlc-wave-memory:dsl-v2:21840a0693a517ec5af9216305a2f0231080434781712fb7556780c3c31da0d4 -->

- 2026-10-04T23:32:11Z — 実行時の検査を、要求を送らない WebInvocationPrivilegeEvaluator の判定にした（Q1 B）; 監査の行などの副作用が無く、PUBLIC の広げすぎも落とせる。代わりに、今の版で部品が使えるかは確かめておらず、使えなければ MockMvc に切り替える（BR1.5）。
<!-- aidlc-wave-memory:cross-cutting:6e7430575bdb82e2f2e0e3e46ff9b2639be66588940bfb91e2bd9860c40e97cd -->

- 2026-10-04T23:32:11Z — icon の実行時の照合の一覧を app/registry に 18 個で持つことにした; make-you-chic-ui の入口は型 IconName を出すが、iconRegistry の値は出していない。固定先を上げてアイコンが増えたら一覧を直す手間が残る。
<!-- aidlc-wave-memory:cross-cutting:95186ffc736f17a0e847e993c239725ab55f98913b31557869c34550b8d2d09d -->

- 2026-10-05T10:43:51Z — 同時の重なりはグループの行の排他を順番の決め手にし、外部キーは最後の守りにした（Q2: A）; 利用者の管理と同じ形で勝ち負けが確かめやすい代わりに、GROUP_BUSY の code と U4 が呼ぶ排他の口が増える。H2 の振る舞いは NFR 設計で捨ての試しのコードで確かめる。
<!-- aidlc-wave-memory:group:f538c57b1f24bc85588e91abf728733195c867b89a7f9b504ca80d84eb9b676d -->

- 2026-10-05T10:43:51Z — B3 は role のパッケージに仮の問う口の実装を置く（Q4: A）; develop がどの時点でも起動する代わりに、B5 で置き換え忘れる危険が残るため、B5 の終わりの条件に仮の実装が残っていないことを入れる。
<!-- aidlc-wave-memory:group:845e8c4d53e3d3b1fd49404024a95d1d9d3659bd015634f21d18da73b31f99e2 -->

- 2026-10-05T11:10:15Z — 作業ロールの切り替えは排他を取らず、保存からロールへの外部キーも置かない; Q3 A。切り替えは軽いまま、正しさは読みのたびの読み替え（C5）で守る代わりに、保存が割り当ての外を指すことがある。
<!-- aidlc-wave-memory:role:5ec205ced0e5dd99e6baa1d6343c60823a4b07d36458e846f4db9e996f55bbde -->

- 2026-10-05T11:10:15Z — また割り当てたら覚えていた作業ロールに戻る形を受け入れた; Q9 A。読み取りで状態を変えない決まりを保つ代わりに、利用者は自分で切り替えずに作業ロールが変わることがある。
<!-- aidlc-wave-memory:role:69d3159a9d88ed3b408f92555672442493c8792986a85b4b36ceea1541365e87 -->

- 2026-10-05T11:10:15Z — import の監査は1回1行の要約にした; Q6 A。detail の上限に必ず収まる代わりに、前後の値の全件は監査だけでは分からず、ファイルの SHA-256 で照らす。
<!-- aidlc-wave-memory:role:b03bad0320142a1d642b6f77743f0f22e605bd1499f92f33fd27596cbdbb3a76 -->

- 2026-10-05T11:10:15Z — 一意の違反の後は巻き戻して新しいトランザクションで失敗の出来事を出し、名前の鍵は Java で作って通常の列に保存する; group のレビューの R-01・R-02・R-04 の手当てを先取りした。group の承認の場の決着が違えばそろえ直す。
<!-- aidlc-wave-memory:role:f9da8f6973fb481c28edba8ed37f430cf4fa2419b9edc6290b7b47d4ac1f7885 -->

- 2026-10-05T13:50:00Z — 項目の id を絞る前の DSL の木での位置の道にした（Q2 A）; 作業ロールを切り替えても同じ項目の id が変わらず開閉の状態が保たれる代わりに、DSL を適用し直すと id が変わり、開閉が閉じに戻りうる。前置きは U7 が付ける。
<!-- aidlc-wave-memory:navigation:9040ce3af2946caf655b434f138fe464a1c6087b781a5c11559e3cf0b436ff2d -->

- 2026-10-05T13:50:00Z — current() と写しの DSL の識別が食い違っても読み直さない（BR1.5）; 読み直しの手間を省ける代わりに、適用の直後の1回は古いメニューの形が返りうる。新しい DSL に無いテーブルは写しが NONE を返して落ちるため、権限の無い項目は出ない。
<!-- aidlc-wave-memory:navigation:6f3e05777990e852d05fdcdfd4d40b6ea77582f8c493effaeb9e0e978461204d -->

- 2026-10-05T13:50:00Z — 一覧のファイルと画面の一覧の一致のテストを、ファイルを作る U5 の B7 で frontend/src/app/registry/ の下に置くことにした（Q5 B）; service の単位が画面のテストを1件持つことになるが、ファイルと同じ Bolt で一致を関門に入れられる。
<!-- aidlc-wave-memory:navigation:47501be2a3f08611a2af4874349ba8958403e0c755673931e87e453e9089e09f -->

- 2026-10-05T13:47:26Z — AC1.2.15 を満たすためルーターを data router に替える（Q2 B）; 画面の外の移動も止められる代わりに、B8 で骨組みの入口 main.tsx に手が入り、U7 の作業と同じ場所になる。コード生成の最初に確かめ、だめなら画面の中の移動だけに狭める（7.3）。
<!-- aidlc-wave-memory:role-admin-ui:ca32c5fc926f33923152fdb0df835720ade711cef152a7172bdc0e08790113b3 -->

- 2026-10-05T13:47:26Z — 誤りの一覧・ファイルの保存・候補の Modal・名前の Modal は機能ごとに持ち、dsl・ほかの機能から import しない; ESLint の制限と dsl に手を入れないためで、似た形の部品が機能ごとに重なる。名前の検査の純粋な関数だけは shared/validation に置く。
<!-- aidlc-wave-memory:role-admin-ui:4d3cb5487bcf11fd176cd6ffec765fbc747a4bfb1e04328d97a6ec1fd7afbd17 -->

- 2026-10-05T13:47:26Z — 候補は1人ずつ足し Modal を開いたままにした（Q3 A）; API と同じ単位で失敗が1件ずつ分かる代わりに、多くの人を足すときは押す数が増える。
<!-- aidlc-wave-memory:role-admin-ui:6f13384eb6db1dc645fe1078bceccccadd734a22965d7f845802bceb4f5c5395 -->

- 2026-10-05T14:18:04Z — 画面の道が変わるたびに作業ロールと業務のメニューを読み直す（Q2 A）; 管理者の変更が次の画面の移動で表示に出る代わりに、移るたびに要求が2本増える。どちらも 1 秒の目標の軽い API で、利用者は 50 名程度のため受け入れた。
<!-- aidlc-wave-memory:app-frame-ui:752d378b655ca2d5d9dea57d3f409e302aa881b8285ad8786c2d7f4b77684c7b -->

- 2026-10-05T14:18:04Z — make-you-chic-ui の残り3点は追加で依頼し、待たずに 5bf1ffe で進める（Q4 A）; 畳んだ状態の aria-labelledby は axe の違反になりうるが未検証で、違反なら B9 の時点で依頼者に諮る。依頼文は make-you-chic-ui-request-2.md。
<!-- aidlc-wave-memory:app-frame-ui:99454a8fff8be1c3bf77b1976622a99d15fb44a05746f488f42c9e4f1b215c84 -->
## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->

- 2026-10-04T23:32:42Z — dsl-schema-v1.json の公開を止めると、エディターで v1 の URL を参照している利用者の補完が切れる; 版 1 は受け付けないため置かない形にしたが、README の案内で足りるかを Code Generation で確かめる。
<!-- aidlc-wave-memory:dsl-v2:659cd1ffe57fe031f726a0f237444424da882f4327359bda3e48f185d444a715 -->

- 2026-10-04T23:32:42Z — 負荷の試験の道具（perf の DSL を作るスクリプト）の版 2 への書き換えが B1 の作業の量に入る; Code Generation の計画で見積もる。
<!-- aidlc-wave-memory:dsl-v2:0ca4d700b721398de3adf069144b53d015f8614e8174d42ddb12897fc61013ed -->

- 2026-10-04T23:32:11Z — MockMvc に切り替えたときの PUBLIC の判定で、口の中の業務の 401（リフレッシュの Cookie が無いときなど）と安全の決まりの 401 をどう見分けるか; コード生成の最初の試しで確かめる。判定の部品が使えれば起きない。
<!-- aidlc-wave-memory:cross-cutting:0577e2eec37ea9992d4c620a65f4bbe4419d6bceee964cb4701f6682586e32f9 -->

- 2026-10-04T23:32:11Z — navSections に組み立てるときの「ホーム」の置き場と、業務と管理のメニューの id の重なり; U7 の設計で決める。S1 の画面イメージにはホームが無いが、今のコードは先頭に必ず置いている。
<!-- aidlc-wave-memory:cross-cutting:157d952e40c33b74c7a1b6ffbea87b15af36ea209b555535dc0a1ba02dceaa49 -->

- 2026-10-04T23:32:11Z — 分類は3つの値だけ; 後の Intent（J・K）で業務データの API を権限で守るとき、値を足すか、AUTHENTICATED にして中で判定するかを決める必要がある。
<!-- aidlc-wave-memory:cross-cutting:842354b6fb4256a8181d44b5917a0098fb0a38f54793b11edf14fde98d28de8b -->

- 2026-10-05T10:43:51Z — グループの名前は管理者が自由に入れるため、名前にメールアドレスなどが入ると監査の detail にそのまま残る; 伏せる規則は入れる項目を型で限ることで守り、名前の中身までは見ない。承認の場で受け入れてよいかを確かめる。
<!-- aidlc-wave-memory:group:017e5238d95f1a6589eb4824148ade76d27447240dcfe591a85e18f86886e078 -->

- 2026-10-05T10:43:51Z — 認可の 403 を確かめる「作業ロールを持ち管理者の印だけを欠く利用者」は B5 まで作れない; B3 では管理者の印を持たない利用者で確かめ、B5 で作業ロールつきの利用者を表に足す形にした。
<!-- aidlc-wave-memory:group:6d053b163a85af6dff24a7ddc5c9b6338b3cc6e3036a2cdb7e04ef4a6f2252fb -->

- 2026-10-05T11:10:15Z — YAML の大きさの上限は仮に 10 MiB; NFR2.4 により NFR 要件の段で応答時間とあわせて確定する。書き出しの理論上の最大（約 40 MB）が読み込みの上限を超えうる点も引き継ぐ。
<!-- aidlc-wave-memory:role:420f54500a79479bd7cd1dd8d6acb9dd503a738ed0a6ec7988769c3eee1ca8de -->

- 2026-10-05T11:10:15Z — 作業ロールを持ち管理者の印だけを欠く利用者は B5 まで作れない; B4 は管理者の印の無い利用者で 403 を確かめ、B5 で表に足す（group と同じ扱い）。
<!-- aidlc-wave-memory:role:073df7025fe405411a9d24d33e65ed894476e6840fc683709f3b0e82f433ab55 -->

- 2026-10-05T11:10:15Z — 同じ名前の同時の作成・同じ組の同時の割り当てが、違反になるか待ちの上限切れになるかは未確かめ; NFR 設計の捨ての試しのコードで確かめる（project.md の学び）。
<!-- aidlc-wave-memory:role:ec99f9e2812d3ae8838fa1f491e6470cac4779a22eda2597192a43c429b8a242 -->

- 2026-10-05T13:50:00Z — 画面の道 /tables/{スキーマ名}/{テーブル名} が要求の検査で 400 になりうる; 名前に /・%・..・; があると、直接開く・再読み込みで index.html の前に REQUEST_REJECTED になる。AC5.1.8・AC5.1.13 に関わり、U7 の機能設計で道の形を決める必要がある（functional-spec.md 9節）。
<!-- aidlc-wave-memory:navigation:8898bbb10ab1b24a057c72a954267e0ad2a6ba2ed5c45ac35adb75c15c6bd91e -->

- 2026-10-05T13:50:00Z — 同じテーブルを指す項目が複数あるときの今の項目と、label が空のときの既定の表示は U7 が決める; dsl-v2 は同じ組の重なりを禁じておらず、表示名の空の文字列も許すため。
<!-- aidlc-wave-memory:navigation:c5fbbc10cfd12195fd5d29d90250f52e5303abdc4bed22215b53628983fb87e1 -->

- 2026-10-05T13:50:00Z — 一覧のファイル（.txt）のライセンスヘッダーの検査の対象にするかはコード生成の計画で決める; # のコメント行でヘッダーを書き、読むときに飛ばす形にした。
<!-- aidlc-wave-memory:navigation:b7c86b37665ab91922e0444efd58f73bc05504b3eb738ab24cc143b2a5b22a45 -->

- 2026-10-05T13:47:26Z — 確かめの応答（TransferCheck）は全変更を1回で返すため、大きい YAML で応答と描画が重くなりうる; 画面は 100 件ずつ描くが応答の大きさは減らせない。U4 の NFR 要件（NFR2.4）の論点として引き継ぐ。
<!-- aidlc-wave-memory:role-admin-ui:c913109278efe31a6cfb777f364c4b18d67fb7c25ea4d359389e080a954b2145 -->

- 2026-10-05T13:47:26Z — 詳細の道（/admin/roles/:roleId など）でサイドバーの今の項目をどう示すかは U7 が決める; 前方一致で今の項目とするかを functional-spec.md 11節で引き継いだ。
<!-- aidlc-wave-memory:role-admin-ui:f66b30676a3a17f05165a02501fa16c056c7171425e88d4973a7d77440050931 -->

- 2026-10-05T13:47:26Z — 骨組みのログアウトは道を変える前にトークンを捨てうるため、未保存の確かめが出ないことがある; U7 の設計でログアウトの順を決めるかを 11節で引き継いだ。
<!-- aidlc-wave-memory:role-admin-ui:4e5e5e3ef0f650a809d6cc4c246c35ccce1dc1cde504c849ba91abf7927e3f23 -->

- 2026-10-05T14:18:04Z — S8 の木の道（U4 の /api/me/permissions/schemas/{schemaName}/...）は要求の検査に当たる名前で 400 になりうる; navigation は引数の形にしたが契約 C8 は変えていないため、S8 では読み込めない表示になる（7節 (j)）。U4 の道を直すかは承認の場で確かめる。
<!-- aidlc-wave-memory:app-frame-ui:ac5dadf8050ea63b6271bd9ad8bab3fa05ec95b1b1a88dfb0f5a264abf8b4156 -->

- 2026-10-05T14:18:04Z — MyPermissionNode の displayName の形と create・delete の型は契約 C8 で決まっていない; 画面は {ja, en} と真偽値を仮に置き、U4 の確定の形に合わせてコード生成で直す（frontend-components.md 2節）。
<!-- aidlc-wave-memory:app-frame-ui:0984c02c9b94eb3349d5926ae3838927421f89971eea61958a28633c6ed7158e -->
