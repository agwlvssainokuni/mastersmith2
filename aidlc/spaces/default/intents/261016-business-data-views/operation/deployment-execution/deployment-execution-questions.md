# Deployment Execution の質問（Intent 261016-business-data-views）

## 決まっていること（質問にしない）

- **配備の手順**: Deployment Pipeline の `cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md` のとおり（流れの順 1〜15、スモークテスト S1〜S13、戻しの練習）。Environment Provisioning の `validation-report.md` の申し送り（WAR の作り直しを `caffeinate` で包む、戻しの練習の間は lgtm を起動しない）に従う。
- **配備の前の検査**: verify・CI・E2E は Build and Test と CI Pipeline の結果を正とし、流し直さない。配備の前の k6 は流さず Performance Validation に任せる（cd-config.md 3.3 に決まりとの差を記録済み）。
- **内部DB の移行**: V13（`audit_events` に空を許す3列を足す、前進のみ、後方互換）。新しい版の起動のときに Flyway が流す。入れ替えの前に内部DB を複写する（権限 600）。
- **書き込みの設定**: 入れ替えの停止の間に、`.env` を外へ複写してから書き込みの2項目と `.env.targetdb` の書き込みのパスワードを足し、見本の PG を作り直して書き込みのアカウントを作る（Deployment Pipeline の Q1: A）。パスワードの生成と書き足しのコマンドは、流す前に依頼者に見せて承認を得る（Q2: A）。
- **依存するサービス**: 見本の対象DB（`mastersmith-targetdb-postgres-1`）と Mailpit は動いている。TEMPORARY の取り上げは済んでいる。
- **戻し方**: イメージだけ（戻し先 `mastersmith:pre-business-data-views` = `f103aee1702a`）。書き込みだけを止める手（`.env` の複写を戻す）もある。
- **`main` への取り込み**: 配備の記録をコミットしてから、依頼者の承認を得て `develop` を fast-forward する。push は依頼者が行う。

## 質問

### Q1. 救済の見込み（初期管理者）

新しい版の起動のたびに、初期管理者の設定の利用者が停止中・管理者の印なし・パスワードが `.env` の値と違う、のどれかに当たると、自動で救済が働きます（初期管理者のパスワードが `.env` の値に戻り、ログイン中の端末はログインし直しになる）。前の配備（Intent 261015-deps-ui-carryover）の後に、初期管理者について次のことをしましたか？

A. どれもしていない（画面でパスワードを変えていない・止めていない・印を外していない）
B. 画面で初期管理者のパスワードを変えた（救済で `.env` の値に戻ることを受け入れる）
C. 初期管理者を止めた、または印を外した（救済で戻ることを受け入れる）
D. 分からない（入れ替えの前の複写で、停止と印の有無を数えて確かめてから決める）
X. Other (please specify)

[Answer]: A

### Q2. 配備を始める時期とアプリの停止

入れ替えの停止の間に、内部DB の複写・`.env` の書き足し・見本の PG の作り直しと書き込みのアカウントの作成・イメージの作り直しを行うため、アプリは数分止まります。見本の PG も作り直しの間（十数秒）止まります。配備の後の監査の確かめと戻しの練習の複写で、さらに約 10 秒ずつ止まります。

A. 今から始める（上の停止を受け入れる）
B. 時期を指定する（Other に時刻を書く）
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

- Q1: A — 前の配備の後に、初期管理者のパスワードを画面で変えていない・止めていない・印を外していない（救済は働かない見込み。入れ替えの前の複写で停止と印の有無を数えて裏付ける）。
- Q2: A — 今から配備を始める（入れ替えで数分、見本の PG の作り直しで十数秒、配備の後の確かめと戻しの練習の複写で約 10 秒ずつのアプリの停止を受け入れる）。
- 決まっていること: Deployment Pipeline の手順のとおり（停止の前の WAR の作り直しと確かめ、戻し先のタグ、入れ替えの前の複写、`.env` の外への複写と書き込みの設定の書き足し（コマンドは承認を得てから流す）、見本の PG の作り直しと書き込みのアカウントの確かめ、入れ替え、health、救済の見込み、スモークテスト S1〜S13、監査の件数、戻しの練習、`main` への fast-forward は承認を得てから、push は依頼者）。移行は V13。戻しはイメージだけ。

Does this all look correct before I execute the deployment?

- Looks correct
- Request changes

[Answer]: Looks correct
