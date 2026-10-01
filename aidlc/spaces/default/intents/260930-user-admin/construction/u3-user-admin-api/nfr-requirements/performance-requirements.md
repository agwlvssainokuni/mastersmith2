# Performance Requirements — U3 利用者の管理の API（u3-user-admin-api）

U3 の性能の要件です。要件定義の NFR5（応答時間）を、この単位の7つの API（一覧 GET `/api/admin/users`、氏名と言語の変更 PUT `/api/admin/users/{userId}/profile`、5つの操作 POST `/api/admin/users/{userId}/grant-admin`・`revoke-admin`・`suspend`・`resume`・`reset-login-failures`、契約 C3）に当てます。この段の質問の答え（Q1 B・Q2 A・Q3 A・Q4 A）と、まとめの確認（Looks correct）で決めました。

枝番は単位の中で .1 から振ります。上流に当たる ID が無い要件は、次のとおり寄せました（`aidlc/spaces/default/memory/project.md` の学び）。

- 応答時間・想定の規模・観測の指標は、応答時間の NFR5 の枝番に寄せた（NFR5.1〜NFR5.11。規模は `scalability-requirements.md`、指標は `observability-requirements.md`）。
- 接続の数と接続プールの確かめは、接続の使い方の NFR6 の枝番にした（`scalability-requirements.md`・`reliability-requirements.md`）。
- ログの中身は、個人情報と秘密情報の NFR3 の枝番に寄せた（`security-requirements.md`・`observability-requirements.md`）。
- 監査・カバレッジ・静的解析・テストの道具は、テストの NFR9 の枝番に寄せた。

出典の略号: FS は機能設計 `aidlc/spaces/default/intents/260930-user-admin/construction/u3-user-admin-api/functional-design/functional-spec.md`、BR は同じフォルダの `rules.md` の決まり、要点 n と Qn はこの段の `nfr-requirements-questions.md` の「設計の要点（案）」の番号と質問、NFR・FR は `aidlc/spaces/default/intents/260930-user-admin/inception/requirements-analysis/requirements.md`、CS は契約 `aidlc/spaces/default/intents/260930-user-admin/inception/contract-design/contract-summary.md`、U1 NFR は `aidlc/spaces/default/intents/260930-user-admin/construction/u1-user-suspension/nfr-requirements/security-requirements.md`、PM は `aidlc/spaces/default/memory/project.md`、TM は `aidlc/spaces/default/memory/team.md`、前 U2・前 U3 は `aidlc/spaces/default/intents/260925-user-management/construction/u2-user-preferences/nfr-requirements/`・`u3-invitation/nfr-requirements/`。

## 前提

- 測る環境は、当面の配備先である開発者の PC 上のコンテナ（colima の VM、コンテナの上限は配備と同じ CPU 4・メモリ 2g）とし、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で測る（PM の Testing Posture、`perf/README.md`）。本物の内部DB と監査ログを汚さない。
- 想定の規模は前の Intent と同じ、利用者 最大 50 名・同時 10 件の要求（前 U2 の NFR6.7）。一覧だけは、悪い側の条件として利用者 1,000 名で測る（Q1 B）。
- 7つの API はどれもパスワードの照合（bcrypt）をしない。1件の処理は内部DB の数回の読み書きと、5つの操作では確定の後の監査の記録だけで、ログイン（同時 10 件の p95 が 940 ms）よりずっと軽い見込み（未測定）。
- 印を付ける・外す・止めるは、管理者の印を持つすべての行と対象の行を利用者 ID の順に排他する（BR3.1）。そのため、これら3つの操作は同時に流しても1つずつ通り、後の要求は前の確定を待つ。排他の待ちの上限は既存と同じ 3000 ミリ秒（`jakarta.persistence.lock.timeout`、既存の `LoginAttemptStateRepository`・`InvitationRepository` と同じ値）。
- 測定の持ち主は、この Intent の流れにある performance-validation の段（k6）。build-and-test は台本の用意と `k6 inspect` での読み込みの確かめを受け持つ（前の Intent と同じ分け方、要点 5）。

## 要件

| ID | 要件 | 確かめ方と持ち主の段 | 出典 |
|---|---|---|---|
| NFR5.1 | 一覧（GET `/api/admin/users`）は、内部DB に利用者 1,000 名を入れた状態で、同時 10 件の要求の p95 が 1 秒以内。場面は (a) 検索なしの1ページ目、(b) 検索なしの最後のページ（page 50）、(c) 多くの行に当たる検索の文字（例: 試験用の利用者のメールアドレスに共通の文字列）、(d) ほとんど当たらない検索の文字（例: どの利用者にも無い文字列）の4つに分け、場面ごとに判定する。想定の 50 名では測らない（悪い側で満たせば想定の規模も満たすとみなす） | 使い捨ての環境に試験用の利用者 1,000 名を SQL で入れ、k6 で場面ごとに同時 10 件をくり返し、`http_req_duration` の p95 と、`checks`（200 と total の値）の率 1 を閾値に置く（performance-validation） | NFR5、Q1 B、要点 1・12、BR1.1・BR1.5 |
| NFR5.2 | 一覧の1回の要求で内部DB に送る問い合わせは、全体の件数・20 件までの行・その利用者 ID のロックの判定の結果の3回までとし、行の数で回数が増えない（ロックの判定の結果は1回の問い合わせでまとめて読む）。最後のページより後の page では行を読まない。パスワードのハッシュの列を読まない | 既存のテストの部品（`SqlStatementCounter`）で、20 件のページと空のページの問い合わせの回数を結合テストで確かめる。読む列は問い合わせのレビューで確かめる（code-generation） | NFR5、BR1.1・BR1.6・BR1.7、FS の 2.1、要点 1 |
| NFR5.3 | 氏名と言語の変更（PUT `/{userId}/profile`）は、同時 10 件の要求で p95 が 1 秒以内（成功の 204 と入力の誤りの 400 とも） | k6 で同時 10 件を流し、p95 と `checks` の率 1 を閾値に置く。各 VU は自分の対象の利用者を使う（performance-validation） | NFR5、要点 2、BR5.2 |
| NFR5.4 | 5つの操作（印を付ける・外す・止める・停止を解く・失敗回数を戻す）は、5つを合わせて同時 10 件で流し、操作ごとに p95 が 1 秒以内。あわせて、どの要求も期待した 204 を返すこと（409 USER_ADMIN_BUSY・USER_ADMIN_NO_CHANGE・5xx が 0 件）を合格の条件にする | k6 で、試験用に入れた管理者（操作する人）と対象の利用者だけを使い、印を付けて外す・止めて解く・ロックさせて戻す、のように組で状態を戻しながらくり返す。対象の利用者は VU ごとに分け、変えるものが無い（NO_CHANGE）が起きない組にする。失敗回数を戻す組は、対象の利用者でログインを1回失敗させて失敗回数を 1 以上にしてから戻す（戻せる条件は失敗回数 1 以上、BR1.7。ログインの失敗の要求は、この要件の p95 の判定に含めない）。操作ごとの p95 と、`checks`（状態コード 204）の率 1 を閾値に置く。初期管理者は対象にしない（performance-validation。台本の細部もその段） | NFR5、Q2 A、要点 3・4、BR3.1・BR3.5 |
| NFR5.5 | 止める操作（POST `/{userId}/suspend`）の NFR5.4 の測定は、対象の利用者に未無効のリフレッシュトークン 100 件と無効のリフレッシュトークン 1,000 件を持たせた悪い側の条件で行う（U1 のまとめての無効化の目標を、止める操作の目標に含めるため） | 使い捨ての環境で、止める対象の利用者ごとに行を SQL で入れてから NFR5.4 の場面を流す。止めて解くたびに未無効の行が無くなるため、くり返しの前に入れ直すか、くり返しの回数分の対象を用意する（細部は performance-validation の台本） | U1 NFR の NFR5.2、NFR5、Q2 A |
| NFR5.6 | 排他の待ちの上限は既存と同じ 3000 ミリ秒のまま変えない。上限切れの 409 USER_ADMIN_BUSY は約 3 秒で返るため、この応答だけを 1 秒の目標の例外とする。ただし、目標の負荷（NFR5.4）で BUSY が1件でも出たら合格としない（BUSY を例外として除いて p95 を判定しない） | NFR5.4 の `checks` の率 1 で確かめる（performance-validation）。上限切れが 409 で返り 500 にならないことは結合テスト（`reliability-requirements.md` の NFR4.3）で確かめる（code-generation） | 機能設計の承認の場の申し送り、BR3.5、Q2 A、要点 4 |
| NFR5.7 | 負荷の試験の台本は既存の `perf/k6/scenarios.js` に場面を足し、場面の名前と、試験用の利用者 1,000 名・管理者・対象の利用者・リフレッシュトークンの行を入れる手順を `perf/README.md` に書く。build-and-test では、足した場面を `k6 inspect`（`--include-system-env-vars` を付ける）で読み込めることと、場面の名前が正しく出ることを確かめる | `k6 inspect` の結果の記録（build-and-test）。測定そのものは performance-validation | 要点 5、PM の Testing Posture（`k6 inspect` の学び） |

## 測り方の決まり

- 場面は、一覧の4つ（NFR5.1）、氏名と言語の変更、5つの操作（操作ごとに判定）に分け、場面ごとに p95 を判定する。1つの場面の遅さを、ほかの場面の速さで薄めない。
- 合否は k6 の閾値で決める: 場面ごとの `http_req_duration` の p95 が 1000 ms 以内、`checks` の率が 1（期待した状態コード以外が 0 件）。目標を緩めて「満たした」ことにはしない（PM の Testing Posture）。
- 長い試験は `caffeinate -i` で台本全体を包んで流し、PC のスリープで結果が崩れるのを防ぐ。遅れが出たら `pmset -g log` でスリープを確かめる（PM の Testing Posture）。
- 試験の前に、場面ごとの「Build and Test・Performance Validation に引き継ぐこと」を台本の手順と1つずつ突き合わせる（PM の Testing Posture の学び）。
- 1,000 名の利用者は SQL で直接入れるため、ロックの状態の行を持たない。失敗回数を戻す場面の対象は、組の最初のログインの失敗で行が作られる。一覧の場面で「ロック中」などの表示を混ぜたいときは、流す前に一部の利用者を1人ずつログインさせて行を作る（PM の Testing Posture の学び）。
- 片付けは、確かめの結果（監査の件数など、`reliability-requirements.md` の NFR6.2）を見てから行う（PM の Testing Posture の学び）。
- k6 は同じ VM の CPU を分け合うため、値に k6 の分が混ざりうる。この扱いは前の Intent と同じとし、結果に明記する。
- 目標に届かないときは、目標を緩めず、原因をログと状態で確かめて依頼者に相談する。

## 索引の要否との関係

一覧の検索は、メールアドレスと氏名を小文字にそろえた部分一致のため、索引を使わず全体を走査します（BR1.5）。並びは登録した日時の古い順です（BR1.1）。索引を作るか（`users.created_at` の索引、氏名を小文字にそろえる列の扱い）は NFR 設計で決めます（FS の 8.2 節）。

NFR 設計はコード生成と performance-validation より前の段のため、NFR5.1 の測定の結果をそのまま NFR 設計の材料にはできません。そこで、NFR 設計は 1,000 名の条件での見込み（問い合わせの形と H2 の実行計画の確かめなど、作り方は NFR 設計で決める）で索引の要否を決め、performance-validation が NFR5.1 で確かめます。届かなかったときは、目標を緩めずに索引を足す直しを依頼者に諮ります（承認の場で確かめたいこと、下の「上流との差」の P-D2）。

## 上流との差

| ID | 上流 | 上流の記載 | この段の要件 | 理由と扱い |
|---|---|---|---|---|
| P-D1 | 要件 NFR5 | 「一覧（検索を含む）と各操作の API は 95 パーセンタイルで 1 秒以内 [assumption]（件数の想定と測り方は NFR 要件の段で決める）」 | 目標は 1 秒のまま。一覧は 1,000 名の悪い側の条件で測る（NFR5.1）。409 USER_ADMIN_BUSY の応答だけは約 3 秒で返り、1 秒の目標の例外とする。ただし目標の負荷で BUSY が出ないことを合格の条件にする（NFR5.6） | 依頼者の決定（Q1 B・Q2 A）と、機能設計の承認の場の申し送り（排他の待ちの上限 3000 ミリ秒を変えない）。要件の文書は書き換えない |
| P-D2 | この段のまとめの確認（Q1 B の要約） | 「結果を索引の要否を NFR 設計で決める材料にする」 | NFR 設計は 1,000 名の条件での見込みで索引の要否を決め、測定（NFR5.1）は performance-validation が行う | NFR 設計はコード生成と測定より前の段のため、測定の結果を NFR 設計で直接使えない。要約と段の順が食い違うため、段の順を正として書き、差をここに記録する（PM の Way of Working）。承認の場で依頼者に確かめる |
