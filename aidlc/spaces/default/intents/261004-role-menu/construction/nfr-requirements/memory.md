<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->


- 2026-10-05T15:35:19Z — 枝番を、この Intent の要件の NFR の番号の意味で寄せた（分類と検査は NFR1、共有の木は NFR4、ESLint・テスト・依存は NFR6）; project.md の学びの NFR6・NFR9 は前の Intent の並びのため読み替えた。制約 C4・C5 は NFR の番号が無いため NFR6 に寄せ、成果物の冒頭に書いた。
<!-- aidlc-wave-memory:cross-cutting:e1a2b54dcbe2051638c162f80704611e52cef6883001d508f116f3922d85c842 -->

- 2026-10-05T15:35:19Z — NFR2・NFR3・NFR5 は U1 に当たらないとして N/A にした; U1 は API・データ・監査の出来事・指標を持たない。要件 NFR6.4 の access.service は、U1 が手を入れないため部分として扱った。
<!-- aidlc-wave-memory:cross-cutting:aea0b31d976c841579608ccba9b94dce79d479a9ab7b1734bc555ae1b67dfd30 -->

- 2026-10-05T15:38:13Z — 要件の NFR は主に役割・権限の API について書いているため、DSL の守りを NFR1、起動時のログを NFR5 に寄せた; 枝番は単位の中で .1 から振り、寄せたことと N/A の中身（NFR1.1〜1.4・1.6・5.1・5.2）を security-requirements.md の冒頭に書いた。
<!-- aidlc-wave-memory:dsl-v2:b7669bcacc3ee126b7794aa700363a1a6f5bb824e5de10a563388939753d8763 -->

- 2026-10-05T15:55:46Z — 上流の枝番と同じ番号は同じ意味でだけ使い、足す要件は上流の最後の枝番の次から振った; dsl-v2 の NFR 要件のレビュー（R-01）で、同じ番号を別の意味に使って N/A と有効が食い違った反省から。対応表を各成果物の冒頭に置いた。
<!-- aidlc-wave-memory:group:490b076758750accf005e72bf01a3dd8ba7d8222e4a44152965e7510a849b456 -->

- 2026-10-05T15:55:46Z — NFR2.3（要求ごとの権限の読み出しの重さ）を「部分」と読んだ; U4 の解決が要求ごとに呼ぶ groupIdsOfUser はこの単位の口のため、その問い合わせの数だけをこの単位で持ち、解決全体の時間は U4 に残した。
<!-- aidlc-wave-memory:group:5dc531968f18ffb7a92bfd9c8ab67c83922533ca1b11676e2a5230d75f24d7bb -->

- 2026-10-05T17:29:59Z — 要件 NFR6.4 の access.service を一覧から外す作業は role の範囲で当たらないと読んだ; 作業ロールは専用の API で渡し access・auth の本体を変えないため。cross-cutting で持ち主が決まっていなかった点を tech-stack-decisions.md に書いた。
<!-- aidlc-wave-memory:role:0719181d7b24450cd18e300da3901c691a15c9cc180654af4bbd48301fe06aea -->

- 2026-10-05T17:29:59Z — 別名の上限 0 はコレクションを指す別名 0 と読んだ; 今の部品はコレクションを指す別名だけを数え、スカラーを指す別名は節を増やさないため（機能設計の再レビューの R-05）。
<!-- aidlc-wave-memory:role:5acdef2698096f69dcc0b5810f39462ee720dfe8b9fbb1dbdd17fa94db9193cb -->

- 2026-10-06T00:30:00Z — 置き場の問い合わせにもメニューと同じ p95 1 秒を置いた; 要件 NFR2.2 が名指すのはメニューを返す API だけだが、同じ「メニューから画面へ移る」流れの API で、まとめの確認の「決まっていること」で承認されたため。
<!-- aidlc-wave-memory:navigation:97a5dbc054761fb5eeed22fe42acaf40cb82aa95e1202f543a66f986f3ee00ac -->

- 2026-10-06T00:30:00Z — 要求の数の見積もりを、利用者 50 名が 5 秒に1回移る毎秒 10 要求と読んだ; app-frame-ui の Q2 A（画面の移動のたびに読む）を受け、10 VU の閉じた繰り返しがこれを覆うことを NFR2.1 に書いた。
<!-- aidlc-wave-memory:navigation:73006b9ed88bde977e93f9dc04e0565e5c3a2f742d4a93989d25f865c7395649 -->

- 2026-10-05T22:03:31Z — 段の定義が必須とする rules は ui の単位に無いため、機能設計の D1〜D32 を入力にした; 機能設計の段の produces_kinds で ui は rules.md を作らない。各成果物の出典に書き、無い rules.md の中身は作らなかった。
<!-- aidlc-wave-memory:role-admin-ui:948da5866ca9b64219636d70f6059134d1c976e4fe060cbcc914469942b8c6cc -->

- 2026-10-05T22:03:31Z — アクセシビリティと多言語の要件（NFR4.x）は tech-stack-decisions.md に置いた; ui の単位の作る成果物は4つで、前の Intent の ui の単位（user-admin の U5）も同じ置き場だったため。
<!-- aidlc-wave-memory:role-admin-ui:62efb45d2f9958d842ce3c79b157455d93729bab128267c8401b29ef585e7063 -->

- 2026-10-05T22:03:31Z — 依存・共有の部品・ルーター・脆弱性の関門（NFR6.5〜NFR6.8）は上流 NFR6.4 の行に寄せた; 上流に依存の枝番が無く、統合の関門として最も近いため。対応表と traceability の両方に同じ寄せ方を書いた。
<!-- aidlc-wave-memory:role-admin-ui:ebcff3fa28822c375589d38ce507e7a69b9f30fb11030e5945b6a402ab4c3912 -->

- 2026-10-05T22:19:52Z — rules.md の代わりに機能設計の D1〜D30 を入力にした; ui の単位は機能設計で rules.md を作らないため（U6 と同じ）。無い rules.md の中身は作らず、各成果物の出典に書いた。
<!-- aidlc-wave-memory:app-frame-ui:574aa37837e2a25388d571d504d27ccba8044ca502c9a0727a0f1b14622f3c3a -->

- 2026-10-05T22:19:52Z — 固定先の更新の依存の確かめは、上流で e82b651 から 5bf1ffe の間に json と LICENSE の変更が無いことを読み取りで確かめた結果を根拠にした; それでも B9 の更新のコミットで npm ci・OSV-Scanner・verify を確かめる要件（NFR6.6）にした。
<!-- aidlc-wave-memory:app-frame-ui:6e6ab886bc5f40be122a099a373822e0800624c9e317a17860dab2a349a8a54c -->
## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->


- 2026-10-05T15:35:19Z — PUBLIC の口だけテストの側に一覧を持つ（Q1 B）; ADR-008 はすべての口の一覧を退けたが、これは PUBLIC だけの追加の守りとして補足の扱いにした。分類の正は口の隣の印のまま。コード生成の計画に ADR-008 の補足として書く。
<!-- aidlc-wave-memory:cross-cutting:857638aa6ced9630b4fc75b66e658f675137701ef6198e42bedfbd51ca05a982 -->

- 2026-10-05T15:35:19Z — library の単位のため、性能・規模・信頼性・観測の文書は作らなかった; 段の定義の produces_kinds のとおり。実際のブラウザの axe は U6・U7 の画面の検査に委ねた（NFR4.5）。
<!-- aidlc-wave-memory:cross-cutting:85a38dd55031b622cf93c476cede343c99345732a7b14926b102d53dbc9258b0 -->

- 2026-10-05T15:38:13Z — library の単位のため性能・信頼性・画面・テストの要件を tech-stack-decisions.md に書いた; 段の定義の produces_kinds で performance・reliability の文書を作らないため。扱いを両方の成果物の冒頭に書いた。
<!-- aidlc-wave-memory:dsl-v2:2005970393f7fde0fe2926a3d627454a6745f9fab858b3afc1eb5d753443bbfa -->

- 2026-10-05T15:55:46Z — 要件 NFR2.2 に無いグループの管理の API に p95 1 秒を置き、NFR2.5 として足した; 依頼者の Q1: A による。利用者の管理の API と同じ目標と道具（k6）で、台本はコード生成（B3）、流すのは Performance Validation。
<!-- aidlc-wave-memory:group:87d935b099897a4a167afc340d965e086b66474c212c8190253a20c14b1777f1 -->

- 2026-10-05T15:55:46Z — traceability.json は段の定義の例（NFR1〜NFR6 の行）より細かく、上流の枝番ごとにも行を立てた; dsl-v2 のレビューの R-02 で、当たらない枝番が OK の中に混ざると要件の網羅の連鎖が誤ると指摘されたため。OK の target には、この単位で定義した ID だけを書いた。
<!-- aidlc-wave-memory:group:e365355c19489f390b78531e790ff621cc94b700e78f707ffe377ceed7b6a1c5 -->

- 2026-10-05T17:29:59Z — 0 以下の ID を 400 ではなく 404 にした; group にそろえるためで、まとめの確認で機能設計の BR2.4 との差として受け入れられた。security-requirements.md の NFR1.10 に記録した。
<!-- aidlc-wave-memory:role:f87cf2e1ff2109efbad3be5843b7c69f8904a27c4add646d927acf02c5e7cbe3 -->

- 2026-10-05T17:29:59Z — 書き出しの応答に X-Role-Transfer-Exceeds-Import-Limit のヘッダーを足した; Q4 A の「上限を超えたことを示す」の形。契約 C7 に足す互換の変更として tech-stack-decisions.md の引き継ぎに書いた。
<!-- aidlc-wave-memory:role:54403e4308de6d6910893ce81aeb7069dc4f57571ce9ff679df68926d79250cc -->

- 2026-10-05T17:29:59Z — import の判定の順で DSL の有無を指紋より先に置いた; 機能設計の再レビューの R-12 の手当てで、BR9.10 の書き方との差として NFR3.8 に記録した。
<!-- aidlc-wave-memory:role:a4b26241def606abdaecd0bf0c4bb5416dfaf347691c80dc477e0fa1a8f65149 -->

- 2026-10-06T00:30:00Z — 置き場の問い合わせは resolve を1回呼ぶ（Q1 A）; 承認済みの機能設計 BR5.2 の「snapshotFor を1回」との差を security-requirements.md と tech-stack-decisions.md の「上流との差」に書いた。拒否の理由ごとの時間をそろえないことは受け入れた制約にした。
<!-- aidlc-wave-memory:navigation:5a93d7d513743b51b7d3ec001b89f031285eb2e677a03e3c952019e0a68a1253 -->

- 2026-10-06T00:30:00Z — 読み取りと DSL の適用し直しの重なりの要件を、質問の案の「足す枝番（NFR3.3）」ではなく NFR1.10 で振った; 上流の NFR3 を N/A にしたまま枝番を NFR3 の行に載せると「成果物で定めた枝番がすべて当たる枝番の行に現れる」を満たせないため。中身は「権限の無い項目を出さない」で NFR1.1 の行に当たる。信頼性と観測の要件も同じ理由で NFR1.x・NFR6.x の続きで振り、各成果物の対応表に理由を書いた。
<!-- aidlc-wave-memory:navigation:7e8f6bcb51b71a9517f86eb6e421c1cc902dec3b0a4f7677fc079e55c137756c -->

- 2026-10-06T00:30:00Z — 性質ベースのテストの性質を機能設計 BR2.7 の6つから8つにした（id の一意と、権限が違っても同じ id）; 機能設計の再レビューの R-05 を受け、まとめの確認で承認された。
<!-- aidlc-wave-memory:navigation:b457ade868d41a3beda8f100c3ffa04465270757663725790ed34a6cb52ae94d -->

- 2026-10-05T22:03:31Z — 共有の ApiDownload に応答のヘッダーを足す（Q2 A）; role の NFR2.9 の書き出しのヘッダーを画面が読むため。shared の部品に B8 で手が入るが、項目を足すだけの互換の変更で、既存の DSL のダウンロードは変えない。
<!-- aidlc-wave-memory:role-admin-ui:e385d30683f42d840e5a216cd39a4966a4eb99bc0b8fc54c38234012eb307c34 -->

- 2026-10-05T22:03:31Z — 機能設計の再レビューの R-03・R-04・R-09 は承認済みの機能設計を書き換えずコード生成の計画に回した; R-04 の今の main.tsx の受け渡しを保つことは NFR6.7 の要件に含めた。
<!-- aidlc-wave-memory:role-admin-ui:b2a76b3112a63acf73fdba8b4c761e8e4090aa7d2b935574a22eed712505a344 -->

- 2026-10-05T22:19:52Z — 機能設計の WorkRoleAnnouncer を置かず、切り替えの結果は Toast だけで伝える; Toast の入れ物が既に aria-live を持ち、同じ文が2回読まれるため（再レビューの R-05）。tech-stack-decisions.md の上流との差 (b) に記録した。
<!-- aidlc-wave-memory:app-frame-ui:3eeacaf83d195a49d91f5b3949fbd986288a5b779f49313fe336afb46a238315 -->

- 2026-10-05T22:19:52Z — /logout は操作の印のある移動のときだけログアウトする（Q3 A）; 機能設計 D27 の「描いたら logout() を呼ぶ」との差で、外のリンクによるログアウトの強制を防ぐ。差 (d) に記録した。
<!-- aidlc-wave-memory:app-frame-ui:10139c4fb9c8d752e2caae8948a31f1e3a19894fc3d1421f96cc3a3e072b3153 -->

- 2026-10-05T22:19:52Z — 機能設計の質問のファイルの「257 文字以上は 400」は古い; navigation の承認の場の直しで長さの上限はやめた。質問のファイルは書き換えず、差 (a) に今の決まりを書いた。
<!-- aidlc-wave-memory:app-frame-ui:c2a182812b26f7fc9efb19a84803e5a707023703d568ddef24c614de050004e6 -->

- 2026-10-05T22:28:57Z — 承認の場の決定「Major 11 件だけを直す」で R-01 を直し、PUBLIC の一覧を口の単位（9 つ）で比べる形に決めた; 各行は宣言された方法の集合と道の型の集合で比べる。暗黙の HEAD・OPTIONS は足さず、server.error.path は解決した値、パス変数は宣言の形のまま比べる。解決した道を読むため、比べる場所を構造の検査から実行時の検査（*IT）に移した。
<!-- aidlc-wave-memory:cross-cutting:49c96b945af20e56907c46840ae005d76a40d68b7913736e92a564869335180a -->

- 2026-10-05T22:31:56Z — 承認の場の Request Changes（Major 11 件だけを直す）で R-01・R-02 を直した; 上流の枝番と同じ番号は同じ意味でだけ使い、足す要件を NFR1.7〜・NFR2.5〜・NFR3.3・NFR5.3〜・NFR6.5〜 に振り直し、traceability.json を上流の枝番ごとの行（N/A と部分つき）にした。振り替えの対応は security-requirements.md の末尾に記録した。
<!-- aidlc-wave-memory:dsl-v2:0f291fdb2553221378ff47f29c8561aa2aeccb98602f950e4e93e3e6136c0ac5 -->

- 2026-10-05T22:30:06Z — 承認の場の Request Changes（Major 11 件だけを直す）で R-01・R-02 を直した; NFR2.5 は操作ごとに1回の繰り返しに要求1つの場面に分けて iteration_duration の p95 と checks の率 1 で判定し、NFR2.7 は GroupConnectionUsageIT（書き込み 2・読み取り 1）と、上限 10 で 4 VU（尽きない）・20 VU（待ちが起きる）の期待を数で置いた。場面の名前をそろえるため tech-stack-decisions.md の引き継ぎの1行と scalability の NFR2.8 の場面名も直した。
<!-- aidlc-wave-memory:group:a0b7d222c9bc3609661a178b45779d90c17f47726078b3257286c17693580b39 -->

- 2026-10-05T22:29:47Z — 承認の場の Request Changes（Major 11 件だけを直す）で R-01・R-02 を直した; resolve が写しを使わない形を契約 C5・機能設計 2.9 との差として記録し U5 に必須の引き継ぎを書いた。1要求の接続の本数は NFR2.11 の RoleConnectionUsageIT で決定的に確かめ、rolePoolLimit は上限に届いた証拠と合格の条件を数で持つ補助の確かめにした。
<!-- aidlc-wave-memory:role:bd4eb756619f3e4db41c296eb3963b8369c738bb0198d94d4d5ba1d600656bb1 -->

- 2026-10-06T02:00:00Z — 承認の場の Request Changes（Major 11 件だけを直す）で R-01 を直し、NFR1.12 と NavigationMenuAccessConsistencyIT を足した; Q1 A でメニュー（snapshotFor）と置き場（resolve）の道が2本になり、移れる項目が 403 になる食い違いを見つけるテストが無かったため。性質ベースにはせず4つの作業ロールと DSL の組の結合テストにし、2つの口の一致のテストを U4 role の B5 へ引き継いだ（security-requirements.md の末尾の節）。
<!-- aidlc-wave-memory:navigation:60ecb530319ec2a6ed41f94de6ebaa929caedf8b880260c6a9d4b14c0c47dfd6 -->

- 2026-10-06T00:00:00Z — 承認の場の Request Changes（Major 11 件だけを直す）で R-01 を直し、F の流れの E2E は始めに自分で版 2 の DSL を適用する形にした; app-frame-ui の NFR 要件の Q2 の決定にそろえ、後始末は無し・040 より後の番号・前のテストの状態に頼らない。B8 の前半と B9 の後半は同じファイルで続ける。
<!-- aidlc-wave-memory:role-admin-ui:c42e40a8d24910ed463ffdc7a5735f5bd18083a4fcaac477cbc44c4560bb3328 -->

- 2026-10-05T22:40:00Z — 承認の場の決定「Major 11 件だけを直す」で R-01・R-02 を直した; /logout の印は履歴に残るルーターの state をやめてメモリ上の一度きりの値にし、戻る・再読み込みでログアウトが繰り返されないことを I の流れの最後の手順で確かめる（R-01）。画面の時間は5回の中央値で判定し、最大と1回目を並べて記録する（R-02）。
<!-- aidlc-wave-memory:app-frame-ui:8f7b66af56043a4e7455b3516bead238be0167b009b76fa6ff4e59e146d5b864 -->
## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->


- 2026-10-05T15:35:19Z — 全体の archUnit の設定 failOnEmptyShould=false は変えず、U1 の規則だけ allowEmptyShould(false) にした; 全体を変えると既存の規則が空で落ちうる。代わりに、新しい規則を書く人が規則ごとに付け忘れうるため、対象の口が 34 以上あることも確かめる。
<!-- aidlc-wave-memory:cross-cutting:6e14ba6de0815e938c6b70bfdc50d77c51eb57bad43b6448863c3aa19acc43f5 -->

- 2026-10-05T15:35:19Z — 検査の時間に目標の数値を置かなかった; 起動の文脈を使い回せば増える時間は小さい見込みのため。Build and Test で実測して記録し、目立って増えたらそのとき見直す。
<!-- aidlc-wave-memory:cross-cutting:f16f7a65c2db630d1f680f6b2495f119bf62042dd6379b71270faa14484c29fa -->

- 2026-10-05T15:38:13Z — 1回ずつの時間は PostgreSQL の1種類だけで測る（Q1: A）; 版 2 で変わるのは DSL の形と検証で対象DB の読み取りは変わらないため。3種類の DB の差は確かめない代わりに、測る時間は約3分の1になる。
<!-- aidlc-wave-memory:dsl-v2:fad0b5e3df1238da29a9253d529f2f2a7235eef92325d7fc7dc06cbadaab2935 -->

- 2026-10-05T15:38:13Z — SafeYamlReader の境界は小さな上限の値で確かめ、10 MiB の本文は作らない; テストの時間とヒープを抑える代わりに、実際の上限の値での境界は呼ぶ側（role）と既存の DSL のテストに任せる。
<!-- aidlc-wave-memory:dsl-v2:761ff5121c4118cf0f8f29d13b0211c83ca4eb0ec94e9cd30399657d9b4a55ce -->

- 2026-10-05T15:55:46Z — 違反の後の失敗の監査は TransactionTemplate を2回順に実行する形にした（R-11）; REQUIRES_NEW の入れ子より接続を同時に2本以上持たず接続プールの見積もりが変わらない代わりに、業務処理の方法の中でトランザクションの組み立てを明示する手間が増える。
<!-- aidlc-wave-memory:group:1ccf8bc84d044c63abcf1c2587bcec8e49a318764a3b9e3ef5d3a51bc458998d -->

- 2026-10-05T15:55:46Z — 件数の上限は強制せず、詳細の全件返しのままメンバー 1,000 人で目標を確かめる（Q2: A）; 契約 C6 と機能設計を変えずに済む代わりに、目安を大きく超える規模では応答が大きくなりうる。
<!-- aidlc-wave-memory:group:5df7c89df645d7be772b0aacc6217bef4b8a0fc5f1607940f267651358eb0770 -->

- 2026-10-05T17:29:59Z — k6 の判定は1回の繰り返しに要求1つの場面の iteration_duration で行い、トークンは setup() で取って場面を 3 分にした; 時計のずれに強い代わりに、操作が2つ以上の場面は op のタグが iteration_duration に付くかを台本で確かめる必要がある（group のレビューの R-01 を先に避けた）。
<!-- aidlc-wave-memory:role:ec453eb33fc93b0a97214a58a2226be0c4b8c090ddd589c666e98cf11d7ebf8d -->

- 2026-10-05T17:29:59Z — import は 10 MiB・確かめ 15 秒・適用 30 秒とした（Q4 A）; 往復できる範囲が広い代わりに、適用の間は同じロールの操作が ROLE_BUSY になる。時間は NFR 設計の捨ての試しで先に測る。
<!-- aidlc-wave-memory:role:22f1800d4db41a6e6625205804d28dbc4a7a5b70e97ed5639f0bd4bc8cdc3354 -->

- 2026-10-05T17:29:59Z — resolve は祖先の行だけを読み、たくさんの対象は snapshotFor を1回とした（Q2 A）; 契約 C5 の口の形を変えずに重さを抑える代わりに、呼ぶ側の使い分けを口の説明で守らせる必要がある。
<!-- aidlc-wave-memory:role:a9461e9bc8c9b2254186f0da96a16ff55096585542e79fbf3e8422d1a18e6cae -->

- 2026-10-06T00:30:00Z — 上限を下げた k6 の場面を置かず、接続の数は結合テスト NavigationConnectionUsageIT で決定的に確かめる; 読み取りだけで1要求1本のため詰まりが起きず、role のレビューの R-02 の「k6 では見積もりの誤りを見分けにくい」を避けられる。代わりに、負荷の下での待ちの長さは測らない。
<!-- aidlc-wave-memory:navigation:bd065328cc68a36c9db6bd38c9b3700fa654c0e85ecaf9466dece1d786a507c8 -->

- 2026-10-06T00:30:00Z — k6 は操作ごとに場面を分けた（navMenu・tableAccessVisible・tableAccessDenied・navMenuBaseline）; op のタグに頼らず、iteration_duration の p95 で判定できる代わりに場面の数が増える。場面は時間をずらして順に流す。
<!-- aidlc-wave-memory:navigation:efc120201299ab339089d387b43c29e218e2203cde162c4ce7fa63855ae251c6 -->

- 2026-10-05T22:03:31Z — 画面の時間は目標を書いたうえで記録だけにし、成否にしない（Q1 A）; 前の Intent の U5 と同じで、手元の1台の測りで統合を揺らさない代わりに、目標を外れても統合は止まらない。値は Build and Test で目標と並べて記録する。
<!-- aidlc-wave-memory:role-admin-ui:2b9f7fe36d46e1598372a2c7f08ff6204509513088d591b778a88509284c63da -->

- 2026-10-05T22:03:31Z — 1テーブルのカラムは分割せずに全件を描く; 目安 100 カラムでは十分で作りが単純になる代わりに、悪い側の 1,000 カラムは時間を記録するだけで守りを置かない。
<!-- aidlc-wave-memory:role-admin-ui:fbe6b8066ad61c71c6d8bea43c5ccbc769b5679900b77d141371cc5d6c884d9d -->

- 2026-10-05T22:19:52Z — 流れの E2E は始めに自分で DSL を適用し、後始末をしない（Q2 A）; 前のテストの状態に頼らない代わりに、後のファイルに適用中の DSL が残る。流れのファイルを 040 より後に置き、F の流れも同じ形にそろえるよう B9 の計画に引き継いだ。
<!-- aidlc-wave-memory:app-frame-ui:623fc0f094c60016df188180bbadb177aafc1a2f49a1413566c2cc0e2653d8e5 -->

- 2026-10-05T22:19:52Z — 画面の時間の目標（0.5 秒・0.2 秒・0.5 秒）は前例からの見立てで、記録だけにした（Q1 A）; 超えたら Build and Test が Not Met を記録し、依頼者が扱いを決める。目標は緩めない。
<!-- aidlc-wave-memory:app-frame-ui:1d6b934d4be44f0a1e4d49add0989807db245987295386050e851493252c4f25 -->
## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->

- 2026-10-05T15:35:19Z — WebInvocationPrivilegeEvaluator が Spring Security 7.1.1 で、方法つきの決まりと AuthenticatedUserToken の主体を判定できるか; NFR 設計の捨ての試しで確かめる。だめなら MockMvc の形に決め直し、依頼者に差として伝える。
<!-- aidlc-wave-memory:cross-cutting:96acaa04f946e217c68801f8aa5508fad00d50053281b7d5b7523361c8831c3c -->

- 2026-10-05T15:35:19Z — 要件 NFR6.4 の access.service を一覧から外す作業を、どの単位が持つか; U1 は手を入れない。手を入れる単位（U4 など）の NFR 要件かコード生成の計画で確かめる必要がある。
<!-- aidlc-wave-memory:cross-cutting:7530e27cd38bf5e594f13eea236146e1f000fc4f806b29ab9e577dec3eb453cf -->

- 2026-10-05T15:38:13Z — 生成する DSL の大きさは約 6.3 MB の試算で、まだ実測していない; Build and Test で測り、コメントの多い対象DB で 10 MiB に近づくときは上限を上げるかを依頼者に諮る。
<!-- aidlc-wave-memory:dsl-v2:9421744b1ff39daba6f626b97eb36579572bce1d9fb8832af22b305be75f63a8 -->

- 2026-10-05T15:55:46Z — 同じ名前の同時の作成・変更で、重なった側が違反になるか待ちの上限切れになるかは未確定; NFR 設計の捨ての試しのコード（NFR3.7）で確かめ、GroupBusyApiIT・GroupConflictAuditIT の期待をその結果に合わせる。
<!-- aidlc-wave-memory:group:504e8486a72f132702ee13492a6b3dfde1b3131e9e28dd6793122b5c0d59e5d6 -->

- 2026-10-05T15:55:46Z — 入口の判定の後の窓（NFR1.10）を U4 role も受け入れるかは U4 で決める; グループと同じ考え方を推奨として引き継いだ。
<!-- aidlc-wave-memory:group:1d79d0187898794e9451e5e4e838ee4ae3923567386cdb21ec370fa0e7f482cb -->

- 2026-10-05T17:29:59Z — 10 MiB の確かめ 15 秒・適用 30 秒と、snapshotFor 300 ミリ秒は根拠の測定が無い目標; NFR 設計の捨ての試し（NFR3.7）で先に測り、届かなければ上限を下げる案を依頼者に諮る。
<!-- aidlc-wave-memory:role:6055b76fac15306e9fc7187d6e6035893d7c8b38f6f190bf8c283ead0d94747c -->

- 2026-10-05T17:29:59Z — rolePoolLimit の 20 VU は待ちが起きることだけを期待にした; 尽きて 500 が出るかは確率で決まるため合格の条件にせず、件数を記録する。
<!-- aidlc-wave-memory:role:87f36ffadb237ddac7b8e2acbab122e6f423355302816828465ded105f80492d -->

- 2026-10-05T17:29:59Z — exec.vu.metrics.tags の op のタグが iteration_duration に付くかは未確かめ; 台本を書く B4 で確かめ、付かなければ操作ごとに場面を分ける。
<!-- aidlc-wave-memory:role:7c6f4bef376281412e22d962d9fd215ba49a19c866070dde5363a3d12c78ee2f -->

- 2026-10-06T00:30:00Z — 解決の口の読み取りの途中で止める待ち合わせの口の置き場が決まっていない; NavigationConnectionUsageIT と NavigationDslSwapIT が要る。role の RoleBarrier で足りるか、navigation の側の替え物で包むかをコード生成の計画で決める（tech-stack-decisions.md の引き継ぎ）。
<!-- aidlc-wave-memory:navigation:6af4762f58a43a6f13498b96dcdacb54be54b1288ac7e00cd03f736249d76261 -->

- 2026-10-06T00:30:00Z — app-frame-ui の機能設計の質問の案に、置き場の「257 文字以上は 400」という直す前の記述が残っていた; navigation の承認の場の直し R-01 で長さの上限はやめている。U7 の担当への伝達を指揮役に頼んだ。
<!-- aidlc-wave-memory:navigation:75b8d68807f8fbfa1bac6cf4c7511e830ba89b85e8cae88bd39921795e102628 -->

- 2026-10-05T22:03:31Z — 上限ちょうどの確かめの応答（約 25 万の変わる点）の受け取りの時間とメモリは画面で減らせない; NFR2.7 で値とヒープの大きさを記録し、応答の大きさそのものは U4 の持ち物として残る。
<!-- aidlc-wave-memory:role-admin-ui:2d3537ec8b02e7bfd5fdda47fe16be4e48c625974ceb5940b45769904f7f1597 -->

- 2026-10-05T22:03:31Z — data router への差し替え（NFR6.7）が既存の画面と E2E を変えないかは、コード生成の最初の小さな確かめで決まる; 成り立たなければ機能設計 7.3 の形に切り替えて差を記録する。
<!-- aidlc-wave-memory:role-admin-ui:a8961620ca821caed5215ce193e9eeefe6a59e6aab57abc1dc7e76f889846426 -->

- 2026-10-05T22:19:52Z — 自分の権限の応答の型（displayName の形、create・delete の型）は未確定; U4 の B5 の計画で確かめ、B9 の計画に書き留める（NFR6.8）。
<!-- aidlc-wave-memory:app-frame-ui:f0f29ac3cea38485683b7d7c4e5d58751c9fd9cca387422cd9778ed9d05c71e1 -->

- 2026-10-05T22:19:52Z — 畳んだ状態の 5bf1ffe の aria-labelledby が axe の違反になるかは未検証; 違反なら除外を足さずに B9 の時点で依頼者に諮る（NFR4.4）。
<!-- aidlc-wave-memory:app-frame-ui:a2707d5e53ff6281c5cd9370f9666ce5362a90c127f22b87e141fde7cd33f196 -->
