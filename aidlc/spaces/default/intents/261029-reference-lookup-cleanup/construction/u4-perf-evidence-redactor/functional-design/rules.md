# 業務の決まり — U4 u4-perf-evidence-redactor

k6 の結果（summary json と、標準出力・ログのテキスト）の JWT を記録（`evidence/`）に置く前に伏せる道具（PerfEvidenceRedactor）と、`verify` の中で既存の記録を調べる検査の決まり。YAML が正で、下の表はその要約。

```yaml
rules:
  # ---- BR1: JWT の形と置換 ----
  - id: BR1.1
    statement: JWT の形を「eyJ で始まり、base64url の文字（英大小・数字・- ・_）が 1 文字以上続く区間を . で 3 つつないだもの」とし、直前の 1 文字が英数字のときは JWT の形とみなさない
    category: validation
    applies_to: 置換（BR1.2。JSON の口とテキストの置換の口 BR1.8 の両方）と残存の検査（BR2.1）と evidence の検査（BR4.2）が共有する JWT の形の定義
    trigger: 文字列を調べるたび
    logic: >
      IF 文字列の中に「eyJ＋base64url の文字 1 文字以上」「.」「base64url の文字 1 文字以上」「.」「base64url の文字 1 文字以上」が
      続く部分があり、かつその直前の 1 文字が英数字でない（または文字列の先頭である）
      THEN その部分（3 区間ぶん、最も長く取れるところまで）を JWT の形の 1 件とみなす。
      2 区間（. が 1 つ）しか無いものは JWT の形とみなさない。3 区間目の後ろの = などの base64url でない文字は JWT の形に含めない
    violation: 該当なし（定義）
    source: FR5.1・AC4.1.1

  - id: BR1.2
    statement: JWT の形の部分は、1 つの文字列の中に何件あってもすべて [REDACTED-JWT] に置き換える
    category: calculation
    applies_to: 置換
    trigger: 文字列の値・配列の要素の文字列・オブジェクトのキー名を処理するとき / テキストの置換の口でファイルの中身全体を処理するとき（BR1.8）
    logic: >
      この置換は 1 つの関数（文字列を受けて文字列を返す）とし、JSON の口とテキストの置換の口の両方がこの同じ関数を呼ぶ。
      IF 文字列に BR1.1 の JWT の形が 1 件以上ある THEN 各件を左から順に [REDACTED-JWT] に置き換え、周りの文字はそのまま残す。
      [REDACTED-JWT] は eyJ で始まらないため BR1.1 に一致せず、置換をもう一度当てても結果は変わらない（冪等）
    violation: 該当なし（変換）
    source: FR5.1・AC4.1.1・AC4.1.5

  - id: BR1.3
    statement: 置換は JSON の木のすべての文字列に当て、文字列でない値には手を付けない
    category: calculation
    applies_to: 置換
    trigger: 入力の木をたどるとき
    logic: >
      IF 値がオブジェクト THEN 各キー名と各値に再帰で当てる。
      IF 値が配列 THEN 各要素に再帰で当てる。
      IF 値が文字列 THEN 長さにかかわらず（数 MB でも）BR1.2 を当てる。
      IF 値が数・真偽・null THEN そのまま残す。
      入れ子の深さに上限は置かず、深い入れ子でも途中で止まらない形でたどる
    violation: 該当なし（変換）
    source: FR5.1・FR5.3・AC4.1.1・AC4.1.5

  - id: BR1.4
    statement: Bearer の後ろに続く JWT は、JWT の部分だけを置き換え、Bearer と空白は残す
    category: calculation
    applies_to: 置換
    trigger: 文字列に「Bearer eyJ…」が含まれるとき
    logic: >
      IF 文字列が「Bearer 」の後ろに JWT の形を持つ THEN 直前の 1 文字が空白（英数字でない）ため BR1.1 に一致し、
      結果は「Bearer [REDACTED-JWT]」になる。Bearer という語そのものは伏せない
    violation: 該当なし（変換）
    source: AC4.1.1

  - id: BR1.5
    statement: キー名の置換で、同じオブジェクトの中の 2 つ以上のキーが同じ名前になるときは、片方を消さずに失敗にする
    category: constraint
    applies_to: 置換（オブジェクトのキー名）
    trigger: 1 つのオブジェクトのキー名をすべて置き換えたとき
    logic: >
      IF 置換の後のキー名の中に重なりがある（置換の後の名前が、ほかのキーの元の名前または置換の後の名前と同じ）
      THEN 置換を中止し、出力を書かずに失敗で終わる（BR3.3）。誤りの表示には、衝突したオブジェクトの道（置換の後の名前でたどったもの）だけを出す
    violation: 終わりのコード 3。出力のファイルと一時ファイルを残さない
    source: AC4.1.1・AC4.1.5(4)

  - id: BR1.6
    statement: 出力は JSON として読め、構造（キーの集合と並び、値の型）が入力と同じで、setup_data の外の JWT を含まない値は入力と同じ値のままにする（setup_data の下の文字列は BR1.7 で替わる）
    category: constraint
    applies_to: 置換の結果と書き出し
    trigger: 書き出す直前
    logic: >
      キーの並びは入力の順を保つ（置き換えたキーは元の位置のまま名前だけが替わる）。
      数・真偽・null・JWT を含まない文字列は、JSON として読み直したときに入力と同じ値になる。
      同じかどうかは JSON として読んだ値で比べ、空白・改行・字下げなどの書式は同じでなくてよい。
      出力は UTF-8・改行 LF・末尾に改行 1 つの、字下げのある JSON とする（字下げの幅はコード生成で決める）
    violation: 該当なし（書き出しの形）
    source: FR5.1・FR5.3・AC4.1.5

  - id: BR1.7
    statement: 入力の上の階層がオブジェクトで setup_data というキーを持つときは、その下のすべての文字列の値を [REDACTED-SETUP] に置き換え、キーと値の型は保つ
    category: calculation
    applies_to: 置換（k6 の summary json の setup_data。setup() の返す値）
    trigger: 入力の木をたどるとき
    logic: >
      IF 上の階層の値がオブジェクトで、キー setup_data を持つ
      THEN setup_data の値の下（入れ子・配列を含む）の文字列の値をすべて [REDACTED-SETUP] に替える。
      キー名は替えない（キー名にも BR1.2 の JWT の形の置換だけは当てる）。数・真偽・null・オブジェクト・配列の形はそのまま残す。
      setup_data の値そのものが文字列なら、それも [REDACTED-SETUP] に替える。
      上の階層より深いところにある setup_data という名前のキーは特別に扱わない（BR1.2〜BR1.4 だけを当てる）。
      [REDACTED-SETUP] は eyJ で始まらず BR1.1 に一致しないため、置換をもう一度当てても結果は変わらない（冪等）。
      JWT の形の置換（BR1.2〜BR1.4）は、これまでどおり木の全体に当てる
    violation: 該当なし（変換）
    source: 追加の質問 F1: A・AC4.1.9・project.md Forbidden（招待のトークン、認証の値を perf の記録へ置かない）

  - id: BR1.8
    statement: テキストの置換の口（k6 の標準出力・ログなど JSON でないテキスト）は、ファイルの中身全体を 1 つの文字列として BR1.2 と同じ置換の関数に通し、それ以外の文字は 1 文字も変えない
    category: calculation
    applies_to: 置換（テキストの置換の口。BR3.4）
    trigger: テキストの置換の口で入力を読んだ後
    logic: >
      入力の中身全体（UTF-8 として読んだ 1 つの文字列）に、BR1.2 の置換の関数をそのまま 1 回当てる。
      別の正規表現・別の置換の関数を持たない（JWT の形の定義 BR1.1 と置換の関数を JSON の口と共有する）。
      行に分けずに全体へ当てる（JWT の形は改行を含まないため、行ごとに当てた結果と同じになる）。
      改行（LF・CRLF）・空白・先頭の BOM・JWT の形でない部分は入力のまま残し、末尾の改行を足したり除いたりしない。
      setup_data の伏せ字（BR1.7）と、JSON に関わる決まり（BR1.3・BR1.5・BR1.6・BR2.4）は当てない（テキストは JSON の木を持たないため）。
      [REDACTED-JWT] は BR1.1 に一致しないため、もう一度当てても結果は変わらない（冪等）
    violation: 該当なし（変換）
    source: AC4.1.8・FR5.4・レビュー R-01 を受けた依頼者の決定

  # ---- BR2: 残存の検査 ----
  - id: BR2.1
    statement: 置換の後に、置換とは独立の残存の検査を、書き出す直前の文字列そのものに当てる
    category: validation
    applies_to: 道具の書き出し（JSON の口とテキストの置換の口）・evidence の検査（BR4.2）
    trigger: 置換が済み、書き出す前 / evidence の検査で各ファイルを読んだとき
    logic: >
      残存の検査は置換の関数を呼ばず、木をたどらず、文字列（書き出す直前の JSON の文字列、テキストの置換の口では書き出す直前の出力の文字列、
      または evidence の各ファイルの中身）を
      BR1.1 の形で端から調べる別の関数とする。
      IF 1 件でも見つかる THEN 失敗とする。置換と同じ形の定義を使うが、たどり方（木か文字列か）を分けることで、
      置換のたどり漏れ（キー名・入れ子・配列）を拾う
    violation: 道具では終わりのコード 4、evidence の検査では verify の失敗
    source: FR5.2・AC4.1.2・X11

  - id: BR2.2
    statement: 残存の検査で見つかったときは、出力を書かず、一時ファイルも残さない
    category: policy
    applies_to: 道具の書き出し（JSON の口とテキストの置換の口）
    trigger: BR2.1 または BR2.4 で 1 件以上見つかったとき
    logic: >
      IF 残存の検査（BR2.1・BR2.4）が失敗 THEN 出力のパスにファイルを作らず、作りかけの一時ファイルがあれば消してから非 0 で終わる。
      出力のパスに前からファイルがあったときは、そのファイルを変えない
    violation: 終わりのコード 4
    source: FR5.2・AC4.1.3

  - id: BR2.3
    statement: 道具と検査の表示（標準出力・標準エラー）に、見つけた値・置き換えた値とその前後の文字を出さない
    category: policy
    applies_to: 道具・evidence の検査のすべての表示
    trigger: 成功・失敗のどちらでも、何かを表示するとき
    logic: >
      表示してよいのは、件数、ファイルのパス、文字列の中の位置（行と桁、またはバイトの位置）、衝突したオブジェクトの道（置換の後の名前）だけ。
      JSON の読み取りの誤りの表示に、実行環境が入力の断片を含めることがあるため、その文言をそのまま出さず、位置だけを出す
    violation: 該当なし（出さない形を守る。テストで確かめる BR5.2）
    source: FR5.5・project.md Forbidden（認証の値を伏せずに perf の記録へ置かない）

  - id: BR2.4
    statement: 書き出す直前の文字列を JSON として読み直し、setup_data の下に [REDACTED-SETUP] でない文字列の値が残っていないことを、置換とは独立に確かめる
    category: validation
    applies_to: 道具の書き出し（BR1.7 の確かめ）
    trigger: BR2.1 と同じ時点（書き出す直前）
    logic: >
      置換の関数を呼ばず、書き出す直前の文字列を JSON として読み直した木の上の階層の setup_data の下をたどる。
      IF [REDACTED-SETUP] でない文字列の値が 1 件でもある THEN BR2.2 と同じく出力も一時ファイルも残さず失敗にする。
      表示は件数と道（キー名でたどったもの。キー名は BR1.2 を当てた後のもの）だけで、値は出さない（BR2.3）。
      evidence の検査（BR4.2）は json 以外も調べるため、この確かめは含めない
    violation: 終わりのコード 4（残存の検出と同じ扱い）
    source: 追加の質問 F1: A・FR5.2

  # ---- BR3: 入力・出力・失敗 ----
  - id: BR3.1
    statement: 入力が読めない・空・JSON でないときは失敗にし、出力を書かない
    category: validation
    applies_to: 道具の入力
    trigger: 入力を読むとき
    logic: >
      IF 入力のファイルが無い・読めない、中身が空（空白だけを含む）、または JSON として読めない
      THEN 出力を書かず、終わりのコード 2 で終わる。JSON の上の階層の形（オブジェクトか配列か）は問わない
    violation: 終わりのコード 2
    source: AC4.1.4

  - id: BR3.2
    statement: JSON の口の引数は入力のパスと出力のパスの 2 つ、テキストの置換の口の引数は --text と入力のパスと出力のパスの 3 つで、どちらも出力のパスは evidence という名前のディレクトリの下に置く
    category: validation
    applies_to: 道具の呼び出し（契約 C7 と、それに足すテキストの置換の口）
    trigger: 道具を起動したとき
    logic: >
      最初の引数が --text なら テキストの置換の口（BR3.4）、そうでなければ JSON の口として扱う。
      IF 口ごとの引数の数が合わない（JSON の口で 2 つでない、テキストの置換の口で --text の後が 2 つでない）、
      出力のパスの親のディレクトリが無い、出力のパスのどこにも evidence という名前のディレクトリが無い、
      または入力と出力が同じファイル THEN 何も読み書きせず終わりのコード 1 で終わる。
      出力のパスに前からファイルがあるときは、成功したときだけ置き換える（BR3.3）
    violation: 終わりのコード 1
    source: FR5.1・NFR8・契約 C7

  - id: BR3.3
    statement: 出力は、すべての検査が通った後に一度に置き、途中の状態のファイルを出力のパスに残さない
    category: policy
    applies_to: 道具の書き出し（JSON の口とテキストの置換の口）
    trigger: 書き出すとき
    logic: >
      出力のパスと同じディレクトリに一時ファイルを書き、書き終えてから出力のパスへ名前を替えて置く。
      IF 書き込み・名前の替え・その前の検査のどこかで失敗 THEN 一時ファイルを消し、出力のパスは元のまま（無ければ無いまま）にする。
      終わりのコードは 0 成功、1 呼び出しの誤り、2 入力の誤り、3 キーの衝突、4 残存の検出、5 書き出しの失敗とする
    violation: 終わりのコード 5（書き出しの失敗）
    source: FR5.2・AC4.1.3・契約 C7

  - id: BR3.4
    statement: テキストの置換の口は、入力のテキストを確かめ、BR1.8 で置き換え、出力の文字列に残存の検査（BR2.1）を当ててから、JSON の口と同じ形で一度に書き出す
    category: validation
    applies_to: 道具のテキストの置換の口（node perf/<伏せ字の道具> --text <入力> <evidence/ の中の出力のパス>）
    trigger: --text で起動したとき
    logic: >
      (1) 引数と出力のパスは BR3.2 で確かめる（誤りはコード 1）。
      (2) IF 出力のパスの拡張子が .json THEN 何も書かずコード 1（summary json は JSON の口で置く）。
      (3) 入力を読む。IF 無い・読めない、中身が空（空白だけを含む）、UTF-8 として読めない（不正なバイト列）、
      または NUL の文字を含む（evidence の検査 BR4.2 がバイナリとして飛ばす形を記録に置かないため）THEN 何も書かずコード 2。
      (4) IF 入力が JSON として読めて上の階層がオブジェクトで setup_data というキーを持つ（k6 の summary json）
      THEN 何も書かずコード 1（BR1.7 を通さずに summary json を置く抜け道を作らない）。
      (5) BR1.8 で置き換えた出力の文字列に BR2.1 の残存の検査を当てる。IF 1 件でも見つかる THEN 何も書かずコード 4（BR2.2）。
      (6) BR3.3 と同じく、同じディレクトリの一時ファイルに書いてから名前を替えて置く。失敗すれば一時ファイルを消しコード 5。
      (7) 成功したら、置き換えた件数と出力のパスだけを表示しコード 0 で終わる（BR2.3）。
      どの失敗でも、出力のパスは起動の前と同じで、一時ファイルは残らない。コード 3（キーの衝突）はこの口では起きない
    violation: 終わりのコード 1・2・4・5
    source: AC4.1.8・AC4.1.3・AC4.1.4・FR5.2・FR5.4・レビュー R-01 を受けた依頼者の決定

  # ---- BR4: 記録を置く手順と既存の記録の検査 ----
  - id: BR4.1
    statement: k6 の summary json と標準出力・ログを evidence/ に置く唯一の入口はこの道具で、手で写して置かない
    category: policy
    applies_to: perf の手順（perf/README.md）と Build and Test・Performance Validation の作業
    trigger: k6 の結果を記録に置くとき
    logic: >
      k6 は使い捨ての出力の置き場（build/perf-results など、git の対象外）に --summary-export で summary json を書き、
      標準出力・ログも同じ置き場のファイルに受ける。
      記録へは、summary json は「node perf/<伏せ字の道具> <出力の置き場の json> <evidence/ の中のパス>」で、
      標準出力・ログは「node perf/<伏せ字の道具> --text <出力の置き場のファイル> <evidence/ の中のパス>」でだけ置く。
      どちらの口で置いた後も、evidence の検査（BR4.2 と同じ口）と gitleaks dir（BR4.3）を流す。
      cp・手での編集・エディタでの貼り付けで evidence/ に置かない。手で置いたものは BR4.2 が拾う（JWT の形だけ。拾えない値は functional-spec.md 10節）
    violation: BR4.2 の検査で verify が失敗する
    source: NFR8・AC4.1.7・FR5.4

  - id: BR4.2
    statement: ./gradlew verify の中で、aidlc/ の下のすべての evidence/ のすべてのテキストのファイルに、残存の検査を当てる
    category: validation
    applies_to: verify（CI も同じ verify を呼ぶ）
    trigger: verify の安全の検査の段（verifySecurity）
    logic: >
      aidlc/ の下で evidence という名前のディレクトリを探し、その下（入れ子を含む）のすべての通常のファイルを対象にする。
      拡張子では絞らず（.json・.md・.txt・.log・.tsv など）、中身の先頭に NUL の文字を含むものだけをバイナリとして飛ばし、飛ばした件数を表示する。
      git の対象か否かを問わず作業フォルダの上のファイルを調べる（コミットの前に拾うため）。
      調べ方は BR2.1 の残存の検査の関数と同じ（道具のファイルの検査の口から呼ぶ）。
      IF 1 件でも見つかる THEN verify を失敗させ、ファイルのパスと件数と位置だけを表示する（BR2.3）
    violation: verify の失敗（統合の前の関門で止まる）
    source: AC4.1.7・Q1: A・FR5.5

  - id: BR4.3
    statement: evidence/ に置いた後に evidence の検査と gitleaks dir を流し、どちらも 0 件であることを確かめてから記録をコミットする
    category: policy
    applies_to: perf の手順・Build and Test
    trigger: 道具（JSON の口・テキストの置換の口のどちらでも）で evidence/ に置いた後、コミットの前
    logic: >
      置いた evidence/ に evidence の検査（BR4.2 と同じ口。verify を待たずに単独で呼ぶ）を流し、
      続けて gitleaks dir（リポジトリの .gitleaks.toml を使い、値を伏せる表示）を流す。
      IF どちらかで 1 件でも見つかる THEN コミットせず、原因を確かめて置き直す。
      既存の verify の gitleaksScan はコミット済みの履歴だけを見るため、まだコミットしていない記録はこの手順と BR4.2 で守る
    violation: コミットしない（手順の関門）
    source: FR5.3・AC4.1.6・team.md Testing Posture

  # ---- BR5: 仕組みのテスト ----
  - id: BR5.1
    statement: 置換と残存の検査に、fast-check の性質ベースのテストを当て、4 つの性質を確かめる
    category: validation
    applies_to: 仕組みのテスト（frontend/ の Vitest から道具を呼ぶ）
    trigger: verify と CI のたび
    logic: >
      ランダムな JSON（入れ子・配列・数・真偽・null・数 MB までの長い文字列・キー名）に、JWT の形の文字列と、
      JWT に近い文字列（2 区間・eyJ の前に英数字・末尾の =・Bearer の前置き）を混ぜて、
      (1) 置換の後の文字列に残存の検査が 0 件、(2) 置換を 2 回当てても 1 回と同じ（setup_data の内と外の両方で）、
      (3) setup_data の外では、JWT を含まない値は変わらない、(4) 出力が JSON として読め、キーの集合と並びと値の型が入力と同じ
      （setup_data の内と外の両方で。キーの衝突が無い入力に限る）、
      (5) 上の階層の setup_data の下の文字列の値はすべて [REDACTED-SETUP] になり、数・真偽・null は変わらない、
      を確かめる。入力の半分程度は、上の階層に setup_data（入れ子・配列・数・文字列・JWT の形を混ぜる）を持つ形で作る。
      失敗のときの乱数の種と道を表示して再現できるようにする
    violation: テストの失敗（verify の失敗）
    source: FR5.3・AC4.1.5・team.md Testing Posture

  - id: BR5.2
    statement: 境界と失敗の場合を、例のテストで1つずつ確かめる
    category: validation
    applies_to: 仕組みのテスト
    trigger: verify と CI のたび
    logic: >
      少なくとも次を確かめる。2 区間は残る・eyJ の前が英数字なら残る・1 つの文字列の複数の JWT がすべて替わる・
      Bearer の後ろだけが替わる・キー名の JWT が替わる・キーの衝突で終わりのコード 3 と出力なし・数 MB の文字列の中でも替わる・
      空と JSON でない入力で終わりのコード 2 と出力なし・残存の検査が JWT の形を含む文字列で失敗し出力も一時ファイルも残らない・
      表示に値が出ない（BR2.3）・setup_data の下の招待のトークンに似た文字列（JWT の形でない）と JWT が [REDACTED-SETUP] になり数は残る（BR1.7）・
      setup_data の値そのものが文字列・配列のとき・深い所の setup_data は特別に扱わないこと・
      evidence の検査が JWT の形を含む .md や .log のファイルを拾い、値を表示しない。
      テキストの置換の口では、LF と CRLF の改行・末尾の改行の有無・先頭の BOM が変わらない・1 行に複数の JWT と Bearer の後ろが替わる・
      空と空白だけと不正な UTF-8 と NUL を含む入力でコード 2 と出力なし・出力の拡張子 .json でコード 1・上の階層に setup_data を持つ JSON の入力でコード 1・
      引数の誤り（--text の後が 2 つでない、出力が evidence/ の外）でコード 1・残存の検査が見つけたときコード 4 で出力も一時ファイルも残らない・
      表示に値が出ない。
      テストの中の JWT の形の値は、実行の中で組み立て、テストのソースに完成した JWT の形の文字列を書かない（gitleaks と BR4.2 を自分で落とさないため）
    violation: テストの失敗（verify の失敗）
    source: AC4.1.1〜AC4.1.4・AC4.1.7・FR5.3

  - id: BR5.3
    statement: 仕組みのテストは ./gradlew verify と CI で毎回実行され、飛ばされない
    category: policy
    applies_to: verify・CI
    trigger: verify と CI のたび
    logic: >
      テストは frontend/ の Vitest の単体テストの対象（verifyUnitTest の段）に入れ、条件で飛ばす仕組みを持たない。
      道具のファイルが frontend/ の ESLint・oxlint・TypeScript・ライセンスヘッダーの検査の対象に入らないときは、
      ADR-006 の切り替え先（道具を frontend/ の下に置く、または perf/ に専用の検査の入口を置く）で検査に入れる。どちらにするかはコード生成で決める
    violation: verify の構成の誤り（コード生成の計画で確かめる）
    source: FR5.3・AC4.1.6・ADR-004・ADR-006

  - id: BR5.4
    statement: テキストの置換の口に、fast-check の性質ベースのテストを当て、3 つの性質を確かめる
    category: validation
    applies_to: 仕組みのテスト（テキストの置換の口と、BR1.2 の共有の置換の関数）
    trigger: verify と CI のたび
    logic: >
      ランダムなテキスト（任意の Unicode の文字・LF と CRLF の改行・空白・長い行・JSON に似た断片）に、
      実行の中で組み立てた JWT の形の文字列と JWT に近い文字列（2 区間・eyJ の前に英数字・末尾の =・Bearer の前置き・. が 4 つ以上）を混ぜて、
      (t1) 置換の後のテキストに残存の検査が 0 件、(t2) 置換を 2 回当てても 1 回と同じ（冪等）、
      (t3) JWT の形を含まないテキストは置換の前と後で文字単位で同じ（改行・空白・JWT に近い文字列を含めて）、を確かめる。
      テキストの置換の口が JSON の口と同じ置換の関数を呼ぶこと（別の置換を持たないこと）は、例のテストで同じ文字列に当てた結果が一致することで確かめる。
      失敗のときの乱数の種と、値を含まない位置を表示して再現できるようにする
    violation: テストの失敗（verify の失敗）
    source: AC4.1.5・AC4.1.8・FR5.3・team.md Testing Posture・レビュー R-01 を受けた依頼者の決定

  # ---- BR6: 文書と台本の決まり ----
  - id: BR6.1
    statement: perf/README.md に、伏せる対象・対象外・伏せる手順・唯一の入口を書く節を置く
    category: policy
    applies_to: perf/README.md（U4 が節を書き、U5 が整理した README に収める）
    trigger: 道具を入れるとき
    logic: >
      節には次を書く。(1) 伏せる対象は k6 の summary json（--summary-export）で、JWT は全体で [REDACTED-JWT] に、
      setup() の返す値（setup_data）の下の文字列は JWT の形かにかかわらずすべて [REDACTED-SETUP] になる（数は残る）、
      (2) 記録へ置く唯一の入口と呼び方・終わりのコード、(3) summary json の対象外は k6 の標準出力とログで、記録に置くときは
      テキストの置換の口「node perf/<伏せ字の道具> --text <使い捨ての置き場のファイル> <evidence/ の中のパス>」で置く（BR1.8・BR3.4。
      JSON の口と同じ置換で、setup_data の伏せ字は当たらない）こと、どちらの口でも置いた後に evidence の検査と gitleaks dir を流す手順（BR4.3）、
      (4) リフレッシュトークンと Authorization ヘッダーの値が summary json に入らないことを確かめた結果と、その前提（BR6.2）
    violation: 該当なし（文書）
    source: FR5.4・AC4.1.8・AC4.1.9

  - id: BR6.2
    statement: k6 の台本は、秘密の値（トークン・Cookie・Authorization ヘッダー・招待のトークン・パスワード）を、setup_data の外に出る所（タグの値・check の名前・指標の名前・group の名前）と標準出力・ログ（console.log の行・誤りの文）に入れない
    category: constraint
    applies_to: perf/k6/scenarios.js（今とこれから足す場面）
    trigger: 場面を足す・変えるとき
    logic: >
      summary json に台本の値が入る道は、setup() の返す値（setup_data。BR1.7 で文字列をすべて伏せる）と、
      指標・タグ・check・group の名前（metrics・root_group。伏せない）である。
      秘密の値は setup_data か VU ごとの状態（setup の外）にだけ持ち、タグの値・check の名前などには固定の名前だけを使う。
      リフレッシュトークンの Cookie は VU ごとの状態にだけ持ち、Authorization ヘッダーは要求のたびに組み立てる（今の台本の形）。
      秘密の値を console.log や誤りの文で標準出力・ログに出さない（テキストの置換の口 BR1.8 は JWT の形だけを替え、
      JWT の形でない秘密は置換でも evidence の検査 BR4.2 でも拾えないため。置いた後の gitleaks dir BR4.3 と合わせて守る）。
      IF 秘密の値を setup_data の外に出す必要が出た THEN 置換の対象を広げるかを依頼者に諮る（AC4.1.9）
    violation: 依頼者に諮るまで、その場面の結果を記録に置かない
    source: AC4.1.9・FR5.5・追加の質問 F1: A
```

## 決まりの要約

| ID | 種類 | 要点 | 出どころ |
|---|---|---|---|
| BR1.1 | validation | JWT の形 = eyJ で始まる base64url の 3 区間。2 区間と、直前が英数字のものは対象外 | FR5.1・AC4.1.1 |
| BR1.2 | calculation | 1 つの文字列の中のすべてを [REDACTED-JWT] に。冪等 | FR5.1・AC4.1.1・AC4.1.5 |
| BR1.3 | calculation | 値・配列の要素・キー名・長い文字列のすべての文字列に当て、文字列でない値は触らない | FR5.1・FR5.3 |
| BR1.4 | calculation | `Bearer eyJ…` は JWT の部分だけを替える | AC4.1.1 |
| BR1.5 | constraint | キー名の置換で衝突したら失敗（コード 3） | AC4.1.1 |
| BR1.6 | constraint | 出力は JSON として読め、キー・並び・型が入力と同じ | FR5.1・AC4.1.5 |
| BR1.7 | calculation | 上の階層の setup_data の下の文字列をすべて [REDACTED-SETUP] に（キーと型は保つ） | F1: A・AC4.1.9 |
| BR1.8 | calculation | テキストの置換の口は中身全体に BR1.2 と同じ関数を当て、ほかの文字は変えない（setup_data の扱いは無い） | AC4.1.8・R-01 |
| BR2.1 | validation | 置換と独立の残存の検査を、書き出す直前の文字列に当てる | FR5.2・AC4.1.2 |
| BR2.2 | policy | 残存があれば出力も一時ファイルも残さない（コード 4） | AC4.1.3 |
| BR2.3 | policy | 表示に値・前後の文字を出さない（件数・パス・位置だけ） | FR5.5 |
| BR2.4 | validation | 読み直した木で setup_data の下に伏せていない文字列が無いことを独立に確かめる（コード 4） | F1: A・FR5.2 |
| BR3.1 | validation | 入力が無い・空・JSON でない → 失敗（コード 2） | AC4.1.4 |
| BR3.2 | validation | 引数は JSON の口で 2 つ、テキストの置換の口で --text と 2 つ。出力は evidence/ の下（コード 1） | 契約 C7・NFR8 |
| BR3.3 | policy | 一時ファイルに書いてから置く。終わりのコードの割り当て | AC4.1.3・契約 C7 |
| BR3.4 | validation | テキストの置換の口の確かめ（.json の出力と summary json の入力はコード 1、空・不正な UTF-8・NUL はコード 2）、残存の検査、同じ書き出し | AC4.1.8・AC4.1.3・R-01 |
| BR4.1 | policy | evidence/ に置く唯一の入口はこの道具（summary json は JSON の口、標準出力・ログはテキストの置換の口） | NFR8・AC4.1.7 |
| BR4.2 | validation | verify の中で aidlc/ の下のすべての evidence/ のテキストを調べる | AC4.1.7・Q1: A |
| BR4.3 | policy | 置いた後に evidence の検査と gitleaks dir で 0 件を確かめてからコミット | AC4.1.6 |
| BR5.1 | validation | fast-check の 5 つの性質（(3) は setup_data の外、(5) は setup_data の伏せ字）と種の記録 | AC4.1.5 |
| BR5.2 | validation | 境界と失敗の例のテスト | AC4.1.1〜AC4.1.4・AC4.1.7 |
| BR5.3 | policy | テストは verify と CI で毎回、飛ばさない | AC4.1.6 |
| BR5.4 | validation | テキストの置換の口の 3 つの性質（残存 0 件・冪等・JWT の無いテキストは不変）と種の記録 | AC4.1.5・AC4.1.8・R-01 |
| BR6.1 | policy | perf/README.md の節の中身 | FR5.4・AC4.1.8 |
| BR6.2 | constraint | 台本は秘密の値を setup_data の外（タグ・check・指標・group の名前）と標準出力・ログに出さない | AC4.1.9・F1: A |
