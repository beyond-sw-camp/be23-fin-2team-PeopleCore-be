package com.peoplecore.approval.slot.type;

import com.peoplecore.approval.slot.SlotContextDto;
import com.peoplecore.approval.slot.SlotType;

public class CompanyNameSlot implements SlotType {
    public static final CompanyNameSlot INSTANCE = new CompanyNameSlot();

    @Override
    public String resolve(SlotContextDto context) {
        return context.getCompanyName();
    }

    @Override
    public String getCode() {
        return "COMPANY_NAME";
    }
}
