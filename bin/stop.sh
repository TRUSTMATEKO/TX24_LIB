#!/bin/bash
#==============================================================================
# Application Stop Script
#==============================================================================

#==============================================================================
# 설정 변수
#==============================================================================
PROC_NAME="TX24_NAVERWORKS"
BIN_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_FILE="${BIN_DIR}/${PROC_NAME}.pid"

# Shutdown 설정
GRACEFUL_TIMEOUT=30  # graceful shutdown 대기 시간 (초)
FORCE_SHUTDOWN=false

#==============================================================================
# 사용법 출력
#==============================================================================
usage() {
    cat << EOF
Usage: $0 [OPTIONS]

Options:
    -f, --force         Force shutdown (SIGKILL)
    -t, --timeout SEC   Graceful shutdown timeout (default: ${GRACEFUL_TIMEOUT}s)
    -h, --help          Show this help message

Description:
    기본적으로 graceful shutdown (SIGTERM)을 시도하고,
    타임아웃 후에도 종료되지 않으면 강제 종료합니다.
    
Examples:
    $0                  # Graceful shutdown with 30s timeout
    $0 -f               # Force shutdown immediately
    $0 -t 60            # Graceful shutdown with 60s timeout

EOF
    exit 0
}

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
# Graceful Shutdown
#==============================================================================
graceful_shutdown() {
    local pid=$1
    local timeout=$2
    
    echo "Attempting graceful shutdown of $PROC_NAME (PID: $pid)..."
    echo "Sending SIGTERM signal..."
    
    # SIGTERM 전송
    kill -15 "$pid" 2>/dev/null
    
    if [ $? -ne 0 ]; then
        echo "Error: Failed to send SIGTERM signal"
        return 1
    fi
    
    # 프로세스 종료 대기
    local elapsed=0
    local check_interval=1
    
    while [ $elapsed -lt $timeout ]; do
        if ! is_running "$pid"; then
            echo "$PROC_NAME stopped gracefully (${elapsed}s)"
            rm -f "$PID_FILE" 2>/dev/null
            return 0
        fi
        
        sleep $check_interval
        elapsed=$((elapsed + check_interval))
        
        # 진행 상황 표시 (5초마다)
        if [ $((elapsed % 5)) -eq 0 ]; then
            echo "Waiting for graceful shutdown... (${elapsed}/${timeout}s)"
        fi
    done
    
    # 타임아웃
    echo "Graceful shutdown timeout (${timeout}s)"
    return 1
}

#==============================================================================
# Force Shutdown
#==============================================================================
force_shutdown() {
    local pid=$1
    
    echo "Force stopping $PROC_NAME (PID: $pid)..."
    echo "Sending SIGKILL signal..."
    
    # SIGKILL 전송
    kill -9 "$pid" 2>/dev/null
    
    if [ $? -ne 0 ]; then
        echo "Error: Failed to send SIGKILL signal"
        return 1
    fi
    
    sleep 1
    
    if ! is_running "$pid"; then
        echo "$PROC_NAME force stopped"
        rm -f "$PID_FILE" 2>/dev/null
        return 0
    else
        echo "Error: Failed to stop process"
        return 1
    fi
}

#==============================================================================
# 명령행 인자 파싱
#==============================================================================
parse_arguments() {
    while [ $# -gt 0 ]; do
        case "$1" in
            -f|--force)
                FORCE_SHUTDOWN=true
                shift
                ;;
            -t|--timeout)
                if [ -n "$2" ] && [ "$2" -eq "$2" ] 2>/dev/null; then
                    GRACEFUL_TIMEOUT=$2
                    shift 2
                else
                    echo "Error: Invalid timeout value: $2"
                    exit 1
                fi
                ;;
            -h|--help)
                usage
                ;;
            *)
                echo "Error: Unknown option: $1"
                echo "Use -h or --help for usage information"
                exit 1
                ;;
        esac
    done
}

#==============================================================================
# Main
#==============================================================================
main() {
    # 명령행 인자 파싱
    parse_arguments "$@"
    
    echo ""
    echo "Stop $PROC_NAME"
    echo ""
    
    # PID 파일 확인
    if [ ! -f "$PID_FILE" ]; then
        echo "Error: PID file not found: $PID_FILE"
        
        # 실행 중인 프로세스 찾기
        local running_proc
        running_proc=$(ps -ef | grep java | grep "$PROC_NAME" | grep -v grep)
        if [ -n "$running_proc" ]; then
            echo ""
            echo "Found running process:"
            echo "$running_proc"
            echo ""
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
    
    echo "Process ID: $pid"
    echo "Shutdown Mode: $([ "$FORCE_SHUTDOWN" = true ] && echo "FORCE" || echo "GRACEFUL (timeout: ${GRACEFUL_TIMEOUT}s)")"
    echo ""
    
    # Shutdown 실행
    if [ "$FORCE_SHUTDOWN" = true ]; then
        # 강제 종료
        force_shutdown "$pid"
        exit_code=$?
    else
        # Graceful shutdown 시도
        graceful_shutdown "$pid" "$GRACEFUL_TIMEOUT"
        exit_code=$?
        
        # Graceful shutdown 실패 시 강제 종료
        if [ $exit_code -ne 0 ]; then
            echo ""
            echo "Falling back to force shutdown..."
            force_shutdown "$pid"
            exit_code=$?
        fi
    fi
    
    echo "" 
    exit $exit_code
}

main "$@"