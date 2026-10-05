# NFR 要件の質問 — U6 role-admin-ui

対象の単位: U6 role-admin-ui（kind: ui、大きさ L、作るのは B8）。作る成果物は `performance-requirements.md`・`security-requirements.md`・`tech-stack-decisions.md`・`traceability.json` の4つです（段の定義の `produces_kinds`。scalability・reliability・observability は ui の単位に作らない）。

段の定義は `rules` を必須の入力に挙げていますが、ui の単位は機能設計で `rules.md` を作りません（機能設計の段の `produces_kinds`）。そのため、この単位では承認済みの `construction/role-admin-ui/functional-design/functional-spec.md` の 2節の画面の決まり D1〜D32 を、業務の決まりの代わりの入力として使います。存在しない `rules.md` の中身は作りません。

読んだもの:

- この単位の承認済みの機能設計 `construction/role-admin-ui/functional-design/`（`functional-spec.md` の D1〜D32・W3.1〜W9.1・7節・8節・9節・13節、`frontend-components.md`）と、その再レビューで残った指摘 R-02〜R-09。
- 要件 `inception/requirements-analysis/requirements.md`（NFR1〜NFR6。この単位の中心は NFR1.1・NFR1.4〜NFR1.6・NFR2.2・NFR2.4・NFR4.1〜NFR4.3・NFR6.1・NFR6.3・NFR6.4）。契約 `inception/contract-design/contract-summary.md`（C2・C6・C7）。コード知識ベース `aidlc/spaces/default/codekb/mastersmith2/technology-stack.md`。
- 先に確定した単位の NFR 要件 `construction/role/nfr-requirements/`・`construction/group/nfr-requirements/`・`construction/cross-cutting/nfr-requirements/` と、それぞれのレビューの指摘。
  - role: R-01〜R-09。
  - group: R-01 判定の指標、R-02 上限に届く場面の合格の条件、R-04 テストも持ち主も無い行、R-05 時間の目標の欠け。
  - cross-cutting: R-03 アイコンの照合の行、R-05 共有の木の深さ。
- 前の Intent の ui の単位の NFR 要件 `aidlc/spaces/default/intents/260930-user-admin/construction/u5-user-admin-ui/nfr-requirements/`（画面の時間の測り方の前例）。
- コード:
  - `frontend/src/shared/api-client/apiClient.ts`。`apiDownload` は本文と `Content-Disposition` だけを返し、ほかのヘッダーを返さない。
  - `frontend/package-lock.json`。react-router 8.4.0（MIT）で、`createBrowserRouter`・`RouterProvider`・`useBlocker` は同じパッケージにある。
  - `frontend/scripts/check-bundle-size.mjs`。
  - `frontend/e2e/120-user-admin-accessibility.e2e.ts`・`support/displayCombos.ts`・`support/adminApiRoute.ts`。

質問は 2 問です。ほかの点は上流・`team.md`・`project.md`・承認済みの機能設計・先の単位の答えとコードで決まっているため、「決まっていること」と「この段で決める要点」に書き、まとめの確認で確かめます。

---

## 決まっていること

### ID の振り方（group・role と同じ形）

- 上流の NFR の枝番（NFR1.1〜NFR1.6・NFR2.1〜NFR2.4・NFR3.1〜NFR3.2・NFR4.1〜NFR4.3・NFR5.1〜NFR5.2・NFR6.1〜NFR6.4）は、**同じ番号は同じ意味** でだけ使う。
  - 上流の要件をこの単位に当てはめた要件は、上流と同じ ID にする。
  - 新しく足す要件は、上流の最後の枝番の次から振る（NFR1.7〜・NFR2.5〜・NFR4.4〜・NFR6.5〜）。
  - 各成果物の冒頭に、上流の ID とこの単位の ID の対応表を1つ置く。
- `traceability.json` は上流の枝番ごとに行を立て、この単位で足した ID はすべて、意味の近い上流の枝番の行の target にも載せる（role のレビューの R-04）。
  - 当たらないものは理由と持ち主の単位を書いて N/A にする。NFR3.1・NFR3.2 は同時性とトランザクションでサーバーの持ち物、NFR5.1・NFR5.2 は監査と指標で U3・U4 の持ち物、NFR2.1・NFR2.3 は規模とサーバーの読み出しの重さ。
  - 混ざるものは「部分」と書き、残りの持ち主を書く。NFR1.2 の認可の表のテストは U3・U4、NFR1.3 の分類の網羅は U1、NFR6.2 の性質ベースのテストのうちメニューは U5・U7、NFR6.3 の E2E は U6・U7。
  - 質問のファイルで「部分」とした扱いと、成果物の status を食い違わせない（role のレビューの R-04）。
- テストも持ち主の段も無い要件の行は作らない（group のレビューの R-04）。受け入れた制約は「受け入れた制約」の節に書く。

### セキュリティ（devsecops・コンプライアンスの視点）

- 画面で隠すことはサーバー側の判定の代わりにしない。
  - 管理の API の 403 `ACCESS_DENIED` は骨組みの `useAdminForbidden` に渡す。
  - 道を直接入れた管理者でない利用者には、骨組みの `ADMIN_FORBIDDEN` が出る（D2、NFR1.1、`project.md` の Mandated）。
- 要求の本文は決めた項目だけを送る（名前は `{name}`、割り当ては `{userId}` か `{groupId}` の一方、保存は `scope` と `entries`。D7・W5.2・D12、NFR1.4）。判定の正はサーバー。
- 名前・氏名・メールアドレス・DSL の表示名は文字として描き、HTML として解釈しない。画面に HTML を直接埋めない（D28。ESLint の `react/no-danger` と oxlint のセキュリティ系の決まりが既に守る）。
- 応答・要求の値・YAML の本文・指紋は、コンソール・ブラウザの保存（`localStorage` など）に出さない（D3・W7.3、NFR1.6）。
- YAML のファイルは、送る前に `File.size` で上限と比べる。上限ちょうどは送る。この確かめは案内のためで、判定の正はサーバーの検証（D23、`team.md` の信頼できない入力、NFR1.5 の画面の部分）。上限は role で 10 MiB（10,485,760 バイト）に確定した（role の NFR2.9）。
- 個人データの新しい保存は無い（画面は表示だけ）。テストの見本のメールアドレスは `example.com` だけ。
- フロントエンドの依存の脆弱性の関門は今のまま。実行時の依存の High 以上・`MAL-`・成果物を作る道具の High 以上で統合を止め、`ignore-scripts=true` も今のまま（`team.md` の Deployment・Code Style）。

### 依存とライセンス

- 新しい依存は足さない。
  - data router（`createBrowserRouter`・`RouterProvider`）と `useBlocker` は、今の react-router 8.4.0（MIT、lockfile で固定）にある。
  - fast-check・vitest-axe・Playwright・@axe-core/playwright は既存のまま。
  - make-you-chic-ui の固定先は e82b651 のまま（入れ子のサイドバーを取り込む固定先の更新は B9 の U7 の持ち物）。

### テスト・品質（品質の視点）

- 画面のテストは承認済みの機能設計の 8.1 のとおり（Vitest＋Testing Library＋user-event＋vitest-axe、部品ごとの axe、`waitFor`、説明文は英語）。fast-check の対象は `permissionDraft`・`removalImpact`・`removedRoles`・`changePaging`。
- 実際のブラウザの検査は機能設計の 8.2 のとおりで、E2E の本数に数えない（`project.md` の読み方）。
  - 範囲: S3〜S7・S9 の誤りの状態と開いた部品、2語の氏名・64 文字の名前、表示の設定の 20 組すべて、幅 360px・768px・1280px は既定の1組。
  - 共有の木（S4）の実際のブラウザの axe は U6 が持つ（cross-cutting の NFR4.5）。
- 流れの E2E は機能設計の 8.3 のとおり。F の流れを B8 で書き、B9 で同じファイルに足す。本数は1本で、自分で作った利用者とロールだけを対象にする。
  - 仮の資格情報はプロセスの環境変数で渡し、`webServer.env` に置かない。報告の json の文字列にパスワード・トークン・メールアドレスが無いことを確かめる（`project.md` の学び）。
- カバレッジ: フロントエンドの下限（行 80%・分岐 70%、`@vitest/coverage-v8` の `thresholds`）を、新しい3機能と `useradmin` の追加を含めて満たす。バックエンドのパッケージの一覧（`packagesJudgedByTotal`）には触れない。

### 性能（API の側）

- API の応答時間は、U3・U4 の目標をそのまま当て、U6 では足さず測り直さない。
  - role・group の管理の API は p95 1 秒。
  - import の確かめは上限ちょうどで 15 秒、適用は 30 秒。
- U6 は、画面が送る要求の道・引数・本文が契約と確定の形のとおりであることを、各機能の `api/*.test.ts` で確かめる。

---

## この段で決める要点（質問にしない案。まとめの確認で確かめる）

- **待ちと二重の送信**（前の Intent の U5 の NFR5.5 と同じ形。import の 15 秒・30 秒に合わせる）
  - 画面は要求に独自の時間切れを置かない（既存の ApiClient のまま）。
  - 送信から 5 秒を過ぎても応答が無いときは「時間がかかっています」を出し、応答で消す。S7 の確かめと適用では、上限ちょうどのファイルで確かめは最大 15 秒・適用は最大 30 秒かかりうることを、案内の文に含める。
  - 二重の送信は、押したボタンを処理中にし、フックが送信の最中を参照で持ってその間の呼び出しを捨てることで防ぐ。
- **1テーブルのカラムの数**（機能設計の再レビューの R-08）
  - 目安は要件 NFR2.1 の 1テーブル最大 100 カラム。S4 のカラムの表は分割せずに全件を描き、件数の上限は画面で強制しない。
  - 悪い側（1テーブル 1,000 カラム、role・group と同じ 10 倍の余裕）でも、描画と Select の操作が固まらないことを Q1 の測りで記録する。
- **古い答えを捨てる**: 木の開閉・節の選び・読み直しが重なったら、最後に始めた読み込みの答えだけを使い、画面を離れた後の答えを捨てる（共有の木の世代の考え方と同じ）。決まった間隔の自動の読み直しはしない。
- **初回の JavaScript**: 新しい画面はすべて遅延読み込み（`lazy`）にする。`frontend/scripts/check-bundle-size.mjs` の目安（入口と静的に読み込むファイルで gzip 500KB、超えたら警告だけ）を、B8 の前と後で測って記録する（前の Intent の U5 の NFR5.6 と同じ）。
- **書き出しのファイルの名前**: `Content-Disposition` の名前は、英数字・`.`・`_`・`-` だけで `.yaml` か `.yml` で終わるときだけ使い、それ以外は `roles.yaml` にする。ブラウザの保存の名前に、道の区切りや見えない文字を入れないため。
- **画面の文言の上限の値**（R-07）: 「上限 10 MiB」は文言に直接書かず、`MAX_TRANSFER_BYTES` から差し込む。値は role の NFR2.9 と同じ 10,485,760 バイトで、サーバーの値と画面の値が同じであることを `roletransfer` のテストで定数として確かめる。
- **機能設計の再レビューで残った点の置き場**
  - R-02: 各画面と API の組で 403 が `useAdminForbidden` に渡ることと、道の直接入力で `ADMIN_FORBIDDEN` になることを、画面のテストの表として `security-requirements.md` の NFR1.1 の確かめ方に入れる。
  - R-05: S4 と S5 は同じ `RoleDetailPage` を2つの道に登録し、タブの切り替えの後もフォーカスがタブに残ることを画面のテストに入れる。
  - R-06: 知らない `reason` は「内容に誤りがあります」の汎用の文言にし、`reason` を文言に写す関数に fast-check の性質のテストを当てる。
  - R-03（traceability の Deferred を OK の部分に）・R-04（7.1 の断片を今の `main.tsx` の形に合わせる）・R-09（権限の木の道の条件つきの言い回しを外す）は、承認済みの機能設計を書き換えず、コード生成の計画に引き継ぐ。
- **受け入れた制約**
  - 画面の時間は手元の PC の1台で測り、値を記録するだけで統合を止めない（前の Intent の U5 と同じ）。
  - 骨組みのログアウトでは未保存の確かめが出ないことがある（機能設計 7.2）。

---

## Q1 画面の側の時間の目標と測り方

背景: 前の Intent の利用者の管理の画面は、次の形で目標を置きました（U5 の NFR5.1・NFR5.2）。
- 実際のブラウザの検査のファイルの中に測りのテストを1件置く。表示の設定の既定の1組だけで、5 回ずつ測る。
- 目標を書いたうえで、Build and Test で値を記録し、テストの成否にはしない。

U6 で重いのは次の3つです。
- S4: 木でテーブルを選んでカラムの表が出るまで。
- S4: 保存の後の読み直し。
- S7: 確かめの結果が出るまで。上限ちょうど（約 25 万の対象）の応答は数十 MB の JSON になりうり、画面は 100 件ずつ描きます。

API の時間は U4 の目標（p95 1 秒、確かめ 15 秒）が持つため、画面の側だけを測る形が前例です。

A. 画面の側の時間に目標を置き、U6 の実際のブラウザの検査のファイルの中に測りのテストを1件置く（推奨: 前例と同じ形で、API の時間と画面の重さを分けて押さえられる。S7 の大きな応答で画面が固まらないことを数字で残せる）。
  - 置き方: 表示の設定の既定の1組、API は見本で返す。各 5 回測り、Build and Test で記録して成否にしない。
  - (1) S4: テーブルを選んでからカラムの表（100 カラム）が出るまで 1 秒以内。悪い側の 1,000 カラムは記録だけ。
  - (2) S4: 保存の 204 を受けてから読み直した表が出るまで 1 秒以内。
  - (3) S7: 上限ちょうどに当たる確かめの応答（25 ロール × 1万の変わる点）を受けてから、ロールごとの要約の表が出るまで 3 秒以内。
  - (4) S7: 開いたロールの次の 100 件が出るまで 0.5 秒以内。
B. A と同じ目標と測り方だが、S7 の (3) は上限ちょうどの応答ではなく、ふつうの規模（2 ロール × 数千の変わる点、AC3.1.9）で測る。上限ちょうどの応答は1回流して時間とメモリを記録するだけにする。
C. 画面の側の時間には目標を置かず、API の時間（U4 の目標）だけにする。S7 の大きな応答で画面が固まらないことは、画面のテストで 100 件ずつしか描かないことを確かめるだけにする。
X. Other (please specify)

[Answer]: A **Mode:** guided

## Q2 書き出しが読み込みの上限を超えたことを、画面がどう知るか

背景: role の NFR 要件（NFR2.9）で、書き出した本文が 10 MiB を超えたときは、応答にヘッダー `X-Role-Transfer-Exceeds-Import-Limit: true` を付け、画面の案内は U6 が持つことになりました。案内の文は「上限を超えたため、このファイルはそのまま読み込めません。ロールの一部ずつに分けて読み込んでください」の形です。ところが、共有の `apiDownload`（`frontend/src/shared/api-client/apiClient.ts`）は本文と `Content-Disposition` だけを返し、ほかのヘッダーを返しません。

A. 共有の `ApiDownload` に応答のヘッダー（`Headers`）を足す互換の変更をし、`roletransfer` はヘッダー `X-Role-Transfer-Exceeds-Import-Limit` の値で案内を出す。既存の使う側（DSL のダウンロード）は変えず、`apiClient.download.test.ts` に足す（推奨: 上限を超えたかの判定をサーバーの1か所に置け、上限が変わっても画面を直さずに済む。共有の部品の変更は項目を足すだけ）
B. 共有の部品は変えず、`roletransfer` が受けた本文の大きさ（`blob.size`）を `MAX_TRANSFER_BYTES` と比べて案内を出す（同じ判定を画面にも持つ。上限を変えるときは両方を直す。ヘッダーは使わない）
C. A と B の両方で判定し、どちらかが上限を超えたと示せば案内を出す（ヘッダーが付かない不具合にも気づけるが、判定が2か所になる）
X. Other (please specify)

[Answer]: A **Mode:** guided

## Consolidated Summary Confirmation

答えのまとめ（role-admin-ui の NFR 要件）:

- Q1 A: 画面の側の時間は前の Intent の U5 と同じ形で、既定の1組・API は見本で各 5 回測り、記録するだけで成否にはしない。目安は S4 の 100 カラムの表が出るまで 1 秒、保存の後の表が出るまで 1 秒、S7 の上限ちょうどの確かめの応答を受けてから要約が出るまで 3 秒、次の 100 件が出るまで 0.5 秒。
- Q2 A: 共有の `shared/api-client` の `ApiDownload` に応答のヘッダーを足す互換の変更をし（B8 で shared に手が入る）、書き出しが読み込みの上限を超えたことは role のヘッダー `X-Role-Transfer-Exceeds-Import-Limit` で知る（判定はサーバーの1か所）。
- 決まっていることと要点（rules.md の代わりに機能設計の D1〜D32 を入力にする、作る成果物は performance・security・tech-stack・traceability の4つ、ID の振り方と traceability は group・role と同じ形、新しい依存は足さず data router と useBlocker は react-router 8.4.0（MIT）で足りる、5 秒を過ぎたら「時間がかかっています」を出し独自の時間切れは置かない、1 テーブル 100 カラムが目安で悪い側は 1,000 カラム、初回の JavaScript の大きさを B8 の前後で記録、書き出しのファイル名は安全な形のときだけ使う、上限の値は MAX_TRANSFER_BYTES から文言に差し込む、機能設計の再レビューで残った R-02・R-05・R-06 はこの段の要件に入れ R-03・R-04・R-09 はコード生成の計画へ）のとおり。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
