# Security Design — U3 利用者の管理の API（u3-user-admin-api）

U3 のセキュリティの設計です。承認済みの `construction/u3-user-admin-api/nfr-requirements/security-requirements.md`（NFR1.1〜NFR1.5・NFR3.1〜NFR3.3・NFR8.1・NFR9.1・NFR9.2）と、`tech-stack-decisions.md` の NFR11.1・NFR11.2 を満たす作りを決めます。この段は inline の段のため、プラットフォーム担当の視点（配備先が開発者の PC 上のコンテナであること、秘密と設定の扱い）もこの文書に重ねて書きます。

出典の略号は `reliability-design.md` と同じ。試しのコードの記録は `reliability-design.md` 1節です。

## 1. 認可（NFR1.1・NFR1.3、BR7.1）

- 7つの API（一覧 GET `/api/admin/users`、PUT `/{userId}/profile`、POST `/{userId}/grant-admin`・`revoke-admin`・`suspend`・`resume`・`reset-login-failures`）は、既存の `/api/admin/` の管理者の判定に乗せる。新しい公開の決まり（`SecurityRuleContributor`）は足さない。
- 未認証は 401 AUTHENTICATION_REQUIRED、管理者でない利用者は 403 ACCESS_DENIED（既存のアクセスの拒否の監査だけ）。どちらも業務処理を呼ばない。
- 停止中の管理者は、U1 のアクセストークンの認証の入口で 401 になり、認可と業務処理に届かない（U1 の `security-design.md` 2.4）。U3 で足す作りは無い。
- 判定は要求ごとに内部DB の今の値で行い、トークンや画面が持つ印に頼らない（既存の作り）。キャッシュを置かない（`performance-design.md` 6節）。
- 確かめ: 7つの API のそれぞれで、未認証・管理者でない・管理者・停止中の管理者を結合テストで確かめる（`team.md` の Testing Posture の管理の API の認可）。7つの API を B3（一覧・氏名と言語）と B4（5つの操作）に分けて書く（FS の 8.1）。

## 2. 印の切り替えと確かめ直し（NFR1.2・NFR1.4、BR2.5・BR2.6）

| 場面 | 作り |
|---|---|
| 印を付けた・外した確定の後の次の要求 | 既存の管理者の判定は要求ごとに内部DB の `users.admin_flag` を読むため、次の要求から 200・403 が切り替わる。印の付け外しはトークンに触れない（BR4.1・BR4.2） |
| 自分の印を外す | 判定の順で SELF_OPERATION が先に当たり 409 USER_ADMIN_SELF_OPERATION（BR2.2） |
| 印を付ける・外す・止めるの確かめ直し | 排他の後に別の問い合わせで読んだ有効な管理者の集合（`reliability-design.md` 2.1）に、操作した人の ID が含まれるか。業務の理由の判定をすべて通った後・書き換えの直前に置く（BR2.5） |
| 停止を解く・失敗回数を戻すの確かめ直し | 操作した人の利用者の行を排他なしで読み直し（C8 に足す `findAdminSummary` の例、FS の D8）、いて・印を持ち・停止していないか |
| 外れていた | 状態を変えずに 403 ACCESS_DENIED、その操作の失敗（NOT_ADMIN）として監査に残す（BR2.6） |

- 確かめ直しの 403 と認可の入口の 403 は同じ code で、画面の扱いは変わらない（FS の D10）。
- 確かめ: 待ち合わせの口で排他の待ちの間に操作した人の印を外す・止める場合を作り、403 と監査の NOT_ADMIN と状態が変わらないことを結合テストで確かめる（`reliability-design.md` 6節の口を使う）。
- 停止を解く・失敗回数を戻すの確かめ直しは排他なしで読むため、読んだ直後に別の操作が確定する短い隙が残る（11節の R2）。

## 3. 一括代入の防止と書き換えの口（NFR1.5・NFR11.2、BR5.4・BR7.2・BR7.3）

- 氏名と言語の変更の要求の DTO（`ProfileRequest`）は `displayName` と `language` の2つの項目だけを持つ。知らない項目は Jackson の既定の扱い（既存の DTO と同じ設定）で読み捨て、印・停止・失敗回数を結び付けない。
- `/api/me`・`/api/me/preferences` の DTO は変えない。どちらも印・停止・失敗回数の項目を持たない。
- 管理者の印・停止・失敗回数を書き換える口（C8 の `setAdmin`・失敗回数を戻す2段目、C1 の `setSuspended`・`revokeAllRefreshTokens`）を呼ぶのは `useradmin.service` だけにし、ArchUnit の `UserAdminBoundaryArchitectureTest` で呼び出し元を限る（NFR11.2）。
- 確かめ: 氏名と言語の変更・`/api/me`・`/api/me/preferences` の本文に `admin`・`suspended`・失敗回数の項目を足して送り、その値が変わらないことを結合テストで確かめる。

## 4. 伏せ字の型の経路（NFR3.1、BR1.3・BR1.5・BR5.2・BR7.4、FS の D13）

`TraceAspect` は `web`・`service`・`domain`・`repository` の層の引数と戻り値を TRACE で文字列にします。そのため、検索の文字・メールアドレス・氏名は、どの層のメソッドの引数と戻り値にも String で渡しません。Q3 A で比べ方を `ilike` にしても、この経路は変えません。

| 値 | web | service | user.service（C8） | user.repository | 文字列にする場所 |
|---|---|---|---|---|---|
| 検索の文字 q | controller の引数を `SearchText` で受ける（Bean でない変換の部品が包むだけ） | `SearchText` | `SearchText` | `SearchText` から作った検索のパターン（`RedactedText`） | `SearchText` の record の中（小文字化とエスケープ）と、問い合わせの SpEL（`:#{#pattern.value()}`） |
| 氏名・言語の変更 | `ProfileRequest`（toString で氏名を伏せる）から `ProfileCommand` | `ProfileCommand` | `ProfileCommand` | 検証を通った `ProfileUpdate` | `ProfileUpdate` の record の中と SpEL |
| 一覧の行 | `AdminUser`・`AdminUserPage`（toString で伏せる） | 同左 | `UserAdminSummary`・`UserAdminSlice`（toString で伏せる） | 投影の値（`UserAdminSummary` を直接作る） | record の中だけ |

- 排他の口が返す値（AdminRowsLock・UserRowLock）と、待ち合わせの口の引数は、利用者 ID と区分だけ。
- 業務のログを出すときは利用者 ID と区分だけをキーと値で出す。
- 確かめ: `UserAdminSecretLeakIT` で、TRACE を有効にした場合と既定のログのレベル（INFO）の場合の両方で、7つの API（一覧は q を付けた要求を含む）と、5つの操作の上限切れ（7節）を呼び、アプリのログ・監査の行・応答にメールアドレス・氏名・検索の文字・パスワードのハッシュ値が出ないことを確かめる（既存の `*SecretLeakIT` と同じ形）。どの層の引数にも String で渡していないことはレビューで確かめる。

## 5. 応答に含めない値とエラー応答（NFR3.2・NFR3.3、BR1.7・BR7.5）

- 一覧の行は決めた11項目と `self` だけを返す。問い合わせはパスワードのハッシュの列を読まない（投影、`performance-design.md` 2節）。ロックは `LockView` の3つ（ロック中か・解除の予定の時刻・戻せるか）だけで、失敗回数そのものとダミーの行を `useradmin` へ渡さない（C8）。
- エラー応答は既存の `@RestControllerAdvice`（`GlobalExceptionHandler`）だけが作る。項目の名前と理由と code だけを返し、入れた値・内部の例外のメッセージ・スタックトレースを載せない。q が長すぎる誤りは `q`・`TOO_LONG` だけ。409 USER_ADMIN_BUSY は、どの排他で待ったかも対象の利用者の値も載せない（`BusinessException` を新しく作って投げ、原因の例外をつながない）。
- 確かめ: 一覧の応答の項目、400・404・409 の応答の本文を結合テストで確かめる。`UserAdminSecretLeakIT` で応答と監査の行にハッシュ値・トークンの値・失敗回数が無いことを確かめる。

## 6. 入力の上限と検索の問い合わせ（NFR9.1、BR1.1・BR1.4・BR1.5・BR2.7、Q3 A）

### 6.1 入力の上限

| 入力 | 上限と検証 | 誤り |
|---|---|---|
| q | 前後の空白を除いて 254 コードポイントまで。除いた後が空なら検索なし | 400 VALIDATION_FAILED（q・TOO_LONG） |
| page | 共通のページ送りの検証（U2 の `Paging#parsePage`） | 400 VALIDATION_FAILED |
| userId | 整数として結び付け、読めなければ 400 | 400 VALIDATION_FAILED |
| 要求の本文 | 既存の上限（1MB）のまま | 既存の扱い |

### 6.2 検索の比べ方（Q3 A）

- 大文字と小文字は Unicode の範囲まで区別しない。内部DB の側は、メールアドレスと氏名を HQL の `ilike :pattern escape '\'` で比べる（H2 の方言で `ILIKE` に写る）。JVM の既定の Locale に左右されないため、Dockerfile・テストの JVM・E2E の起動で Locale を固定する設定は要らない。
- 検索の文字は、BR1.5 のとおり Java の側で `Locale.ROOT` の小文字化をしてから、`\`・`%`・`_` を `\` でエスケープし、前後に `%` を付けたパターンにする（`SearchText` の中）。問い合わせは名前つきの引数だけで組み立て、文字列をつなげない。
- 試しのコードで、同じ 1,000 名の表に JDBC の `email ILIKE ? ESCAPE '\' OR display_name ILIKE ? ESCAPE '\'` を当てて確かめた結果:

| 検索の文字（Java の側で小文字にした後） | 当たったもの |
|---|---|
| `ａｂｃ`（全角の小文字） | `ＡＢＣ Taro`（全角の大文字） |
| `taro` | `ＡＢＣ Taro` |
| `école` | `ÉCOLE Ärger` |
| `ärger` | `ÉCOLE Ärger` |
| `100\%`（% を文字どおり） | `100% off_sale\x` だけ（`100X offXsale` には当たらない） |
| `off\_sale`（_ を文字どおり） | `100% off_sale\x` だけ |
| `sale\\x`（\ を文字どおり） | `100% off_sale\x` |
| `istanbul` | `ISTANBUL` には当たり、`İstanbul` には当たらない |
| `İstanbul` を Java の `Locale.ROOT` で小文字にした `i̇stanbul`（2文字） | `İstanbul` にも当たらない |
| `straße`・`strasse` | それぞれ `Straße`・`STRASSE` だけ（ß と SS は別） |

- ASCII の英字・全角の英字・アクセントつきの欧文字は大文字と小文字を区別せずに当たり、`%`・`_`・`\` のエスケープは効く。
- 既知の差（受け入れる）: (1) `İ`（点のある大文字の I）は、Java で小文字にすると2文字になるため、`İ` を含む検索の文字は `İ` を含む氏名に当たらない。(2) `ß` と `SS` は別の文字として扱う。どちらも文字列全体の小文字化と1文字ずつの比べで結果が分かれるまれな文字で、BR1.5 の「ASCII の英字は必ず区別しない」は満たす。
- `ilike` は H2 の方言に頼る。別の DB へ移すときは見直す（`scalability-design.md` 5節）。
- まだ確かめていないこと: 試しのコードは JDBC で `ILIKE` を当てたため、Spring Data の `@Query` の解析が HQL の `ilike ... escape` と SpEL の引数を受け付けることは確かめていない。B3 の最初の手順で確かめ、受け付けないときは、同じ形の `ILIKE` を使う native の問い合わせ（名前つきの引数と SpEL のまま）にし、差をコード生成の記録に書く。
- 確かめ: ASCII の英字・全角の英字・アクセントつきの欧文字で大文字と小文字を区別しないこと、`%`・`_`・`\` を含む検索が文字どおりに一致すること、q の 254・255 コードポイント、空白だけの q、page の 0・数でない・桁あふれを結合テストで確かめる。`İ` と `ß` の既知の差は、決めた側の動作として1件ずつ結合テストに固定するかをコード生成の計画で決める。

## 7. 上限切れの例外とログ（NFR3.1・NFR3.4、BR3.5）

試しのコードの確かめ 4 で、排他の待ちの上限切れの例外の連なりの最後（`org.h2.mvstore.MVStoreException`）の文に、排他されていた行の全部の列の値が入ると分かりました。`users` の行ならメールアドレス・パスワードのハッシュ値・氏名、ロックの状態の行なら失敗回数と解除の予定の時刻です。この文をログに出すと、`project.md` の Forbidden（パスワードのハッシュ値・メールアドレスをログに含めない）に反します。

| 経路 | 危険 | 設計 |
|---|---|---|
| 排他の口で Busy に変える所のログ | 例外そのもの（cause とスタックトレース）を SLF4J に渡すと、連なりの文が出る | 例外のクラスの名前だけをキーと値で出す（`observability-design.md` 3節）。例外そのものは渡さない |
| `TraceAspect`（TRACE のとき） | `repository` の層のメソッドから例外が出ると、`TraceAspect` が例外の文字列とスタックトレース（`mastersmith.trace.log-exception-stack-trace` の既定は true）を出す | 上限切れを、問い合わせを実行する `repository` のメソッドの本体の中で受け、例外をメソッドの外へ出さない（`reliability-design.md` 5.2、差 ND-1）。既存の経路も同じ形に直す（7.2） |
| `GlobalExceptionHandler`（既定の INFO でも出る） | 想定外の誤りは ERROR でスタックトレース付き（原因の連なりを含む）。ログのレベルを変えなくても、連なりの最後の文が出る | U3 の上限切れは Busy に変えて `BusinessException`（原因をつながない）で返すため、ここを通らない。既存の経路は、repository の中で値を含まない例外に置き換えて投げる（7.2） |
| Hibernate の `SqlExceptionHelper` | WARN で誤りの番号・SQLState・`?` のままの SQL の文を出す | 値は出ない（確かめ 4）。そのまま。この前提は 7.3 で確かめ続ける |

```text
// repository の層の形（説明のための擬似コード）
LockAttempt lockAdminRows(long targetUserId) {   // 戻り値は ID の一覧か Busy
    try {
        return LockAttempt.locked(query.setLockMode(PESSIMISTIC_WRITE)
                .setHint("jakarta.persistence.lock.timeout", 3000).getResultList());
    } catch (PersistenceException e) {
        if (!RowLockFailures.isLockFailure(e)) {
            throw e;                                  // 排他の失敗でないものは今までどおり
        }
        RowLockFailures.warn(LOGGER, "ADMIN_ROWS", e); // 種類とクラスの名前だけ。e は渡さない
        return LockAttempt.busy();
    }
}
```

- 確かめ: `UserAdminSecretLeakIT` で、TRACE を有効にした場合と既定のログのレベル（INFO）の場合の両方で、5つの操作のそれぞれの上限切れ（別の接続で行を持ち続ける）を起こし、アプリのログに排他されていた行のメールアドレス・氏名・パスワードのハッシュ値・失敗回数と `MVStoreException` の文が出ないことを確かめる（B4）。

### 7.1 受ける例外の範囲（上限切れと行き詰まり）

- 排他の問い合わせのメソッドは `jakarta.persistence.PersistenceException` を受け、小さな判定の部品（例 `RowLockFailures`。置き場は 7.2 の例外と同じ新しいパッケージ、例 `common.persistence`）で排他の失敗かを見分ける。
- 排他の失敗とみなす条件（どれか1つ）: (1) 型が JPA の `LockTimeoutException`・`PessimisticLockException`・`QueryTimeoutException`。(2) 原因の連なりに `SQLException` があり、誤りの番号が 50200（上限切れ）か 40001（行き詰まり）、または SQLState が HYT00・40001。
- 理由: 50200 が JPA の `LockTimeoutException` になることは試しのコードで確かめた（`reliability-design.md` 1.6）。40001 は、Hibernate の `H2Dialect` が `LockAcquisitionException` にし、`ExceptionConverterImpl` が JPA の `PessimisticLockException` に写す見込みで、試してはいない。型の写し方が Hibernate の版で変わっても受けられるよう、誤りの番号でも見分ける。
- 排他の失敗でない例外は、今までどおりそのまま外へ出す（想定外の誤り）。
- 確かめ（B4）: 判定の部品の単体テストで、(1) の各型、原因に 50200・40001・HYT00 を持つ例外を排他の失敗とし、ほかの番号（例 23505）を排他の失敗としないこと。EntityManager の問い合わせを差し替えた repository の単体テストで、各例外のときに Busy（U3 の口）か値を含まない例外（7.2 の口）になり、例外そのものがメソッドの外へ出ないこと。行き詰まりを実際に起こす結合テストは必須にしない（`reliability-design.md` 4節のとおり起きない作りで、判定を型と番号の両方で行うため）。

### 7.2 既存の経路の直し（R4、この Intent の B4 で直す）

コードを読んで、`backend/src/main` の行の排他の問い合わせ（`PESSIMISTIC_WRITE`・`@Lock`）をすべて洗い出しました。U3 が足すものを除くと、上限切れの例外が repository の外へ出るのは次の4つです。

| # | 問い合わせ | 今の形 | 呼ぶ所 | 例外の文に入りうる行の値 |
|---|---|---|---|---|
| E1 | `LoginAttemptStateRepository#lockForUpdate` | EntityManager を直接使う。上限 3000 | `LoginService` の判定（実在の利用者）。同じクラスの `lockDummyForUpdate` の代わりの道（ダミーの行がすべて排他されているとき） | 失敗回数・解除の予定の時刻 |
| E2 | `InvitationRepository#findByEmailAndStateForUpdate`（`findPendingByEmailForUpdate` から） | Spring Data の `@Lock`。上限 3000 | `InvitationService` の招待 | メールアドレス・招待のトークンのハッシュ値・言語・日時など |
| E3 | `InvitationRepository#findByIdForUpdate` | 同上 | `InvitationService` の送り直し・取り消し | 同上 |
| E4 | `InvitationRepository#findByTokenHashForUpdate` | 同上 | `RegistrationService` の登録の完了 | 同上 |

- 対象の外: `lockDummyForUpdate` の最初の問い合わせ（SKIP LOCKED で待たず、上限切れにならない）。ダミーの行は個人に関する値を持たないが、代わりの道は E1 を通るため E1 の直しに含まれる。
- 今のまま既定の INFO で起きること: 上限切れは想定外の誤り（500）になり、`GlobalExceptionHandler` が ERROR でスタックトレース付き（原因の連なりを含む）を出す。招待の行ならメールアドレスと招待のトークンのハッシュ値がアプリのログに出て、`project.md` の Forbidden（メールアドレスをアプリのログに含めない）に既定の設定のまま触れる。招待のトークンのハッシュ値も、応答に含めないと決めている値で、ログに出すべきでない。TRACE のときは `TraceAspect` も同じ連なりを出す。

直す形（ND-1 と同じく、repository のメソッドの本体の中で受ける）:

- E1: `lockForUpdate` の本体の中で 7.1 の判定で受け、排他の失敗なら WARN（排他の種類 `LOGIN_ATTEMPT_ROW` とクラスの名前）を出してから、値を含まず原因をつながない例外（例 `RowLockUnavailableException`。決まった文だけを持つ）を投げる。U3 が足す `tryLockForUpdate` は、同じ問い合わせと判定を共有して Busy を返す。
- E2〜E4: Spring Data の `@Lock` の問い合わせは例外が代理の外へ出るため、メソッドの中で受けられない。3つの排他の問い合わせを EntityManager を直接使う実装に移し、E1 と同じく受けて値を含まない例外を投げる。候補は Spring Data の独自の断片（例 `InvitationLockQueries` とその実装を `InvitationRepository` に合わせる形）で、`InvitationService`・`RegistrationService` の呼び方と流れは変えない。メールアドレスは今と同じく伏せる値の型（`InvitationEmail`）で受け、文字列はメソッドの本体の中でだけ取り出す。断片か別の repository のクラスかは、コード生成の計画で決める。
- 応答は今の振る舞いを保つ: 投げる例外は想定外の誤りのまま `GlobalExceptionHandler` が 500 INTERNAL_ERROR にする。トランザクションは今と同じく例外で巻き戻り、排他の前に監査の出来事を出す経路は無いため監査も今と同じ。新しい例外は `DataIntegrityViolationException` の系統にせず、`InvitationService` の同時の招待の捕まえ直しに当たらないようにする。409 などに変えない。
- 変わるのはログだけ（差 SD-6）: (1) repository の WARN が1行増える。(2) `GlobalExceptionHandler` の ERROR のスタックトレースが、元の例外の連なりではなく値を含まない例外のものになる（元の例外のクラスの名前は WARN で分かる）。(3) TRACE の `TraceAspect` の出力も値を含まない例外になる。
- 作業の範囲: B4 で行う。`invitation.repository`・`invitation.service` はすでにパッケージごとの下限の対象、`auth.repository` は B1 で `packagesJudgedByTotal` から外れる計画。判定の部品と例外を置く新しいパッケージは自動で下限の対象になる。既存の `InvitationBoundaryArchitectureTest`・`AuthBoundaryArchitectureTest` は `common` への依存を禁じていないことを読んで確かめた（コード生成の計画で確かめ直す）。
- 不具合の直しのため、再現するテストを直しと同じコミットに含める（`project.md` の Mandated）。
- 確かめ（B4）: 別の接続で行を持ち続けて上限切れを起こし、ログイン（実在の利用者）・招待・送り直し・取り消し・登録の完了のそれぞれで、(a) 応答が今までどおり 500 INTERNAL_ERROR、(b) TRACE を有効にした場合と既定のログのレベル（INFO）の場合の両方で、アプリのログに排他されていた行の値（メールアドレス・招待のトークンのハッシュ値・見分けやすい値で入れた解除の予定の時刻）と `MVStoreException` の文が出ない、(c) WARN に排他の種類とクラスの名前が出る、を確かめる。置き場は既存の `AuthSecretLeakIT`・`InvitationSecretLeakIT` にテストを足す形を候補とする。ダミーの行がすべて排他されているときのログインを入れるかは、コード生成の計画で決める。

### 7.3 試しの前提を確かめ続ける

- 前提: 行の値は、例外の連なりの最後の `MVStoreException` の文にだけ入り、Hibernate の `SqlExceptionHelper` の WARN（誤りの番号・SQLState・`?` のままの SQL）には入らない。この前提は、試しのコード（H2 2.4.240・Hibernate 7.4.5.Final の1つの設定）での観察に頼っている。
- `UserAdminSecretLeakIT`（4節・7節）と 7.2 のテストを、TRACE を有効にした場合と既定のログのレベル（INFO）の場合の両方で流し、ログに値が出ないことを本番のテストとして確かめ続ける。
- H2 や Hibernate を上げるときは、この前提（値が入る場所、例外の型の写し方、7.1 の判定）を見直す（11節の R6）。

## 8. code と説明文（NFR8.1、BR2.3）

- 足す code は 404 USER_NOT_FOUND と、409 USER_ADMIN_SELF_OPERATION・USER_ADMIN_TARGET_SUSPENDED・USER_ADMIN_NO_CHANGE・USER_ADMIN_LAST_ADMIN・USER_ADMIN_BUSY の6つ。`useradmin.domain` の `UserAdminProblemTypes` に置き、起動時の一覧 `UserAdminProblemTypeCatalog` を `useradmin.service` に置く。1つの code に1つの状態コードを固定する。
- 説明文は ja・en を用意し、要求の Accept-Language で選ぶ（それ以外は ja）。既存のメッセージの仕組みに足すだけ。
- 共通の code（VALIDATION_FAILED・AUTHENTICATION_REQUIRED・ACCESS_DENIED）は既存の定義を使い回す。ACCESS_DENIED は `useradmin.web` から `access.domain` の `AccessProblemTypes` を使う（FS の D1、NFR11.1 の依存1本）。
- 確かめ: ja・en の説明文を結合テストで確かめる。code と状態コードの重複は既存の起動時の検査で確かめる。

## 9. 静的解析・依存・秘密（NFR9.2、プラットフォームの視点）

- 問い合わせは名前つきの引数と SpEL だけで組み立て、`SQL_` の指摘を出さない。新しい除外は足さない。
- 新しい依存を足さない（`tech-stack-decisions.md`）。ライセンスの確かめは要らない。
- 新しい設定の値・環境変数・秘密を足さない。排他の待ちの上限は既存の定数（3000 ミリ秒）にそろえ、設定にしない。配備先（開発者の PC 上のコンテナ）の `compose.yaml`・`.env.example`・Dockerfile は変えない。JVM の Locale を固定する設定も足さない（6.2）。
- 秘密情報の検出・依存関係の脆弱性検査は、除外を足さずに `./gradlew verify` でそのまま通す。

## 10. 確かめのテストの一覧（`team.md` の Testing Posture の必須テスト）

| テスト | 確かめること | Bolt の目安 |
|---|---|---|
| 管理の API の認可 | 7つの API で 401・403・200／204、停止中の管理者は 401 | B3・B4 |
| 管理者の印の変更 | 付けた直後・外した直後の次の要求で 200・403 が切り替わる。自分の印を外すは 409 | B4 |
| 確かめ直し | 待つ間に印が外れた・止められた操作した人は 403 と NOT_ADMIN | B4 |
| 要求の改ざん | 氏名と言語・`/api/me`・`/api/me/preferences` の本文で印・停止・失敗回数が変わらない | B3・B4 |
| 利用者の管理の漏えい（`UserAdminSecretLeakIT`） | TRACE と既定のレベル（INFO）の両方で、7つの API と q 付きの一覧、5つの操作の上限切れ。ログ・監査・応答に値が出ない | B3・B4 |
| 既存の経路の上限切れの漏えい（7.2） | TRACE と既定のレベル（INFO）の両方で、ログイン・招待・送り直し・取り消し・登録の完了の上限切れ。500 のままで、ログに行の値と `MVStoreException` の文が出ない | B4 |
| 排他の失敗の判定（7.1） | 型と誤りの番号で上限切れと行き詰まりを見分け、例外がメソッドの外へ出ない（単体テスト） | B4 |
| 入力と検索 | 6.1・6.2 の境界と文字どおりの一致 | B3 |

どの Bolt に置くかはコード生成の計画で決めます。テストの説明文は英語、テストデータのメールアドレスは予約のドメイン（`example.com` など）だけです。

## 11. 残る危険

| ID | 危険 | 受け入れる根拠 | 見直す時点 |
|---|---|---|---|
| R1 | 管理の API に要求の回数の制限が無い | NFR 要件の R1 のとおり（管理者だけが呼ぶ社内向け、アクセストークンは既定 5 分、すべて監査に残る、最後の管理者は無くせない） | 社外に公開する配備先が決まったとき、利用者の規模が増えたとき |
| R2 | 停止を解く・失敗回数を戻すの確かめ直しは排他なしで読むため、読んだ直後に別の操作が確定する短い隙が残る | 有効な管理者を減らす操作ではなく、不変条件は壊れない（FS の D5、NFR 要件の R2） | 権限の種類を増やすとき |
| R3 | 管理者による氏名と言語の変更は監査に残らない | 要件の決定（BR5.3、NFR 要件の R3） | 氏名を業務の識別に使うようになったとき |
| R4 | 既存のログイン・招待・送り直し・取り消し・登録の完了（7.2 の E1〜E4）は、排他の待ちの上限切れを想定外の誤り（500）として扱う。そのとき、ログのレベルを変えなくても既定の INFO のまま、`GlobalExceptionHandler` が ERROR でスタックトレース付き（原因の連なりを含む）を出す。連なりの最後の文に、排他されていた行の値（招待の行ならメールアドレスと招待のトークンのハッシュ値、ロックの状態の行なら失敗回数と解除の予定の時刻）が入り、`project.md` の Forbidden（メールアドレスをアプリのログに含めない）に触れる。TRACE のときは `TraceAspect` も同じ連なりを出す | 受け入れない。依頼者の決定で、この Intent の B4 で ND-1 と同じ形（repository のメソッドの中で受け、値を含まない例外に置き換える）に直す。応答（500）は変えない。確かめは TRACE と既定のレベルの両方の漏えいのテスト（7.2） | 直した後は 7.3 の前提の見直し（R6）に移る |
| R5 | 検索の既知の差（`İ` と `ß`、6.2） | まれな文字で、BR1.5 の最低限（ASCII）は満たす。Locale に左右されない利点を取った（Q3 A）。承認の場で受け入れた | 氏名に該当の文字が多い利用者が増えたとき、別の DB へ移すとき |
| R6 | 行の値が H2 の `MVStoreException` の文にだけ入り、Hibernate の `SqlExceptionHelper` の WARN には入らない、という前提は、試しのコードの1つの設定（H2 2.4.240・Hibernate 7.4.5.Final）での観察に頼る。40001 の例外の型の写し方は試していない | `UserAdminSecretLeakIT` と 7.2 のテストが TRACE と既定のレベルの両方で確かめ続ける。判定は型と誤りの番号の両方で行う（7.1） | H2 か Hibernate を上げるとき（7.3 の前提を見直す） |

## 12. 上流との差

| ID | 上流 | 上流の記載 | この段の設計 | 理由と扱い |
|---|---|---|---|---|
| SD-1 | BR1.5 | 氏名の列を DB の側で小文字にする場合も、Locale.ROOT の小文字化と同じ結果になることを確かめる | DB の側は小文字にせず `ilike` で比べる。結果は `İ` と `ß` で Locale.ROOT の小文字化と分かれうる（既知の差、R5） | Q3 A。試しのコードで差の範囲を確かめた（6.2） |
| SD-2 | BR3.5 | 上限切れの例外は `user.service`・`auth.service` の口の本体の中で受ける | `repository` の層の本体の中で受ける | `reliability-design.md` 11節の ND-1。`TraceAspect` の TRACE で行の値を出さないため |
| SD-3 | NFR3.1 の確かめ方 | `UserAdminSecretLeakIT` で7つの API と q 付きの一覧を確かめる | 5つの操作の上限切れの場合を足す | 確かめ 4 で行の値が例外に入ると分かったため |
| SD-4 | Q3 A | Spring Data の問い合わせの解析が `ilike` を受け付けることは Q1 の試しか B3 の最初で確かめる | 試しは JDBC で行ったため、B3 の最初で確かめる。受け付けないときは native の問い合わせに切り替える | 試しの範囲の記録（6.2）。承認の場で B3 の最初の手順として申し送った |
| SD-5 | `team.md` の Code Style（例外のログは変換する境界で1回だけ） | 1つの例外のログは1か所で1回 | 排他の失敗の1件で、repository の WARN（排他の種類とクラスの名前）と、変換の境界のログ（U3 の BUSY は WARN の code、既存の経路は ERROR）の2行が出る | repository は例外を値を含まない形に変える1つ目の境界、変換の境界は応答に変える2つ目の境界で、どちらにも出さないと元の例外のクラスの名前が失われる（例外そのものはつながないため）。2行は同じトレースID で結び付く。承認の場で、決まりとの差として受け入れた |
| SD-6 | U3 の範囲（NFR 要件の security と reliability） | U3 は既存のログイン・招待・登録の完了の上限切れの扱いを変えない（旧 R4） | 既存の4つの問い合わせ（7.2 の E1〜E4）を repository の中で受ける形に直し、ログだけを変える。応答・巻き戻し・監査は変えない | 承認の場の依頼者の決定（R-01 Major）。B4 の作業が増える（`invitation.repository` の排他の問い合わせの移し替え、判定の部品と例外、漏えいのテスト） |

## 承認の場の決定（Request Changes、2026-10-02）

前回のレビュー（R-01〜R-04）を受けた依頼者の決定と、この単位の成果物への反映です。

### 直したこと

| 指摘 | 依頼者の決定 | 直した場所と中身 |
|---|---|---|
| R-01（Major） | 既存の漏えいをこの Intent の B4 で直す。行の値を含む例外を repository の外へ出さず、クラスの名前だけをログに出す。応答は今の振る舞いを保つ。R4 を既定の INFO でも出る点を明記して書き直す。対象の問い合わせをコードで洗い出す | 7.2 を足した（読んで洗い出した E1〜E4、直す形、応答は 500 のまま、変わるのはログだけ、TRACE と既定のレベルの両方の漏えいのテスト）。7節の表、10節、11節の R4、12節の SD-6。`reliability-design.md` 5.2・10節・ND-1、`observability-design.md` 3節、`logical-components.md` 1・4・6・7節、`traceability.json` |
| R-02（Minor） | 本番の業務処理が Busy のときに巻き戻しの印を付けることを単体テストで直接確かめる | `reliability-design.md` 5.3 の表に単体テストの行を足し、結合テストの行の目的を口の側の確かめに書き直した。表の後の説明に、保険としての目的を書いた |
| R-03（Minor） | 受ける例外の型を広げる。WARN が2行出る点は Code Style との差として書く | 7.1 を足した（`PersistenceException` を受け、型と誤りの番号 50200・40001 と SQLState で見分ける判定、単体テスト）。7節の擬似コード。12節の SD-5、`observability-design.md` 3節、`reliability-design.md` 4節・5.1・ND-3 |
| R-04（Minor） | 行の値が H2 の内部の文にだけ入る前提を、`UserAdminSecretLeakIT` が TRACE と既定のレベルの両方で確かめると書く。H2 や Hibernate を上げるときに見直す | 7.3 と 11節の R6 を足した。4節・7節の確かめ、`reliability-design.md` 1.6・5.3 |

### 受け入れたこと

| 事項 | 内容 |
|---|---|
| ND-1 | 上限切れを `repository` のメソッドの中で受ける（承認済みの BR3.5 の書き方との差。機能設計は書き換えない） |
| R5 | 検索の既知の差（`İ`・`ß`、6.2） |
| BUSY のときのログ2行 | repository の WARN と変換の境界の WARN（SD-5。既存の経路の上限切れでも WARN と ERROR の2行になる） |

### 申し送り

| 事項 | 先 |
|---|---|
| SD-4（Spring Data の問い合わせで `ilike` と SpEL の引数が使えるか） | B3 のコード生成の最初の手順 |
| 7.2 の直し（E1〜E4。招待の排他の問い合わせを断片にするか別のクラスにするか、判定の部品と例外の名前と置き場、境界テストの確かめ、ダミーの行のログインのテストを入れるか、再現のテストを同じコミットに入れること） | B4 のコード生成の計画 |
| 7.1 の判定の単体テストと、5.3 の本番の業務処理の単体テスト | B4 のコード生成の計画 |
