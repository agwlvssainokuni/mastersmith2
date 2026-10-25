# Requirements Analysis の質問（Intent 261025-deps-perf-carryover）

依頼: 前の Intent 261016-business-data-views の振り返り（`aidlc/spaces/default/intents/261016-business-data-views/operation/feedback-optimization/feedback-loop.md` 2節）の第1の束（依存の更新と小さな直し）と第2の束（MySQL・MariaDB の読み取りの性能の直し）を1つの Intent で行い、あわせて Spring Boot を 4.1.1 から 4.1.2 に上げる。

## 決まっていること（質問にしない）

- **Dependabot の知らせの受け方**: GitHub の画面でマージせず、手元で版と lockfile をまとめて更新し、`./gradlew verify` を通してから `develop` に統合し、プルリクエストは閉じる（`team.md` の Way of Working）。今開いている知らせは 15 件（#54〜#68）で、振り返りの A1 と一致する。イメージ（collector・mailpit・otel-lgtm）は版のタグと index のダイジェストで固定し、Dependabot が見ない固定先もそろえる（`team.md` の Deployment）。
- **A3（make-you-chic-ui の依頼（5））は除く**: 上流の `main` の先頭は `ecb07e9` で、今の固定先と同じ。依頼（5）に当たる新しいコミットは公開されていない（読み取りの `git fetch` で確かめた）。依頼の文の「無ければ除く」に従い、自前の `ScrollRegion`・`PageSizeSelect` は置き換えない。
- **A2（`braces` 3.0.3）**: 上流の lockfile は今も 3.0.3 のままで、固定先を上げて直せる状態にない。警告のまま記録に残し、この Intent では直さない。
- **A4（配備した版と `main` をそろえる）**: 次の配備で `main` の先頭から WAR を作り直して配備する（配備の段の作業）。
- **Spring Boot 4.1.2 の事実（Maven Central の BOM を読み取りで確かめた）**: 4.1.2 の BOM は Tomcat 11.0.27（今の上書き 11.0.26 より新しい）、Jackson の BOM 3.1.7（今の上書き 3.1.7 と同じ）、OpenTelemetry 1.62.0（4.1.1 と同じ）を管理する。したがって Tomcat と Jackson の上書きは要らなくなる見込み。`opentelemetry-logback-appender`（2.28.1-alpha）は OpenTelemetry が 1.62.0 のままなので固定を残す見込みで、外部エクスポートを有効にした確かめが要る（`verify` には現れない）。`networknt` 3.0.6 は Jackson が 3.1 系のままなので残す。
- **性能の目標は緩めない**: Not Met 4 件（U1-NFR1.1・1.3・3.2・2.4）の目標の数値は変えない。直した後に3種類の対象DB（MySQL・MariaDB・PostgreSQL）で負荷の試験を流し直す。配備したアプリを約 4 時間止めることになる（前回 4 時間 27 分）。
- **負荷の試験の持ち主**: この Intent の流れに Performance Validation の段が無いため、k6 と測定は Build and Test で流す（`project.md` の学び）。長い試験は `caffeinate -dims` で包み、眠りが入った回は使わない。
- **含める持ち越し**: 第1の束の C1〜C7・G2・G4・G9 と `perf/README.md` (c) の `grep -o` の形（1021 行目。複数行の JSON で何も出ない見立てで、まだ流して確かめていない）、第2の束の B2〜B6・G1 と前からの P5〜P7。

## 質問

### Q1. 「Not Met」の部分一致の件数の出し方（B1）

部分一致の検索は索引を使えず、1要求で件数（`COUNT(*)`）とページ（`LIMIT/OFFSET`）の2回、表の全部を読みます。10 VU で読み取りのプール（5）の借りる待ちが 3 秒を超え、503 になります。一覧の件数をどう出しますか？

A. 件数を数える上限を置く: 上限 + 1 件まで数え、超えたら「N 件以上」と出す。ページ送りは続ける。すべての検索に当てる
B. 部分一致の検索のときだけ A の上限つきの件数にする。完全一致・絞り込みなしは正確な件数のまま
C. 件数は今のまま正確に出し、読み取りのプールの引き上げ（Q3）と索引の案内（Q4）だけで直す
X. Other (please specify)

[Answer]: C

### Q2. 件数を数える上限の値（Q1 で A か B のとき）

上限の値は、正確な件数が分かる範囲と、数えるときの読む行数（表の全部を読まずに済む量）の折り合いです。どの値にしますか？

A. 10,000 件（上限を超えると「10,000 件以上」）
B. 1,000 件
C. 100,000 件
X. Other (please specify)

[Answer]: X. 該当しない（Q1 が C のため、件数の上限は設けない）

### Q3. 読み取りのプールの上限（B2）

読み取りのプールの既定は 5 です。10 に上げると待ちは減りますが、対象DB の `anon` の最大（MariaDB 85.01%・MySQL 90.3%）の余裕を減らしうるとされています。

A. 既定は 5 のままにする。Q1 の上限つきの件数で直し、足りなければ負荷の試験で測ってから決める
B. 既定を 10 に上げ、3種類の対象DB の負荷の試験で `anon` の最大が線を超えないことを確かめる
C. 既定は 5 のままにし、`MASTERSMITH_TARGET_DB_POOL_MAXIMUM_SIZE` で 10 にできることを README に案内するだけにする
X. Other (please specify)

[Answer]: B

### Q4. 索引の案内（B1）

部分一致の列に索引が効かないことを、利用者・運用者にどう伝えますか？

A. README と運用の手順書（`runbooks.md` 相当）に書く。画面と DSL は変えない
B. A に加え、DSL の検索の設定の説明（JSON Schema の `description`）に「部分一致は索引が効かない」旨を足す
C. B に加え、部分一致の検索の入力欄の近くに注意を画面で出す
X. Other (please specify)

[Answer]: B

### Q5. Spring Boot 4.1.2 で固定を外す範囲

上の事実のとおり、Tomcat と Jackson の上書きは要らなくなる見込みです。

A. Tomcat（11.0.26）と Jackson（3.1.7）の上書きを外し、`.github/dependabot.yml` の Jackson の `ignore` も外す。appender と networknt は残し、外す時期のコメントを更新する。外した後に `./gradlew verify` と OSV-Scanner で High 以上が出ないことを確かめる
B. 4.1.2 に上げるだけで、上書きは今のまま残す（外す判断は次の Intent）
X. Other (please specify)

[Answer]: A

### F1. （追加）正確な件数のまま、プール 10 でも Not Met が残ったとき

Q1: C（件数は正確なまま）と Q3: B（プールを 10 に上げる）の組み合わせでは、部分一致が表の全部を2回読む点は変わりません。プール 10 と索引の案内だけでは、10 VU の部分一致や MariaDB の深い OFFSET の目標（Not Met 4 件）が満たせない可能性があります。また Q2 は Q1: C のため該当しません。直した後の負荷の試験でなお Not Met が残ったとき、どうしますか？目標は緩めません。

A. 残った件を Not Met のまま記録して次の Intent へ持ち越し、この Intent は承認して終える（件数を「多数」と出す案は次の Intent の候補にする）
B. その場で依頼者に諮り、件数を上限つき（Q2: B の 1,000 件）に切り替える追加の直しをこの Intent の中で行って流し直す
C. Q1 を A（上限つきの件数）に変えて最初から直す
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

- 作業の形: 第1の束（A1 の Dependabot 15 件の手元での更新と固定先のそろえ、C1〜C7・G2・G4・G9、`perf/README.md` (c) の `grep` の形）と第2の束（B2〜B6・G1・P5〜P7）を1つの Intent で行う。A2（`braces` 3.0.3）は上流が直っていないため記録だけ、A3（make-you-chic-ui の依頼（5））は上流が未公開のため除く。A4 は次の配備で WAR を `main` の先頭から作り直す。
- Q1（C）: 部分一致の検索の件数は正確なままにする。Q2 は該当しない（Q1: C のため上限は設けない）。
- Q3（B）: 読み取りのプールの既定を 5 から 10 に上げる。合格の条件は、対象DB の OOMKilled が false・`oom_kill` が 0。`anon` の最大は 85% の線と並べて記録し、超えたら合格の条件で Met として依頼者に諮る。OOMKilled か `oom_kill` が 0 でないときは、プール 10 は採らず Q3 をやり直す。
- Q4（B）: 部分一致の列に索引が効かないことを README・手順書と DSL の検索の設定の説明（JSON Schema の `description`）に書く。画面は変えない。
- Q5（A）: Spring Boot 4.1.2 に上げ、Tomcat（11.0.26）と Jackson（3.1.7）の上書きと `.github/dependabot.yml` の Jackson の `ignore` を外す。`opentelemetry-logback-appender` 2.28.1-alpha と networknt 3.0.6 は残し、外部エクスポートを有効にして appender を確かめる。外した後に `./gradlew verify` と OSV-Scanner で High 以上が出ないことを確かめる。
- F1（A）: 直した後の負荷の試験で Not Met が残った件は、目標を緩めず Not Met のまま記録して次の Intent へ持ち越す。この Intent が約束するのは「Not Met を必ず解消する」ことではなく、プール 10 の効果を測り、Met になった件・ならなかった件を件数で記録すること。
- 要件の書き直し（レビューの指摘を反映。答えは変えていない）: 変わるものを6つの一覧に限った（プール、DSL の説明、符号なしの型名と 400/409、`table-access` の削除、成功の知らせ）。C7 は U1 のレビューの R-02・R-05〜R-08 の内容、P5〜P7 は feedback-loop の内容と合否の基準を書いた。P6 は目標を緩めず、締める案の提案までとする。小さな直しの各項目に確かめ方を足した。見送った更新の記録先を `dependabot.yml` の `ignore` とコミットのメッセージにした。

Does this all look correct before I generate the requirements artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
