# Reliability Design — U4 監査ログ（u4-audit-log）

U4 の信頼性の要件（`reliability-requirements.md` の NFR10.1、NFR10.2、NFR9.1、NFR9.2）を満たす設計。処理の流れは U4 の `functional-spec.md`（WF1〜WF3）、決まりは U4 の `rules.md`、技術は U4 の `tech-stack-decisions.md` に従う。性能・安全・拡張性・観測性の要件（`performance-requirements.md`・`security-requirements.md`・`scalability-requirements.md`・`observability-requirements.md`）は各設計書で扱う。設計の方針の確認は `nfr-design-questions.md` にある。`contract-summary.md` は、本ワークフローで Contract Design を行わないため無い。

## 1. 受け取りの2つの経路（NFR10.2）

| 経路 | 出来事 | 受け取り方 | 元の操作が取り消されたとき |
|---|---|---|---|
| 確定の後 | ログイン成功・ログイン失敗・ログアウト（U2 のトランザクションの中で知らせる） | 確定の後に受け取る仕組み（`AFTER_COMMIT`） | 受け取りが呼ばれず、記録しない（BR1.4） |
| その場 | アクセス拒否（U3 の 401／403 の処理の中、トランザクションの外で知らせる） | 同じ受け取りの仕組みで、トランザクションが無いときも受け取る設定 | 取り消しの対象の更新が無い |

- どちらの経路も、知らせた要求と同じスレッドで受け取る（トレースIDの一致のため。`observability-design.md`）。
- 書き込みは、どちらの経路でも「新しいトランザクション」として始める（元のトランザクションに加わらない）。確定の後の経路では元のトランザクションは既に確定しており、加わると書き込みが確定されないため、新しいトランザクションが必須である。
- テスト: 元の操作を取り消した場合（ログインの失敗回数の更新が失敗した場合）に記録が無いこと。

## 2. 失敗を持ち込まない（NFR10.1）

```text
onEvent(event):
  try {
    newTransaction { repository.append(AuditEvent.from(event, truncate)) }
  } catch (Exception e) {
    log.error(fixed message, all fields as key-values, exception type)
    // 呼び出し元へ伝えない。再試行しない
  }
```

- 例外は U4 の中で必ず捕まえる。確定の後の経路ではフレームワークも例外を受け止めるが、その場の経路では呼び出し元（U3 の 401／403 の処理）へ伝わるため、フレームワークの受け止めに頼らない（U4 の `tech-stack-decisions.md`）。
- 捕まえる範囲は、トランザクションの開始（接続を借りる失敗）・監査イベントの組み立て・追記・確定のすべて。
- ERROR のログは1件の失敗につき1回（`observability-design.md`）。再試行はしない（BR3.1）。
- U3 も念のため例外を捕まえる（U3 の `nfr-design/reliability-design.md` 1章）が、U4 はそれに頼らない。
- テスト: 2つの経路それぞれで、追記の失敗（DB の例外）と接続を借りる失敗を起こし、ログイン・ログアウト・401／403 の応答が変わらないこと、ERROR が1回出ること。

## 3. 監査の記録とアプリのログを分ける（NFR9.2）

- 監査イベントは内部DBの監査イベントの表にだけ記録し、アプリのログを記録の代わりにしない（BR3.2）。
- アプリのログに監査イベントを出すのは、書き込みに失敗したとき（ERROR）だけ。成功したときは出さない（二重の記録にしない）。
- テスト: 各出来事で、監査イベントの表に行が1件増えること。

## 4. 必須のテスト（NFR9.1）

| テスト | 内容 |
|---|---|
| 必須の項目 | LOGIN_SUCCEEDED・LOGIN_FAILED・LOGGED_OUT・ACCESS_DENIED のそれぞれで、決まりの項目がそろって記録される |
| 存在しないメールアドレス | 存在しないメールアドレスでのログインの失敗が、入力されたメールアドレスと理由 USER_NOT_FOUND で記録される |
| 失敗しても操作は失敗しない | 2章のテスト |
| 取り消し | 1章のテスト |
| 切り詰め | 300 文字のメールアドレス、サロゲートペアの途中で切れる User-Agent（性質ベースのテスト（jqwik）で、切り詰めの結果が壊れた文字を含まず上限以下であることを確かめる） |
| トレースID | 同じ要求のアプリのログと監査イベントのトレースIDが一致する |
