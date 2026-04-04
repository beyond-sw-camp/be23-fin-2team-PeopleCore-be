package com.peoplecore.approval.slot.type;

import com.peoplecore.approval.slot.SlotContextDto;
import com.peoplecore.approval.slot.SlotType;

public class FormCodeSlot implements SlotType {
    public static final FormCodeSlot INSTANCE = new FormCodeSlot();

    @Override
    public String resolve(SlotContextDto context) {
        return context.getFormCode();
    }

    @Override
    public String getCode() { return "FORM_CODE"; }
}