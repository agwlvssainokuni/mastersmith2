# 要件（261003-user-admin-followup）

Initial description: 利用者の管理の後始末（第1の束）。依頼の文の全文は `aidlc/spaces/default/intents/261003-user-admin-followup/project-description.json` [desc]
Workflow-selected scope: bugfix（深さ Minimal） [scope]

## Intent の分析

前の Intent（260930-user-admin）で作った利用者の管理の画面と、その確かめに残った不具合・確かめ残し・漏えいの疑いを片付ける。出どころは、前の Intent の `operation/feedback-optimization/feedback-loop.md` の第1の束（K1〜K3・T1〜T4・S1）[desc]。

- **ねらい**:
  - キーボードの利用者が、確かめの表示を閉じた後も同じ場所から操作を続けられる。
  - 画面の右端のメニューが欠けない。
  - 送信中の値のずれを防ぐ。
  - 性能の見積もりと警報の確かめ残しを埋める。
  - `project.md` の Forbidden（メールアドレスをアプリのログに含めない）に反する経路が無いことを確かめる。
- **種類**: 不具合の修正（画面3点）、テストと台本の直し（4点）、安全の確かめ（1点）。配備（開発者の PC 上のコンテナ）まで含める [desc]。
- **完了の目安**（feedback-loop.md 2節）:
  - E2E 110・120 で、閉じた後のフォーカスが行の「操作」に戻ること、開いたメニューが画面の中に収まることを確かめて通る。
  - 負荷の試験の場面 (B) で、待ちの最大が 0 より明らかに大きいか、時間切れの累計が 1 以上になる。あわせて、警報3件が `Alerting` になる。
  - S1 の確かめのテストが、重なった値を出さないことを確かめて通る。

## 機能の要件

### FR1 確かめの表示を閉じた後のフォーカス（K1、N-19。部品 `make-you-chic-ui`・`frontend-feature-useradmin`・`frontend-e2e`、K-17）

- **FR1.1** make-you-chic-ui の固定先を `3d9521a` から `e82b651`（`origin/main` に公開済み）へ上げる。承認を得た専用のコミットで、更新の前後のハッシュを記録する。`develop` へは、短命のブランチから fast-forward で統合する [desc] [memory:M1]。
- **FR1.2** 利用者の管理の画面で、行の「操作」から開いた確かめの表示（`ConfirmActionDialog`・`EditProfileDialog`）を閉じた後、フォーカスがその行の「操作」のボタンに戻る。閉じ方（やめる・Escape・閉じるボタン・操作の成功・操作の失敗）を問わない。
  - Dropdown は開き口の ref を置き換えるため、`Modal` の `finalFocusRef` には、画面が持つ行の「操作」のボタンの参照（`actionRefs`）から作った、描画ごとに作り直さない ref を渡す（K-17）[desc]。
- **FR1.3** E2E 110・120 は、`inert` が外れるのを待ってから次の操作をする形をやめる。閉じた後に `document.activeElement` が行の「操作」のボタンであることを確かめる形に替える [desc]。
- **FR1.4** 利用者の管理の画面の外で `Modal` を使う画面でも、閉じた後のフォーカスが開いた元（または決めた戻り先）に戻ることを、E2E か手元のブラウザで確かめる。対象は招待の画面（`InviteDialog`・`CancelConfirmDialog`）と DSL の管理の画面（`DslConfirmDialog`）。戻らない画面があれば、この Intent で `finalFocusRef` などで直す [Q4]。
- **判定**:
  - Given 行の「操作」から確かめの表示を開いた
  - When やめる・Escape・閉じるボタンで閉じる
  - Then フォーカスはその行の「操作」のボタンにあり、`body` ではない。

### FR2 行の「操作」のメニューのはみ出し（K2。部品 `frontend-feature-useradmin`・`frontend-e2e`、K-18）

- **FR2.1** `UserRowActions.tsx` の Dropdown に `placement="bottom-end"` を指定し、表の右端の列で開いたメニューが画面の右へはみ出さないようにする [desc] [memory:M2]。
- **FR2.2** E2E 120 のはみ出しの確かめに、開いたメニューの矩形（`getBoundingClientRect`）が画面（viewport）の中に収まることの確かめを足す。画面全体の横のスクロールの確かめは残す [desc]。
- **判定**:
  - Given 狭い幅（E2E 120 の幅）と広い幅で利用者の一覧を表示した
  - When 右端の行の「操作」を開く
  - Then メニューの左右・下の端が viewport の中にある。

### FR3 送信中の言語の選択（K3。部品 `frontend-feature-useradmin`、K-19）

- **FR3.1** 利用者の情報の変更の表示（`EditProfileDialog.tsx`）で、送信中は言語の選択（`RadioGroup`）を押せなくする。氏名の欄と同じ扱いにする [desc]。
- **FR3.2** 画面部品のテストで、送信中に言語の選択が押せないことと、送信が終わると押せるようになることを確かめる（不具合を再現するテストを同じコミットに含める）[memory:M3]。

### FR4 接続プールの上限の負荷の場面 (B) と警報の確かめ直し（T1。部品 `perf-and-monitoring`、K-20）

- **FR4.1** k6 の台本（`perf/k6/scenarios.js`）の場面 (B) を、準備のログインの失敗を含まない形に直す [desc]。
- **FR4.2** 使い捨ての環境で接続プールの上限を下げ、2本目を借りる待ちが 1 分以上続く負荷をかける。そのうえで次を確かめる [Q2]。
  - (a) `hikaricp` の待ちの最大が 0 より明らかに大きいか、時間切れの累計が 1 以上になる。
  - (b) 本物の警報の決まりのまま、`ms-pool-pending`・`ms-error-logs`・`ms-audit-fail` の3件が `Alerting` になる。
  - (c) 409 `USER_ADMIN_BUSY` の2行（L3・L4）の結び付きを確かめる（nfr-validation-matrix.md 3.1節）。
- **FR4.3** 手順（`perf/README.md`）を、直した台本と上限を下げる手順に合わせて直す。
- **判定**: (a)〜(c) を Build and Test の記録に実測の値で残す。警報の決まりの値（しきい値・続く時間）は変えない。

### FR5 管理者の印を外した直後の 403 と監査（T2、AC2.2.6。部品 `useradmin`、K-21）

- **FR5.1** 管理者の印を外した直後の、その利用者の管理の API への要求が 403 になることと、印を外した操作の監査の行（操作した人・対象の利用者・結果）を、1つのテストで続けて確かめる [desc]。

### FR6 出力を捕まえるテストの範囲（T3。部品 `mail`、K-22）

- **FR6.1** `MailConfigurationIT` の出力の確かめを、確かめたいロガーの出力だけに絞る。背景のスレッドの出力で落ちないようにする [Q3]。
- **FR6.2** 再現は試みない。直した後に、そのテストを単独で数回流し、`./gradlew verify` でも通ることを確かめる [Q3]。

### FR7 perf/README.md の注意書き（T4。部品 `perf-and-monitoring`、K-23）

- **FR7.1** `hikaricp.connections.acquire` の単位（秒・ミリ秒）が外部エクスポートの有無で変わるため、`baseUnit` を見て読むことを `perf/README.md` に書く [desc]。

### FR8 一意の制約の違反の文の漏えい（S1、Q-H。部品 `user`・`invitation`・`useradmin`、K-24）

- **FR8.1** 一意の制約に当たる要求を送り、重なった値（メールアドレスなど）が次のどこにも出ないことを確かめるテストを足す [desc]。
  - 要求の例: 同時の利用者の作成、同時の招待、そのほか確かめで分かった経路。
  - 確かめる出力: アプリのログ（既定の水準（INFO）と、`TraceAspect` の TRACE を有効にした場合の両方）、監査、エラー応答、トレースの属性。
  - 確かめ方は既存の `*SecretLeakIT` と同じ形にする。
  - Hibernate の `SqlExceptionHelper` の既定のログの経路を先に確かめる。
- **FR8.2** 出ると分かったときは、`project.md` の Forbidden に当たるため、この Intent で直す。直し方は確かめの結果を見てから、Code Generation の計画の承認の場で決める [Q1]。直し方の候補は次の3つ。
  - ロガーを止める
  - 違反を受ける所でクラスの名前だけを出す
  - その両方
- **FR8.3** 出ないと分かったときは、確かめのテストだけを足し、結果を記録する [Q1]。

### FR9 配備（部品 `container-runtime`）

- **FR9.1** 統合した版を、開発者の PC 上のコンテナ（compose のプロジェクト `mastersmith`）へ配備する。前の Intent の配備の手順（`cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`）を正とし、今回の差だけを Deployment Pipeline の段で書く [desc] [memory:M4]。

## 非機能の要件

- **NFR1 アクセシビリティ**: 利用者の管理の画面の確かめの表示は、WCAG 2.1 AA の 2.4.3（フォーカス順序）を満たす。前の Intent の U5-NFR7.1（Not Met）を Met にする（FR1）。
- **NFR2 漏えいの防止**: 一意の制約の違反の経路で、メールアドレス・氏名などの個人に関する値を、アプリのログ（INFO・TRACE）・監査・エラー応答・トレースの属性に含めない（`project.md` の Forbidden、FR8）。
- **NFR3 検査**: 統合の前に `./gradlew verify` を通す。コンテナの実行環境を動かし、対象DB のテストも含める（team.md）。E2E（`./gradlew e2eTest`）は、画面に関わる変更のため統合の前に手元で流す（team.md）。
- **NFR4 カバレッジ**: 行 80%・分岐 70% の下限を下回らない。手を入れるパッケージが `packagesJudgedByTotal` に当たれば、そのパッケージの下限を満たして一覧から外す（team.md）。
- **NFR5 回帰のテスト**: 不具合（FR1・FR2・FR3）を直すときは、再現するテストを同じコミットに含める（`project.md` の Mandated）。

## 制約

- `vendor/make-you-chic-ui` の中身は直接変更しない。固定先を上げるだけにする（`project.md` の Forbidden）。
- 負荷の試験は使い捨ての環境で行い、配備したアプリのデータと監査ログを汚さない。配備したアプリを止めるときは、先に依頼者に伝える（`project.md` の Testing Posture の学び）。
- 警報の決まり（しきい値・続く時間）は変えない（Q2: A）。
- `git push` は依頼者が行う。コミットは提案して承認を得てから行う。

## 前提

- `e82b651` は `origin/main` の先頭にある（2026-10-04 に `git ls-remote` で確かめた）。手元の `vendor/make-you-chic-ui` にも取得済み。
- `e82b651` の `finalFocusRef` は、省略できる追加の props で、今の使い方に影響しない（make-you-chic-ui の対応の報告）[assumption]。
- 招待の画面・DSL の管理の画面の `Modal` も、固定先を上げれば閉じた後のフォーカスが開いた元に戻る見込みである（未確認。FR1.4 で確かめる）[assumption]。
- 待ちが 1 分以上続く負荷は、colima の VM（CPU 4・6GiB）の使い捨ての環境で作れる [assumption]。

## 範囲の外

- 第2の束（安全の機能の判断）と第3の束（配備先の決定を待つもの）。
- team.md の Testing Posture の「2026-09-29 の時点で 12 パッケージ」の記述は、今は 7 個で古い（K-7）。team.md の変更は決まりの変更にあたるため、この Intent では直さない。直すときは依頼者の判断で別に行う。
- コンテナイメージの固定の形（ダイジェスト付きのまま、2026-10-04 の依頼者の判断）。

## 未解決の点

- FR8.2 の直し方は、S1 の確かめの結果を見て Code Generation の計画の承認の場で決める（Q1: D）。
- FR1.4 で戻らない画面が見つかったときの戻り先（開いた元のボタンか、別の要素か）は、Code Generation で画面ごとに決める。

## Sources

- 依頼の文: `aidlc/spaces/default/intents/261003-user-admin-followup/project-description.json` [desc]
- 質問と答え: `aidlc/spaces/default/intents/261003-user-admin-followup/inception/requirements-analysis/requirements-analysis-questions.md` [Q1]〜[Q4]
- 前の Intent の振り返り: `aidlc/spaces/default/intents/260930-user-admin/operation/feedback-optimization/feedback-loop.md`（2節 第1の束）
- コード知識ベース: `aidlc/spaces/default/codekb/mastersmith2/business-overview.md`（所見 K-17〜K-24 の一覧）、`architecture.md`（Interaction Diagrams 5・6）、`code-structure.md`
- 開発担当のスキャン: `aidlc/spaces/default/intents/261003-user-admin-followup/inception/reverse-engineering/developer-scan.md`
- 決まり:
  - [memory:M1] `aidlc/spaces/default/memory/team.md` Way of Working（サブモジュールの固定先の更新は fast-forward）、`project.md` Mandated（固定先の更新は専用のコミット）
  - [memory:M2] `project.md` Testing Posture の学び（2026-10-03、はみ出しの確かめと `bottom-end`）
  - [memory:M3] `project.md` Mandated（不具合の再現のテストを同じコミットに含める）
  - [memory:M4] `project.md` Deployment の学び（前の Intent の配備の手順を正として差だけを書く）

## Assumptions & Open Questions

- 前提は「前提」の節のとおり（`[assumption]` の3件）。FR1.4 と Build and Test の負荷の試験で確かめる。
- 未解決の点は「未解決の点」の節のとおり（FR8.2 の直し方、FR1.4 の戻り先）。
