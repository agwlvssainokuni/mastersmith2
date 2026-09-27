<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->


- 2026-09-25T15:58:57Z — 前後の空白を除くと空になる値は「無い」と同じ扱い（既定・警告なし）とした; Q2 A は空白を除いて判定すると決めたが、空白だけの値の扱いは決めていなかった。空の文字列を既定とする application.yaml の書き方と揃え、BR1.3 に明記した。
<!-- aidlc-wave-memory:u8-instance-appearance:6c4138150f75989b207caa87ae21f477a56f70b61c14881c2a7ad52d482727b0 -->

- 2026-09-25T15:58:57Z — traceability.json の upstream_ids を FR8.1・FR8.2 とした; CR2 は受け入れ基準の番号を持たないため、依頼の指示どおり要件の枝番を使った。FR8.2 の Given/When/Then と CR2 の確かめ方（サーバー側）は functional-spec.md の7節で BR に対応づけた。
<!-- aidlc-wave-memory:u8-instance-appearance:b4685c7b8d986cb7b5679fe7651614a7bebb11a48180ef2fb5d6ae4c5f1e3a1f -->

- 2026-09-25T15:58:49Z — Q1 C の templateId の文字の決まりは選択肢 A のもの（英小文字・数字・ハイフン）を引き継いだ; C はファイル名を下線で区切るため、templateId に下線を許さないと区切りが一意になる。置き場に名前の合わないファイルや一覧に無い templateId のファイルがあるときも、「壊れている」と同じく起動を止める扱いにした（BR2.1・BR2.3）。
<!-- aidlc-wave-memory:u1-mail:f81e0d20f7109ba6e4d2fdc35fa98293bf9e2bef095e78fb26ddf53fa5c7293e -->

- 2026-09-25T15:58:49Z — 契約 C1 の宛先の型「EmailAddress」は、U1 の中に同じ決まりを持つ形で読んだ; 既存の EmailAddress は user.domain の静的な決まりで、部品 Mail は depends_on が空のため依存させない。正規化は呼び出し元が行い、正規化済みでない宛先は INVALID_INPUT とした（BR3.1、functional-spec.md 10節）。
<!-- aidlc-wave-memory:u1-mail:e1d0ca0fba2fcba66366fbf94c166047c3c440558ccfa764f7e3476275477f24 -->

- 2026-09-25T15:58:49Z — isConfigured の意味を契約の「接続先が設定されているか」から広げた; Q2 B（差出人も必須）と Q3 A（資格情報と暗号化 NONE は設定がないと同じ）の答えの帰結で、真偽を返す形は変わらず契約の持ち主は U1 のため、functional-spec.md 10節に差として書いた。
<!-- aidlc-wave-memory:u1-mail:2feb94915d872b85e6acaa17744a926629c22e6fff66c6f41d25142d5cbbc1b5 -->

- 2026-09-25T15:58:49Z — 答えで決まっていない細部を設計で決めた; ポートを省いたときは暗号化の方式の標準の番号、STARTTLS を受け付けない受け手は CONNECTION_FAILED、認証の拒否は REJECTED、差し込む値の null は INVALID_INPUT、SMTP の項目が1つも無いときは WARN を出さない（前の Intent の対象DB の設定と同じ扱い）。
<!-- aidlc-wave-memory:u1-mail:3cd704243b9c165275bafd10f127bc4e5c2e3d9717c77da70e443918008ef873 -->

- 2026-09-25T16:05:00Z — Q3 A の「Unicode の空白の文字」を White_Space の性質と読み、前後を除いてから Cc・Cf を判定する順にした; 前後の改行・タブは除かれて通り、内側にあるときだけ Cc として拒否される。U+00A0 なども前後なら除く。
<!-- aidlc-wave-memory:u2-user-preferences:38f3496085dc461c03e34b0ab169e04da1ba217fd96908225925bc9edaabb22d -->

- 2026-09-25T16:05:00Z — 今のパスワードが空なら VALIDATION_FAILED、72 バイトを超えるなら照合せず不一致（PASSWORD_CURRENT_MISMATCH）とした; 質問の要点 7 の順序（検証→照合→保存）に、既存のログインの照合（72 バイト超えはダミーで不一致）の扱いを当てはめた。
<!-- aidlc-wave-memory:u2-user-preferences:25ab98f0f18066b32517b6ddbead40caa53bec7dba892b09651cde85b02a4f24 -->

- 2026-09-25T16:05:00Z — traceability の upstream_ids に US4.1・US5.1 の AC 22 件と、U2 が受け持つ共通の決まり CR1.1・CR3・CR4・CR5 を並べた; US3.2 の AC（AC3.2.9・AC3.2.11 など）は U3 が主の単位のため入れず、作成の決まり BR5.x は reverse に理由を書いた。
<!-- aidlc-wave-memory:u2-user-preferences:5adbda69fdb836769c3a897e6bd50c02e0734e3797b409c210e20bb694ab6a25 -->

- 2026-09-25T16:05:00Z — 言語の既定（Accept-Language が無い・当たらないときは ja）と、/api/me/ の 401 がアクセスの拒否として監査されないことをコードで確かめて書いた; AcceptLanguageResolver と AdminAuthenticationEntryPoint（管理者のみのパスだけ知らせる）による。
<!-- aidlc-wave-memory:u2-user-preferences:bc5465e1ed9ecef67f00cf336149b9d8878d0e3a3a065b53b57e2eeebc1000c8 -->

- 2026-09-27T00:02:21Z — ベース URL が無いときは WARN を出さず、不正な値のときだけ WARN を出す; Q3 A は「合わなければ WARN を1件」とだけ述べ、値が無いときの扱いは決めていない。U1 の BR1.2（SMTP の項目が1つも無ければ警告しない）にそろえ、招待を使わない使い方を許した（rules.md の BR1.3）。
<!-- aidlc-wave-memory:u3-invitation:e8b2c18716e226471153aaf34a0b90deb2e16257687bf98208f14fabc4e8fe8a -->

- 2026-09-27T00:02:21Z — 招待した管理者の氏名が得られないときのメールアドレスは、U2 の利用者の要約（BR5.5）から得ることにした; 契約 C2 には利用者 ID からメールアドレスを引く操作が無く、U2 では氏名が必須のため、氏名が無いのは利用者の行そのものが無いときだけになる。その場合は空の文字列を返す（今は利用者を消す操作が無く起きない）とした（BR5.3）。
<!-- aidlc-wave-memory:u3-invitation:217c7b0f1adba6c724a95cda2f43ab6a55cde9076c68759e58a669969039b794 -->

- 2026-09-27T00:02:21Z — 招待のメールアドレスに CR・LF があれば、正規化の前の生の入力で拒否する; 既存の EmailAddress.normalize は trim で前後の改行を除くため、AC1.1.3（改行を含むメールアドレスは拒否）を満たすには正規化より先に確かめる必要がある（BR1.1）。
<!-- aidlc-wave-memory:u3-invitation:f0f510b510172d85bd962a1352f9cd7a9069cfc233c3022cd13f158b22f26740 -->

- 2026-09-27T00:02:23Z — traceability の upstream_ids に US1.1・US3.2・US4.1 の受け入れ基準をすべて入れた; 検査の道具は unit-of-work-story-map.md で U4 と結び付いたストーリーの AC をすべて求めるため、44 件を並べた。U4 が関わらないものは N/A、U5〜U7 の画面で確かめるものは Deferred とした。共通の決まりは CR1.1〜CR1.5・CR2・CR6・CR6.6 を足した。
<!-- aidlc-wave-memory:u4-display-foundation:3763f8ba6f574d9ff60852ff122de5588c4b6f65dac44a92c4c6bb6074e38570 -->

- 2026-09-27T00:02:23Z — clearPreview は言語の見せ方もやめる、と読んだ; C9 は clearPreview をテーマ・文字の大きさの見せ方として書いているが、U6 が完了せずに画面を離れたときに招待の言語を残さないため、見せ方をすべてやめる口とした。U7 は言語の見せ方を置かないため影響しない。
<!-- aidlc-wave-memory:u4-display-foundation:8bb695bedf7ff800db483ae1f7ed693720f2e97360606fc25009e966ceb4d183 -->

- 2026-09-27T00:02:23Z — ログインの画面の言語の切り替えのために saveBrowserLanguage を U4 の中の口として足した; 設計の要点 6(d) の「言語だけを書き換え、ほかの2つは保存済みのまま」を saveBrowserDisplaySettings（3つをまとめて保存する）では表せないため。C9 への安全な追加として扱った（resolvedTheme・displayName・LANGUAGE_NAMES も同じ）。
<!-- aidlc-wave-memory:u4-display-foundation:e2730d619d452c325ba2f3d9221930c1e1cb8edb8003ea03466c2f58c4bd073f -->

- 2026-09-27T00:43:17Z — AC2.1.2 を「期限内の行にも期限内と文字で出す」と読んだ; 画面イメージは期限切れの印だけだったが、AC2.1.2 は1秒前の招待が期限内であることも文字で示すことを求めるため。設計の要点 4 と上流との差（要点 19 の c）に書いた。
<!-- aidlc-wave-memory:u5-invitation-ui:3405ed65b763b1b3f196d2f0ebb79c89ce33b2f6a208a61c284201a09f6ce4d3 -->

- 2026-09-27T00:43:17Z — 招待の 400 VALIDATION_FAILED はメールアドレスの項目の誤りとして扱うことにした; 今の GlobalExceptionHandler の VALIDATION_FAILED は項目ごとの誤りを返さず、契約 C5 も形を決めていない。Modal の項目のうち言語は選択肢から選ぶため誤りにならず、項目の形に頼らずに済む。
<!-- aidlc-wave-memory:u5-invitation-ui:0e280a89babbffd6b8abd8dfd2dd52ea1b8406f7ff297dfbcda6e04ad62c0cf4 -->

- 2026-09-27T00:43:17Z — U3 の R-01・R-02 と U1 の R-01 は直した後の形を前提にした; 依頼者の決定で承認の場でまとめて直すため。U3 の今の文書（BR5.3 の氏名が無ければメールアドレス）とは違うことを「決まっていること」と要点 19 の b に書いた。
<!-- aidlc-wave-memory:u5-invitation-ui:3698557d5e1f291370386ea39b49e88008789141156f55bc6a21179e9bafee53 -->

- 2026-09-27T01:05:00Z — Q1 A の「関数を shared へ移す」は formatDateTime だけを移すと読んだ; shortHash・formatBytes は DSL だけが使うため features/dsl/format.ts に残す。shared は app に依存しない今の形を保つため、移した関数の言語の引数は 'ja'・'en' の型を自分で持つ（9節の d）。
<!-- aidlc-wave-memory:u5-invitation-ui:ce375c9adfcc1b4294811dd72d916f29c6450335e502446bc2f90e32f0331d6f -->

- 2026-09-27T01:05:00Z — traceability の対象は US1.1・US2.1・US2.2 の全 AC と CR1.1・CR1.4・CR6・CR6.1〜CR6.9 とした; サーバーだけで確かめる AC（AC1.1.11〜13・AC2.1.3・AC2.2.1・2.2.5・2.2.6・2.2.13）は U4 の書き方に合わせて Deferred ではなく N/A（U3 が OK で持つ）にした。U3 から Deferred で受けた7件はすべて OK。
<!-- aidlc-wave-memory:u5-invitation-ui:d6c2c5bafb8969c17dda01567cfb11949a7396533ccddfaa5b28f82239bd33e2 -->

- 2026-09-27T01:05:00Z — 一覧の 403 は、DSL の画面の「表示できない」ではなく読めなかった表示と一般の 4xx の文言にした; まとめの確認の要点 8・11 の形のままにし、骨組みの access ADMIN で 403 は使っている間に権限を失った場合だけのため（9節の g）。
<!-- aidlc-wave-memory:u5-invitation-ui:30fd7773a2cf061c47952ab9fa1ee66bca0f780f09b2c8aca2f6fe4513410f99 -->

- 2026-09-27T01:05:00Z — 送り直しの一般の失敗（404・503 以外）と取り消しの一般の失敗は読み直さないことにした; 要点 12・13 は一般の失敗の読み直しを書いていないため、状態が変わっていない前提でフォーカスを元のボタンに残す。
<!-- aidlc-wave-memory:u5-invitation-ui:579198aefa690f464a4d38b65177b1d79b3be97bd0cdcf71a2cc949d0b4b9948 -->

- 2026-09-27T00:43:17Z — 質問は判断が分かれる4点（ログインしたまま開いたとき・フラグメントの消し方・サーバーの 400 の出し方・使えないリンクの文と導線）に絞った; 残りは上流（契約 C6・U3・U4 の W8・W9・refined-mockups）で決まっているため設計の要点 20 件と「決まっていること」に書いた。確かめの通信の失敗を「使えない」と見せないこと、選んだ軸だけを見せることはリードの判断として要点に置いた。
<!-- aidlc-wave-memory:u6-registration-ui:2fcac5dcc0ae6e529e026230366cb867c1c803735c6b7a152dd56f01118f67f5 -->

- 2026-09-27T00:43:17Z — 確かめの関数（パスワードの規則・氏名）の置き場を `frontend/src/shared/validation/` とし U7 と共用する案にした; U6 と U7 は同じ B5 で作るため、先に設計する U6 で置き場と形を決め、U7 の機能設計はそれを使う前提とした。
<!-- aidlc-wave-memory:u6-registration-ui:09aa8ea07ca2551c0940b8cf18f6cde6340a40553c88282b2369ecdfcfe39308 -->

- 2026-09-27T01:01:00Z — Q2 B の `history.replaceState` を React Router の置き換えの移動（replace）で行うと読んだ; 直接 `history.replaceState` を呼ぶとルーターの場所の情報にフラグメントが残り食い違うため。履歴の項目を増やさない点は答えと同じで、テストでも MemoryRouter の最初の URL でリンクを与えられる。
<!-- aidlc-wave-memory:u6-registration-ui:204a31bf39fc7fd179603d084f02a26e9af186a7c75ebc633898aef8ef73cf79 -->

- 2026-09-27T01:01:00Z — Q1 A の案内はトークンがあるときだけ出し、トークンが無い・空ならログアウトを求めずに「リンクが使えない」にした; 確かめるものが無いのにセッションを終わらせないため。答えの「リンクを確かめる前に」を、確かめる対象があるときと読んだ。
<!-- aidlc-wave-memory:u6-registration-ui:9e0703629ce26d936bcd1dffd196eaae3d5fca233c7a8108a29897d869a601dd -->

- 2026-09-27T01:01:00Z — 完了の時点の 404 で「リンクが使えない」に移ったときは見せ方（選んだ言語・テーマ・文字の大きさ）をそのまま残し、離れるときに clearPreview で戻すことにした; 移った瞬間に言語が変わると読んでいた人が戸惑うため。
<!-- aidlc-wave-memory:u6-registration-ui:f8bfb85f0e6ffacfa6c80522aaa41c2fa0b38c86428e7a9bb776bcb6bc7244e9 -->

- 2026-09-27T01:01:00Z — traceability の upstream_ids は US3.2 の AC3.2.1〜AC3.2.18 と、単位の対応表で U6 に結び付く CR1.1〜CR1.5・CR6・CR6.1〜CR6.9 の 33 件にした; U3 と U4 が U6 に Deferred にした AC3.2.1・AC3.2.16・AC3.2.17・AC3.2.18 はすべて OK で受けた。サーバーで確かめる AC は、画面の表示とつながるものを u3-invitation への Deferred、つながらないものを N/A とした。
<!-- aidlc-wave-memory:u6-registration-ui:e0b442691d7e7478fecb938a2cd7b6f6e07b9470412b3c723325661795a22a82 -->

- 2026-09-27T00:42:21Z — 質問は3問（ユーザーメニューから画面へ移る手段・画面の検証の範囲・読んだ値と当たっている値の食い違い）に絞った; 画面の構成・見せ方・保存の後の動き・文言・部品は refined-mockups・U2・U4 で決まっているため、設計の要点（16 件）と「決まっていること」に書いた。
<!-- aidlc-wave-memory:u7-preferences-ui:7a7a3f54ee0b813cd184faf9d9096255d0afca1e128a99e70684c0b860934d54 -->

- 2026-09-27T00:42:21Z — U2 の持ち越し R-01〜R-03 は画面の変更を生まないと読んだ; R-02 は U2 が Deferred で回した AC4.1.11・AC4.1.12・AC5.1.7〜AC5.1.9 を U7 の traceability.json で受ける形で扱う。
<!-- aidlc-wave-memory:u7-preferences-ui:d7177e8befba2f1f8d24bf95a82ca9ba01d3ae212d1e454f067a9cca6fd06ed6 -->

- 2026-09-27T01:02:05Z — traceability の Deferred と N/A の分け方を依頼の文どおりにした; ほかの単位（U2・U4）で確かめる AC は Deferred、画面に当たる要素が無い CR6.5・CR6.7・CR6.8 だけを N/A にした。U5 はサーバーだけで確かめる AC を N/A にしており、単位の間で書き方が違う。
<!-- aidlc-wave-memory:u7-preferences-ui:acfda282f50e8ae5aafc5093b223f34cb790fcd73bf38cefe240f949e1d385a9 -->

- 2026-09-27T01:02:05Z — AC4.1.4・AC4.1.8・AC4.1.10・AC5.1.3・AC5.1.6 は U4・U2 でも OK だが、画面の側の確かめがあるため U7 でも OK にした; 対象の文に、サーバーや土台の側の持ち主を併記した。
<!-- aidlc-wave-memory:u7-preferences-ui:4dde850749840c5acbffa7a342e833ade7cfb26588781040eb8bbe32fd27f810 -->

- 2026-09-27T01:02:05Z — 保存の成功の Toast は、applyUserPreferences の後の描画で出す印（pendingSavedNotice）の形にした; make-you-chic-ui の Toast は呼んだ時点の文字列を持つため、新しい言語で出すには言語が切り替わった描画の後に文言を引く必要がある（AC4.1.12）。
<!-- aidlc-wave-memory:u7-preferences-ui:4a16e3758ca02b23beae61bcf39f7cb89bbcad230fbf4ff97471338d8dca2e46 -->

- 2026-09-27T01:02:05Z — 今のパスワードには規則（長さ・バイト数）を当てず、空だけを確かめることにした; 今のパスワードは規則ができる前のものでもよく、照合はサーバー（U2 の BR4.2）が行うため。Q2 A の「サーバーと同じ決まり」は U2 の BR4.1（currentPassword は有ること）と読んだ。
<!-- aidlc-wave-memory:u7-preferences-ui:02a824e5d549b13cb9dfb3a600b08c1adfb6f50c4b8bf3e85ee2e9526890242e -->

- 2026-09-27T00:00:00Z — 承認の場の Request Changes の R-02 で、GET 以外のメソッドの応答を既存の扱い（401・405）に任せると明記した; config/SecurityConfig と access/web/AdminApiDefaultAccess で /api/** がログイン必須、auth/web/TokenAuthenticationEntryPoint が 401 / AUTHENTICATION_REQUIRED、common/error/web/GlobalExceptionHandler が 405 / METHOD_NOT_ALLOWED と Allow を返すことをコードで確かめた。U8 は新しい応答・code を作らず、W3.3・8節・BR3.1・BR3.2 に書き、functional-spec.md の9節に直しの一覧を置いた。
<!-- aidlc-wave-memory:u8-instance-appearance:59161d514afc62eb884047031ebbae6eb05192e2f53ed9add46a0a3e10ded549 -->

- 2026-09-27T02:02:19Z — BR3.3 で空の文字列に加え空白だけの値も拒否すると決めた（R-01）; Q4 の動機は中身の無いリンクのメールを送信済みにしないことで、空白だけの registrationUrl も受け手から見て空と同じため。空白の判定は String.strip と同じ Character.isWhitespace とし、値そのものは削らずにそのまま描く。
<!-- aidlc-wave-memory:u1-mail:a1eb9370c1c54fde9a826738adc72f86bb9d0b5c1a153ef600223f7dcc638cc1 -->

- 2026-09-27T02:02:19Z — validityHours は U1 では他の差し込みと同じ文字列として扱い、正の整数かの確かめは U3 に置いた（U3 の R-02 に伴う変更）; U1 の一覧は名前の集合だけを持ち値の型を持たないため。U1 の守りは空・空白・改行の拒否までになる。
<!-- aidlc-wave-memory:u1-mail:37010544ac234ce80d3e42bdbb17fdb976796fbb6ceff5fbc58fe390a5a44322 -->

- 2026-09-27T02:02:19Z — AC3.1.4 のエスケープの確かめを、置き場のすべてのテンプレートの差し込む値すべて（registrationUrl・validityHours）と、テスト用のテンプレートでの仕組みの確かめの2つにした（R-03）; 招待のテンプレートは利用者の入れた値を差し込まないため。後のテンプレートが利用者の値を差し込めば (1) で自動で対象になる。
<!-- aidlc-wave-memory:u1-mail:298316b19eaaf1cd69a9c87e0d3cfd169551c308e734029d4b5d3fff30a3ec4b -->

- 2026-09-27T00:00:00Z — Request Changes R-02 の分け方に合わせて、依頼に名前の無い AC4.1.8・AC4.1.10 も Deferred にした; どちらも画面の表示（ユーザーメニューの名前、プリファレンスの画面の初期値）で確かめる基準で、u7-preferences-ui の設計が OK で受けているため。U2 はサーバーの部分（BR3.3・BR6.1・BR9.1・BR2.2）を受け持ち、OK から外れた BR は traceability.json の reverse に理由を書いた。
<!-- aidlc-wave-memory:u2-user-preferences:ee4b04091e3ebe6b74500b9f810ba716c50f07bf33a4889055a1b9044649693e -->

- 2026-09-27T00:00:00Z — Request Changes R-01 で、NFR4 の 403 は /api/me/ の3本には当てはまらないと読み、確かめるのは 401 と 200（パスワードの変更は 204）とした; 管理者の権限を要しない API で、CR4 も2つにしているため。要件の文書は書き換えず functional-spec.md 7節の D1 に差を書いた。
<!-- aidlc-wave-memory:u2-user-preferences:f05d92f93bef1d414fe4bb75119bf8d364272b50e0444aa628b6f37c1aed3a00 -->

- 2026-09-27T02:16:21Z — 承認の場の Request Changes で、N/A 30 件をすべて Deferred にした; 依頼者の決定（ほかの単位で確かめるものは Deferred、この単位に全く関わらないものだけ N/A）に沿って、各単位の traceability.json を読んで確かめたところ、30 件はどれも U2・U3・U5・U6・U7 のどれかが OK で持っていた。そのため N/A は 0 件になり、target には確かめる単位の名前と理由を書いた。
<!-- aidlc-wave-memory:u4-display-foundation:5d8ddd43580514631246e89aa20f6017ddabf387c542efe78c40c10bf4b936e9 -->

- 2026-09-27T02:16:21Z — make-you-chic-ui の更新（edb1f94 → 735ef04）で U4 が触れる API は変わっていないと判断した; 差分は Button・Dropdown・Modal・RadioGroup・Table だけで、ThemeProvider・useTheme・design-system-* の鍵・Alert は変わっていない。LoginLanguageSwitch は interaction-spec.md 6節のフォーカスと Enter・Space の決まりに合わせて Button の組のままとし、新しい RadioGroup（選択肢ごとの lang・legend）には切り替えなかった。
<!-- aidlc-wave-memory:u4-display-foundation:61787a12819624393580e3ceb2999d11f3a5535a3c7798b2af0f7ecb74ed3186 -->

- 2026-09-27T02:28:06Z — 承認の場の Request Changes で、網羅の記録の N/A 8 件をすべて Deferred にした; 行き先の単位の traceability.json を読み、AC3.2.8・AC3.2.12〜AC3.2.14 は u3-invitation が OK、CR1.1・CR1.5 は u4-display-foundation が OK、CR6.7・CR6.8 は u5-invitation-ui が OK で受けていたため。この単位に全く関わらないものは残らず、N/A は 0 件になった。
<!-- aidlc-wave-memory:u6-registration-ui:4d2954d74681b71af4c33459498c59882e640e858a215cf6f8be70090e81d5a6 -->

- 2026-09-27T02:28:06Z — 新しい版の Button の loading（aria-disabled でフォーカスを保つ）に合わせ、送信中は文字の入力の欄を disabled ではなく readOnly にし、ラジオは選んでも値を変えない形にした; disabled にするとフォーカスのあった要素が押せなくなった時点でフォーカスが本文へ移り、Button の変更の狙いと食い違うため。フックの側でも submitting の間の送信と選択を無視し、Enter キーでの二重の送信も防ぐ。
<!-- aidlc-wave-memory:u6-registration-ui:24030a1cd18584c94290089cd3c203fb8af10e5ca2a97a1b89fa2618ead48762 -->

- 2026-09-27T02:27:58Z — R-01 は 6.3 の表を正として W8 を合わせた; 表の「一般の失敗は読み直さない」は上の判断（01:05:00Z）どおりで、W8 の 5 だけが古い書き方だった。W8 の 4 を応答の種類ごとの箇条にして表と1対1にした。
<!-- aidlc-wave-memory:u5-invitation-ui:467edb3867506f8bd26a180306abe3eab2f890b66e74b8f237dea80fe6946579 -->

- 2026-09-27T02:27:58Z — 網羅の記録の CR6.9 も Deferred（U6・U7）にした; 依頼者の決定の「ほかの単位で確かめるものは Deferred」に当たり、パスワードの項目の決まりは U6・U7 が確かめるため。N/A は残らない。
<!-- aidlc-wave-memory:u5-invitation-ui:7c61abd457dbc2b91d73d9a00ea80e577362a6244bdf7ae4917f7e42e83c0934 -->
## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->


- 2026-09-25T16:05:00Z — 確認済みの要点に無い決まり BR3.4（保存は自分の列だけを書き換え、相手の列を古い値で上書きしない）を足した; 既存の User は全列を持つエンティティで、プリファレンスの保存とパスワードの変更が同時に起きると、先に読んだ古いハッシュで新しいハッシュを上書きしうるため。要点 6 の「後勝ち」は同じ4列どうしに限ると読んだ。
<!-- aidlc-wave-memory:u2-user-preferences:c520183e733945cd0db9579462369cd963a74827ba61495a99092a43784c3b49 -->

- 2026-09-27T00:02:21Z — 設計の要点 1 の completedAt と「終わった日時」を、1つの endedAt にまとめた; 完了・取り消し・置き換えの時点を1つの属性にまとめると、保存期間（Q1 C）の起点が1つで済む。完了の時点は state が COMPLETED の行の endedAt とし、completedUserId は残した。差は functional-spec.md の6節に書いた。
<!-- aidlc-wave-memory:u3-invitation:54e0ac3bc7c9fa7570516fce3aa7bcc5823d4f152f7f6de45fbc1e503d6fb256 -->

- 2026-09-27T00:02:21Z — 状態 REPLACED と、送信の結果の内部の値 PENDING を足したが、契約の文書は書き換えなかった; Q4 A で C8 に足す EMAIL_ALREADY_REGISTERED と、Q2 C の内部の PENDING（API では FAILED として返す）を、依頼のとおり functional-spec.md の6節「上流との差」に記録した。
<!-- aidlc-wave-memory:u3-invitation:aede9699ac5afc6cb25f374e3a24ac6f9cc948438119b7403371f860c45955e5 -->

- 2026-09-27T00:02:23Z — OK の target を BR ではなく functional-spec.md の流れの番号（W1〜W12）と決まり（D1〜D14）にした; ui の単位は rules.md を作らないため、依頼どおりにした。段の定義と検査の道具は OK の target に rules.md の BRx.y を求めるため、OK の 16 件は invalid_targets、rules.md が無いことも理由として出る見込み。D は BR の番号の形を使わず、検査の道具が孤立した規則として拾わないようにした。
<!-- aidlc-wave-memory:u4-display-foundation:36c308712cb062e03024945d5d9e825db92bdd088b04ef69cd0efcf3ec6c6057 -->

- 2026-09-27T01:05:00Z — 取り消しの確かめの役割を interaction-spec.md の alertdialog ではなく make-you-chic-ui の Modal の dialog にした; Modal は role を dialog に固定し、サブモジュールは変更できないため。DSL の確かめと同じ作りで、CR6.7 の動きは満たす。まとめの確認の要約には無かった差で、9節の e に記録し承認の場で伝える必要がある。
<!-- aidlc-wave-memory:u5-invitation-ui:5cd5db5926c8ac534ccfd102fbbc14ebf4614d9fc39480e5401499e6a152bde3 -->

- 2026-09-27T01:05:00Z — 「一覧でこの招待を見る」を mockups.md のリンクではなく見た目がリンクの button にした; URL へ移らない画面の中の操作のため（9節の f）。
<!-- aidlc-wave-memory:u5-invitation-ui:6582af4cf4847b44c6091ca3da224df53c52c4a37f247a665cb07edbb42de204 -->

- 2026-09-27T01:01:00Z — `design-system-mapping.md` の RadioGroup を使わず、`frontend/src/shared/ui/` に RadioFieldset を作る設計にした; make-you-chic-ui の RadioGroup・Radio は選択肢の名前を文字列だけで受け、選択肢の文字に `lang` 属性を付けられず（CR6.6）、fieldset・legend の名前付けも持たないため（`vendor/make-you-chic-ui` のコードで確かめた）。make-you-chic-ui は変えず、差を functional-spec.md の 10節に記録した。
<!-- aidlc-wave-memory:u6-registration-ui:5b69c71c3652d74ad5cfc0aa038d7f660808fa3e6fc1ba70e1fac822823e43e1 -->

- 2026-09-27T01:01:00Z — 使えないリンクの文を画面イメージ（S2）ではなくストーリーの AC3.2.2 の趣旨にし「ログインの画面へ」のリンクを足した（Q4 B）; 承認済みの画面イメージは書き換えず、functional-spec.md の 10節に差を記録した。
<!-- aidlc-wave-memory:u6-registration-ui:7989ddc79a8846384a2319e35649fd941e35c035441e8fe1becfd2f31391117e -->

- 2026-09-27T01:01:00Z — OK の target を BR ではなく functional-spec.md の流れの番号（W1〜W13）にした; ui の単位は rules.md を作らないため、依頼と U4 の形に合わせた。検査の道具は OK の target に rules.md の BRx.y を求めるため、OK の 21 件は invalid_targets として出る見込み。決まりは D1〜D12 とし、BR の形を使わない。
<!-- aidlc-wave-memory:u6-registration-ui:f7f16afa2b2d9a540e2db74dbfdbbfce060997199232acaaa7f26dfbe99fe0b9 -->

- 2026-09-27T01:02:05Z — 言語・テーマ・文字の大きさの選択を design-system-mapping.md の RadioGroup ではなく共用の RadioFieldset（U6 が作る）で書いた; make-you-chic-ui の RadioGroup は選択肢の名前を文字列でしか受けず lang を付けられず（CR6.6）、fieldset・legend も描かないことをコードで確かめた。U6 の担当からの知らせに合わせ、functional-spec.md の 10節の (b) に差として記録した。
<!-- aidlc-wave-memory:u7-preferences-ui:12bca13c150c661ebae0d3a0e5ab73a9f5274af007c13a8797b347544e0ef8ad -->

- 2026-09-27T01:02:05Z — 骨組み（AppFrame、持ち主は U4）の型・登録の検査・ShellLayout を U7 が変える設計にした; Q1 A の答えどおりだが unit-of-work.md の U7 の境界の外のため、functional-spec.md の 9節と 10節の (a) に明記し、承認の場で確かめる。
<!-- aidlc-wave-memory:u7-preferences-ui:1afb7e3273133351d6237843581791e6795bd336b292b901c85af31b91c2ad18 -->

- 2026-09-27T02:02:19Z — 変更の記録を functional-spec.md の 10節（上流との差）ではなく新しい 11節「承認の場の Request Changes（2026-09-27）による直し」に表でまとめた; 上流との差と、承認の場での直しを分けて読めるようにするため。U1 の R-02 は依頼者の判断で受け入れ、直していないことも同じ表に記録した。
<!-- aidlc-wave-memory:u1-mail:590cc2c58a772441b52208a31372592729bd83c3139204e3ee83d7173cacecf0 -->

- 2026-09-27T00:00:00Z — 契約 C8 の PASSWORD_CHANGED に無い target_user_id を記録する設計を保ち、契約は書き換えずに差（D2）を functional-spec.md 7節に書いた; 契約の持ち主はこの単位だが、承認前の段で契約の文書を直さない依頼（R-03）に従った。C8 への反映は6節で後の段へ渡した。
<!-- aidlc-wave-memory:u2-user-preferences:3a5bdf531d48f869e0d26060ceba119bc5b721771b7c2fe5eaad8d021ad4cfdd -->

- 2026-09-27T02:16:00Z — 承認の場の Request Changes（R-01）で、招待した管理者の表示を findDisplayName の氏名だけにし、メールアドレスへの切り替えを消した; 契約 C2 に利用者 ID からメールアドレスを引く操作が無いため。AC2.1.8 の「氏名が得られないときはメールアドレス」は、U2 で氏名が必須で既存の利用者の初期値がメールアドレスであることから氏名の表示で満たすと記録し、00:02:21Z の解釈（利用者の要約から引く）を置き換えた（rules.md の BR5.3、functional-spec.md の6節）。
<!-- aidlc-wave-memory:u3-invitation:e76d56a4f7625e5942081479bef9f0be887be38d6d571a043d04d29413bb2a45 -->

- 2026-09-27T02:16:00Z — 承認の場の Request Changes（R-02）で、有効期限の長さの時間の数を差し込み validityHours として招待メールに入れ、契約 C10 と設計の要点 23 の固定の文言「24 時間有効です」との差を functional-spec.md の6節に書いた; 契約の文書は書き換えていない。U1 の BR2.2・BR2.4・BR3.3（直し済み）にそろえ、正の整数かの確かめは U3 の BR1.6（時間の単位の正の整数に限り、不正は起動を止める）が持つ。これで 00:02:21Z の未解決の点（長さを変えると文面と食い違う）は解消した。
<!-- aidlc-wave-memory:u3-invitation:f140dae5b142802f7494aa1bbb19e18e6c4b794ba75808ba52b0b727494a616f -->

- 2026-09-27T02:28:06Z — 自前の RadioFieldset をやめ、make-you-chic-ui の新しい版（735ef04）の RadioGroup に legend と選択肢ごとの lang を渡す形に置き換えた; make-you-chic-ui 側に取り込まれたため design-system-mapping.md との差は無くなった。RadioGroup は aria-describedby を渡す口を持たず FormField に入れると label と legend が重なるため、言語の案内は legend の2行目に入れ（選ぶと画面の言語が変わることを選ぶ前に知らせる）、テーマと文字の大きさの案内は結び付けずに文字として置いた。固定先の更新はコード生成の B4 で行う。
<!-- aidlc-wave-memory:u6-registration-ui:35e979a2d82e3cda1da033910760781e552a76a8e60c9fdb0949e3ae8fb79c4f -->

- 2026-09-27T02:28:06Z — U6 R-01 を受け、完了の成功を Toast ではなくログインの画面の Alert（U4 の W9）で知らせる差を functional-spec.md の 10節と traceability.json の CR6.4 に記録した; 完了の直後に置き換えで画面を移るため、この画面の Toast は移動で消えるかログインの画面の案内と二重になる。失敗を role=alert で残す部分は CR6.4 のとおり。
<!-- aidlc-wave-memory:u6-registration-ui:f13c888e343e2aab53e71f3fb34e4bd8bd1c7f28c553544185835a7f19ed00d0 -->

- 2026-09-27T02:27:58Z — 承認の場の Request Changes で、取り消しの確かめを alertdialog に戻し 9節の (e) を消した; make-you-chic-ui の新しい Modal（735ef04）が role と closeOnBackdropClick を持つため。背景の押下の見張りの複写もやめた。
<!-- aidlc-wave-memory:u5-invitation-ui:2c4b497bd359dda4002b41dd8958746d8ce25dec09bca02bb47cab5f161d4599 -->

- 2026-09-27T02:27:58Z — 新しい Table に caption が無いため、表の名前は aria-label、フォーカスの行き先は見える見出し h2 に置き換えた; Table は行の class・包む要素の名前とフォーカスの口も持たないため、目立たせた行は :has() の CSS、横に動かす領域への到達は包む要素の中のボタンで満たす形にした（9節の j）。自前の tabIndex=0 の領域は、横に動く要素そのものではなくなるため置かない。
<!-- aidlc-wave-memory:u5-invitation-ui:c0930d2fe2fb8c6dc0c2c70c50d5790160f3f658856c5ee9aefe429a41fe575d -->
## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->


- 2026-09-25T15:58:57Z — 警告のログに設定された値そのものを出さない形（BR2.1）にした; 秘密ではない値だが、前の Intent の対象DB の設定の警告と揃え、項目の名前・既定・許される値だけで原因が分かる形を選んだ。代わりに、どの値を書いたかは設定を見て確かめる必要がある。
<!-- aidlc-wave-memory:u8-instance-appearance:d0ef79e4dbbdc9f28fb404ddbce8d88bc2eccf5c3e873b8772f5551f43d1ad7d -->

- 2026-09-25T15:58:57Z — Q1 A の決まり（使えないトークンの 401 を受け入れる）は FR8 の受け入れの条件ではないため、BR3.3 として書き traceability の reverse に N/A で理由を付けた; 画面の側（U4）の ApiClient の変更は U4 の機能設計で受け持つ。
<!-- aidlc-wave-memory:u8-instance-appearance:476437227aeec03d2c87c7772eaa4a736c22c81bd7adfd55b4408614da9c7372 -->

- 2026-09-25T15:58:49Z — テンプレートの中の差し込みの名前と一覧の一致は、起動時ではなくテストで確かめる形にした（Q4 A の答えどおり）; 起動の処理を小さく保てる代わりに、テストを通さずに動かすと食い違いが送信の時まで見つからない（その場合も送信の依頼の名前の確かめで INVALID_INPUT になり、リンクの無いメールは送られない）。
<!-- aidlc-wave-memory:u1-mail:49e645156957c10da042a687cd2eb18f5b422fce0518b8af20a2d691c456e24e -->

- 2026-09-25T15:58:49Z — 本文を送った後の応答の待ちの時間切れも TIMEOUT とし、自動の再試行はしない; 受け手に届いている場合があり、管理者の送り直しで同じ招待のメールが重なって届くことはありうる。重複を防ぐ仕組みは入れていない（functional-spec.md 8節）。
<!-- aidlc-wave-memory:u1-mail:2ff3b6eb2578d4ed36379e4f65e538511835286963ed67b67c62b60283444209 -->

- 2026-09-25T16:05:00Z — Q2 B で Cf を拒否するため、ゼロ幅の接合子（U+200D）を使う絵文字の組み合わせ（家族の絵文字など）と異体字の選択子の一部を含む氏名も拒否される; 表示の向きの上書きを防ぐ代わりに受け入れた。異体字の選択子（U+FE0F）は Mn で Cf ではないため通る。
<!-- aidlc-wave-memory:u2-user-preferences:f8a03e2c303efecbb2028dbfae17a74936d14fb32c688c4a310240066a0b591e -->

- 2026-09-25T16:05:00Z — 利用者の作成（BR5.1）は決まりに合わない値を結果の型ではなく想定外の誤りにした; 入力の誤りは呼び出し元（U3）が同じ関数で先に返す前提で、作成の結果の型は C2 のとおり Created・EmailAlreadyUsed の2つに保つ。
<!-- aidlc-wave-memory:u2-user-preferences:123170732ee295c86f89cdf4e053594daa829830d0939ce4a94623df4ef1b467 -->

- 2026-09-27T00:02:21Z — 終わった招待の行では tokenHash を消さずに残す; 使用済み・取り消し済み・置き換え済みのリンクの監査の理由を分けられる代わりに、保存の日数（既定 90 日）のあいだハッシュが内部DB に残る。ハッシュからトークンは戻せず、行は定期の削除で消える（BR7.6・BR11.1）。
<!-- aidlc-wave-memory:u3-invitation:9bcfad9c0141d9c059405bcaf277e6229e9c6798e72f89e7f79d8f205b49fa63 -->

- 2026-09-27T00:02:21Z — AC2.2.12・AC3.2.1・AC3.2.18 は、サーバー側が支える部分もあるが Deferred（画面の単位）にした; どの AC も、確かめる中心が Toast・画面の表示・ブラウザへの保存であるため。サーバー側の支えは、対象の BR を traceability.json の理由の文に書いた。
<!-- aidlc-wave-memory:u3-invitation:da9f055cd9482615d09af673dbc756f5ff39c8f11de31daf891bb44c3d2b88bb -->

- 2026-09-27T00:02:23Z — 画面の値は描画の中で純粋な関数で決め、保存の後の値と見せ方はログイン状態に結び付けて持つ形にした; ログイン状態が変わった描画でそのまま文言が切り替わり、前の利用者の値が途中で出ない（AC4.1.5）。その代わりに状態の持ち方がやや込み入る。make-you-chic-ui の属性の反映が描画の後になる場合に備え、U4 が同じ時点で html の属性にも置く逃げ道を書き、どちらにするかはコード生成で確かめる。
<!-- aidlc-wave-memory:u4-display-foundation:34e225445265ebc7b218d53045a30299232dca5dd425865bba51213d29f90267 -->

- 2026-09-27T00:02:23Z — U4 が make-you-chic-ui に値を渡すのは、そのタブの画面の値が変わったときだけにした; ほかのタブの見た目の変化を打ち消すと、Q3 A の「ほかのタブへの映り方は make-you-chic-ui の今の動きのまま受け入れる」に反するため。
<!-- aidlc-wave-memory:u4-display-foundation:5120c751b22eb03a150ffca32f6ef0966b7e4baf6819476658df04aaa74942bb -->

- 2026-09-27T00:43:17Z — 質問を3問（日時の書式と置き場・今のページの持ち方・開いたままの間の期限切れ）に絞り、ほかは設計の要点に置いた; 応答ごとの画面の動き・警告の文言・フォーカスの置き場は、契約 C5・画面イメージ・CR6・DSL の画面の前例から導けるため。要点が 19 件と多くなり、依頼者は要点の表を読んで確かめる必要がある。
<!-- aidlc-wave-memory:u5-invitation-ui:2f98cdc388f7496de9f82f31a150acafa4a926e3ad0f446a087e9607ead79bf7 -->

- 2026-09-27T01:05:00Z — 背景のクリックで閉じない仕組み（DSL の DslConfirmDialog の押下の見張り）と一般の失敗の文言を features/invitation に複写した; 機能どうしの読み込みを作らないため。同じ仕組みが2か所になり、3つ目の機能で要るなら shared へ移すことを考える。
<!-- aidlc-wave-memory:u5-invitation-ui:f28049da034ba782d36b4205c538c237636d1ba7da52381b527feb21ffd9f274 -->

- 2026-09-27T01:05:00Z — 「取り消す」の英語を Cancel ではなく Revoke にした; DSL と同じく「やめる」を Cancel とするため、確かめの Modal で2つの Cancel が並ぶのを避けた。
<!-- aidlc-wave-memory:u5-invitation-ui:cc12ca297552e98438e254e2df9eb24f713bfd4aa525ee62cf954f89cdf55f84 -->

- 2026-09-27T01:01:00Z — 氏名の前後の空白の除去を JavaScript の標準の除去ではなく Unicode の White_Space の正規表現で行う形にした; 標準の除去は U+FEFF を除き U+0085 を除かず、U2 の BR1.1 と集合が違うため。画面とサーバーの判定がそろう代わりに、関数を自前で持ち性質ベースのテストで確かめる必要がある。
<!-- aidlc-wave-memory:u6-registration-ui:99b5e85fbec1c64338051571578a5b41fa7c383c85610e88ac23912c17159a07 -->

- 2026-09-27T01:01:00Z — サーバーの 400 のときはフォームの上の知らせにフォーカスを移し、通信の失敗・5xx のときは移さないことにした; 400 は入力を直す必要があり知らせから読み始めるほうがよく、送れない失敗はそのまま送り直せばよいため。
<!-- aidlc-wave-memory:u6-registration-ui:65cef6c2e045d1a0deaf8b14827bfd5fb978051c3c79611f38a88a87584eefad -->

- 2026-09-27T01:02:05Z — 保存の送信中に画面を離れた後の成功の応答でも applyUserPreferences を呼ぶことにした; 内部DB は保存されたため画面の値をそろえる側を選んだ。代わりに、離れた先の画面で見た目や言語が急に変わることがある（10節の (h)、承認の場で確かめる）。
<!-- aidlc-wave-memory:u7-preferences-ui:1d0934e4e4a315b1d4ffa07c161d8d6e374c0a11db0d6bd561fafa025bc7a08e -->

- 2026-09-27T01:02:05Z — 開いた時点のそろえ（Q3 A）で言語やテーマが変わっても知らせないことにした; 利用者の操作ではなく内部DB の値に合わせるだけのためだが、利用者には理由の分からない切り替わりに見えうる（10節の (i)）。
<!-- aidlc-wave-memory:u7-preferences-ui:50865b7a1c9699309453e263e4fc908512402876b570ba799265c574e8df6715 -->

- 2026-09-27T01:02:05Z — 送信の間は主な操作のボタンに加えて入力と「元に戻す」も変えられなくした; 送信中の選択が成功の応答で上書きされて失われるのを防ぐ代わりに、送信の間は選び直せない。
<!-- aidlc-wave-memory:u7-preferences-ui:d07572f3e7ae732715292494c57cc7ee5ca3582b73a72803b7c2337e0af9b0c5 -->

- 2026-09-27T02:16:00Z — 有効期限の長さを時間の単位（1 時間で割り切れる長さ）に限った; 差し込む値を正の整数の時間の数にするため、90 分などの割り切れない長さは起動を止める。細かい長さを選べなくなる代わりに、本文の時間の数と実際の有効期限が必ず一致する。en の文は「1 hours」になりうるが、既定は 24 で、文の形はコード生成でテンプレートを書くときに整える。
<!-- aidlc-wave-memory:u3-invitation:67816f2fc168ce0a3b9018554aae4d9142da882f6e0c9847d379777bb44e1ce9 -->

- 2026-09-27T02:16:21Z — 登録の完了の API（/api/registration/verify・/api/registration/complete）を ApiClient の公開の API のパスに足した（U4 R-01）; 既存のセキュリティの決まりは公開のパスでも付いたトークンを検証するため、ログインしたままのタブで開くと契約 C6 に無い 401 が起きうる。前の版で U6 に任せるとしていた未解決の点を、U4 が持つ一覧の問題としてこの単位で閉じた。判定はパスの完全一致のままとし、一覧の名前（AUTH_API_PATHS）の改め方はコード生成で決める。
<!-- aidlc-wave-memory:u4-display-foundation:99bbdaf19eaa4f1fc95f1c6cfea586fc75637bd3d840c6c089a23d771336737f -->

- 2026-09-27T02:16:21Z — 見た目の設定のハングの影響範囲を W2 の5と 7節に明記した（U4 R-02）; 待ちに上限を置かない Q2 A は変えず、1つの API の障害が全画面の最初の描画を止めうること、依頼者が受け入れたこと、今のセッションの復元と同じ待ち方であることを記録した。
<!-- aidlc-wave-memory:u4-display-foundation:6deb2934eddf4e00787d8f126fdc104cbb57806e991e61276bdd078c7e72e41f -->

- 2026-09-27T02:16:21Z — 既存の @fontsource/noto-sans-jp（OFL-1.1、5.3.0、推移依存なし）の採用の理由を functional-spec.md 9.3 に後から記録した; 前の Intent の最初の Bolt で足されたが記録が無く、team.md のライセンスの決まりに当たるため。採用そのものは変えていない。
<!-- aidlc-wave-memory:u4-display-foundation:aee8c2ca8eeca22c83ecfca7068141ceef0416a57c6860cc679844d30770a9fb -->

- 2026-09-27T02:27:58Z — 読み直しの間も Table を描いたまま data を空にし、ページ送りのボタンのフォーカスを保つ形にした; Table を外すと押したボタンが消えてフォーカスが失われるため。代わりに読み込み中もページ送りのボタンが押せ、重なりは D2 の最後の答えだけを使う形で受ける。Table のページ送りは 20 件以下でも出る。
<!-- aidlc-wave-memory:u5-invitation-ui:790e87314b90d6cbeaba99485063ab6ce3df989a6fea882fc8cd8d93c9ed2112 -->
## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->

- 2026-09-25T15:58:49Z — 差し込む値の空の文字列は拒否していない（BR3.3 は null だけを拒否する）; 空の registrationUrl を防ぐのは U3 の組み立ての決まりに任せた。U1 の側でも空を拒否するかは、承認の場で必要なら確かめる。
<!-- aidlc-wave-memory:u1-mail:0436daff9ec2b85abe2a31e0a4e2906e487fb0a5b4d1ef5b7e2298231da463bc -->

- 2026-09-25T16:05:00Z — 対になっていないサロゲート（Cs）を含む氏名の扱いを決めていない; Q2 B は Cc・Cf だけを拒否とし、ほかは許すとしたため BR に入れなかった。JSON の読み取りで弾かれるかをコード生成で確かめ、通るなら依頼者に確かめる。
<!-- aidlc-wave-memory:u2-user-preferences:7930c2f441460088eeba30b8f833f8b2ff9ba1e3dcacb6d95711a13d679165de -->

- 2026-09-25T16:05:00Z — 今のパスワードの誤りが続いたときの制限は持たない（BR4.5）; NFR 要件の段で決め、入れるときは BR4.5 を広げる。
<!-- aidlc-wave-memory:u2-user-preferences:4fc7341e87a27de32cd2f6b3123b88dac370c6390379e9687e8259380072b2fd -->

- 2026-09-27T00:02:21Z — 有効期限の長さの設定を 24 時間から変えると、テンプレートの固定の文「24 時間有効です」と食い違う; 今は BR1.6 で「長さを変えたらテンプレートも直す」としたが、長さを差し込む値にするかは U1 のテンプレートの一覧の差し込みの名前（registrationUrl だけ）に関わるため、この段では決めなかった。
<!-- aidlc-wave-memory:u3-invitation:2efdc6484af0ccacf5d0aecb17d5f2b7d7ad6672628ad1b808cf598861236b75 -->

- 2026-09-27T00:02:23Z — ユーザーメニューの項目の登録（UserMenuItemRegistration.action）は引数の無い関数で、画面の移動の手段を持たない; U7 が「プリファレンス」「パスワードの変更」の画面へ移るには、登録に移動先を持たせる変更が要るかもしれない。U4 は登録の仕組みを変えない（設計の要点 14）ため、U7 の機能設計か承認の場で確かめる。
<!-- aidlc-wave-memory:u4-display-foundation:cb9859ef3ec6c410df58529f8c6390a2a9066b3f05d85fb8680167ba7880e99b -->

- 2026-09-27T00:02:23Z — ログインした人が登録の完了のリンクを開くと、/api/registration/ の要求にトークンが付く; 期限切れのトークンなら 401 のあとの更新で通る見込みだが、ApiClient の公開の API のパスに /api/registration/ を足すかは U4 の範囲の外として決めていない。U6 の機能設計で確かめる。
<!-- aidlc-wave-memory:u4-display-foundation:cbf4918933efcac18edbb1c4f2b27a5a3a29744f8daf1a062ecd0c7245efc676 -->

- 2026-09-27T00:02:23Z — 見た目の設定の待ちに上限を置かない（Q2 A）ため、応答が返らないと画面が出ないまま残る; 今のセッションの復元と同じ待ち方で、受け入れた決定として記録した。
<!-- aidlc-wave-memory:u4-display-foundation:c884671b594ab27dbad54f5ede38483c1c64db739d703c59172d862296f5ad89 -->

- 2026-09-27T00:02:23Z — 既に使っている @fontsource/noto-sans-jp（OFL-1.1）の採用の理由の記録は、intents の文書には見当たらなかった; Noto Serif JP の記録（functional-spec.md 9.2）で同じライセンスと書いたが、sans の記録を足すかは依頼者に確かめる。
<!-- aidlc-wave-memory:u4-display-foundation:31bb10a977a99d2192702438b30cee43d907de77e8ade0e7146a6d2cc004f7a8 -->

- 2026-09-27T00:43:17Z — 使えないリンクの文がストーリーの AC3.2.2 と画面イメージの S2 で食い違う; AC3.2.2 は「いちばん新しい招待メールのリンクを使うか」を含み、画面イメージは送り直しの依頼だけ。確定済みの成果物の食い違いのため Q4 で依頼者に確かめる（`project.md` の Change Control の決まり）。
<!-- aidlc-wave-memory:u6-registration-ui:abe864d393461656d7db543b1d25f9aaf4ae048c44ca58f2971b0ccfb69449f4 -->

- 2026-09-27T00:43:17Z — 既存の VALIDATION_FAILED は誤りの項目の一覧を持たず、契約 C6 も形を決めていない; U3 の BR4.1 の「項目ごと」と応答の形がつながっていないため、画面の扱いを Q3 で確かめる。B を選ぶと U3 の承認済みの設計と共通の変換への追加が要る。
<!-- aidlc-wave-memory:u6-registration-ui:05bc06bf40a0b7dfa93befd2748bdb052c6a3ec4f16880984ae9e73423ff7107 -->

- 2026-09-27T01:01:00Z — 確かめ・完了の API にトークンを付けないことは U4 の R-01 の直し（U4 の承認の場で行う予定）に頼る; この単位では ApiClient を変えないため、U4 の直しが入らないとログインしたままの経路以外でも期限切れのトークンが付きうる。U6 はログアウトの後に確かめる（W3）ため通常は通らないが、U4 の承認の結果を確かめる。
<!-- aidlc-wave-memory:u6-registration-ui:4cc941e44e10ba9bc711db3d28684c6170e01aeb51f78faa236436b0f2b59233 -->

- 2026-09-27T01:01:00Z — `frontend/src/shared/ui/` の RadioFieldset を U7 も使う前提にした; U7 の機能設計で同じ部品を使うかを確かめる（プリファレンスの言語の選択肢にも lang 属性が要る）。
<!-- aidlc-wave-memory:u6-registration-ui:c5d4343d6cc3108476cec2acdb1b387520123dbf925aae97a9c4d298e50d6e0e -->

- 2026-09-27T00:42:21Z — VALIDATION_FAILED の項目ごとの誤りの形がまだ決まっていない; U2 は項目の名前と理由を付けると決めたが、契約 C4 に形が無く、今の GlobalExceptionHandler も返していない。U7 はその形に依存するため、U2 のコード生成で決まる形を U7 のコード生成の前に確かめる。
<!-- aidlc-wave-memory:u7-preferences-ui:795274f0879177709dcd370cab7e72e13ecf17d53495b698b8d1825c9f1a0926 -->

- 2026-09-27T00:42:21Z — make-you-chic-ui の Button は loading のあいだ disabled になり、フォーカスが外れうる; AC4.1.12（保存の後もフォーカスは保存のボタン）と CR6.3 を両立させるため、送信の後にフォーカスを戻す要点にした。実際に外れるかはコード生成で確かめる。
<!-- aidlc-wave-memory:u7-preferences-ui:907738528c16902a27154ac22d1ee82a294180ee345917f5d93b6f19163c000f -->

- 2026-09-27T00:42:21Z — パスワードの規則の画面の関数を U6 と共通の置き場（frontend/src/shared/ の下）に置く案にした; U6 の機能設計はまだ無いため、U6 の設計と置き場を突き合わせる。
<!-- aidlc-wave-memory:u7-preferences-ui:7bcc660980a573aee65a7ecc06dba43cccd9b959c72f48b45385de0653bc853e -->

- 2026-09-27T01:02:05Z — 共用の確かめの関数と RadioFieldset の名前・引数・理由の値は U6 の成果物で決まる; U7 の文書は理由の名前を仮に書いたため、U7 のコード生成で U6 の形に合わせる（functional-spec.md の 10節の (d)）。
<!-- aidlc-wave-memory:u7-preferences-ui:e8736c3b67ccd0aa639729e6209a4a209dbbf2ab3ea09379f190bb467f054053 -->

- 2026-09-27T01:02:05Z — 見せ方の最中にトークンの更新の応答が来ると U4 が見せ方を捨て、フォームと画面の見た目が食い違う; 次の選択・元に戻す・保存で解けるためこの単位では追わず、コード生成で実際に起きるかを確かめる（10節の (f)）。
<!-- aidlc-wave-memory:u7-preferences-ui:8d85e80b24e341b2dbbbd15190aeb8df2129cbb00410ce7dea766eec71302461 -->

- 2026-09-27T02:28:06Z — U7 も RadioGroup に置き換えるため、案内の置き方（legend の中か、結び付けない文字か）を U6 とそろえるかを承認の場で確かめる; U7 の初めの版はテーマと文字の大きさの案内を aria-describedby で結び付ける形で、新しい RadioGroup にはその口が無い。Button の loading の変更が既存の画面のテスト（disabled を確かめるもの）に及ぶかは、固定先を更新する B4 で確かめる。
<!-- aidlc-wave-memory:u6-registration-ui:999d0ddcfd1d64038556072658aed55f709c1e9a1d00a366e12a5dcd8ca4e2d2 -->

- 2026-09-27T02:29:49Z — 承認の場の Request Changes で、traceability.json の来歴を U2 の直しの後の事実に合わせた; U2 は画面の表示で確かめる AC4.1.1・AC4.1.8・AC4.1.10 を U7 へ Deferred にし、U4 は AC4.1.1・AC4.1.10 を回している。AC4.1.8・AC4.1.10 にも受けた来歴を書き、10節の (e) の一覧も直した（R-01）。
<!-- aidlc-wave-memory:u7-preferences-ui:12fdf28d6bc6713010d94018aa52a54a1bbd262cf69aa1e643b653301e3dde41 -->

- 2026-09-27T02:29:49Z — U6 の共用の確かめの関数への依存を 10節の (d2) に明記した; 確定済みの unit-of-work-dependency.md（U7 は U2・U4 に依存）には無く書き換えないため。RadioFieldset をやめたので U6 への依存はこの関数だけで、同じ B5 で U6 を先に作る（R-02）。
<!-- aidlc-wave-memory:u7-preferences-ui:46b55db332f729d98a7dce539fd7b263f98eb919075b19f705e98736eb314dad -->

- 2026-09-27T02:29:49Z — 選択の部品を make-you-chic-ui の新しい版（735ef04）の RadioGroup に置き換え、案内の置き方を U6 にそろえた; RadioGroup は aria-describedby を外から受けないため、言語の案内は legend の中の2行目、テーマと文字の大きさの案内は結び付けない文字にした。interaction-spec.md の 7節との差は 10節の (b) に書き、固定先の更新はコード生成の B4 で行う。
<!-- aidlc-wave-memory:u7-preferences-ui:abfbcf1d0c8d2942f695ae681cf8e3a3713b80e8ba1f2fbf99b2257c90c5bb07 -->

- 2026-09-27T02:29:49Z — ユーザーメニューの移動を Dropdown の MenuItem の href と onClick（preventDefault して navigate）にした; path を骨組みに足すこと（Q1 A）は残し、ShellLayout が path から href と onClick を作る。サイドバーの項目と同じ作りで、リンクとして読み上げられる。
<!-- aidlc-wave-memory:u7-preferences-ui:0390f3afbee30f33fe290bbadac2e37318adcadb5715de7d51b1817335ce05ba -->

- 2026-09-27T02:29:49Z — 送信の後にフォーカスを戻す作りをやめ、Button の loading に任せた; 新しい版の Button は aria-disabled でフォーカスを保つため。文字の項目は readOnly、ラジオは disabled にせずフックが選択を受け付けない形（U6 と同じ）にし、項目の誤りのときのフォーカスの移動だけを残した。
<!-- aidlc-wave-memory:u7-preferences-ui:da51a5c1ab9f35a55d34decce05a7357dacd9260b85b9784a5e03bcc3e353e2c -->

- 2026-09-27T02:29:49Z — 網羅の記録の Deferred 10・N/A 3 を依頼者の決まりに照らし、形を変えずに残した; Deferred はストーリー US4.1・US5.1 の受け入れ基準でほかの単位が確かめるもの、N/A の CR6.5・CR6.7・CR6.8 は U7 の画面に当たる状態・操作・表示が無いものと判断した（U5 の CR6.9・U6 の CR6.7・CR6.8 の N/A と同じ扱い）。target に「この単位に全く関わらない」と理由を書き直した。
<!-- aidlc-wave-memory:u7-preferences-ui:1ae96309544a6f0c03c231cb0987b474dbc2bd5571801a453db51ccff8dcb33e -->
