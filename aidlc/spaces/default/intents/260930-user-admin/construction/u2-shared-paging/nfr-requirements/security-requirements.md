# Security Requirements — U2 ページ送りの共通化（u2-shared-paging）

U2 のセキュリティの要件です。U2 は種類 library の単位のため、この段の成果物は `security-requirements.md`・`tech-stack-decisions.md`・`traceability.json` の3つです。性能・観測の前提は、別の文書を作らず、この文書の後半の節に置きます。

U2 は、招待の一覧のページ送りの計算（サーバーの `InvitationPaging`、画面の `paging.ts`）を、口と振る舞いを変えずにサーバーの `common.paging` の Paging と画面の `frontend/src/shared/paging/` の UiPaging へ移すだけの単位です。状態も保存するデータも持たない純粋な関数で、新しい API・新しい依存・スキーマの変更がありません。そのため、この段の質問は 0 問とし、設計の要点の確認（Looks correct）で決めました（`project.md` の Way of Working の学び）。

要件定義の NFR3（個人情報と秘密情報）・NFR5（応答時間）・NFR9（テスト）・NFR11（構造の決まり）を、この単位に当てます。ほかの NFR（NFR1・NFR2・NFR4・NFR6・NFR7・NFR8・NFR10）は当てはまらず、理由を `traceability.json` に書きました。

枝番は単位の中で .1 から振ります。上流に当たる ID が無い要件は、次のとおり寄せました。`project.md` の学びは「応答・同時性・指標は NFR6、画面の側のセキュリティ・依存・テスト・起動は NFR9 に寄せる」としていますが、U2 では次の2点がこの学びと違います（承認の場の決定、R-01）。

- **サーバー側の page の検証・桁あふれ・エラー応答・静的解析を NFR9 に寄せた（NFR9.1〜NFR9.4）。** 学びが NFR9 に寄せてよいとするのは画面の側の要件ですが、これらはサーバー側の要件です。それでも NFR9 に置いたのは、どれもテストと検査の関門（単体テスト・結合テスト・`./gradlew verify`）で確かめる要件で、要件定義の NFR に当たる ID が無いためです。page の検証の元になる機能の要件は FR1.5（ページの番号が不正なら入力の誤りとして拒否する）で、NFR9.1〜NFR9.3 の出典に加えました。
- **観測（指標・ログ）を NFR5 に寄せた（NFR5.2）。** 学びは指標を NFR6 に寄せるとしていますが、U2 では NFR6（接続の使い方）を N/A としており、観測の要件は「新しい指標を足さない」ことだけで、応答時間（NFR5）の前提と一続きに読めるため、NFR5 の枝番にしました。
- テストの道具・性質ベースのテスト・カバレッジ・依存は、学びどおり NFR9 の枝番とし、`tech-stack-decisions.md` に置いた（NFR9.6〜NFR9.11）。

出典の略号: FR は要件定義 `aidlc/spaces/default/intents/260930-user-admin/inception/requirements-analysis/requirements.md` の機能の要件、FS は機能設計 `aidlc/spaces/default/intents/260930-user-admin/construction/u2-shared-paging/functional-design/functional-spec.md`、BR は同じフォルダの `rules.md` の決まり、要点 n はこの段の `nfr-requirements-questions.md` の設計の要点、CS は契約 `aidlc/spaces/default/intents/260930-user-admin/inception/contract-design/contract-summary.md`、ADR は `aidlc/spaces/default/intents/260930-user-admin/inception/domain-design/decisions.md`、PM は `aidlc/spaces/default/memory/project.md`、TM は `aidlc/spaces/default/memory/team.md`。

## 守るもの

| 守るもの | 置き場 | 守り方 |
|---|---|---|
| サーバーの入力の検証（page） | 一覧の API の問い合わせの文字列 | Paging.parsePage だけで検証し、誤りは 400 `VALIDATION_FAILED` にする。画面の UiPaging の計算をサーバーの検証の代わりにしない（NFR9.1） |
| 一覧の読み取りの位置の正しさ | Paging.offsetOf の計算 | long で計算し、負の位置や桁あふれを起こさない（NFR9.2） |
| エラー応答の中身 | Problem Details の応答 | 既存の `VALIDATION_FAILED` を使い回し、例外のメッセージを載せない（NFR9.3） |
| 機能の間の依存の向き | `common.paging` と `src/shared/paging/` | どの機能にも依存しない。既存の境界の検査を書き換えない（NFR11.1） |
| 招待の一覧の今の振る舞い | 招待のサーバーと画面 | 参照先の変更だけで、既存のテストが通ったまま（NFR9.5） |

Paging・UiPaging は、parsePage が受ける問い合わせの文字列（利用者が送った page の値）を除いて、page・total・位置などの整数だけを受け渡し、メールアドレス・氏名・パスワード・トークンを持ちません（NFR3.1）。

## セキュリティの要件

| ID | 要件 | 確かめ方と持ち主の段 | 出典 |
|---|---|---|---|
| NFR3.1 | Paging・UiPaging の口の引数と戻り値は、page・total・行の数・位置・ページの向きの整数と列挙にし、メールアドレス・氏名・トークン・検索の文字を受け取らない。ただし、parsePage の引数だけは、利用者が送った問い合わせの文字列（長さに上限が無い）をそのまま受ける。これは個人に関する値・秘密ではないため、この単位では `toString` の伏せ字と `*SecretLeakIT` を足さない。検索の文字の伏せ字は U3 の受け持ち | 移した後の口の型（引数と戻り値）をコード生成のレビューで確かめる。あわせて、page の文字列が TRACE のログに出うる範囲を確かめる。`TraceAspect` の対象は Spring の部品のうち `web`・`service`・`domain`・`repository` の層で、素のクラスを置く `common.paging` は当たらない見込みだが、page の文字列を受ける呼び出し元のコントローラー（`web`）と業務処理（`service`。招待では `InvitationService` の一覧の口が受けた文字列を parsePage に渡す）の引数は対象になる。その範囲と、長い文字列がそのままログに出ることを受け入れてよいかを、コード生成のレビューで確かめる（code-generation） | NFR3、TM の Code Style（`TraceAspect`）、BR1.5・BR2.5、要点 4 |
| NFR9.1 | page はサーバーの Paging.parsePage だけで検証する。指定なしは 1、1〜9 桁の数字で 1 以上だけを受け、それ以外（`0`・負の数・小数・数字でない・空・前後の空白・符号つき・10 桁以上）は 400 `VALIDATION_FAILED` で拒否する。拒否のときは全体の件数を数えず、監査にも残さない。画面の UiPaging はサーバーの検証の代わりにしない | 移した `PagingTest` に今の拒否の事例（`0`・`-1`・`1.5`・`abc`・空・` 1`・`+1`・`9999999999`）をすべて残し、jqwik の性質（1〜999,999,999 は受ける、数字以外・10 文字以上・`"0"` は空）を足す。招待の一覧の API の既存の 400 の結合テストが通ったままであることを確かめる（code-generation） | FR1.5、BR1.2・BR3.1、TM の Code Style（検証はサーバー側を正とする）、要点 2 |
| NFR9.2 | offsetOf は long で計算し、9 桁までの page と Integer の最大の page で、負の位置や桁あふれを起こさない。1 未満のページ・位置を渡したときの IllegalArgumentException はプログラムの誤りとし、正しい呼び出しでは起きない | 既存のテストの `offsetOf(Integer.MAX_VALUE)` の事例と1未満の例外の事例を `PagingTest` に残し、jqwik の性質（offsetOf(page + 1) − offsetOf(page) が 20、pageOf(offsetOf(page) + 1) が page に戻る）を足す。あわせて、「行を読まずに空にする」判定（offsetOf と全体の件数の比べ）と実際の読み取りの位置（Spring Data の `PageRequest.of(page - 1, 20)`）が同じ page から導かれ、食い違わないことを結合テストで確かめる。招待の一覧の結合テスト（最後のページと、その次のページが空の 200 になること）で確かめ、利用者の一覧は U3 の「最後のページより後」の結合テスト（残る危険 R2）で同じことを確かめる（code-generation） | FR1.5、BR1.3・BR1.4・BR1.5、要点 3 |
| NFR9.3 | page の誤りの応答は、既存の `VALIDATION_FAILED`（Problem Details、`code` つき）を使い回し、新しい code を足さない。例外のメッセージとスタックトレースを応答に載せない | 招待の一覧の API の既存の 400 の結合テストが、code と本文の項目を変えずに通ることを確かめる（code-generation） | FR1.5、BR3.1、TM の Code Style（Problem Details・共通の code）、要点 5 |
| NFR9.4 | 既存の静的解析の関門（SpotBugs ＋ FindSecBugs の priority 1、`SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` は priority にかかわらず）と、画面の oxlint・ESLint のセキュリティ系のルール、秘密情報の検出・依存関係の脆弱性検査を、除外を足さずにそのまま通す | `./gradlew verify`（code-generation・build-and-test） | TM の Code Style（静的解析とセキュリティ検査）、PM の Mandated、要点 7 |
| NFR9.5 | 招待の振る舞いを変えない。招待の既存のテスト（サーバー・画面とも）が、参照先の変更だけで通ったままであること。移すテスト（`PagingTest`・`paging.test.ts`）は今の事例をすべて残す | `./gradlew verify` の単体・結合テストと画面のテスト。招待のテストの中身を書き換えず、import と参照先の変更だけであることをコード生成のレビューで確かめる（code-generation） | BR3.5、FS の 2.4 の 7.、Bolt の計画の B2 の完了の条件、要点 10 |
| NFR11.1 | `common.paging` はどの機能（`invitation`・`user` など）にも依存しない。既存の `ArchitectureTest` と `InvitationBoundaryArchitectureTest` は書き換えず、緩めない。画面は機能どうしで直接 import せず、`src/shared/paging/` から使う | 既存の ArchUnit の境界テストが変更なしで通ることを確かめる。画面の import の向きはコード生成のレビューで確かめる（code-generation） | NFR11、FS の 2.4 の 7.、TM の Code Style、ADR-004、要点 6 |

## 性能と観測の前提

この単位は数回の整数の計算だけで、応答時間に効きません。新しい性能の目標は足さず、既存の目標を緩めません（PM の Testing Posture）。

| ID | 要件 | 確かめ方と持ち主の段 | 出典 |
|---|---|---|---|
| NFR5.1 | 新しい性能の目標は足さない。招待の一覧の API の既存の目標（同時 10 件の要求で p95 1 秒以内）はそのまま保つ。一覧の応答時間（要件 NFR5）は、全体の件数を数えて 20 件を読む呼び出し元の受け持ちで、利用者の一覧は U3 の NFR 要件で扱う | Paging の計算が内部DB の問い合わせを増やさない（数える1回と読む1回は呼び出し元のまま）ことをコード生成のレビューで確かめる（code-generation）。招待の一覧の応答時間は、既存の k6 の場面 `invitationList`（`perf/k6/scenarios.js`、1ページ目と最後のページ、p95 1 秒）で確かめる。この Intent の流れには Performance Validation の段があるため、その段を持ち主とする。U2 のために新しい場面は足さない（performance-validation） | NFR5、BR3.2・BR3.4、要点 1 |
| NFR5.2 | 状態・保存・外部への接続・新しい API が無いため、新しい指標・警報・ログを足さない。招待の一覧の既存の指標とログは、そのまま | 既存の警報の決まり（`docker/monitoring/provisioning/alerting/mastersmith.yaml`）が変わらないことをレビューで確かめる（code-generation） | NFR5、要点 8 |

## 脅威と扱い

| 脅威 | 扱い | 要件 |
|---|---|---|
| 不正な page（巨大な数・符号・空白・数字でない）で、例外や負の位置を起こす | サーバーの parsePage で 400 に拒否し、件数を数えない。offsetOf は long で計算する | NFR9.1・NFR9.2 |
| 画面の検証だけに頼り、サーバーで検証しない | page の検証はサーバーの Paging だけを正とする | NFR9.1 |
| エラー応答から内部の例外の中身が漏れる | 既存の `VALIDATION_FAILED` を使い、例外のメッセージを載せない | NFR9.3 |
| 共通の部品が機能に依存し、境界が崩れる | `common.paging` は機能に依存しない。境界の検査を書き換えない | NFR11.1 |
| 移す途中で招待の一覧の振る舞いが変わる | 招待の既存のテストを参照先の変更だけで通す | NFR9.5 |

## 残る危険

| ID | 危険 | 受け入れる根拠 | 見直す時点 |
|---|---|---|---|
| R1 | `InvitationPaging`（よく通るクラス）が `invitation.domain` から抜け、パッケージごとのカバレッジ（行 80%・分岐 70%）が下がりうる（NFR9.8） | コード生成の計画で今の値を実測し、切り替えの後に測り直す。下回ったときは除外を増やさず、`invitation.domain` のほかのクラスのテストを足して満たす（機能設計の承認の場の決定 R-03） | U2 のコード生成の計画を書くとき |
| R2 | 「最後のページより後は 200 の空の一覧」（BR3.2）は呼び出し元が守る決まりで、Paging の口では強制できない | 契約 C2 で口を4つに決めた。利用者の一覧は U3 のコード生成の計画で「最後のページより後」と「全体 0 件」の結合テストを足す（機能設計の承認の場の決定 R-02） | U3 のコード生成の計画を書くとき |

## 上流との差

上流（要件・機能設計・契約）と違う要件はありません。上流に当たる ID が無い要件を NFR5・NFR9 に寄せたこと（冒頭のとおり）だけが、読み方の補いです。この寄せ方は `project.md` の学び（指標は NFR6、NFR9 は画面の側の要件）と2点で違い、その理由を冒頭に書きました。

## 承認の場の決定（Request Changes、2026-10-01）

依頼者は、レビューの指摘 R-01〜R-05 をすべて直すと決めました。

| 指摘 | 重さ | 扱い | 直したこと・申し送り |
|---|---|---|---|
| R-01 | Major | 直した | 枝番の寄せ方は変えず、冒頭に `project.md` の学びとの差を理由つきで書いた。サーバー側の page の検証・桁あふれ・エラー応答・静的解析を NFR9 に置いた理由は、どれも関門で確かめる要件で、上流に当たる NFR の ID が無いため。観測を NFR5.2 に置いた理由も、学び（NFR6）との差として書いた。NFR9.1〜NFR9.3 の出典に FR1.5 を足し、出典の略号に FR を足した |
| R-02 | Minor | 直した | NFR3.1 と「守るもの」の後の説明を、parsePage の引数は長さに上限の無い文字列だと正した。page の文字列が TRACE のログに出うる範囲（呼び出し元の `web`・`service` の引数）を、コード生成のレビューで確かめる項目に足した |
| R-03 | Minor | 直した | NFR5.1 の招待の一覧の応答時間の確かめを、条件つきの書き方から、既存の k6 の場面 `invitationList` で確かめる形に改めた。この Intent に Performance Validation の段があるため、その段を持ち主とした。新しい場面は足さない |
| R-04 | Minor | 直した | NFR9.2 の確かめに、「空にする」判定（offsetOf）と実際の読み取りの位置（`PageRequest`）が同じ page から導かれることを、招待の一覧の結合テストと U3 の「最後のページより後」の結合テストで確かめる一文を足した |
| R-05 | Minor | 直した | `tech-stack-decisions.md` の NFR9.9 を、慣習は「下位のパッケージには `package-info.java` を置かない」（今あるのは `common/package-info.java` だけ）と正し、`common.paging` にも置かないと決めた |

申し送り: R-02 の TRACE の範囲の確かめは、U2 のコード生成のレビューで行います。R-04 の利用者の一覧の側の確かめは、U3 のコード生成の計画で「最後のページより後」の結合テストとあわせて扱います。枝番は増やしていないため、`traceability.json` の変更はありません。
