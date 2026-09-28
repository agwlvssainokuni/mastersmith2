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
package cherry.mastersmith.invitation.service;

/**
 * 招待と登録の完了の、同時の操作を確実に重ねるための待ち合わせの口（{@code reliability-design.md} 2.3・3節）。
 *
 * <p>本番は何もしない既定の部品（{@link NoOpInvitationBarrier}）を使い、本番のコードの流れは変えない。同時の操作の結合テストが
 * 差し替えて、重なりを確実に作る（U2 の {@code PasswordChangeBarrier} と同じ考え方）。
 */
public interface InvitationBarrier {

    /** 招待の行を追記する直前に呼ばれる（招待のトランザクションの中）。 */
    void beforeInsert();

    /**
     * 招待の行の排他を得た直後に呼ばれる（送り直し・取り消し・登録の完了のトランザクションの中）。
     *
     * @param invitationId 排他を得た招待の ID
     */
    void afterLock(long invitationId);
}
