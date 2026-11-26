package com.ttuk.grouppurchase;

import com.ttuk.category.CategoryRepository;
import com.ttuk.grouppurchase.dto.SellerGroupPurchaseListRequest;
import com.ttuk.grouppurchase.dto.SellerGroupPurchaseRow;
import com.ttuk.member.Member;
import com.ttuk.member.MemberRepository;
import com.ttuk.member.MemberRole;
import com.ttuk.participation.Participation;
import com.ttuk.participation.ParticipationRepository;
import com.ttuk.participation.dto.JoinedQuantity;
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
class SellerGroupPurchaseQueryIntegrationTest {

    private static final long FRESH_FOOD_ID = 1L;

    @Autowired
    GroupPurchaseQueryRepository groupPurchaseQueryRepository;
    @Autowired
    GroupPurchaseRepository groupPurchaseRepository;
    @Autowired
    ParticipationRepository participationRepository;
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    CategoryRepository categoryRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    Clock clock;

    private Member seller;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now(clock);
        seller = saveMember("seller@ttuk.kr", "판매자", MemberRole.SELLER, "01022223333");
    }

    @AfterEach
    void cleanUp() {
        participationRepository.deleteAll();
        groupPurchaseRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("내가 등록한 공동구매만 최신순으로 나오고, 개수도 내 것만 센다")
    void findSellerList_ownOnlyLatestFirst() {
        Member otherSeller = saveMember("other@ttuk.kr", "다른판매자", MemberRole.SELLER, "01044445555");
        save(seller, "대파 한 단", now.plusDays(1));
        save(otherSeller, "양파 3kg", now.plusDays(1));
        save(seller, "감자 5kg", now.plusDays(2));

        List<SellerGroupPurchaseRow> rows = groupPurchaseQueryRepository.findSellerList(
                seller.getId(), new SellerGroupPurchaseListRequest(null, null, null), now);

        assertThat(rows).extracting(SellerGroupPurchaseRow::title)
                .containsExactly("감자 5kg", "대파 한 단");
        assertThat(groupPurchaseQueryRepository.countSellerList(seller.getId(), null, now))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("상태 필터는 화면 기준이다 — 마감 시각이 지난 OPEN은 OPEN이 아니라 CLOSED에 나온다")
    void findSellerList_statusFilter() {
        save(seller, "진행 중", now.plusDays(1));
        save(seller, "정산 전", now.minusMinutes(1));
        GroupPurchase closed = save(seller, "마감", now.minusDays(1));
        GroupPurchase canceled = save(seller, "취소", now.plusDays(1));
        finish(closed.getId(), GroupPurchaseStatus.CLOSED, 12, 2700);
        finish(canceled.getId(), GroupPurchaseStatus.CANCELED, 5, 2850);

        assertFilter(GroupPurchaseStatus.OPEN, "진행 중");
        List<SellerGroupPurchaseRow> closedRows = assertFilter(GroupPurchaseStatus.CLOSED, "마감", "정산 전");
        assertFilter(GroupPurchaseStatus.CANCELED, "취소");

        SellerGroupPurchaseRow closedRow = closedRows.get(0);
        assertThat(closedRow.basePrice()).isEqualTo(3000);
        assertThat(closedRow.maxHeadcount()).isEqualTo(30);
        assertThat(closedRow.finalHeadcount()).isEqualTo(12);
        assertThat(closedRow.finalPrice()).isEqualTo(2700);
    }

    @Test
    @DisplayName("수량 합은 JOINED만 더하고, 참여가 없는 공동구매는 결과에 없다")
    void sumJoinedQuantities() {
        GroupPurchase joined = save(seller, "대파 한 단", now.plusDays(1));
        GroupPurchase empty = save(seller, "양파 3kg", now.plusDays(1));
        Member buyerA = saveMember("buyer-a@ttuk.kr", "구매자에이", MemberRole.BUYER, "01011110001");
        Member buyerB = saveMember("buyer-b@ttuk.kr", "구매자비", MemberRole.BUYER, "01011110002");
        Member buyerC = saveMember("buyer-c@ttuk.kr", "구매자씨", MemberRole.BUYER, "01011110003");
        join(joined, buyerA, 3);
        join(joined, buyerB, 2);
        join(joined, buyerC, 1);
        participationRepository.cancelJoined(joined.getId(), buyerB.getId(), now);

        List<JoinedQuantity> result = participationRepository.sumJoinedQuantities(
                List.of(joined.getId(), empty.getId()));

        assertThat(result).containsExactly(new JoinedQuantity(joined.getId(), 4L));
    }

    private List<SellerGroupPurchaseRow> assertFilter(GroupPurchaseStatus status, String... expectedTitles) {
        List<SellerGroupPurchaseRow> rows = groupPurchaseQueryRepository.findSellerList(
                seller.getId(), new SellerGroupPurchaseListRequest(status, null, null), now);

        assertThat(rows).extracting(SellerGroupPurchaseRow::title)
                .containsExactly(expectedTitles);
        assertThat(groupPurchaseQueryRepository.countSellerList(seller.getId(), status, now))
                .isEqualTo(expectedTitles.length);
        return rows;
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

    private GroupPurchase save(Member owner, String title, LocalDateTime closeAt) {
        return groupPurchaseRepository.save(GroupPurchase.builder()
                .seller(owner)
                .category(categoryRepository.getReferenceById(FRESH_FOOD_ID))
                .title(title)
                .unit("개")
                .basePrice(3000)
                .floorPrice(1500)
                .discountType(DiscountType.RATE)
                .discountValue(5)
                .discountStepSize(10)
                .maxHeadcount(30)
                .closeAt(closeAt)
                .build());
    }

    private void finish(Long groupPurchaseId, GroupPurchaseStatus status, int finalHeadcount, int finalPrice) {
        jdbcTemplate.update("""
                update group_purchase
                   set status = ?, final_headcount = ?, final_price = ?
                 where id = ?
                """, status.name(), finalHeadcount, finalPrice, groupPurchaseId);
    }

    private void join(GroupPurchase groupPurchase, Member buyer, int quantity) {
        participationRepository.save(Participation.builder()
                .groupPurchase(groupPurchase)
                .member(buyer)
                .quantity(quantity)
                .joinedAt(now)
                .build());
    }

}