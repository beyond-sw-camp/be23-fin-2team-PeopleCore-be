package com.peoplecore.approval.slot.type;

import com.peoplecore.approval.slot.SlotContextDto;
import com.peoplecore.approval.slot.SlotType;

public class DeptNameSlot implements SlotType {
    public static final DeptNameSlot INSTANCE = new DeptNameSlot();

    @Override
    public String resolve(SlotContextDto context) {
        return context.getDeptName();
    }

    @Override
    public String getCode() { return "DEPT_NAME"; }
}