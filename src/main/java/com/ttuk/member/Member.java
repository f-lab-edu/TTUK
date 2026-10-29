package com.ttuk.member;

import com.ttuk.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_member_email", columnNames = "email"))
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 15)
    private String nickname;

    @Column(nullable = false, length = 20)
    private String phoneNum;

    @Column(nullable = false, length = 10)
    private String zipCode;

    @Column(nullable = false)
    private String address;

    private String addressDetail;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private MemberRole role;

    @Builder
    private Member(String email, String encodedPassword, String nickname, MemberRole role,
                   String phoneNum, String zipCode, String address, String addressDetail) {
        this.email = email;
        this.password = encodedPassword;
        this.nickname = nickname;
        this.role = role;
        this.phoneNum = phoneNum.replaceAll("[^0-9]", "");
        this.zipCode = zipCode;
        this.address = address;
        this.addressDetail = addressDetail;
    }

}
