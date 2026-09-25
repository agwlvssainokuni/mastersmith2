# Unit of Work Story Map — user-management

ストーリーは `aidlc/spaces/default/intents/260925-user-management/inception/user-stories/stories.md`、単位は `unit-of-work.md`。

## ストーリーと単位

| Story | ストーリー | 主な単位 | Directory | 関わる単位 |
|---|---|---|---|---|
| US1.1 | 利用者を招待する | U3 | u3-invitation | U1（送信）・U5（入力の Modal）・U4（言語の初期値の元になる管理者の言語） |
| US2.1 | 招待の状況を一覧で確かめる | U3 | u3-invitation | U5（一覧の画面） |
| US2.2 | 招待を送り直す・取り消す | U3 | u3-invitation | U1（送信）・U5（操作と確かめ） |
| US3.1 | 自分の言語で招待メールを受け取る | U1 | u1-mail | U3（テンプレートの中身とリンク） |
| US3.2 | 招待のリンクから登録を完了する | U3 | u3-invitation | U2（利用者の作成）・U6（画面）・U4（表示の設定・ログインの画面の案内） |
| US4.1 | 自分の表示の設定と氏名を変える | U2 | u2-user-preferences | U4（表示の設定を当てる・ユーザーメニューの氏名）・U7（画面） |
| US5.1 | 自分のパスワードを変える | U2 | u2-user-preferences | U7（画面） |

## 単位ごとのストーリー

| 単位 | 主に受け持つストーリー | 関わるストーリー |
|---|---|---|
| U1 u1-mail | US3.1 | US1.1・US2.2 |
| U2 u2-user-preferences | US4.1・US5.1 | US3.2 |
| U3 u3-invitation | US1.1・US2.1・US2.2・US3.2 | US3.1 |
| U4 u4-display-foundation | — | US1.1・US3.2・US4.1（共通の決まり CR1・CR2 の土台） |
| U5 u5-invitation-ui | — | US1.1・US2.1・US2.2 |
| U6 u6-registration-ui | — | US3.2 |
| U7 u7-preferences-ui | — | US4.1・US5.1 |
| U8 u8-instance-appearance | — | 共通の決まり CR2（インスタンスの見た目の設定）。すべての画面に関わる |

すべてのストーリーが1つの主な単位を持つ。U1〜U7 は少なくとも1つのストーリーに関わる。U4〜U7 は画面の単位で、主な単位にはしていないが、画面の受け入れ基準（`stories.md` の画面の表示・アクセシビリティの AC）を受け持つ。U8 はストーリーにせず共通の決まりとした CR2（ストーリーの段の Q3: C・F1: B）を受け持つ。

## 画面をまたぐ要件（共通の決まり）

| 共通の決まり | 受け持つ単位 |
|---|---|
| CR1 言語の適用 | U4（土台・要求の言語）、U1・U3（メールの言語）、U5〜U7（文言） |
| CR2 インスタンスの見た目の設定（Should） | U8（設定の読み取りと公開の API）、U4（画面に当てる） |
| CR3 監査ログ | U2（パスワードの変更と、列の追加と列の一覧の正）、U3（招待と登録の出来事の種類） |
| CR4 利用者ごとの可否 | U2・U3（サーバー側の判定とテスト） |
| CR5 秘密と個人情報 | U1・U2・U3 |
| CR6 画面の共通の決まり | U4〜U7 |

## 単位の中でのストーリーの順

- U2: US4.1（列の追加・読み書き）→ US5.1（パスワードの変更）
- U3: US1.1（招待）→ US2.1（一覧）→ US2.2（送り直し・取り消し）→ US3.2（登録の完了）
- U5: US1.1 → US2.1 → US2.2
