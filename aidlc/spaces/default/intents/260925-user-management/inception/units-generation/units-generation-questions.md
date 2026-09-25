# Units Generation の質問

部品の一覧（`aidlc/spaces/default/intents/260925-user-management/inception/domain-design/components.md`）と ADR（`decisions.md`）、ストーリー（`stories.md`）から、作業の単位（Unit）の分け方を決めます。どの順に作るか（価値・危険・骨格のどれを先にするか）は、次の Delivery Planning で決めます。

決まっていること（質問にしない）:
- 配備は1つの実行可能 WAR（画面の `dist` を同梱）で、単位ごとに別々に配備はしない（`team.md` の Code Style・Deployment）。
- 部品の境目は Domain Design のとおり（招待は Invitation、メールは共通の Mail、プリファレンスとパスワードの変更は UserAccount、見た目の設定は InstanceAppearance、表示の設定を当てる仕組みは AppFrame）。

---

## Q1. 単位の分け方

A. バックエンドの部品と画面を分ける（前の Intent と同じ形）。例: Mail（library）・利用者のプリファレンスとパスワードの変更（UserAccount と Authentication の広げ、service）・招待と登録の完了（Invitation、service）・見た目の設定と表示の設定の土台（InstanceAppearance・AppFrame・ApiClient）・招待の管理の画面（ui）・登録の完了の画面（ui）・プリファレンスとパスワードの変更の画面（ui）などの 6〜7 単位
B. 利用者の目的ごとに、バックエンドと画面をまとめた縦の切れ目にする。例: Mail（library）・表示の設定の土台（見た目の設定・AppFrame・ApiClient）・プリファレンスとパスワードの変更（UserAccount の広げと画面）・招待と招待の管理（Invitation の招待の側と画面）・登録の完了（Invitation の完了の側と画面）の 5 単位
C. 大きくまとめる。例: Mail（library）・招待と登録の完了（バックエンドと画面）・プリファレンスと表示の設定（バックエンドと画面）の 3 単位
X. Other (please specify)

[Answer]: A

## Q2. 監査の出来事を足す作業の置き場

監査ログ（AuditLog）に、招待・送り直し・取り消し・登録の完了と失敗・パスワードの変更の出来事の種類と、対象と結果の列を足します。

A. 出来事を出す単位の中で、その出来事の種類を足す（招待の出来事は招待の単位、パスワードの変更は利用者の単位）。対象と結果の列を足す移行は、最初に監査に手を入れる単位で行う
B. 監査の変更だけの単位を1つ作り、ほかの単位がそれに依存する
X. Other (please specify)

[Answer]: A

## Q3. 独立した単位を並行して作れることの扱い

依存の図の上で、互いに依存しない単位は並行して作れます（例: Mail と利用者のプリファレンス）。

A. 並行できることは依存の図に記録するが、作るのは1つずつ（前の Intent と同じ。順は Delivery Planning）
B. 並行できる単位は並行して作る前提にする
X. Other (please specify)

[Answer]: A

## Q4. 利用者の表の変更（氏名・プリファレンスの列）を持つ単位

利用者の表への列の追加は、プリファレンスの読み書きと、登録の完了での利用者の作成（氏名とプリファレンスを受け取る）の両方に要ります。

A. 利用者の単位（プリファレンスとパスワードの変更）が列の追加と作成の広げを持ち、招待と登録の完了の単位はそれに依存する
B. 招待と登録の完了の単位が列の追加を持ち、利用者の単位がそれに依存する
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめと、分け方の案:

- Q1 A: バックエンドの部品と画面を分ける
- Q2 A: 監査の出来事の種類は出来事を出す単位の中で足す。対象と結果の列の移行は、最初に監査に手を入れる単位（U2。U3 は U2 に依存するため必ず先になる）で行う
- Q3 A: 並行できることは依存の図に記録するが、作るのは1つずつ（順は Delivery Planning）
- Q4 A: 利用者の表の列の追加と、利用者の作成の広げ（氏名とプリファレンスを受け取る）は U2 が持ち、U3 は U2 に依存する

分け方の案（7 単位）:

- U1 `u1-mail`（library）: Mail。テンプレートの描画（java-mustache-processor の取り込みを含む）と SMTP の送信。依存なし
- U2 `u2-user-preferences`（service）: UserAccount の広げ（氏名・プリファレンスの列、読み書き、パスワードの変更、作成の広げ）、Authentication の応答の広げ、AuditLog のパスワードの変更の出来事と対象・結果の列。依存なし
- U3 `u3-invitation`（service）: Invitation（招待・一覧・送り直し・取り消し・登録の完了・公開の決まり）と AuditLog の招待の出来事。依存: U1・U2
- U4 `u4-display-foundation`（ui）: AppFrame の表示の設定の当て方（テーマ system・文字の大きさ・言語の切り替え・ブラウザへの保存・ユーザーメニューの氏名）、ApiClient の要求の言語、AuthUi（ログインの画面の言語の切り替え・登録の完了の後の案内）、InstanceAppearance（見た目の設定の公開の API。小さなバックエンドを含む）。依存: U2
- U5 `u5-invitation-ui`（ui）: 招待の管理の画面。依存: U3・U4
- U6 `u6-registration-ui`（ui）: 登録の完了の画面。依存: U3・U4
- U7 `u7-preferences-ui`（ui）: プリファレンスとパスワードの変更の画面。依存: U2・U4

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
