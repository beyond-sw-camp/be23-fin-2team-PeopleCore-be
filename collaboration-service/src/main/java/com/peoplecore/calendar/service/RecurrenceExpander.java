package com.peoplecore.calendar.service;


import com.peoplecore.calendar.entity.Events;
import com.peoplecore.calendar.entity.RepeatedRules;
import com.peoplecore.calendar.enums.Frequency;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.C;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class RecurrenceExpander {

//  무한루프 방어용
    private static final int MAX_ITERATIONS = 1000;

    public static class Occurrence {
        public final LocalDateTime startAt;
        public final LocalDateTime endAt;

        public Occurrence(LocalDateTime s, LocalDateTime e){
            this.startAt = s;
            this.endAt = e;
        }
    }

//    (사용자가 화면에서)조회하는 기간이랑 겹치는 경우만 반환
    public List<Occurrence> expand(Events event, LocalDateTime viewStart, LocalDateTime viewEnd){
        List<Occurrence> result = new ArrayList<>();
        if(event.getRepeatedRules() == null){
//            단일 일정
            if (overlaps(event.getStartAt(), event.getEndAt(), viewStart, viewEnd)){
                result.add(new Occurrence(event.getStartAt(), event.getEndAt()));
            }
            return result;
        }

        RepeatedRules rule = event.getRepeatedRules();
//        일정 한건의 지속시간
        Duration duration = Duration.between(event.getStartAt(), event.getEndAt());
        int interval = rule.getIntervalVal() == null ? 1 : Math.max(1, rule.getIntervalVal());
        Integer maxCount = rule.getCount();
        LocalDateTime untilEnd = rule.getUntil() == null ? null : rule.getUntil().atTime(23,59,59);

//        cursor(커서) 로 움직이며 현재 지점을 가리키기
        LocalDateTime cursor = event.getStartAt();
        int produced = 0;

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            if (untilEnd != null && cursor.isAfter(untilEnd)) break;    //종료날짜 도달
            if (maxCount != null && produced >= maxCount) break;        //반복횟수 도달
            if (cursor.isAfter(viewEnd)) break;                         //view 범위까지

//            cursor(시작시각) 에 duration 만큼 더하기 -> 종료시각
            LocalDateTime cursorEnd = cursor.plus(duration);

            if (cursorEnd.isAfter(viewStart) || cursorEnd.equals(viewStart)){
                result.add(new Occurrence(cursor, cursorEnd));
            }

            produced++;
            cursor = nextCursor(cursor, rule.getFrequency(), interval);
        }

        if (result.size() >= MAX_ITERATIONS){
            log.warn("RecurrenceExpander 최대반복한도(1000회) 도달(hit MAX_ITERATIONS) for eventsId={}", event.getEventsId());
        }
        return result;
    }


    private LocalDateTime nextCursor(LocalDateTime current, Frequency freq, int interval){
        if (freq == null){
            return current.plusDays(interval);
        }
        switch (freq){
            case DAILY: return current.plusDays(interval);
            case WEEKLY: return current.plusWeeks(interval);
            case MONTHLY: return current.plusMonths(interval);
            case YEARLY: return current.plusYears(interval);
            default: return current.plusDays(interval);
        }
    }


    private boolean overlaps(LocalDateTime aStart, LocalDateTime aEnd, LocalDateTime bStart, LocalDateTime bEnd)    {
        return aStart.isBefore(bEnd) && aEnd.isAfter(bStart);
    }
}
