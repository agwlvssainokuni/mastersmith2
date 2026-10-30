# NFR Requirements の質問 — U3 u3-reference-screens（Intent 261029-reference-lookup-cleanup）

U3（作成・更新の画面の参照の部品とピッカーのダイアログ）の非機能の要件で、判断が分かれる点だけを尋ねる。最初の選択肢を推奨とし、理由を添える。U3 は種類が `ui` のため、この段の成果物は performance-requirements.md・security-requirements.md・tech-stack-decisions.md・traceability.json。

## 決まっていること（質問しません）

- 要件の枝番は、単位の間で重ならないよう、U3 は `NFRx.41` から振る。
- アクセシビリティは要件 NFR4 のとおり: WCAG 2.1 AA、キーボードだけで開く・検索する・選ぶ・閉じる、axe はピッカーを開いた状態・長い名前で、ブランドカラーとテーマのすべての組。画面の部品ごとに vitest-axe を 1 件。
- 画面の文言と誤りのメッセージは日本語と英語（要件 NFR6）。
- セキュリティは要件 NFR1 と `team.md` の必須テスト (f)（ラベルと値を文字で描く）、`project.md` の Forbidden（参照先の行の値を `data-testid`・`id`・`document.title`・ログに入れない）のとおり。
- 初回の読み込みの JavaScript の量は、既存の確かめ（`frontend/scripts/check-bundle-size.mjs`、gzip の合計の目安 500 KB を超えたら警告）をそのまま使う。今は約 165 KB。
- 開いた直後の必須の入力不可の参照項目の誤り（`role="alert"`）の読み上げの扱いは、機能設計で NFR 設計に回したとおり、NFR 設計で決める。
- E2E は参照ピッカーの代表の流れ 1 本と、本数に数えない axe の確かめ。`./gradlew e2eTest` で、`verify` の外。

## Q1. [品質] 画面の側の応答の時間を測るか

サーバー側の目標（選択肢とピッカーの一致の検索は p95 1 秒未満、部分一致は 3 秒未満）は U1 が k6 で測る。画面の側でも、ダイアログで検索してから結果が出るまでの時間を測るかを決める。

A. 画面の側の時間の目標は置かず、サーバー側の k6 の目標と、画面のテストで「検索中」の表示と結果の表示が正しく切り替わることだけを確かめる（推奨）。前の Intent の業務データの画面と同じ扱いで、測る手間が増えない
B. Playwright で、ダイアログで検索を押してから結果の表が出るまでの時間を測り、目標（例: サーバーの目標＋500 ms）を置く。画面の遅さも分かるが、手元の PC の負荷で値が揺れ、合否の回を複数回流す必要がある
X. Other (please specify)

[Answer]: A
