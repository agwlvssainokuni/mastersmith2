/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cherry.mastersmith.dsl.parse;

import cherry.mastersmith.dsl.domain.DslError;
import cherry.mastersmith.dsl.domain.DslErrorKind;
import cherry.mastersmith.dsl.domain.DslMessageKeys;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.events.AliasEvent;
import org.yaml.snakeyaml.events.CollectionEndEvent;
import org.yaml.snakeyaml.events.CollectionStartEvent;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.NodeEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.parser.Parser;

/**
 * SnakeYAML の読み込みの出来事を1つずつ確かめる {@link Parser} の包み（BR1.2〜BR1.4、NFR2.2・NFR2.3・NFR3.1）。
 *
 * <p>SnakeYAML の {@code LoaderOptions} の深さ・別名の上限は位置を持たない例外で止まり、タグの検査は独自の global タグにしか
 * 効かない。そこで、節の木を作る部品（{@code Composer}）が出来事を受け取る前に、ここで次を確かめ、位置つきで止める。
 *
 * <ul>
 *   <li>タグ: 明示のタグはすべて拒否する（{@code !!} の型の指定・独自のタグ {@code !name}・{@code !} とも）
 *   <li>入れ子の深さ: 文書の根から数えた対応表と並びの段の数が上限（{@link YamlLimits#maxDepth()}）を超えたら止める
 *   <li>別名: コレクションを指す別名の数が上限（{@link YamlLimits#maxCollectionAliases()}）を超えたら止める
 * </ul>
 *
 * <p>上限は呼ぶ側が渡す。{@code LoaderOptions} にも同じ値を渡すこと（{@link SafeYamlParser#loaderOptions(YamlLimits)}）。
 *
 * <p>あわせて、別名を書いた位置を文書の順に記録する（誤りを参照を書いた場所で示すため。BR4.2）。
 */
final class LimitingParser implements Parser {

    private final Parser delegate;

    private final int maxDepth;

    private final int maxCollectionAliases;

    /** アンカーの名前ごとに、コレクションを指すかどうか。 */
    private final Map<String, Boolean> anchorIsCollection = new HashMap<>();

    private final List<YamlPosition> aliasPositions = new ArrayList<>();

    private Event lastChecked;

    private int depth;

    private int collectionAliases;

    /**
     * 作る。
     *
     * @param delegate SnakeYAML の読み込み
     * @param limits 読み込みの上限（深さと別名の数を使う）
     */
    LimitingParser(Parser delegate, YamlLimits limits) {
        this.delegate = delegate;
        this.maxDepth = limits.maxDepth();
        this.maxCollectionAliases = limits.maxCollectionAliases();
    }

    @Override
    public boolean checkEvent(Event.ID choice) {
        Event event = peekEvent();
        return event != null && event.is(choice);
    }

    @Override
    public Event peekEvent() {
        return inspect(delegate.peekEvent());
    }

    @Override
    public Event getEvent() {
        return inspect(delegate.getEvent());
    }

    /**
     * 別名を書いた位置を文書の順に返す。
     *
     * @return 別名の位置の一覧
     */
    List<YamlPosition> aliasPositions() {
        return aliasPositions;
    }

    private Event inspect(Event event) {
        if (event == null || event == lastChecked) {
            return event;
        }
        lastChecked = event;
        YamlPosition position = YamlPosition.of(event.getStartMark());
        switch (event) {
            case ScalarEvent scalar -> {
                rejectTag(scalar.getTag(), position);
                rememberAnchor(scalar, false);
            }
            case CollectionStartEvent start -> {
                rejectTag(start.getTag(), position);
                depth++;
                if (depth > maxDepth) {
                    throw YamlRejection.of(
                            DslErrorKind.DEPTH_LIMIT,
                            position,
                            null,
                            DslMessageKeys.DEPTH_LIMIT,
                            String.valueOf(maxDepth));
                }
                rememberAnchor(start, true);
            }
            case CollectionEndEvent end -> depth--;
            case AliasEvent alias -> {
                aliasPositions.add(position);
                if (Boolean.TRUE.equals(anchorIsCollection.get(alias.getAnchor()))
                        && ++collectionAliases > maxCollectionAliases) {
                    throw YamlRejection.of(
                            DslErrorKind.ALIAS_LIMIT,
                            position,
                            null,
                            DslMessageKeys.ALIAS_LIMIT,
                            String.valueOf(maxCollectionAliases));
                }
            }
            default -> {
                // 文書・流れの始まりと終わりは確かめることが無い。
            }
        }
        return event;
    }

    private void rememberAnchor(NodeEvent event, boolean collection) {
        if (event.getAnchor() != null) {
            anchorIsCollection.put(event.getAnchor(), collection);
        }
    }

    private static void rejectTag(String tag, YamlPosition position) {
        if (tag != null) {
            throw YamlRejection.of(
                    DslErrorKind.FORBIDDEN_TAG, position, null, DslMessageKeys.FORBIDDEN_TAG, DslError.excerpt(tag));
        }
    }
}
