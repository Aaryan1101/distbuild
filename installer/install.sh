#!/bin/bash
set -euo pipefail

# distbuild installer for Linux/macOS
# Usage: curl -fsSL https://raw.githubusercontent.com/Aaryan1101/distbuild/main/installer/install.sh | bash

DISTBUILD_VERSION="1.1.4"
INSTALL_DIR="${INSTALL_DIR:-/opt/distbuild}"
SERVICE_USER="${SERVICE_USER:-$USER}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if running as root
check_root() {
    if [[ $EUID -eq 0 ]]; then
        log_error "Do not run this installer as root. Run as a regular user and the installer will ask for sudo when needed."
        exit 1
    fi
}

# Check system requirements
check_requirements() {
    log_info "Checking system requirements..."
    
    # Check if curl is available
    if ! command -v curl &> /dev/null; then
        log_error "curl is required but not installed. Please install curl and try again."
        exit 1
    fi
    
    # Check OS
    if [[ "$OSTYPE" == "linux-gnu"* ]]; then
        OS="linux"
    elif [[ "$OSTYPE" == "darwin"* ]]; then
        OS="macos"
    else
        log_warning "Unsupported OS: $OSTYPE. This installer supports Linux and macOS."
    fi
    
    log_success "System requirements check passed"
}

# Check Java installation
check_java() {
    log_info "Checking Java installation..."
    
    if command -v java &> /dev/null; then
        JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
        if [[ "$JAVA_VERSION" -ge 17 ]]; then
            log_success "Java $JAVA_VERSION found (>= 17 required)"
            return 0
        else
            log_warning "Java $JAVA_VERSION found but version 17+ is recommended"
        fi
    fi
    
    log_warning "Java 17+ not found. Installing OpenJDK 17..."
    
    if command -v apt-get &> /dev/null; then
        # Ubuntu/Debian
        if sudo apt-get update && sudo apt-get install -y openjdk-17-jdk; then
            log_success "OpenJDK 17 installed successfully"
        else
            log_error "Failed to install OpenJDK 17"
            return 1
        fi
    elif command -v yum &> /dev/null; then
        # CentOS/RHEL/Fedora
        if sudo yum install -y java-17-openjdk-devel; then
            log_success "OpenJDK 17 installed successfully"
        else
            log_error "Failed to install OpenJDK 17"
            return 1
        fi
    elif command -v brew &> /dev/null; then
        # macOS
        if brew install openjdk@17; then
            log_success "OpenJDK 17 installed successfully"
        else
            log_error "Failed to install OpenJDK 17"
            return 1
        fi
    else
        log_error "Please install Java 17+ manually and try again"
        return 1
    fi
}

# Check Redis (optional but recommended)
check_redis() {
    log_info "Checking Redis installation..."
    
    if command -v redis-server &> /dev/null; then
        log_success "Redis found"
        return 0
    fi
    
    log_warning "Redis not found. Redis is recommended for optimal performance."
    read -p "Would you like to install Redis? (y/N): " -n 1 -r
    echo
    
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        if command -v apt-get &> /dev/null; then
            sudo apt-get update && sudo apt-get install -y redis-server
            log_success "Redis installed successfully"
        elif command -v yum &> /dev/null; then
            sudo yum install -y redis
            log_success "Redis installed successfully"
        elif command -v brew &> /dev/null; then
            brew install redis
            log_success "Redis installed successfully"
        else
            log_warning "Please install Redis manually for optimal performance"
        fi
    fi
}

# Download distbuild JAR
download_distbuild() {
    log_info "Downloading distbuild ${DISTBUILD_VERSION}..."
    
    JAR_URL="https://github.com/Aaryan1101/distbuild/releases/download/v${DISTBUILD_VERSION}/distbuild-${DISTBUILD_VERSION}.jar"
    
    # Create install directory
    sudo mkdir -p "$INSTALL_DIR"
    
    # Download JAR
    if curl -fsSL "$JAR_URL" -o "${INSTALL_DIR}/distbuild.jar"; then
        log_success "distbuild ${DISTBUILD_VERSION} downloaded successfully"
    else
        log_error "Failed to download distbuild JAR"
        exit 1
    fi
    
    # Set permissions
    sudo chown "$SERVICE_USER:$SERVICE_USER" "$INSTALL_DIR/distbuild.jar"
    sudo chmod 755 "$INSTALL_DIR/distbuild.jar"
}

# Create launcher script
create_launcher() {
    log_info "Creating distbuild launcher..."
    
    sudo mkdir -p "$INSTALL_DIR/bin"
    
    cat << EOF | sudo tee "$INSTALL_DIR/bin/distbuild" > /dev/null
#!/bin/bash
exec java \${DISTBUILD_JVM_OPTS:--Xmx512m} -jar "$INSTALL_DIR/distbuild.jar" "\$@"
EOF
    
    sudo chmod +x "$INSTALL_DIR/bin/distbuild"
    sudo chown "$SERVICE_USER:$SERVICE_USER" "$INSTALL_DIR/bin/distbuild"
    
    log_success "Launcher script created at $INSTALL_DIR/bin/distbuild"
}

# Add to PATH
add_to_path() {
    log_info "Adding distbuild to PATH..."
    
    SHELL_RC=""
    if [[ -f "$HOME/.bashrc" ]]; then
        SHELL_RC="$HOME/.bashrc"
    elif [[ -f "$HOME/.zshrc" ]]; then
        SHELL_RC="$HOME/.zshrc"
    fi
    
    if [[ -n "$SHELL_RC" ]]; then
        if ! grep -q "$INSTALL_DIR/bin" "$SHELL_RC"; then
            echo "export PATH=\"$INSTALL_DIR/bin:\$PATH\"" >> "$SHELL_RC"
            log_success "Added $INSTALL_DIR/bin to PATH in $SHELL_RC"
            log_warning "Run 'source $SHELL_RC' or restart your terminal to use distbuild"
        else
            log_success "$INSTALL_DIR/bin already in PATH"
        fi
    else
        log_warning "Could not find shell configuration file. Please add $INSTALL_DIR/bin to your PATH manually."
    fi
}

# Verify installation
verify_installation() {
    log_info "Verifying installation..."
    
    # Check if distbuild command is available
    if command -v distbuild &> /dev/null; then
        log_success "distbuild command is available"
    else
        log_warning "distbuild command not in PATH. You may need to restart your shell or run: source ~/.bashrc"
    fi
    
    # Test distbuild version
    if "$INSTALL_DIR/bin/distbuild" version &> /dev/null; then
        log_success "distbuild is working correctly"
    else
        log_error "distbuild is not working correctly"
        return 1
    fi
}

# Show next steps
show_next_steps() {
    log_success "distbuild installation completed!"
    echo
    echo "Next steps:"
    echo "1. Restart your terminal or run: source ~/.bashrc"
    echo "2. Run: distbuild doctor"
    echo "3. Start the coordinator: distbuild coordinator start"
    echo "4. Start a worker: distbuild worker start"
    echo "5. Check status: distbuild status"
    echo
    echo "For help: distbuild --help"
    echo "For documentation: https://github.com/Aaryan1101/distbuild"
}

# Main installation flow
main() {
    echo "distbuild Installer v${DISTBUILD_VERSION}"
    echo "=================================="
    echo
    
    check_root
    check_requirements
    check_java
    check_redis
    download_distbuild
    create_launcher
    add_to_path
    verify_installation
    show_next_steps
}

# Run installation
main "$@"
