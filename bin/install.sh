#!/bin/bash
#==============================================================================
# TX24_LIB systemd Service 자동 설치 스크립트
#==============================================================================

set -e

#==============================================================================
# 색상 정의
#==============================================================================
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

#==============================================================================
# 설정 변수 (필요시 수정)
#==============================================================================
SERVICE_NAME="tx24lib"
SERVICE_USER="syslink"
SERVICE_GROUP="syslink"
INSTALL_DIR="/home/syslink/TX24_LIB"
JAVA_HOME="/usr/lib/jvm/java-11"

#==============================================================================
# 함수 정의
#==============================================================================
print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_info() {
    echo -e "${YELLOW}ℹ $1${NC}"
}

check_root() {
    if [ "$EUID" -ne 0 ]; then 
        print_error "이 스크립트는 root 권한이 필요합니다."
        echo "sudo로 다시 실행해주세요: sudo $0"
        exit 1
    fi
}

check_user() {
    if ! id "$SERVICE_USER" &>/dev/null; then
        print_warning "사용자 $SERVICE_USER가 존재하지 않습니다."
        read -p "사용자를 생성하시겠습니까? (y/n): " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            useradd -m -s /bin/bash "$SERVICE_USER"
            print_success "사용자 $SERVICE_USER 생성 완료"
        else
            print_error "사용자가 필요합니다. 종료합니다."
            exit 1
        fi
    else
        print_success "사용자 $SERVICE_USER 확인 완료"
    fi
}

check_install_dir() {
    if [ ! -d "$INSTALL_DIR" ]; then
        print_error "설치 디렉토리를 찾을 수 없습니다: $INSTALL_DIR"
        read -p "설치 디렉토리 경로를 입력하세요: " INSTALL_DIR
        if [ ! -d "$INSTALL_DIR" ]; then
            print_error "디렉토리가 존재하지 않습니다. 종료합니다."
            exit 1
        fi
    fi
    print_success "설치 디렉토리 확인: $INSTALL_DIR"
}

check_scripts() {
    local missing=0
    
    if [ ! -f "$INSTALL_DIR/bin/start.sh" ]; then
        print_error "start.sh를 찾을 수 없습니다."
        missing=1
    fi
    
    if [ ! -f "$INSTALL_DIR/bin/stop.sh" ]; then
        print_error "stop.sh를 찾을 수 없습니다."
        missing=1
    fi
    
    if [ ! -f "$INSTALL_DIR/bin/restart.sh" ]; then
        print_error "restart.sh를 찾을 수 없습니다."
        missing=1
    fi
    
    if [ $missing -eq 1 ]; then
        exit 1
    fi
    
    print_success "스크립트 파일 확인 완료"
}

set_permissions() {
    print_info "권한 설정 중..."
    
    # 디렉토리 소유권
    chown -R "$SERVICE_USER:$SERVICE_GROUP" "$INSTALL_DIR"
    
    # 스크립트 실행 권한
    chmod +x "$INSTALL_DIR/bin/start.sh"
    chmod +x "$INSTALL_DIR/bin/stop.sh"
    chmod +x "$INSTALL_DIR/bin/restart.sh"
    
    print_success "권한 설정 완료"
}

check_java() {
    if [ ! -d "$JAVA_HOME" ]; then
        print_warning "JAVA_HOME을 찾을 수 없습니다: $JAVA_HOME"
        
        # Java 경로 찾기 시도
        local java_paths=(
            "/usr/lib/jvm/java-11"
            "/usr/lib/jvm/java-11-openjdk-amd64"
            "/usr/lib/jvm/java-17"
            "/usr/lib/jvm/java-17-openjdk-amd64"
            "/usr/lib/jvm/default-java"
        )
        
        local found=0
        for path in "${java_paths[@]}"; do
            if [ -d "$path" ]; then
                JAVA_HOME="$path"
                print_success "Java 경로 발견: $JAVA_HOME"
                found=1
                break
            fi
        done
        
        if [ $found -eq 0 ]; then
            read -p "JAVA_HOME 경로를 입력하세요: " JAVA_HOME
            if [ ! -d "$JAVA_HOME" ]; then
                print_error "유효하지 않은 경로입니다."
                exit 1
            fi
        fi
    else
        print_success "Java 경로 확인: $JAVA_HOME"
    fi
}

create_service_file() {
    print_info "Service 파일 생성 중..."
    
    local service_file="/etc/systemd/system/${SERVICE_NAME}.service"
    
    cat > "$service_file" <<EOF
[Unit]
Description=TX24_LIB Application Service
After=network.target

[Service]
Type=forking
User=$SERVICE_USER
Group=$SERVICE_GROUP

# 작업 디렉토리
WorkingDirectory=$INSTALL_DIR/bin

# 환경 변수
Environment="JAVA_HOME=$JAVA_HOME"
Environment="PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

# PID 파일
PIDFile=$INSTALL_DIR/bin/TX24_LIB.pid

# 시작 명령
ExecStart=$INSTALL_DIR/bin/start.sh

# 중지 명령
ExecStop=$INSTALL_DIR/bin/stop.sh

# 재시작 명령
ExecReload=$INSTALL_DIR/bin/restart.sh

# 재시작 정책
Restart=on-failure
RestartSec=10

# 타임아웃
TimeoutStartSec=30
TimeoutStopSec=30

[Install]
WantedBy=multi-user.target
EOF

    chmod 644 "$service_file"
    print_success "Service 파일 생성: $service_file"
}

register_service() {
    print_info "Service 등록 중..."
    
    # systemd reload
    systemctl daemon-reload
    print_success "systemd daemon reload 완료"
    
    # Service 활성화
    systemctl enable "$SERVICE_NAME"
    print_success "Service 활성화 완료 (부팅 시 자동 시작)"
}

print_summary() {
    echo ""
    echo "=========================================="
    echo "  TX24_LIB Service 설치 완료!"
    echo "=========================================="
    echo ""
    echo "Service 이름: $SERVICE_NAME"
    echo "사용자: $SERVICE_USER"
    echo "설치 경로: $INSTALL_DIR"
    echo "Java 경로: $JAVA_HOME"
    echo ""
    echo "사용 가능한 명령어:"
    echo "  시작:    sudo systemctl start $SERVICE_NAME"
    echo "  중지:    sudo systemctl stop $SERVICE_NAME"
    echo "  재시작:  sudo systemctl restart $SERVICE_NAME"
    echo "  상태:    sudo systemctl status $SERVICE_NAME"
    echo "  로그:    sudo journalctl -u $SERVICE_NAME -f"
    echo ""
    echo "자동 시작: 활성화됨 (부팅 시 자동 시작)"
    echo ""
}

ask_start_service() {
    read -p "지금 서비스를 시작하시겠습니까? (y/n): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        systemctl start "$SERVICE_NAME"
        sleep 3
        systemctl status "$SERVICE_NAME" --no-pager
    fi
}

#==============================================================================
# Main
#==============================================================================
main() {
    echo "=========================================="
    echo "  TX24_LIB systemd Service 설치"
    echo "=========================================="
    echo ""
    
    # 설정 확인
    print_info "설정 정보:"
    echo "  Service 이름: $SERVICE_NAME"
    echo "  사용자: $SERVICE_USER"
    echo "  설치 디렉토리: $INSTALL_DIR"
    echo "  Java 경로: $JAVA_HOME"
    echo ""
    
    read -p "계속 진행하시겠습니까? (y/n): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        print_warning "설치가 취소되었습니다."
        exit 0
    fi
    
    echo ""
    print_info "설치를 시작합니다..."
    echo ""
    
    # 단계별 실행
    check_root
    check_user
    check_install_dir
    check_scripts
    check_java
    set_permissions
    create_service_file
    register_service
    
    echo ""
    print_summary
    ask_start_service
    
    echo ""
    print_success "모든 작업이 완료되었습니다!"
}

main "$@"