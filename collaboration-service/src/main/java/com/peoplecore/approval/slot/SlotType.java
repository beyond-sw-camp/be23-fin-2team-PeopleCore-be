package com.peoplecore.approval.slot;

public interface SlotType {
    String resolve(SlotContextDto context);
    String getCode();
}
