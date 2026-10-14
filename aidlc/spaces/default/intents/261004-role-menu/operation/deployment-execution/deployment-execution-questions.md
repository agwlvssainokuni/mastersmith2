# Deployment Execution の質問 — 261004-role-menu

## 決まっていること（質問にしない）

- 配備の流れと手順は、承認済みの `operation/deployment-pipeline/cd-config.md`（11 の順）・`deployment-strategy.md`（入れ替えとスモークテスト S1〜S11、監査の期待の件数）・`rollback-runbook.md`（戻しと戻しの練習 D1〜D6）のとおりに行う。環境は `operation/environment-provisioning/` のとおり（今の WAR を使い、入れ替えの前に SHA-256 と直しの値を確かめ直す。戻しの練習の間は lgtm などを起動しない。片付けは配備の終わりに名前の無いイメージとビルドのキャッシュだけ）。
- 配備するアプリのコードは 005730a（CI 成功）。移行 V10〜V12 は新しい版の起動で当たり、前の版のアプリへ戻すときは V10〜V12 の表と列が残ったまま動く（広げるだけの変更）。入れ替えの前に内部DB を複写する（権限 600）。
- パスワードなど秘密が要る操作（ログイン、画面の操作）は依頼者が行い、AI は監査の記録とログで裏付ける。値は表示しない（`project.md`）。
- 監査に残る操作は前もって伝え、依頼者が画面を触ったら、監査に残る操作をしたかをその場で尋ねて期待の件数を合わせる（`project.md`）。
- main への取り込みは develop からの fast-forward で、タグは付けない。push は依頼者が行う。

## Q1. 配備をいつ行うか

入れ替えのあいだ（停止・複写・起動、数分）と、DSL の移し替えのあいだは、配備したアプリが使えない。スモークテストと戻しの練習には、依頼者の画面の操作が要る（あわせて 30〜60 分ほど）。

A. 今から行う（依頼者が画面の操作に付き合える）
B. 後で行う（依頼者が時間を決めて知らせる）
X. Other (please specify)

[Answer]: A

## Q2. 初期管理者の救済の見込み

前の配備の後に、初期管理者の利用者を画面で次のように変えたことがあるか。どれかに当たると、新しい版の起動で救済が働き、パスワードが `.env` の値に戻り、ログイン中の端末はログインし直しになる（前の版から同じ仕組み）。入れ替えの前に内部DB の複写でも数えて確かめる。

A. どれもしていない（救済は働かない見込み）
B. パスワードを画面で変えた
C. 利用を止めた、または管理者の印を外した
X. Other (please specify)

[Answer]: A

## Q3. スモークテストで作る確かめ用のロール2つとグループを、最後に残すか消すか

A. 消す（ROLE_UNASSIGNED・ROLE_DELETED が各 2、GROUP_MEMBER_REMOVED・GROUP_DELETED が各 1 の監査が増える。配備の後の複写の前に消す）
B. 残す（後の Performance Validation や画面の確かめに使える。監査は増えない）
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

答えのまとめ: Q1 A（今から配備する。依頼者が画面の操作に付き合う）、Q2 A（前の配備の後に初期管理者を画面で変えていない。救済は働かない見込みで、入れ替えの前に複写でも数えて確かめる）、Q3 A（スモークテストで作るロール2つとグループは、配備の後の複写の前に消す）。承認済みの cd-config.md・deployment-strategy.md・rollback-runbook.md の手順どおりに進める。この内容で進めてよいか。

- Looks correct
- Request changes

[Answer]: Looks correct
