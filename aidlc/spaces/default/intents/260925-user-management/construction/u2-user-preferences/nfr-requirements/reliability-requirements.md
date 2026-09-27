# Reliability Requirements — U2 利用者のプリファレンスとパスワードの変更（u2-user-preferences）

U2 の信頼性の要件です。要件定義の NFR5（接続の使い方）・NFR9（テスト）・NFR10（スキーマの変更）を当てます。当面の配備先は開発者の PC 上のコンテナ1台で、可用性の数値の目標（SLO）は配備先が決まってから決めます（`aidlc/spaces/default/memory/team.md` の Deployment、`project.md` の Deployment）。出典の略号は `performance-requirements.md` と同じ。

## 要件

| ID | 要件 | 確かめ方と持ち主の段 | 出典 |
|---|---|---|---|
| NFR5.2 | パスワードの変更の成功は、確定の後の監査の記録で2本目の接続を借りる経路になる（`AuditEventListener` の AFTER_COMMIT と `AuditEventRecorder` の `REQUIRES_NEW`）。同時 10 件の成功とログインの要求が重なっても接続プールが尽きず、監査の記録が欠けない | performance-validation の k6 で、パスワードの変更の成功を同時 10 件で流し、使い捨ての環境の `/actuator/metrics` の hikaricp の値で、接続を借りる待ちの時間切れの累計が 0、借りるまでの待ちの最大が借りる待ちの上限（5 秒）より十分小さいことを確かめる。あわせて、流した成功の件数と監査の PASSWORD_CHANGED の SUCCESS の件数が一致することを確かめる | NFR5、`project.md` の Corrections・Testing Posture、BR7.3 |
| NFR9.4 | 監査の書き込みが失敗しても、パスワードの変更の応答（成功の 204、今のパスワードの誤りの 400）は変わらない。失敗はアプリのログに ERROR で残し、パスワードの値を含めない | 既存の `AuditWriteFailureIT` と同じ形の結合テストで、成功と誤りの両方を確かめる（code-generation） | NFR9、BR7.3、既存の AuditLog の決まり |
| NFR9.8 | プリファレンスの保存とパスワードの変更は、1つのトランザクションで確定するか、何も変えないかのどちらか（一部だけの保存をしない）。変更が巻き戻ったときは成功の監査を記録しない。内部DB の障害などの想定外の誤りは既存の 500 の扱い（内部の例外のメッセージを載せない） | 結合テスト（BR3.2 の誤りで何も変わらないこと、巻き戻りで監査が残らないこと）（code-generation） | NFR9、BR3.2・BR3.3・BR7.3 |
| NFR10.1 | スキーマの変更は1つの Flyway の V7 にまとめ、前進のみとする。適用済みの V1〜V6 は書き換えない | Flyway の `validate-on-migrate` と、V1〜V6 のファイルが変わっていないことのレビュー（code-generation） | NFR10、BR9.1・BR9.2 |
| NFR10.2 | V7 を当てると、既存の利用者（初期管理者）に氏名＝保存済みのメールアドレス・language ja・theme system・fontSize md が入り、既存の監査の行の2列は空のまま。起動し直しても V7 は1回だけ当たる | 結合テスト（V6 までを当てた内部DB に行を入れてから V7 を当て、値を確かめる）（code-generation） | NFR10、BR2.2、BR9.1、BR9.2 |
| NFR10.3 | 後方互換: 1つ前の版のアプリ（この Intent の前の develop の版）が、V7 を当てた後の内部DB で起動し、ログイン・トークンの更新・監査の記録が今までどおり動く。見込みの根拠は、Flyway の既定が知らない新しい移行を無視すること、Hibernate の `ddl-auto: validate` が余分な列を許すこと、language・theme・fontSize が既定の値を持ち、監査の2列が空を許すことだが、どれもまだ確かめていない | 確かめる作りと手順は NFR 設計・基盤の設計（infrastructure-design）で決める。実際の確かめは戻しの手順の練習として deployment-pipeline・deployment-execution で行う | NFR10、`team.md` の Deployment、BR9.1、機能設計の6節 |
| NFR10.4 | 後方互換の既知の限界: displayName は既定の値を持たない必須の列のため、1つ前の版のアプリが利用者を作ると追記が失敗する。1つ前の版が利用者を作るのは、利用者が1人もいないときの初期管理者の作成だけで、V7 を当てた内部DB には必ず利用者がいる（V7 の前に作られた利用者、または V7 の後の作成）ため、戻したときに起きない見込み。利用者が1人もいない内部DB に戻す場面（空の内部DB で1つ前の版を起動する）は、V7 を当てていない内部DB を使うため当てはまらない | NFR10.3 と同じ段で、利用者がいる内部DB で1つ前の版が初期管理者を作らないことを確かめる | NFR10、BR9.1、機能設計の2.6 の 5 |
| NFR10.5 | V7 を当てる前に、配備した内部DB のバックアップを取り、戻しの手順に「V7 の後の内部DB のまま1つ前の版の成果物で起動する」（第一の手）と「バックアップから戻す」（第二の手）を書く | deployment-pipeline で手順を決め、deployment-execution で行う | NFR10、`team.md` の Deployment（すべての配備に戻し方）、`project.md` の Deployment |

## 障害のときのふるまい

| 障害 | ふるまい | 要件 |
|---|---|---|
| 監査の書き込みの失敗 | 元の応答のまま、アプリのログに ERROR | NFR9.4 |
| 接続プールの待ちの時間切れ（5 秒） | 既存の 500 の扱い。成功の監査の記録で起きると監査が欠けうる（既知の制約、README の「監査ログ（U4）」） | NFR5.2 |
| 内部DB の障害 | 既存の 500 の扱い、トランザクションは巻き戻る | NFR9.8 |
| V7 の適用の失敗 | 既存の Flyway の扱いで起動を止める | NFR10.1 |

## 確かめる段の一覧

| 論点 | 持ち主の段 |
|---|---|
| 2本目の接続の負荷の試験（NFR5.2） | performance-validation |
| V7 の後方互換の確かめ方（NFR10.3・NFR10.4） | NFR 設計・基盤の設計（infrastructure-design） |
| 後方互換の実地の確かめ（戻しの練習）とバックアップ（NFR10.3〜NFR10.5） | deployment-pipeline・deployment-execution |
