## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/nfr-design/traceability.json > upstream_ids・coverage と nfr-design の各 design.md | traceability.json の上流の一覧が NFR2.6・NFR3.1・NFR5.4・NFR6.1・NFR6.2・NFR8.1・NFR9.1〜9.3 を持たない。このうち NFR2.6（DD-17・DD-22）・NFR3.1・NFR8.1（DD-18）・NFR5.4（DD-10・DD-21）・NFR6.2・NFR9.x（logical-components.md の構造のテスト）は設計の文書に扱いがあり、対応表への書き漏れ。NFR6.1（JaCoCo の記録を取らない・計測の除外を足さない）は、どの設計の文書にも決定が無い（DD-8〜11 は NFR6 を挙げない）。 | traceability.json に上記の ID をすべて足し、対応先の DD を書く。NFR6.1 には、設計の決定（internalDbPostgresTest の JaCoCo を有効にしない・計測の除外を足さない・確かめ方）を performance-design.md か logical-components.md に 1 件足して対応づける。 | New |
| R-02 | Major | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/nfr-design/reliability-design.md > DD-2・T1・T2 | 試しの読み（犠牲側は輪が閉じる時刻と先の側の 1 秒の検査の前後で決まる）は正しい。しかし DD-2 は「アプリが犠牲」に合否を固定し、保持側が犠牲になったらテストの失敗とする。余裕は deadlock_timeout 1 秒から、アプリが待ちに入ってから観測して輪を閉じるまでの遅れ（約 0.13 秒）を引いた約 0.8 秒で、CI や負荷の高い PC で観測がそれ以上遅れると、原因が環境の揺れだけの失敗になる。team.md が避ける不安定なテストになりうる。BR3.3 は「検出された側が排他の失敗で失敗し、もう一方は続けられる」までしか求めておらず、アプリ側への固定は機能設計より強い。アプリが 1 つの処理の中で先に 1 行を持ち、次の行を待つ形を、アプリの API のどの経路で作るかも書かれていない。 | 合否を「ちょうど一方が 40P01 で失敗し、もう一方が続けられ、アプリ側の失敗は排他の失敗の区分に当たる」に直す（アプリが犠牲なら区分を確かめ、保持側が犠牲ならアプリが続けられることを確かめる）。または、輪が閉じるまでの余裕を数値で示し、その余裕を割ったときの扱いを書く。deadlock_timeout が既定の 1 秒であることを SHOW で確かめることと、アプリが 2 行を順に取る経路を決める。 | New |
| R-03 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/nfr-design/reliability-design.md > DD-3 | 時差の戻し忘れを「構造のテストの隣の単体のテスト（`*Test`）で確かめる」とあるが、`*Test` は普段の test タスクで流れ、internalDbPostgresTest の中の IT の後の既定の時差は読めない。確かめがどこで動くか決まっていない。また「接続プールの接続は作成時の時差のまま」は、プールが時刻のテストの間に接続を作り直すと成り立たない（共有のアプリは起動したまま残る）。単体のテストが増えると、DD-9 の「普段の verify に加わる新しいテストは構造のテスト 1 件」とも合わない。 | 戻しの確かめは internalDbPostgresTest の中（時刻のテストの後に実行される IT、または `@AfterAll` の表明）に置く。プールの接続の作り直しの影響は、時刻のテストの間は共有のアプリの接続を使わないか、影響が無いことの根拠を書く。DD-9 の件数を実際の `*Test` の数（構造のテスト・ソースの読み取りのテスト）と合わせる。 | New |
| R-04 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/nfr-design/logical-components.md > 構造のテスト・許す依存 | 許す依存の一覧が「など」で終わり、構造のテストが書ける固定の一覧になっていない。NFR 要件のレビューが求めた「クラス名で固定する」を、Code Generation の計画へ送っている。MutableClock は auth の testsupport にあり、U3 から他の機能の testsupport を読む形の可否も書かれていない。 | 許す部品をクラス名で列挙して固定し（HttpTestClient・JsonLogRecords・LogEvents・MutableClock・TestDatabase.randomSecret・InternalDbPostgres 系）、「など」を消す。他の機能の testsupport を読む依存を許すかを 1 行で決める。 | New |
| R-05 | Info | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u3-postgres-behaviour-tests/nfr-design/reliability-design.md・security-design.md > 上流との差 | NFR 要件のレビューの持ち越しは解けている。時間の起点は T3 の実測（2.15 秒と 2.02 秒の差が観測の遅れ 0.13 秒と整合）で呼び出しの開始に直し、差を記録した（DD-1）。randomSecret は InternalDbPostgresDatabase が実際に TestDatabase.randomSecret を呼んでいるため許す側にするのが妥当（DD-14）。時刻は別のクラスに決めた（DD-3）。DD の番号は DD-1〜22 で重複・欠番が無く、参照も合っている。src/main を変えない・依存を足さない・H2 の部品を変えないという境界とも矛盾しない。 | 対応は不要。 | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| なし（Read だけで確認する指定） | 実行せず | DD 番号の一覧と参照、NFR 要件の ID と対応表の突き合わせを読みで行った。実在の確認として、U1 の InternalDbPostgresDatabase が TestDatabase.randomSecret を呼ぶこと、許す部品の主要なクラスのファイルが存在することを Read と検索で確かめた。 |

### Summary

設計の決定は NFR の大半を測れる形で扱い、使い捨ての試し T1〜T4 の読みも概ね正しく、要件のレビューの持ち越しも解けている。Major は 2 件（対応表の漏れと NFR6.1 の決定の欠落、行き詰まりの犠牲側をアプリ側に固定した合否が不安定になりうる点）で、承認の場で直せる範囲のため READY とする。
