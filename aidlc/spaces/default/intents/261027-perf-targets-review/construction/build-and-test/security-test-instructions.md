# セキュリティの確かめの手順（Intent 261027-perf-targets-review）

Test Strategy は Minimal で、アプリのコード・認証・認可・API は変えない。確かめるのは次のとおり。

- 依存の脆弱性: `./gradlew verify` の OSV-Scanner で重大度 High 以上が出ないこと。Build and Test の終わりと `main` への取り込みの前に、直近のコミットの CI（試行ごと）も確かめる。新しい公表はソースを変えなくても次の CI で落ちうる。
- 秘密情報の検出（Gitleaks）: `verify` の中で流れる。測り直しの記録（`evidence/`）に、資格情報・トークン・メールアドレスが入っていないことを、記録の検索で件数 0 を確かめる（`password`・`token`・`@` を含む行の一覧を取り、架空の値と予約のドメインだけであること）。
- Dependabot: `gh pr list --state open` の全件の扱いを記録する（#80 は linux/386 だけのタグのため見送り、`.github/dependabot.yml` に `ignore` を足した）。PR を閉じる操作は依頼者の確認を得てから行う。
