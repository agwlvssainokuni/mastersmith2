## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-03T13:56:44Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/infrastructure-design/traceability.json > coverage NFR2.1・NFR3.1 | NFR2.1（メニュー API の p95 が 1 秒以内）と NFR3.1（実ブラウザの axe の違反 0 件）が OK になっている。しかし cicd-pipeline.md の 4 節では、どちらも Build and Test が測る引き継ぎ（H1・H3）で、この段では測っていない。測っていない目標を満たした扱いにしている | NFR2.1 と NFR3.1 の status を Deferred にし、target に持ち主の段（Build and Test）と H1・H3 を書く。設計として確かめた部分（問い合わせを増やさない・E2E 160 の拡張）は別の行か注記で残す | New |
| R-02 | Major | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/infrastructure-design/cicd-pipeline.md > 3.1 配備の手順・3.2 戻し方 | 戻し先を「前の版のイメージ（pre-… のタグ）」としているが、3.1 に入れ替えの前に今のイメージへ pre- のタグを付ける手順が無い。docker compose up --build app は mastersmith:local を上書きするため、タグが無いと戻し先が残らない。この単位はサーバーのコードも変えるため、戻しは画面だけでなくサーバーも古い版にする必要がある | 3.1 の手順 1 の前に、現在のイメージ ID を記録して pre- のタグを付ける手順（と、タグの名前の付け方）を足す。戻した後にイメージ ID を確かめる手順も 3.2 に書く | New |
| R-03 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/infrastructure-design/cicd-pipeline.md > 2 節の表 段 4・段 9 | SpotBugs（FindSecBugs）を段 4 に置いているが、実物の build.gradle.kts では SpotBugs（spotbugsGate）は段 8 の安全の検査にある。段 9 は「既存のタスクのまま」とあるが、実物は WAR の組み立てに加え、素材の一致の確認（verifyFaviconInWar）と初回の読み込みの量の測り（frontendBundleSize、500KB 超は警告）を含む。ホームの部品の追加と管理の機能の削除でこの量が動く | SpotBugs を段 8 に移し、段 9 の内容を実物どおりに書く。bundle:size の値の前後を Build and Test で記録する旨を H に足す | New |
| R-04 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/infrastructure-design/monitoring-design.md > Dashboards・cicd-pipeline.md > 3.3 S7 | 「業務のメニューと置き場」の区画と呼んでいるが、実物のダッシュボードにその名前の区画は無い。該当するのは「ロール・グループ・メニュー」の行にあるパネル「API ごとの要求の数（1 時間）」とその p95・5xx のパネルで、「業務のメニューと置き場」は説明文の中の語にすぎない。要求の数のパネルは 1 時間の増分で、S1〜S3 の間だけを切り出す手順が無い | S7 とダッシュボードの節で、実在するパネルの名前を使い、uri が /api/me/navigation の行を見る手順にする。1 時間の窓での読み方も書く | New |
| R-05 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/infrastructure-design/cicd-pipeline.md > 3.3 の監査の期待・S6 | 管理者でない利用者が管理の道を開く S6 は、サーバー側の 403（ACCESS_DENIED）を伴う操作を含みうる。監査の期待は「S1〜S7 は監査に残る操作を含まない」としているが、403 を監査に残すかを確かめた記述が無い | S6 が呼ぶ API と、403 が監査・ログに残るかをソースで確かめ、期待の件数に書く（残らないならその旨を明記する） | New |
| R-06 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/infrastructure-design/cicd-pipeline.md > 4 節 H1〜H7 | 引き継ぎのうち、統合の前の E2E（1 節で team.md により必須）と、パッケージごとのカバレッジの実測値の記録に持ち主の段が付いていない。H7 の持ち主は Code Generation の記録で、この段の渡し先ではない | E2E の統合前の実行とカバレッジ値の記録を H として足し、持ち主（Code Generation・Build and Test）を明記する | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| 実物の読み取り（Dockerfile） | COPY backend/build/libs/mastersmith.war のみ。ビルドは含まない | 「Dockerfile は WAR を写すだけ」の主張は正しい |
| 実物の読み取り（build.gradle.kts の verifyStages・ci.yml） | 段 0〜9 の並びと、CI が ./gradlew verify を流し timeout 60 分 | 段の並びと CI の主張は概ね正しい。SpotBugs の段と段 9 の内容のみ上の指摘のとおり |
| 実物の読み取り（backend/build.gradle.kts） | packagesJudgedByTotal に navigation.* は無い。WAR の画面は WEB-INF/classes/static | navigation.* のパッケージごとの下限の主張と WAR の確かめの主張は正しい |
| 実物の読み取り（navigation のテスト・e2e） | 挙げられたテストのクラスと E2E 030・090・130・160 は実在する | 引用先は解決する |

### Summary

実物との事実の突き合わせは概ね合っており、新しい資源を作らないという主張に反する点は見つからなかった。ただし、測っていない目標（NFR2.1・NFR3.1）を OK にしている点と、戻し先のタグを付ける手順が抜けている点の 2 件の Major は、承認の場で直してから進めるとよい。件数は Major 2 以内のため評決は READY。
