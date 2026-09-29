# Build and Test の質問（Intent 260928-quality-followup）

## 質問にせず、決まっていることとして扱う点

コード生成の記録（`code-summary.md` の 12節）で、Build and Test に引き継がれたものです。

- **最後の実測**: `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify`（colima の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡して、対象DB を含める）と `./gradlew e2eTest`（Mailpit を起動して）を流す。実測の数字だけを記録する。
- **FR3.3 の診断の確かめ**: 待ちの上限を一時的に極端に短くして、2つのテストを1回ずつ流す。決めた項目が失敗の知らせとログに出ることを確かめたら、元に戻す（コードには残さない）。
- **FR3.4 の差**: `team.md` の決まりとの差を `test-results.md` にも書く。
- **統合と CI の確かめ（NFR2）**: コード生成の計画の Step 28 のとおり、Step 25 の検査が通った状態で、依頼者の承認を得て `fix/260928-quality-followup` から `develop` へ fast-forward で統合する。`git push` は依頼者が行い、CI の `verify` の結果をこの段で確かめる。（最初の質問の案では Q2 として尋ねかけたが、計画で決まっていたため質問から外した。）

## Q1. p95 の警報が「鳴る」ことの確かめ方（要件の未決の点 O3）

コード生成の段では、使い捨ての環境で、警報3件の式が値を持つことまでを確かめました（ログイン 487.5 ms、トークンの更新 95 ms、管理者の確認 95 ms。しきい値はどれも 1000 ms）。しきい値を超えたときに実際に鳴ることは、まだ確かめていません。

A. 使い捨ての環境で、しきい値を一時的に低く（例：50 ms）した警報の決まりを読み込ませ、要求を送り続けて3件が鳴ることを確かめる。確かめたら元に戻す（リポジトリのファイルは変えない）。VM のメモリが足りないため、そのあいだ配備したアプリを止める。
B. 鳴ることの確かめは行わず、式が値を持ち、その値がしきい値と比べられることまでで判定する。鳴ることは `Unverified` とし、持ち主（配備の後の段）を書いて引き継ぐ。
C. 使い捨ての環境で本当に遅い応答を作り（例：負荷をかけてログインを 1000 ms 超えにする）、しきい値を変えずに鳴ることを確かめる。時間がかかり、PC の負荷も大きい。
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

- 統合（計画の Step 28）: 依頼者の承認で `develop` を `fix/260928-quality-followup` の先頭（`d1fda19`）へ fast-forward で統合した。`git push` は依頼者が行い、CI の `verify` の結果をこの段で確かめる（NFR2）。
- 最後の実測: `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify`（対象DB を含む）と `./gradlew e2eTest`（Mailpit を起動して）を流し、実測の数字だけを記録する。
- FR3.3 の診断の確かめ: 2つのテストの上限を一時的に極端に短くして1回ずつ流し、決めた項目が出ることを確かめて元に戻す。
- Q1（O3）: A。使い捨ての環境で、しきい値を一時的に 50 ms にした警報の決まりを読み込ませ、3件が鳴ることを確かめて元に戻す（リポジトリのファイルは変えない）。そのあいだ配備したアプリを止める。
- FR3.4 の差を `test-results.md` にも書く。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
