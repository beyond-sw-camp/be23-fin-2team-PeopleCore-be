package com.peoplecore.approval.slot.type;


import com.peoplecore.approval.slot.SlotContextDto;
import com.peoplecore.approval.slot.SlotType;

public class DeptCodeSlot implements SlotType {
    public static final DeptCodeSlot INSTANCE = new DeptCodeSlot();

    @Override
    public String resolve(SlotContextDto context) {
        return context.getDeptCode();
    }

    @Override
    public String getCode() { return "DEPT_CODE"; }
}