# 性能の要件 — U7 app-frame-ui

## 出典

- `functional-spec.md`（この単位の承認済みの機能設計。D4 の読み直しのきっかけ、D7・D8 の切り替え、D10〜D18 のサイドバー、D21 の置き場、4節の状態、6.3 の実際のブラウザの検査）と `frontend-components.md`。
- `rules.md` は ui の単位には無い。段の定義が必須とする `rules` の代わりに、`functional-spec.md` の D1〜D30 を入力として使う（`security-requirements.md` の出典と同じ）。
- `requirements.md`（NFR2.1〜NFR2.2、NFR4.2）。
- `contract-summary.md`（C8・C9）と、先の単位の NFR 要件の目標:
  - role: 作業ロールの読み取りと切り替え・自分の権限の木は p95 1 秒。
  - navigation: 業務のメニュー・置き場の問い合わせは p95 1 秒。画面の移動のたびの読み直しは毎秒 10 要求ほどの見積もり（navigation の NFR2.1）。悪い側は menus 1,000 項目・深さ 5 段。
- `technology-stack.md`（コード知識ベース）。
- この段の答え: Q1 A（画面の側の時間の目標と測り方）。まとめの確認は Looks correct。
- 前例: role-admin-ui の NFR 要件（NFR2.9 の測りの決まり）と、そのレビューの R-03（目標を超えたときの扱い）・R-04（初回の JavaScript）・R-07（測り始めの点）。

上流の枝番との対応は `security-requirements.md` の「ID の振り方」の表のとおり。

## 要件

| ID | 要件 | 確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR2.2 | API の応答時間は U4・U5 の目標をそのまま当て、U7 では足さず測り直さない（作業ロールの読み取りと切り替え・自分の権限の木・業務のメニュー・置き場の問い合わせは、どれも p95 1 秒）。U7 は、画面が送る要求の数と形が見積もりのとおりであることを確かめる。<br>・画面の移動1回で、作業ロールと業務のメニューの2本（D4 の (d)）。<br>・切り替え1回で、`PUT` 1本と読み直しの2本。<br>・同じ描画の中で重なったきっかけは1回にまとめる | API の時間は U4・U5 の k6 の場面（Performance Validation）。<br>`WorkRoleProvider.test.tsx`・`BusinessNavigationProvider.test.tsx` で、移動1回・切り替え1回・きっかけの重なりで送る要求の数を数える | Code Generation（B9）、API は U4・U5 |
| NFR2.5 | 作業ロールの切り替え: 項目を選んでから、業務の区画が新しい木に替わり、結果の文（Toast）が出るまで 0.5 秒以内（目安の木 100 項目・深さ 1）。悪い側の木（1,000 項目・深さ 5 段）は値の記録だけ | 測りのテスト（NFR2.8） | Code Generation（B9）・Build and Test |
| NFR2.6 | 深い階層を開く: 悪い側の木で、開閉のボタンを押してから子が出るまで 0.2 秒以内 | 測りのテスト（NFR2.8） | Code Generation（B9）・Build and Test |
| NFR2.7 | 画面の移動: 悪い側の木で、サイドバーの項目を押してから次の画面の見出しが出るまで 0.5 秒以内。読み直しの間に業務の区画が消えない | 測りのテスト（NFR2.8）。区画が消えないことは、`BusinessNavigationProvider.test.tsx` で遅らせた応答を使って確かめる | Code Generation（B9）・Build and Test |
| NFR2.8 | 測りの決まり（Q1 A、U6 の NFR2.9 と同じ形）:<br>・測りのテストは、U7 の実際のブラウザの検査のファイル（`frontend/e2e/` の U7 の検査、番号はコード生成で U6 と合わせて決める）の中に1件置く。<br>・表示の設定の 20 組の繰り返しの外で、既定の1組（テーマ light・文字の大きさ md・ブランドカラー blue・既定の幅）だけで動かす。<br>・API は差し替えの口と見本で遅れなく返し、API の時間を含めない。測り始めは、操作（押す・選ぶ）を送った時点に固定する。<br>・各 5 回測り、1回目と2〜5回目を分けて記録する。<br>・**判定に使う値**（承認の場の直し R-02）: NFR2.5〜NFR2.7 の目標は、5 回の値の **中央値**（小さい順に並べた3番目）で判定する。最大と1回目の値も並べて記録する（1回目は読み込みの初めの重さを含むため、判定から外さずに中央値の中で扱う）。悪い側の木の値（NFR2.5 の記録だけの分）は判定しない。<br>・**Not Met の決まり**: 中央値が目標を超えたら Not Met とする。中央値が目標以内なら、最大が目標を超えても Met とし、最大の値を並べて記録する。5 回のうち測れない回（テストの失敗など）があれば、その目標は判定せず `Unverified` として理由を記録する。<br>・値は記録だけで、テストの成否にしない。E2E の本数に数えない（`project.md` の読み方）。<br>・**目標を超えたとき**は、Build and Test が値と Not Met を記録し、承認の場で依頼者が扱いを決める。目標を緩めて満たしたことにはしない（U6 のレビューの R-03、`org.md`・`team.md`） | 測りのテストが5回の値を添付に記録すること（名前と時間だけ。氏名・メールアドレスを含めない）。Build and Test の記録に、目標ごとの5回の値・中央値・最大・1回目と、Met・Not Met・Unverified の判定を書く | Code Generation（B9）・Build and Test |
| NFR2.9 | 読み直しの重なりと古い答え（D4）:<br>・要求の世代で、最後に始めた読み込みの答えだけを使い、古い答えと画面を離れた後の答えは捨てる。<br>・読み直しの間は今の表示を残す。<br>・決まった間隔の自動の読み直し（ポーリング）はしない。<br>・画面は要求に独自の時間切れを置かない（既存の ApiClient のまま） | `WorkRoleProvider.test.tsx`・`BusinessNavigationProvider.test.tsx`・`useTableAccess` のテストで、遅らせた古い応答が新しい表示を上書きしないこと | Code Generation（B9） |
| NFR2.10 | 初回の JavaScript（U6 のレビューの R-04）:<br>・骨組み（作業ロール・`buildNavSections`・開閉）は入口に入るため、`frontend/scripts/check-bundle-size.mjs` の入口の値（gzip、目安 500KB で超えたら警告だけ）を、B9 の前と後で測って記録する。<br>・遅延読み込み（`lazy`）の `tables`・`mypermissions`・ログアウトの画面の chunk の gzip の大きさを、ビルドの結果（manifest）から読んで並べて記録する。<br>・3つの画面は入口に入れない | B9 の前と後の `check-bundle-size.mjs` の出力と chunk の大きさを、コード生成の成果物（code-summary）に記録する。3つの画面が `lazy` で登録されていることを登録のテストで確かめる | Code Generation（B9） |
| NFR2.11 | 作業ロールの切り替えの二重の送信を防ぐ（機能設計の再レビューの R-04）。<br>・`PUT` の待ちの間は、Dropdown の代わりに押せないボタン（`aria-disabled` と押下の無視。フォーカスは保つ）に差し替える。<br>・vendor の Dropdown はトリガーの `onClick` を上書きするため、トリガーの側の `aria-disabled` だけでは防げない。<br>・フックが送信の最中を参照で持ち、その間の `switchTo` の呼び出しを捨てる | `WorkRoleSwitcher.test.tsx`: 待ちの間に一覧が開かず、2本目の `PUT` が送られず、フォーカスがボタンに残る | Code Generation（B9） |
| NFR2.12 | 測りに使う悪い側の木は、navigation の NFR2.1 の悪い側（menus 1,000 項目・深さ 5 段・40 文字の名前を含む）と同じ形を見本で作る。目安の木は 100 項目・深さ 1（既定の DSL の形）。menus の項目の数の上限は画面で置かない | 測りのテストの見本の生成の関数（`e2e/support/` に置く）の形を、見本の型（C9 の `NavigationResponse`）で縛る | Code Generation（B9） |

## 受け入れた制約（性能）

- 画面の時間は手元の PC の1台で測り、値を記録するだけで統合を止めない（U6 と前の Intent と同じ）。
- 画面の移動のたびに2本の要求が増える（機能設計の Q2 A）。U5 の見積もり（毎秒 10 要求ほど）と U4・U5 の k6 で押さえ、画面の側では数だけを確かめる（NFR2.2）。

## 承認の場の決定と直し

- 決定: 依頼者は承認の場で Request Changes を選び、直す範囲を「Major 11 件だけを直す」とした。この単位で直すのは R-01・R-02 の2件で、ほかの指摘は直さない。レビューの記録は `aidlc/spaces/default/intents/261004-role-menu/.aidlc-reviews/nfr-requirements/units/app-frame-ui/4e9510ac79c4db11/1.json`。
- **R-02（Major）**: 画面の時間の目標（NFR2.5〜NFR2.7 の 0.5 秒・0.2 秒・0.5 秒）を、5 回の測りのどの値で判定するかが無かった。NFR2.8 に次を足した。
  - 判定は5回の中央値で行い、最大と1回目も並べて記録する。
  - Not Met の決まり: 中央値が目標を超えたら Not Met。最大だけが超えたときは Met として最大を記録する。測れない回があれば `Unverified` として理由を記録する。
- R-01 の直しは `security-requirements.md` の同じ名前の節に書いた。
