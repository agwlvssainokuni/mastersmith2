# Performance Design — U2 利用者のプリファレンスとパスワードの変更（u2-user-preferences）

U2 の性能の設計です。承認済みの `construction/u2-user-preferences/nfr-requirements/performance-requirements.md`（NFR5.1・NFR6.1〜NFR6.6）を満たす作りを決めます。

出典の略号: NFR はこの単位の NFR 要件の枝番、BR は `construction/u2-user-preferences/functional-design/rules.md`、Q1〜Q4 と「要点 n」はこの段の `nfr-design-questions.md`、C2〜C8 は `inception/contract-design/contract-summary.md`。

## 1. 時間の見積もりと考え方

| 操作 | 重い処理 | 内部DB の接続を持つ間 | 目標 |
|---|---|---|---|
| プリファレンスの取得 | なし（1行の読み取り） | 読み取り1回 | p95 1 秒（NFR6.1） |
| プリファレンスの保存 | なし（DB の外の検証と4列の更新） | 更新1回（検証を通ったときだけ） | p95 1 秒（NFR6.2） |
| パスワードの変更の成功 | bcrypt 2回（照合・新しいハッシュ、各 約 278 ms） | ハッシュの読み取り1回と条件つきの更新1回、確定の後の監査で2本目 | p95 2 秒（NFR6.3） |
| パスワードの変更の今のパスワードの誤り | bcrypt 1回（照合） | ハッシュの読み取り1回、監査の記録1回（トランザクションの外） | p95 1 秒（NFR6.4） |
| パスワードの変更の入力の誤り | なし | なし（DB を使わない） | p95 1 秒（NFR6.4） |
| ログイン・更新の応答の広げ | なし（既存の読み取りの値を使う） | 増えない | 前の目標のまま（NFR6.5） |

- 応答時間の大半は bcrypt の CPU の時間で決まる。bcrypt の間は接続を持たない（2節）ため、接続の待ちが応答時間に加わるのは、プールが尽きかけたときだけにする。
- キャッシュは置かない。取得は要求ごとに内部DB から読む決まり（BR3.1）で、1行を主キーで読むだけのため、キャッシュの無効化の複雑さに見合う得が無い。

## 2. パスワードの変更の流れ（NFR5.1・NFR6.3・NFR6.4、Q3 A）

```
changePassword(userId, origin, currentPassword, newPassword, confirmation):
  errors = validate(...)                        // DB を使わない（BR4.1）
  if errors: return Invalid(fieldErrors)
  hash = readHash(userId)                       // 短い読み取りのトランザクション → 接続を返す
  if hash is empty: return UserNotFound
  if !fitsMaxBytes(current) or !matches(current, hash):   // bcrypt、接続なし
      publish(PasswordChangedEvent FAILURE)     // トランザクションの外 → 監査はその場で記録
      return CurrentMismatch
  newHash = encode(newPassword)                 // bcrypt、接続なし
  tx { rows = updateHashIf(userId, hash, newHash); if rows==1: publish(SUCCESS) }
  if rows==0: 読み直して UserNotFound か CurrentMismatch（reliability-design.md 2節）
  return Changed
```

- 業務処理は結果の型で返し、`user.web` が Invalid を 400 VALIDATION_FAILED（`fieldErrors` つき）、CurrentMismatch を 400 PASSWORD_CURRENT_MISMATCH、UserNotFound を 401 AUTHENTICATION_REQUIRED、Changed を 204 に変える（`security-design.md` 2節・3節）。

- 接続を持つのは `readHash` と条件つきの更新の短い間だけで、bcrypt の約 0.3〜0.6 秒の間は0本（NFR5.1）。
- 72 バイトを超える今のパスワードは照合の仕組みに渡さず不一致とする（BR4.2）。ログインの `DummyPasswordHash` の代わりの照合はしない。本人はログイン済みで、利用者の有無を時間の差で推し量る脅威が無いため。
- トランザクションの境界は `user.service` に置く。同じクラスの中の呼び出しでは `@Transactional` が効かないため、読み取りと条件つきの更新は既存の `LoginService` と同じ `TransactionTemplate` で囲む（`team.md` の Code Style の「トランザクションの境界は業務処理の層」を守る。ArchUnit の既存のテストで確かめる）。
- 成功の出来事は条件つきの更新と同じトランザクションの中で知らせ、確定の後に監査が記録される。更新した行が 0 のときは出来事を知らせない。

## 3. プリファレンスの取得と保存（NFR6.1・NFR6.2）

- 取得: 読み取り専用のトランザクションで `findById` を1回。応答は4つだけで、email・admin を含めない（BR3.1）。
- 保存: 4つの検証（DisplayName・表示の設定の値）を DB の外で済ませ、誤りがあれば DB に触れずに 400 を返す（BR3.2）。通ったときだけ、短いトランザクションで4列だけを更新する問い合わせを1回（`reliability-design.md` 3節）。更新の後の再読み込みはせず、検証を通った値をそのまま返す（BR3.3 の「保存した値」と同じ値）。

## 4. ログイン・更新の応答の広げ（NFR6.5、C3、BR6.1）

- 既存の `UserSummary` に displayName・language・theme・fontSize を足す。ログインは `verifyPassword` の `findByEmail`、トークンの更新は `findById` の結果から作るため、内部DB への問い合わせは増えない。
- アクセストークンの認証（`AccessTokenAuthenticationProvider` が要求ごとに `findById`）も同じ `UserSummary` を読むが、列が4つ増えるだけで問い合わせの回数は変わらない。
- 確かめ: コード生成で、ログイン・更新の処理が発行する SQL の数が変わらないことを結合テスト（Hibernate の統計、または既存の問い合わせの数え方）で確かめる。性能は performance-validation の既存のログイン・更新の場面で確かめる。

## 5. 利用者の作成（C2、U3 の登録の完了の性能に関わる）

- `createUser` は、メールアドレスをそろえた後、bcrypt の前に登録済みかを読み取り1回で確かめ、登録済みなら計算せずに EmailAlreadyUsed を返す（要点 5）。同時の作成で一意の制約に当たったときも EmailAlreadyUsed を返す（BR5.2）。
- bcrypt は U3 の決定（U3 の Q4 A）のとおり、呼び出し元のトランザクションの中で計算する。契約 C2 と BR5.3 は変えない。U3 の NFR6.5 が 1 秒の目標から外した「同じメールアドレスの利用者がいる拒否」は、この作りで bcrypt を計算しなくなるが、同時の作成で一意の制約に当たる場合は計算の後になるため、U3 の要件の扱いは変えなくてよい。

## 6. bcrypt の cost（NFR6.6）

- `mastersmith.auth.password.bcrypt-cost` の既定 12 を変えない。新しいハッシュも照合も既存の `PasswordEncoder`（`UserAccountConfig`）を使う。

## 7. 測り方（performance-validation へ渡すこと）

- k6 の場面: プリファレンスの取得・保存（成功・入力の誤り）、パスワードの変更の成功・今のパスワードの誤り・入力の誤り、既存のログイン・更新。場面ごとに同時 10 件で p95 を判定する（NFR の「測り方の決まり」）。
- パスワードの変更の成功は、仮の利用者を 10 名以上用意し、場面ごとに変更の前後のパスワードを交互に使う。同じ利用者を同時に変えると Q3 A の条件つきの更新で 400 が混ざるため、1人の利用者を同時に2つの場面で使わない。
- 使い捨ての環境で、`caffeinate -i` を付けて流す（`project.md` の Testing Posture）。

## 8. 上流との差

| ID | 上流 | 上流の記載 | この段の設計 | 理由と扱い |
|---|---|---|---|---|
| P-D1 | NFR5.1（`nfr-requirements/performance-requirements.md`） | 新しいハッシュの書き込みの短い間だけ接続を持つ | 書き込みを「読んだときのハッシュのままなら書き換える」条件つきの更新にした | 依頼者の決定（Q3 A）。接続を持つ時間の考え方は変わらない。詳細は `reliability-design.md` 2節 |
