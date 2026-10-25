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
package cherry.mastersmith.navigation.testsupport;

import cherry.mastersmith.common.testsupport.HttpTestClient;
import cherry.mastersmith.role.domain.MainPermission;
import cherry.mastersmith.role.domain.PermissionTarget;
import cherry.mastersmith.role.testsupport.RoleActors;
import cherry.mastersmith.role.testsupport.RoleActors.Actor;
import cherry.mastersmith.role.testsupport.RoleFixtures;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * navigation の結合テストの利用者・ロール・割り当て・作業ロールを作り、2本の API を送る（role の口 {@link RoleActors}・
 * {@link RoleFixtures} を使う。テストの中だけの依存）。
 */
public final class NavigationActors {

    private final RoleActors actors;

    private final RoleFixtures fixtures;

    private final HttpTestClient client;

    /**
     * 作る。
     *
     * @param actors role の利用者の部品
     * @param fixtures role のデータの部品
     * @param port アプリの待ち受けの番号
     */
    public NavigationActors(RoleActors actors, RoleFixtures fixtures, int port) {
        this.actors = actors;
        this.fixtures = fixtures;
        this.client = new HttpTestClient(port);
    }

    /**
     * ロールを作り、テーブルの階層の明示の主権限を置く。
     *
     * @param schema スキーマの名前
     * @param tables テーブルの名前ごとの主権限
     * @return ロール ID
     */
    public long role(String schema, Map<String, MainPermission> tables) {
        long roleId = fixtures.role(RoleFixtures.uniqueName("業務のメニュー"));
        tables.forEach((table, main) -> fixtures.setting(roleId, PermissionTarget.table(schema, table), main));
        return roleId;
    }

    /**
     * ロールを作り、スキーマの階層の明示の主権限を置く（テーブルは継承する）。
     *
     * @param schema スキーマの名前
     * @param main 主権限
     * @return ロール ID
     */
    public long schemaRole(String schema, MainPermission main) {
        long roleId = fixtures.role(RoleFixtures.uniqueName("業務のメニュー"));
        fixtures.setting(roleId, PermissionTarget.schema(schema), main);
        return roleId;
    }

    /**
     * ログインした利用者を作り、ロールを割り当てて作業ロールにする。
     *
     * @param roleId ロール ID
     * @return 利用者
     */
    public Actor memberWithWorkRole(long roleId) {
        Actor member = actors.member();
        fixtures.assignUser(roleId, member.userId());
        fixtures.selectWorkRole(member.userId(), roleId);
        return member;
    }

    /**
     * ロールの無いログインした利用者を作る。
     *
     * @return 利用者
     */
    public Actor member() {
        return actors.member();
    }

    /**
     * 業務のメニューを読む。
     *
     * @param token アクセストークン（無ければ null）
     * @param headers 足すヘッダー（名前, 値, ...）
     * @return 応答
     */
    public HttpResponse<String> navigation(String token, String... headers) {
        return get("/api/me/navigation", token, headers);
    }

    /**
     * 道と問い合わせをそのまま送る。
     *
     * @param pathAndQuery 道と問い合わせ
     * @param token アクセストークン（無ければ null）
     * @param headers 足すヘッダー（名前, 値, ...）
     * @return 応答
     */
    public HttpResponse<String> get(String pathAndQuery, String token, String... headers) {
        var builder = client.request(pathAndQuery).GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (headers.length > 0) {
            builder.headers(headers);
        }
        return client.send(builder.build());
    }
}
