package com.peoplecore.pay.domain;

import com.peoplecore.company.domain.Company;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.enums.LegalCalcType;
import com.peoplecore.pay.enums.PayItemCategory;
import com.peoplecore.pay.enums.PayItemType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

import static com.peoplecore.pay.enums.PayItemType.DEDUCTION;
import static com.peoplecore.pay.enums.PayItemType.PAYMENT;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Table(name = "pay_items")  //급여항목
public class PayItems {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long payItemId;

    @Column(length = 100, nullable = false)
    private String payItemName;

//    지급/공제 구분
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayItemType payItemType;

//    과세여부
    @Builder.Default
    private Boolean isTaxable = true;

//    고정항목 여부 (금액이 고정)
    @Builder.Default
    private Boolean isFixed = true;

    private Integer sortOrder;

    @Builder.Default
    private Boolean isActive = true;    //사용, 미사용

    @Builder.Default
    private Boolean isDeleted = false; //삭제 -> 화면에서 안보여줌

//    항목분류
    @Enumerated(EnumType.STRING)
    private PayItemCategory payItemCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

//    법정수당여부
    @Builder.Default
    private Boolean isLegal = false;

//    법정산정방식구분용
    @Enumerated(EnumType.STRING)
    private LegalCalcType legalCalcType;

//    비과세 한도
    @Builder.Default
    private Integer taxExemptLimit = 0;

//    시스템 자동생성항목 - 정산전용 (삭제/수정 불가)
    @Builder.Default
    private Boolean isSystem = false;

//    시스템 보호항목 (수정/삭제 불가)
    @Builder.Default
    private Boolean isProtected = false;


    public void update(String payItemName, Boolean isFixed, Boolean isTaxable, Integer taxExemptLimit, PayItemCategory payItemCategory){
        if(Boolean.TRUE.equals(this.isSystem)){
            throw new CustomException(ErrorCode.SYSTEM_PAY_ITEM_NOT_EDITABLE);
        }
        if (Boolean.TRUE.equals(this.isProtected)) {
            throw new CustomException(ErrorCode.PROTECTED_PAY_ITEM_NOT_EDITABLE);
        }
        this.payItemName = payItemName;
        this.isFixed = isFixed;
        this.isTaxable = isTaxable;
        this.taxExemptLimit = taxExemptLimit;
        this.payItemCategory =payItemCategory;
    }

//    사용여부 토글
    public void toggleActive(){
        this.isActive = !this.isActive;
    }

    public void softDelete(){
        if(Boolean.TRUE.equals(this.isSystem)){
            throw new CustomException(ErrorCode.SYSTEM_PAY_ITEM_NOT_DELETABLE);
        }
        if (Boolean.TRUE.equals(this.isProtected)) {
            throw new CustomException(ErrorCode.PROTECTED_PAY_ITEM_NOT_DELETABLE);
        }
        this.isDeleted = true;
        this.isActive = false;
    }

}
