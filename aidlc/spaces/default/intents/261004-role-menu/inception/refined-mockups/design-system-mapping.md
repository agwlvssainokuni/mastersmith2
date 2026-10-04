# デザインシステムの対応 — role-menu

出典: `mockups.md`・`interaction-spec.md`。部品は make-you-chic-ui（`vendor/make-you-chic-ui`、このリポジトリから直接変えない）を使う。make-you-chic-ui にある部品は AppShell・Sidebar（平ら）・Table・Tabs・Modal・Select・RadioGroup・Dropdown・Badge・Toast・Alert・Tooltip・FormField・TextInput・Checkbox・Switch・Card・Icon。

## 画面の要素と部品

| 画面の要素 | 使う部品 | 備考 |
|---|---|---|
| アプリの枠 | AppShell | 既存 |
| 業務のメニューの木・管理のメニュー（S1） | make-you-chic-ui の入れ子のサイドバー（依頼） | 今の Sidebar は平らで、入れ子・`aria-current`・見出し・言語に合わせた `aria-label` が無い。上流への追加を先に依頼する（`team.md`、`make-you-chic-ui-request.md`） |
| 作業ロールの切り替え（S1） | Dropdown（`role="menuitem"` の一覧） | 選択中は項目の名前に文字「（使用中）」を含めて示す。選択中の印（menuitemradio）は依頼の候補（下の表） |
| 作業ロールが1つのときの表示 | 文字（Badge でもよい） | ボタンにしない |
| ロール・グループの一覧（S3・S6） | Table・Button・Dropdown（placement: bottom-end） | 既存の管理の画面と同じ |
| 作成・名前の変更（S3・S6） | Modal・FormField・TextInput | |
| 削除・適用・未保存の確かめ | Modal | 背景のクリックで閉じない |
| ロールの詳細のタブ（S4・S5） | Tabs | |
| 権限の設定の左の木（S4・S8） | frontend の側の部品（DSL の画面の `DslMenuTree` と同じ開閉のボタンの形） | make-you-chic-ui に木の部品が無い。共有するため `src/shared/` に置く（`team.md` の Code Style）。上流への取り込みを諮る候補 |
| 権限の設定の右の表（S4・S8） | Table・Select | 行ごとの Select |
| 継承の元・今の DSL に無い・メニューに出る | 文字＋Badge（色だけにしない） | |
| 割り当て・メンバーの候補（S5・S6） | Modal・TextInput・候補の一覧（Checkbox か Button の並び） | 候補の一覧の部品は無いため frontend の側で作る（既存の招待・利用者の画面の検索と同じ形） |
| 確かめの結果の表（S7） | Table（行の開閉は frontend の側。DSL の違いの表と同じ素の table の形） | make-you-chic-ui の Table に行の開閉が無い（前の Intent と同じ判断） |
| 結果の知らせ | Alert・Toast | |
| 準備中・権限なし（S2） | Card・Alert・Button | |

## make-you-chic-ui に無いもの（上流へ取り込みを諮る一覧）

| 足りない点 | 今回の扱い | 依頼の優先 |
|---|---|---|
| Sidebar の入れ子（開閉のボタン・字下げ）、`aria-current`、見出しでのまとまり、言語に合わせた `aria-label`、Sidebar を AppShell の外で差し替える口 | 上流への追加を先に依頼する（`team.md`）。間に合わないときに自前に切り替えるかは、その時点で依頼者に諮る | 必須 |
| 木の部品（開閉と選択、読み込み中の子） | frontend の `src/shared/` に作る | 任意（取り込まれたら置き換える） |
| Dropdown の menuitemradio（選択中の印） | 文字「（使用中）」で代える | 任意 |
| Table の行の開閉 | 素の table で作る（既存と同じ） | 任意 |

## デザイントークン

- 新しい色・余白のトークンは足さない。継承の元・今の DSL に無い・メニューに出るの印は、既存の Badge の種類（中立・注意）を使う。
- ブランドカラーとテーマ（light・dark・system）のすべての組で、文字のコントラスト 4.5:1、部品 3:1 を満たす（axe で確かめる。`project.md` の学び）。
