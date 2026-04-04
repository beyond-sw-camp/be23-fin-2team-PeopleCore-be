package com.peoplecore.approval.slot.type;

import com.peoplecore.approval.slot.SlotContextDto;
import com.peoplecore.approval.slot.SlotType;

public class FormNameSlot implements SlotType {
    public static final FormNameSlot INSTANCE = new FormNameSlot();

    @Override
    public String resolve(SlotContextDto context) {
        return context.getFormName();
    }

    @Override
    public String getCode() { return "FORM_NAME"; }
}