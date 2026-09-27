<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->


- 2026-09-27T06:22:08Z — library の単位のため、性能・信頼性・観測に当たる設計（NFR5・NFR6・送信の観測）を logical-components.md の節に置く案にした; NFR 要件の段が tech-stack-decisions.md の2節に置いた形にならう。成果物は security-design.md・logical-components.md・traceability.json の3つ。
<!-- aidlc-wave-memory:u1-mail:22088e7561fffd9385cf54d724d476fbab47e2e2c5c8efaefbc2b9a6a27763e4 -->

- 2026-09-27T06:22:08Z — 質問は、承認済みの NFR 要件から作りが1つに決まらない2点（描画と送信の内部をメソッドの追跡から外す作り、送信の観測の単位）に絞った; 起動時の点検の場所・テンプレートの準備・失敗の分類・想定外の例外の包み方は、targetdb の前例と project.md の Forbidden で一通りに決まるため要点にした。
<!-- aidlc-wave-memory:u1-mail:b400ffa4af3ef043a661ce0a61444f32b6f290b0cfe4fd5d0e63bca185c876b5 -->

- 2026-09-27T09:49:19Z — 失敗の分類は Jakarta Mail の API の型（SendFailedException・AuthenticationFailedException）と Spring の例外の型だけで行う設計にした; Angus Mail は実行時だけの依存で、SMTP の例外の型をコンパイルの時に使えないため。受け手の拒否の SMTP の例外は SendFailedException を継ぐので REJECTED に入る。質問ファイルの要点 8 の書き方との差として security-design.md の10節に書いた。
<!-- aidlc-wave-memory:u1-mail:3db85cf51ff56bb068590ff06b9ca9f95920edc2ee99751aa31b70c34c6166b6 -->

- 2026-09-27T09:49:19Z — templateId と language は、一覧にある値と ja・en だけをログと Observation のタグにそのまま出し、それ以外は unknown と出す設計にした; 呼び出し元が渡す未知の文字列（改行を含みうる）をログ・タグに写さず、指標のタグの種類が増え続けるのも防ぐため。BR6.2・BR6.3 との差として記録した。
<!-- aidlc-wave-memory:u1-mail:66888d916a260a65371feb5c21196b46d093cd92d5d8134fc160a5c26f1ce502 -->

- 2026-09-27T06:15:03Z — 質問は order の割り当て（Q1）と接続を借りないことの結合テストの作り（Q2）の2問にし、ほかは NFR 設計の要点（案）12 件として要約で確かめる形にした; 性能・拡張性・信頼性・観測性の設計は承認済みの NFR 要件（NFR4.1〜NFR9.8）と機能設計でほぼ決まっていると読んだ。候補の3つ目の判定の関数の置き場は、機能設計の1節が domain を作らないと決めているため service とし、質問にしなかった。
<!-- aidlc-wave-memory:u8-instance-appearance:f6c7db5cf4a40f3446d5edca2e0294a52fe881836eb67b9284ccb910ab93678f -->

- 2026-09-27T06:15:03Z — 既存の SecurityRuleContributor の説明文の「U2 は 100 台、U3 は 200 台」は前の Intent の単位（auth 110・access 210）を指すと読んだ; AuthSecurityContributor・AdminSecurityContributor の説明文が同じ番号を使っているため。この Intent の u2・u3 と名前が重なることが NFR 要件の R-01 の原因で、Q1 A で機能の名前の割り当てに書き直す案にした。
<!-- aidlc-wave-memory:u8-instance-appearance:1599335e9bda540647901007441ff71583482c4e4a01e897fb6bbb7433eabdf2 -->

- 2026-09-27T06:15:03Z — 既存のテストの決まりが order 100・150・200・250 を使っていることを確かめ、U8 の値の候補から外した; 同じ文脈に入ると SecurityExtensionValidator で起動が止まるため。本番は x10、テストは x00・x50 の慣習と読んだ。
<!-- aidlc-wave-memory:u8-instance-appearance:aae184c6b867b658c0a98e23ae163f59ee69686a646a8b7f7a09d65dfb111d7a -->

- 2026-09-27T09:54:53Z — jqwik とカバレッジ（NFR9.7・NFR9.8）の設計は logical-components.md の5節に置いた; 5つの設計の分類のどれにも自然に当たらず、テストの置き場は部品の一覧と一緒に見る方が分かりやすいため。reliability-design.md からはその節を参照した。
<!-- aidlc-wave-memory:u8-instance-appearance:e6dd59ad229d85e9415777f7a18be6b9e9e71af069ccb6cd295d96003c035dfa -->

- 2026-09-27T06:17:10Z — 質問を4問（本人の特定と送り手の情報の取り方・項目ごとの誤りの形・同時のパスワードの変更・V7 の後方互換）に絞った; NFR 要件で作りが1つに決まらない点だけを選んだ。bcrypt をトランザクションの外に置く流れ、2本目の接続、カバレッジの戻し、利用者の作成で bcrypt の前に登録済みを確かめることは、NFR5.1・NFR5.2・team.md の決まりとコードから作りが決まるため、設計の要点に書いた。
<!-- aidlc-wave-memory:u2-user-preferences:df9610487060e0dd40730a977ad2af68c38d662dbb041419231bc69d45f8dcb2 -->

- 2026-09-27T06:17:10Z — 利用者の作成で bcrypt の前に登録済みを確かめる作りは質問にしなかった; 読み取り1回が増えるだけで、U3 の登録の完了は verify と完了の前に existsByEmail で拒否済みのため時間の差で新しく分かることが無い。一意の制約に当たったときの巻き戻しの印は、U3 が EmailAlreadyUsed で必ず巻き戻す（U3 の BR7.4）ため食い違わない。
<!-- aidlc-wave-memory:u2-user-preferences:e6d461d42c358303f87612986d6c0ee39c6373abcdd2466b7849c564ea574ae5 -->

- 2026-09-27T09:55:23Z — 401 の AUTHENTICATION_REQUIRED は auth.domain.AuthProblemTypes にあり user から参照できないと分かり、user.web が ProblemTypeRegistry.findByCode で起動時に引く作りにした; 同じ code を user の一覧に重ねると起動時の重複の検査で止まるため。本人の行が消えた場合は業務処理が結果の型で返し、user.web が 401 に変える（security-design.md 2節）。
<!-- aidlc-wave-memory:u2-user-preferences:0f84f9fa6f5f1439e06ab925c6b9fcec60fdf7eeb7773e040596630beff6a5e5 -->

- 2026-09-27T10:13:06Z — 質問は3問（ログインの画面の幅 375px を B4 に入れるか・axe の合否の規則・トークンを付けないパスの一覧の形）にし、ほかは要点 11 件として要約で確かめる形にした; 最初の描画のゲート・時間の測り方・フォントは承認済みの NFR 要件と機能設計でほぼ決まっていると読んだ。既存の E2E への非干渉（R-01）は、Playwright の既定のテストごとの新しいコンテキストとログインしない検査で作りが1つに決まるため、要点 6 にした。
<!-- aidlc-wave-memory:u4-display-foundation:56b7d6c946eee4bdc5252d89dcd4f7b4cd42bbce4a6dd416d188ce67616a2b5c -->

- 2026-09-27T10:13:06Z — U5〜U7 の共通の決定（幅 375px の6組は B5 で U5〜U7 の画面に当てる）はログインの画面に触れていないと読み、Q1 にした; NFR 要件の承認の場の R-02 がログインの画面の狭い幅の確かめ方を B4 の計画に求めているため。
<!-- aidlc-wave-memory:u4-display-foundation:ac55658b1b1086b6e852d106f2dcdc10355e0187af678dba6f071caba2405595 -->

- 2026-09-27T10:13:06Z — ApiClient の一覧の名前は機能設計の 6.1 がコード生成で決めるとしていたが、Q3 として前倒しで尋ねる形にした; トークンを付けないパスの判定の置き場はセキュリティの設計に当たり、今の名前は apiClient.ts の中だけで使われていて影響が閉じるため。C で「コード生成で決める」も選べるようにした。
<!-- aidlc-wave-memory:u4-display-foundation:ff036155f0ad3fd2cf3032aba4facca501d7c28bf0defc55e257f7d6fd342710 -->

- 2026-09-27T10:15:24Z — 差し込み口の order は U8 の NFR 設計の割り当て 310 を採ると要点に書いた; U3 の承認済みの機能設計と NFR 要件は「コード生成で決める」のままだが、U8 の security-design.md 2.3 が機能の名前で 100 台ずつ割り当てたため。質問にはしなかった。
<!-- aidlc-wave-memory:u3-invitation:7b67cd1870344706020893a73d49fd009f5d75398819898e51c865c0c22b0083 -->

- 2026-09-27T10:15:24Z — 承認の場の Minor（BR7.4 の経路は Unverified、R1 に警報が無いことを書き足す）を要点 5・15 に置いた; U2 の createUser が bcrypt の前に登録済みを確かめるため、この経路はふつう bcrypt を計算しない。性能の場面には入れず、正しさだけを結合テストで確かめる。
<!-- aidlc-wave-memory:u3-invitation:12a77d774d6401fff258e1fdd4945a8651c6a34f690389bb1d4f16c2419239e0 -->

- 2026-09-27T10:37:17Z — 登録の完了の拒否で U3 が自分の setRollbackOnly を付ける作りを reliability-design.md 3節に書いた; createUser が EmailAlreadyUsed で付ける印は参加したトランザクション全体の印で、呼び出し元が自分の印なしに確定すると UnexpectedRollbackException になるため。U2 の設計の「呼び出し元は必ず巻き戻す」を具体にした。
<!-- aidlc-wave-memory:u3-invitation:2db4269b78a4f9142a893a209215ed80938f98760b38926d8d1e3b255a5d756c -->

- 2026-09-27T10:37:17Z — TraceAspect が invitation の Bean の引数と戻り値を TRACE で文字列にするため、トークンと URL を伏せ字の値の型で受け渡す作りにした; NFR1.5 の漏えいを TRACE のロガーでも確かめるよう InvitationSecretLeakIT に含めた。
<!-- aidlc-wave-memory:u3-invitation:a9b2cd41060ab6b135328deed4081ad6acdac84c7737d178213cec031b6567d4 -->

- 2026-09-27T10:49:35Z — Table の足りない口と 400 の fieldErrors は質問にしなかった; NFR9.5 と機能設計の 6.2（項目ごとの誤りの形に頼らない）で作りが1つに決まるため。要点 7・9 に書き、まとめの確認で確かめる。
<!-- aidlc-wave-memory:u5-invitation-ui:abdfcc29b5d8db4d492ec04e2b35c4c4815f9e134e6d266181fac42210920976 -->

- 2026-09-27T10:49:35Z — 承認の場の Minor R-01（取り消しの 204）を画面の側にも当てた; 「成功の本文を JSON として読み、読めなければ通信の失敗」を一覧・招待・送り直しだけに当て、取り消しは本文を読まずに成功とする形を要点 7 に書いた。
<!-- aidlc-wave-memory:u5-invitation-ui:5a3debe3261e313f5a717162dff5295ecb7ebe4c42509e72f57a7bee0b330503 -->

- 2026-09-27T12:06:21Z — Q3 A（ログインの画面から開く）を画面の開き方だけに当て、測定の準備の招待の API にはログインの API のトークンを使うと読んだ; Q2 A が API で 21 件を置くとしており、準備は画面の操作ではないため。performance-design.md の上流との差に記録した。
<!-- aidlc-wave-memory:u5-invitation-ui:25bbdba487d204d01b8303deb6981a9d188e47b2d7ba9fe56fdce72cac3964b5 -->

- 2026-09-27T10:50:06Z — 質問は2問（差し替えの答えと本物の応答の形の照合・400 の fieldErrors の出し方）にし、ほかは要点 11 件として要約で確かめる形にした; 検査の作りは U4 の NFR 設計、トークンの扱いは機能設計と NFR 要件でほぼ決まっていると読んだ。検査の回数と時間は承認の場で受け入れ済みのため、見込みの値を要点 4 に書くだけにした。
<!-- aidlc-wave-memory:u6-registration-ui:4f39fef0ed52781bbd5269dfc0e27dcc828d99136c56676a301a22a231cc9735 -->

- 2026-09-27T10:50:06Z — 登録の完了の 400 に U3 が fieldErrors を載せることになった点を Q2 にした; 承認済みの機能設計の Q3 A は項目ごとの誤りの形が無いことを前提にしており、前提が変わったため。読む関数は U7 の features/preferences にあり、機能どうしは依存しないため置き場の選択が要る。
<!-- aidlc-wave-memory:u6-registration-ui:1222745b6e4cf3014d8f03f52e31dac9cd80c3fc74f3de7c31abe5f40668e6d2 -->

- 2026-09-27T10:50:06Z — E2E-1 の招待メールのリンクの取り出し方は、NFR 要件どおり infrastructure-design の持ち主のまま質問にしなかった; この段では、E2E-1 がその手段で取り出したリンクを使う前提だけを書いた。
<!-- aidlc-wave-memory:u6-registration-ui:055264812e93d9deb361c097a4b2bca3910218818487827aaa1247abcc8e17a9 -->

- 2026-09-27T12:10:20Z — 質問の要点 5 の「window.location.hash から取り出す」を、成果物では機能設計の W2 のとおり React Router の場所の hash にした; 承認済みの機能設計がルーターの場所を読むと決めており、要点の書き方が不正確だったため。作りの結論（最初の描画で読み、確定の後に消す）は変わらない。
<!-- aidlc-wave-memory:u6-registration-ui:6317239679329c9ef4d6044f9368d277fe15bf2a9e70443958fc1d9a9e94eaab -->

- 2026-09-27T12:10:20Z — StrictMode の二重の確かめは、機能設計の W4 の4（害は無く最後の答えだけで移す）のままとし、送らない印は足さなかった; 初稿で印を足しかけたが承認済みの設計と違う作りになるため取り下げた。
<!-- aidlc-wave-memory:u6-registration-ui:ac163a20ddda584e6010cf7f3e4204079860b1b1502b491ffa6c08e3103f25c9 -->

- 2026-09-27T12:10:20Z — 見本の型の食い違いは frontend の typecheck（tsconfig の include に e2e が入る）で verify の中で気づけると確かめた; E2E-1 の毎回の照合と合わせ、画面の側の型と本物の応答の両方からずれを見る。
<!-- aidlc-wave-memory:u6-registration-ui:b9431262dcfd7bc096962bc675841e9f18e54771915adf5987a2c26a66e275e0 -->

- 2026-09-27T10:49:58Z — 質問は3問（測りの置き場と利用者・ログインの後の検査の利用者と組の当て方・検査する状態）にし、ほかは要点 11 件として要約で確かめる形にした; 検査の組・合否・axe-core の読み込み方は U4 の NFR 設計で、fieldErrors の形は U2 の NFR 設計で決まっていると読んだ。fieldErrors と U6 の共用の関数の関係、骨組みの変更の既存の画面への影響は、作りが1つに決まるため要点 6・8 にした。
<!-- aidlc-wave-memory:u7-preferences-ui:d4a14e755d5df9d9ffd8148da126629ae1621f53cb48c61489b4db59ef131dc0 -->

- 2026-09-27T10:49:58Z — U4 の組の切り替え（U4 の鍵を初めのスクリプトで置く）はログインの後の画面では効かないと読み、Q2 にした; ログインの応答の利用者の設定が当たり（U4 の W5・D8）、プリファレンスの画面は GET の値でそろえる（D2）ため。推奨は GET /api/me/preferences の差し替えで D2 の本物の道を通す案。
<!-- aidlc-wave-memory:u7-preferences-ui:84de693dd04c88363add0de94549bc7de6d3c0d57fcd68f948cd205c61483d5a -->

- 2026-09-27T10:49:58Z — 機能設計の 10節の (c)（fieldErrors の形は U2 のコード生成で決まる）は、U2 の NFR 設計の3節で形が決まったため、この段で読み取りと理由の対応（REQUIRED → required など、INVALID_VALUE は選択の一般の文言）を設計する案にした; U6 の共用の関数の理由の union（U6 の機能設計の6節）と同じ union に寄せ、1つの表で文言にする。
<!-- aidlc-wave-memory:u7-preferences-ui:5b2b17438398d24005638b634f4d81be14b049d07303e0063c9f58e68aeb8515 -->
## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->


- 2026-09-27T06:22:08Z — BR2.4 の検査（三重の波かっこ・{{& を書かない）に、部分テンプレート {{> と区切りの変更 {{= を書かないことを足す案にした; 区切りを変えると三重の波かっこの検査をすり抜けうり、Mustache.compile(String) は解決できない部分テンプレートを黙って空にするため。承認済みの BR2.4 の趣旨を強める追加として、成果物の上流との差に書く。
<!-- aidlc-wave-memory:u1-mail:d395eefc112f9521276480449cbc0b687309546dcf21cd2099c3676a2b29acae -->

- 2026-09-27T09:49:19Z — NFR11.1 の確かめ方（招待のメールが受け手の画面に出ることを B1 の完了の条件にする）を、B1 では profile の起動と isConfigured が真になることまでとし、画面での確かめを B3 に引き継ぐ設計にした; B1 の時点では本番の一覧が空で招待のテンプレートが無いため。Delivery Planning の B1 の完了の条件（profile で起動できる）とは食い違わず、NFR11.1 との差として logical-components.md の11節に書き、B1 の計画で依頼者に確かめる。
<!-- aidlc-wave-memory:u1-mail:0acc5efc2f4f611ab55f32a9a588bc02b8afdbecb0f82b081ce1400eb0b5cfcf -->

- 2026-09-27T09:49:19Z — Observation の属性の確かめに micrometer-observation-test（TestObservationRegistry）を足さず、ObservationRegistry.create() に文脈を集める小さな受け手を付ける設計にした; 今の lockfile に無い依存を増やさずに同じことを確かめられるため。質問ファイルの Q2 B の書き方との差として記録した。
<!-- aidlc-wave-memory:u1-mail:2dc988a0484058d7308890966d0ee76cf85c49253b4f16571404e9fe93ce5b59 -->

- 2026-09-27T09:54:53Z — Q1 A で order を機能の名前の割り当てに変え、u3-invitation の 310 を U8 の段で先に決めた; 承認済みの U3 の機能設計・NFR 要件は「コード生成で決める」としており、差を security-design.md の8節に記録した。U3 の NFR 設計・コード生成への引き継ぎが要る。
<!-- aidlc-wave-memory:u8-instance-appearance:6b310396b737790d8846f38bfea5e5830059605a62d434561d8af67314bedc19 -->

- 2026-09-27T09:54:53Z — SecurityRuleContributor の説明文の変更が「U8 の範囲の書き足し」から「割り当て全体の書き直し」に広がった; common.security は packagesJudgedByTotal にあるため、team.md の「手を入れる Bolt では一覧から外す」に当たるかを、書き直す Bolt の計画で依頼者に確かめると security-design.md の8節と logical-components.md の5節に書いた。
<!-- aidlc-wave-memory:u8-instance-appearance:5fbb1e536f36e833a9a97e28630b01afc73ec97192218e6dca7d523dcda321b9 -->

- 2026-09-27T09:55:23Z — 要約の要点 14（PasswordChangedEvent は user.service）と違い、user.domain に置いた; audit.domain.AuditEventFactory は既存の出来事をどれも各機能の domain から読むため。依頼者が Looks correct とした要約との差として security-design.md の S-D3 に記録した。
<!-- aidlc-wave-memory:u2-user-preferences:83c02275264926acbd150519c49c1f220caa9d78ca1e96febc940648cd49f700 -->

- 2026-09-27T09:55:23Z — 契約 C4 に無い fieldErrors の形を決め（Q2 A）、パスワードの書き込みを条件つきの更新にした（Q3 A）; 契約と機能設計の文書は書き換えず、security-design.md の S-D1・S-D2、reliability-design.md の R-D1・R-D2、performance-design.md の P-D1 に差を記録した。C4 への反映は後の段。
<!-- aidlc-wave-memory:u2-user-preferences:9a5ecf1b8d415acdb472c7fab89a16151c87a295bbac53c41bec1f2e53b615db -->

- 2026-09-27T10:34:50Z — Q2 A の「scrollable-region-focusable が無ければ名前で足す」は当たらないとした; 手元の axe-core 4.13.0 の getRules() で scrollable-region-focusable が wcag2a、color-contrast が wcag2aa を持つと確かめたため。代わりに、版を上げてタグが変わっても抜けないよう、2つの規則が結果にあることを検査の中で確かめる設計にした（security-design.md の 6.4）。
<!-- aidlc-wave-memory:u4-display-foundation:6064498b974cc8c9c55cde175bdab14ba656e859cb3d8afe2018785520cccfb2 -->

- 2026-09-27T10:34:50Z — NFR6.4（Serif を読まない）と NFR9.4（CSP の違反が無い）の確かめを、記録だけでなく失敗の条件にした; 時間と違って不安定ではないため。NFR 要件の記述（記録する）への追加として performance-design.md の上流との差に書いた。
<!-- aidlc-wave-memory:u4-display-foundation:d73de017b9c3e0a980e2145aa76fee26c0aeb579bc3f9be83db39e4bac4003cb -->

- 2026-09-27T10:34:50Z — 質問ファイルの要点 4 の「font-display: swap と unicode-range で読むときだけ読む」の unicode-range を、成果物では使わなかった; 手元の @fontsource/noto-sans-jp 5.3.0 の japanese-400.css は @font-face が1つで unicode-range を持たなかった。読むときだけ読むのは 'Noto Serif JP' を使う文字を描くときに限るためで、結論は変わらない。Serif も同じ形の見込みとし、コード生成で確かめる形で performance-design.md の5節に書いた。
<!-- aidlc-wave-memory:u4-display-foundation:81debbfe7a49939b838ee8937c58dfd356c46fe808384ceae4635d8bd02ee752 -->

- 2026-09-27T10:37:17Z — order 310（SD-D1）、R1 の警報なしの書き足し（SD-D2）、BR7.4 の Unverified（PD-D1）、C5・C6 への fieldErrors の追加（SD-D3）を各成果物の上流との差に記録した; 承認済みの U3 の文書と契約は書き換えず、契約への反映は U3 のコード生成の計画までに行う。
<!-- aidlc-wave-memory:u3-invitation:b45408181905c678cb0fe06ff260d1b44f15567233553fa0175cb0f04001b46c -->

- 2026-09-27T10:37:17Z — 定期の削除の既定の案（3 時 45 分・1000 件）と invitation の中の @EnableScheduling を書いた（RD-D2・RD-D3）; 最終の値はコード生成で決める承認のままとし、auth の設定に黙って頼らないため。
<!-- aidlc-wave-memory:u3-invitation:9cac76dd81c9162690fc539e7a3a4e080638514df8fb0b07f567dc994f75b9aa -->

- 2026-09-27T12:06:21Z — 質問の案で「既存のリンタの決まりを緩めない」としかけた console の扱いを、テストの見張りで確かめる形に直した; frontend/.oxlintrc.json に console を止める決まりが無いと確かめたため。決まりを足すとほかの機能に及ぶので足さない。
<!-- aidlc-wave-memory:u5-invitation-ui:a2bb044a7875d3df8b5e89e5b7dc7950bf1ef287bcf66fb57d2ce8699e739660 -->

- 2026-09-27T10:50:06Z — E2E-1 の5回の計測で、アドレス欄の #token= と CSP の違反も確かめる（失敗の条件にする）案にした; NFR 要件では NFR1.3 は流れの1回、NFR9.5 は記録だけだが、U4 の NFR 設計が不安定でない確かめを失敗の条件にした前例に合わせた。要約で確かめ、成果物の上流との差に書く。
<!-- aidlc-wave-memory:u6-registration-ui:14df7de32c82c6041bcf852ed3db68972b5f78ce53387a9aba3e4792638ea9da -->

- 2026-09-27T12:10:20Z — 計測の5回で実際のブラウザの保存にトークンが無いことも確かめる（2.1 の (c)）を足した; 要約に無かった追加で、jsdom と実際のブラウザの保存の差を埋める読み取りだけの確かめのため。performance-design.md の PD-D3 に書き、承認の場で伝える。
<!-- aidlc-wave-memory:u6-registration-ui:fab1b66ffaf2cd6055ea6bc4539e264cddf484ecd85c7a9c02a2a760e8d5347f -->

- 2026-09-27T12:10:20Z — 機能設計の W2 の4（閲覧の履歴の同期ではフラグメントの無いアドレスだけが残る）を言い過ぎの見込みとして security-design.md の SD-D2 に書いた; 最初の移動のアドレスが閲覧の履歴に残るかは確かめていない。project.md の Change Control に従い、機能設計を直すかは承認の場で依頼者に確かめる。
<!-- aidlc-wave-memory:u6-registration-ui:aa0ac7ed2568dd7fdc471f99accf13c848b88c588dcaa399399c9fea5bf97997 -->

- 2026-09-27T12:13:53Z — Q1〜Q3 はすべて A、まとめは Looks correct で答えが出た; U4 の組の切り替え方をログインの後の画面では使わず GET /api/me/preferences の差し替えにしたことと、機能設計の 10節の (c) の fieldErrors の形をこの段で当てたことを、logical-components.md と security-design.md の上流との差に記録した。承認済みの文書は書き換えていない。
<!-- aidlc-wave-memory:u7-preferences-ui:55d7cb9cd4b82908fcc5fc96f551e18ef1a0dbbe4d8277ddc2ae91a92951d913 -->

- 2026-09-27T12:13:53Z — 測りの登録の完了を画面ではなく API（POST /api/registration/complete）で行う作りにした; 画面の流れは U6 の E2E-1 が確かめるため、測りの準備を短くする。招待を使える設定か受け手の手段が無いときは測りを飛ばして Unverified とし、U5 の performance-design.md の 4.2 と同じ扱いにそろえた。
<!-- aidlc-wave-memory:u7-preferences-ui:d2b12911f8f44101e94f618244deec3d0e2c43b811df4e713886b1dbdb5526bf -->
## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->


- 2026-09-27T06:22:08Z — 想定外の例外は、固定の文言と原因の連なりの型の名前だけを持つ U1 の例外に包み、原因の例外そのものを付けない案にした; 既存の @RestControllerAdvice が 5xx を ERROR とスタックトレースで出すため、部品の例外のメッセージ（宛先や SMTP の応答を含みうる）がログに残るのを防ぐ。代わりに調べるときは型の名前・時刻・トレースIDで絞ることになる。
<!-- aidlc-wave-memory:u1-mail:10d12543afbf1acbd0a77c17095fbcf776a1abfe4b56a08beec03732b068f1d3 -->

- 2026-09-27T06:22:08Z — 差し込みの名前と一覧の一致は起動時ではなく BR2.6 のとおりテストで確かめる案にした; java-mustache-processor の公開の API は Mustache.compile と Template.render だけで構文木を出さず、起動時に名前を数えるには部品の内部に頼ることになるため。
<!-- aidlc-wave-memory:u1-mail:9ce6f75dc69f82238f332c8080b4c57f922c36b2c6c7802d04bd3c2a15d1e0d9 -->

- 2026-09-27T09:49:19Z — Observation は send の全体（NOT_CONFIGURED と確かめの失敗を含む）を覆い、FAILED は error にせず outcome と failure.kind のタグで表し、error には包んだ MailUnexpectedException だけを渡す設計にした; トレースの例外のイベントに部品の文言（宛先・SMTP の応答）を載せないため。代わりに、FAILED の送信はトレースの上ではエラーの印が付かず、タグで絞る必要がある。
<!-- aidlc-wave-memory:u1-mail:f3d2057fb6c688ad07bbc6360e60648c4d00613abd44c437f621b44305d08210 -->

- 2026-09-27T09:49:19Z — 本番の MailTemplateRegistry は一覧と置き場を受け取って作り、テストは別の置き場 mail/test-templates とテスト用の一覧で作る設計にした; B1 の時点で本番の一覧が空でも送信の結合テストを書け、テスト用のテンプレートが本番の数え上げに混ざらないため。Spring を起動する結合テストはテストの設定で Bean を置き換える必要がある。
<!-- aidlc-wave-memory:u1-mail:01367f250b0821f457a92196fe015b01a7ad8a361ff9421cd48202ec47612ce1 -->

- 2026-09-27T06:15:03Z — NFR5.2 の確かめは、接続を借りた回数の比べ（Q2 A）を推奨とし、プールの一時停止の再現（Q2 B）を選択肢に残した; 借りた回数が増えなければ一時停止で待たされることも無く、同じ文脈で安く確かめられる。一時停止の再現は心配の場面そのものだが、MBean を有効にした専用の文脈でテストの時間が増える。
<!-- aidlc-wave-memory:u8-instance-appearance:cb7291fd3b6a9008b3ee87f2b5602f0f712ee2128e8d311d578a511be89a8732 -->

- 2026-09-27T09:54:53Z — 借りた回数の比べは同じ文脈のほかのテストの要求が混ざると誤って失敗しうる; reliability-design.md の3.2節に、比べる前に待ち合わせてほかの要求が混ざらない形にすることを書き、具体はコード生成に残した。指標が取れないときの数える包みへの切り替えも同じくコード生成で記録する。
<!-- aidlc-wave-memory:u8-instance-appearance:6fe7db00d3293d79e0a1580db297c3502a15bf53d085acfaae5fdc2c851614ca -->

- 2026-09-27T06:15:03Z — Q1 A は U8 の段で u3-invitation の order（310）まで割り当てる案にした; 作る順（B3 → B4）どおりの並びで重なりを先に防げる代わりに、U3 の承認済みの「U3 のコード生成で決める」を U8 の決定で先に埋める形になるため、U3 の NFR 設計・コード生成への引き継ぎを明記した。
<!-- aidlc-wave-memory:u8-instance-appearance:3030c6f0d4ed628a9728b72b3a5bc7515ed163884cc14d3568a89cffc9b7652d -->

- 2026-09-27T09:55:23Z — createUser は EmailAlreadyUsed のとき自分で巻き戻しの印を付ける作りにした; 一意の制約に当たった後に正常に戻ると、自分で始めたトランザクション（初期管理者の作成）で確定のときに UnexpectedRollbackException になるため。呼び出し元は EmailAlreadyUsed で必ず巻き戻す前提になり、Javadoc に書く。
<!-- aidlc-wave-memory:u2-user-preferences:acc14baaf788abe40735fac0aa919a9176ac4d69f56fc7b1db70410cbb9529d0 -->

- 2026-09-27T09:55:23Z — Hibernate の validate が余分な列を許すことは自動のテストで確かめず、戻しの練習（deployment-execution）に回した; 1つ前の版のエンティティをテストで持てないため。見込みが外れたと分かるのは配備の段になる。
<!-- aidlc-wave-memory:u2-user-preferences:3b711e2d11d68d685e6c0df1f3e6b9a05fd07925933164b2487873daab5b755e -->

- 2026-09-27T10:13:06Z — axe-core は Node の側で本体を読み、page.evaluate で評価する案にした; addScriptTag の中身の埋め込みは CSP の script-src 'self' で止まり、bypassCSP はアプリの条件とずれて同じファイルで CSP の違反を確かめられなくなるため。同じオリジンの道への差し替えは、検査のためだけの道が増えるため選ばなかった。
<!-- aidlc-wave-memory:u4-display-foundation:bc25e19b6e1aeb854937e08d5e93bcdcd34facdf3c89181f0856924acdee9984 -->

- 2026-09-27T10:13:06Z — ブランドカラーの組は /api/appearance の答えの差し替え、テーマと文字の大きさは U4 の鍵の初めのスクリプトで切り替える案にした; html の属性を直接書き換えるより本物の読み込みと当て方の道（D5・W1・W3）を通せるため。代わりにサーバーの設定の値そのものからの当て方は、この検査では通らない。
<!-- aidlc-wave-memory:u4-display-foundation:ce2300c6b81accd52125e932d3caf69c92349048908840c61ed06efdbfc46fbe -->

- 2026-09-27T10:13:06Z — 最初の画面の時間はテストの側の時計（goto の直前から見出しが見えるまで）で測る案にした; Playwright の待ちの間隔を含み長めに出るが、長めの側で判定するほうが目標を緩めない決まりに合うため。
<!-- aidlc-wave-memory:u4-display-foundation:641fb27a09d70414435b6abc25120dd70afc9bab381b810299191b99f360657d -->

- 2026-09-27T10:15:24Z — ADR-010 の「H2 の索引で招待中を1件に限れるか」を使い捨ての試しで確かめた; スクラッチの Java（メモリの中の H2 2.4.240）で、生成列と一意の制約で PENDING の2件目が 23505 で拒まれ、同時の追記は待った後に拒まれて1件だけ残った。Flyway と Hibernate の validate との組み合わせは未確認で、B3 の最初の結合テストで確かめる案（Q2 A）にした。
<!-- aidlc-wave-memory:u3-invitation:fa1146b8b1ff3e378ec9c7371f89fb633e6cf5a458d334e0ef54c93ce4394817 -->

- 2026-09-27T10:37:17Z — 送信の失敗のログは U3 の INFO 1件と U1 の WARN 1件に分けた（Q5 A）; 1つの失敗に WARN が2件並ばず、ログの設定と U1 を変えずに済む代わりに、invitationId と失敗の WARN は同じトレースIDでつないで読む必要がある。
<!-- aidlc-wave-memory:u3-invitation:e9834e4b32727fc15236c1f36281513b361addcc8f42c666ef83475505791f1a -->

- 2026-09-27T10:37:17Z — 公開の道のアクセストークンの扱いを変えない（Q3 A）ことを残る危険 R3 として security-design.md に足した; auth と common.security に手を入れずに済む代わりに、画面の側がトークンを付けると登録の完了が 401 になりうる。
<!-- aidlc-wave-memory:u3-invitation:0bf80ae87735deef065127ded3a0de60972c899d7d75eda5b34bb99f805fa326 -->

- 2026-09-27T12:06:21Z — 警告の状態は、同じコンテキストで差し替えの答えを替えてページを読み込み直して出す形にした; ログインを1組1回に抑えられる代わりに、Cookie のリフレッシュトークンの復元の道に頼る。
<!-- aidlc-wave-memory:u5-invitation-ui:07699192be1d7db8da5d0ddb677d3ce27462430216a98b426448ca6e5fb4288c -->

- 2026-09-27T12:06:21Z — 招待を使える設定が無いときは測定を test.skip にして理由を注記に残し Unverified で引き継ぐ形にした; 目標を緩めずに済む代わりに、infrastructure-design が E2E の WAR に設定を渡すまで画面の時間は測れない。
<!-- aidlc-wave-memory:u5-invitation-ui:45ce9c6c7bc5da023be5a0624c6ac128e8fc70cfd462db1746e685b5781f5976 -->

- 2026-09-27T10:50:06Z — Q1 の推奨は E2E-1 の中で本物の確かめの応答と見本の形を毎回照合する A にした; R-02 の「1回の照合」より強く、形のずれに e2eTest のたびに気づけるため。代わりに E2E-1 が見本のモジュールに依存し、流れのファイルと検査のファイルが見本を共有する。
<!-- aidlc-wave-memory:u6-registration-ui:7628204705461cf38fce8375b26ad126e18bb6217f7ba67f4447c5823c5c1e56 -->

- 2026-09-27T10:50:06Z — Q2 の推奨は機能設計の Q3 A のままの A にした; サーバーの 400 は画面の確かめをすり抜けたときに限られ、項目に結び付けると U7 の承認済みの置き場に手が入るため。代わりに CR6.1 の「最初の誤りの項目にフォーカス」はサーバーの 400 では満たさないまま（Q3 A で受け入れ済み）。
<!-- aidlc-wave-memory:u6-registration-ui:158bb9451a6ddd321a9d537afbd8723588d27136437033cef43664fdbb474c9e -->

- 2026-09-27T12:13:53Z — 2つの画面を同じコンテキストで続けて検査し、誤りの状態も同じコンテキストで続ける作りにした; 組ごとのログインは 40 回、検査は 80 回になる。検査の中で PUT・POST が送られていないことをコンテキストの要求の記録で確かめ、サーバーの状態を変えないことを保つ。
<!-- aidlc-wave-memory:u7-preferences-ui:9a5edab6aae3350f74984eaad83b56f2d5733a19e0af9041cee36e390a384650 -->

- 2026-09-27T12:13:53Z — 送信中の印を useState ではなく useRef で持つ作りにした; 状態の更新は次の描画まで見えず、描画の前の2回目のクリック・Enter を止められないため。表示は状態の saving・sending で描く。
<!-- aidlc-wave-memory:u7-preferences-ui:096379c8d44e1caff86f5e14078057ce1f18c33a63085da523753c0080d51e1b -->
## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->

- 2026-09-27T06:22:08Z — TraceAspect は web・service・domain・repository の Bean の引数と戻り値を toString で TRACE に出すため、描画・組み立てを別の Bean に分けると差し込む値の Map や描いた本文（招待の URL）が出うる; 守り方を Q1 にした（推奨は用途名の下位パッケージ mail.template・mail.transport に置く A。前例は dsl/parse・dslmanage/generate）。
<!-- aidlc-wave-memory:u1-mail:4466d8d3b0804f4377da76cb4a8000e1714bea6fc70e571e10e358b1f681ae86 -->

- 2026-09-27T06:22:08Z — 今のアプリには自前の span・指標が無く、BR6.3 は属性を付けるかを決めていない; 送信の時間を分けて見るかを Q2 にした（推奨は Observation を1つ作りタグを templateId・language・outcome・failureKind に絞る B）。
<!-- aidlc-wave-memory:u1-mail:ca6b64488f5f6001330fccf3123218ef09b7870fbe181c94ed4a46e129829f0d -->

- 2026-09-27T06:22:08Z — 承認の場の R-01（starttls.enable だけの設定の平文の危険を README に書くことを B1 の完了の条件に）と R-02（環境変数から点を含む鍵への結び付きと composite build の5条件を B1 で確かめ、崩れたら ADR-010 の切り替え）を、要点 12 と logical-components.md の「B1 で確かめること」に引き継ぐ案にした; 環境変数の結び付きが成り立たないときは既定の 3 秒で動くことを確かめ、変え方を B1 で依頼者に諮る。
<!-- aidlc-wave-memory:u1-mail:991559ca88d21d8f1c3f24bd68aeb172fce377f4458c4fbaa052570df76fbbaa -->

- 2026-09-27T09:49:19Z — 実行可能 WAR の中で classpath の mail/templates/*.html を数え上げられるかは確かめていない; B1 で手元の WAR の起動で確かめ、できなければ一覧からファイル名を組み立てて直接読む形に替える（logical-components.md の10節）。
<!-- aidlc-wave-memory:u1-mail:76fd51b788c0dc0d7e0b18fc2149e26a0d26dc627a1fae35d0ba1443dba35d1e -->

- 2026-09-27T06:15:03Z — SecurityRuleContributor の説明文の書き足しは packagesJudgedByTotal にある common.security の変更になる; 承認済みの NFR9.7 はコードの中身を変えない扱いとしているが、team.md の「手を入れる Bolt では一覧から外す」を説明文だけの変更にも当てるかは明記されていない。書き足す Bolt（Q1 A なら先に作る U3 の B3 になりうる）の計画で扱いを確かめる。
<!-- aidlc-wave-memory:u8-instance-appearance:d1fa380949995885919031f586b8fef140634cd91e69da6b59833cc717482e9d -->

- 2026-09-27T06:17:10Z — 新しい user.web は auth の主体の型と送り手の情報を使えない（既存の ArchUnit「user does not depend on auth」）; 機能設計の entities.md は「ClientInfo と同じ取り方」とだけ書いている。Q1 で、user の中で取る・common へ移す・構造の検査を緩めるのどれにするかを尋ねた。
<!-- aidlc-wave-memory:u2-user-preferences:525e7b70a756e9327bb6a74bc70d769816f71ac12a81386fa201959b66758183 -->

- 2026-09-27T06:17:10Z — 今の User は全列を持つエンティティで、変更の検出の更新は全列を書く; BR3.4（相手の列を古い値で上書きしない）を守るため、書き換えは更新の問い合わせで行うと要点に書いた。Flyway 12 が知らない新しい移行を無視する既定と、Hibernate の validate が余分な列を許すことは、まだ見込みのまま（Q4）。
<!-- aidlc-wave-memory:u2-user-preferences:512eb3ccf6e2b8a972b2cf827c8cd86898665e9be511b5e9757342082d7b925c -->

- 2026-09-27T10:34:50Z — page.evaluate で axe-core の本体を評価しても CSP に止められないことは、まだ実際には確かめていない; @axe-core/playwright と同じ読み込み方で、DevTools の手順での評価はページの CSP の対象外という見込みに頼る。コード生成で、CSP の見出しの付いた WAR の画面で評価でき、違反の知らせが出ないことを確かめる。
<!-- aidlc-wave-memory:u4-display-foundation:0c92a678c7e508c4bc557ae04758b2b6daaf496bf14c15b24262530c33bcc43d -->

- 2026-09-27T10:34:50Z — 既存の 010 の collectProblems と同じ見方の手伝いを、新しい検査の手伝いのモジュールに複写する設計にした; 既存のファイルを変えない方針を優先したため、同じ考えの手伝いが2か所になる。まとめるかは、コード生成の計画で依頼者に確かめる余地がある。
<!-- aidlc-wave-memory:u4-display-foundation:9bd7a68dd949c59fdd1a6a56d30e8b7639959ac0d4e33fe42e52ed3475abbdd7 -->

- 2026-09-27T10:15:24Z — 公開の2つの道でも既存の AuthSecurityContributor がアクセストークンを読むため、壊れたトークン付きなら permitAll の前に 401 になると分かり Q3 にした; 画面の側は U4 の ApiClient が付けない前提で、サーバーでも読まないようにすると common.security と auth.web に手が入る。
<!-- aidlc-wave-memory:u3-invitation:7eedfdc8e9284a927088bd298c545feab47093832238d45e8987b857bc4ace76 -->

- 2026-09-27T10:15:24Z — invitation から auth への依存を Q1 にした; 構造の検査は禁じず dslmanage の前例があるが、承認済みの components.md の Invitation の depends_on は UserAccount と Mail だけで、U2 は機能の中で取る形を選んでいる。
<!-- aidlc-wave-memory:u3-invitation:e8d815019830356aadd6431293470a50f77df08fbc5a02087793cf8bea3f7635 -->

- 2026-09-27T10:15:24Z — 送信の間に接続を持たないことの確かめ方を Q4 にした; 承認どおりの「一覧が応答する」テストだけでは接続が 30 本あるため持ち続けを見落としうる。
<!-- aidlc-wave-memory:u3-invitation:2111038d8a58abbc2b865293e1c8d88bc64dc441a8712fd7efc9645f7f15ee4b -->

- 2026-09-27T10:15:24Z — 送信の失敗のログを Q5 にした; U1 は失敗ごとに WARN を1件出すが invitationId を持たず、logback の MDC は traceId・spanId だけを出すため、重ねない形に選択肢がある。
<!-- aidlc-wave-memory:u3-invitation:2c287ea7c4a0c5de94fd44a907f98aba77041f646c40d27d0ac3131c2d0dc1b4 -->

- 2026-09-27T10:49:35Z — 検査で一覧の行と警告の状態をどう出すか（Q1）; E2E の WAR は1つを共有し招待を使える設定にするはずで、本物の警告の状態は出せないため、page.route の差し替え（U6 の Q2: B と同じ）を推奨にした。依頼者の答えを待つ。
<!-- aidlc-wave-memory:u5-invitation-ui:d87c900554b25af4a461501711ec45d500d0350f40dbff91e221b7299eb5d64a -->

- 2026-09-27T10:49:35Z — 測定の 21 件の用意と測る場（Q2）とログインの開き方（Q3）; リフレッシュトークンは使うたびに作り直されるため storageState を使い回せず、コンテキストごとのログインが要る。測定は本物の一覧を読むため、E2E の WAR の招待の設定（infrastructure-design）に頼り、無ければ Unverified とする案にした。
<!-- aidlc-wave-memory:u5-invitation-ui:7f09e2e4e16bf3bd8e0e507967d23534093e8e6c2ae4b28d56511848a55019f2 -->

- 2026-09-27T12:06:21Z — Q1〜Q3 はすべて A、まとめは Looks correct で答えが出た; B5 の検査のファイルの番号・初期管理者の設定を変えない方針・共用の手伝いの形は、B5 のコード生成の計画で U6・U7 とそろえて決める（logical-components.md の5.1）。
<!-- aidlc-wave-memory:u5-invitation-ui:2e4c8037ffce85a371465ea43974fa430b9a9add3347e812f3c7fda09fe9958e -->

- 2026-09-27T10:50:06Z — 検査の時間の見込み（U6 の分 約 1〜2 分、e2eTest の検査の部分 約 3〜8 分）は実測していない; 1組あたり 1〜3 秒の見込みからの試算で、B5 のコード生成と Build and Test で実測して記録する。
<!-- aidlc-wave-memory:u6-registration-ui:10b310021983c1e18cec794cf1b7f9ea45c02e2449fda0fe40d444f4b20abf16 -->

- 2026-09-27T10:50:06Z — フラグメントを置き換えの移動で消しても、ブラウザの閲覧の履歴に最初のアドレス（トークンを含む）が残るかは確かめていない; ブラウザに依存するため残る危険として要点 5 に書き、トークンの1回だけ有効と有効期限（U3）で支える形にした。
<!-- aidlc-wave-memory:u6-registration-ui:6b91703a40ed0058ed3751044606463ab147e81d317d39e5f30eaaf7ec49a016 -->

- 2026-09-27T10:49:58Z — Q1 の推奨 A（測りのテストの中で招待から利用者を作る）は、受け手からリンクを取り出す手段と E2E の WAR への SMTP の設定の渡し方に頼る; どちらも infrastructure-design の持ち主で未定（U5・U6 と同じ前提）。B5 の検査のファイルの番号と、U5〜U7 の検査・測りで初期管理者の設定とパスワードを変えないことは、B5 のコード生成の計画で U5・U6 とそろえる必要がある。
<!-- aidlc-wave-memory:u7-preferences-ui:e3f3527bf703cc8062f800918678e588dd4b484f65d4f170e98660d61456d9ad -->
