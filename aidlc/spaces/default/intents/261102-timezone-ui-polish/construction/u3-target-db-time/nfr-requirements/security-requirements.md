# セキュリティの要件 — u3-target-db-time

出典: 機能設計（`construction/u3-target-db-time/functional-design/functional-spec.md`・`rules.md` の BR3.1〜BR3.19・`reviews/review-01.md`）、要件（`inception/requirements-analysis/requirements.md` の FR5・FR6・NFR1〜NFR6）、契約 C4（`inception/contract-design/contract-summary.md`）、質問と答え（`nfr-requirements-questions.md`: Q1: A・Q2: A・Q3: B・Q4: B）、`team.md`（Testing Posture・Code Style・Deployment）、`project.md`（Forbidden・Mandated）。

U3 は、対象DB の日時の値の読み書き・接続のタイムゾーン・既定の DSL の生成の直し（種類 library。アプリに入る）。新しい API・表・画面・列は足さない。そのため、セキュリティ（と、この単位に当たる信頼性）の要件は「値と接続情報を外へ出さない」「信頼できない入力（読めない値・書き込みの値）で壊れない・黙って食い違わない」「接続の初期化が失敗しても安全側に倒れる」「確かめる」に絞る。性能と技術の選択は `tech-stack-decisions.md`。

## 番号の付け方

inception の NFR と同じ番号は、同じ意味でだけ使う。NFR2.1（問い合わせの数を増やさず、今の性能の目標を変えない）の細目は `tech-stack-decisions.md` の NFR2.1.1〜NFR2.1.5、NFR5.1（日時の必須のテスト）の細目は NFR5.1.1、NFR5.2（テストのタイムゾーンを明示に固定する）の細目は NFR5.2.1・NFR5.2.2。inception の NFR1.1〜NFR1.3 は他の単位の要件のため、U3 だけに当たる要件は、その次の枝番 NFR1.4（入力・漏えい・信頼性）・NFR1.5（依存とソースの決まり）から振る（上流の NFR に当たる行が無い追加の要件）。

## 要件の一覧

| ID | 要件 | 根拠・決まり | 確かめ方 |
|---|---|---|---|
| NFR1.4.1 | 日時・時刻の値（読めない文字・端数を超えた値を含む）、対象DB の接続情報、JDBC の例外のメッセージを、アプリのログ・監査・トレースの属性・エラー応答に含めない。誤りは、項目の名前と誤りの種類（SQLState・例外のクラスの名前）だけで示す。日時・時刻の値を持つ型は `toString` で値を伏せる | BR3.15・`team.md` Code Style（業務データの行の値・JDBC の例外）・`project.md` Forbidden | TRACE を有効にした `XxxSecretLeakIT`（読めない値・端数超過・主キーの日時の値を含む）。外部へ送る設定を有効にした結合テストでトレースの属性に値が出ないこと。エラー応答に例外のメッセージが出ないこと |
| NFR1.4.2 | 読めない日時・時刻（ゼロの日時・範囲外・24 時間を超える time・負の time・infinity）は、読めなかったセルだけ DB が送った文字をそのまま返し、ほかのセル・ほかの行は普通に読む。要求全体を失敗させず、応答の項目は増やさない。読めなかった値そのものは、ログ・エラー応答・トレースの属性に出さない（値の型だけを出す） | BR3.10・C4 | 3種類の対象DB の結合テスト: 読めない値を含む行を一覧・詳細で読んでも失敗せず、読めないセルだけ元の文字になること。TRACE のログに値が出ないこと |
| NFR1.4.3 | 書き込み・検索の日時・時刻の値は信頼できない入力として扱う。読めない形（ゼロの日時・24 時間を超える time・infinity・読めない文字）は 400 `VALIDATION_FAILED`（どの項目か）で拒否し、読めない値を送り返しても保存されない。DSL に明示した桁を超える端数は 400 `VALIDATION_FAILED` で拒否し、丸め・切りで黙って保存しない。新しい code・状態コードは足さず、値は応答・ログに含めない。確かめは書き込みの文の前に、DSL の値だけで行い、問い合わせを足さない | BR3.5・BR3.9・BR3.11・BR3.14 | 結合テスト: 読めない形・桁の超過の境界（桁ちょうどは受け付け、1桁超えると拒否）・拒否の後に対象DB の値が変わらないこと。性質ベース（jqwik）: 任意の文字列で例外が外へ出ず、読めない文字は拒否されること |
| NFR1.4.4 | 主キーの値が読めない行の詳細・更新・削除は、行を特定せず、既存の「行を1件に決められない操作」の拒否にする。一覧にはその行を出す。拒否の後に状態が変わらない | BR3.12・`project.md` Mandated（主キーの無い行の更新・削除は画面で隠すだけに頼らずサーバー側で拒否） | 結合テスト: 読めない主キーの行を、詳細・更新・削除で要求して拒否され、対象DB の値が変わらないこと |
| NFR1.4.5 | 接続のタイムゾーンの初期化（PostgreSQL の読み取りと書き込みのプール）が失敗した接続は、借りられない（既存の借りる失敗と同じ扱い。新しい code は足さない）。初期化の失敗を、UTC でない接続で読み書きして日時がずれる状態にしない。初期化の文は定数で、利用者の入力を含まない。失敗のログに接続情報・資格情報・例外のメッセージを出さない | BR3.1・Q1: A・`project.md` Forbidden | 結合テスト（PostgreSQL）: 初期化の後、読み取りと書き込みの両方の接続のセッションのタイムゾーンが UTC であること。初期化の失敗を作ったとき借りる失敗になり、値が漏れないこと（失敗の作り方は NFR 設計で決める） |
| NFR1.4.6 | 業務データの SQL は、今までの決まり（識別子は適用中の DSL の名前と照らして引用し、値はバインド変数で渡す）のまま変えない。日時の値の読み書きの変更で、利用者の入力を SQL の文字に組み込む経路を足さない。`java.sql.Connection` の `prepareStatement`・`createStatement` を呼んでよい場所を広げない | `project.md` Forbidden・`team.md` Code Style（SQL の組み立て・既存の境界テスト） | 既存の境界テスト（ArchUnit）と SpotBugs の `SQL_` の関門が変更なしで通ること。`'`・`"`・`;`・`--`・改行を含む日時の検索の値が SQL として解釈されないこと |
| NFR1.5.1 | 新しい依存を足さない（JDBC ドライバー・Hikari の既存の機能と標準の `java.time` だけ。Q1: A・Q2: A）。足す場合は、採用の前にライセンスを確かめ、Apache License 2.0 と異なるものは採用の理由を設計の記録に残し、`backend/gradle.lockfile` と OSV-Scanner の対象に入れる | `team.md` Code Style（新しい依存） | コード生成の差分で、`backend/build.gradle.kts` と lockfile に新しい依存が増えていないこと |
| NFR1.5.2 | ソースの先頭に Apache License 2.0 のヘッダー（`/* ... */` の形、年 `2026`、著作権者名 `agwlvssainokuni`）を置き、フォーマット・リンタ・静的解析（SpotBugs ＋ FindSecBugs）を通す。既存の境界テストと SpotBugs の除外は緩めず、足す形にする。日時の型の対応・DB の種類ごとの差は repository の層に閉じ、service の層は DB の種類で分岐しない | `project.md` Mandated・`team.md` Code Style | `./gradlew verify`（Spotless・SpotBugs・ArchUnit） |
| NFR5.1.1 | 日時の必須のテスト（`team.md` Testing Posture）を、この単位の範囲で確かめる: MySQL・MariaDB の `datetime`・`timestamp`・`date`・`time` と PostgreSQL の `timestamp`・`timestamptz`・`date`・`time`・`timetz` を、3種類の対象DB で `./gradlew verify` の中で毎回、一覧・詳細・作成・更新（と検索・主キー・参照の値の往復）の結合テストで確かめる。日付の変わり目、秒の端数 0・3・6 桁（読み・書き・往復で落ちない・丸められない）、範囲の端（MySQL の `timestamp` の 2038 年の上限・`time` の 24 時間超・負の値は、決めた側の動作で）、往復（読んだ値のまま更新しても変わらない。**ただし DSL の桁が DB の実際の桁以上のとき**）、桁の食い違い（DSL の桁が DB の桁より小さい列で、変えた欄が 400 になり、変えていない欄は影響しないこと）、読めない値（NFR1.4.2〜NFR1.4.4）を含める。性質ベース（jqwik。失敗時の乱数の種を記録）: 変換して戻すと元の時点に戻る、時差を持たない型は接続の時差の前後で値が変わらない。テスト用のスキーマ・データベースは実行ごとに一意の名前で作って終わったら消し、書いた後の値はアプリとは別の接続で読み直す | BR3.2・BR3.3・BR3.5〜BR3.14・`team.md` Testing Posture | `./gradlew verify`（コンテナの実行環境があること。無いときは Way of Working のとおり飛ばした状態で統合しない） |
| NFR5.2.1 | 日時の型の対応・読み書きの部品は、JVM の既定のタイムゾーンを暗に使わない。時差と時計は明示に渡す（テストは `ZoneId`・`Clock` を注入）。テストの JVM の `user.timezone` を別の値に変えて流しても、読み書きの値が変わらない | BR3.17・`team.md` Testing Posture | 結合テスト・単体テスト: `user.timezone` を `Asia/Tokyo` と別の値（例: `America/Los_Angeles`・`UTC`）にして同じ結果になること（試し（K-95）の結果を、恒常のテストに残す） |
| NFR5.2.2 | 実行時のタイムゾーンの設定（Dockerfile の `-Duser.timezone`・compose の `TZ`・テストの JVM の `user.timezone`・E2E の起動）は、この単位で変えない。変える必要が出たときは4か所を同じ値にそろえ、配備の記録に書く。接続のタイムゾーンは接続の初期化で行い、JVM の設定に頼らない | BR3.19・`team.md` Deployment | コード生成の差分で4か所が変わっていないこと |

## 脅威の見立て（U3 の範囲）

| 脅威 | 見立て | 対応 |
|---|---|---|
| 読めない値・端数超過の値・主キーの日時の値がログ・トレース・応答に漏れる | 業務データの値は個人に関する値を含みうる。JDBC の例外のメッセージに行の値が入りうる | NFR1.4.1・NFR1.4.2・NFR1.4.3 |
| 読めない値を送り返して、壊れた値を保存する・行を取り違える | 読めない文字をそのまま返す形は、書き込みで日時として読めない値として拒否される | NFR1.4.3・NFR1.4.4 |
| 接続のタイムゾーンが UTC でないまま読み書きして、時点がずれる | PostgreSQL は JVM の既定を送る見込み（K-95）。ずれた書き込みは業務データの誤りになる | NFR1.4.5・NFR5.2.1 |
| 日時の検索の値による SQL の組み込み | 検索の値はバインド変数で渡す今のまま | NFR1.4.6 |
| 桁の違いによる黙った値の食い違い | DSL の桁を超える端数は拒否する。DSL の桁が DB の桁より小さい列は、変えた欄が 400 になる（安全側） | NFR1.4.3・NFR5.1.1 |

## この単位に当たらない inception の NFR

NFR1.1（自作の SVG の安全）は u6-app-icon-assets、NFR1.2（本人だけが自分の設定を変える）は u2-display-time-zone、NFR1.3（メニューの応答の印）・NFR2.2（ホームの API）は u5-menu-icons-home、NFR3.1〜NFR3.3（アクセシビリティ）・NFR6.1（言語）は画面を持つ単位、NFR4.1（内部DB の列を足すだけ）は u2、NFR4.2（メニューの応答は足すだけ）は u5、NFR5.3（E2E は1本まで）は画面を通す u4-business-data-time が受け持つ。U3 は列・表・画面・E2E を足さない。

## 上流との差（承認済みの機能設計・契約・要件は書き換えず、ここに記録する）

- **BR3.4 の取り下げ（Q2: A、レビュー R-02）**: 機能設計の「結果の列情報が桁を返さないときは DB が送った桁のまま出す」は、今の読み取りが日時を型にしてから整形し、DB が送った末尾の 0 を取り出せないため実現できない。この単位は、桁を結果の列情報だけで決め、桁が得られない DB・型が NFR 設計の試し・結合テストで見つかったら、その型だけ設計に戻して依頼者に諮る。DSL の桁へは切り替えない。
- **BR3.13 の前提の明記（Q3: B、レビュー R-01）**: 「読んだ値をそのまま更新に送っても変わらない」は、DSL の桁が DB の実際の桁以上のとき（一致するとき）の保証。DSL の桁が DB の桁より小さい列（手書きの DSL が小さい桁を書いた、DSL の適用後に DB の桁が広がった）で、読んだ値の端数が DSL の桁を超える場合、その値を変えて送り返すと BR3.9 の 400 になる（丸め・切りはしない）。画面は変えていない欄を送らないため、当たるのは変えた欄と API を直接呼ぶ場合だけ。DB の実際の桁に合わせる手段（書き込みの前の問い合わせ・桁の持ち回り）は、問い合わせの数を増やさない決まりのため採らない。DSL の桁を DB の桁へ直すのは、既定の DSL の生成と照合の側の仕事で、この単位の範囲を超える。
- **読めないセルの既存の失敗の経路の変更（レビュー R-03）**: 今は、型の変換の非検査の例外が読めないセルになり、要求全体が `READ_CONVERSION` の失敗になる。この単位は、日時・時刻のセルについて、これを「読めなかったセルだけ DB が送った文字をそのまま返し、要求全体は失敗させない」に変える。文字の取り出し自体の失敗（`SQLException`）は、今までのとおり読み取りの失敗のまま。他の型のセルの扱いは変えない。
- **`timetz` の書き込みの変更（レビュー R-03）**: 今の書き込みは `timetz` を UTC に正規化して保存する。この単位は、時差つきの文字を時差ごと保存する（読みも DB の時差のまま）に変える。時差の無い値は UTC とみなして UTC の時差を付けて保存する。前の版の画面は時差つきの文字として読めるため壊れない。
- **契約 C4 の応答の形の例外・サーバー側の新しい拒否**: 機能設計の 5 節のとおり（読めないセルは DB が送った文字、DSL に明示した桁を超える端数は 400）。この段で追加の差は無い。
- **単位の種類**: 依頼文には service とあったが、`unit-of-work.md` の U3 は library。性能・拡張・信頼性・観測の独立した文書は作らず、変換の費用の目標は `tech-stack-decisions.md` に置く（U1 と同じ）。信頼性のうち、この単位に当たるもの（接続の初期化の失敗・読めない値）は、この文書の NFR1.4.2・NFR1.4.5 に置いた。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u3-target-db-time/functional-design/functional-spec.md`・`rules.md`・`reviews/review-01.md`
- 要件: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/requirements-analysis/requirements.md`
- 契約: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/contract-design/contract-summary.md`
- 質問と答え: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u3-target-db-time/nfr-requirements/nfr-requirements-questions.md`（Q1: A・Q2: A・Q3: B・Q4: B）

## Assumptions & Open Questions

- [assumption] PostgreSQL のドライバーが接続の開始時に JVM の既定のタイムゾーンを送り、接続の初期化の文がそれを上書きする、という見立ては未実行。NFR 設計の捨ての試し（K-95）で、`user.timezone` を変えて確かめる。変わるなら、開始時の指定（ドライバーの接続の設定）への切り替えを依頼者に諮る。
- [assumption] 結果の列情報が桁を返すかは、3種類の対象DB・型で未確認（Q2: A）。試しと結合テストで確かめ、得られない型があれば設計に戻す。
- [assumption] 初期化の失敗を結合テストで作る方法（PostgreSQL の接続でセッションの設定が失敗する状況）は NFR 設計で決める。
- [assumption] MariaDB の `timestamp` の上限は版によって違うと見て、サーバー側で決め打ちしない（上限の動作は結合テストで確かめ、決めた側の動作を明示する）。
- 未解決の点は無い（性能は `tech-stack-decisions.md`）。
