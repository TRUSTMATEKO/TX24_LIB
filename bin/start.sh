#!/bin/bash
#==============================================================================
# Application Start Script
#==============================================================================

# errexit - 에러 발생 시 스크립트 즉시 종료
set -e

#==============================================================================
# 설정 변수
#==============================================================================
# 프로세스 설정
PROC_NAME="TX24_NAVERWORKS"
MAIN_CLASS="kr.tx24.inet.server.INetServer"

# 디렉토리 설정 (bin 기준)
BASE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BIN_DIR="${BASE_DIR}/bin"
CONF_DIR="${BASE_DIR}/conf"
LOG_DIR="${BASE_DIR}/logs"
LIB_DIR="${BASE_DIR}/lib"
CLASSES_DIR="${BASE_DIR}/classes"
DIST_DIR="${BASE_DIR}/dist"

# Fat JAR 설정 (기본 Production 모드)
FAT_JAR_NAME="TX24_NAVERWORKS.jar"
FAT_JAR="${DIST_DIR}/${FAT_JAR_NAME}"

# PID 파일
PID_FILE="${BIN_DIR}/${PROC_NAME}.pid"

#==============================================================================
# JVM 옵션 변수 (필요시 주석 해제)
#==============================================================================
JVM_OPTS_SERVER="-server"
JVM_OPTS_ENCODING="-Dfile.encoding=UTF-8"
JVM_OPTS_SECURITY="-Djava.security.egd=file:/dev/./urandom"
JVM_OPTS_NETWORK="-Djava.net.preferIPv4Stack=true"
JVM_OPTS_STACK="-Xss1m"
JVM_OPTS_HEAP_MIN="-Xms128m"
JVM_OPTS_HEAP_MAX="-Xmx256m"
JVM_OPTS_META_MIN="-XX:MetaspaceSize=96M"
JVM_OPTS_META_MAX="-XX:MaxMetaspaceSize=256m"

# 추가 JVM 옵션 (필요시 주석 해제)
# JVM_OPTS_GC="-XX:+UseG1GC"
# JVM_OPTS_GC_LOG="-Xloggc:${LOG_DIR}/gc.log"
# JVM_OPTS_DUMP="-XX:+HeapDumpOnOutOfMemoryError"

#==============================================================================
# 프로그램 옵션 - 필수 설정
#==============================================================================
REDIS_HOST="127.0.0.1:6379/0"
LOG_LEVEL="INFO"
LOG_MAXDAY="90"
LOGGER="false,true,true"        # console,file,remote
JVM_MONITOR="false"             # JVM Monitoring

#==============================================================================
# 프로그램 옵션 - 선택 설정 (필요시 주석 해제)
#==============================================================================
# REDIS1_HOST="DEVDBM1:6379/0"
# REDIS2_HOST="DEVDBM2:6379/0"
LOG_REDIS_HOST="127.0.0.1:6379/0"
# LOG_REDIS1_HOST="DEVDBM:6379/0"
# LOG_REDIS2_HOST="DEVDBM:6379/0"
NLB_CONFIG="${CONF_DIR}/nlb.json"
# DB_CONFIG="${CONF_DIR}/db.json"

#EXECUTOR_THRESHOLD_WARN="true"
#EXECUTOR_THRESHOLD_MILLIES="5000"

#==============================================================================
# Java 설정
#==============================================================================
if [ -z "$JAVA" ]; then
    if [ -n "$JAVA_HOME" ]; then
        JAVA="$JAVA_HOME/bin/java"
    else
        JAVA="java"
    fi
fi

#==============================================================================
# JVM 옵션 구성 (변수가 정의되어 있을 때만 추가)
#==============================================================================
JVM_OPTS=""
[ -n "$JVM_OPTS_SERVER" ] && JVM_OPTS="${JVM_OPTS} ${JVM_OPTS_SERVER}"
[ -n "$JVM_OPTS_ENCODING" ] && JVM_OPTS="${JVM_OPTS} ${JVM_OPTS_ENCODING}"
[ -n "$JVM_OPTS_SECURITY" ] && JVM_OPTS="${JVM_OPTS} ${JVM_OPTS_SECURITY}"
[ -n "$JVM_OPTS_NETWORK" ] && JVM_OPTS="${JVM_OPTS} ${JVM_OPTS_NETWORK}"
[ -n "$JVM_OPTS_STACK" ] && JVM_OPTS="${JVM_OPTS} ${JVM_OPTS_STACK}"
[ -n "$JVM_OPTS_HEAP_MIN" ] && JVM_OPTS="${JVM_OPTS} ${JVM_OPTS_HEAP_MIN}"
[ -n "$JVM_OPTS_HEAP_MAX" ] && JVM_OPTS="${JVM_OPTS} ${JVM_OPTS_HEAP_MAX}"
[ -n "$JVM_OPTS_META_MIN" ] && JVM_OPTS="${JVM_OPTS} ${JVM_OPTS_META_MIN}"
[ -n "$JVM_OPTS_META_MAX" ] && JVM_OPTS="${JVM_OPTS} ${JVM_OPTS_META_MAX}"
[ -n "$JVM_OPTS_GC" ] && JVM_OPTS="${JVM_OPTS} ${JVM_OPTS_GC}"
[ -n "$JVM_OPTS_GC_LOG" ] && JVM_OPTS="${JVM_OPTS} ${JVM_OPTS_GC_LOG}"
[ -n "$JVM_OPTS_DUMP" ] && JVM_OPTS="${JVM_OPTS} ${JVM_OPTS_DUMP}"

#==============================================================================
# 프로그램 옵션 구성 (변수가 정의되어 있을 때만 추가)
#==============================================================================
PRG_OPTS=""

# 필수 옵션
[ -n "$PROC_NAME" ] && PRG_OPTS="${PRG_OPTS} -DPROC=${PROC_NAME}"
[ -n "$CONF_DIR" ] && PRG_OPTS="${PRG_OPTS} -DCONF=${CONF_DIR}"
[ -n "$LOG_DIR" ] && PRG_OPTS="${PRG_OPTS} -DLOG_DIR=${LOG_DIR}"
[ -n "$LOG_LEVEL" ] && PRG_OPTS="${PRG_OPTS} -DLOG_LEVEL=${LOG_LEVEL}"
[ -n "$LOG_MAXDAY" ] && PRG_OPTS="${PRG_OPTS} -DLOG_MAXDAY=${LOG_MAXDAY}"
[ -n "$LOGGER" ] && PRG_OPTS="${PRG_OPTS} -DLOGGER=${LOGGER}"
[ -n "$JVM_MONITOR" ] && PRG_OPTS="${PRG_OPTS} -DJVM_MONITOR=${JVM_MONITOR}"

# Redis 옵션
[ -n "$REDIS_HOST" ] && PRG_OPTS="${PRG_OPTS} -DREDIS=${REDIS_HOST}"
[ -n "$REDIS1_HOST" ] && PRG_OPTS="${PRG_OPTS} -DREDIS1=${REDIS1_HOST}"
[ -n "$REDIS2_HOST" ] && PRG_OPTS="${PRG_OPTS} -DREDIS2=${REDIS2_HOST}"

# Log Redis 옵션
[ -n "$LOG_REDIS_HOST" ] && PRG_OPTS="${PRG_OPTS} -DLOG_REDIS=${LOG_REDIS_HOST}"
[ -n "$LOG_REDIS1_HOST" ] && PRG_OPTS="${PRG_OPTS} -DLOG_REDIS1=${LOG_REDIS1_HOST}"
[ -n "$LOG_REDIS2_HOST" ] && PRG_OPTS="${PRG_OPTS} -DLOG_REDIS2=${LOG_REDIS2_HOST}"

# 설정 파일 옵션
[ -n "$NLB_CONFIG" ] && PRG_OPTS="${PRG_OPTS} -DNLB=${NLB_CONFIG}"
[ -n "$DB_CONFIG" ] && PRG_OPTS="${PRG_OPTS} -DDBSET=${DB_CONFIG}"

# Async Executor 옵션
[ -n "$EXECUTOR_THRESHOLD_WARN" ] && PRG_OPTS="${PRG_OPTS} -Dasync.threshold.warn=${EXECUTOR_THRESHOLD_WARN}"
[ -n "$EXECUTOR_THRESHOLD_MILLIES" ] && PRG_OPTS="${PRG_OPTS} -Dasync.threshold.millies=${EXECUTOR_THRESHOLD_MILLIES}"

#==============================================================================
# 실행 모드 감지
#==============================================================================
detect_run_mode() {
    # 기본은 production 모드
    # Fat JAR 이름이 지정되어 있고 파일이 존재하면 production
    if [ -n "$FAT_JAR_NAME" ] && [ -f "$FAT_JAR" ]; then
        echo "production"
    else
        echo "development"
    fi
}

#==============================================================================
# 옵션 출력 함수
#==============================================================================
print_options() {
    local run_mode
    run_mode=$(detect_run_mode)
    
    echo ""
    echo "TX24 NAVER WORKS Application"
    echo "Run Mode: ${run_mode}"
    
    if [ "$run_mode" = "production" ]; then
        echo "Executable: ${FAT_JAR}"
    else
        echo "Main Class: ${MAIN_CLASS}"
        echo "Classpath: ${LIB_DIR}/*:${CLASSES_DIR}"
    fi
    
    echo ""
    echo "JVM Options:"
    if [ -n "$JVM_OPTS" ]; then
        # 공백을 기준으로 라인별 출력
        echo "$JVM_OPTS" | tr ' ' '\n' | grep -v '^$' | while read -r opt; do
            echo "  $opt"
        done
    else
        echo "  (none)"
    fi
    
    echo ""
    echo "Program Options:"
    if [ -n "$PRG_OPTS" ]; then
        # 공백을 기준으로 라인별 출력
        echo "$PRG_OPTS" | tr ' ' '\n' | grep -v '^$' | while read -r opt; do
            echo "  $opt"
        done
    else
        echo "  (none)"
    fi
    echo ""
}

#==============================================================================
# 프로세스 확인
#==============================================================================
check_process() {
    # PID 파일 확인
    if [ -f "$PID_FILE" ]; then
        local old_pid
        old_pid=$(cat "$PID_FILE" 2>/dev/null)
        if [ -n "$old_pid" ] && [ "$old_pid" -gt 0 ]; then
            if ps -p "$old_pid" > /dev/null 2>&1; then
                echo "Error: $PROC_NAME is already running (PID: $old_pid)"
                exit 1
            else
                rm -f "$PID_FILE"
            fi
        fi
    fi
    
    # 실행 중인 프로세스 확인
    local running_proc_count
    running_proc_count=$(ps -ef | grep "[j]ava" | grep -c "$PROC_NAME" || true)
    if [ "$running_proc_count" -gt 0 ]; then
        echo "Error: Process is already running"
        ps -ef | grep "[j]ava" | grep "$PROC_NAME"
        exit 1
    fi
}

#==============================================================================
# 애플리케이션 시작
#==============================================================================
start_application() {
    local run_mode
    run_mode=$(detect_run_mode)
    
    echo "Starting $PROC_NAME in ${run_mode} mode..."
    echo ""
    
    cd "$BIN_DIR" || exit 1
    
    # Production Mode: Fat JAR 실행 (기본)
    if [ "$run_mode" = "production" ]; then
        echo "Executing: java -jar ${FAT_JAR}"
        $JAVA $PRG_OPTS $JVM_OPTS -jar "$FAT_JAR" &
        local pid=$!
    # Development Mode: Classpath 실행 (Fallback)
    else
        echo "Warning: Fat JAR not found (${FAT_JAR})"
        echo "Fallback to development mode"
        echo "To build Fat JAR, run: ant build -Dproduction=true"
        echo ""
        
        # Classpath 구성
        CLASSPATH="${LIB_DIR}/*:${CLASSES_DIR}"
        
        echo "Executing: java -cp ${CLASSPATH} ${MAIN_CLASS}"
        $JAVA $PRG_OPTS $JVM_OPTS -cp "$CLASSPATH" $MAIN_CLASS &
        local pid=$!
    fi
    
    echo "$pid" > "$PID_FILE"
    
    sleep 2
    if ps -p "$pid" > /dev/null 2>&1; then
        echo ""
        echo "Started successfully (PID: $pid)"
        echo ""
        return 0
    else
        echo ""
        echo "Failed to start"
        rm -f "$PID_FILE"
        return 1
    fi
}

#==============================================================================
# Main
#==============================================================================
main() {
    print_options
    
    check_process
    
    if ! start_application; then
        exit 1
    fi
    
    # 로그 확인 옵션
    if [ "$1" = "log" ]; then
        sleep 1
        if [ -f "${LOG_DIR}/root.txt" ]; then
            tail -f "${LOG_DIR}/root.txt"
        else
            echo "Warning: Log file not found: ${LOG_DIR}/root.txt"
        fi
    fi
}

main "$@"