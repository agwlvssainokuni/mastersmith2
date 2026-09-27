# Functional Design の質問 — u3-invitation（招待と登録の完了、service）

単位の定義は `aidlc/spaces/default/intents/260925-user-management/inception/units-generation/unit-of-work.md`（U3）、主に受け持つストーリーは US1.1・US2.1・US2.2・US3.2（`aidlc/spaces/default/intents/260925-user-management/inception/user-stories/stories.md`）、契約は C1・C2・C5・C6・C8・C10（`aidlc/spaces/default/intents/260925-user-management/inception/contract-design/contract-summary.md`）です。依存する単位の機能設計は `aidlc/spaces/default/intents/260925-user-management/construction/u1-mail/functional-design/` と `aidlc/spaces/default/intents/260925-user-management/construction/u2-user-preferences/functional-design/` を読みました。

## 設計の要点（案）

上流ですでに決まっていることと既存のコードから導ける、この単位の機能設計の見通しです。質問の答えで変わる点は（→ Qn）と書きます。

### エンティティ

1. U3 が持つエンティティは **Invitation**（招待の表、新しいパッケージ `invitation`）の1つだけとする。属性は部品の一覧どおり invitationId・email・language・tokenHash・invitedByUserId・invitedAt・expiresAt・sendResult・state に、登録の完了の記録（completedAt・completedUserId）と終わった日時（取り消し・置き換えの時点）を足す。ベース URL・SMTP の設定・有効期限の長さは設定の値としてエンティティにせず、決まり（BR）として書く（`project.md` の学び）。
2. **状態**は PENDING（招待中）・COMPLETED（登録を完了）・CANCELLED（取り消し）・REPLACED（期限切れのまま同じメールアドレスに新しく招待されて置き換わった、AC2.2.6）の4つとする。**期限切れは状態として保存せず**、PENDING のうち「時計の今 ≧ expiresAt」のものとして、読むたびに注入した時計（既存の `Clock` の Bean、`AuthClockConfig`）で決める。有効は「今 < expiresAt」だけ（時刻ちょうどは無効、`stories.md` の合否の判定の仕方）。
3. 移り変わりは PENDING → COMPLETED（登録の完了）・PENDING → CANCELLED（取り消し。期限切れでも可、AC2.2.10）・PENDING（期限切れ）→ REPLACED（同じメールアドレスへの新しい招待、AC2.2.6）だけとし、終わった状態からは動かない。送り直しは状態を変えず、同じ行の tokenHash・expiresAt・sendResult を新しい値に置き換える（前のトークンはその時点で見つからなくなる、AC2.2.1・AC2.2.8）。invitedAt と invitedByUserId は送り直しでも変えない（AC2.2.12 で新しい値になるのは有効期限と送信の結果だけのため。一覧の並びも変わらない）。
4. 同じメールアドレスの PENDING（期限切れを含む）は常に1件までとする。新しく招待するとき、期限内の PENDING があれば INVITATION_ALREADY_PENDING で拒否し（FR1.4、AC1.1.13）、期限切れの PENDING があれば同じトランザクションでそれを REPLACED にしてから新しい招待を作る（AC2.2.6）。取り消し・完了の後の再招待は許す（AC2.2.5、要件の前提 A4）。1件に限る仕組み（H2 の索引か行ロックか）は NFR 設計で決める（ADR-010）。

### トークンと URL

5. トークンは既存のリフレッシュトークン（`backend/src/main/java/cherry/mastersmith/auth/domain/RefreshTokenValues.java`）と同じ作り方にする: 暗号学的な乱数 32 バイト（256 ビット、NFR1 の 128 ビット以上を満たす）を URL で使える Base64（埋め草なし、43 文字）にし、内部DB には SHA-256 のハッシュだけを一意の値として保存する（`project.md` の Mandated）。確かめのときは、形（長さと使える文字）が合わない値をハッシュを引かずに「見つからない」とする。
6. 招待のリンクは「ベース URL（末尾の `/` を除く）＋ `/register#token=` ＋ トークン」で、ベース URL の設定の値だけから組み立てる（契約 C6・C10、FR1.7）。既存の `ProblemBaseUrlResolver` は設定が無いと要求の Host から組み立てるため（K-9）、招待の URL には使わず、U3 は設定の値を直接読む。ベース URL の値の確かめ方は（→ Q3）。
7. U1 は差し込む値の null だけを拒否し、空の文字列は通す（U1 の BR3.3）。そのため U3 は、招待を使える設定かを招待・送り直しの最初に確かめ（要点 13）、上の組み立てで常に空でない registrationUrl を作ってから送信を頼む。空・`#token=` の欠けた URL で送信を頼まないことを U3 の決まりとし、テスト用の受け手で受けたメールの本文にリンクがあることで確かめる（AC3.1.2）。

### 招待・送り直し・取り消し・一覧（C5）

8. **招待の流れ**: (1) 入力の検証（メールアドレスは既存の決まりで正規化・254 文字まで・CR・LF を拒否、言語は ja・en、FR1.2・AC1.1.3）→ (2) 招待を使える設定か（要点 13。だめなら 503、AC1.1.6・AC1.1.11）→ (3) 短いトランザクションで、登録済みの確かめ（C2 の existsByEmail、409 INVITATION_EMAIL_REGISTERED）・招待中の確かめ（409 INVITATION_ALREADY_PENDING に invitationId と page）・期限切れの置き換え・トークンの発行・招待の保存を行い確定する → (4) 確定の後に INVITATION_ISSUED を知らせる → (5) トランザクションの外で U1 の send を1回呼ぶ（C1、ADR-009）→ (6) 別の短いトランザクションで sendResult を記録する → (7) 201 で招待を返す（送信の失敗でもエラーにしない、契約の Q2: A）。確定の時点の sendResult と、途中で止まったときの扱いは（→ Q2）。
9. **送り直し**: 招待を使える設定かを最初に確かめ（だめなら 503 で、トークン・有効期限は変えない、AC2.2.9）、PENDING（期限切れを含む）の行だけを対象に新しいトークンと「今＋24 時間」の有効期限を確定し、INVITATION_RESENT を知らせてから送る。PENDING でない・無い ID は 404 INVITATION_NOT_FOUND（AC2.2.7）。送信の結果は、記録の時点でまだ同じトークン（送ったトークン）の行にだけ書く（同時にもう一度送り直された・取り消された招待の結果を、古い送信の結果で上書きしない）。
10. **取り消し**: 設定が無くても行える（AC2.2.9）。PENDING（期限切れを含む）だけを CANCELLED にし、INVITATION_CANCELLED を知らせて 204。それ以外は 404 INVITATION_NOT_FOUND。
11. **一覧**: PENDING（期限切れを含む）だけを、invitedAt の新しい順（同じ時刻は invitationId の大きい順）に 20 件ずつ返す（要件の前提 A1、AC2.1.3・AC2.1.5）。expired は時計で決める。招待した管理者は C2 で氏名を引き、無ければその管理者のメールアドレスを出す（M9: A、AC2.1.8）。ページが最後を超えたら items を空にして total を返す。応答にトークン・トークンのハッシュ・URL を含めない（AC2.1.7）。invitationEnabled と unavailableReasons（BASE_URL_NOT_CONFIGURED・SMTP_NOT_CONFIGURED）を付ける。
12. **招待中の案内の page**: 409 INVITATION_ALREADY_PENDING の page は、一覧と同じ並びでのその招待の位置から「位置 ÷ 20 の切り上げ」で求める（AC1.1.4）。
13. **招待を使える設定か**: ベース URL が使える値（→ Q3）で、かつ U1 の isConfigured が真のときだけ使える。足りないものごとに BASE_URL_NOT_CONFIGURED・SMTP_NOT_CONFIGURED を並べる（FR1.8、契約 C5）。設定の値そのものは返さない。影響を受けるのは招待と送り直しだけで、一覧・取り消し・リンクの確かめ・登録の完了は設定が無くても動く。

### 登録の完了（C6）

14. **リンクの確かめ**（`POST /api/registration/verify`）: トークンで PENDING かつ期限内の招待を探し、あれば email と language を返す。完了の時点で拒否される状態（その email の利用者がすでにいる）もここで同じく拒否する（開いたのにフォームを入れてから拒否されるのを避けるため）。拒否は理由によらず 404 REGISTRATION_LINK_INVALID（NFR3、AC3.2.2）。招待を消費せず、監査しない（契約 C6、CR3）。
15. **登録の完了**（`POST /api/registration/complete`）: (1) 入力の検証を DB を使わずに先に行う（氏名は U2 の DisplayName の関数（U2 の BR1.6）、言語・テーマ・文字の大きさは U2 の BR2.1、パスワードは既存の PasswordPolicy、2回の入力の一致もサーバーで確かめる。誤りは 400 VALIDATION_FAILED で招待を消費しない、AC3.2.4・AC3.2.9・AC3.2.10）→ (2) 1つのトランザクションで招待の行を排他して読み、PENDING かつ期限内かを確かめる → (3) U2 の createUser（admin false）を同じトランザクションで呼ぶ（C2、U2 の BR5.3）→ (4) EmailAlreadyUsed なら巻き戻して 404 REGISTRATION_LINK_INVALID（AC3.2.11）→ (5) 招待を COMPLETED にし completedUserId を記録して確定 → (6) 確定の後に REGISTRATION_COMPLETED を知らせて 204。自動ではログインしない（FR4.7）。同時の2回の完了・取り消しとの競合では、行の排他で片方だけが通る（AC3.2.7・AC2.2.13。排他の作りの細部は NFR 設計）。
16. **リンクの拒否の理由**（監査の failure_reason だけに使い、応答は常に同じ）: トークンの形が合わない・ハッシュが無い（送り直しで古くなったトークン・改ざんを含む）→ INVITATION_NOT_FOUND、COMPLETED → INVITATION_ALREADY_USED、CANCELLED → INVITATION_CANCELLED、REPLACED と期限切れの PENDING → INVITATION_EXPIRED（契約 C8）。完了の時点で同じメールアドレスの利用者がいたときの理由は（→ Q4）。
17. 招待中の人は利用者の表にいないため、ログイン・トークンの更新・アクセストークンの認証の3経路は構造で拒否される（ADR-001、AC3.2.8）。既存のアクセストークンの主体は利用者 ID（数値）で、招待の ID も数値のため、AC3.2.14 のテストは「どの利用者の ID とも重ならない招待の ID・招待のメールアドレスを主体にしたトークン」で 401 を確かめる（招待の ID を主体にしたトークンを発行する経路は無い）。

### 監査（C8）

18. 出来事は契約 C8 の5種類。INVITATION_ISSUED・INVITATION_RESENT・INVITATION_CANCELLED は操作した管理者（actorUserId）と対象の招待（targetInvitationId）、結果 SUCCESS で、それぞれの確定の後に記録する。送り直しで送信だけが失敗しても結果は SUCCESS のままとし、送信の失敗は記録しない（FR9.2）。管理者の操作の拒否（400・404・409・503）は記録しない（契約 C8 の結果が SUCCESS だけのため）。
19. REGISTRATION_COMPLETED は actorUserId を空、targetInvitationId と targetUserId（作った利用者）で、確定の後に記録する。REGISTRATION_FAILED は完了の要求のリンクの拒否だけを記録し（入力の誤りと verify の失敗は記録しない、CR3）、actorUserId は空、targetInvitationId は分かるときだけ（見つからない・改ざんでは空）、トランザクションが巻き戻った後に要求の中で記録する（U2 の BR7.3 と同じ扱い）。記録の書き込みの失敗は既存の AuditLog の決まり（別のトランザクションで追記、失敗はアプリのログに ERROR、応答は変えない）に従う。トークン・URL・パスワードは出来事にも列にも入れない。

### 公開の決まり・エラー・秘密

20. `/api/admin/invitations` の下は既存の AccessControl の決まり（未認証 401・管理者でない 403）にそのまま乗る（CR4）。`/api/registration/verify` と `/api/registration/complete` の POST だけを U3 の SecurityRuleContributor で認証なしにする（ADR-011）。順番の値は既存の 110・210 と U8 が使う値に重ならないものをコード生成で決める（重なりは既存の `SecurityExtensionValidator` で起動が止まる）。
21. エラーの code は契約の一覧どおり、新しい `InvitationProblemTypeCatalog`（1つの code に1つの状態コード）に ja・en の説明文で置く。INVITATION_ALREADY_PENDING と INVITATION_NOT_CONFIGURED は契約の追加の項目（invitationId・page、unavailableReasons）を付ける。部品の間の想定内の失敗は結果の型で返し、web の層で Problem Details に変える（契約の共通の決まり）。
22. アプリのログ・トレースの属性・エラー応答には、招待のトークン・URL・メールアドレス・パスワードを出さない。ログに出すのは invitationId・言語・送信の結果の種類・状態までとする（`project.md` の Forbidden、NFR1・NFR2、CR5）。

### 招待メールのテンプレート（C10）

23. U1 の一覧に invitation（差し込みは registrationUrl だけ）を足し、`mail/templates/invitation_ja.html`・`invitation_en.html` を置く（U1 の BR2.1・BR2.2）。本文は E1 の構成で、MasterSmith から招待されたこと・リンクから登録を終えるとログインできること・心当たりが無ければ何もしなくてよいこと（AC3.1.8）、行き先の分かるボタンの文言と URL の文字（AC3.1.9）、「このリンクは 24 時間有効です」（日時は書かない、AC3.1.2）を載せ、招待した管理者の氏名は載せない。`<title>` を件名に、`<html lang>` を言語にする。エスケープのテストの対象となる差し込む利用者の値は registrationUrl だけ（AC3.1.4）。U3 の受け持ちの AC3.1.2・AC3.1.3・AC3.1.8・AC3.1.9 は、テスト用の受け手で受けたメールで確かめる。

### その他

24. スキーマの変更は、U2 の V7 の後の番号（V8 以降）で招待の表を足す（前進のみ・後方互換、NFR10）。新しいパッケージ `invitation` は、パッケージごとのカバレッジの下限（行 80%・分岐 70%）の対象になる（`team.md`）。有効期限の判定（今 < expiresAt）と、トークンの形の確かめは、性質ベースのテストの対象にする（`team.md`）。

## 決まっていること（質問にしない）

- 招待は利用者の表と別の表に持ち、登録の完了で利用者を作る。依存は invitation → user の一方向（ADR-001、`domain-design-questions.md` の Q1: A、要件の Q1）。
- トークンは推測できない値で、ハッシュだけを保存し、1回だけ有効、作成・送り直しから 24 時間で期限切れ（★設定で変えられる）。時刻ちょうどは無効（`project.md` の Mandated、FR1.5、NFR1、`stories.md` の合否の判定の仕方）。
- 招待のリンクは `https://<ベース URL>/register#token=<トークン>` で、画面はトークンを要求の本文に入れて確かめと完了の API を呼ぶ（`contract-design-questions.md` の Q1: A、契約 C6）。
- 招待・送り直しは確定の後にトランザクションの外で送り、結果を別の短いトランザクションで記録し、要求はその結果を待って応答する（ADR-009、FR2.3）。送信は1回だけで自動の再試行はしない（契約 C1、要件の Q4）。
- 送信に失敗しても招待は残り、招待は 201・送り直しは 200 で sendResult を FAILED にして返す（FR2.4、`contract-design-questions.md` の Q2: A）。
- 招待の時に入れるのはメールアドレスと言語だけ（FR1.1、要件の Q5）。登録済み・招待中は拒否し、招待中は一覧の行へ案内する（FR1.4、要件の F4、AC1.1.4）。
- 期限切れの招待と同じメールアドレスへの新しい招待は許し、古い招待は無効になって一覧から消える（AC2.2.6）。取り消し後の再招待は許す（AC2.2.5、要件の前提 A4）。
- 一覧は完了も取り消しもしていない招待（期限切れを含む）を、招待した日時の新しい順に 20 件ずつ（要件の前提 A1、`refined-mockups-questions.md` の Q7: B、契約 C5）。招待した管理者は氏名、無ければメールアドレス（`user-stories-questions.md` の M9: A）。
- ベース URL か SMTP の接続先が無いときは、アプリは起動し、招待と送り直しを 503 INVITATION_NOT_CONFIGURED（理由の一覧つき）で拒否する。取り消しは設定が無くても行える（FR1.8、要件の Q12、AC2.2.9、契約 C5）。
- 登録の完了の拒否は、期限切れ・使用済み・取り消し済み・存在しない・改ざん・同時の完了・同じメールアドレスの利用者がいる、のどれでも同じ 404 REGISTRATION_LINK_INVALID（NFR3、AC3.2.2・AC3.2.11、契約 C6）。
- 登録の完了の入力は、パスワード（2回）・氏名・言語・テーマ・文字の大きさ。パスワードは既存の規則、氏名は U2 の決まり（254 コードポイントまで、前後の空白を除く、Cc・Cf を拒否）（FR4.2・FR4.4、U2 の `functional-design-questions.md` の Q1: A・Q2: B・Q3: A）。自動ではログインしない（FR4.7、要件の Q7）。
- 利用者の作成は C2 の createUser で、呼び出し元のトランザクションに参加し、既存の UserCreatedEvent（ロックの状態の用意）を通す。登録済みは EmailAlreadyUsed で返る（U2 の BR5.1〜BR5.3）。
- 監査の出来事は契約 C8 の INVITATION_ISSUED・INVITATION_RESENT・INVITATION_CANCELLED・REGISTRATION_COMPLETED・REGISTRATION_FAILED と、失敗の理由 INVITATION_EXPIRED・INVITATION_ALREADY_USED・INVITATION_CANCELLED・INVITATION_NOT_FOUND。列は既存の result・failure_reason・actor_user_id と、U2 が足す target_user_id・target_invitation_id（参照の制約なし）。verify の失敗・送信の失敗・入力の誤りは記録しない（要件の Q11・F1、FR9、`stories.md` の CR3、U2 の BR7.1・BR9.2）。
- 招待の管理の API は `/api/admin/invitations`、登録の完了の API は `/api/registration/` の下で差し込み口で公開にする（ADR-011、契約の共通の決まり）。
- 招待メールは HTML、件名は `<title>`、招待の言語で送る。有効期限は「このリンクは 24 時間有効です」とだけ書き、招待した管理者の氏名は載せず、ボタンと URL の文字の両方を載せる（契約 C10、`user-stories-questions.md` の M1: C・M5: A、`mockups.md` の E1）。テンプレートは `mail/templates/invitation_<language>.html` に置き、U1 の一覧に行を足す（U1 の `functional-design-questions.md` の Q1: C・Q4: A）。
- トークン・招待の URL・メールアドレス・パスワードをログ・トレースの属性・エラー応答に出さない。招待の URL を要求の Host から組み立てない（`project.md` の Forbidden）。
- 時刻に依存する処理は注入した時計から得る（`team.md` の Testing Posture）。メールのテストは JVM の中のテスト用の SMTP の受け手で受けて確かめる（`team.md`）。
- 同じメールアドレスの招待中を1件に限る仕組みは NFR 設計、登録の完了の公開の API の回数の制限と SMTP の時間切れの値は NFR 要件で決める（ADR-010・ADR-011、契約の Open questions、`stories.md` の後の段に回す点）。
- E2E（招待から登録の完了まで）は U6 で書く（`unit-of-work.md` の U3 の注意）。

## Q1. 終わった招待を内部DB にいつまで残すか

要件の未解決の点「招待の表の保存期間」が機能設計に回されています。監査の記録の target_invitation_id は参照の制約を持たないため（U2 の BR9.2）、招待の行を消しても監査は壊れませんが、使用済み・取り消し済みのリンクを開かれたときの監査の失敗の理由は、行が無いと INVITATION_NOT_FOUND になります（応答はどれでも同じ）。

A. 消さずに残し続ける（招待の数は利用者の数と同じ程度で小さい見込み）
B. 終わった招待（完了・取り消し・置き換え）を、終わった日時から保存の日数（既定 90 日、設定で変えられる）を過ぎたら、定期の処理で消す（既存のリフレッシュトークンの削除の定期処理と同じ形）。期限切れのまま残る招待中は一覧に出るため消さない
C. B に加えて、期限切れの招待中も、有効期限から保存の日数を過ぎたら消す（一覧から消える）
D. 終わった時点ですぐ行を消す（完了・取り消し・置き換え）。使用済み・取り消し済みのリンクは「存在しない」と同じ扱いになる
X. Other (please specify)

[Answer]: C

理由: 要件で機能設計に回された論点で、定期の処理の有無・状態の持ち方・監査の失敗の理由の出方が変わるため。

## Q2. 送信の途中でアプリが止まったときの送信の結果

ADR-009 の「悪い点」と契約 C5 の Open question です。招待の確定の後、送信の結果を記録する前にアプリが止まると、その招待の送信の結果が残りません。契約 C5 の sendResult は SENT・FAILED の2つです。

A. 招待（送り直し）の確定の時点で sendResult を FAILED（送れたと確かめていない）として保存し、送信に成功したら SENT に書き換える。途中で止まれば一覧に「送信の失敗」と出て、管理者が送り直せる。契約の値は変えない（送信中の数秒は、ほかの管理者の一覧に「送信の失敗」と出うる）
B. 内部に送信中（PENDING）を持ち、確定の時点で PENDING、結果で SENT か FAILED にする。契約 C5 の sendResult に PENDING を足し（安全な追加）、画面（U5）は「送信の結果が分からない」と表示して送り直せるようにする
C. 内部に PENDING を持つが、API では FAILED として返す（契約は変えない。記録の上では「止まった」と「失敗した」を区別できる）
X. Other (please specify)

[Answer]: C

理由: 契約で U3 に回された論点で、sendResult の値の集合と、止まった後に管理者に見える表示が変わるため。

## Q3. ベース URL の値の確かめ

ストーリーの段の「後の段に回す点」で、ベース URL が URL の形でない・`http` などのときに設定されていないのと同じ扱いにするかが機能設計に回されています。招待のリンクにはトークンが載ります（`#` の後はサーバーに送られませんが、`http` の画面は経路の途中で書き換えられうる）。手元の確かめは `http://localhost:8080` などで行います。なお、同じ設定を使う既存のエラー応答の type の URL の扱いは変えません。

A. 絶対 URL（`http` か `https`、ホストがあり、問い合わせ・`#`・利用者情報（`user@`）を含まない。パスは許す）だけを使える値とする。合わなければ BASE_URL_NOT_CONFIGURED と同じ扱いにし、起動のときに項目の名前だけの WARN を1件出す。起動は止めない
B. A に加えて `https` に限る。ただし `localhost`・`127.0.0.1`・`[::1]` のときだけ `http` を許す（手元の確かめのため）
C. 値があるかだけを見て、形は確かめない（誤った値は、招待メールのリンクが開けないことで気づく）
X. Other (please specify)

[Answer]: A

理由: 上流では「無いとき」だけが決まっており、不正な値を使える扱いにするか、`http` を許すかで、招待を使える設定かの判定とテストが変わるため。

## Q4. 登録の完了の時点で同じメールアドレスの利用者がいたときの監査の失敗の理由

この場合の応答は、ほかの拒否と同じ 404 REGISTRATION_LINK_INVALID で、招待は使用済みにならないことが決まっています（AC3.2.11）。一方、契約 C8 の REGISTRATION_FAILED の失敗の理由（INVITATION_EXPIRED・INVITATION_ALREADY_USED・INVITATION_CANCELLED・INVITATION_NOT_FOUND）には、この場合に当たる値がありません。

A. 失敗の理由に EMAIL_ALREADY_REGISTERED（24 文字。既存の列の長さ 32 に収まる）を足して記録する（契約 C8 への値の追加。応答は変えない）
B. 値を足さず、INVITATION_NOT_FOUND として記録する（監査からは本当の理由が分からない）
C. この場合は監査に記録しない（FR9.1 の失敗の列挙に無いため）
X. Other (please specify)

[Answer]: A

理由: 契約 C8 の値の集合で決まらず、監査の記録の種類（契約への値の追加の有無）が変わるため。

---

## Consolidated Summary Confirmation

答えのまとめ:

- 設計の要点（案）は冒頭の「設計の要点（案）」の 24 件のとおり（空でない招待のリンクを U3 がベース URL の値だけから組み立ててから送信を頼む、リンクの確かめでも同じメールアドレスの利用者がいれば拒否する、送り直しでは招待した日時と招待した管理者を変えない、トークンは既存のリフレッシュトークンと同じ作り方 などを含む）
- Q1 C: 終わった招待（完了・取り消し・置き換え）は終わった日時から、期限切れの招待中は有効期限から、保存の日数（既定 90 日、設定で変えられる）を過ぎたら定期の処理で消す
- Q2 C: 内部に送信中（PENDING）を持ち、確定の時点で PENDING、結果で SENT か FAILED にする。API（契約 C5）では PENDING を FAILED として返す（契約は変えない）
- Q3 A: ベース URL は http・https の絶対 URL（問い合わせ・#・利用者情報を含まない。パスは許す）だけを使える値とし、合わなければ BASE_URL_NOT_CONFIGURED と同じ扱いにして起動時に項目の名前だけの WARN を1件出す。起動は止めない
- Q4 A: 登録の完了の時点で同じメールアドレスの利用者がいたときは、監査の失敗の理由に EMAIL_ALREADY_REGISTERED を足して記録する（契約 C8 への値の追加。応答は変えない）

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
