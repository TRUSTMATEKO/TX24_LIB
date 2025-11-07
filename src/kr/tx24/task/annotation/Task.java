package kr.tx24.task.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 스케줄링할 Task 클래스에 지정하는 Annotation
 * 
 * Fixed Rate vs Fixed Delay 
 * !! 현재 대부분의 구동 방식은 충돌을 방지해야 하므로 DELAY 방식을 채용 
 * Fixed Rate (RATE) 사용 시기:
 * - 정확한 시간 간격이 중요한 경우
 * - 작업 실행 시간이 짧고 일정한 경우
 * - 시간 기반 통계나 모니터링
 * 
 * Fixed Delay (DELAY) 사용 시기:
 * - 작업 완료 후 일정 시간 대기가 필요한 경우
 * - 작업 실행 시간이 가변적인 경우
 * - 작업 간 충돌을 방지하고 싶은 경우
 * - 예: 큐 처리, API 호출, 데이터 동기화, 캐시 갱신
 * 
 * 주의사항:
 * - Fixed Rate: 작업이 지연되면 다음 작업이 즉시 실행될 수 있음
 * - Fixed Delay: 작업 시간이 길어도 다음 작업까지 대기 시간 보장
 * <p>
 * daysOfWeek 는 'M' 월단위는 무시하며, 지정 시는 해당 요일만 실행됩니다.<br>
 * 비어 있을 경우는 전체 일자를 의미합니다.
 * <p>
 * 사용 예시:
 * <pre>
 * {@code
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
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Task {
    
	/**
     * 스케줄링 타입
     */
    enum ScheduleType {
        /**
         * 고정 간격 스케줄링 (scheduleAtFixedRate)
         * <p>
         * 작업 시작 시점 기준으로 일정한 간격을 유지.<br>
         * 이전 작업이 지연되어도 다음 작업은 정해진 시간에 시작.
         */
        RATE,
        
        /**
         * 고정 지연 스케줄링 (scheduleWithFixedDelay)
         * <p>
         * 작업 완료 시점 기준으로 일정한 지연을 유지.<br>
         * 이전 작업이 완료된 후 정해진 시간이 지나야 다음 작업이 시작.
         * 작업 충돌이 없음.
         */
        DELAY
    }
	
	/**
     * Task 이름 (고유 식별자)
     * <p>
     * 시스템 내에서 Task를 구분하는 고유한 이름
     */
    String name();
    
    /**
     * 실행 시간 (HH:mm 형식)
     * <p>
     * <b>형식:</b> "HH:mm" (24시간 형식)<br>
     * <b>예시:</b> "09:00", "23:30"<br>
     * <b>기본값:</b> 빈 문자열 (현재 시간 사용)
     */
    String time();
    
    /**
     * 반복 주기
     * <p>
     * <b>형식:</b><br>
     * - M: 월 단위 (startDay 기준)<br>
     * - 숫자+w: 주 단위 (예: 1w, 2w)<br>
     * - 숫자+d: 일 단위 (예: 1d, 7d)<br>
     * - 숫자+h: 시간 단위 (예: 2h, 6h)<br>
     * - 숫자+m: 분 단위 (예: 30m, 45m)
     * - 숫자+s: 초 단위 (예: 3s, 30s - 1~59초만 허용)
     * <p>
     * <b>예시:</b> "M" (매월), "2w" (2주), "1d" (1일), "2h" (2시간), "30m" (30분), "5s" (5초)
     */
    String period();
    
    
    /**
     * 스케줄링 타입
     * <p>
     * <b>RATE:</b> 작업 시작 시점 기준 고정 간격 (scheduleAtFixedRate)<br>
     * <b>DELAY:</b> 작업 완료 시점 기준 고정 지연 (scheduleWithFixedDelay) - 기본값
     * <p>
     * <b>기본값:</b> DELAY<br>
     * DELAY는 작업 완료 후 지정된 시간만큼 대기 후 다음 작업 실행<br>
     * RATE는 작업 시작 시간을 기준으로 일정 간격 유지
     */
    ScheduleType type() default ScheduleType.DELAY;
    
    /**
     * 실행할 요일 지정
     * <p>
     * <b>설정:</b><br>
     * - 빈 배열: 매일 실행<br>
     * - 요일 지정: 해당 요일만 실행<br>
     * - 월 단위(M) 주기에서는 무시됨
     * <p>
     * <b>예시:</b> {DayOfWeek.MONDAY, DayOfWeek.FRIDAY}
     */
    java.time.DayOfWeek[] daysOfWeek() default {};
    
    /**
     * Task 시작 일자 (yyyyMMdd 형식)
     * <p>
     * <b>동작:</b><br>
     * - 이 날짜부터 스케줄 시작<br>
     * - 빈 문자열이면 즉시 시작<br>
     * - 월 단위(M) 주기는 필수
     * <p>
     * <b>형식:</b> "yyyyMMdd"<br>
     * <b>예시:</b> "20250101", "20251231"
     */
    String startDay() default "";
    
    /**
     * Task 종료 일자 (yyyyMMdd 형식)
     * <p>
     * <b>동작:</b><br>
     * - 이 날짜 이후로는 실행 안 됨<br>
     * - 빈 문자열이면 종료 없음
     * <p>
     * <b>형식:</b> "yyyyMMdd"<br>
     * <b>예시:</b> "20251231"
     */
    String endDay() default "";
    
    /**
     * 활성화 여부
     * <p>
     * <b>기본값:</b> true<br>
     * false로 설정 시 Task가 스케줄되지 않음
     */
    boolean enabled() default true;
    
    /**
     * Task 설명
     * <p>
     * Task의 목적이나 동작에 대한 설명<br>
     * 로그에 출력되어 Task 식별에 도움
     */
    String desc() default "";
    
    /**
     * 우선순위 (높을수록 먼저 스케줄)
     * <p>
     * <b>기본값:</b> 50<br>
     * <b>범위:</b> 0 ~ 100 권장<br>
     * 값이 클수록 우선적으로 스케줄링됨
     */
    int priority() default 50;
}
