# Requirements Analysis の質問（Intent 261015-deps-ui-carryover）

依頼:「第1の束と第2の束を同じintentで対応」。束は、前の Intent 261004-role-menu の振り返り（`aidlc/spaces/default/intents/261004-role-menu/operation/feedback-optimization/feedback-loop.md` 2節）の第1の束（依存の更新と性能の確かめ直し: U1・P1〜P7）と第2の束（画面・コードの小さな直し: K1〜K5）です。

## 決まっていること（質問にしない）

- **Dependabot の知らせの受け方**: GitHub の画面でマージせず、手元で版と lockfile をまとめて更新し、`./gradlew verify` を通してから `develop` に統合し、プルリクエストは閉じる（`team.md` の Way of Working）。イメージは版のタグと複数アーキテクチャの index のダイジェストで固定し、`.github/dependabot.yml` に書いた Dependabot が見ない固定先もそろえる（`team.md` の Deployment）。`vite` は成果物を作る道具の一覧（`config/npm-build-tools.txt`）に入っている。今開いている知らせは 20 件で、振り返りの内訳と一致する。
- **make-you-chic-ui の依頼の残り（K1）**: 上流の `main` に、依頼（3）に当たるコミット `a9309c4`（Dropdown を画面に収める口、畳んだサイドバーの今の項目の印、トップバーの文言の口）が公開されている（今の固定先は `5bf1ffe`）。固定先の更新の専用のコミットで取り込み、その単位は fast-forward で統合する（`team.md` の例外）。取り込んだら、アプリの CSS の `:has()` での上書きを外して新しい口に置き換える。
- **漏えいに関わる直し（K2 R-05 の DSL の誤りの抜粋、K3 R-03 の record の `toString`）**: 直す前に `*SecretLeakIT` の形の再現のテストを書き、同じコミットに入れる（`project.md` の Mandated）。
- **性能の目標は緩めない**: P1（ロールの書き込みの2本使いの待ちの最大 20 ms 未満）などの目標は、測り直しで外れても緩めて「満たした」ことにしない。
- **負荷の試験の持ち主**: この Intent の流れに Performance Validation の段が無いため、k6 と測定は Build and Test で流す（`project.md` の学び）。長い試験は `caffeinate -dims` で包み、眠りが入った回は使わない。k6 の setup は前の場面の状態に頼らない形にする（P3）。
- **画面に関わる変更**: 統合の前に E2E（`./gradlew e2eTest`）を手元で流す。実際のブラウザの axe は、ブランドカラーとテーマのすべての組で行う。
- **特定できていない指摘の対象**: K2 の R-04・R-07・R-08 と K4 の R-03・R-05〜R-07 は、コード生成の計画で前の Intent のレビューの記録から対象のファイルを特定する。

## 質問

### Q1. 作業の区切り方（ブランチと統合の形）

束をまとめて1つの Intent で直すため、統合の形が混ざります。make-you-chic-ui の取り込み（K1）は fast-forward、ほかは squash が決まりです。どう区切りますか？

A. 3つの短命のブランチで順に進める: ①依存の更新（U1、squash）→ ②make-you-chic-ui の取り込みと画面・コードの小さな直し（K1〜K5、fast-forward）→ ③負荷の試験の台本の直しと性能の確かめ直し（P1〜P7、squash）。新しい土台のイメージで性能を測れる順
B. 2つに分ける: ①依存の更新と性能（U1・P1〜P7、squash）→ ②make-you-chic-ui と画面・コード（K1〜K5、fast-forward）
C. 1つのブランチにまとめ、make-you-chic-ui の専用のコミットを残すため全体を fast-forward で統合する
X. Other (please specify)

[Answer]: A

### Q2. Dependabot が見ない固定先をどこまでそろえるか（U1）

読み取りで、Dependabot の知らせが来ないのに同じイメージを指す固定先が見つかりました。アプリの土台の `eclipse-temurin` の jdk（`docker/hikari-pool.sh`・`perf/README.md`・`README.md`。jre の #38 と同じ版の jdk のタグがあるかは未確認）、`README.md` の mailpit（ダイジェストが無い）、`grafana/k6`（知らせの経路が無く 20 件に入っていない）です。どこまで上げますか？

A. 20 件に加え、同じイメージを指す固定先（eclipse-temurin の jdk を jre と同じ版に、mailpit のダイジェストを足す）をそろえ、`grafana/k6` も最新に上げてダイジェストで固定する
B. 20 件と、同じイメージを指す固定先（jdk・mailpit）だけをそろえる。`grafana/k6` は今の版のまま
C. 20 件だけ取り込み、固定先のずれは記録に残して後の Intent に回す
X. Other (please specify)

[Answer]: A

### Q3. 取り込むと検査が通らない更新が出たときの扱い（U1）

20 件の中に、`./gradlew verify`・E2E が通らない更新（大きな版の変更など）が出たときの扱いを先に決めます。

A. その件だけ見送り、`.github/dependabot.yml` の `ignore` に見送る理由と外す時期を書き、残りを取り込む。ただし重大度 High 以上の脆弱性の直しを含む更新は見送らず、直して取り込む
B. 見送らず、直して全件取り込む
C. 通らない件が出るたびに、直すか見送るかを依頼者に諮る
X. Other (please specify)

[Answer]: A

### Q4. ロールの書き込みの待ちの最大の確かめ直し（P1）

前の Intent で、ロールの書き込みが接続を2本使うときの待ちの最大が、合否の回の1回目で 23.55 ms（目標 20 ms 未満）、流し直しで 8.55 ms でした。原因は分かっていません。合否の回を何回流し、どこまで追いますか？

A. 合否の回を 5 回流し、20 ms 以上の回の数と、その回のログ・指標の見立てを記録する。原因が分かって直せるときだけ直す（直す形はその時点で依頼者に諮る）
B. 10 回流し、同じく記録する（回数を増やして出る割合を確かに見る）
C. 20 ms 以上の回が出なくなるまで原因を追い、直す
X. Other (please specify)

[Answer]: A

### Q5. 大きな入力のヒープの見直し（P2）

10 MiB の DSL の投入で GC の後のヒープが 573〜746 MB あり、見積もり（256〜384 MB）の約 2 倍でした。読み込みの部品（`SafeYamlParser`。DSL と権限の YAML の両方が使う）には1行の長さの上限がありません。どこまで行いますか？

A. 試しの測定でヒープを取り直して見積もりを直し、1行の長さの上限を足し、メモリの上限（compose の 2g）と最大ヒープの割合（`MaxRAMPercentage` 50）も見直す
B. 試しの測定と見積もりの直し、1行の長さの上限を足すことだけ行う。メモリの上限と最大ヒープの割合は今のまま
C. 試しの測定と見積もりの直しだけ行い、コードと設定は変えない
X. Other (please specify)

[Answer]: A

### Q6. BUSY（行の排他の待ちの上限切れ）の確かめ方（P4）

負荷の試験のどの回でも `ROLE_BUSY`・`GROUP_BUSY` が 0 件で、ログ（L3）と応答（L4）の結び付きを確かめられていません。結合テストには `RoleBusyLogTraceIT`・`GroupBusyLogIT` があります（中身が結び付きをすでに確かめているかは未確認）。

A. 既存の結合テストの中身を確かめ、結び付きが足りなければ結合テストに足す。k6 では上限切れを狙わない（前の Intent の `USER_ADMIN_BUSY` と同じ形）
B. k6 に、行の排他の上限切れを起こす場面を足して確かめる
C. A と B の両方を行う
X. Other (please specify)

[Answer]: A

### Q7. 残りの性能の項目をどこまで入れるか（P5〜P7）(select all that apply)

振り返りでは見積もりのままや「次に起きたら」とした項目です。今回入れるものを選んでください。

A. P5: DSL の投入と権限の YAML の確かめを重ねる k6 の場面を足して流す
B. P6: 置き場の p95 の目標（実測の約 30 倍のゆとり）を締める側に見直す（新しい目標の値は Build and Test の実測をもとに決める）
C. P7: 単体テストの JVM のヒープ（1536m）を、境界テストの読み込みを共有する直しで下げる
D. どれも入れない（記録だけ残す）
X. Other (please specify)

[Answer]: D

## Consolidated Summary Confirmation

- Q1: A — 3つの短命のブランチで順に進める（①依存の更新 U1・squash → ②make-you-chic-ui の取り込み `a9309c4` と画面・コードの小さな直し K1〜K5・fast-forward → ③k6 の台本の直しと性能の確かめ直し P1〜P4・squash）。
- Q2: A — 20 件に加え、Dependabot が見ない固定先もそろえる（eclipse-temurin の jdk を jre と同じ版に、README.md の mailpit にダイジェストを足す、grafana/k6 を最新に上げてダイジェストで固定する）。
- Q3: A — 検査が通らない更新はその件だけ見送り、`.github/dependabot.yml` の `ignore` に理由と外す時期を書く。重大度 High 以上の脆弱性の直しを含む更新は見送らずに直して取り込む。
- Q4: A — ロールの書き込みの待ちの最大（P1）は合否の回を 5 回流し、20 ms 以上の回の数と見立てを記録する。原因が分かって直せるときだけ直し、直す形はその時点で依頼者に諮る。目標は緩めない。
- Q5: A — 大きな入力のヒープ（P2）は、試しの測定で取り直して見積もりを直し、読み込みの部品に1行の長さの上限を足し（DSL と権限の YAML の両方）、メモリの上限と最大ヒープの割合も見直す。
- Q6: A — BUSY の結び付き（P4）は、既存の結合テストの中身を確かめ、足りなければ結合テストに足す。k6 では上限切れを狙わない。
- Q7: D — P5（重なりの場面）・P6（置き場の p95 の目標）・P7（単体テストのヒープ）は今回入れず、記録だけ残す。
- 決まっていることとして、P3（k6 の台本の setup を前の場面に頼らない形に直す）は ③に含め、漏えいに関わる直し（K2 R-05・K3 R-03）は先に `*SecretLeakIT` の形の再現のテストを書く。

Does this all look correct before I generate the requirements artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
