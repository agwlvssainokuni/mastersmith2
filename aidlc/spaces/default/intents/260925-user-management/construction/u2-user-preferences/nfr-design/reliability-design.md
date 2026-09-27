# Reliability Design — U2 利用者のプリファレンスとパスワードの変更（u2-user-preferences）

U2 の信頼性の設計です。承認済みの `construction/u2-user-preferences/nfr-requirements/reliability-requirements.md`（NFR5.2・NFR9.4・NFR9.8・NFR10.1〜NFR10.5）を満たす作りを決めます。出典の略号は `performance-design.md` と同じ。当面の配備先は開発者の PC 上のコンテナ1台で、再試行・サーキットブレーカー・フェイルオーバーは置きません（外部の依存が内部DB だけで、呼び出しはどれも同じ JVM の中のため）。

## 1. 監査の記録（NFR9.4・NFR9.5・BR7.2・BR7.3）

- 出来事 `PasswordChangedEvent`（`user.domain`、`logical-components.md` 1節）を、既存の `AuditEventListener` に受け取りの操作を1つ足して受ける。設定は既存の3つと同じ（`@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)`、最優先の順番）。記録は既存の `AuditEventRecorder`（`REQUIRES_NEW`）で1件追記する。
- 成功: 条件つきの更新と同じトランザクションの中で知らせる → 確定の後に記録。巻き戻ったとき・更新した行が 0 のときは知らせないため、記録されない（NFR9.8）。
- 今のパスワードの誤り: トランザクションの外で知らせる → `fallbackExecution` でその場で記録（BR7.3）。
- 書き込みの失敗: 既存のとおり受け止め、ERROR（固定の文「監査イベントの記録に失敗しました」、記録しようとした項目）を1回出し、元の応答（204・400）は変えない（NFR9.4）。ERROR の項目に targetUserId・targetInvitationId を足す（`security-design.md` 4節）。
- 監査の組み立て（`AuditEventFactory` に `from(PasswordChangedEvent)` を足す）: eventType PASSWORD_CHANGED、result、FAILURE のとき failureReason CURRENT_PASSWORD_MISMATCH、actorUserId と targetUserId に本人、sourceIp・userAgent・traceId は出来事の値、occurredAt は出来事の時計の値。ほかの列は空。
- 確かめ: 既存の `AuditWriteFailureIT` と同じ形で、成功と誤りの両方で記録を失敗させても応答が変わらないこと。`AuditRollbackIT` と同じ形で、成功のトランザクションを巻き戻したときに記録されないこと。

## 2. 同時のパスワードの変更（Q3 A）

- 新しいハッシュの書き込みは「読んだときのハッシュのままなら書き換える」条件つきの更新にする（`user.repository` の更新の問い合わせ、名前つきの引数）。

```sql
UPDATE users SET password_hash = :newHash
 WHERE user_id = :userId AND password_hash = :readHash
```

- 更新した行が 1: 成功（出来事を同じトランザクションで知らせる）。
- 更新した行が 0: 同じトランザクションの中で本人の行を読み直す。無ければ本人がいない（401）。あれば、照合の後にほかの変更が確定したとして今のパスワードの誤り（400 PASSWORD_CURRENT_MISMATCH）にし、トランザクションを終えた後に PASSWORD_CHANGED・FAILURE・CURRENT_PASSWORD_MISMATCH を知らせる（新しい code・理由は足さない）。
- 効果: 古いパスワードで照合を通った要求が、先に確定した新しいパスワードを黙って上書きしない。代わりに、2つの端末からほぼ同時に変えると、後の方は 400 になる（まれ。画面は今のパスワードの誤りとして出し、本人がやり直せる）。
- bcrypt の形式のハッシュは作るたびに塩が変わるため、同じパスワードへの変更（BR4.4）でも条件の値はぶつからない。
- 確かめ: 結合テストで、照合の後・書き込みの前に別の変更を確定させる（テストで差し替えられる待ち合わせの口を使う。本番のコードの流れは変えない）と、後の要求が 400 になり、先の新しいパスワードでログインでき、監査に SUCCESS 1件・FAILURE 1件が残ること。

## 3. 部分の書き換え（NFR9.8・BR3.3・BR3.4）

- 今の `User` は全列を持つエンティティで、変更の検出で更新すると全列を書く。プリファレンスの保存とパスワードの変更が重なると、先に読んだ古い値で相手の列を上書きしうるため、エンティティの変更の検出では書き換えない。
- プリファレンスの保存は4列だけ、パスワードの変更は password_hash だけを書き換える更新の問い合わせ（`@Modifying` の JPQL、名前つきの引数）を `user.repository` に置く。更新の後は持続化の文脈を消す（`clearAutomatically`）ため、同じトランザクションの中で古い値を読まない。
- 同じ4列どうしの同時の保存は後に確定した方が残る（BR3.4、版による排他は持たない）。
- 1つのトランザクションで確定するか何も変えないかのどちらか。検証を通らないときは DB に触れない（BR3.2）。内部DB の障害などの想定外の誤りは巻き戻り、既存の 500（内部の例外のメッセージを載せない）。
- 確かめ: 結合テストで、プリファレンスの保存とパスワードの変更を重ねても両方の結果が残ること、誤りの項目が1つでもあれば4列が変わらないこと。

## 4. 利用者の作成（BR5.2・BR5.3、C2）

- `createUser` は呼び出し元のトランザクションに参加する。登録済みかを bcrypt の前に確かめ、登録済みなら EmailAlreadyUsed を返す。同時の作成で一意の制約（`uk_users_email`）に当たったときも、例外を受け止めて EmailAlreadyUsed を返す。
- 一意の制約に当たると、トランザクションは巻き戻しの印を持つ（リポジトリの操作が参加したトランザクションの既定）。そのまま `createUser` が正常に戻ると、`createUser` 自身がトランザクションを始めた場合（初期管理者の作成）には確定のときに `UnexpectedRollbackException` になる。これを防ぐため、`createUser` は EmailAlreadyUsed を返すとき（登録済みの読み取りで分かった場合も含めて）今のトランザクションに自分で巻き戻しの印を付ける（`TransactionStatus#setRollbackOnly`）。自分で始めたトランザクションなら例外なしで巻き戻り、呼び出し元のトランザクションに参加しているなら呼び出し元が巻き戻す。
- 呼び出し元は EmailAlreadyUsed のとき必ず巻き戻す前提とし、この前提を `createUser` の Javadoc に書く。U3 は BR7.4 で必ず巻き戻す。初期管理者の作成（`InitialAdminInitializer`）は、今の `DataIntegrityViolationException` の受け止めを結果の型の判定に置き換える（ログの文は今のまま）。
- 確かめ: 結合テストで、同じメールアドレスの2つの作成を重ねると、片方が Created・片方が EmailAlreadyUsed で、利用者は1人だけ、UserCreatedEvent は1回だけ（ロックの状態の行が1つ）。

## 5. 2本目の接続（NFR5.2・NFR5.3）

- 作りは既存のまま（AFTER_COMMIT の受け取りの中で `REQUIRES_NEW` の2本目を借りる）。成功のパスワードの変更だけが最大2本、ほかの操作は1本。bcrypt の間は0本（`performance-design.md` 2節）。
- 接続を借りる待ちの時間切れ（5 秒）が監査の記録で起きると、その記録は欠け、ERROR が1回出る（既知の制約、README の「監査ログ（U4）」）。
- 確かめは performance-validation の k6 で、使い捨ての環境にだけ `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics` を渡し、hikaricp の待ちの時間切れの累計が 0、借りるまでの待ちの最大が 5 秒より十分小さいこと、流した成功の件数と監査の PASSWORD_CHANGED・SUCCESS の件数が一致することを見る（`project.md` の Testing Posture）。

## 6. スキーマの変更 V7 と後方互換（NFR10.1〜NFR10.4、Q4 A）

### 6.1 V7 の作り

1つのファイル `V7__...sql` にまとめ、V1〜V6 は書き換えない（NFR10.1、Flyway の `validate-on-migrate` でチェックサムの変化を検知）。中の順序:

1. users に language・theme・fontSize を、既定の値（ja・system・md）つき・必須で足す（既存の行にも既定の値が入る。1つ前の版のアプリの追記でも入る）。
2. users に display_name を空を許す VARCHAR（254 コードポイントを収める長さ）で足す。
3. 既存の行の display_name に email を入れる（BR2.2）。
4. display_name を必須にする。既定の値は置かない（承認どおり、BR9.1）。
5. audit_events に target_user_id・target_invitation_id を空を許す BIGINT で足す。参照の制約と索引は置かない（BR9.2）。

H2 は DDL をトランザクションで巻き戻せないため、V7 の途中で失敗すると一部だけ当たった状態が残りうる。失敗したときは既存の Flyway の扱いで起動を止め、配備の段の手順（NFR10.5 のバックアップから戻す）で戻す。

### 6.2 確かめの2段（Q4 A）

| 段 | 確かめること | 作り | 持ち主の段 |
|---|---|---|---|
| (1) 自動の結合テスト | V7 まで当てた内部DB に対して、V6 までしか知らない Flyway（移行の置き場をテストの資源の V1〜V6 の複写に向ける）の `validate` と `migrate` が失敗しないこと（Flyway 12 の既定が、適用済みで知らない新しい移行を無視することの確かめ） | 組み込みの H2 の一時のファイルに今の Flyway で V7 まで当て、同じ DB に V6 までの設定の Flyway を当てる | code-generation |
| (1) 同上 | 既知の限界の固定: 足した4列を渡さない users の追記（1つ前の版の作成と同じ形）は display_name が無いため失敗し、language・theme・fontSize は既定の値で入ること。足した2列を渡さない audit_events の追記（1つ前の版の監査の記録と同じ形）は通ること | 同じ DB に JDBC で追記する | code-generation |
| (1) 同上 | V6 までを当てた内部DB に利用者と監査の行を入れてから V7 を当てると、利用者に氏名＝email・ja・system・md が入り、監査の2列は空のまま。起動し直しても V7 は1回だけ当たる（NFR10.2） | 同上 | code-generation |
| (2) 戻しの練習 | 1つ前の版のイメージ（この Intent の前の develop の版）を、V7 を当てた後の内部DB の複写で起動し、健全性（/actuator/health の 200）・ログイン・トークンの更新・監査の記録が今までどおり動くこと。利用者がいる内部DB のため初期管理者の作成が動かないこと（起動のログの「既にいるため、作成しませんでした」）。Hibernate の `validate` が余分な列を許すことも、ここで確かめる | 配備した内部DB を壊さないよう複写で行う。手順は deployment-pipeline で書く | deployment-pipeline・deployment-execution |

- Hibernate の `validate` が余分な列を許すことは、自動のテストでは1つ前の版のエンティティを持てないため (2) で確かめる。(1) で見込みが外れたと分かったら、コード生成の計画の中で依頼者に諮る（作りを変える場合は承認済みの BR9.1 との差になる）。
- 既知の限界（NFR10.4）: 1つ前の版が利用者を作るのは、利用者が1人もいないときの初期管理者の作成だけ。V7 を当てた内部DB には必ず利用者がいるため、戻したときに起きない。戻しの手順（NFR10.5）に「空の内部DB で1つ前の版を起動するときは、V7 を当てていない内部DB を使う」を書く。

## 7. 障害のときのふるまい

| 障害 | ふるまい | 設計 |
|---|---|---|
| 監査の書き込みの失敗 | 元の応答のまま、ERROR を1回 | 1節 |
| 接続の待ちの時間切れ（業務の1本目） | 既存の 500、トランザクションは巻き戻る | 3節・5節 |
| 接続の待ちの時間切れ（監査の2本目） | 変更は確定済み、監査が欠け ERROR | 5節 |
| 照合の後の同時の変更 | 後の要求は 400 PASSWORD_CURRENT_MISMATCH、失敗を監査 | 2節 |
| 認証の後に本人の行が消えた | 401 AUTHENTICATION_REQUIRED | `security-design.md` 2節 |
| V7 の適用の失敗 | 起動を止める、バックアップから戻す | 6節 |

## 8. 上流との差

| ID | 上流 | 上流の記載 | この段の設計 | 理由と扱い |
|---|---|---|---|---|
| R-D1 | 機能設計 `rules.md` の BR3.4・BR4.3 | パスワードの変更は password_hash だけを書き換える。パスワードどうしの同時の変更の扱いは決めていない | 読んだときのハッシュのままなら書き換える条件つきの更新にし、変わっていれば 400 PASSWORD_CURRENT_MISMATCH と失敗の監査にした（2節） | 依頼者の決定（Q3 A）。BR4.2 の「今のパスワードが誤っていれば拒否」を、照合の後に今のパスワードが変わった場合にも当てたもの。新しい code・監査の理由は足さない。機能設計の文書は書き換えない |
| R-D2 | NFR10.3（`nfr-requirements/reliability-requirements.md`） | 確かめる作りと手順は NFR 設計・基盤の設計で決める | 自動の結合テスト（Flyway の無視・既知の限界・V7 の値）と、戻しの練習での1つ前の版の起動の2段にした（6.2） | 依頼者の決定（Q4 A）。基盤の設計（infrastructure-design）は戻しの練習の環境（内部DB の複写の置き場）だけを扱えばよい |
