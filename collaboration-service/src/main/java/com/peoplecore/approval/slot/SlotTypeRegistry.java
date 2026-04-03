package com.peoplecore.approval.slot;

import com.peoplecore.approval.slot.type.*;
import com.peoplecore.common.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class SlotTypeRegistry {
    private final Map<String, SlotType> registry = new HashMap<>();

    @PostConstruct
    public void init() {
        register(CompanyNameSlot.INSTANCE);
        register(DeptCodeSlot.INSTANCE);
        register(DeptNameSlot.INSTANCE);
        register(FormCodeSlot.INSTANCE);
        register(FormNameSlot.INSTANCE);
        register(NoneSlot.INSTANCE);
    }

    public void register(SlotType slotType) {
        registry.put(slotType.getCode(), slotType);
    }

    public SlotType find(String code, String customValue) {
        if ("CUSTOM".equals(code)) {
            return new CustomSlot(customValue != null ? customValue : "");
        }
        SlotType slotType = registry.get(code);
        if (slotType == null) {
            throw new BusinessException("알 수 없는 슬롯 타입: " + code);
        }
        return slotType;

    }

}
