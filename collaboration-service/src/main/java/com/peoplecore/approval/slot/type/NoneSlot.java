package com.peoplecore.approval.slot.type;

import com.peoplecore.approval.slot.SlotContextDto;
import com.peoplecore.approval.slot.SlotType;

public class NoneSlot implements SlotType {
    public static final NoneSlot INSTANCE = new NoneSlot();

    @Override
    public String resolve(SlotContextDto context) {
        return null;  // 번호 조립 시 filter로 제외됨
    }

    @Override
    public String getCode() { return "NONE"; }
}