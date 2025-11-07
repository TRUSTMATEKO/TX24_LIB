package kr.tx24.task.config;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

import kr.tx24.task.annotation.Task.ScheduleType;

public record TaskConfig(
	    String name,
	    Class<? extends Runnable> taskClass,
	    LocalTime scheduledTime,
	    Duration period,
	    ScheduleType type,
	    Set<DayOfWeek> daysOfWeek,
	    LocalDate startDate,
	    LocalDate endDate,
	    boolean enabled,
	    String description,
	    int priority
	) implements Comparable<TaskConfig> {
	    
	    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
	    
	    /**
	     * 월 단위 주기 여부 확인
	     * period.toDays() == -1 이면 월 단위
	     */
	    public boolean isMonthlyPeriod() {
	        return period.toDays() == -1;
	    }
	    
	    public String getScheduledTimeString() {
	        return scheduledTime.toString();
	    }
	    
	    public String getPeriodString() {
	        if (isMonthlyPeriod()) {
	            return "M (Monthly)";
	        }
	        
	        long weeks = period.toDays() / 7;
	        long days = period.toDays();
	        long hours = period.toHours();
	        long minutes = period.toMinutes();
	        long seconds = period.toSeconds();
	        
	        if (weeks > 0 && period.toDays() % 7 == 0) {
	            return weeks + "w (Week)";
	        } else if (days > 0) {
	            return days + "d (Day)";
	        } else if (hours > 0) {
	            return hours + "h (Hour)";
	        } else if (minutes > 0) {
	            return minutes + "m (Minute)";
	        } else {
	            return seconds + "s (Second)";
	        }
	    }
	    
	    public String getScheduleTypeString() {
	        return type == ScheduleType.RATE ? "Fixed Rate" : "Fixed Delay";
	    }
	    
	    public String getDaysOfWeekString() {
	        if (daysOfWeek.isEmpty()) {
	            return "Every day";
	        }
	        return daysOfWeek.stream()
	            .sorted()
	            .map(day -> {
	                String eng = day.toString().substring(0, 3);
	                String kor = getKoreanDayOfWeek(day);
	                return eng + "/" + kor;
	            })
	            .reduce((a, b) -> a + ", " + b)
	            .orElse("Every day");
	    }
	    
	    private String getKoreanDayOfWeek(DayOfWeek day) {
	        return switch (day) {
	            case MONDAY -> "월";
	            case TUESDAY -> "화";
	            case WEDNESDAY -> "수";
	            case THURSDAY -> "목";
	            case FRIDAY -> "금";
	            case SATURDAY -> "토";
	            case SUNDAY -> "일";
	        };
	    }
	    
	    public String getStartDateString() {
	        return startDate != null ? startDate.format(DATE_FORMATTER) : "N/A";
	    }
	    
	    public String getEndDateString() {
	        return endDate != null ? endDate.format(DATE_FORMATTER) : "N/A";
	    }
	    
	    public boolean isValidDate(LocalDate date) {
	        if (startDate != null && date.isBefore(startDate)) {
	            return false;
	        }
	        if (endDate != null && date.isAfter(endDate)) {
	            return false;
	        }
	        return true;
	    }
	    
	    public boolean isValidDayOfWeek(DayOfWeek dayOfWeek) {
	        // 월 단위 주기는 요일 체크 안 함
	        if (isMonthlyPeriod()) {
	            return true;
	        }
	        
	        if (daysOfWeek.isEmpty()) {
	            return true;
	        }
	        return daysOfWeek.contains(dayOfWeek);
	    }
	    
	    /**
	     * 다음 실행 날짜 계산 (월 단위)
	     * startDate 기준으로 매월 같은 일자
	     */
	    public LocalDate getNextMonthlyDate(LocalDate current) {
	        if (!isMonthlyPeriod() || startDate == null) {
	            return null;
	        }
	        
	        int targetDay = startDate.getDayOfMonth();
	        LocalDate next = current.plusMonths(1).withDayOfMonth(1);
	        
	        // 목표 일자가 해당 월의 마지막 날보다 크면 마지막 날로 조정
	        int lastDayOfMonth = next.lengthOfMonth();
	        int actualDay = Math.min(targetDay, lastDayOfMonth);
	        
	        return next.withDayOfMonth(actualDay);
	    }
	    
	    @Override
	    public int compareTo(TaskConfig other) {
	        // priority가 높을수록 먼저 (내림차순)
	        return Integer.compare(other.priority, this.priority);
	    }
	}
