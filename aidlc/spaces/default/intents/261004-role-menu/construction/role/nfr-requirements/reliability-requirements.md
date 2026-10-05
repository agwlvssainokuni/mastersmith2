# 信頼性の要件 — U4 role

## 出典

- `functional-spec.md`・`rules.md`（この単位の承認済みの機能設計。BR7.8・BR8.1〜BR8.6・BR9.10・BR9.11・BR10）
- `requirements.md`（NFR3.1・NFR3.2、要件 C2）
- `contract-summary.md`（C4・C5・C7・C10）
- `technology-stack.md`（コード知識ベース。行の排他の待ち 3000 ms）
- この段の答え: `nfr-requirements-questions.md` の Q1〜Q4 と、まとめの確認（「この段で決める要点」、group からの引き継ぎ）。
- group の NFR 要件の `reliability-requirements.md` と、そのレビューの R-07。

## ID の対応表

全体の表は `security-requirements.md` の「ID の振り方」にある。この成果物に書くのは次のもの。

| 上流の枝番 | この単位での扱い | この単位の ID |
|---|---|---|
| NFR3.1 同時の重なり | 当たる | NFR3.1 |
| NFR3.2 import の一括確定 | 当たる | NFR3.2 |
| （足す） | ROLE_BUSY、違反の後の監査、監査の書き込みの失敗、移行、捨ての試し、import の判定の順 | NFR3.3〜NFR3.8 |

## 要件

| ID | 要件 | 測り方・確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR3.1 | 次の同時の重なりで、終わった後の内部DB が不変条件を満たし、負けた側は業務の code の 4xx で 500 にならない（どちらが勝つかは問わない）。<br>(a) ロールの削除と、同じロールの割り当て: 消えていれば割り当ては 0 件、割り当てが残れば消えていない（AC1.1.6）。<br>(b) 同じ名前の同時の作成: ロールは1つだけ（AC1.1.11）。<br>(c) 同じロールの同時の保存: 両方成功し、値は後に確定した方。監査に2行、それぞれ正しい前後の値（AC1.2.12）。<br>(d) 作業ロールの切り替えと、そのロールの外し: 次の要求の有効な作業ロールは割り当ての中のロールか無し（AC4.1.5）。<br>(e) グループの削除と、同じグループへのロールの割り当て: 消えていれば割り当ては 0 件（AC2.1.5 のロールの側、group からの引き継ぎ）。<br>(f) import の確かめの後の、別の管理者の保存・削除・同じ名前の作成・DSL の適用し直し: 適用は拒否され、状態は別の管理者の操作の後のまま（AC3.1.4）。<br>(g) 同じ組の同時の割り当て: 割り当ては1行だけ | 結合テスト `RoleConcurrencyIT`（(a)〜(c)・(g)）、`WorkRoleConcurrencyIT`（(d)）、`GroupRoleAssignmentConcurrencyIT`（(e)）、`RoleTransferStaleIT`（(f)）。重なりは待ち合わせの口（`RoleBarrier`。本番は何もしない部品、テストは `@Primary`）で確実に作り、スレッドの数に頼らない（`team.md`）。(b)・(g) の負けた側の code（違反の `ROLE_NAME_DUPLICATE`・`ROLE_NO_CHANGE` か、待ちの上限切れの `ROLE_BUSY` か）は、NFR3.7 の捨ての試しの結果を受けて、NFR Design の承認で期待を確定する | Code Generation（(a)〜(c) は B4、(a) の割り当ての側と (d)・(e)・(g) は B5、(f) は B6）。(b)・(g) の期待の確定は NFR Design |
| NFR3.2 | import の適用は、1つのファイルの分を1つのトランザクションでまとめて確定する。途中で失敗したら、ロールも設定も変わらず、成功の監査は残らない（BR9.11） | 結合テスト `RoleTransferAtomicityIT`: テストだけの部品で N 件目（例 1,000 件目）の設定の書き込みで失敗を起こし、適用の後にすべてのロールと設定の行を読み直して、前と同じであることと、`ROLE_TRANSFER_APPLIED` の成功の行が無いことを確かめる（AC3.1.8） | Code Generation（B6） |
| NFR3.3 | ロールの行の排他の待ちの上限は、既存の行の排他と同じ 3 秒。上限切れと一意の鍵の待ちの上限切れは、巻き戻して 409 `ROLE_BUSY` で断り、監査に残さない（BR8.3・BR8.4）。巻き戻しの印を先に付け、DB に触れずに返す（利用者の管理と同じ形）。import の適用の間（最大 30 秒）に同じロールへ来たほかの管理の操作も `ROLE_BUSY` になり、状態は変わらない（受け入れた制約） | 結合テスト `RoleBusyApiIT`: 待ち合わせの口で排他を持ったまま止め、別の要求が 3 秒の後に `ROLE_BUSY` になり、読み直した状態が変わらず、監査の行が増えないこと。WARN は `observability-requirements.md` の NFR5.3 | Code Generation（B4） |
| NFR3.4 | 一意の制約・主キー・外部キーの違反を受けたときは、1つ目のトランザクションの中で巻き戻しの印を付けて「違反」の結果の型を返して終える（確定させない）。同じ業務処理の方法の中で、続けて2つ目の `TransactionTemplate`（書き込みの無いトランザクション）を実行し、その中で失敗の出来事だけを出す。入れ子の REQUIRES_NEW は使わない（1つ目の接続を持ったまま2本目を借りない。group と同じ形、BR8.5） | 結合テスト `RoleConflictAuditIT`: 同じ名前の同時の作成と、同じ組の同時の割り当てで、負けた側の失敗の監査の行が1件残り、`UnexpectedRollbackException` と 500 が出ないこと | Code Generation（B4・B5） |
| NFR3.5 | 監査の書き込みが失敗しても、操作は成功のまま（既存の決まり。`AuditWriteFailureIT` の形） | 既存の `AuditWriteFailureIT` と同じ形で、ロールの作成と作業ロールの切り替えの1つずつを確かめる | Code Generation（B4・B5） |
| NFR3.6 | 移行は表（ロール・権限の設定・利用者の割り当て・グループの割り当て・作業ロールの保存）を足すだけで、既存の列を消さない。`users.admin_flag` は残す。1つ前の版のアプリは、新しい表を知らないまま起動する（`ddl-auto: validate` は余分な表を許し、Flyway は前の版が知らない新しい移行を既定で無視する）。移行の番号は、コード生成の時点の次の空き番号を使う。B4 でロールと設定の表、B5 で割り当てと作業ロールの保存の表を足す | 既存の移行の結合テスト（Flyway の検証）が通ること。戻しの確かめ（前の版のイメージで起動し、管理の権限が管理者の印のまま変わらないこと）は配備の段 | Code Generation（B4・B5）、戻しの確かめは Deployment Pipeline・Deployment Execution |
| NFR3.7 | 次の前提を、NFR 設計でリポジトリの外の捨ての試しのコードで先に確かめ、結果の要点（版・設定・数値・設計への意味）を成果物に写す（`project.md` の学び）。<br>(1) 同じ名前の同時の作成・同じ組の同時の割り当てで、重なった側が違反になるか、待ちの上限切れになるか。<br>(2) YAML のキー `true`・`null`・`123`・`yes` が、`SafeYamlReader` の木で文字列のキーになるか（機能設計の再レビューの R-06）。ならないときは、書き出しでキーを常に二重引用符で囲み、文字列でないキーを中身の誤りにする。<br>(3) 10 MiB の YAML の確かめと適用の時間（NFR2.4）。<br>(4) 悪い側での `snapshotFor` 1回の時間（NFR2.3） | NFR 設計の成果物に結果を写す。(1) は NFR3.1 (b)・(g) と NFR3.4 の期待、(2) は往復の性質ベースのテスト（`tech-stack-decisions.md` の NFR6.2）、(3)・(4) は時間の目標の見通しに使う。(3) が目標に届かないときは、上限を下げる案を依頼者に諮る | NFR Design |
| NFR3.8 | import の適用の判定の順は、本文の型・大きさ → DSL の有無 → 読み込み → 中身の検証 → ロールの排他 → 一覧の作り直し → DSL の有無（排他の後にもう一度）→ 指紋 → 変える点の有無とする。DSL が外れたときは `ROLE_TRANSFER_STALE` ではなく `DSL_NOT_APPLIED` を返す（機能設計の再レビューの R-12 の手当て。BR9.10 の書き方との差として記録する） | 結合テスト `RoleTransferStaleIT` に「確かめの後に DSL が外れた」場合を足し、`DSL_NOT_APPLIED` になること | Code Generation（B6） |

## 機能設計の再レビューで残った点の手当て（まとめの確認のとおり）

| 指摘 | 手当て | 置き場 |
|---|---|---|
| R-04 BR3.3 の作業ロールの保存の削除が B4 に入る | 保存の削除は保存の表ができる B5 で足し、B5 の条件に削除と保存の掃除の結合テストを入れる | `tech-stack-decisions.md` のコード生成への引き継ぎ |
| R-05 別名の数え方 | コレクションを指す別名だけを数え、上限 0 の境界もその数え方で書く | `security-requirements.md` の NFR1.5 |
| R-06 YAML のキーの型 | 捨ての試しで確かめ、結果で書き出しの引用符と読み込みの誤りを決める | NFR3.7 (2) |
| R-07 書き出しが上限を超えうる | 止めずにヘッダーで知らせ、分けて読み込めることを README に書く | `scalability-requirements.md` の NFR2.9 |
| R-08 解決の写しの再利用と重さ | `resolve` は祖先の行だけ、たくさんの対象は `snapshotFor` を1回、300 ミリ秒の予算 | `performance-requirements.md` の NFR2.3 |
| R-09 契約 C5 の形の差 | U5 navigation の設計に `workRole` と `dslHash` の差を知らせる | `tech-stack-decisions.md` の U5 への引き継ぎ |
| R-11 YAML の中身の TRACE | 中身を伏せる型で受け渡し、`RoleSecretLeakIT` で確かめる | `security-requirements.md` の NFR1.9 |
| R-12 import の判定の順 | DSL の有無を指紋より先に置く | NFR3.8 |
| R-13 割り当ての一覧の全件返し | 上限を置かず、悪い側で時間を確かめる | `scalability-requirements.md` の NFR2.8 |
| R-14 AC4.1.13 との差 | 保存が同じロールを指す状態と読み、読み替え中は監査ありとして、テストを場合で分ける | `observability-requirements.md` の NFR5.6 |

## 障害と戻し

- アプリは1つ（組み込みの H2）で、可用性の目標は既存の配備の段の決まりのまま（新しい SLO は足さない）。
- 戻しはイメージを直前の版に戻す。新しい表は前の版から見えないだけで、データは残る。前の版へ戻した後にまた上げたとき、ロール・設定・割り当て・作業ロールの保存はそのまま使える。
- import の適用の失敗は、全件の巻き戻し（NFR3.2）で設定が半端に残らない。
