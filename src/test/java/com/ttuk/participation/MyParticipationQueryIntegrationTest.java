package com.ttuk.participation;

import com.ttuk.category.CategoryRepository;
import com.ttuk.grouppurchase.DiscountType;
import com.ttuk.grouppurchase.GroupPurchase;
import com.ttuk.grouppurchase.GroupPurchaseRepository;
import com.ttuk.grouppurchase.GroupPurchaseStatus;
import com.ttuk.member.Member;
import com.ttuk.member.MemberRepository;
import com.ttuk.member.MemberRole;
import com.ttuk.participation.dto.MyParticipationListRequest;
import com.ttuk.participation.dto.MyParticipationRow;
import com.ttuk.support.TestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
class MyParticipationQueryIntegrationTest {

    private static final long FRESH_FOOD_ID = 1L;

    @Autowired
    ParticipationQueryRepository participationQueryRepository;
    @Autowired
    ParticipationRepository participationRepository;
    @Autowired
    GroupPurchaseRepository groupPurchaseRepository;
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    CategoryRepository categoryRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    Clock clock;

    private Member seller;
    private Member buyerA;
    private Member buyerB;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now(clock);
        seller = saveMember("seller@ttuk.kr", "판매자", MemberRole.SELLER, "01022223333");
        buyerA = saveMember("buyer-a@ttuk.kr", "구매자에이", MemberRole.BUYER, "01011110001");
        buyerB = saveMember("buyer-b@ttuk.kr", "구매자비", MemberRole.BUYER, "01011110002");
    }

    @AfterEach
    void cleanUp() {
        participationRepository.deleteAll();
        groupPurchaseRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("내 참여만 최신 참여순으로 나오고, 개수도 내 것만 센다")
    void findMyList_ownOnlyLatestFirst() {
        GroupPurchase first = save(now.plusDays(1));
        GroupPurchase second = save(now.plusDays(2));
        Participation older = join(first, buyerA);
        join(first, buyerB);
        Participation newer = join(second, buyerA);

        List<MyParticipationRow> rows = participationQueryRepository.findMyList(
                buyerA.getId(), new MyParticipationListRequest(null, null, null), now);

        assertThat(rows).extracting(MyParticipationRow::participationId)
                .containsExactly(newer.getId(), older.getId());
        assertThat(participationQueryRepository.countMyList(buyerA.getId(), null, now))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("상태 필터는 화면 기준이다 — 정산 전은 CONFIRMED, 취소한 뒤 마감된 건은 CANCELED")
    void findMyList_statusFilter() {
        GroupPurchase open = save(now.plusDays(2));
        GroupPurchase pending = save(now.minusMinutes(1));
        GroupPurchase closed = save(now.minusDays(1));
        GroupPurchase canceledBySeller = save(now.plusDays(2));
        GroupPurchase closedAfterLeave = save(now.minusDays(1));

        Participation left = join(open, buyerA);
        participationRepository.cancelJoined(open.getId(), buyerA.getId(), now);
        Participation rejoined = join(open, buyerA);
        Participation waiting = join(pending, buyerA);
        Participation confirmed = join(closed, buyerA);
        Participation sellerCanceled = join(canceledBySeller, buyerA);
        Participation leftBeforeClose = join(closedAfterLeave, buyerA);
        participationRepository.cancelJoined(closedAfterLeave.getId(), buyerA.getId(), now);

        changeStatus(closed.getId(), GroupPurchaseStatus.CLOSED);
        changeStatus(canceledBySeller.getId(), GroupPurchaseStatus.CANCELED);
        changeStatus(closedAfterLeave.getId(), GroupPurchaseStatus.CLOSED);

        assertFilter(MyParticipationStatus.OPEN, rejoined.getId());
        assertFilter(MyParticipationStatus.CONFIRMED, confirmed.getId(), waiting.getId());
        assertFilter(MyParticipationStatus.CANCELED,
                leftBeforeClose.getId(), sellerCanceled.getId(), left.getId());
        assertThat(participationQueryRepository.countMyList(buyerA.getId(), null, now))
                .isEqualTo(6);
    }

    private void assertFilter(MyParticipationStatus status, Long... expectedIds) {
        List<MyParticipationRow> rows = participationQueryRepository.findMyList(
                buyerA.getId(), new MyParticipationListRequest(status, null, null), now);

        assertThat(rows).extracting(MyParticipationRow::participationId)
                .containsExactly(expectedIds);
        assertThat(participationQueryRepository.countMyList(buyerA.getId(), status, now))
                .isEqualTo(expectedIds.length);
    }

    private Member saveMember(String email, String nickname, MemberRole role, String phoneNum) {
        return memberRepository.save(Member.builder()
                .email(email)
                .encodedPassword("encoded-password")
                .nickname(nickname)
                .role(role)
                .phoneNum(phoneNum)
                .zipCode("06236")
                .address("서울시 강남구 테헤란로 123")
                .build());
    }

    private GroupPurchase save(LocalDateTime closeAt) {
        return groupPurchaseRepository.save(GroupPurchase.builder()
                .seller(seller)
                .category(categoryRepository.getReferenceById(FRESH_FOOD_ID))
                .title("대파 한 단")
                .unit("단")
                .basePrice(3000)
                .floorPrice(1500)
                .discountType(DiscountType.RATE)
                .discountValue(5)
                .discountStepSize(10)
                .maxHeadcount(30)
                .closeAt(closeAt)
                .build());
    }

    private Participation join(GroupPurchase groupPurchase, Member buyer) {
        return participationRepository.save(Participation.builder()
                .groupPurchase(groupPurchase)
                .member(buyer)
                .quantity(1)
                .joinedAt(now)
                .build());
    }

    private void changeStatus(Long groupPurchaseId, GroupPurchaseStatus status) {
        jdbcTemplate.update("update group_purchase set status = ? where id = ?",
                status.name(), groupPurchaseId);
    }
}