# Build and Test の質問

コード生成の段で、5つの単位（U1〜U5、Bolt B1〜B7）と CI の不安定なテスト2件の直しを `develop` に統合した（最後は d220447、push 済み）。各 Bolt の作業ブランチの上では verify と E2E の全件が通ったが、B6 と直しを合わせた `develop` の今の形ではまだ流していない。

## 決まっていること（質問にしない）

- Test Strategy は Standard。integration・security・performance の手順書を作り、性能の目標は Performance Validation（この Intent の後の段）が持ち主になる。
- 測っていない負荷の目標（U1 の NFR1.1〜NFR3.2、U2 の書き込みの場面など）は `Unverified` とし、持ち主を Performance Validation と書く。目標は緩めない。
- 要件の網羅は、要件定義の FR・NFR と受け入れ基準から、機能設計の BR・NFR 要件の枝番を経て、各単位の traceability.json へ2段の連鎖でたどる。
- テストの件数とカバレッジは、`:backend:cleanTest :backend:cleanIntegrationTest` を付けた verify の実測だけを報告する。長いテストは `caffeinate -dims` で包み、`pmset -g log` を確かめる。
- E2E で見本の PG を使うときは、127.0.0.1:15432 を開けた直後に誤ったパスワードが拒まれることを確かめ、終わったら閉じる。

## Q1. `develop` の今の形での verify と E2E の全件

A. この段で、`develop` の先頭（d220447 の後の記録のコミットを含む）で verify と E2E の全件を1回ずつ流し、件数・カバレッジ・時間を記録する（推奨）

B. 流さない。作業ブランチの上の結果と CI の結果で代える

X. Other (please specify)

[Answer]: B

## Q2. memory.peak の内訳の測り（U5 の BR3.4・BR3.5・BR4.1〜BR4.3、持ち主は Build and Test）

A. この段で、承認済みの手順（`perf/dsl-mixed-memory.sh`、既定の設定で2回、`RETENTION_TIME=0` の試しで2回、別々の使い捨ての環境）で測る。測る間は配備したアプリを止める（約 40〜60 分の見込み）。判定の物差し（memory.peak / memory.max < 0.9）は変えない。結果は取り込みを含めて、その時点で諮る（推奨）

B. 測らず、Performance Validation に回す

X. Other (please specify)

[Answer]: A

## Q3. 業務データの負荷の試験（k6 の業務データの場面）

A. この段では流さず、Performance Validation で流して判定する（この Intent の後の段に Performance Validation がある）。この段は手順書と、台本の `k6 inspect` の確かめまで（推奨）

B. この段で流して判定する

X. Other (please specify)

[Answer]: A

## Q4. CI の確かめ

A. d220447 の CI（実行中）の結果を待ち、B5 の統合の後のすべてのコミットの CI を試行ごとに確かめて記録する。再実行で通ったものは1回目の失敗とテストも書く（推奨）

B. 最後のコミットの CI の結論だけを記録する

X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

**build-instructions**
- 依存の入れ方・環境（colima の DOCKER_HOST・TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE、見本の PG と Mailpit）・1コマンドの検査（`./gradlew verify`）・E2E（`./gradlew e2eTest`、見本の PG の 15432 の開け閉め）・成果物（WAR）を、今の README と Gradle のタスクから記録する。

**テストの実行（Step 9）**
- verify と E2E の全件は、`develop` の今の形では流さない（Q1: B）。各 Bolt の作業ブランチの上の最後の結果（B7 の後: verify 23分24秒・E2E 256 件）と、CI の結果を正として記録する。合わせた形の確かめは CI が受け持つ。
- memory.peak の内訳を、この段で測る（Q2: A）。`perf/dsl-mixed-memory.sh` で既定の設定2回・`RETENTION_TIME=0` の試し2回、それぞれ別の使い捨ての環境で行い、測る間は配備したアプリを止める。物差しは memory.peak / memory.max < 0.9 のまま。取り込むかは結果を見て諮る。
- 業務データの k6 の場面は流さず、Performance Validation で判定する（Q3: A）。この段は手順書と `k6 inspect` の確かめまでとし、負荷の目標は `Unverified`（持ち主 Performance Validation）とする。
- CI は、d220447 の結果を待ち、B5 の統合の後のすべてのコミットを試行ごとに確かめて記録する（Q4: A）。

**手順書**
- integration・security・performance の手順書を作る（Standard。security と performance は NFR があるため）。

**build-and-test-summary・test-results**
- Target Verification Matrix に、全単位の NFR 要件・NFR 設計・Testing Contract の目標を1行ずつ並べ、Met・Not Met・Unverified のどれかを付ける。
- 既知の制約（G13・G15・U3 の R-04・R-07・R-09、詰め直しの道具、切り替えの直後の知らせ、不安定と確かめられていない3件、make-you-chic-ui の依頼（5）の持ち越し）を書く。

**cross-unit-traceability**
- 要件定義の FR・NFR と受け入れ基準を、各単位の traceability.json と2段の連鎖でたどり、覆われていない ID を承認の場に出す。

Does this all look correct before I generate the build and test artifacts?

- Looks correct
- Request changes

[Answer]: Looks correct
