package kr.tx24.task.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 스케줄링할 Task 클래스에 지정하는 Annotation
 * 
 * 사용 예시:
 * @Task(
 *     name = "dailyReport",
 *     time = "09:00",
 *     period = "1d",
 *     daysOfWeek = {DayOfWeek.MONDAY, DayOfWeek.FRIDAY},
 *     startDay = "20250101",
 *     endDay = "20251231",
 *     enabled = true,
 *     desc = "일일 리포트 생성",
 *     priority = 1
 * )
 * public class DailyReportTask implements Runnable { ... }
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Task {
    
    /**
     * Task 이름 (고유 식별자)
     */
    String name();
    
    /**
     * 실행 시간 (HH:mm 형식)
     * 예: "09:00", "23:30"
     */
    String time();
    
    /**
     * 반복 주기
     * 형식: 
     * - M: 월 단위 (startDay 기준)
     * - 숫자+w: 주 단위 (예: 1w, 2w)
     * - 숫자+d: 일 단위 (예: 1d, 7d)
     * - 숫자+h: 시간 단위 (예: 2h, 6h)
     * - 숫자+m: 분 단위 (예: 30m, 45m)
     * 
     * 예: "M" (매월), "2w" (2주), "1d" (1일), "2h" (2시간), "30m" (30분)
     */
    String period();
    
    /**
     * 실행할 요일 지정 (빈 배열이면 매일 실행)
     * 예: {DayOfWeek.MONDAY, DayOfWeek.FRIDAY}
     */
    java.time.DayOfWeek[] daysOfWeek() default {};
    
    /**
     * Task 시작 일자 (yyyyMMdd 형식)
     * 이 날짜부터 스케줄 시작
     * 빈 문자열이면 즉시 시작
     * 예: "20250101", "20251231"
     */
    String startDay() default "";
    
    /**
     * Task 종료 일자 (yyyyMMdd 형식)
     * 이 날짜 이후로는 실행 안 됨
     * 빈 문자열이면 종료 없음
     * 예: "20251231"
     */
    String endDay() default "";
    
    /**
     * 활성화 여부
     */
    boolean enabled() default true;
    
    /**
     * Task 설명
     */
    String desc() default "";
    
    /**
     * 우선순위 (높을수록 먼저 스케줄)
     */
    int priority() default 50;
}
