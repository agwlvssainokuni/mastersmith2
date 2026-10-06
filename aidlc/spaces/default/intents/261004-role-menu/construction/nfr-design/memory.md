<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->


- 2026-10-05T22:58:23Z — 静的な検査と実行時の検査の口の集合は、道ではなくクラスと方法で比べると決めた; 静的な検査は ${server.error.path:/error} を既定値で読み、実行時は解決した値を読むため、設定を変えると道が食い違いうる。PUBLIC の一覧は実行時の検査の中で解決した道の型で比べる。
<!-- aidlc-wave-memory:cross-cutting:50feb03148dbcef859de070015bc5ecddbed13976989f0db21634a203f612499 -->

- 2026-10-05T22:58:23Z — NFR 要件の読み直しの Minor・Suggestion（R-02〜R-07）を、要件の文書は書き換えずにこの段の設計の中で手当てした; R-02 は試しの点、R-03 はアイコンの照合、R-04 は持ち主無し、R-05 は深さ、R-06 は ESLint のテスト、R-07 は広げ方として security-design.md に書いた。
<!-- aidlc-wave-memory:cross-cutting:8663da0024ff3ffb2cff72532694181e1e17db112274cbad2d663a190e546dd0 -->

- 2026-10-05T23:03:38Z — library の単位のため性能・画面・テストの設計を logical-components.md、セキュリティ・ログ・信頼性を security-design.md に割り当てた; 段の定義の produces_kinds で performance・reliability・observability の文書を作らないため。割り当ての表を security-design.md の 2節に置いた。
<!-- aidlc-wave-memory:dsl-v2:3efeed4232fd973e951c88836578c5c8b66d5307e404f410f952a6090816e5bd -->

- 2026-10-05T23:23:24Z — 同じ名前の同時の作成・変更で重なった側の 409 は、先の側が約 2 秒以内に確定すれば GROUP_NAME_DUPLICATE、そうでなければ GROUP_BUSY と読んだ; 捨ての試しで両方が起きると確かめたため（T1・T2）。NFR 要件の読み直しの R-07 として、この段の承認で確定する。
<!-- aidlc-wave-memory:group:548461ce82de9681f2e1a8530ea7b3704ca427e39dab078da072661286970b56 -->

- 2026-10-05T23:41:00Z — 捨ての試しで YAML のキーは型によらず書いたとおりの文字列になると分かった; 今の変換の規則がキーの元の文字列を使うため。読み込みで型のキーを誤りにする手当ては要らず、書き出しの二重引用符だけで往復を保つと読んだ。
<!-- aidlc-wave-memory:role:49fd70c425f4ad44d9fc4beaea01651f45842d6117d544d9fd35b0b78b303de4 -->

- 2026-10-05T23:41:00Z — 別名 0 はコレクションを指す別名 0 と読んだ; 試しでスカラーの別名は受け付けられ節も増えず、コレクションの別名は SnakeYAML の上限で拒否された。
<!-- aidlc-wave-memory:role:1c3f9b3e4b2ad6504b16f9b1b6e75d7d430e47177f1c3005595160f1e6350e82 -->

- 2026-10-06T13:18:24Z — 段の定義が必須とする scalability・reliability・observability の要件は ui の単位に無いため、performance・security・tech-stack の3つを入力にした; NFR 要件の段の produces_kinds で ui はそれらを作らない。各成果物の出典に書いた。
<!-- aidlc-wave-memory:role-admin-ui:9559325578ba7bb04efc1a95235c8c88df095a30d6d59f8fa50863e8e1548222 -->

- 2026-10-06T13:18:24Z — S7 の ROLE_BUSY は RoleTransferSlot と行の排他を区別しない文言にした; 同じ code で画面からは見分けられず、どちらも少し待ってやり直せば済むため。
<!-- aidlc-wave-memory:role-admin-ui:d852c7a38f5fdac210ddb59c45cc23f283a012c6d2b49f88584ef61a604ad886 -->

- 2026-10-06T13:30:00Z — navigation の R-08 は role の NFR 設計の Q2 A で決着したと読んだ; role の引き継ぎが「対象が1つなら resolve でよい」に改められ、置き場の resolve 1回と合う。security-design.md 2節と logical-components.md 4節に経緯を書いた。
<!-- aidlc-wave-memory:navigation:2136bac56bf1ebb6e14aff58e833b7f84beb4550cc38ac6f653201a38d07299a -->

- 2026-10-06T13:30:00Z — 捨ての試し N1 で印字できる文字・制御文字のどれも拒否されなかったため、Q3 A の「base64url への切り替えを諮る」条件には当たらないと読んだ; /api の下でも 23 種すべて 401（要求の検査の 400 は 0 件）で、StrictHttpFirewall は引数の値を拒否しない。
<!-- aidlc-wave-memory:navigation:b2da7eb73546e12a84f5c2ebcc74dd9604d7511eef34f6a1745b1bc3186249eb -->

- 2026-10-06T13:36:46Z — ui の単位のため scalability・reliability・observability の要件と設計は無く、performance・security・tech-stack の3つの要件を入力にした; 段の定義が必須とする入力が無いことを各成果物の出典に書いた（U6 と同じ）。
<!-- aidlc-wave-memory:app-frame-ui:20a5d8aaf8284e1f6cc395844261ece18a32b712cf5384c9bbf287d4e6abba30 -->

- 2026-10-06T13:36:46Z — 測り終わりの要素は 5bf1ffe の Sidebar のソースで決めた; 開いたまとまりにだけ子の ul（id は aria-controls と同じ）が描かれ、Toast は既定 4 秒で消えるため、見張りは押す前に入れる形にした。
<!-- aidlc-wave-memory:app-frame-ui:dba9d923530d7b4ed5a81107b3daa17c7fd0de0430d873bf27e4a8164c3b450a -->

- 2026-10-06T13:53:49Z — 承認の場の決定「推奨の案のとおり直す」で R-01 を直し、検査の検査を設計に足した; 規則を ApiAccessRules の1か所で作り、本番のクラスと違反の見本の両方に当てる。PUBLIC の一覧の比べは純粋な関数にして、4つの境界を Spring を起動しない単体のテストで確かめる。見本の @RestController は既存の TestFixtureEndpoints と同じく、決して設定しない条件で Bean にしない。
<!-- aidlc-wave-memory:cross-cutting:893010f3fe8484c79a43281ba0407462338ceadd49f48eec6d82086fcba624fc -->
## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->


- 2026-10-05T22:58:23Z — 試しで判定の部品が使えたため、要件 NFR1.4・機能設計 BR1.5 の MockMvc への切り替えは発動しない形にした; 決まりは手順として残し、文書は書き換えずに security-design.md 8節に差を書いた。未ログインの主体は匿名のトークンで渡すと決めた（null でも同じ結果）。
<!-- aidlc-wave-memory:cross-cutting:8f05c6402b73dc6970c2e8e8a004a0e7d47245cbf9b6ce102950a253139430aa -->

- 2026-10-05T22:58:23Z — 試しの写しでは .env と aidlc・reference を写さなかった; 秘密の値と記録を外へ出さないため。試しの主体のメールアドレスは example.com の見本の値で、結果のファイルにも秘密の値は無い。
<!-- aidlc-wave-memory:cross-cutting:03a2cfd2a9f7a6c760b6b5ec3e4bd5a8ce3bdb26f5d2dec5332550ceac0f0cc6 -->

- 2026-10-05T23:03:38Z — 機能設計 BR3.6 の appliedUnreadable の判定を、起動時に読めなかった版の ID との比べに変えた（Q1: A）; 識別を比べる形は適用の確定から提供口の差し替えまでの間に誤って真になるため。承認済みの機能設計は書き換えず security-design.md の 5節に差を書いた。
<!-- aidlc-wave-memory:dsl-v2:bd0e4398081b0a55c6389fd40caee50b9c5b8f400d2d15ce567f339c500b3c93 -->

- 2026-10-05T23:03:38Z — 捨ての試しの T3 は本文を約 8.2 MB で比べた（10 MiB に届かない作り）; 版 1 と版 2 の比べには足りると判断し、10 MiB の実測は Build and Test に任せた。
<!-- aidlc-wave-memory:dsl-v2:7740e5f568edee38ef511169fd3a2eae69d7ecb88eb6f3fc9343659c7219a628 -->

- 2026-10-05T23:23:24Z — 一意の鍵・主キーの待ちの上限を承認済みの「3 秒」ではなく H2 の既定の約 2 秒のままにした; 捨ての試しで書き込みの待ちにはヒントが効かず約 2,000 ms で切れ、SET LOCK_TIMEOUT はプールの接続に残ってほかの機能に響くため。差は reliability-design.md 1.3 と logical-components.md 4節に記録した。
<!-- aidlc-wave-memory:group:1717aac2e4867e31e07512c4413527f61f1a37465e4e53e46094975ab64a7c48 -->

- 2026-10-05T23:23:24Z — Q1: A で、承認済みの NFR2.7 に上限ちょうどの 5 VU の回を足し、20 VU の回を上限に届いた証拠つきの記録の回に変えた; 差を scalability-design.md 2.3 に記録し、U4 にも同じ形を勧めた。
<!-- aidlc-wave-memory:group:aef0b42426fffa87adb1adede5b62b02907fd5c59e7f56d2122c8896ea03aa7e -->

- 2026-10-06T11:33:51Z — 読み直し1回目（NOT-READY）の指摘 R-01〜R-10 を、依頼者の決定（Q5: A と、指摘をすべて直す）で直した; 巻き戻しの印は service の部品 RoleStoreTransactions の status.setRollbackOnly に集め、待ち合わせの口を4つにして API で届く重なりの表と (b)・(g) の期待を確定した。RoleTransferSlot の根拠はヒープ約 1 GiB に直し、本文を読む前に取って finally で放す形にした。各成果物の末尾に直しの節を足した。
<!-- aidlc-wave-memory:role:328921a5452d6c0aaefed5cabbd768724857f10bd30bca73a901e6a98c6bd4c5 -->

- 2026-10-05T23:41:00Z — 確かめと適用を同時に1つずつしか通さない RoleTransferSlot を足した; 試しで1回の確かめにヒープ 512 MB の半分ほどを使うと分かったため。承認済みの要件に無い決まりで、reliability-design.md 6節と security-design.md 7節に記録した。
<!-- aidlc-wave-memory:role:986d476d268bd2990cd67c8002a6e8d67ee963d7278f1e5611e2566b614b8eba -->

- 2026-10-05T23:41:00Z — 巻き戻しの印を業務処理ではなく role/store の中で付ける形にした; group の NFR 設計の読み直しの R-05 の手当てで、group の今の設計とは違う。
<!-- aidlc-wave-memory:role:2beee19e6c28965c21ac251db595766df0a7d37711a8a5f2f4660f34b7ffd6df -->

- 2026-10-05T23:41:00Z — rolePoolLimit の合否の回を上限 11・5 VU・acquire の最大 20 ms 未満と時間切れ 0 にした（Q1: A）; 承認済みの NFR2.7 と group の今の形との差を scalability-design.md 2.3・2.4 に記録した。
<!-- aidlc-wave-memory:role:a2d786830da13c6db51767595ed85ca721b270bc0802d32f20674fe2da7a4cfd -->

- 2026-10-05T23:41:00Z — 試しは Spring Boot と Hibernate を使わず HikariCP と JDBC で行った; 3つの範囲は JDBC で確かめられ、2 時間の上限の中で終えるため。本番の上乗せは目標の 10 分の 1 未満の値から見通した。
<!-- aidlc-wave-memory:role:0c31f6ebfdcda37d5edd620d7c648ec1b20ad2b8afcbbf35a3d33b7b211a97ce -->

- 2026-10-06T13:18:24Z — 適用で送る本文を、確かめのときに読んだ文字列にした（Q1 A）; 機能設計 W7.4 の「同じファイル」を読んだ本文と決めた。ファイルを書き換えたら選び直す案内を足した。
<!-- aidlc-wave-memory:role-admin-ui:ed56be853bca28dba9fc9fcf82021dee3dafd22d52639b95d306d0c31a52222c -->

- 2026-10-06T13:18:24Z — make-you-chic-ui の Tabs は矢印のキーでその場で選ぶため、未保存で止めたときに「留まる」の後のフォーカスを今のタブへ戻す決まりを足した; 機能設計 7.2 に無い細部で、ソースの focusAndSelect で確かめた。
<!-- aidlc-wave-memory:role-admin-ui:2f2535c68345132de3ecf204b688503bf661852c425382e23d73ed08cced7b4f -->

- 2026-10-06T13:30:00Z — NFR1.12 の測り方を承認済みの要件（メニューの応答から期待を作る・4つの組）から、期待の表・7つの組に変えた（Q1 A）; 要件の書き方はメニューに出ない READ のテーブルを 403 と誤るため。承認済みの文書は書き換えず logical-components.md 4節に差を書いた。
<!-- aidlc-wave-memory:navigation:e17953279e5e2f8ef7bbaef0172a5a566612e78b7d5b3d622bba1c3f08407e46 -->

- 2026-10-06T13:30:00Z — NFR2.6 の測り方を「解決の口の途中で止めて HikariCP の使用中の数を読む」から、テストだけの DataSource の包みでスレッドごとの最大を数える形に変えた（Q2 A）; role の RoleBarrier に読み取りの途中の点が無く、瞬間値はほかの要求の接続も数えるため。
<!-- aidlc-wave-memory:navigation:94f1cd4a7f2860830c03e1d8633ba04e166cc5f167c35826c5e5bb704801775d -->

- 2026-10-06T13:30:00Z — 空の引数を画面入出力の層で明示して確かめることにした; 試し N2-f で Spring の結び付けが table= の空を拒否せず 200 になったため。BR5.1 の中身は変えない。
<!-- aidlc-wave-memory:navigation:8b5251b1d3db55fa45bba46a4fbb80337ca5401e3869398a2f5b3fd15b4793d8 -->

- 2026-10-06T13:36:46Z — 承認の場で直さなかった NFR 要件の Minor（R-04・R-05・R-07・R-08・R-09）を、この段の設計に入れた（Q2 A）; 要件の文書は書き換えず、各設計の文書の上流との差に記録した。
<!-- aidlc-wave-memory:app-frame-ui:92783e10161d6edfb781869cb05c3cd083e1ef514d59e9632cbea47b30b669e7 -->

- 2026-10-06T13:36:46Z — ログアウトの印を骨組みから消すため、登録の型の LoginStateProvider に任意の clearLogoutIntent を足す; 骨組みから features/auth を import しない向きを保つため、今の logout と同じ形にした（logical-components.md 3節）。
<!-- aidlc-wave-memory:app-frame-ui:a25204e63696dc8f184223b699e5f99ba85d94857e94c1566f6de3fe5f0661b4 -->

- 2026-10-06T13:57:17Z — 承認の場の Request Changes（推奨の案のとおり直す）で R-01 を直した; 捨ての試し T4 で 10 MiB の読み込みと検証のヒープを測り（1件 256〜384 MB、2件同時は 512 MB で OutOfMemoryError・1 GB で通る）、既存の DslHeavyOperationGate で同時の投入は1つに絞られることと、適用の前の読み直しと RoleTransferSlot との重なりを受け入れた制約として security-design.md 4.9 に書いた。Build and Test で本番と同じヒープで測る引き継ぎを logical-components.md 4節に書いた。
<!-- aidlc-wave-memory:dsl-v2:2fd4849dc412982d49b1a9a7689f18623a12b38f32d1c1138cb55cc66fb61a20 -->

- 2026-10-06T13:57:14Z — 承認の場の Request Changes（推奨の案のとおり直す）で、接続の合否を U4 と同じ上限 11・書き込み 5 VU（時間切れ 0・500 が 0 件・acquire 最大 20 ms 未満、pending は参考、20 VU は記録だけ）にし、同時の重なりのテストを時間に頼らない形（待たない違反・先の側を放さない上限切れ・4つの点の待ち合わせの口）にし、巻き戻しの印を GroupStoreTransactions に集めた; API 経由では同じメンバーの追加の主キーと削除の外部キーの違反は行の排他のため届かないと確かめ、store を直接呼ぶテストにした。鍵の待ち約 2 秒と 409 の code の分かれは受け入れた振る舞いとして記録した。
<!-- aidlc-wave-memory:group:83b9a8abf32f3588f47e5c7a77cbda493707e6a0d1e7289fedfb449912153f88 -->

- 2026-10-06T13:54:49Z — 承認の場の Request Changes（推奨の案のとおり直す）で、鍵・主キーの待ちの上限を約 2 秒の決まった値として書き、group は role の合否の形と重なりのテストの形にそろえたと直した; あわせて R-02 の表を見直し、#9 の MERGE の待ちを B5 の始めに確かめること、#10 の逆の順を合図なしで放すことを足した。RoleStoreTransactions と ArchitectureTest の整合は確かめて変更なし。
<!-- aidlc-wave-memory:role:9cc38c7f2de85de11d36b522546c8db27421f4598a141ad6a977dcee9affd35d -->

- 2026-10-06T15:00:00Z — 承認の場の Request Changes（推奨の案のとおり直す）で R-01・R-02 を直した; R-01 は接続の数えに測る直前の reset() と要求の頭 X-Test-Measure-Id による印ごとの数えを足し、ログインで2本を使った同じスレッドの最大が残らない形にした。R-02 は正しくない % の並びで値が ERROR に出る件を依頼者の決定（案 1）で受け入れた制約とし、NFR1.8 を「部分」にした。認証の前か後かは試しで /api に送っていないため未確認と書いた。
<!-- aidlc-wave-memory:navigation:88b576758f19aee23dfc5bb4c8b27a51e240a54312043fa3980d3b997aaf17ec -->

- 2026-10-06T14:00:00Z — 承認の場の Request Changes（推奨の案のとおり直す）で R-01・R-02 を直し、画面の時間の測りを app-frame-ui の Q1 A の形にそろえた; 時計は画面の中の performance.now の1つ、測り始めは click・change の捕捉の段か要求の responseEnd、見本は前もって直列化し adminApiRoute.ts に body を受ける形を足す、判定は 5 回の中央値。
<!-- aidlc-wave-memory:role-admin-ui:c6a1dd4b036635f070c9b432c23cf236c55cd85180570b05c5ce4bee3c19980a -->

- 2026-10-06T14:00:00Z — 承認の場の決定「推奨の案のとおり直す」で R-01〜R-03 を直した; 今のコードでは path の項目に移る直前の口も useLogout も無いと確かめ、path の項目に任意の beforeNavigate、LoginStateGate に useClearLogoutIntent を足す設計にし、LogoutPage は authSession.logout を直接呼ぶ。測り始めは操作の直前に入れる click の捕捉の聞き手で、目当ての要素のときだけ記録する（role-admin-ui もそろえる）。
<!-- aidlc-wave-memory:app-frame-ui:095b940472d3042981e744e7cde0461d496aba43ad64094677adfa21fc7dd4e8 -->
## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->


- 2026-10-05T22:58:23Z — 実行時の検査では内部DB に利用者を作らず、主体の値だけで判定する形にした; 判定は AuthenticatedUser の admin() だけで決まると試しで確かめたため。代わりに、停止中の利用者の拒否（アクセストークンの認証）はこの検査では見ず、既存の auth のテストに任せる。
<!-- aidlc-wave-memory:cross-cutting:f0899fb2e2b2e583a722f5bc89f7e767c2fea426627ed1482087b97749a111fe -->

- 2026-10-05T22:58:23Z — 共有の木に深さの上限を置かなかった; 深さは呼ぶ側のデータで決まり、開いた節だけを描くため。メニューの木の深さの上限は U2・U7 が持つ。
<!-- aidlc-wave-memory:cross-cutting:b985e4fe8ca0cf11c8d9f3175e745e26a9a09a2abd93ff76a1c4a643e1883ac0 -->

- 2026-10-05T23:03:38Z — 深さと別名の上限を LoaderOptions と LimitingParser の両方に同じ値で渡す形にした; 試しの T1' で LoaderOptions の方が小さいと部品の例外（位置なし）が先に出て SYNTAX に写るため。値を2か所に渡す手間の代わりに、区分と位置を必ず保てる。
<!-- aidlc-wave-memory:dsl-v2:c145a85133dbf14f924e144f4f9d0b6513739e24a9e11efdc773643fde1e9e30 -->

- 2026-10-05T23:23:24Z — 違反・上限切れを起こしうる書き込みと排他を TraceAspect の対象の外の group.store にまとめた（Q3: A）; 例外の文（行の値が入ることを試しで確かめた）が TRACE に出る経路を構造の検査で閉じられる代わりに、Spring Data の書き込みの方法を使わず EntityManager を直接使う部品が1つ増える。
<!-- aidlc-wave-memory:group:7ae8a52098f3dd1b79c24568b1c832de52ca79248b63bc4dd83788de1b1c0aeb -->

- 2026-10-05T23:23:24Z — 捨ての試しは Spring Boot の最小のアプリで約 10 分で7項目を確かめ、場所を消した; 素の JDBC では分からない Hibernate の例外の型と、setRollbackOnly を付け忘れると UnexpectedRollbackException になることが分かった（T5・T7）。
<!-- aidlc-wave-memory:group:1224b72a530b9eb483b7b6d700b5be3a5cfb0fd6c35e59f685922afbe2e0ecee -->

- 2026-10-05T23:41:00Z — import の書き込みを role/store の中の JDBC のバッチ（1,000 件）にした; 試しで 26 万件の適用が約 3.7 秒・1文の最大 57 ms で、エンティティを作らずに済む代わりに、JPA と JDBC が同じトランザクションの接続を使う前提が要る。
<!-- aidlc-wave-memory:role:017bd68d1140171f64ca5dd856be6405c90eac3735e7fb5ef1fd7fb033a7d0ac -->

- 2026-10-05T23:41:00Z — 写しと resolve の2つの口はそのままにし、一致を EffectivePermissionConsistencyIT の期待の表で守る（Q2: A）; 予算と承認済みの形を変えない代わりに、2本の実装の一致をテストで保ち続ける必要がある。
<!-- aidlc-wave-memory:role:c1afd85b3f975b5ea4932390872e298cdad2bde23b11438e5efd633a138c213a -->

- 2026-10-05T23:41:00Z — 違反のテストは先の側を確定させてから書く待たない違反、上限切れは H2 の上限まで放さない形にした（Q3: A）; 時間の境に合否を預けない代わりに、待った後の違反の経路は区分の単体テストだけで確かめる。
<!-- aidlc-wave-memory:role:9a53f47657b3f86e7f1ead0a89b8d7650f29015466adbe422e072118a676f1e5 -->

- 2026-10-06T13:18:24Z — 最新の読み込みと待ちの案内の小さな部品は、shared に置かず3つの機能に同じ形で持つ; 機能どうしの import の禁止と shared の変更を2つに抑えるためで、同じ形のコードが3か所に並ぶ。
<!-- aidlc-wave-memory:role-admin-ui:6414a734236c94c7d56fce6556420ba7a52ac65014dbd0e8afcad3abdccb8d34 -->

- 2026-10-06T13:18:24Z — 古い答えは世代の数で捨て、要求は取り消さない; ApiClient に取り消しの口が無く書き換えはサーバーで確定しうるため。不要な応答の受け取りは残る。
<!-- aidlc-wave-memory:role-admin-ui:93b94d4b82baf9db55443cc106c3b938c8829588f971450518afd4a06ffd3b44 -->

- 2026-10-06T13:30:00Z — 待ち合わせの口 NavigationBarrier を本番のコードに何もしない部品として置いた（Q2 A）; role の承認済みの設計に手を入れずに DSL の差し替えの重なりを決定的に作れる代わりに、本番に使わない口が1つ増え、境界テストとカバレッジの対象になる。
<!-- aidlc-wave-memory:navigation:d49c371e0611211c6c920ef6f072e9a27b92a403af7de4b1405bc796d54d0061 -->

- 2026-10-06T13:30:00Z — 一致のテストの組を7つにし、口の値の網羅は role の EffectivePermissionConsistencyIT に任せた; HTTP を通した側で上書き・カラムだけ・DSL なしまで確かめる代わりに、悪い側の組の用意は SQL で直接入れて時間を抑える。
<!-- aidlc-wave-memory:navigation:a530857f810e2ad132f04ca1e2c4c196f36e966c89f4034f46191be29c3ece1c -->

- 2026-10-06T13:36:46Z — 画面の時間の時計を画面の中の performance.now() の1つにした（Q1 A）; 0.2 秒の目標を Playwright の待ちと往復に左右されずに測れる代わりに、U6 の測り方（テストの側の Date.now()）とはそろわない。
<!-- aidlc-wave-memory:app-frame-ui:5cdb65cd740db2de3b1ea1646dfe53e03b8b6233341227a9c081f16e66e66301 -->

- 2026-10-06T13:36:46Z — E2E の番号を 160（検査）・170（I の流れ）にした; U6 の 140・150 と重ならず、DSL を自分で適用するほかのファイルより後に流れる。計画の最初に U6 の案と突き合わせて確定する。
<!-- aidlc-wave-memory:app-frame-ui:ea5c738ba9d1281ffa9cde21a433be6206323fa84e520ac09b715e0cd8b2f7c0 -->
## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->

- 2026-10-05T22:58:23Z — 実行時の検査を、ほかの結合テストと同じ起動の文脈で使い回せるか; 試しは素の @SpringBootTest（MOCK）で約 32 秒だった。ほかの結合テストは RANDOM_PORT と固有の設定を持つものが多く、文脈を使い回せるかはコード生成で既存の設定の形をそろえて確かめる。
<!-- aidlc-wave-memory:cross-cutting:2f137edfa7f6d716d64ff9469e83b4b1cf72e836b132be46058b6a5d008d035d -->

- 2026-10-05T22:58:23Z — 後の Intent（J・K）で分類を権限つきに広げるときの主体の作り方; U1 の検査は主体と期待を表で持ち、行を足すだけで広げられる形にした。その Intent の設計で決める。
<!-- aidlc-wave-memory:cross-cutting:3f39629cb3497b0f5dd53d07c06af75e9141e67558fee77f1c7ad3488e4aa92e -->

- 2026-10-05T23:03:38Z — 別名の爆発のテストの時間の上限 5 秒は、手元の実測 0.2 秒に余裕を持たせた値; 負荷の高い CI で落ちないかは Build and Test と CI で確かめる。
<!-- aidlc-wave-memory:dsl-v2:225c1f39959a6a4a162a75de610d9eed4893822d7ea585b44d0b00571becfd21 -->

- 2026-10-05T23:23:24Z — 上限ちょうどの 5 VU の回で pending が 0 にとどまるかは、HikariCP が接続を返す時機（確定の後の監査の2本目を返した直後に次の要求が借りる）に頼る; Performance Validation の結果で、ほかの接続の使い手が無いのに pending が出たときは、見積もりの誤りか時機のゆらぎかを hikaricp の値とログで切り分ける。
<!-- aidlc-wave-memory:group:1093e6fda236cbea9027315cbdd85162bb80c3af14c6b3a4d8995219995b586f -->

- 2026-10-05T23:23:24Z — Hibernate のロガー org.hibernate.orm.jdbc.error が違反の文（値つき）を WARN に出すことを試しで確かめた; 本番は OFF の設定だが、group の漏えいのテストでこの設定が消されたときに落ちる形にした。
<!-- aidlc-wave-memory:group:8190e05a732521d4b1f18b5f21a75c223cc623e3796937f6c8f6085734e16903 -->

- 2026-10-05T23:41:00Z — 試しは上限の 96%（10,115,916 バイト）で測り、上限ちょうどは比例で見通した; Performance Validation の roleTransferLarge で上限ちょうどの1回を測る。
<!-- aidlc-wave-memory:role:14bb90edb4ac4273a531ccc0ba2ab7caba62db0256fd82f8ed8d9c8411ae3a8c -->

- 2026-10-05T23:41:00Z — acquire の最大 20 ms 未満は測定の無い目安; 合否の回の結果が 5〜20 ms のときは合格のまま記録し、承認の場で目安を見直す。
<!-- aidlc-wave-memory:role:30bba6726f6f3d7a8504756f9ad93b670bb72d1e3dfa495a835d94520a7e3ba6 -->

- 2026-10-05T23:41:00Z — 一意の鍵・主キーの待ちの上限（約 2 秒か 3 秒か）は group の承認の場の決着にそろえる; 決着が違えば reliability-design.md 2.1 と RoleBusyApiIT の期待を合わせる。
<!-- aidlc-wave-memory:role:01732ff9f42b9f5246263f70f5ea45e28cce465812efbf6c68c56dbaf75826cf -->

- 2026-10-06T13:18:24Z — E2E の番号（U6 の検査 140・F の流れ 150）は app-frame-ui と合わせてコード生成の計画で確定する; 読み直しの R-08。
<!-- aidlc-wave-memory:role-admin-ui:d853355636372f5f310bb95120953ea60fc7c44a64ea9aabc6c6a123721dc1e2 -->

- 2026-10-06T13:18:24Z — data router への差し替えが既存の画面と E2E を変えないかは、コード生成の最初の確かめで決まる; 成り立たなければ機能設計 7.3 に切り替える。
<!-- aidlc-wave-memory:role-admin-ui:275372b182b7c3f6a2c94daa3add3c4f1308c900b45ccb327405ce9bd7308d5a -->

- 2026-10-06T13:30:00Z — 正しくない % の並び（a%zz など）の引数は 500 になり、ERROR のログの例外の文に引数の名前と値が入る（試し N2-d）; アプリ全体の今の振る舞いで、NFR1.8 と細工した要求で食い違う。受け入れた制約にするか、共通の誤りの変換で 400 にして文を出さないかを承認の場で依頼者に諮る（observability-design.md 3節）。
<!-- aidlc-wave-memory:navigation:dda9cecf6c6c9514a7729d83e3abe702f2c456dd595604858805b6d11366d6b9 -->

- 2026-10-06T13:36:46Z — 160 の 20 組 × 状態の実行時間は未見積もり; B9 の計画で1組あたりを見積もり、10 分を超える見込みなら 160・161 に分ける。
<!-- aidlc-wave-memory:app-frame-ui:4012df612dd3d3769e341d03d69fb4987357b918c7848fd00a53b8e991df3459 -->

- 2026-10-06T13:36:46Z — 自分の権限の応答の型は U4 の B5 で確かめる前提; 確かめられなければ S8 を外すかを計画の承認で諮る。
<!-- aidlc-wave-memory:app-frame-ui:cd0d73d8de108ce7052ec5e26a77161d52d6ac0571a049993361780b125e134c -->
