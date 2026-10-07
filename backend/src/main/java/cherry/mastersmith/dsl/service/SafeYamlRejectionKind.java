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
package cherry.mastersmith.dsl.service;

/**
 * 上限つきの安全な YAML の読み込みの口（{@link SafeYamlReader}）の拒否の区分（U2 の機能設計の BR6.2、entities.md の
 * SafeYamlRejectionKind）。
 */
public enum SafeYamlRejectionKind {
    /** 本文の大きさが上限を超えた（読まずに止めた。位置なし）。 */
    TOO_LARGE,
    /** 入れ子の深さが上限を超えた。 */
    TOO_DEEP,
    /** コレクションを指す別名の数・別名を展開した後の節の数が上限を超えた、または別名が自分自身を含む。 */
    TOO_MANY_ALIASES,
    /** タグを使っている（型の指定・独自のタグとも）。 */
    TAG_NOT_ALLOWED,
    /** 1つの対応表の中で同じキーが重なっている。 */
    DUPLICATE_KEY,
    /** UTF-8 として読めない、または YAML として読めない（対応表のキーが単独の値でないときを含む）。 */
    SYNTAX
}
