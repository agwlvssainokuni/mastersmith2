<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-27T02:53:05Z — 「依存の取得元は Maven Central だけ」は依存の解決の決まりで、ビルドのプラグインには当たらないと読んだ; ルートの settings.gradle.kts に pluginManagement が無く、アプリ自身のプラグインも今すでに Gradle Plugin Portal から取っている。そのため java-mustache-processor のビルドの設定が Plugin Portal のプラグイン（dependency-check 10.0.4 は Maven Central に無い）を使うことは決まりを崩さないとし、B1 の確かめの条件に Gradle 9.7.1 で動くことを入れた。
- 2026-09-27T02:53:05Z — 性能・キャッシュ・暗号化の方式・固定先のコミットは質問にせず要点にした; テンプレートは起動時に準備済み（BR2.3）、暗号化は BR1.4・BR1.5 で決まり、固定先はタグ 0.1.0 が既定のブランチの先頭（8d44c36）と一致するため。質問は判断が分かれる時間切れの値・変えられるか・送信の部品の組み込み方・テスト用の受け手の4問に絞った。

- 2026-09-27T03:07:32Z — Q3 X の「送らない」を、送信の部品を ObjectProvider で受け、部品が無い・接続先が空白だけ・差出人が無いときに NOT_CONFIGURED とする形で満たすと読んだ; Spring Boot 4.1.1 の MailSenderAutoConfiguration は spring.mail.host があるときだけ JavaMailSenderImpl を作り、OnPropertyCondition は空の文字列も「ある」とするため。application.yaml に空の既定の spring.mail.host を置かず、.env.example の SMTP の行はコメントにする案にした。
- 2026-09-27T03:45:42Z — library の単位で性能・信頼性の文書が無いため、NFR5・NFR6 を tech-stack-decisions.md の2節、NFR8・NFR9・NFR11 を3〜5節に置き、冒頭に要件の節と場所の対応表を付けた; セキュリティ（NFR1・NFR2）と上流との差は security-requirements.md。ヘルスチェックから外す要件は、SMTP の状態がアプリの応答と健全性を左右しない点で NFR6.4 にした。
- 2026-09-27T03:45:42Z — 枝番は依頼どおり単位の中で .1 から振った（要件 27 件、traceability は OK 7・N/A 4）; 前の Intent（dsl-schema-loader）は NFR1 を単位の間で通し番号にしていたため、この Intent では後の単位と同じ NFR1.1 などが重なる。単位のディレクトリで区別できる前提。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-27T03:07:32Z — Q3 X と Q2 B により承認済みの BR1.1・BR1.2・BR1.3・BR1.5・BR1.8 と差が出るため、機能設計は書き換えず質問ファイルの要点 18 に差を並べた; 成果物（security-requirements.md・tech-stack-decisions.md）にも差として書く（project.md の決まり）。ヘルスチェックから外す設定は機能設計に無かった追加。
- 2026-09-27T03:26:07Z — F1 B・F2 A・F3 A で BR1.3（数でないポートで起動が止まる、protocol の点検）・BR1.4（NONE は分類の結果で判定）・BR1.5（587 の既定が無くなり 25・465 になる）・BR1.8（TLS を弱める値も README の運用で扱う）の差が増えたため、要点 18 に足した; README に書く運用で設定しない値の一覧を要点 8 に置いた。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-27T02:53:05Z — Spring Boot のメールの自動設定を使わない案を推した; spring.mail.properties.* が環境変数から mail.debug を有効にできる口になり BR1.8 を破り、メールのヘルスチェックが SMTP に接続するため。代わりに送信の部品を U1 が自分で組み立てる手間が増える。
- 2026-09-27T02:53:05Z — テスト用の受け手に SubEtha SMTP を推した; GreenMail は STARTTLS を受け付けられず BR1.5 の STARTTLS の成功を確かめられず、JUnit 4（EPL 1.0）と Angus Mail をまとめた JAR を連れてくるため。GreenMail のほうが公開が新しく機能は多い。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-27T02:53:05Z — 送信の全体の上限を作らないため、遅れ続ける受け手と DNS の待ちでは時間切れの値を超えうる; 全体を打ち切るには別のスレッドが要り ADR-009 と合わないため既知の限界として記録する案にした。依頼者が全体の上限を求めるかは Q1 の答えとあわせて確かめる。
- 2026-09-27T03:07:32Z — 答えの分析で残った4点を追加の質問 F1〜F4 にした; F1 暗号化の指定（MailProperties に1つの項目が無く STARTTLS の 587 が自動にならない）、F2 数でないポート（Integer の結び付けで起動が止まり BR1.3 と食い違う）、F3 spring.mail.properties から TLS を弱める値・mail.debug を入れられたときの守り、F4 「取得元は Maven Central だけ」がビルドのプラグインにも当たるか。
- 2026-09-27T03:07:32Z — 時間切れは spring.mail.properties に置き、U1 が起動時に getJavaMailProperties から読んで点検する案にした; 部品は読めない値を無制限として扱うおそれがあるため。環境変数から点を含む map の鍵へ結び付くかは B1 で確かめる。
- 2026-09-27T03:26:07Z — F1 B の説明（STARTTLS が必須でなければ「設定がない」）と F3 A（starttls.required=false を含む TLS を弱める値に守りを足さない）が、starttls.enable=true・required が true でない組でぶつかるため、追加の質問 F5 にした; 推奨は NONE と同じに分類し、資格情報があるときだけ BR1.4 で送らない案（資格情報を平文に流さない趣旨を保ちつつ、ほかは F3 A のとおり運用で扱う）。
- 2026-09-27T03:26:07Z — F1 B で、SMTPS は protocol smtps と ssl.enabled の2通りを SMTPS に分類し、両方と STARTTLS の指定が重なるときは SMTPS を優先、知らない protocol は「設定がない」とする案にした; 時間切れの点検も使う protocol の鍵（mail.smtps.* か mail.smtp.*）で行う。質問にしなかったのは、Spring Boot と部品の動きから一通りに決まるため。
