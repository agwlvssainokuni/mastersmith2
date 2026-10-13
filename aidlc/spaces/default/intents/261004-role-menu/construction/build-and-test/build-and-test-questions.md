# Build and Test の質問 — 261004-role-menu

この段は、全7単位（9つの Bolt）を統合した `develop`（0415b94）を対象に、ビルドとテストを流し、品質の目標ごとに実測を記録する。Test Strategy は Standard。

## 決まっていること（質問にしない）

- コード生成の段の承認の条件: (1) 権限の YAML の上限（節 1,000,000・10 MiB。role R-01 Major）は、この段で測って決め、配備の前の関門とする。(2) あわせて JVM の `ExitOnOutOfMemoryError` を入れるかを決める。(3) ほかの持ち越した指摘は、受け入れた制約か後の段・後の Intent への引き継ぎとして記録し、すぐ直したいものはこの段で拾う。
- 件数とカバレッジは `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` の実測だけを報告する（`project.md`）。長いテストは `caffeinate` で包み、流した後に `pmset -g log` でスリープが無かったかを確かめる。
- この段でコードを直すときは、`develop` から短命のブランチを作り、`verify` と E2E の全体を通してから squash で統合する（`team.md`）。コミットは提案して承認を得てから行う。
- 0415b94 の CI（run 38398376712）は動いている最中。結果は `test-results.md` に記録する。

## Q1. 権限の YAML の上限をどう決めるか

コード生成の段の実測（配備と同じ最大ヒープ 1 GiB）: 10 MiB・約 100 万の節の確かめは 21.9 秒（目標 15 秒を超える）で、GC の後もヒープが約 930 MB 残った。約 50 万の節でも Full GC が起きた。今の上限に合う「正しい書き出し」の最大の大きさは測っていない。

A. 設計の想定の規模（ロールの数・スキーマ・テーブル・カラム）で正しい書き出しを作り、その大きさと節の数を測る。それに余裕を持たせた上限の候補（たとえば 30 万・50 万の節）で、配備と同じ上限の使い捨ての環境のヒープと時間を測り、結果を見て値を決める（決めた後に直す）
B. 担当の案のとおり節 50 万・5 MiB に下げ、同じ試しをやり直して確かめる
C. 今の値のまま、既知の制約として Performance Validation に回す
X. Other (please specify)

[Answer]: A

## Q2. メモリが尽きたときに JVM を止める設定（`ExitOnOutOfMemoryError`）を入れるか

今は、メモリが尽きても JVM は止まらずに動き続ける。compose のアプリは `restart: "no"`（異常で止まったときは再起動せず、開発者が原因を見る）。

A. Dockerfile の JVM の既定の引数に足す。メモリが尽きたらアプリは止まり、起き直さない（compose の方針どおり、開発者が原因を見てから起動し直す）。テストの JVM には足さない
B. 足さない（今のまま。メモリが尽きた後も動き続ける）
X. Other (please specify)

[Answer]: A

## Q3. 持ち越した指摘のうち、この段で直すものはどれか（複数選べる）

A. app-frame-ui R-01: 作業ロールの切り替えの後にメニューの読み直しが失敗したら、印（読み直しの失敗の表示）を出す
B. group R-03: グループの名前で、幅ゼロの空白などの見えない文字だけの名前を空として拒む
C. role-admin-ui R-02: 処理中の知らせのフックの複製（groupadmin・roletransfer）にテストを足す
D. app-frame-ui R-05: サイドバーの項目を修飾キー（Ctrl・Cmd）つきで押したときは、新しいタブで開けるようにする
E. どれもこの段では直さない（すべて受け入れた制約か後の Intent への引き継ぎとして記録する）
X. Other (please specify)

[Answer]: A, B, C, D

## Q4. k6 の負荷の試験をどこで流すか

この Intent の流れには Performance Validation の段がある。B3〜B9 で書いた k6 の場面（グループ・ロール・割り当て・受け渡し・メニュー）は、まだ流していない。

A. Performance Validation の段に任せ、この段では `Unverified`（持ち主 performance-validation）として引き継ぐ。Q1 の上限の測りだけはこの段で行う
B. この段で k6 の全部の場面も流す
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

答えのまとめ: Q1 A（設計の想定の規模で正しい書き出しの大きさと節の数を測り、余裕を持たせた上限の候補を配備と同じ上限の使い捨ての環境で測ってから値を決め、直す）、Q2 A（Dockerfile の JVM の既定の引数に ExitOnOutOfMemoryError を足す。テストの JVM には足さない）、Q3 A・B・C・D（読み直しの失敗の表示、見えない文字だけのグループ名を空として拒む、処理中の知らせのフックの複製のテスト、修飾キーつきのクリックで新しいタブ）を、この段で短命のブランチで直し、verify と E2E の全体を通して squash で統合する、Q4 A（k6 の場面は Performance Validation に任せ、この段では Unverified として引き継ぐ。Q1 の上限の測りだけはこの段で行う）。この内容で進めてよいか。

- Looks correct
- Request changes

[Answer]: Looks correct
