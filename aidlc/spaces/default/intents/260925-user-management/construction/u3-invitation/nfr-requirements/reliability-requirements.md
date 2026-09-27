# Reliability Requirements — U3 招待と登録の完了（u3-invitation）

U3 の信頼性の要件です。要件定義の NFR5（接続の使い方）・NFR9（テスト）・NFR10（スキーマの変更）を当てます。当面の配備先は開発者の PC 上のコンテナ1台で、可用性の数値の目標（SLO）は配備先が決まってから決めます（`aidlc/spaces/default/memory/team.md` の Deployment、`project.md` の Deployment）。出典の略号は `performance-requirements.md` と同じ。

## 要件

| ID | 要件 | 確かめ方と持ち主の段 | 出典 |
|---|---|---|---|
| NFR5.3 | 招待・送り直し・取り消し・登録の完了の成功は、確定の後の監査の記録で2本目の接続を借りる経路になる（既存の `AuditEventListener` の AFTER_COMMIT と `AuditEventRecorder` の `REQUIRES_NEW`）。登録の完了は bcrypt の間も接続を持つ（NFR5.2）。同時 10 件の招待・登録の完了とログインの要求が重なっても接続プールが尽きず、監査の記録が欠けない | performance-validation の k6 で、招待と登録の完了の成功をそれぞれ同時 10 件で流し、使い捨ての環境にだけ `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics` を渡して `/actuator/metrics` の hikaricp の値を読み、接続を借りる待ちの時間切れの累計が 0、借りるまでの待ちの最大が借りる待ちの上限（5 秒）より十分小さいことを確かめる。あわせて、流した成功の件数と監査の INVITATION_ISSUED・REGISTRATION_COMPLETED の件数が一致することを確かめる | NFR5、`project.md` の Corrections・Testing Posture、BR8.1・BR8.2、Q4 A |
| NFR9.4 | 監査の書き込みが失敗しても、招待（201）・送り直し（200）・取り消し（204）・登録の完了（204、拒否の 404）の応答は変わらない。失敗はアプリのログに ERROR で残し、トークン・URL・メールアドレス・パスワードを含めない | 既存の `AuditWriteFailureIT` と同じ形の結合テストで、新しい出来事5つ（INVITATION_ISSUED・INVITATION_RESENT・INVITATION_CANCELLED・REGISTRATION_COMPLETED・REGISTRATION_FAILED）ごとに1件ずつ確かめる（code-generation） | NFR9、BR8.5、既存の AuditLog の決まり、要点 18 |
| NFR9.8 | 同時の操作でも決まりが崩れない: 同じメールアドレスへの同時の招待は1件だけが作られ、もう一方は 409 INVITATION_ALREADY_PENDING でメールを送らず監査に残さない（BR2.5）。同じ招待への送り直し・取り消し・登録の完了の同時の要求は1つずつ判定され、取り消した招待で利用者が作られず、同じ招待で2人の利用者が作られない（BR6.4） | 結合テストで、待ち合わせ（テストの中の同期の仕組み）を使って確実に重ねた2つの要求で確かめる。`sleep` や実時刻に頼らない（`team.md` の Testing Posture）（code-generation） | NFR9、BR2.5・BR6.4、AC1.1.13・AC2.2.13・AC3.2.7、ADR-010 |
| NFR9.9 | 送信は1回だけで、自動の再試行をしない（U1 の NFR6.3）。送信が失敗しても招待と新しいトークン・有効期限は確定したままにし、sendResult を FAILED で記録する。送信の途中でアプリが止まったときは PENDING のまま残り、API では FAILED として返す（管理者が一覧で見つけて送り直せる）。送信の結果は、送ったトークンのハッシュを今も持つ行にだけ記録し、同時の送り直しの新しい結果を古い結果で上書きしない | 結合テストで、受け手が接続を拒む・応答しない・宛先を拒むときの sendResult と応答、PENDING の行が API で FAILED に見えること、受け手が受けた接続が1回だけであること、記録の時点で tokenHash が変わっていれば書かないことを確かめる（code-generation） | NFR9、BR4.3〜BR4.5、ADR-009、U1 の NFR6.3、要点 17 |
| NFR9.10 | 保存の日数（既定 90 日）を過ぎた終わった招待と期限切れの招待中は、定期の処理で消す。件数の上限ごとに別のトランザクションで消し、判定の条件を消す操作の中で確かめ直す（同時に送り直された行は消さない）。消した件数を INFO で出し、失敗は ERROR（スタックトレース付き）で出して例外を外へ出さず次の回に任せる（既存の `RefreshTokenCleanupJob` と同じ形）。消えた招待のリンクと ID は存在しないものと同じに扱い、監査の記録は消さない。既定の時刻と件数の上限はコード生成で決める | 結合テストで、境目の前後（ちょうど・直前）の行、件数の上限を超える行、失敗のときに次の回で消えることを、注入した時計で確かめる（code-generation） | NFR9、BR11.1・BR11.2、要点 20 |
| NFR9.13 | 招待・送り直し・取り消し・登録の完了のそれぞれの確定は、1つのトランザクションで確定するか、何も変えないかのどちらか。登録の完了は、利用者の作成と招待の完了を同じトランザクションで確定し、巻き戻ったときは利用者を作らず招待を PENDING のままにし、成功の監査を記録しない。内部DB の障害などの想定外の誤りは既存の 500 の扱い（内部の例外のメッセージを載せない） | 結合テスト（EmailAlreadyUsed の巻き戻しで利用者が増えず招待が PENDING のままであること、巻き戻りで成功の監査が残らないこと）（code-generation） | NFR9、BR7.3・BR7.4、要点 22 |
| NFR10.1 | 招待の表は、U2 の V7 の後の番号（V8 以降）の1つの Flyway の前進の変更で新しく足す。既存の表と適用済みの V1〜V7 は変えない | Flyway の `validate-on-migrate` と、適用済みのファイルが変わっていないことのレビュー（code-generation） | NFR10、BR11.3 |
| NFR10.2 | 後方互換: 1つ前の版のアプリ（招待の表を知らない版）が、招待の表を足した後の内部DB で起動し、今までどおり動く。見込みの根拠は、新しい表を足すだけで既存の表を変えないこと、Flyway の既定が知らない新しい移行を無視すること、Hibernate の `ddl-auto: validate` が知らない表を見ないことだが、まだ確かめていない | 確かめる作りと手順は NFR 設計・基盤の設計（infrastructure-design）で決め、実際の確かめは戻しの手順の練習として deployment-pipeline・deployment-execution で行う（U2 の NFR10.3 と同じ段で合わせて行う） | NFR10、`team.md` の Deployment、BR11.3、要点 21 |
| NFR10.3 | 1つ前の版へ戻した後に今の版へ戻し直すと、招待の表は戻す前の状態のまま使われる。戻している間に1つ前の版は招待の表を読み書きしないため、招待中の招待はそのまま残り、戻している間に有効期限を過ぎたものは期限切れになる | 戻しの手順（deployment-pipeline）に書く | NFR10、`team.md` の Deployment（すべての配備に戻し方） |

## 障害のときのふるまい

| 障害 | ふるまい | 要件 |
|---|---|---|
| SMTP の受け手が接続を拒む・応答しない・宛先を拒む | 招待・送り直しは確定したまま、sendResult FAILED で 201・200。管理者が送り直す | NFR6.2・NFR9.9 |
| 送信の途中でアプリが止まる | sendResult は PENDING のまま、API では FAILED。監査の SUCCESS は確定の後に記録済み | NFR9.9 |
| 招待を使える設定でない（ベース URL・SMTP が無い） | 招待と送り直しだけ 503、一覧・取り消し・リンクの確かめ・登録の完了は動く | BR1.5（機能設計のまま） |
| 監査の書き込みの失敗 | 元の応答のまま、アプリのログに ERROR | NFR9.4 |
| 接続プールの待ちの時間切れ（5 秒） | 既存の 500 の扱い。成功の監査の記録で起きると監査が欠けうる（既知の制約、README の「監査ログ（U4）」） | NFR5.3 |
| 内部DB の障害 | 既存の 500 の扱い、トランザクションは巻き戻る | NFR9.13 |
| 定期の削除の失敗 | その回に消した分まで、アプリのログに ERROR、次の回に任せる | NFR9.10 |
| V8 以降の適用の失敗 | 既存の Flyway の扱いで起動を止める | NFR10.1 |

## 確かめる段の一覧

| 論点 | 持ち主の段 |
|---|---|
| 2本目の接続と登録の完了の bcrypt の間の接続の負荷の試験（NFR5.3）、Mailpit を受け手に置く負荷の試験の手順 | performance-validation（手順書の用意は build-and-test） |
| 同じメールアドレスの招待中を1件に限る仕組みと行の排他の作り（NFR9.8、NFR6.10） | NFR 設計 |
| 招待の表を足した後の後方互換の確かめ方（NFR10.2） | NFR 設計・基盤の設計（infrastructure-design） |
| 後方互換の実地の確かめ（戻しの練習）と戻しの手順（NFR10.2・NFR10.3） | deployment-pipeline・deployment-execution |
