## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-08T23:21:31Z
**Iteration:** 2
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | backend/build.gradle.kts > tasks.jacocoTestCoverageVerification（パッケージごとの rule）と code-summary.md 2節 Step 5 | `packagesJudgedByTotal` と `excludes` を消した結果、残り7パッケージのうち common.health 以外の6つ（access.service・audit.repository・common.error.domain・common.error.service・common.i18n.domain・common.web）は、この段で一度も実測していない。common.health も確かめたのは単体テストだけの行 53/53 で、分岐の割合（70% 以上）は未実測。audit.repository は「interface だけで JaCoCo が数えない」という仮定のまま。外す仕組みを先に消したため、1つでも下限未満なら全件の verify と CI が落ちる。 | 承認の前か Build and Test の最初に、`:backend:cleanTest :backend:cleanIntegrationTest` を付けた全件の `./gradlew verify` で全パッケージの行・分岐を実測し、記録する。下限未満のパッケージがあれば、除外や一覧を戻さずテストを足す。その実測が通るまで develop へ統合しない、と段の成果物に明記する。 | Resolved (reviewer) |
| R-02 | Minor | scripts/ops-readonly-check.mjs > checkContainers と main の終了コード | Docker の実行環境に繋がらない（colima 停止・DOCKER_HOST 抜け）ときも、動いているコンテナが 0 件のときと同じ文面で終了コード 0 になる。コンテナを入れ替えた後の確かめ（版とダイジェストの一致）に使うと、確かめていないのに通ったように見える。 | 繋がらないときは、食い違い（1）とも通った（0）とも違う終了コード（例 2）にして、出力の1行目にも「確かめていない」と書く。 | Resolved (reviewer) |
| R-03 | Minor | scripts/ci-attempts.mjs > main の tip 判定（`runs` を createdAt で並べた最後）と parseArgs の既定（origin/main・origin/develop） | 先頭のコミットの合否を「最も新しい実行」で決めるため、FR4.2 で予定している it_forks の比べ（workflow_dispatch で同じコミットに流す）の実行や、取り消した実行が最後になると、push の実行が通っていても終了コードが 1 や 2 になる。また `git fetch` をしないため、手元の origin/* が古いと範囲が黙ってずれる（code-summary の照合は fetch の後に流している）。 | 先頭の判定は event が push の実行に限る（dispatch の実行は表に出すだけ）。範囲の両端の SHA を出力に書き、docs/verification.md の手順に「先に git fetch」を入れる（または道具の中で fetch する）。 | Resolved (reviewer) |
| R-04 | Minor | scripts/count-db-copy.mjs > main（作業の置き場 build/count-db-copy/） | 利用者のメールアドレスなどを含む複写を、リポジトリの build/ の下（gitignore 済み）へ展開する。後始末は finally と SIGINT・SIGTERM だけで、SIGKILL・端末の切断・停電では残る。起動時に前の実行の `work-*` を掃除しないため、残ったものが次に気づかれない。 | 起動の最初に build/count-db-copy/ の古い `work-*` を消す（または残っていたら警告して止まる）。docs/internal-db.md に「中断したら build/count-db-copy/ を確かめる」と書く。 | Resolved (reviewer) |
| R-05 | Minor | .github/workflows/ci.yml > env.IT_FORKS_DEFAULT と inputs.it_forks | develop へのプッシュのたびに、CI の上でまだ比べていない N=3 が既定になる（対象DB のコンテナは JVM ごとに起動するため、3種類×3 JVM の同時起動になりうる）。入力の差し込みは環境変数経由・引用符付きで安全。値の上限は Gradle 側で下限（1 以上）しか見ないため、手動の実行で大きな値を入れると runner の資源を使い切りうる。 | 比べるまでの既定が 3 であることを docs/verification.md に明記し、失敗が続いたら 1 に戻す手順を書く。必要なら入力の値に上限（例 8）を足す。 | Resolved (reviewer) |
| R-06 | Minor | aidlc/spaces/default/codekb/mastersmith2/architecture.md ほか（component-inventory.md・code-quality-assessment.md・dependencies.md・code-structure.md） | 派生の記録がまだ `packagesJudgedByTotal` を現役の仕組みとして書いている。team.md と食い違う読み物が残る。 | 次の Reverse Engineering で更新するか、各ファイルの該当箇所に「Intent 261108-ci-checks-release で廃止」と注記する。 | Unresolved |
| R-07 | Minor | aidlc/spaces/default/memory/team.md > Testing Posture の「バックエンドでは、全体の合計に加えて…」と次の「パッケージごとの下限は…」 | 書き換えの結果、隣り合う2つの項目が同じ内容（全パッケージに同じ下限）を言っている。食い違いは無いが、読みの源が2つに割れる。 | 後の項目に統合して1つにする（依頼者の決定のうえで）。 | Resolved (reviewer) |

> R-01 Reviewer note: 計画の Summary の Tests に「全パッケージの下限は Build and Test の全件の verify で実測し、それが通るまで develop へ統合しない（R-01）」が入った。6 パッケージの実測は要件の段の質問の「決まっていること」の表にあり、行 80% 以上・分岐 70% 以上を満たす（access.service は分岐なし、audit.repository は数える行なしで Build and Test で確かめると明記）。common.health は行 79.25% で、テスト 2 件を足した。依頼者の決定どおりの扱いで、統合の関門は計画に書かれている。

> R-02 Reviewer note: ops-readonly-check.mjs は、Docker に繋がらない・compose.yaml が無い・docker inspect が読めないときに null を返し、終了コード 2 とし、出力の 3 行目に「1 の比べは確かめていない」を差し込む。1 と 5 も「確かめていない」と出す。docs/operations.md の終了コードの説明も合っている。

> R-03 Reviewer note: ci-attempts.mjs は先頭の判定を event が push の実行に限り、push の実行が無ければ「push の実行なし」で終了コード 1。範囲の両端の SHA を出力する。docs/verification.md に git fetch origin の手順と判定の読みが入った。

> R-04 Reviewer note: count-db-copy.mjs は起動の最初に removeLeftovers で build/count-db-copy/ の古い work-* を消し、件数を標準エラーに出す。docs/internal-db.md に中断したときの確かめが入った。

> R-05 Reviewer note: ci.yml は入力を環境変数 IT_FORKS で受け、case で 1〜8 の一桁だけを通し、外れたら ::error:: で止める。docs/verification.md に既定 3 と、失敗が続いたら IT_FORKS_DEFAULT を "1" に戻す手順がある。なお ci.yml 冒頭のコメント（19 行）は「Gradle の側が確かめる」とだけ書いており、1〜8 の上限は手順側にあることが書かれていない。読み違いの恐れは小さく、止める理由にはならない。

> R-06 Reviewer note: 知識ベースの派生の記録に packagesJudgedByTotal の古い記述が残る。知識ベースは Reverse Engineering の段でしか書き換えられないため、次の Intent の Reverse Engineering で直すことを依頼者が受け入れ済み。この回は据え置きでよい。

> R-07 Reviewer note: team.md の Testing Posture は、「すべてのパッケージごとにも同じ下限を当てる（外すパッケージは無い）」の1項目にまとまり、重なっていた「ただし…新しいパッケージだけに当てる」の項目は無い。読みの源は1つになった。

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| Read による確認（ops-readonly-check.mjs・ci-attempts.mjs・count-db-copy.mjs・ci.yml・docs・team.md・要件の質問の表） | 直しは R-02〜R-05・R-07 の指摘の内容に合っている | 新しい欠陥は見当たらない。コマンドは流していない（依頼のとおり Read のみ） |

### Summary

前回の指摘は R-06（依頼者が受け入れ済みで次の Reverse Engineering に回す）を除いて直っており、R-01 は全件の verify の実測を統合の関門にする扱いが計画に書かれた。新しい指摘は無く、READY とする。
