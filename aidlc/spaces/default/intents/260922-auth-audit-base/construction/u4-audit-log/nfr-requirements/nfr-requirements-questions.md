# NFR Requirements — Questions（U4 監査ログ / u4-audit-log）

U4 の非機能要件で決まっていない点を確認します。

決定済みの事項（再確認はしません）: 技術は U1 の tech-stack-decisions.md のとおり（Spring Data JPA、H2、Flyway）。記録は元の操作の確定後に別のトランザクションで、要求と同じスレッドで行う。書き込みの失敗は操作に影響させず、アプリのログに ERROR で出す。変更・削除の機能は作らず、無期限に保存する（保存期間は後続Intentで検討。OQ1）。

### Q1. 監査ログの改ざんへの備えは、どこまで行いますか？

- A. アプリに変更・削除の機能を持たせないことに加えて、各記録に直前の記録のハッシュを含めてつなぐ（ハッシュの連鎖）。途中の記録を書き換えたり消したりすると、検査で分かるようにする
- B. アプリに変更・削除の機能を持たせないだけとする（内部DBのファイルは OS の権限で守る）
- X. Other (please specify)

[Answer]: B. アプリに変更・削除の機能を持たせないだけとする（内部DBのファイルは OS の権限で守る）

### Q2. 監査イベント1件の書き込みにかけてよい時間の目標は、どれにしますか？

書き込みは要求と同じスレッドで行うため、ログイン・ログアウト・拒否の応答時間に含まれます。

- A. 95% が 50 ミリ秒以内
- B. 95% が 100 ミリ秒以内
- X. Other (please specify)

[Answer]: A. 95% が 50 ミリ秒以内

## Consolidated Summary Confirmation

- 改ざんへの備え: アプリに監査イベントの変更・削除の機能を持たせない。内部DBのファイルは OS の権限で守る（ハッシュの連鎖などは行わない）
- 書き込みの時間: 監査イベント1件の書き込みは 95% が 50 ミリ秒以内

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
