# Feedback & Optimization — 質問

Intent `260925-user-management` の最後の段です。運用の結果を振り返り、次の Intent への入力をまとめるための質問です。

## 前提（読み取りだけで調べた結果。2026-09-29）

- **配備**: 配備先は開発者の PC 上のコンテナです。配備した版は `83b572b`（イメージ `sha256:9e5243a30b77…`、上限 メモリ 2g・CPU 4、Flyway の版 8）です。
  - 見本の PostgreSQL（上限 512MiB）が動いています。
  - 手元の監視（lgtm）と Mailpit は、見たいときだけ起動します。今は止まっています。
  - VM は colima の CPU 4・メモリ 6GiB です。
- **戻しの用意**: 戻し用のタグ `mastersmith:pre-user-management`、配備の前の内部DB の複写、配備の前の `.env` の複写があります。
- **SLO**: 仮の目標は前の Intent の記録を正とし、配備先が決まったときに正式に決めます（project.md の Deployment）。
  - 手元の監視は常には動いていないため、30 日の稼働率や誤りの予算の消費の実測はありません。
  - Performance Validation で、この段が持ち主の 21 件はすべて Met でした（使い捨ての環境、同時 10）。
- **利用と障害**: 配備してから数時間で、利用者は依頼者1名です。障害は起きていません。依頼者の判断で、配備したアプリを Performance Validation の間に約 24 分半、Observability Setup の間に数十秒止めました。
- **費用**: クラウドの費用はありません（配備先が決まるまでクラウドの基盤は作らない）。資源は VM の CPU・メモリ・ディスクだけです。
- **後に回したこと・未確認のまま残ったこと**（この Intent の途中で決めたもの）:
  - CI の2つの時間切れの失敗（`H2CompactionByPoolSuspensionIT`・`InvitationAdminPage.test.tsx`）と、U4〜U7 のコントラストの Not Met（NFR7.1）。依頼者の決定「次のintentでコントラストと一緒に直す」があります。make-you-chic-ui の固定先の更新も、この Intent の後に行います。
  - アプリの指標 `http_server_requests`・`mastersmith.mail.send` にバケットが無く、p95 の警報3件が鳴らないこと（Observability Setup。次の Intent で直すと決定）。
  - Dependabot の開いたままの更新の知らせ 11 件（CI Pipeline。次の Intent）。Dependabot alerts は無効のままです。
  - 承認済みの記録の誤り2つ。書き換えず、後の段の記録を正としました。
    - `alarms.md`：送信の失敗は、既存の警報に当たらない。
    - `log-queries.md`・`runbooks.md`：登録の完了の拒否は、監査に残る。
  - bcrypt を使う API の p95 の余裕が 69〜96 ms と小さいこと（Performance Validation）。
  - 招待の定期の削除の失敗（N3）と総当たり（R1）の警報を、配備先が決まったときに見直すこと。
  - 戻したときのブラウザの表示の設定と、初期管理者のメールアドレスを変えて戻したときの動きが未確認であること。
  - 初期管理者の INFO のログにメールアドレスが出ること（据え置きを受け入れ済み）。

## Q1. SLO の報告

A. 30 日の実測データが無いため、SLO の判定は `Unverified` とする。今ある証拠を基準の値として記録し、配備先が決まったときの測り方を書く（project.md の Deployment の決まり、前の Intent と同じ）。今ある証拠は次のとおり。
   - 配備の後の健全性の確かめとスモークテスト
   - Observability Setup の式の値
   - Performance Validation の結果
   - この段の確かめの時点の健全性
B. 手元の監視と外部エクスポートを、これから一定の期間（例: 1 日）動かし、その間の稼働と誤りの率を測ってから報告する。VM のメモリを約 1.5GB 余分に使い、`.env` の2行の切り替えとアプリの作り直しが要る
X. Other (please specify)

[Answer]: A

## Q2. 費用と資源の見直し

A. クラウドの費用は無いと記録する。資源の実測の値と同時に動かせる組み合わせの見積もりを記録し、見直しの提案を依頼者の判断事項として並べる（前の Intent と同じ）。クラウドの費用の見積もりは、配備先が決まってから行う。
   - 記録する資源：VM、アプリ、見本の対象DB、手元の監視、Mailpit、使い捨ての試験の環境。
   - 見直しの提案の例：bcrypt の余裕と CPU の割り当て、lgtm のメモリ。
B. A に加えて、候補の配備先（例: AWS の小さな構成）の費用の概算も今作る
X. Other (please specify)

[Answer]: A

## Q3. 設定のずれ（ドリフト）の確かめ方

A. 動いている環境を読み取りだけで確かめ、記録した設計の値と1つずつ比べる（前の Intent と同じ）。ずれが見つかったら記録し、直すかは依頼者に諮る。
   - 比べる先：Infrastructure Design・Deployment Pipeline・Environment Provisioning・Deployment Execution・Observability Setup の記録。
   - 確かめるもの：イメージ・上限・ボリューム・`.env` の項目の有無（値は見ない。外部エクスポートの2行はコメントであること）・compose の設定・内部DB の版・戻し用のタグとバックアップ。
B. 動いている環境は調べず、文書どうしの食い違いだけを記録する
X. Other (please specify)

[Answer]: A

## Q4. 次の Intent への入力のまとめ方

A. 後に回したことを次の Intent の候補として束ね、優先の順の案を付ける。順は案として示し、決めるのは依頼者。
   1. まず、既に決まっている「コントラストと CI の時間切れの直し、make-you-chic-ui の固定先の更新」の Intent に、次を加える案。
      - p95 のバケット（警報3件を働かせる）
      - Dependabot の開いた知らせ 11 件の取り込み
   2. 次に、承認済みの記録の誤りの整理（記録だけにしたもの）と、bcrypt の余裕の見直し。
   3. そのほかは、配備先の決定を待つものとして分ける（N3・R1 の警報、実在の受け手での測り直し、SLO の正式な値）。
B. 候補を並べるだけにし、束ね方と順は次の Ideation で決める
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

回答をまとめると、この段では次のとおり進めます。

1. **SLO（Q1: A）** → `slo-report.md`
   - 30 日の実測データが無いため、判定は `Unverified` とします。
   - 基準の値として、次を記録します。
     - 配備の後の健全性の確かめとスモークテスト（S1〜S12）
     - Observability Setup で式を実行した時点の値
     - Performance Validation の結果（21 件 Met、p95 の値）
     - この段で確かめる時点の健全性
   - 配備先が決まったときの測り方を書きます（アプリの指標にバケットを出した後の p95 を含む）。
2. **費用と資源（Q2: A）** → `cost-analysis.md`
   - クラウドの費用は無いと記録します。
   - VM・アプリ・見本の対象DB・手元の監視・Mailpit・使い捨ての試験の環境の上限と実測の値、同時に動かせる組み合わせの見積もりを書きます。
   - 見直しの提案を、依頼者の判断事項として並べます（bcrypt の余裕と CPU の割り当てなど）。
3. **設定のずれ（Q3: A）** → `drift-report.md`
   - 動いている環境を読み取りだけで確かめます（`docker inspect`・`docker compose config` の設定の名前・ボリューム・`.env` の項目の有無（値は見ない）・内部DB の版・戻し用のタグとバックアップ）。
   - 記録した設計の値と1つずつ比べます。ずれは記録し、直すかは承認の場で依頼者に諮ります。
4. **次の Intent への入力（Q4: A）** → `feedback-loop.md`
   - 後に回したことを束ね、優先の順の案を付けます。
     1. 決まっている「コントラストと CI の時間切れの直し、make-you-chic-ui の固定先の更新」に、p95 のバケットと Dependabot の知らせ 11 件を加える案
     2. 承認済みの記録の誤りの整理と、bcrypt の余裕の見直し
     3. 配備先の決定を待つもの
   - 決めるのは依頼者です。
5. この段では、コード・設定・環境を変えません（読み取りだけ）。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
