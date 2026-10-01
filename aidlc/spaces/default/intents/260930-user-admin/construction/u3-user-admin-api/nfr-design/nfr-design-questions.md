# NFR Design の質問 — u3-user-admin-api

単位 U3（`aidlc/spaces/default/intents/260930-user-admin/inception/units-generation/unit-of-work.md`、種類 service）の NFR 設計のための質問です。service の単位のため、成果物は `performance-design.md`・`security-design.md`・`scalability-design.md`・`reliability-design.md`・`observability-design.md`・`logical-components.md`・`traceability.json` のすべてです。確かめた資料は、この単位の承認済みの NFR 要件 `construction/u3-user-admin-api/nfr-requirements/`（6つの文書と `traceability.json`。とくに `security-requirements.md` の「承認の場の決定」の申し送り、`reliability-requirements.md` の NFR4.3〜NFR4.5、`performance-requirements.md` の「索引の要否との関係」と NFR10）、機能設計 `construction/u3-user-admin-api/functional-design/`（`rules.md` の BR1.5・BR3.1〜BR3.6・BR4.3、`functional-spec.md` の 2.2・8.2 節・9節）、契約 `inception/contract-design/contract-summary.md`（C1・C3・C6・C8）、今のコード（`backend/src/main/java/cherry/mastersmith/auth/repository/LoginAttemptStateRepository.java`・`auth/service/LoginService.java`・`invitation/repository/InvitationRepository.java`・`invitation/service/RegistrationService.java`・`invitation/service/InvitationBarrier.java`・`audit/repository/AuditEventRepository.java`・`audit/service/AuditEventRecorder.java`・`audit/domain/AuditEvent.java`、`backend/src/main/resources/application.yaml`、`backend/src/main/resources/db/migration/V2〜V4`、テストの `invitation/testsupport/TestInvitationBarrier.java`）、Gradle の取得済みの部品のソース（H2 2.4.240 の `Select`・`StringFunction1`・`CompareLike`・`ConstraintReferential`、Hibernate 7.4.5 の `H2Dialect`・`ExceptionConverterImpl`）、決まり `aidlc/spaces/default/memory/team.md`・`project.md` です。機能設計 8.2 節と NFR 要件から申し送られた点のうち、作りで判断が分かれる3点を質問にしました。

## 設計の要点（案）

承認済みの NFR 要件・機能設計と、コードと部品のソースの確認から導ける作りの見通しです。質問の答えで決まる点は（Qn）と書きます。

### 信頼性（排他・最後の管理者の保護）

1. **管理者の行の排他（BR3.1）の作り**: `user.repository` に、管理者の印を持つ行と対象の行を `PESSIMISTIC_WRITE`・待ちの上限 3000 ミリ秒（既存と同じ `jakarta.persistence.lock.timeout`）で読む問い合わせを1つ置く。戻すのは利用者 ID だけにし、エンティティを持続の文脈に載せない。排他の後の「対象の要約」と「有効な管理者の ID の集合」は、別の問い合わせで値（投影）として読む。エンティティで読み直すと、Hibernate の持続の文脈に残った古い状態を返しうるため。
2. **H2 で待った後に何が数えられるか（BR3.1 の根拠、NFR4.5）**: H2 2.4.240 のソースでは、`SELECT ... FOR UPDATE` は行を走査しながら1行ずつ排他し、排他を得た行の最新の値で条件を評価し直す（`Select.isConditionMetForUpdate`）。そのため、待つ間に印が外れた行は結果から外れ、待つ間に新しく印が付いた行は排他されない。排他の後の別の問い合わせは、READ COMMITTED（H2 の既定、アプリでも変えていない）のため、待つ間に確定した変更を含めて数える。これは BR3.1 の根拠 (3)・(4) と合う見込みだが、まだ動かして確かめていない。確かめの形と時点は（Q1）。通らなければ BR3.1 の切り替え先（固定の1行を排他して、印・停止を変える3つの操作を1つずつ通す）に移る。
3. **行を取る順**: H2 は ORDER BY の順ではなく、走査の順に行を排他する。条件 `admin_flag = TRUE OR user_id = :target` は主キーの順の走査になる見込みで、そのとき取る順は利用者 ID の昇順になる（BR3.1 の「ID 順」の前提）。実行計画（EXPLAIN）で主キーの走査であることを確かめる（Q1）。一覧の並びのための索引を足す場合（Q2 B）も、この問い合わせの実行計画が変わらないことを確かめる。
4. **上限切れの後の巻き戻し（BR3.5、NFR4.3）**: Hibernate 7.4.5 は H2 の上限切れ（誤りの番号 50200）を `LockTimeoutException` に変え、JPA の決まりどおりトランザクションに巻き戻しの印を付けない。Spring はこれを `CannotAcquireLockException`（`PessimisticLockingFailureException` の系統）に変える。排他の口（`user.service`・`auth.service`）はメソッドの本体の中でこの系統を受けて Busy を返し、`useradmin.service` が `TransactionTemplate` の `status.setRollbackOnly()` を先に付けてから、DB に触れずに巻き戻す（既存の `RegistrationService.refuse` と同じ形）。途中の Spring Data の口が例外で全体の巻き戻しの印を付けても、自分の巻き戻しの印が先にあるため Spring は確定を試みず、`UnexpectedRollbackException`（500）にならない。H2 が行き詰まりを見つけたとき（40001）も同じ系統で届くが、BR3.1・BR3.4 の作りでは起きない見込みのため、同じく Busy として 409 にし、アプリのログの WARN に例外のクラスの名前を出して気づけるようにする。結合テスト（AC4.1.11）で 409・状態が変わらない・監査が増えないことを確かめる。
5. **既存の上限切れとの違い**: 今のログイン・招待・登録の完了は、上限切れを想定外の誤り（500）として扱っている（`InvitationRepository` の説明のとおり）。U3 で 409 に変えるのは5つの操作だけで、ログインの判定（待ち合わせの口を足す `LoginService`）の上限切れの扱いは変えない。
6. **止める操作とトークンの外部キー（BR3.4・BR4.3）**: H2 の外部キーの確かめ（`ConstraintReferential.existsRow`）は親の行を排他せずに読む。そのため、ログインのリフレッシュトークンの追記は、止める操作が排他している利用者の行を待たない見込み。止める操作のまとめての無効化（`refresh_tokens` の更新）は、同じ利用者のトークンの更新が持つトークンの行を待つことがあるが、トークンの更新は利用者の行を排他しないため、待つ向きは一方だけで行き詰まらない。利用者の行の印・停止の列の更新は、参照される列（`user_id`）を変えないため、子の表の確かめも走らない。これも（Q1）の形で確かめる。
7. **待ち合わせの口（BR3.6）**: 既存の `InvitationBarrier`・`PasswordChangeBarrier` と同じ形にする。`useradmin.service` に口（例 `UserAdminBarrier`、有効な管理者を数える直前に操作の区分と対象の利用者 ID を受ける）、`auth.service` に口（例 `LoginAttemptBarrier`、ロックの状態の行を排他した直後に利用者 ID を受ける）を置き、それぞれ何もしない既定の部品（`@Component`）を本番に置く。`auth.service` の口は、失敗回数を戻す操作の1段目と、`LoginService` の実在の利用者の行の排他の直後から呼ぶ（ダミーの行では呼ばない）。口の引数は ID と区分だけで、`TraceAspect` の TRACE に個人に関する値は出ない。テストの部品は `useradmin/testsupport`・`auth/testsupport` に置き、止める時間は 3000 ミリ秒より短くする（NFR4.4）。2つ目の操作が排他の待ちに入ったことの確かめは、既存の `TestInvitationBarrier` と同じく `INFORMATION_SCHEMA.SESSIONS` を読む形を候補とし、決めるのはコード生成の計画。
8. **トランザクションと監査（NFR9.4・NFR9.5）**: 5つの操作は `useradmin.service` の `TransactionTemplate` で1つのトランザクションにする。業務の拒否は書き込みをせずに確定し、成功と拒否の監査の出来事は確定の後に `AuditEventListener`（AFTER_COMMIT・`REQUIRES_NEW`）が2本目の接続で記録する。作りは既存のまま。

### 性能

9. **一覧の問い合わせ（NFR5.1・NFR5.2）**: 件数・20 件までの行・ロックの判定の結果の3回まで。行は利用者 ID・メールアドレス・氏名・言語・印・停止・登録した日時だけを投影で読み、ハッシュの列は読まない。ロックの判定の結果は `IN` の1回の問い合わせで読む。最後のページより後の page では行を読まない。部分一致の検索は前後に `%` が付くため、どの比べ方でも全体の走査になる。比べ方は（Q3）、索引は（Q2）。
10. **5つの操作の応答時間（NFR5.4〜NFR5.6）**: 待ちの上限は 3000 ミリ秒のまま。BUSY だけを 1 秒の目標の例外とするが、目標の負荷で BUSY が出たら不合格（NFR5.6）。止める操作のまとめての無効化は U1 の口（`user_id` の索引がある）を使う。キャッシュは置かない（要求ごとに内部DB の今の値で判定する NFR1.2 のため）。

### セキュリティ

11. **伏せ字の型の経路（NFR3.1）**: 機能設計の D13 のとおり、`SearchText`・`RedactedText`・`ProfileCommand`・`ProfileUpdate`・`UserAdminSummary` を controller から repository の口まで渡し、文字列にするのは record の中と SpEL の中だけ。比べ方（Q3）を変えても、この経路は変えない。
12. **認可・確かめ直し・一括代入の防止（NFR1.1〜NFR1.5）**: 既存の `/api/admin/` の認可の決まりに乗り、新しい公開の決まりを足さない。確かめ直しは機能設計の BR2.5 の位置。要求の DTO は displayName と language だけを持つ。
13. **静的解析（NFR9.2）**: 問い合わせは名前つきの引数だけで、`SQL_` の指摘は出ない見込み。新しい除外は足さない。

### 拡張性・観測

14. **1台・組み込みの H2 のまま（NFR6.1）**: アプリのメモリに状態を持たない。接続は5つの操作が1件に2本、ほかは1本。上限 10 の場面（NFR6.3）の条件と BUSY の件数の扱い（NFR 要件のレビューの R-07・R-08）は performance-validation の台本で扱う。
15. **観測（NFR5.9〜NFR5.11・NFR3.4・NFR9.3）**: 新しい指標・警報・ダッシュボードは足さない。409 USER_ADMIN_BUSY は既存の変換の境界で WARN（code だけ）。監査の種類5つと理由（NOT_ADMIN など）は列挙に値を足すだけ。

## 決まっていること（質問にしない）

| 決まっていること | 出典 |
|---|---|
| 排他の待ちの上限は 3000 ミリ秒のまま。409 USER_ADMIN_BUSY だけを 1 秒の目標の例外とし、目標の負荷で BUSY が出たら不合格 | NFR4.4・NFR5.6、機能設計の承認の場の申し送り |
| BR3.1 の確かめが通らなければ、固定の1行を排他して操作を1つずつ通す形に切り替える。切り替えても NFR5.4 の測り方は変えない | BR3.1、NFR4.5 |
| 上限切れは業務処理が巻き戻しの印を明示的に付けて巻き戻し、409・状態は変わらない・監査なし。500 にしない | BR3.5、NFR4.3 |
| どの操作も利用者の行とロックの状態の行を同時に排他しない | BR3.4、NFR4.3 |
| 待ち合わせの口は2か所（`useradmin.service` の数える直前、`auth.service` のロックの状態の行の排他の直後。ログインの判定からも呼ぶ）。本番は何もしない部品 | BR3.6、機能設計の承認の場で受け入れ |
| 監査の種類と理由は既存の `event_type`・`failure_reason`（VARCHAR(32)、CHECK 制約なし）に値を足すだけ。1つ前の版のアプリは、本番のコードで監査の行を書くだけ（`AuditEventRecorder` は `save` だけを呼ぶ。`findById`・`findAllByOrderByOccurredAtAsc` を呼ぶのはテストだけ）で、新しい値の行を読まないため困らない。Hibernate の `validate` は値を確かめない | NFR10.1、コードの確認 |
| 表と列は変えない。索引を足すと決めたときだけ、V10 として前進のみ・1つ前の版が動く後方互換で足し、移行は U3 が持つ。移行や戻しのためのテストは作らない | NFR10.2、NFR 要件の承認の場の決定 |
| 上限 10 の場面（NFR6.3）の条件と BUSY の件数（レビューの R-07・R-08）は performance-validation の台本で扱う | NFR 要件の承認の場の申し送り |
| 新しい依存・指標・警報は足さない | `tech-stack-decisions.md`、NFR5.10 |
| 2つ目の操作が排他の待ちに入ったことの確かめ方は、コード生成の計画で決める | BR3.6 |

---

## Q1. H2 と Hibernate の振る舞い（待った後の数え・行を取る順・外部キーの待ち）と一覧の見込みを、いつ・どの形で確かめますか？

理由: BR3.1 の根拠（要点 2）・行を取る順（要点 3）・外部キーの待ち（要点 6）は、H2 2.4.240 と Hibernate 7.4.5 のソースを読んだ見込みで、まだ動かしていません。通らなければ BR3.1 の切り替え先（固定の1行の排他。置き場によっては表と移行が増える）に移り、この段の `reliability-design.md`・`logical-components.md` の中身が変わります。あわせて、索引の要否（Q2）を決める 1,000 名の一覧の実行計画と時間も、NFR 要件で「捨ての試しのコードで見る形を基本とし、細部は NFR 設計で決める」とされています。前の Intent（dsl-schema-loader）でも、気がかりな点を本番とは別の試しのコードで先に確かめました。

A. この段で、成果物を書く前に、本番のコードとは別の捨ての試しのコード（コミットしない一時の場所、終わったら消す）で、H2 2.4.240・Hibernate 7.4.5 を使い次を確かめ、結果（確かめたこと・値・日時）を成果物に記録する: (1) 2つの接続で、一方が管理者の行を排他して止めている間に、もう一方の排他つきの読み取りが待ち、先が印を外して・付けて確定した後の結果と、排他の後の別の問い合わせの数え、(2) 排他の問い合わせの実行計画が主キーの走査であること、(3) 止める操作の更新とリフレッシュトークンの追記・更新が待つか、(4) 上限切れの後に `setRollbackOnly` で巻き戻して例外が出ないこと、(5) 1,000 名での一覧の3つの問い合わせの実行計画と時間。同じ観点のうち (1)・(3)・(4) は、本番に残る結合テストとしてコード生成（B4）でも確かめる（推奨。設計の前に切り替えの要否が分かり、書き直しを防げる）
B. 試しのコードは作らず、コード生成の B4 の最初の手順で、本番に残る結合テストとして (1)〜(4) を書いて確かめる。設計は見込みのとおり成り立つ前提で書き、通らなければ B4 の中で切り替え、差を記録する。一覧の見込み（5）は問い合わせの形からの見積もりだけにし、performance-validation の NFR5.1 で確かめる。この段は早く終わるが、切り替えが要ると分かるのが B3 の後になる
C. A のうち (1)（BR3.1 の根拠）だけをこの段の試しのコードで確かめ、(2)〜(5) は B のとおりコード生成と performance-validation に回す
X. Other (please specify)

[Answer]: A

## Q2. 一覧のための索引（`users.created_at`）を足しますか？

理由: 一覧は登録した日時の古い順・利用者 ID の順に 20 件を読み、全体の件数を数えます（BR1.1）。今の `users` の表には `created_at` の索引がありません（V2）。効くのは並びの索引だけで、検索ありの部分一致には効きません（NFR 要件の「索引の要否との関係」）。1,000 名なら、索引が無くても H2 が全体を読んで並べ替える量は小さい見込みです（未測定）。足すと、V10 の移行と、1つ前の版のアプリが新しい移行の済んだ内部DB で動くことの確かめ（NFR10.2 の (3)）が加わります。届かなかったときは目標を緩めず、索引を足す直しを諮ることが承認の場で決まっています（P-D2）。

A. 足さない。Q1 で試しのコードを作るなら、その 1,000 名の実行計画と時間を根拠として成果物に記録し、作らないなら問い合わせの形からの見積もりを根拠とする。performance-validation の NFR5.1 で届かなければ、索引を足す直しを依頼者に諮る（推奨。今の規模では効果が小さく、移行と後方互換の確かめを増やさない）
B. 足す。V10 で `users (created_at, user_id)` の索引を足し、並びと検索なしのページの読み出しに使う。移行のテストは作らず、既存の結合テストがそのまま通ることと、1つ前の版のアプリを V10 の済んだ内部DB で起動できることを確かめる（NFR10.2）。将来の利用者の増加に先に備えられる代わりに、移行と確かめが1つ増え、排他の問い合わせの実行計画が変わらないことも確かめる（要点 3）
C. A と同じく足さないが、Q1 の試しで1回の一覧の要求（3つの問い合わせの合計）が目安の時間（例 50 ミリ秒）を超えたら、この段の中で B に切り替える（目安の値は答えで決める）
X. Other (please specify)

[Answer]: A

## Q3. 検索で大文字と小文字を区別しない範囲と、内部DB の側での比べ方をどうしますか？

理由: 機能設計（BR1.5）は、検索の文字を Java の側で `Locale.ROOT` で小文字にし、「氏名の列を DB の側で小文字にする場合も同じ結果になることを確かめる」「範囲を Unicode まで広げるかを確かめる」としています。H2 2.4.240 のソースを読むと、`LOWER` は Locale を指定しない `String.toLowerCase()` で、JVM の既定の Locale に左右されます（トルコ語などでは `I` が点の無い `ı` になる）。今の Dockerfile・テストの JVM・E2E の起動では既定の Locale を固定していません。一方、`ILIKE`（Hibernate の HQL の `ilike`、H2 の方言で `ilike` に写る）は1文字ずつ大文字と小文字を無視して比べ、Locale に左右されません。メールアドレスは保存のときに `Locale.ROOT` で小文字にそろえてあります。どれも前後に `%` が付く部分一致のため、性能はほぼ同じ（全体の走査）です。

A. Unicode まで広げ、内部DB の側はメールアドレスと氏名を `ilike :pattern escape '\'` で比べる。検索の文字は BR1.5 のとおり Java の側で `Locale.ROOT` の小文字化とエスケープをする。JVM の Locale に左右されない。代わりに、文字列全体の小文字化と1文字ずつの比べで結果が分かれるまれな文字（小文字にすると2文字になる `İ` など）がある点と、`ilike` が H2 の方言に頼る点（別の DB へ移すときは見直す）を既知の差として記録する。Spring Data の問い合わせの解析が `ilike` を受け付けることは Q1 の試しか B3 の最初で確かめる。ASCII の英字・全角の英字・アクセントつきの欧文字で、大文字と小文字を区別しないことを結合テストで確かめる（推奨。Locale の設定に頼らず、BR1.5 の「実行環境の Locale に左右されない」をそのまま満たすため）
B. Unicode まで広げ、内部DB の側は `lower(displayName) like :pattern escape '\'` で比べる（BR1.5 の文字どおりの形）。H2 の `LOWER` が JVM の既定の Locale に頼るため、Dockerfile・テストの JVM・E2E の起動で既定の Locale を固定する（例 `-Duser.language=en`）。設定が3か所に増え、固定し忘れると環境によって結果が変わる
C. ASCII の英字だけを区別しない形にする（全角の英字・欧文字の大文字と小文字は区別する）。内部DB の側で A〜Z だけを小文字にする仕組み（H2 に専用の関数が無いため `TRANSLATE` などで作る）が要り、要件の最低限（ASCII は必ず区別しない）は満たすが、問い合わせが読みにくくなる
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

答えのまとめと、U3 の NFR 設計の計画:

- 設計の要点（案）のとおりに作る。成果物は service の6つと traceability。
- Q1 A: 成果物を書く前に、コミットしない捨ての試しのコードで次の5点を確かめ、結果を成果物に書く。
  1. 排他を待った後の数えに、待つ間に確定した印の変更が入ること（BR3.1 の根拠）
  2. 排他の問い合わせが主キーの順に走査すること（実行計画）
  3. ログインのトークンの追記が、利用者の行の外部キーで待たないこと
  4. 上限切れの後に、明示の巻き戻しの印で 500 にならずに巻き戻せること
  5. 1,000 名での一覧の実行計画と時間
  - 1・3・4 は、B4 の結合テストでも確かめる。
  - 通らなかった点があれば、成果物を書く前に切り替え先（固定の1行の排他など）を諮る。
  - 試しのコードはリポジトリの外の一時の場所で動かし、終わったら消す。
- Q2 A: `users.created_at` の索引は足さない。Q1 の試しの結果を根拠にし、Performance Validation で目標に届かなければ、索引を足す直しを諮る。
- Q3 A: 検索は、大文字と小文字を Unicode の範囲まで区別しない。内部DB の側は HQL の `ilike` で比べる（JVM の Locale に左右されない）。
- 監査の値を足した後の1つ前の版のアプリへの影響は無い（監査の行は書くだけで読まない、CHECK の制約も無い）。決まっていることとして書く。
- NFR 要件のレビューの R-07・R-08 は、Performance Validation の台本で扱う。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
