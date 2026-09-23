# 戻し方の手順書（rollback-runbook）

Intent `260922-auth-audit-base`（auth-audit-foundation）の、配備した版を直前の版に戻す手順。配備先は開発者の PC 上のコンテナだけ（`cd-config.md` 2節）。決定は `deployment-pipeline-questions.md` の Q3（WAR からイメージを作り直す）・Q5（直前の版のコミットを取り出して手元で作り直す）・Q6（コミットのハッシュを控える）にある。入力は `construction/ci-pipeline/ci-config.md`（ci-config）、`construction/ci-pipeline/quality-gates.md`（quality-gates）、各単位の `infrastructure-design/infrastructure-specification.md`（infrastructure-specification）と `infrastructure-design/cicd-pipeline.md`（cicd-pipeline）である。

## 1. 戻すと決める基準

`deployment-strategy.md` 4節の中止の条件に当たり、設定の誤りでは説明できないとき、直前の版に戻す。

| きっかけ | 例 |
|---|---|
| 健全にならない | 約 2 分たっても `healthy` にならない、`unhealthy` になる |
| 起動が失敗する | Flyway のスキーマの変更が失敗して止まる、起動のログに ERROR が出て止まる |
| スモークテストが通らない | ログインできない、管理者向け領域が開けない、監査イベントが記録されない |
| 配備の後に不具合が見つかった | 新しい版だけで起きる誤りの応答、ログの ERROR の増加 |

戻すかどうかは依頼者が決める。AI-DLC の作業の中で AI が戻す場合は、依頼者の承認を得てから行う。

## 2. アプリの版を戻す（データはそのまま。通常はこれ）

直前の版のコミットのハッシュは、前回の配備の手順 2（`deployment-strategy.md` 2節）で控えたものを使う（Q6）。控えが無いときは、`git log --oneline` で前回の配備の版を探す。

| 順 | 手順 | コマンドの例 |
|---|---|---|
| 1 | 今のアプリを止める | `docker compose stop app` |
| 2 | 今のボリュームを複写する（戻した後に調べるため） | README の「内部DBのバックアップと戻し方」の `tar czf …` |
| 3 | 直前の版のコミットを別の場所に取り出す（Q5） | `git worktree add ../mastersmith-rollback <直前の版のハッシュ>` |
| 4 | 取り出した場所でサブモジュールを取得する | `(cd ../mastersmith-rollback && git submodule update --init)` |
| 5 | 取り出した場所で WAR を作る | `(cd ../mastersmith-rollback && ./gradlew :backend:bootWar)`（画面のビルドを含む。Node.js 24 が要る） |
| 6 | 作った WAR を、配備に使う場所へ複写する | `cp ../mastersmith-rollback/backend/build/libs/mastersmith.war backend/build/libs/mastersmith.war` |
| 7 | イメージを作り直して起動する（Q3。タグは `local` のまま） | `docker compose up -d --build` |
| 8 | 健全になるまで待つ | `docker compose ps` で `healthy` |
| 9 | スモークテストを行う | `deployment-strategy.md` 3節と同じ項目 |
| 10 | 取り出した場所を片付ける | `git worktree remove ../mastersmith-rollback` |
| 11 | 戻した版を記録する | 直前の版のハッシュが、いま動いている版の記録になる |

- 手順 5 は `./gradlew verify` ではなく `:backend:bootWar` でよい。直前の版は、配備したときに `./gradlew verify` を通っている（`deployment-strategy.md` 2節の手順 3）。
- 手順 6 で手元の `backend/build/libs/mastersmith.war` は上書きされる。新しい版の WAR が要るときは、新しい版で `./gradlew verify` をやり直して作る。
- 作業ツリーはそのまま（新しい版のコミット）でよい。取り出した場所は別のディレクトリのため、作業ツリーを切り替えない。

## 3. スキーマは戻さない

- スキーマの変更は前進のみ・後方互換のため、直前の版のアプリは今のスキーマ（新しい版が当てた変更を含む）で動く（`deployment-strategy.md` 5節、U1〜U4 の `cicd-pipeline.md`）。スキーマを元に戻す手順は持たない。
- 直前の版は、自分が知らない新しいスキーマの変更が当たった DB で起動することになる。Flyway は、アプリが知らない新しい変更を既定で無視し、Hibernate は自分の表と列だけを確かめる（`ddl-auto: validate`）。この前提で起動できることは、deployment-execution の段で実際に確かめる（「Assumptions & Open Questions」）。

## 4. データも戻す（データが壊れたときだけ）

新しい版がデータを壊した（誤った書き込みなど）ときだけ、2節の後にデータも戻す。**戻したバックアップの後に記録された利用者の変更と監査イベントは失われる**ため、依頼者の判断で行う。

| 順 | 手順 |
|---|---|
| 1 | 2節でアプリの版を戻す |
| 2 | README の「内部DBのバックアップと戻し方」の戻す手順で、配備の前に取ったバックアップ（`deployment-strategy.md` 2節の手順 4）を展開する。所有者を 10001 に戻す（`chown -R 10001:10001 /data`） |
| 3 | `docker compose start app` で起動し、`healthy` とスモークテストを確かめる |

2節の手順 2 で取った「戻す直前のボリューム」は、失われる記録を後から調べるために残しておく。バックアップにはメールアドレスと接続元IPが入るため、実行の利用者と管理する者だけが読める場所に置く（README の「監査ログの確かめ方」）。

## 5. してはいけないこと

| 操作 | 理由 |
|---|---|
| バックアップの前にボリュームを消す（`docker compose down -v` など） | 監査ログを含む内部DBがすべて消える（U4 の `infrastructure-specification.md` 3章） |
| `backend/build/libs/mastersmith.war` を、未コミットの変更を含む作業ツリーで作った WAR にする | どの版が動いているか分からなくなる（Q6） |
| スキーマの変更のファイル（`V*__*.sql`）を書き換えて戻そうとする | Flyway の確認で起動が止まる。スキーマは前進のみ |
| 戻すために署名鍵を替える | 発行済みのトークンがすべて無効になる。鍵の交換は戻しとは別の操作（U2 の `cicd-pipeline.md` 3章） |

## 6. 戻し方の練習

確かめていない戻し方は、戻し方として当てにできない。この手順は deployment-execution の段で、配備と合わせて一度通して実行し、次の2つを確かめる。

- 2節の手順で直前の版に戻り、スモークテストが通ること
- 直前の版が、新しい版が当てたスキーマの変更を含む DB で起動できること（3節）

## Sources

- `aidlc/spaces/default/intents/260922-auth-audit-base/operation/deployment-pipeline/deployment-pipeline-questions.md`（Q3・Q5・Q6）
- `aidlc/spaces/default/intents/260922-auth-audit-base/operation/deployment-pipeline/cd-config.md`、`deployment-strategy.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/ci-pipeline/ci-config.md`（CI の成果物は戻しに使わないことの対比）、`quality-gates.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u1-app-skeleton/infrastructure-design/infrastructure-specification.md`・`cicd-pipeline.md`（戻し方・データの保護）
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u2-authentication/infrastructure-design/infrastructure-specification.md`・`cicd-pipeline.md`（鍵の交換）
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u3-access-control/infrastructure-design/infrastructure-specification.md`・`cicd-pipeline.md`
- `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u4-audit-log/infrastructure-design/infrastructure-specification.md`・`cicd-pipeline.md`（ボリュームの削除の注意）
- `backend/src/main/resources/application.yaml`（Flyway・Hibernate の設定）、`README.md`

## Assumptions & Open Questions

- [assumption] 直前の版のアプリが、新しい版の当てたスキーマの変更を含む DB で起動できること（Flyway が知らない新しい変更を既定で無視すること）は、設定と Flyway の既定の振る舞いからの見込みで、まだ試していない。deployment-execution で確かめる。
- [assumption] `git worktree` で取り出した場所でのビルドには、`vendor/make-you-chic-ui` のビルドと `npm ci` のための時間とネットワークが要る。戻しにかかる時間は、deployment-execution で一度測る。
