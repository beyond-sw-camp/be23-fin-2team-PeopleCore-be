package com.peoplecore.approval.slot.type;

import com.peoplecore.approval.slot.SlotContextDto;
import com.peoplecore.approval.slot.SlotType;

public class CustomSlot implements SlotType {
    private final String customValue;

    public CustomSlot(String customValue) {
        this.customValue = customValue;
    }

    @Override
    public String resolve(SlotContextDto context) {
        return customValue;  // DB에 저장된 직접 입력값 그대로
    }

    @Override
    public String getCode() { return "CUSTOM"; }
}