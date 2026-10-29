<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->


- 2026-10-30T00:00:00Z — 保存するエンティティが無いため、entities.md は persisted_entities を空にし、道具の中だけの一時の形（入力の木・置換の結果・残存の1件・キーの衝突）を参考として置いた（project.md のエンティティの決まり）。
<!-- aidlc-wave-memory:u4-perf-evidence-redactor:469ef7aa159b5aa1ce1f163c83b096d463329e560090ad6d7bf72816d360fd9e -->


- 2026-10-30T00:00:00Z — JWT の形の 3 区間目は 1 文字以上とし、署名の空の形（alg: none の「a.b.」）は対象にしない。アプリはその形を出さず、「2 区間は置換しない」と食い違わない読み方を選んだ。4 区間以上は先頭の 3 区間を替える（伏せすぎの側）。
<!-- aidlc-wave-memory:u4-perf-evidence-redactor:abebf463fdb871261e02190a03ebc3313b1f8133102246d638554c45d4bf330d -->


- 2026-10-30T00:00:00Z — 残存の検査の「独立」は、置換の関数を呼ばず、木ではなく書き出す直前の文字列を端から調べる別の関数、と読んだ。形の定義（BR1.1）は共有する。
<!-- aidlc-wave-memory:u4-perf-evidence-redactor:73e648a2e35a1a346554965c3576b5819c273eb9544e95aa9d539715484a0ad1 -->


- 2026-10-30T00:00:00Z — AC4.1.9: 台本と既存の記録の鍵の名前（値は開かない）を読み、summary json に台本の値が入るのは setup_data だけ、リフレッシュトークンは vuState（setup の外）、Authorization ヘッダーは要求のたびに組み立てると確かめた。入らないため置換の対象は JWT だけのまま（M2: B）。
<!-- aidlc-wave-memory:u4-perf-evidence-redactor:52e51809460cf806b81bce4192b68b23292cf58bd7474fbf62bf47bc1d776071 -->


- 2026-10-29T22:40:00Z — 契約 C2 の検索の条件の形（column と value の1組）では範囲・複数の値を表せないため、ピッカーの演算子を EQUALS と CONTAINS に限ると読んだ（D6、BR3.2）。承認の場で確かめる。
<!-- aidlc-wave-memory:u1-reference-read:82d472cdd51bec1cdaf90293d6063570ca822a7af2d4b0699dc6f1cca10822ac -->


- 2026-10-29T22:40:00Z — 値のカラムがバイナリか password のときは値を文字で渡せないため、参照を提供しない（NO_PERMISSION）と読んだ（BR3.1）。契約の状態の値を増やさないため。
<!-- aidlc-wave-memory:u1-reference-read:2dc7d2d9d8e2c6af38bbc4c2b0a3bdbf9af77fe902dfd7fdb4cc10b30a30e8f2 -->


- 2026-10-29T22:40:00Z — scaffold が出した AC2.3.5 は stories.md の初稿の ID の対応表にしか無いため、N/A（最終版の AC2.1.10〜AC2.1.12 で追う）にした。
<!-- aidlc-wave-memory:u1-reference-read:a411fc6f177edfd02e24f317c33c46825f5aba31dcc4e7ec397ced7f6dfbee16 -->


- 2026-10-30T00:00:00Z — 契約 C3 の 400「入力不可の参照の項目の値を変えようとした」を、参照元のカラムが書けないとき（既存の NOT_ALLOWED）だけと読んだ。参照先を読めない・多すぎる参照の値は 409（AC3.1.3・AC1.5.2 に合わせた。functional-spec.md の D2）。
<!-- aidlc-wave-memory:u2-reference-write-check:dbda2ea336448e52fa6572faea6246efeb9138f567cf88f53048249f2f72277b -->


- 2026-10-30T00:00:00Z — 必須の参照項目は NOT NULL（nullable が偽）で作成で入力できるカラムと定義した。DSL の required だけで NULL を許すカラムは既存の REQUIRED のまま。
<!-- aidlc-wave-memory:u2-reference-write-check:506362921c7f91994e5f2049b334a0f5cd5411a8217e09475926ba69cce4d607 -->


- 2026-10-29T23:12:48Z — U1 の検索は件数を返さず hasNext だけのため、make-you-chic-ui の Table のページ送り（totalCount から総ページを出す）は、合わせた totalCount と件数を言わない pageStatus の文で使うと読んだ（D25、差 X2）。
<!-- aidlc-wave-memory:u3-reference-screens:71685165ef1b33c9cd8e80d136d87e3fd0a58952b6faae20563baea050aa8da6 -->


- 2026-10-29T23:12:48Z — 作成・更新の画面の定義（purpose つき）は選択肢という参照先の行の値を含むため、一覧・詳細と共有する機能の記憶に書かず、開くたびに読むと読んだ（D2）。
<!-- aidlc-wave-memory:u3-reference-screens:f003419646c87d35028f9c162d8827f0022c92a31da29c995b4fa8d66a9c68a2 -->

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->


- 2026-10-30T00:00:00Z — 契約 C7 に、verify の evidence の検査の口（Q1: A で同じ残存の検査の関数を使うため）を足し、終わりのコードを「非 0」から 1〜5 に細分した。どちらも足す変更で、C7 の既存の呼び方は変えない（functional-spec.md 10節）。
<!-- aidlc-wave-memory:u4-perf-evidence-redactor:f42079dd742cbb6d21bca265b914936d961a31a45a35e1bc5f54fe78700e47fb -->


- 2026-10-30T01:00:00Z — 追加の質問 F1: A を受け、上の階層の setup_data の下の文字列をすべて [REDACTED-SETUP] に替える BR1.7 と独立の確かめ BR2.4 を足し、性質 (3) を setup_data の外に読み替え (5) を足した。ストーリーの M2: B（キーによる伏せ字は行わない）との差として functional-spec.md 10節に記録し、7節の「2 場面を記録に置かない前提」をこの決定に置き換えた。
<!-- aidlc-wave-memory:u4-perf-evidence-redactor:520c09a271a4fc6747e1f9976ffc5f660c89b2b0c5da53075f7e494baf9aa29c -->


- 2026-10-29T22:40:00Z — 契約 C2 は formPart を用途によらず DSL の値で返すとしたが、purpose が CREATE・UPDATE のときだけ lookup を返し、VIEW では今のまま text にした（D2）。U3 が入るまでの間、purpose を送らない既存の作成・更新の画面を壊さないため。
<!-- aidlc-wave-memory:u1-reference-read:53c53a58cb76f7d543ca75c0daa705327cbdded11a1880cf35f43b70a70f68bd -->


- 2026-10-29T22:40:00Z — 契約 C1 の check の注記（detail を使う）と違い、ラベルの1件読みと check は search（値の一致・読む行 1）を使う形にした（D4）。detail は主キーで引き2行で「1つに決まらない」を返すため。check の Unavailable は理由つきの FAILED にした（D3）。
<!-- aidlc-wave-memory:u1-reference-read:3c2aaf52c565c8f10f02192a11fbe198a9091446720e0df84c158831fbee4ad1 -->


- 2026-10-29T22:40:00Z — AC3.5.1 は検索を「件数とページの 2 文」と書くが、契約 C2 の「件数を数えない」に従い文は1つにした（functional-spec.md の 9節）。
<!-- aidlc-wave-memory:u1-reference-read:d8ea276f710d3354a034337f88556068829cd0751bac888fd921763fab3b9d60 -->


- 2026-10-30T00:00:00Z — Q1: A の TOO_MANY を判定するため、必須の参照項目が使えて空のときの 400 が、対象DB を読んだ後の ⑩ で返る（C3 の「400 は業務の失敗より前」との差 D3）。⑨ の 400 では対象DB に触れない決まりを保つため、混じった 400 では TOO_MANY の項目が REQUIRED で並ぶ（D4）。
<!-- aidlc-wave-memory:u2-reference-write-check:67e43447b9e25028696c2993d97af16822f6d7f0b6c6c0359760599a3afc8b8c -->


- 2026-10-30T00:00:00Z — U1 の確かめの口の決まり（9.4・9.6）は Q2: A の文字どおり一致と件数の判定を持たないため、U1 のファイルは変えずに H1・H2 として引き継いだ。
<!-- aidlc-wave-memory:u2-reference-write-check:9f17542fb48cf684d2745d5829c0c2a9274686c271a9f056153a6198d37dfc9a -->


- 2026-10-29T23:12:48Z — 「決まっていること」の「入力の型は valueType から」は、U1 の PickerSearchField に valueType が無いため果たせず、文字の入力にした（D22、差 X1）。
<!-- aidlc-wave-memory:u3-reference-screens:3c75aa1cd9a8d9cf33a1891d6b930d17b6cc102af6639b03853b1459f9b992c5 -->


- 2026-10-29T23:12:48Z — U2 の引き継ぎ「503 は保存されていない」は、TIMEOUT の code が確定の時間切れと共有のため、TIMEOUT だけ既存の「保存されたか分からない」のままにした（D33、差 X4）。
<!-- aidlc-wave-memory:u3-reference-screens:5d9a8551caacf57e4f29ff8e6fb5c0b8f20079baa8381494931f13fd52e28aa7 -->


- 2026-10-29T23:12:48Z — interaction-spec.md のボタンの名前「{項目名}を選ぶ」は、見える文字「選ぶ…」を含まず WCAG 2.5.3 に反するため、見える文字で始める名前にした（D17、差 X3）。
<!-- aidlc-wave-memory:u3-reference-screens:a5a47257217e596bee5b9890be48b0b8876187bd5aabf42b2a9bc917c438f34e -->


- 2026-10-29T23:12:48Z — traceability の確かめ（sensor-traceability）は、ui の単位に rules.md が無いため、OK の行の target に BR が無いことと rules.md が無いことで必ず失敗する。前の Intent の ui の単位（261016-business-data-views の U3）も今の道具で同じく失敗する。rules.md は作らず、target は functional-spec.md の D・F を指した（project.md の学び）。
<!-- aidlc-wave-memory:u3-reference-screens:a575fea3ec31b880febd73e503e51bb6d145a419c8bf88376ef32c385e7941d2 -->


- 2026-10-29T23:25:02Z — 承認の場の Request Changes（依頼者の決定）で、R-01: 同じ道具にテキストの置換の口（--text。BR1.2 の置換の関数を共有）を足し BR1.8・BR3.4・BR5.4 を新設、W1 を W1-J・W1-T に分けて置いた後に evidence の検査と gitleaks dir を流す手順を置いた。R-02: verify に組み込む前に既存の evidence/ へ検査を一度流して 0 件を確かめ、見つかれば依頼者に諮る手順をコード生成に回した。R-03: 手で置いた json の setup_data の下の JWT の形でない秘密は verify で拾えない穴を 10節に記録し、evidence の検査は setup_data に広げない（過去の記録の [REDACTED-JWT] で落ちるため）。
<!-- aidlc-wave-memory:u4-perf-evidence-redactor:f9eeeb8355d30a8173e8c7b41333353c38094bf93d53b7e6fbabf654ca3c1f5b -->


- 2026-10-30T00:00:00Z — 依頼者の Request Changes で直した: ReferenceReadSpec に切らない値のカラムと固定の並びを持たせ（BR7.3・BR11.4 を新設）、定義の全体の上限は ReferenceLookup が上限だけを絞った TargetReadSession（同じ接続・同じ壊れた印）を文ごとに作る形にし残りを秒に切り上げ 1 秒未満は 503（BR8.4）、check を 200 行まで読み型つきの値の厳密な一致で FOUND にし（BR9.4・BR7.6 を新設）、選択肢の重複の除去も同じ一致にし、U2 向けの countState を足し（BR9.7 を新設、契約との差 D7・D8）、ピッカーの searchFields・listColumns に valueType を足し（D9）、U3 へ入力不可の表示を先に出す引き継ぎを足した。決まりは 71 件。
<!-- aidlc-wave-memory:u1-reference-read:4b752624eaf3ef521a909bc1a703c4326ef449c21eac62b002351e7b7d4660bd -->


- 2026-10-29T23:24:58Z — 承認の場の Request Changes（依頼者の決定）で直した: U1 の check（200 行まで読み型つきの完全一致で FOUND）と countState（201 行目まで読み AVAILABLE・TOO_MANY・NO_PERMISSION・FAILED）を U1 と共通の形として使い、暫定の安全側と H1・H2 の引き継ぎを外した（R-01）。`""` は既存の ⑨ の検証の結果にそのまま従う1つの扱いに決め、期待結果の表を置いた（R-02）。同じ参照先の countState を要求内で1回にする BR4.7 を足し、check の束ね方は NFR 設計に回した（R-03）。NOT_EDITABLE_NOT_NULL の field が空とカラム名の2通りになる影響の範囲をコード生成への引き継ぎに書いた（R-04）。
<!-- aidlc-wave-memory:u2-reference-write-check:92dad39afa47a62e995a8b2b939a56d4b73c5db5b8b72e8e388f792445821570 -->


- 2026-10-29T23:24:56Z — 承認の場の Request Changes（依頼者の決定）で直した: R-01 はラベルの1件読みを検索と別の写し parseReferenceLabel で受け page: null を通す（D18）、R-04 は U1 が searchFields・listColumns に valueType を載せる前提で入力を既存の作成・更新の画面にそろえ X1 を解消（D22・D23）、R-05 は用途で入力できないカラムを status より先に見分け権限の文言を出さない（D5・D32）、R-02 は 2ページ目以降の 0 件で Table を描かず「前のページへ」だけ（D26・pickerPaging）、R-03 は読み上げの扱いを NFR 設計で必ず決め行き止まりは文言で受け入れる（9節）。X4 は変えない。D・F の番号は変えていない。
<!-- aidlc-wave-memory:u3-reference-screens:b2d5a8be47bae406ab92952d84d8bf904b1e8ce28006df7e2c1092503f5c18df -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->


- 2026-10-30T00:00:00Z — evidence の検査は拡張子で絞らず、先頭に NUL を含むものだけをバイナリとして飛ばす形にした。手で貼った値を広く拾える代わりに、バイナリに入った値は拾わない（飛ばした件数は表示する）。設計の時点で既存の evidence/ の2か所には JWT の形のファイルが 0 件と確かめた（件数だけ）。
<!-- aidlc-wave-memory:u4-perf-evidence-redactor:a088dec5322d66df589d65984a25f499be903476b0768610a0008879fe01f1da -->


- 2026-10-30T00:00:00Z — 出力の同じさは JSON として読んだ値で比べ、書式（字下げ・空白）は保たない。k6 の指標は浮動小数で、読み直しで値が変わらない代わりに、元のファイルと文字の並びは一致しない。
<!-- aidlc-wave-memory:u4-perf-evidence-redactor:9cddcfc4f9bc28dabca0fcfd90bfe649793b3264417f54c14b5408112e12b2ad -->


- 2026-10-29T22:40:00Z — ラベルは既存の一覧の上限（LIST_TEXT_LIMIT）で切り、切った印は契約に無いため返さない（BR7.3）。200 件の選択肢の応答の大きさを抑える代わりに、長いラベルは画面で全文を出せない。上限は NFR 設計で見直してよい。
<!-- aidlc-wave-memory:u1-reference-read:e29dacbeab77bc495107258d6068c3d36cbdbfbaa4e2c17b3b2ed7a9ec2f7b88 -->


- 2026-10-29T22:40:00Z — 用途で入力できない参照元のカラムは参照先を読まず NO_PERMISSION にした（BR3.3）。読み取りを省ける代わりに、U3 は権限の文言ではなく通常の入力できない表示を優先する必要がある。
<!-- aidlc-wave-memory:u1-reference-read:e0aa10503877e0b8b145d6ce258a3d6c0a34d4e2138185c17a08df1197ed372c -->


- 2026-10-30T00:00:00Z — 件数（TOO_MANY）による拒否を作成の必須の参照項目だけに限った（D5）。保存のたびの 201 行の読み取りを避ける代わりに、NULL を許す項目と更新では、入力不可の項目に正しい値を直接送れば保存される。
<!-- aidlc-wave-memory:u2-reference-write-check:fe0507537787b1b528a1db50ec05ddc72c1d92e5dfb532c187bcd60bcd0248a4 -->


- 2026-10-30T00:00:00Z — 更新前の行の読み取りは既存の detail を使う（新しい SQL のメソッドを作らない）。読めるカラムをすべて読む重さと、上限で切られた値を「変わった」と扱う安全側を受け入れた。
<!-- aidlc-wave-memory:u2-reference-write-check:357d2acce25a2d23c79595497a8b53c8ebf69c6b3f8f251a59c0372e56d91bad -->


- 2026-10-29T23:12:48Z — ピッカーのダイアログは開くたびに条件と結果を空から始める（D21）。参照先の行の値を画面に持ち越さない代わりに、開き直すと条件を入れ直す手間が増える。
<!-- aidlc-wave-memory:u3-reference-screens:8ba554fddc2cb4cb5e1d6b4375e7cb76b156f3857b7c69bacb4c69e1ea98f22a -->


- 2026-10-29T23:12:48Z — E2E 190 は 180 と DSL を共有し、画面の流れの前の API の準備で適用されていなければ適用する（D39）。前のファイルの状態に頼らない代わりに、180 の DSL の見本を変える。
<!-- aidlc-wave-memory:u3-reference-screens:d05e200e198d5bbe2ab90d56f6e730b5f16b87246a38c131e135be716299ba24 -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->

- 2026-10-30T00:00:00Z — AC4.1.9 の対象外だが、registrationVerify・registrationComplete の場面は setup() が招待のトークン（JWT の形でない）を setup_data.registrationTokens に入れ、その summary json を記録に置くと置換されずに残る。置換の対象を広げる・その場面の結果を記録に置かない・台本で setup_data から外す、のどれにするかを依頼者に諮る。決まるまでその 2 場面を記録に置かない前提で設計した（functional-spec.md 7節）。
<!-- aidlc-wave-memory:u4-perf-evidence-redactor:8d5aa08a02d01d348e1f23b7a2d961f7bf67dd2bf51eb7cf83bf784dd58dcff8 -->

- 2026-10-29T22:40:00Z — 承認の場で確かめる4点（functional-spec.md の 12節）: D6 の演算子の限定、値のカラムがバイナリ・password のときの NO_PERMISSION、入力できない参照元の NO_PERMISSION、ラベルの切り方。
<!-- aidlc-wave-memory:u1-reference-read:dabc72e443193894305557ad26f329272fdffa949f86a44d2f496499a00f1c41 -->

- 2026-10-29T22:40:00Z — REFERENCE の問い合わせ1回の時間の上限と、定義の取得の全体の上限の値は NFR 設計で決める（functional-spec.md の 10節）。
<!-- aidlc-wave-memory:u1-reference-read:08a5b9c301420d0709e476fc76401bd49a7935ff2a0c499aefa9b97f73fb5f91 -->

- 2026-10-30T00:00:00Z — H1（文字どおり一致の比べ方の置き場と、照合順序で重なる行の取りこぼし対策）と H2（件数の判定の口）は、U1 の承認の場かコード生成の計画で確定する。
<!-- aidlc-wave-memory:u2-reference-write-check:4c06f69d974902fefeb63769269fbf71046fb3c257b60331e0540e3be49668fe -->

- 2026-10-29T23:12:48Z — U1 に PickerSearchField の valueType を足すか、U2 で保存前の確かめの時間切れを UNAVAILABLE に写すか（functional-spec.md 10節・13節）。
<!-- aidlc-wave-memory:u3-reference-screens:940b6eaa932d1b3bcdc2a43226cd8c13a589209cefbc26cd9db9f69baf27e650 -->

- 2026-10-29T23:12:48Z — FormField の誤りの文は role="alert" で、作成の画面を開いた直後の必須の参照項目の誤り（D13）が読み上げられる。許すかを NFR 設計で確かめる（9節）。
<!-- aidlc-wave-memory:u3-reference-screens:8ee2f821dca2641fc4abc3923f763cef8af64382435c35d5c5d69ca38ee29bce -->
