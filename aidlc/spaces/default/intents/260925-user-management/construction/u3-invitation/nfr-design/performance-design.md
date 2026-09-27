# Performance Design — U3 招待と登録の完了（u3-invitation）

U3 の性能と接続の使い方の設計です。承認済みの `construction/u3-invitation/nfr-requirements/performance-requirements.md`（NFR5.1・NFR5.2・NFR6.1〜NFR6.6）を満たす作りを決めます。

出典の略号: NFR はこの単位の NFR 要件の枝番、BR は `construction/u3-invitation/functional-design/rules.md`、Q1〜Q5 と「要点 n」はこの段の `nfr-design-questions.md`、C1〜C10 は `inception/contract-design/contract-summary.md`、U1・U2・U8 の文書は同じ段のそれぞれの `nfr-design/` の下。

## 1. 時間の見積もりと考え方

| 操作 | 重い処理 | 内部DB の接続を持つ間 | 目標 |
|---|---|---|---|
| 招待（201） | SMTP の送信1回（受け手が正常なら数十〜数百 ms） | 確定の短いトランザクション、確定の後の監査で2本目、送信の間は0本、結果の記録の短いトランザクション | 同時 10 件の p95 5 秒（NFR6.1） |
| 送り直し（200） | 招待と同じ | 招待と同じ | 同時 10 件の p95 5 秒（NFR6.1） |
| 受け手が応答しない・拒む | U1 の時間切れ（3 秒）1回 | 送信の間は0本 | 時間切れが1回で済む場合は1件で 5 秒以内（NFR6.2） |
| 一覧（200） | なし | 読み取り2回（20 件と件数）＋管理者の氏名の読み取り（異なる ID の数だけ） | p95 1 秒（NFR6.3） |
| 取り消し（204） | なし | 排他の読み取りと更新、確定の後の監査で2本目 | p95 1 秒（NFR6.3） |
| リンクの確かめ（200・404） | なし（SHA-256 1回） | 読み取り2回（招待、登録済みか） | p95 1 秒（NFR6.3） |
| 登録の完了の成功（204） | bcrypt 1回（約 278 ms） | 1つのトランザクションで排他・利用者の作成・完了（bcrypt の間も持つ）、確定の後の監査で2本目 | p95 1 秒（NFR6.4） |
| 登録の完了の入力の誤り・リンクの拒否（400・404） | なし | 入力の誤りは0本、リンクの拒否は読み取り1回と監査の記録1回 | p95 1 秒（NFR6.5） |
| 登録の完了の同じメールアドレスの利用者がいる拒否（BR7.4） | ふつうは無し（4節） | 登録の完了と同じ | 目標の対象外、Unverified（4節） |

- 招待・送り直しの応答時間の大半は SMTP の送信で決まる。送信の間は接続を持たない（2節）ため、SMTP の遅さは接続プールの待ちに波及しない。
- 登録の完了の応答時間の大半は bcrypt の CPU の時間で決まる。承認どおり bcrypt の間も接続を持つ（NFR5.2）。
- キャッシュは置かない。招待の行は利用者の数の程度（最大でも数百行）で、どの読み取りも主キー・一意の索引・20 件の範囲で済み、キャッシュの無効化の複雑さに見合う得が無い。

## 2. 招待・送り直しの流れ（NFR5.1・NFR6.1・NFR6.2、ADR-009、要点 1・2）

取りまとめの業務処理（`InvitationService`）には `@Transactional` を付けない。2つの短いトランザクションは、既存の `LoginService`・`RefreshTokenCleanupJob` と同じく `TransactionTemplate` で囲む（同じクラスの中の呼び出しでは `@Transactional` が効かないため）。

```
invite(actor, origin, email, language):
  errors = validate(email, language)          // DB を使わない（BR1.1・BR1.2）
  requireAvailable()                          // 起動時に決めた InvitationAvailability（BR1.5）
  issued = tx { lockPendingOrInsert(...) ; publish(InvitationIssuedEvent) }   // 確定 → AFTER_COMMIT で監査
  url = RegistrationUrl.of(baseUrl, issued.token)    // ベース URL だけから（BR3.4）
  result = sendOutsideTransaction(issued, url)       // U1 の send を1回、接続0本（Q4 A の確かめつき）
  tx { recordSendResult(issued.id, issued.tokenHash, result) }   // 条件つきの更新1回（BR4.3）
  return summary(issued, result)
```

- 送り直しも同じ形で、1つ目のトランザクションが「対象の行を排他して読み、トークン・有効期限・送信の結果を置き換える」になる（BR6.1）。
- 送信の結果の記録は、`invitation_id`・`token_hash`（送ったトークンのハッシュ）・`send_result = 'PENDING'` を条件に含む更新の問い合わせ1回とする。更新した行が 0 なら何もしない（同時の送り直しの新しい結果を古い結果で上書きしない、BR4.3）。
- 記録のトランザクションが内部DB の障害で失敗したときは、機能設計の3節のとおり既存の 500 の扱いにする。行は PENDING のまま一覧では FAILED に見え、管理者が送り直せる（BR4.4）。
- 応答の `sendResult` は、この要求の送信の結果（SENT・FAILED）をそのまま返す。記録の更新が 0 行（同時の送り直しがあった）でも、この要求の結果を返す。

### 2.1 送信の間に接続を持たないことの守りと確かめ（Q4 A）

| 守り・確かめ | 内容 |
|---|---|
| 送信の入口の確かめ | 送信を呼ぶ部品（例: `InvitationMailDispatcher`、`invitation.service`）は、入口で `TransactionSynchronizationManager.isActualTransactionActive()` を確かめ、真なら想定外の誤り（`IllegalStateException`、既存の 500 の扱い）として止める。後の変更で送信がトランザクションの中に入ったとき、最初の実行で気づけるようにする |
| 結合テスト (1) | 受け付けて何も返さない `ServerSocket` を受け手にした送信の最中に、待ち合わせて接続プールの使用中の数（`HikariDataSource#getHikariPoolMXBean().getActiveConnections()`、MBean の登録なしで読める）が 0 であることを確かめる |
| 結合テスト (2) | 承認どおり、同じ送信の最中に一覧の API が時間切れを待たずに応答することを確かめる |
| 単体テスト | 送信の入口の確かめが、トランザクションの中で呼ばれたときに止まることを確かめる |

```java
MailSendResult dispatch(IssuedInvitation issued, RegistrationUrl url) {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
        throw new IllegalStateException("招待メールの送信はトランザクションの外で行う");
    }
    return mailSender.send(requestFor(issued, url));   // U1 の C1、1回だけ
}
```

- テストの待ち合わせは、テストの中の同期の仕組み（受け手の `ServerSocket` が接続を受け付けたことの知らせ）で行い、`sleep` と実時刻に頼らない（`team.md` の Testing Posture）。受け手の時間切れはテストの設定で短い値にする。

## 3. 登録の完了の流れ（NFR5.2・NFR6.4・NFR6.5、BR7.2〜BR7.4、要点 3・4）

```
complete(origin, request):
  errors = validate(request)                  // U2 の DisplayName・表示の設定の値・PasswordPolicy、DB を使わない（BR7.2）
  if errors: throw VALIDATION_FAILED(fieldErrors)   // 招待を消費しない、監査しない
  token = InvitationToken.parse(request.token)      // 形の誤りは DB を引かない（BR3.2）
  outcome = tx(status -> {
      inv = lockByTokenHash(token.hash())            // PESSIMISTIC_WRITE、待ち 3 秒
      if not valid(inv, clock): status.setRollbackOnly(); return Rejected(reason)
      created = userAccount.createUser(newUser)      // C2、同じトランザクション、bcrypt
      if created is EmailAlreadyUsed: status.setRollbackOnly(); return Rejected(EMAIL_ALREADY_REGISTERED)
      markCompleted(inv, created.userId); publish(RegistrationCompletedEvent); return Completed })
  if outcome is Rejected: publish(RegistrationFailedEvent)  // トランザクションの外、その場で記録
                          throw REGISTRATION_LINK_INVALID   // 理由によらず同じ 404（BR7.5）
```

- 形の合わないトークンは、トランザクションを始めずに拒否の経路へ進む（理由 INVITATION_NOT_FOUND、BR3.2・BR7.6）。
- 拒否が決まったら、`TransactionTemplate` の中で自分の `TransactionStatus#setRollbackOnly` を付けてから抜ける。`createUser` が EmailAlreadyUsed で付けた印（U2 の `reliability-design.md` 4節）は参加したトランザクション全体の印になり、呼び出し元が自分の印を付けずに確定しようとすると `UnexpectedRollbackException` になる。自分の印があると、確定の処理は例外なしで巻き戻す（`reliability-design.md` 3節）。
- 接続は、入力の誤りとトークンの形の誤りでは借りない。拒否ではトランザクションの1本（巻き戻し）の後に監査の記録の1本（`REQUIRES_NEW`、その場で）で、同時に2本は持たない。

## 4. BR7.4 の経路の性能（承認の場の決定、NFR6.5、要点 5）

- U2 の `createUser` は、メールアドレスをそろえた後、bcrypt の前に登録済みかを読み取り1回で確かめる（U2 の `performance-design.md` 5節）。そのため、登録の完了の時点で同じメールアドレスの利用者がいる拒否（BR7.4）は、ふつう bcrypt を計算しない。同時の作成で一意の制約に当たる場合だけ、bcrypt の後に決まる。
- 承認の場の決定（2026-09-27）のとおり、この経路は NFR6.5 の目標から外したまま **Unverified** とし、performance-validation の k6 の場面に入れない。同時の登録でしか起きないまれな場合で、場面を作るには同じメールアドレスの利用者を別の経路で先に作る必要があり、目標の判定に意味のある数を流せないため。
- 結合テストでは正しさだけを確かめる: 巻き戻し（利用者が増えない・招待が PENDING のまま）、同じ 404 の本文、監査の REGISTRATION_FAILED・EMAIL_ALREADY_REGISTERED（`reliability-design.md` 3節）。

## 5. 一覧・取り消し・リンクの確かめ（NFR6.3、要点 7）

| 操作 | 問い合わせ |
|---|---|
| 一覧 | PENDING の行を `invited_at` の降順・`invitation_id` の降順に並べ、`(page−1)×20` から 20 件を読む問い合わせ1回と、PENDING の件数の問い合わせ1回（読み取り専用のトランザクション）。招待した管理者の氏名は、ページの中の異なる `invitedByUserId` ごとに C2 の `findDisplayName` を1回ずつ引く（多くても 20 回、管理者は少ないためふつうは1〜2回）。契約 C2 は変えない |
| 取り消し | 対象の行を主キーで排他して読み（待ち 3 秒）、状態と終わった日時を書き換えて確定 |
| リンクの確かめ | トークンの形を確かめ（DB なし）、ハッシュの一意の索引で1行を読み、C2 の登録済みの確かめを1回。内部DB を変えない |

- 索引は、トークンのハッシュの一意（`uk_invitations_token_hash`）と、招待中の一意（生成列 `pending_email`、`reliability-design.md` 2節）の2つだけとする。並べ替え・定期の削除のための索引は、行が利用者の数の程度のため足さない。招待した管理者への参照の制約には H2 が索引を自動で作る。

## 6. 行の排他の待ち（BR6.4、要点 6）

- 送り直し・取り消し・登録の完了の対象の行と、招待の同じメールアドレスの PENDING の行の排他の読み取りは、既存の `LoginAttemptStateRepository` と同じく `LockModeType.PESSIMISTIC_WRITE` と待ちの上限 3 秒（`jakarta.persistence.lock.timeout`）で行う。
- いちばん長く排他を持つのは登録の完了で、bcrypt の約 0.3 秒（同時の要求では CPU の待ちで p95 1 秒程度まで）である。3 秒に収まる見込みのため、待ちの時間切れは想定外として既存の 500 の扱いにする（業務の拒否の応答にしない）。

## 7. 負荷の試験へ引き継ぐこと（performance-validation）

- 場面は承認どおり、招待・送り直し・一覧・取り消し・リンクの確かめ・登録の完了の成功・入力の誤り・リンクの拒否に分け、場面ごとに p95 を判定する。受け手は Mailpit（compose の profile `mail`）で、トークンの用意の方法は手順書を書く段で決める（NFR 要件の「測り方の決まり」のとおり）。
- BR7.4 の経路は場面に入れない（4節）。
- 接続が尽きないことの確かめ（NFR5.3）は `reliability-design.md` 5節。
- bcrypt の cost は既定の 12 のまま変えない（NFR6.6）。目標に届かないときは目標を緩めず、原因を確かめて依頼者に相談する。

## 上流との差

| ID | 上流 | 上流の記載 | この設計 | 理由と扱い |
|---|---|---|---|---|
| PD-D1 | `performance-requirements.md` の NFR6.5 | BR7.4 の拒否は bcrypt を計算しうるため NFR6.4 と同じ重さとし、NFR6.5 の対象から外す | 対象から外したまま Unverified とし、性能の場面に入れず、正しさだけを結合テストで確かめる。U2 の `createUser` の作りで、ふつうは bcrypt を計算しないことを記録した | 承認の場の決定（2026-09-27 Approve の Minor）。要件の文書は書き換えない |
| PD-D2 | `performance-requirements.md` の NFR5.1 | 確かめ方は「送信の最中にほかの API が時間切れを待たずに応答する」結合テスト | 使用中の接続が 0 であることの結合テストと、送信の入口のトランザクションの外の確かめ（本番のコード）を足した（2.1） | Q4 A。接続が 30 本あるため、一覧の応答だけでは接続の持ち続けを見落としうる。要件より確かめを足す向きの差 |
