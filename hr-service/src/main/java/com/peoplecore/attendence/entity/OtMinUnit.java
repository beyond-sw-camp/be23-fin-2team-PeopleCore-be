package com.peoplecore.attendence.entity;

import lombok.Getter;

@Getter
public enum OtMinUnit {
    FIFTEEN(15),
    THIRTY(30),
    SIXTY(60);

    private final int minutes;

    OtMinUnit(int minutes) {
        this.minutes = minutes;
    }

}
