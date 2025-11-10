#!/bin/bash
#==============================================================================
# Application Stop Script
#==============================================================================

#==============================================================================
# 설정 변수
#==============================================================================
PROC_NAME="TX24_LIB"
BIN_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_FILE="${BIN_DIR}/${PROC_NAME}.pid"

#==============================================================================
# 프로세스 확인
#==============================================================================
is_running() {
    local pid=$1
    if [ -n "$pid" ] && ps -p "$pid" > /dev/null 2>&1; then
        return 0
    else
        return 1
    fi
}

#==============================================================================
# Main
#==============================================================================
main() {
    # PID 파일 확인
    if [ ! -f "$PID_FILE" ]; then
        echo "Error: PID file not found: $PID_FILE"
        
        # 실행 중인 프로세스 찾기
        local running_proc
        running_proc=$(ps -ef | grep java | grep "$PROC_NAME" | grep -v grep)
        if [ -n "$running_proc" ]; then
            echo "Found running process:"
            echo "$running_proc"
            echo "Please check and stop manually"
        else
            echo "$PROC_NAME is not running"
        fi
        exit 1
    fi
    
    # PID 가져오기
    local pid
    pid=$(cat "$PID_FILE" 2>/dev/null)
    
    if [ -z "$pid" ]; then
        echo "Error: Invalid PID file"
        rm -f "$PID_FILE" 2>/dev/null
        exit 1
    fi
    
    # 프로세스 실행 확인
    if ! is_running "$pid"; then
        echo "$PROC_NAME is not running (stale PID: $pid)"
        rm -f "$PID_FILE" 2>/dev/null
        exit 0
    fi
    
    # 강제 종료
    echo "Stopping $PROC_NAME (PID: $pid)..."
    echo "Force killing process..."
    
    kill -9 "$pid" 2>/dev/null
    
    sleep 1
    
    if ! is_running "$pid"; then
        rm -f "$PID_FILE" 2>/dev/null
        echo "$PROC_NAME stopped"
        exit 0
    else
        echo "Error: Failed to stop process"
        exit 1
    fi
}

main "$@"