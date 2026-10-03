# Requirements Analysis の質問（261003-user-admin-followup）

## 決まっていること（質問にしない）

依頼の文と、前の Intent（260930-user-admin）の `operation/feedback-optimization/feedback-loop.md` の第1の束、team.md・project.md の決まり、コードの調査（`aidlc/spaces/default/codekb/mastersmith2/` の K-17〜K-24）で決まっている点です。

- **K1（閉じた後のフォーカス）**: make-you-chic-ui の固定先を `3d9521a` から `e82b651`（`origin/main` に公開済み）へ上げる。承認を得た専用のコミットで前後のハッシュを記録し、`develop` へは短命のブランチから fast-forward で統合する（team.md・project.md の決まり）。利用者の管理の画面の確かめの表示（`Modal`）に `finalFocusRef` を渡し、閉じた後に行の「操作」のボタンへ戻す。Dropdown が開き口の ref を置き換えるため、渡す ref は画面が持つ行の「操作」のボタンの参照（`actionRefs`）から作る（K-17）。E2E 110・120 は、`inert` が外れるのを待つ形から、閉じた後のフォーカスが行の「操作」にあることを確かめる形に替える。
- **K2（メニューのはみ出し）**: `UserRowActions.tsx` の Dropdown に `placement="bottom-end"` を足す。E2E 120 に、開いたメニューの矩形が画面の中に収まることの確かめを足す（今の横のスクロールの判定は、画面に固定で置くメニューを拾えない。K-18、project.md の学び 2026-10-03）。
- **K3（言語の欄）**: 利用者の情報の変更の表示（`EditProfileDialog.tsx`）で、送信中は言語の選択（`RadioGroup`）を押せなくする。画面部品のテストで確かめる（K-19）。
- **T2（AC2.2.6）**: 管理者の印を外した直後の要求の 403 と、その監査の行を、1つのテストで続けて確かめる（K-21）。
- **T4（perf/README.md）**: `hikaricp.connections.acquire` の単位（秒・ミリ秒）が外部エクスポートの有無で変わるため、`baseUnit` を見ることを書く（K-23）。
- **S1（Q-H）の確かめ**: 一意の制約に当たる要求（同時の利用者の作成・同時の招待など）を送り、アプリのログ（INFO の既定と TRACE の両方）・監査・エラー応答に重なった値（メールアドレスなど）が出ないことを確かめるテストを足す。Hibernate の `SqlExceptionHelper` の既定のログの経路を先に確かめる（K-24）。出ると分かったときは `project.md` の Forbidden（メールアドレスをアプリのログに含めない）に当たるため直す。
- **負荷の試験の持ち主**: この Intent の流れに Performance Validation の段が無いため、T1 の k6 の試験は Build and Test で、使い捨ての環境で行う（project.md の学び）。
- **配備**: 前の Intent の配備の手順（`cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`）を正とし、今回の差だけを Deployment Pipeline の段で書く。
- **バックエンドのカバレッジ**: 手を入れるパッケージが `packagesJudgedByTotal`（今は 7 個）に当たれば、team.md の決まりでそのパッケージの下限を満たして一覧から外す。当たるかは Code Generation の計画で実測して見積もる。

## Question 1
S1 の確かめで、一意の制約の違反の文（重なったメールアドレスを含む）がアプリのログに出ると分かったとき、どう直しますか？

A. ログの設定で、その文を出すロガー（`org.hibernate.engine.jdbc.spi.SqlExceptionHelper` など、確かめで分かったもの）を止める（`application.yaml` とテストの設定。アプリのコードは変えない）
B. 違反を受ける所で例外の文を出さずにクラスの名前だけを出す形に、アプリのコードを直す（ロガーの設定は変えない）
C. A と B の両方を行う（ロガーを止め、受ける所でもクラスの名前だけにする）
D. 直し方は確かめの結果を見てから、Code Generation の計画の承認の場で決める
X. Other (please specify)

[Answer]: D

## Question 2
T1 の完了の目安「警報3件（`ms-pool-pending`・`ms-error-logs`・`ms-audit-fail`）が `Alerting` になる」をどう確かめますか？（`ms-pool-pending` は 1 分ごとの値を見て、1 分続いたら鳴る形のため、短い待ちでは鳴りにくい見込みです。K-20）

A. 接続プールの上限を下げ、待ちが 1 分以上続く負荷をかけて、本物の警報の決まりのまま3件が鳴ることを確かめる
B. 待ちと時間切れの指標が出ることは本物の負荷で確かめ、警報が鳴ることは、しきい値・続く時間を下げた警報の決まりの写しを使い捨ての環境に読み込ませて確かめる（前の Intent 260928-quality-followup と同じ形）
C. 待ちと時間切れの指標が出ることだけを確かめ、警報が鳴ることは `Unverified` として配備先が決まったときに持ち越す
X. Other (please specify)

[Answer]: A

## Question 3
T3（`MailConfigurationIT` の出力を捕まえる範囲の弱さ）をどこまで直しますか？（`CapturedOutput` が背景のスレッドの出力も含むため、関係の無い出力で確かめが落ちうる。前回どの確かめで落ちたかの記録は無く、再現もしていません。K-22）

A. 確かめを、そのテストが確かめたいロガーの出力だけに絞る形に直す（再現は試みない。直した後に単独で数回と verify で確かめる）
B. 先に再現を試み（時間の上限を決める）、原因が確かめられたときだけ直す。再現しなければ記録だけにする
C. この Intent では直さず、次の Intent へ持ち越す
X. Other (please specify)

[Answer]: A

## Question 4
K1 の閉じた後のフォーカスは、招待の画面など、利用者の管理の画面の外の確かめの表示（`Modal`）でも起きている見込みです（未確認）。どこまで扱いますか？

A. 利用者の管理の画面だけを直して確かめる。ほかの画面は固定先を上げた効果に任せ、確かめもしない
B. 利用者の管理の画面を直し、ほかの `Modal` を使う画面は E2E か手元のブラウザで閉じた後のフォーカスを確かめる。戻らない画面があれば、この Intent で `finalFocusRef` などで直す
C. B と同じく確かめ、戻らない画面は記録だけにして次の Intent へ持ち越す
X. Other (please specify)

[Answer]: B

## Consolidated Summary Confirmation

- 「決まっていること」の K1・K2・K3・T2・T4・S1 の確かめ・負荷の試験の持ち主（Build and Test）・配備（前の Intent の手順を正とする）・カバレッジの扱いは、そのまま要件にする。
- Q1: D — S1 の確かめでアプリのログ（INFO・TRACE）・監査・応答に重なった値が出ると分かったときの直し方（ロガーを止める・受ける所でクラスの名前だけにする・両方）は、確かめの結果を見てから Code Generation の計画の承認の場で決める。出ないと分かったときは確かめのテストだけを足す。
- Q2: A — T1 は、使い捨ての環境で接続プールの上限を下げ、待ちが 1 分以上続く負荷を準備のログインの失敗を含まない台本でかけ、本物の警報の決まりのまま `ms-pool-pending`・`ms-error-logs`・`ms-audit-fail` の3件が `Alerting` になることを確かめる。
- Q3: A — T3 は、`MailConfigurationIT` の確かめを、確かめたいロガーの出力だけに絞る形に直す。再現は試みず、直した後に単独で数回と verify で確かめる。
- Q4: B — K1 は利用者の管理の画面を直し、ほかの `Modal` を使う画面（招待の画面など）でも閉じた後のフォーカスを E2E か手元のブラウザで確かめる。戻らない画面があれば、この Intent で `finalFocusRef` などで直す。

Does this all look correct before I generate the requirements artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
