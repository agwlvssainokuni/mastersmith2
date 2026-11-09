# テストの結果（Intent 261108-ci-checks-release）

入力: `construction/code-generation/code-generation-plan.md`・`unit-test-instructions.md`・`code-summary.md`

## 1. 手元の全件の `verify`

- コマンド: `caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify -PitForks=3`（`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡す）
- コミット: `ded97b38`（作業ブランチ `feature/261108-ci-checks-release`）
- 結果: 終了コード 0、BUILD SUCCESSFUL、15分34秒（935 秒）。走っている間（08:24〜08:40）の Sleep・DarkWake は無い（その前の 08:02 の DarkWake だけ）。

| 種類 | 件数 | 通った | 失敗 | 飛ばした |
|---|---:|---:|---:|---:|
| バックエンドの単体テスト | 2,939 | 2,939 | 0 | 0 |
| バックエンドの結合テスト | 1,716 | 1,706 | 0 | 10（方言の飛ばし。前と同じ） |
| 画面のテスト（Vitest、計測なしと計測ありの2回） | 3,063 × 2 | 3,063 × 2 | 0 | 0 |

- 単体テストは前の Intent の 2,937 件から 2 件増えた（`common.health` に足した2件）。
- バックエンドのカバレッジ: 行 14,915 / 15,258（97.75%）、分岐 6,375 / 6,966（91.51%）。`jacocoTestCoverageVerification` が通った（全体の下限と、すべてのパッケージごとの下限。外すパッケージは無い）。
- パッケージ 71 個のうち、パッケージごとの下限（行 80%・分岐 70%）を下回るものは 0 個。

`packagesJudgedByTotal` だった 7 パッケージ（この Intent で一覧を消した）:

| パッケージ | 行 | 分岐 |
|---|---|---|
| `access.service` | 11/11 | 分岐なし |
| `audit.repository` | 数える行なし（interface だけ。判定は通った） | — |
| `common.error.domain` | 41/41 | 17/18 |
| `common.error.service` | 28/28 | 13/16 |
| `common.health` | 53/53（前は 42/53） | 2/2 |
| `common.i18n.domain` | 38/38 | 25/28 |
| `common.web` | 110/114 | 46/54 |

## 2. E2E 全体

- コマンド: `caffeinate -dims ./gradlew e2eTest`（Mailpit と、E2E 用のポートを開けた見本の PostgreSQL を起動して）
- 結果: 298 件すべて通った（15分13秒、10:02:48〜10:18:02。この間の Sleep・DarkWake は無い）。

### 2.1 途中の失敗と直し（記録）

1回目と2回目は、Mailpit・見本の PostgreSQL を起動していなかったため、E2E が始まる前に止まった（テストの失敗ではない）。

3回目（09:08 ごろ〜）は 269 件が通り、10 件が落ちた。落ちた後に続く 19 件は流れなかった。
- 落ちたもの: 060・080・090・110・150・170・180・190・195・200。どれも招待メールの送信の結果が SENT にならない。
- 090 だけを流し直しても再現した（Mailpit を作り直しても同じ）。依頼者の決定（A）で、30 分を上限に調べた。
- WAR を手で起動して招待を1件送ると、アプリのログは `failureKind=TIMEOUT`（`MailSendException`）だった。
- `nc` と curl で測ると、Mailpit の SMTP の挨拶が返るまで約 7.7 秒かかった（アプリのメールの待ちの上限は 3 秒）。
- VM の中の DNS は速い（逆引き 0 ms）。一方、コンテナは Docker の DNS（127.0.0.11）と、つないでいるネットワークの検索先を使う。
- 逆引きを止める設定 `MP_SMTP_DISABLE_RDNS=true` の Mailpit では、挨拶が 0.01 秒で返った。原因は、Mailpit が送り手の名前を逆引きすることと、今のネットワークの DNS が遅いこと。この Intent の変更とは関係ない（WAR は前の Intent で E2E が通ったものと同じ、E2E のコードも変えていない）。
- 直し: 依頼者の決定（A）で、`compose.yaml` の Mailpit に `MP_SMTP_DISABLE_RDNS: "true"` を足した（コミット `9a739212`、依頼者の承認を得た）。作り直した後、A1 の台本で版とダイジェストが `compose.yaml` と一致することを確かめ、4回目で全件が通った。
- 調べにかかった時間: 約 15 分。

E2E の後、見本の PostgreSQL は E2E 用のポートを外して作り直した（前の Intent と同じ扱い）。

## 3. CI の手動の実行（N の比べ、FR4.2・FR3.2・NFR1・NFR2・NFR4）

依頼者が作業ブランチをプッシュし、`gh workflow run ci.yml --ref feature/261108-ci-checks-release -f it_forks=<N>` で2回動かした（コミット `ded97b38`）。

| | 実行 | job | `./gradlew verify` | 結論 | 試行 |
|---|---|---|---|---|---|
| 前（`-PitForks` なし。Intent 261108-ci-size-coverage の `develop` のプッシュ） | 42489430268 | 35分47秒 | 34分44秒 | success | 1 |
| N=2 | 42535156503 | 29分07秒 | 28分27秒 | success | 1 |
| N=3 | 42535169021 | 21分39秒 | 20分52秒 | success | 1 |

- 選んだ N: 3（FR4.2 の選び方: NFR2 を満たし、job が最短。2 と 3 の差は約 7分28秒で 1 分以上）。既定の `IT_FORKS_DEFAULT: "3"` のままで、変える直しは要らない。
- 足した要約の手順（cloc・要約・HTML）は、どちらも合わせて 14 秒前後。

カバレッジの比べ（JaCoCo の HTML の報告の合計。未通過 / 全体）:

| | 命令 | 分岐 | 行 |
|---|---|---|---|
| 手元（N=3） | 1,868 / 78,989 | 591 / 6,966 | 343 / 15,258 |
| CI の N=2 | 1,868 / 78,989 | 591 / 6,966 | 343 / 15,258 |
| CI の N=3 | 1,876 / 78,989 | 591 / 6,966 | 344 / 15,258 |

- CI の N=3 だけ、`auth.repository` の `LoginAttemptStateRepository.java` 137 行（ダミーの行がすべてほかの試みに排他を持たれているときだけ通る行）が通らず、1 行ずれた。手元の N=3 と CI の N=2 では通っているため、並べる数ではなく、同時のログインの結合テストの競り合いのタイミングによる揺れと見ている（確かめていない見立て）。下限には響かない（`auth.repository` は 48/49）。依頼者の決定で受け入れて記録した。
- 画面のカバレッジ: N=2 は分岐 6,688 / 7,348、N=3 は 6,687 / 7,348（行・関数・文は同じ）。前の Intent でも見た ±1 の揺れ。
- テストの件数と失敗: どちらの実行も `verify` が通った（失敗 0）。件数は実行の要約（依頼者の画面）にある。

Ubuntu 26 の上の道具（FR3.2。N=3 の実行のログから）:

| 道具 | 版 |
|---|---|
| 実行環境 | ubuntu-26.04（イメージの版 20261101.186.1） |
| JDK | Temurin 25.0.5+8 |
| Node | 26.13.0 |
| Gradle | 9.8.2 |
| cloc | 2.06-1（apt） |
| Gitleaks・OSV-Scanner | 8.30.1・2.7.0（ci.yml で版と SHA-256 を固定。前と同じ） |
| 対象DB のコンテナ | ダイジェストで固定（前と同じ） |

どの道具も動き、job は通った。

## 4. 台本の確かめ（Code Generation の後に足したもの）

- A1 `scripts/ops-readonly-check.mjs`: colima を動かし、Mailpit と見本の PostgreSQL を動かした状態で流した。2つとも版とダイジェストが `compose.yaml` と一致し（食い違い 0 件、終了コード 0）、`.env` の値・ホームのパスは出なかった（「password」に当たったのは環境変数の名前だけ）。
- A2 `scripts/ci-attempts.mjs`: `git fetch` の後に既定の範囲（`origin/main..origin/develop` = `b2990415..4fe8e790`、23 コミット）で流した。CI の実行のある 7 コミットはどれも試行 1 で success、実行の無いコミット 16 件、再実行で通った実行 0 件、先頭は通っている（終了コード 0）。取り込みの直前に、統合と記録のコミットを積んだ後の `develop` でもう一度流す（FR6.2）。
- A3 `scripts/count-db-copy.mjs`: Code Generation で依頼者の許しを得て流し、前の道具と件数が一致した（`code-summary.md` 2節）。

## 5. 統合の後の CI の失敗と直し（段の承認の後、2026-11-09〜10 に追記）

- 統合（7コミット、`5b115bfc`）を依頼者がプッシュした `develop` の CI（実行 42633708198、試行 1、job 21分29秒、`-PitForks=3` の既定）が failure。A2 の台本も先頭が通っていないとして終了コード 1 を返した（取り込みの関門として働いた）。
- 落ちたのは `UserRowLockRepositoryIT > admin rows and the target row are locked one by one in ascending user id order` の1件だけ（162 行、`admin1` の行がまだ排他されていなかった）。この Intent で変えていない、前からある結合テスト。
- 見立て: 1つの `SELECT … FOR UPDATE` は H2 で ID の順に1行ずつ排他を取る。テストの待ち合わせ（`H2SessionWaits.awaitExecuting`）は文が実行中になった時点で戻るため、`admin1` を取る前に確かめる隙がある。結合テストを3つの JVM に並べて CPU が混み合い、表に出やすくなったと見ている（確かめていない見立て）。手元の N=3・CI の N=2・N=3 の手動の実行では通っていた。
- 依頼者の決定（A）で、`team.md` の「不安定なテストと CI の失敗」に従い、20 分を上限に再現を試みた: 8 本の `yes` で CPU に負荷をかけて `./gradlew :backend:cleanIntegrationTest :backend:integrationTest -PitTests=UserRowLockRepositoryIT` を 12 回流し、0 回の失敗（00:14〜00:24）。競り合いを確実に起こす手立ては見つからなかった（決まりの「起こせないとき」に当たるため、見立てと試みの範囲をここに記録する）。
- 直し（テストだけ、依頼者の承認を得た）: `H2SessionWaits` に、文がほかのセッションの排他を待って止まる（`INFORMATION_SCHEMA.SESSIONS` の `BLOCKER_ID` が入る）まで待つ `awaitBlocked` を足し、このテストだけを替えた（`awaitExecuting` とほかのテストは変えない）。止まった時点では、それより小さい ID の行の排他は取り終えているため、確かめがタイミングに左右されない。順の誤りは今までどおり検出できる。
- 直した後の確かめ: 同じ負荷で 8 回流して 0 回の失敗。`./gradlew verifyQuick -PitTests=UserRowLockRepositoryIT` が通った（4分14秒）。短いブランチ `fix/261108-ci-checks-release` から `develop` へ squash で戻した（`92792df1`）。全件の確かめは、プッシュの後の CI と A2 で行う。
