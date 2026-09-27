# Performance Requirements — U3 招待と登録の完了（u3-invitation）

U3 の性能の要件です。要件定義の NFR5（接続の使い方）と NFR6（応答時間）を、この単位の招待の管理の API（招待・一覧・送り直し・取り消し、契約 C5）と登録の完了の API（リンクの確かめ・完了、契約 C6）に当てます。

出典の略号: NFR・FR は `aidlc/spaces/default/intents/260925-user-management/inception/requirements-analysis/requirements.md`、BR はこの単位の `construction/u3-invitation/functional-design/rules.md`、Q1〜Q4 はこの段の `nfr-requirements-questions.md`、要点 n は同じファイルの「設計の要点（案）」の番号、U1 の NFRx.y は `construction/u1-mail/nfr-requirements/`、U2 の NFRx.y は `construction/u2-user-preferences/nfr-requirements/`。

## 前提

- 測る環境は、当面の配備先である開発者の PC 上のコンテナ（colima の VM、CPU 4）とし、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で測る（`aidlc/spaces/default/memory/project.md` の Testing Posture）。
- 負荷の想定は前の Intent と U2 と同じ、利用者 最大 50 名・同時 10 件の要求（U2 の NFR6.7）。
- パスワードのハッシュは bcrypt の cost 12 で、ハッシュ1回 約 278 ms。bcrypt 1回のログインの同時 10 件の p95 は、CPU 4 で成功 940 ms（`aidlc/spaces/default/intents/260923-colima-spec-up/construction/build-and-test/test-results.md`）。
- SMTP の接続・読み取り・書き込みの時間切れはどれも 3 秒で、送信は1回だけ（自動の再試行と全体の上限なし）（U1 の NFR6.1・NFR6.3）。
- 測定の持ち主は、この Intent の流れにある performance-validation の段（k6）。Build and Test では、負荷の試験の手順書と場面の用意を引き継ぐ。

## 要件

| ID | 要件 | 確かめ方と持ち主の段 | 出典 |
|---|---|---|---|
| NFR6.1 | 招待（POST `/api/admin/invitations`、201）と送り直し（POST `/api/admin/invitations/{invitationId}/resend`、200）は、受け手が正常に受けるとき、SMTP の送信を含めて同時 10 件の要求で p95 が 5 秒以内 | k6 で招待と送り直しの場面を同時 10 件で流し、`http_req_duration` の p95 を測る。受け手は使い捨ての環境の Mailpit（下の「測り方の決まり」）（performance-validation） | NFR6、要点 1、BR4.1 |
| NFR6.2 | 受け手が応答しないとき（接続を受け付けて何も返さない・接続を拒む）も、招待・送り直しは U1 の時間切れの後に sendResult FAILED の成功の状態コード（201・200）で応答し、要求を待たせ続けない。時間切れが1回で済む場合（接続の時間切れ、または接続の後の最初の読み取りの時間切れ）は、1件の要求で 5 秒以内に応答する。応答が時間切れの手前で遅れ続ける受け手では全体が時間切れの値の数倍になりうる（U1 の NFR6.3 の既知の限界）ことは、この単位でも引き継ぐ | 結合テスト（テストの中で開いた受け付けて何も返さない `ServerSocket` と閉じたポート、テストの設定では時間切れを短い値にする）で、FAILED で応答し招待が確定したままであることを確かめる（code-generation）。既知の限界は確かめの対象にしない | NFR6、U1 の NFR6.1・NFR6.3、BR4.5、要点 1 |
| NFR6.3 | 一覧（GET `/api/admin/invitations`）・取り消し（POST `.../{invitationId}/cancel`、204）・リンクの確かめ（POST `/api/registration/verify`、200・404）は、同時 10 件の要求で p95 が 1 秒以内。どれも bcrypt と SMTP を使わない | k6 で場面ごとに同時 10 件で流し、p95 を測る。一覧は招待中の行を 20 件より多く置いた状態で1ページ目と最後のページを測る（performance-validation） | NFR6、要点 2 |
| NFR6.4 | 登録の完了の成功（POST `/api/registration/complete`、204）は、同時 10 件の要求で p95 が 1 秒以内。新しいパスワードのハッシュで bcrypt を1回計算するため、bcrypt 1回のログインと同じ目標とする（U2 の NFR6.3 と同じく bcrypt の回数に合わせる考え方）。ログインの余裕が 60 ms と小さく、登録の完了は利用者・ロックの状態・招待の書き込みが加わるため、performance-validation で届かなかったときは、目標を緩めて「満たした」ことにはせず、原因を確かめて依頼者に相談する | k6 で成功の場面を同時 10 件で流し、p95 を測る。トークンは1回だけ使えるため、流す回数以上の招待を用意する（下の「測り方の決まり」）（performance-validation） | NFR6、Q1 A、BR7.3 |
| NFR6.5 | 登録の完了の入力の誤り（400 VALIDATION_FAILED）とリンクの拒否（404 REGISTRATION_LINK_INVALID）は、同時 10 件の要求で p95 が 1 秒以内。どちらも bcrypt を計算しない（入力の誤りは内部DB を引かない、BR7.2。トークンの形の誤り・見つからない・有効でないの拒否は `createUser` を呼ぶ前に決まる、BR3.2・BR7.3）。ただし、招待が有効で完了の時点に同じメールアドレスの利用者がいる拒否（BR7.4）は `createUser` の中で決まり、bcrypt を計算しうるため NFR6.4 と同じ重さとし、この要件の対象から外す（同時の登録でしか起きないまれな場合） | k6 で入力の誤りと、形の誤り・見つからないトークンの場面を同時 10 件で流し、p95 を測る（performance-validation） | NFR6、要点 3、BR7.2・BR7.5 |
| NFR6.6 | bcrypt の cost は既定の 12 のまま変えない。NFR6.4 を満たすために cost を下げない | 設定の既定値の確認（`backend/src/main/resources/application.yaml` の `mastersmith.auth.password.bcrypt-cost`） | U2 の NFR6.6、要点 4 |
| NFR5.1 | 招待・送り直しは、確定の短いトランザクション → 内部DB の接続を持たない送信 → 送信の結果の記録の短いトランザクション、の順に行う。SMTP の送信を待つ間（最大で U1 の時間切れの間）、内部DB の接続を持たない。送信を待つ間に内部DB を使うほかの要求は、この要求の送信を待たずに応答できる | NFR 設計で作りと観測の仕方を決める（機能設計の7節）。結合テストで、受け手が応答しない送信の最中にほかの API（一覧など）が時間切れを待たずに応答することを確かめる（code-generation） | NFR5、ADR-009、BR4.1・BR4.3、AC1.1.7、要点 5 |
| NFR5.2 | 登録の完了は、承認済みの BR7.3 と契約 C2 のとおり、1つのトランザクションの中で招待の行を排他して読み、`createUser` を呼んでパスワードのハッシュを計算する。bcrypt の約 278 ms（同時の要求では CPU の待ちで伸びる）の間、内部DB の接続と招待の行の排他を持つことを受け入れる。U2 のパスワードの変更（ハッシュはトランザクションの外、U2 の NFR5.1）とは作りが違う。接続の待ちは NFR5.3（`reliability-requirements.md`）の負荷の試験で確かめる | NFR5.3 の負荷の試験で、接続を借りる待ちの時間切れの累計が 0 であることを確かめる（performance-validation） | NFR5、Q4 A、BR7.3、契約 C2 |

## 測り方の決まり

- **受け手の置き方**: 招待と送り直しは招待を使える設定（ベース URL とメールの送信の設定の両方、BR1.4）でないと 503 になるため、performance-validation の使い捨ての環境では、手元の受け手 Mailpit（compose の profile `mail`、U1 の NFR11.1）を一緒に起動し、アプリに `SPRING_MAIL_HOST=mailpit`・`SPRING_MAIL_PORT=1025`（暗号化 NONE・資格情報なし）と、ベース URL（`mastersmith.web.base-url` に当たる環境変数。名前はコード生成で決める）を渡す。今の負荷の試験の手順（`perf/README.md`）には受け手の起動が無いため、performance-validation（または Build and Test で手順書を用意するとき）に、Mailpit を受け手に置く手順を足す。配備したアプリの設定は変えない。
- **招待の用意**: 招待の場面は、流すたびに違うメールアドレス（予約されたドメイン `example.com` の下）を使い、409 の拒否を混ぜない。登録の完了の成功の場面は、トークンが1回だけ使え、トークンの値は招待メールにしか載らない（BR3.1）ため、流す回数以上の招待を先に作り、トークンを Mailpit の API（受け手の画面の 8025）で受けたメールから取り出すか、使い捨ての内部DB に既知のトークンの SHA-256 のハッシュで招待の行を直接入れる（仮の利用者を SQL で入れる前例と同じ）。どちらにするかは手順書を書く段で決める。
- 場面は、招待・送り直し・一覧・取り消し・リンクの確かめ・登録の完了の成功・入力の誤り・リンクの拒否に分け、場面ごとに p95 を判定する（1つの場面の遅さを、ほかの場面の速さで薄めない）。
- 長い試験は `caffeinate -i` を付けて流し、PC のスリープで結果が崩れるのを防ぐ（`project.md` の Testing Posture）。
- k6・Mailpit は同じ VM の CPU とメモリを分け合うため、値にその分が混ざりうる。この扱いは前の Intent と同じとし、結果に明記する。colima の VM に、使い捨てのアプリと Mailpit を同時に置ける余裕があるかは、試験の前に読み取りだけで確かめる（`project.md` の Corrections）。
- 目標に届かないときは、目標を緩めて「満たした」ことにはしない（`project.md` の Testing Posture）。原因を確かめ、依頼者に相談する。

## 上流との差

| ID | 上流 | 上流の記載 | この段の要件 | 理由と扱い |
|---|---|---|---|---|
| P-D1 | 要件 NFR6（`inception/requirements-analysis/requirements.md`） | 「招待・送り直しの API は、SMTP の送信を含めて 95 パーセンタイルで 5 秒以内に応答する。受け手が応答しないときも時間切れの後に送信の失敗として応答する [assumption]」 | 5 秒の目標は受け手が正常に受けるときの同時 10 件の p95 とした（NFR6.1）。受け手が応答しないときは時間切れの後に FAILED で応答するが、5 秒を守るのは時間切れが1回で済む場合だけで、遅れ続ける受け手では全体が数倍になりうる（NFR6.2） | U1 の段で、全体の上限を作らず送信を1回だけにすると決まっている（U1 の NFR6.3、ADR-009 の同じ要求の中で送る形）。要件の文書は書き換えない。配備先が決まったら値と限界を見直す（U1 の NFR6.3） |
| P-D2 | 要件 NFR6 | 「登録の完了の API は、既存の API と同じく 95 パーセンタイルで 1 秒以内 [assumption]」 | 数値は変えず、同時 10 件の p95 1 秒を目標にした（NFR6.4・NFR6.5）。成功は bcrypt 1回のため余裕が小さいことを記録した | 依頼者の決定（Q1 A）。[assumption] をこの段の目標として確かめた。差は余裕の小ささの記録だけ |
| P-D3 | U2 の NFR5.1 | パスワードのハッシュはトランザクションの外で計算し、bcrypt の間は接続を持たない | 登録の完了では、bcrypt の間も接続と招待の行の排他を持つ（NFR5.2） | 依頼者の決定（Q4 A）。承認済みの BR7.3 と契約 C2 のままにし、C2 と U2 の BR5.3 を変えない。登録の完了は1人1回の操作で、接続の数は上限に収まる見込み（`scalability-requirements.md` の NFR5.4）。U2 の文書は書き換えない |
