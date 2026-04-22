# Aaryan's Publishing Checklist

## All placeholders have been updated with your information! 

### Your Details:
- **Name**: Aaryan
- **Username**: Aaryan1101
- **Email**: aaryan@example.com (update to your actual email)

## Next Steps - What to Do Now

### Step 1: Create Required GitHub Repositories (10 minutes)

#### 1.1 Create Scoop Bucket
```bash
# Go to GitHub and create new repository: Aaryan1101/scoop-distbuild
# Then:
git clone https://github.com/Aaryan1101/scoop-distbuild.git
cd scoop-distbuild
echo '{"version": "1.0.0", "description": "Scoop bucket for distbuild"}' > bucket.json
git add bucket.json
git commit -m "Initial bucket setup"
git push origin main
```

#### 1.2 Create GitHub Pages Branch
```bash
cd /path/to/your/distbuild/repository
git checkout --orphan gh-pages
echo "# distbuild APT Repository" > README.md
git add README.md
git commit -m "Initial gh-pages setup"
git push origin gh-pages
git checkout main
```

### Step 2: Commit Your Changes (2 minutes)
```bash
# Commit all the updated files with your information
git add .
git commit -m "Update distribution configuration with Aaryan's details"
git push origin main
```

### Step 3: Create Your First Release (2 minutes)
```bash
# Tag and push release
git tag v1.0.0
git push origin main --tags
```

### Step 4: Watch GitHub Actions (5 minutes)
- Go to your GitHub repository
- Click on "Actions" tab
- Watch the "Release" workflow run automatically
- It will:
  - Build the JAR
  - Create GitHub Release
  - Update Scoop manifest
  - Build Debian package
  - Update APT repository
  - Create Winget manifest

### Step 5: Test Installation (5 minutes)

#### Test One-Liner Installers:
```bash
# Linux/macOS
curl -fsSL https://raw.githubusercontent.com/Aaryan1101/distbuild/main/installer/install.sh | bash

# Windows PowerShell
irm https://raw.githubusercontent.com/Aaryan1101/distbuild/main/installer/install.ps1 | iex
```

#### Test Package Managers:
```bash
# Scoop (Windows)
scoop bucket add distbuild https://github.com/Aaryan1101/scoop-distbuild
scoop install distbuild

# Verify installation
distbuild version
```

### Step 6: Optional - Set up Package Managers

#### 6.1 Winget (Windows 11) - Manual PR
- GitHub Actions will create the manifest
- Fork `microsoft/winget-pkgs`
- Copy manifests to your fork
- Create PR to microsoft/winget-pkgs

#### 6.2 apt (Linux) - GPG Setup (Optional)
```bash
# Generate GPG key (first time only)
gpg --full-generate-key

# Add to GitHub Pages branch
git checkout gh-pages
# Add your public key as gpg.key
git add gpg.key
git commit -m "Add GPG key"
git push origin gh-pages
```

## What Users Will See After Publishing

### Installation Commands:
```bash
# One-liner (Linux/macOS)
curl -fsSL https://raw.githubusercontent.com/Aaryan1101/distbuild/main/installer/install.sh | bash

# One-liner (Windows)
irm https://raw.githubusercontent.com/Aaryan1101/distbuild/main/installer/install.ps1 | iex

# Scoop (Windows)
scoop bucket add distbuild https://github.com/Aaryan1101/scoop-distbuild
scoop install distbuild

# apt (Ubuntu/Debian)
curl -fsSL https://Aaryan1101.github.io/distbuild/gpg.key | sudo gpg --dearmor -o /etc/apt/keyrings/distbuild.gpg
echo 'deb [signed-by=/etc/apt/keyrings/distbuild.gpg] https://Aaryan1101.github.io/distbuild stable main' | sudo tee /etc/apt/sources.list.d/distbuild.list
sudo apt update && sudo apt install distbuild

# Winget (Windows 11)
winget install Aaryan1101.distbuild
```

### Installation Experience:
```
distbuild Installer v1.0.0
==================================
[INFO] Checking system requirements...
[SUCCESS] System requirements check passed
[INFO] Checking Java installation...
[SUCCESS] Java 17 found (>= 17 required)
[INFO] Downloading distbuild 1.0.0...
[SUCCESS] distbuild 1.0.0 downloaded successfully
[SUCCESS] Launcher script created at /opt/distbuild/bin/distbuild
[SUCCESS] Added /opt/distbuild/bin to PATH
[SUCCESS] distbuild is working correctly
[SUCCESS] distbuild installation completed!
```

## Files Updated With Your Information

All these files now contain your actual details:

### Distribution Files:
- `packages/scoop/distbuild.json` - Aaryan1101
- `packages/winget/distbuild.yaml` - Aaryan1101.distbuild, Aaryan
- `packages/apt/distbuild.control` - Aaryan
- `packages/apt/build-deb.sh` - Aaryan1101
- `installer/install.sh` - Aaryan1101
- `installer/install.ps1` - Aaryan1101
- `README_INSTALLATION.md` - Aaryan1101

### Configuration:
- `gradle.properties` - distbuildVersion=1.0.0
- GitHub Actions workflow - automatic

## Verification Checklist

### Before Publishing:
- [ ] Created Scoop bucket repository: Aaryan1101/scoop-distbuild
- [ ] Created GitHub Pages branch in main repository
- [ ] Committed all configuration changes
- [ ] All placeholders updated with Aaryan1101

### After Publishing:
- [ ] GitHub Actions workflow runs successfully
- [ ] GitHub Release created with JAR file
- [ ] Scoop manifest updated in your bucket
- [ ] One-liner installers work
- [ ] Package manager installations work
- [ ] Users can run `distbuild version` successfully

## Support Links

### Your Repository:
- Main: https://github.com/Aaryan1101/distbuild
- Scoop bucket: https://github.com/Aaryan1101/scoop-distbuild
- GitHub Pages: https://Aaryan1101.github.io/distbuild

### User Documentation:
- Installation guide: README_INSTALLATION.md
- CLI help: `distbuild --help`
- Version info: `distbuild version`

### Package Manager Pages:
- Scoop: https://github.com/lukesampson/scoop/wiki
- Winget: https://github.com/microsoft/winget-pkgs
- apt: https://wiki.debian.org/DebianRepository/Format

---

## You're Ready to Publish! 

**All placeholders are updated with your information (Aaryan/Aaryan1101). Just follow the steps above and your distbuild tool will be available to users worldwide!** 

**The complete distribution system is ready - you just need to create the repositories and push your first release.** 

Good luck with your launch!
