# Unit of Work — user-management

出典: 部品の一覧 `aidlc/spaces/default/intents/260925-user-management/inception/domain-design/components.md`、ADR `aidlc/spaces/default/intents/260925-user-management/inception/domain-design/decisions.md`、要件 `aidlc/spaces/default/intents/260925-user-management/inception/requirements-analysis/requirements.md`、ストーリー `aidlc/spaces/default/intents/260925-user-management/inception/user-stories/stories.md`、この段の答え `units-generation-questions.md`（Q1〜Q4）。

配備はすべての単位で1つの実行可能 WAR（画面の `dist` を同梱）で、単位ごとに別々に配備はしない（`team.md`）。「配備の形」はその前提での置き場を示す。依存と並行できる組は `unit-of-work-dependency.md`、ストーリーとの対応は `unit-of-work-story-map.md`。作る順は Delivery Planning で決める。

## 単位の一覧

| Unit ID | Directory | 名前 | 種類 | 規模 | 部品（components.md） |
|---|---|---|---|---|---|
| U1 | u1-mail | メールの描画と送信 | library | M | Mail |
| U2 | u2-user-preferences | 利用者のプリファレンスとパスワードの変更 | service | M | UserAccount（広げる）・Authentication（広げる）・AuditLog（パスワードの変更の出来事と列） |
| U3 | u3-invitation | 招待と登録の完了 | service | L | Invitation・AuditLog（招待の出来事） |
| U4 | u4-display-foundation | 表示の設定の土台 | ui | M | AppFrame（広げる）・ApiClient（広げる）・AuthUi（広げる） |
| U5 | u5-invitation-ui | 招待の管理の画面 | ui | M | InvitationUi |
| U6 | u6-registration-ui | 登録の完了の画面 | ui | S | RegistrationUi |
| U7 | u7-preferences-ui | プリファレンスとパスワードの変更の画面 | ui | S | PreferencesUi |
| U8 | u8-instance-appearance | インスタンスの見た目の設定 | service | S | InstanceAppearance |

種類の意味: library は単独で動かず他から使う部品、service は API を持つバックエンドの機能、ui は画面。

今回変更しない部品: AccessControl（招待の管理の API を `/api/admin/` の下に置くことで、既存の判定と監査がそのまま付く）と AdminArea（招待の管理の画面は U5 が機能の登録で差し込む）は、どの単位でも変更しない（確認の指摘 R-03）。

監査（AuditLog）の変更の持ち方: 監査の表への対象・結果の列の追加と、列の一覧の正は U2 が持つ。U3 は、U2 が足した列を使って招待と登録の出来事の種類を足す（確認の指摘 R-02）。

見直しの記録: 最初の案では InstanceAppearance を U4（ui）に含めていたが、ui の種類では機能設計の決まりと NFR の設計の文書が作られず、既定への置き換えと警告の業務規則の記録の置き場が無くなるため（確認の指摘 R-01）、依頼者の決定で service の単位 U8 に分けた。利用者の設定と関わりが無いため U2 には入れず、新しい単位にした。

## U1 メールの描画と送信（u1-mail、library）

- **説明**: 言語ごとのテンプレートを java-mustache-processor で描いて HTML のメールを作り、SMTP で送る共通の部品。
- **責務**:
  - java-mustache-processor をサブモジュール `vendor/java-mustache-processor` として取り込み、Gradle の composite build で組む。取得元を Maven Central だけにする決まりを崩さず、推移依存も lockfile と脆弱性検査の対象に含められることを確かめる（`team.md`、ADR-010）。満たせなければ Maven Central への公開に切り替える
  - テンプレートの描画（件名は `<title>` の文面）、エスケープされる差し込みだけで値を入れる決まり、テンプレートのライセンスヘッダー（`{{! ... }}`）と検査の対象への追加
  - SMTP の送信（接続先と資格情報は環境変数だけ、無ければ送らない、時間切れ、失敗の種類を結果で返す、ヘッダーへの改行の差し込みを拒否）
  - 秘密と個人情報の決まり（例外のメッセージ・SMTP の応答・宛先を出さない、`mail.debug` を有効にしない）
  - JVM の中で動くテスト用の SMTP の受け手の採用（ライセンスの確認）と、SpotBugs の関門に `PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` を足す作業（`team.md`。最初に取り込む単位で行う）
  - 手元でメールを見る受け手を compose の profile で起動する設定（`team.md` の Deployment）
- **境界**: 招待の文面（テンプレートの中身）は U3 が持つ。U1 は描画と送信の仕組みだけを持つ。
- **配備の形**: WAR に組み込まれる（新しいパッケージ `mail`）。
- **規模**: M（前例の無い取り込みと、テストの受け手・検査の追加を含むため）。
- **注意**: パッケージ `mail` は新しいため、パッケージごとのカバレッジの下限の対象になる。

## U2 利用者のプリファレンスとパスワードの変更（u2-user-preferences、service）

- **説明**: 利用者に氏名とプリファレンス（言語・テーマ・文字の大きさ）を足し、ログインした利用者が自分で読み書きし、パスワードを変えられるようにする。
- **責務**:
  - 利用者の表への列の追加（Flyway の V7 以降、既存の利用者に初期値、前進のみ・後方互換）
  - 利用者の作成の広げ（氏名とプリファレンスを受け取る。既存のロックの状態の用意を保つ）。U3 がこれを使う
  - プリファレンスと氏名の読み書きの API、パスワードの変更の API（ログインした利用者だけ）
  - ログインとトークンの更新の応答の利用者の情報に、氏名とプリファレンスを足す（Authentication）
  - 監査: パスワードの変更の成功・失敗の出来事と、監査の表への対象・結果の列の追加（最初に監査に手を入れる単位として。Q2: A）。列の一覧の正はこの単位が持つ
- **境界**: 招待と登録の完了の流れは U3。画面は U7 と U4。
- **配備の形**: WAR に組み込まれる（既存のパッケージ `user`・`auth`・`audit` を広げる）。
- **規模**: M。
- **注意**: 既存のパッケージ `user`（domain・repository・service）と `audit`（domain・repository・service）に手を入れるため、テストを足して下限（行 80%・分岐 70%）を満たし、`packagesJudgedByTotal` の一覧から外してパッケージごとの下限の対象に戻す（`team.md`、Domain Design の確認の指摘）。`auth` のパッケージも手を入れる場合は同じ扱い。

## U3 招待と登録の完了（u3-invitation、service）

- **説明**: 管理者の招待・一覧・送り直し・取り消しと、招待された人の登録の完了を受け持つ。
- **責務**:
  - 招待の表（Flyway）、招待のトークン（推測できない値・ハッシュで保存・1回・24 時間）
  - 招待・一覧（20 件ごと、招待した管理者）・送り直し・取り消しの API（`/api/admin/` の下）
  - 招待メールのテンプレート（ja・en）と、確定の後にトランザクションの外で U1 に送信を頼み、結果を記録する流れ（ADR-009）
  - 登録の完了のリンクの確かめと完了の API（公開の決まりを差し込み口で足す、ADR-011）、拒否を同じ応答にする決まり、同時の完了で1人だけ作る決まり、U2 の利用者の作成を呼ぶ
  - 招待を使える設定か（ベース URL・SMTP）の判定
  - 監査: 招待・送り直し・取り消し・登録の完了・登録の失敗の出来事（列は U2 が足したものを使う）
- **境界**: 描画と送信の仕組みは U1、利用者の作成と監査の列は U2。画面は U5・U6。
- **配備の形**: WAR に組み込まれる（新しいパッケージ `invitation`）。
- **規模**: L（状態の移り変わり・トークン・同時の操作・メール送信の失敗・列挙の防止を含むため）。
- **注意**: 同じメールアドレスの招待中を1件に限る作りは NFR 設計で確かめる（ADR-010）。招待から登録の完了までの E2E の代表の流れ1本は、画面がそろう単位（U6）で書く。

## U4 表示の設定の土台（u4-display-foundation、ui）

- **説明**: すべての画面に表示の設定（テーマ system・文字の大きさ・言語・インスタンスの見た目）を当てる仕組みと、要求の言語、ログインの画面の広げ。
- **責務**:
  - AppFrame: テーマ system の解決（make-you-chic-ui に light・dark を渡す）、ログインの後は利用者の設定・ログインの前はブラウザに最後に保存された値を当てる、ブラウザへの保存、表示の言語の切り替えの口と `<html lang>`、ユーザーメニューに氏名を出す、起動時に U8 の見た目の設定を読んで当てる
  - ApiClient: ログインの前は画面の言語、ログインの後は利用者の言語を `Accept-Language` に入れる（ADR-005）
  - AuthUi: ログインの画面の言語の切り替え（Could）、登録の完了から移ったときの案内とメールアドレスの持ち越し
- **境界**: 各画面の中身は U5〜U7。利用者の設定の保存は U2。見た目の設定の読み取りと既定への置き換えは U8。
- **配備の形**: 画面は `dist` として WAR に同梱。
- **規模**: M（既存の画面すべてに当たる骨組みの変更のため）。
- **注意**: make-you-chic-ui は変更しない。

## U8 インスタンスの見た目の設定（u8-instance-appearance、service）

- **説明**: `application.yml` のブランドカラー・フォントファミリーを読み、ログインなしで読める API で返す小さなバックエンド（ADR-006）。
- **責務**: 設定の読み取り、無いときは blue・sans、許されない値のときも blue・sans にして警告のログを1回出す（起動は止めない、FR8.2）、公開の API と公開の決まり（差し込み口）、返すのは色とフォントの名前だけ。
- **境界**: 画面に当てるのは U4。
- **配備の形**: WAR に組み込まれる（新しいパッケージ）。
- **規模**: S。
- **注意**: 設定値だけで、アプリが独自に持つデータは無い（エンティティにしない、`project.md` の学び）。新しいパッケージのため、パッケージごとのカバレッジの下限の対象になる。

## U5 招待の管理の画面（u5-invitation-ui、ui）

- **説明**: 管理者の招待中の一覧・招待の入力の Modal・送り直し・取り消しの確かめの画面（`features/invitation`、画面 S1・S1-M1・S1-M2）。
- **責務**: 一覧（20 件ごと、送信の結果・期限切れの表示）、招待の入力（言語の初期値は管理者自身の言語）、招待中の誤りから一覧の行へ移る操作、取り消しの確かめ、招待を使えないときの警告、ja・en の文言、画面部品ごとのアクセシビリティの検査。
- **境界**: API は U3。表示の設定は U4。
- **配備の形**: `dist` として WAR に同梱。
- **規模**: M。

## U6 登録の完了の画面（u6-registration-ui、ui）

- **説明**: 招待のリンクから開く、ログインなしの単独の登録の完了の画面（`features/registration`、画面 S2）。
- **責務**: リンクの確かめの表示、使えないリンクの同じ表示、氏名・パスワード・表示の設定の入力、選んだ時点で画面に当てる動き、完了で表示の設定をブラウザに保存してログインの画面へ移る、ja・en の文言、アクセシビリティの検査。招待から登録の完了までの E2E の代表の流れ1本（`team.md`）。
- **境界**: API は U3。表示の設定を当てる仕組みは U4。
- **配備の形**: `dist` として WAR に同梱。
- **規模**: S（E2E を含めると M に近い）。

## U7 プリファレンスとパスワードの変更の画面（u7-preferences-ui、ui）

- **説明**: ログインした利用者のプリファレンスとパスワードの変更の画面（`features/preferences`、画面 S4・S5）。
- **責務**: 氏名・言語・テーマ・文字の大きさの表示と保存（テーマと文字の大きさは選んだ時点で見せ、保存しなければ戻す。言語は保存で切り替える）、パスワードの変更、ユーザーメニューへの2つの項目、ja・en の文言、アクセシビリティの検査。
- **境界**: API は U2。表示の設定を当てる仕組みとユーザーメニューの氏名は U4。
- **配備の形**: `dist` として WAR に同梱。
- **規模**: S。
