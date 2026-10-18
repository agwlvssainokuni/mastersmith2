**Collaborator:** aidlc-developer-agent

## Contribution

支援役（開発）として、命名・層の境界・エラーの扱い・ファイルの置き方・コードの書き方の観点で、リードの下書き（`team-practices.md`・`discovered-rules.md`・`evidence.md` の P1〜P14）を見た。読んだのは、コード知識ベースの K-49〜K-51・K-54・K-61 の本文、`backend/src/main/java/cherry/mastersmith/` の機能の一覧と `targetdb/` の中身、`common/observability/TraceAspect.java` の対象の式、`*ProblemTypes.java` の置き場、`frontend/src/features/README.md`・`frontend/src/shared/` と `frontend/src/features/tables/` の一覧、`frontend/package.json` の依存。機能の中身（書き込みを範囲に入れるか、監査の中身、ページの大きさの値など）は、リードと同じく要件・設計の段に回す。

### リードの論点への意見

- **P1（E2E の時点）**: 推奨に同意する。「権限の判定に触れる」を、サーバー側の `role`・`navigation`・新しい業務データの機能の本体のソースと、画面の `src/app/`・`src/features/` の変更と読む、と文に書いておくと、ブランチごとの判断が割れない。
- **P2（E2E の対象DB）**: 推奨に同意する。加えて、E2E が使う対象DB の読み取り（と書き込み）の資格情報は、`project.md` の学び（`webServer.env` に置いた値は json の報告に残る）に合わせ、プロセスの環境変数で渡し、報告に入っていないことを確かめる、を同じ行に書く。
- **P3（本数）**: 同意する。
- **P4（必須のテストの一覧）**: 一覧の中身には同意する。2点を足したい。(1) 「信頼できない入力」に、画面の道に乗る主キーの値（複合の主キー・`/`・`%`・`?`・`#`・空白・引用符を含む値）のエンコードと、サーバー側でのデコードの後の照合を入れる（K-51。今の `team.md` の決まりはメニューの `table` の名前と DSL の YAML についてだけ）。(2) 「画面」に、行の値と DSL のカラムの表示名を文字として描く（HTML として描かない）ことを入れる（N 階層のメニューの決まりと同じ扱い。`react/no-danger` は oxlint が止めるが、テストで確かめる決まりが無い）。一方、discovered-rules の Mandated の「見せないカラム」の行には異論がある（下の Positions）。
- **P5（書き込みのテストと接続）**: 推奨に同意する。下の追加の論点 P15（トランザクションの置き場と時間の上限）と組で問うと、書き込みを範囲に入れたときの作りが決まる。
- **P6（SQL の組み立てと `SQL_` の除外）**: 方向に同意するが、除外の置き場の書き方を直したい。`targetdb/domain/SqlIdentifiers` は識別子を引用符で囲むだけの部品で、SpotBugs の `SQL_` の指摘が出るのは、文字列を `prepareStatement` などに渡す問い合わせの組み立ての側（repository の層のクラス）である。除外を `SqlIdentifiers` に限っても指摘は消えず、除外を広げる口実になりうる。除外は「問い合わせを組み立てる repository のクラス（クラスの名前で絞る）と `SQL_` のパターン名の組」に限り、そのクラスが識別子を `SqlIdentifiers` と DSL の名前の照合からだけ受け取ることを、ArchUnit か単体テストで確かめる形にしたい。あわせて、方言の差（ページ送りの `LIMIT`・`OFFSET`／`FETCH`、識別子の引用符、NULL の並び）は、既存の `MysqlSchemaQueries`・`PostgresSchemaQueries` の形にならって repository の DB の種類ごとのクラスに閉じ、service の層は DB の種類を知らない、を同じ行に足す（下の P17）。
- **P7（行の値の漏えい）**: 推奨に同意する。`TraceAspect` の式は `java.lang.Record+` の中の呼び出しを外すが、record を引数・戻り値に持つ web・service・domain・repository のメソッドは対象で、record の `toString` が TRACE に出る。行の値を持つ型は専用の record（または値の型）にし、`toString` で伏せ、JSON への書き出し（Jackson）は `toString` と別の経路で行う、と書くと、伏せ字と応答の中身が混ざらない。
- **P8（置き場と境界テスト）**: 推奨に同意する。K-50 のとおり `navigation` を広げる形は `navigation` の「DB の部品に依存しない」境界テストに当たるため、新しい機能のパッケージを先に検討し、`RoleBoundaryArchitectureTest` に許す相手を足す変更を計画で承認を得る形が、既存の決まりに最も素直である。業務データの機能の code の定数は、ほかの機能と同じく `<機能>.domain.XxxProblemTypes` と `<機能>.service.XxxProblemTypeCatalog` に置き、見られない・DSL に無い・行が無いを区別しない 403 は共通の `ACCESS_DENIED` を使い回す（置き場の API と同じ。重ねて定義しない）、を P8 か P4 の IDOR の行に書いておくとよい。
- **P9（axe-core）**: 同意する。上げるコミットで `.github/dependabot.yml` の `ignore` の行も外す、を書き足す。
- **P10（依頼（5））**: 同意する。前の Intent の学び（make-you-chic-ui の Table に行の開閉が無く、ページ送りの文言が日本語に固定）は一覧の画面にそのまま当たるため、依頼の一覧に最初から入れる。
- **P11（不安定なテストの確かめ）**: 同意する。
- **P12・P13**: 開発の観点で異論は無い。
- **P14（古い記述）**: 同意する。`frontend/src/features/registration/` の確かめはリードの記録のとおり。

### 抜けている論点の追加

- **P15 対象DB のトランザクションと時間の上限**
  - 推奨: 対象DB への問い合わせは、目的ごとの時間の上限を必ず付ける（今の `ReadPurpose` と `TargetDbProperties.QueryTimeout` の形に、一覧・詳細（と書き込み）の目的を足す。上限の値は設計の段で決める）。書き込みを範囲に入れるなら、対象DB のトランザクションは対象DB 用の別のトランザクションの管理者で service の層に置き、既定（内部DB）のトランザクションの中で対象DB に書かない。内部DB の接続を持ったまま対象DB を待たない（監査など内部DB への書き込みは、対象DB の確定の後に別の短いトランザクションで行う）。
  - 根拠: K-49（目的は2つだけ、`targetdb` は `@Transactional` を止める）、`team.md` の「トランザクションの境界は業務処理の層にだけ置く」、`project.md` の学び（要求1件で接続を2本使う経路は負荷の試験で確かめる、`DslRecordStore` で対象DB を読むあいだ内部DB の接続を持たない形にした）。今の基準には、2つの DB のトランザクションの分け方の決まりが無い。

- **P16 行の値の受け渡しの形（型と大きさ）**
  - 推奨: 行の値は、DB の型ごとの対応を repository の層の1か所で決めて JSON に写す。整数の長い型（`BIGINT` など）と小数（`DECIMAL`・`NUMERIC`）は文字列で渡し、画面で JavaScript の数に直さない（2^53 を超える値と小数の桁が崩れるため）。日時はタイムゾーンを足さず DB の値のまま文字列で渡す。バイナリと大きな文字列は、一覧では渡さないか長さの上限で切る（値は設計で決める）。対応は3種類の対象DB の結合テストで、境界の値（最大の整数・桁の多い小数・NULL）で確かめる。画面の型は `any` を使わず `unknown` か DSL の型から作った union で受ける。
  - 根拠: K-51（`DslColumn.dbType` は名前・長さ・精度・小数の桁を持つ）、`team.md` の tsconfig（`any` の禁止）、`project.md` の Corrections（保存する時刻は UTC。対象DB の値は内部DB と違い、アプリが変換すべきでない）。今の基準に、対象DB の行の値の表現の決まりが無く、単位ごとに割れると画面と API の型が食い違う。

- **P17 DB の種類ごとの SQL の置き場**
  - 推奨: 業務データの SQL の方言の差は、repository の層の DB の種類ごとのクラス（既存の `MysqlSchemaQueries`・`PostgresSchemaQueries`・`SchemaQueries` の形）に閉じ、service の層は DB の種類で分岐しない。MySQL と MariaDB は同じクラスで足りるかを、3種類の結合テストで確かめてから決める。
  - 根拠: 今の `targetdb/repository/` の形。P6 の除外の範囲を、このクラスに限れる。

- **P18 画面の側の置き場（機能と共通の部品）**
  - 推奨: 業務データの一覧・詳細は、今の置き場 `features/tables` を置き換える1つの機能に置き、テーブルを指す道を作るのは `toTablePlaceholderPath` に当たる1か所の関数のままにする（K-52。道の形を変えるときも1か所で変える）。一覧の表・ページ送り・値の書式など、ほかの機能でも使う部品は最初から `src/shared/`（`paging`・`format` の並び）に置き、機能どうしの import の禁止に当たらないようにする。権限の判定の結果は骨組みの `useWorkRole()` の `revision` の変化で読み直す（`features/README.md` の決まり）。
  - 根拠: `frontend/src/features/README.md`、`frontend/src/shared/` の一覧、`team.md` の Code Style（機能どうしは直接 import しない）、K-52（`170-menu-navigation-flow` と今の項目がこの道に依る）。

## Positions

- AGREE: P1 は前の Intent の決定と今の基準の間で、権限の判定に触れる変更だけ統合の前に E2E を流す形が今回の中身に合う。
- AGREE: P2 は E2E を PostgreSQL の1種類に絞り、届かなければ失敗させる形が Mailpit と同じで分かりやすい（資格情報はプロセスの環境変数で渡すことを足す）。
- AGREE: P3 は今の基準の数え方のとおり。
- AGREE: P4 の必須のテストの一覧は K-61 と K-50・K-51 から見て過不足が小さい（道に乗る主キーの値と、値を文字として描くことを足す）。
- OBJECT: discovered-rules の Mandated「見せないカラムは API の応答に含めない」は、カラムの主権限 NONE に限るべきで、DSL の表示の設定（`list.visible`・`detail.visible`）は見せ方の設定であり権限ではないため、固い制約に入れると主キーのカラムを一覧で隠したときの詳細への道などの設計を縛る。
- AGREE: P5 は K-49 の読み取り専用の前提から、別の接続・別のアカウント・別の接続での読み直しが要る。
- OBJECT: P6 の `SQL_` の除外を `SqlIdentifiers` の1か所に限る書き方は、指摘が出るのが問い合わせを組み立てる repository のクラスの側のため効かず、除外はそのクラスとパターン名の組に限り、識別子の出どころをテストで確かめる形にすべき。
- AGREE: P6 の discovered-rules の Forbidden（利用者の入力を SQL に文字列のまま組み込まない）は固い制約にしてよい。
- AGREE: P7 は `TraceAspect` の対象の式から見て必要で、行の値の型の `toString` と JSON の書き出しを分けることを足す。
- AGREE: P8 は `navigation` の境界テストに当たらない新しいパッケージを先に検討する形が既存の決まりに素直。
- AGREE: P9 は規則を外して通さない決まりが無いと、違反の数で取り込みが止まるたびに外す誘いが生じる。
- AGREE: P10・P11・P12・P13・P14 に開発の観点で異論は無い。
