# Practices Discovery — 質問（Intent 261029-reference-lookup-cleanup）

既存の実践（`aidlc/spaces/default/memory/team.md`）が基準です。リードの初稿と品質・開発・セキュリティの3人の意見をもとに、今回の Intent で判断が分かれる点だけを尋ねます。

## Q1: 参照先テーブルの権限の当て方は、実践（固い制約）にしますか、要件定義で決めますか
他テーブル参照の選択肢を返すとき、参照元の権限だけを持ち、参照先を読む権限を欠く利用者に選択肢（ラベル）を返してよいかが論点です。3人とも「要件定義で決める」に賛成で、セキュリティ担当は「403 と 404 のそろえ方も要件で決める」ことを条件にしています。

A. 要件定義で決める（実践には足さない）
B. 「参照先の読み取り権限を持たない利用者には選択肢を返さない」を固い制約（ALWAYS）に足す
C. 要件定義で決めるが、固い制約にするかは要件の段で再度尋ねる
X. Other (please specify)

[Answer]: A

## Q2: 他テーブル参照の必須テストを、team.md のどこまで足しますか
初稿の案は (a) 参照先を読む権限を持たない利用者に選択肢を返さない（参照元の権限だけを持ち、参照先の権限を欠く利用者で確かめる）、(b) 参照先の識別子を適用中の DSL と照合する、(c) 選択肢の件数・検索の上限と時間切れ、(d) 参照先の値をログ・トレース・エラーに出さない、(e) ピッカーの検索の `%`・`_` のエスケープ、(f) ラベルを信頼できない入力として文字で描く、です。3人の意見で次を足す案が出ています: (g) 参照元の書き込みでの参照先の検証と IDOR、(h) 参照先の主キーの形（複合・主キーなし・ビュー・自己参照）、(i) 3種類の対象DB、(j) 接続の数（`*ConnectionUsageIT` の形）、(k) 境界テストの許可の一覧が変わらないこと。

A. (a)〜(k) をすべて必須テストに足す
B. 初稿の (a)〜(f) だけを足す
C. (a)〜(f) に (g)(h)(i) を足す（(j)(k) は設計の段で決める）
X. Other (please specify)

[Answer]: A

## Q3: E2E（画面からの一連の操作のテスト）を足しますか
team.md は「機能の Intent ごとに代表の流れを1本まで」です。今回は参照ピッカーで1本足す案で、品質担当は「自作の前提・専用のスキーマ・axe（展開した状態・長い名前・ブランドカラーとテーマの全組）」を条件にしています。権限を欠く利用者の確かめは E2E ではなく結合テストで行います。

A. 参照ピッカーの代表の流れを1本足す（上の条件つき）
B. 足さない（既存の E2E の書き換えだけ）
X. Other (please specify)

[Answer]: A

## Q4: Dependabot の #81（i18next）・#82（react-i18next）の取り込み方
どちらも patch の更新です。team.md の決まりは「GitHub の画面でマージせず、手元で版と lockfile を更新し、`./gradlew verify` を通して `develop` に統合し、PR を閉じる」です。取り込む前に `hasInstallScript` の増減を確かめて記録します。

A. 既存の決まりどおり、専用の短命ブランチで取り込む（E2E は Build and Test で1回）
B. 他の変更と同じブランチにまとめる
X. Other (please specify)

[Answer]: A

## Q5: k6 の結果から JWT を自動で伏せる仕組みの確かめと、記録への置き方の決まり
現状、JWT を伏せる手順は project.md の学びにあるだけで、perf/README.md にも仕組みにもありません。置き場（後処理のスクリプトか k6 の台本の出力か）は設計の段で決めます。ここでは確かめ方と決まりの強さを決めます。セキュリティ担当は、道具を通さない手置きが残るため、固い制約を足すことを求めています。

A. 仕組みのテスト（冪等・入れ子・JWT 以外を壊さない性質を含む）を `./gradlew verify` と CI に入れ、`gitleaks dir` で確かめる。固い制約は足さない
B. A に加え、「NEVER 認証の値（JWT・リフレッシュトークン・Authorization ヘッダー）を伏せずに perf の記録へ置かない」を Forbidden に足す
X. Other (please specify)

[Answer]: B

## Q6: Mailpit・lgtm のコンテナを入れ替えた後の確かめ
前の Intent で、止まっている lgtm のコンテナが作り直されず古い版のまま残りました。セキュリティ・品質の両担当は「入れ替えの後に `docker inspect` で版とダイジェスト（RepoDigests）が compose.yaml と一致するか確かめる」を決まりにすることに賛成、開発担当は「project.md に同種の学びが既にあるため手順書の項目にする」を提案しています。

A. team.md の Deployment に決まりとして足す（ダイジェストまで確かめると明記）
B. 今回の手順書（Deployment Pipeline の段）の項目にとどめ、決まりには足さない
X. Other (please specify)

[Answer]: A

## Q7: perf 文書と k6 台本の「基準の持ち主」の整理
perf/README.md と k6 の台本で、どの値が基準か読み取りにくい状態です。初稿は「基準は perf/versions.env と compose.yaml に寄せ、README・台本は参照にする」案ですが、3人とも「実践に足すほど安定した決まりではなく、設計の段で決める」に賛成です。確かめ方は `k6 inspect --include-system-env-vars` の前後比較にします。

A. 実践には足さず、設計の段で決める
B. 方針を Code Style（ドキュメント）に足す
X. Other (please specify)

[Answer]: A

## Q8: 参照先を読む SQL の置き場の順序
SQL の文を作ってよいクラスは境界テストで固定されています。開発担当は「既存の `RowSqlExecutor` と `RowDialect` の実装（MySQL 系・PostgreSQL）に足す形を既定にし、新しいクラスを作るときだけコード生成の計画に明記して承認を得る」と順序をはっきり書くことを求めています。既存の境界テストと SpotBugs の除外は緩めません。

A. 既存の部品に足す形を既定にする、と team.md の Code Style に明記する
B. 要件・設計の段で決め、実践には足さない（今の「緩めるときは計画で承認」のまま）
X. Other (please specify)

[Answer]: A

## Q9: 今回の Intent で新しく述べたい固い制約や、今は当てはまらない既存の決まり
上の質問に出ていない、ALWAYS/NEVER にしたいことや、team.md・project.md の記述で古くなっているものがあれば教えてください。

A. なし
B. ある（自由記述）
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

- Q1: 参照先の権限の当て方は要件定義で決める（実践には足さない）
- Q2: 必須テストは (a)〜(k) をすべて Testing Posture に足す
- Q3: 参照ピッカーで E2E を1本足す（自作の前提・専用スキーマ・axe の条件つき）
- Q4: Dependabot #81・#82 は既存の決まりどおり専用ブランチで取り込む
- Q5: JWT の伏せ字の仕組みのテストを verify と CI に入れ、gitleaks dir で確かめ、Forbidden に「認証の値を伏せずに perf の記録へ置かない」を足す
- Q6: コンテナの入れ替えの後の docker inspect（版とダイジェスト）の確かめを Deployment の決まりに足す
- Q7: perf の基準の持ち主の整理は実践に足さず、設計の段で決める
- Q8: 参照先を読む SQL は既存の部品に足す形を既定にする、と Code Style に明記する
- Q9: ほかの固い制約・古い決まりはなし

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
