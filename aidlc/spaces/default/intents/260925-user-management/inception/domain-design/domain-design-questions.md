# Domain Design の質問

要件（`requirements.md`）・ストーリー（`stories.md`）・画面イメージ（`mockups.md`）と、既存のコード（`aidlc/spaces/default/codekb/mastersmith2/architecture.md`・`component-inventory.md`）から、部品（コードを書く単位）の境目を決めます。既存の部品は、前の Intent の Domain Design の部品名（UserAccount・Authentication・AccessControl・AuditLog・AppFrame・AuthUi・AdminArea・ApiClient）で呼びます。

決まっていること（質問にしない）:
- 監査の記録は既存の AuditLog に出来事の種類を足す形とし、共通の仕組みは作らない（`project.md` の DECIDED）。
- 管理者かどうかは管理者フラグだけで決める（要件の制約）。
- 実現可能性の評価（Feasibility）の段はこの範囲に無いため、実現できるかの判断（java-mustache-processor の取り込み、JVM の中の SMTP の受け手、組み込み H2 での一意の確かめなど）は、この段の ADR にまとめる（`project.md` の学び）。

---

## Q1. 招待を持つ部品

招待は利用者の表とは別に持ち、登録の完了で初めて利用者を作ります（要件の FR1.3）。

A. 新しい部品 Invitation（パッケージ `invitation`）が、招待の状態・トークン・送り直し・取り消し・登録の完了を持つ。登録の完了では UserAccount の利用者の作成を呼ぶ。依存の向きは invitation → user（user は invitation を知らない）
B. 既存の UserAccount（パッケージ `user`）の中に招待を足す（利用者の作成と同じ部品で扱う）
X. Other (please specify)

[Answer]: A

## Q2. メール送信とテンプレートを持つ部品

A. 新しい共通の部品 Mail（パッケージ `mail`）が、テンプレートの描画（java-mustache-processor、件名は `<title>`）と SMTP の送信を持ち、Invitation はそれを呼ぶ。今後のメールも Mail を使う
B. 招待の部品（Invitation）の中に閉じる（今は招待メールだけのため）
X. Other (please specify)

[Answer]: A

## Q3. 利用者のプリファレンス（氏名・言語・テーマ・文字の大きさ）を持つ部品

A. 既存の UserAccount が利用者の表の列として持ち、読み書きの操作も UserAccount に足す
B. 新しい部品 UserPreference（パッケージ `preference`）が別の表で持つ（利用者の表は変えない）
C. 利用者の表の列として UserAccount が持つが、画面の API（web の層）は新しいパッケージ `preference` に置く
X. Other (please specify)

[Answer]: A

## Q4. パスワードの変更を持つ部品

今のパスワードの照合（`verifyPassword`）とパスワードの規則は UserAccount にあり、ログイン・トークンは Authentication にあります。

A. UserAccount に「パスワードを変える」操作を足す（照合・規則・保存を1か所に）。画面の API は Q3 と同じ置き場に置く
B. Authentication に置く（ログインと同じく認証の操作として扱う）
X. Other (please specify)

[Answer]: A

## Q5. ログインした利用者のエラーの言語をどう決めるか

要件では、ログインした利用者の要求へのエラーの説明文は利用者の言語で返します（FR7.2）。今のサーバーは要求の `Accept-Language` で言語を決め、トークンの認証のたびに利用者を内部DB から読んでいます。

A. 画面（ApiClient）が、ログインした後は利用者の言語を `Accept-Language` に入れて送る（サーバーの言語の決め方は今のまま。画面のバグで食い違うおそれはある）
B. サーバーが、トークンの認証で読んだ利用者の言語を使ってエラーの言語を決める（`Accept-Language` より優先。認証の主体に言語を足す）
X. Other (please specify)

[Answer]: A

## Q6. インスタンスの見た目の設定（ブランドカラー・フォントファミリー）を画面へ渡す部品

A. 新しい小さな部品 InstanceAppearance がログインなしで読める API で返し、画面はログインの前後とも起動時に読んで当てる
B. 画面を配信するとき（AppFrame の配信）に、配信する HTML に設定を埋め込む（API を増やさないが、配信の仕組みに手を入れる）
X. Other (please specify)

[Answer]: A

## Q7. 画面の部品の分け方

A. 機能ごとに分ける: 招待の管理（管理者、`features/invitation`）・登録の完了（ログインなし、`features/registration`）・プリファレンスとパスワードの変更（ログインした利用者、`features/preferences`）。表示の設定（テーマ system・文字の大きさ・言語）を画面に当てる仕組みは AppFrame に足す
B. 1つの機能 `features/users` にまとめ、表示の設定を当てる仕組みもその中に置く
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめ:

- Q1 A: 新しい部品 Invitation（パッケージ `invitation`）が招待の状態・トークン・送り直し・取り消し・登録の完了を持ち、登録の完了で UserAccount の利用者の作成を呼ぶ。依存は invitation → user の一方向
- Q2 A: 新しい共通の部品 Mail（パッケージ `mail`）がテンプレートの描画（java-mustache-processor、件名は `<title>`）と SMTP の送信を持ち、Invitation が呼ぶ。今後のメールも Mail を使う
- Q3 A: 氏名・言語・テーマ・文字の大きさは既存の UserAccount が利用者の表の列として持ち、読み書きの操作も UserAccount に足す
- Q4 A: パスワードの変更は UserAccount に足す（照合・規則・保存を1か所に）。画面の API は UserAccount の側に置く
- Q5 A: ログインした後は画面（ApiClient）が利用者の言語を `Accept-Language` に入れて送り、サーバーの言語の決め方は今のまま
- Q6 A: 新しい小さな部品 InstanceAppearance がブランドカラー・フォントファミリーをログインなしで読める API で返し、画面は起動時に読んで当てる
- Q7 A: 画面は機能ごとに `features/invitation`（管理者）・`features/registration`（ログインなし）・`features/preferences`（ログインした利用者）に分け、表示の設定（テーマ system・文字の大きさ・言語・見た目）を当てる仕組みは AppFrame に足す
- 決まっていること: 監査は既存の AuditLog に出来事の種類を足す（共通化しない）。実現できるかの判断はこの段の ADR にまとめる

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
