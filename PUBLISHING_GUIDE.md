# Publishing Guide for distbuild

## Step-by-Step Instructions

### Phase 1: Repository Setup (Required)

#### 1. Update Repository References
Replace all placeholder references with your actual GitHub details:

**Files to update:**
- `packages/scoop/distbuild.json` - Replace `YOUR_USERNAME` with your GitHub username
- `packages/winget/distbuild.yaml` - Replace `YOUR_ACTUAL_USERNAME` and `YOUR_ACTUAL_NAME`
- `packages/apt/distbuild.control` - Replace `YOUR_ACTUAL_NAME` and email
- `packages/apt/build-deb.sh` - Replace `YOUR_USERNAME` with your GitHub username
- `installer/install.sh` - Replace `YOUR_USERNAME` with your GitHub username
- `installer/install.ps1` - Replace `YOUR_USERNAME` with your GitHub username
- `.github/workflows/release.yml` - Already uses `${{ github.repository_owner }}` (automatic)

#### 2. Create Required GitHub Repositories

**Create these repositories:**
1. **Main repository**: `your-username/distbuild` (already exists)
2. **Scoop bucket**: `your-username/scoop-distbuild` (create if not exists)
3. **GitHub Pages branch**: Create `gh-pages` branch in main repository

**Create Scoop Bucket:**
```bash
# Clone a template or create empty repo
git clone https://github.com/YOUR_USERNAME/scoop-distbuild.git
cd scoop-distbuild
echo '{"version": "1.0.0", "description": "Scoop bucket for distbuild"}' > bucket.json
git add bucket.json
git commit -m "Initial bucket setup"
git push origin main
```

**Create GitHub Pages Branch:**
```bash
cd /path/to/distbuild
git checkout --orphan gh-pages
echo "# distbuild APT Repository" > README.md
git add README.md
git commit -m "Initial gh-pages setup"
git push origin gh-pages
git checkout main
```

### Phase 2: First Release

#### 1. Update Version
```bash
# Edit gradle.properties
distbuildVersion=1.0.0
```

#### 2. Commit and Tag
```bash
git add gradle.properties
git commit -m "release: v1.0.0"
git tag v1.0.0
git push origin main --tags
```

#### 3. GitHub Actions Will Automatically:
- Build JAR
- Create GitHub Release
- Update Scoop manifest
- Build Debian package
- Update APT repository
- Create Winget manifest

### Phase 3: Package Manager Setup

#### 1. Scoop (Windows) - Automatic
The GitHub Actions will automatically update your Scoop bucket. Users can install with:
```powershell
scoop bucket add distbuild https://github.com/YOUR_USERNAME/scoop-distbuild
scoop install distbuild
```

#### 2. apt (Linux) - Manual Setup Required
**Set up GPG signing:**
```bash
# Generate GPG key (if you don't have one)
gpg --full-generate-key

# Export public key
gpg --armor --export you@example.com > public.key

# Add to GitHub repository
echo "-----BEGIN PGP PUBLIC KEY BLOCK-----" > gpg.key
cat public.key >> gpg.key
echo "-----END PGP PUBLIC KEY BLOCK-----" >> gpg.key

# Upload gpg.key to GitHub Pages
git checkout gh-pages
cp ../gpg.key .
git add gpg.key
git commit -m "Add GPG key"
git push origin gh-pages
```

**Manual APT setup (first time only):**
```bash
# After first release, manually sign packages
cd /tmp/apt-repo
gpg --default-key you@example.com -abs -o Release.gpg Release
gpg --default-key you@example.com --clearsign -o InRelease Release
git add -A && git commit -m "Sign packages" && git push
```

#### 3. Winget (Windows 11) - Manual PR Required
**Create PR to Microsoft repository:**
```bash
# GitHub Actions creates the manifest in /tmp/winget-pkgs
# You need to:
# 1. Fork https://github.com/microsoft/winget-pkgs
# 2. Copy manifests/y/YOUR_USERNAME/distbuild/1.0.0/ to your fork
# 3. Create PR to microsoft/winget-pkgs
```

### Phase 4: Testing Installation

#### Test One-Liner Installers:
```bash
# Linux/macOS
curl -fsSL https://raw.githubusercontent.com/YOUR_USERNAME/distbuild/main/installer/install.sh | bash

# Windows PowerShell
irm https://raw.githubusercontent.com/YOUR_USERNAME/distbuild/main/installer/install.ps1 | iex
```

#### Test Package Managers:
```bash
# Scoop (Windows)
scoop bucket add distbuild https://github.com/YOUR_USERNAME/scoop-distbuild
scoop install distbuild

# apt (Ubuntu/Debian)
curl -fsSL https://YOUR_USERNAME.github.io/distbuild/gpg.key | sudo gpg --dearmor -o /etc/apt/keyrings/distbuild.gpg
echo 'deb [signed-by=/etc/apt/keyrings/distbuild.gpg] https://YOUR_USERNAME.github.io/distbuild stable main' | sudo tee /etc/apt/sources.list.d/distbuild.list
sudo apt update && sudo apt install distbuild
```

### Phase 5: Ongoing Maintenance

#### Release New Version:
```bash
# 1. Update version
echo "distbuildVersion=1.1.0" >> gradle.properties

# 2. Commit and tag
git add gradle.properties
git commit -m "release: v1.1.0"
git tag v1.1.0
git push origin main --tags

# 3. GitHub Actions handles everything automatically
```

#### Monitor Issues:
- Check GitHub Actions logs for any failures
- Monitor Scoop bucket for update issues
- Check APT repository signing status
- Track Winget PR status

### Phase 6: User Support

#### Update Documentation:
- Keep `README_INSTALLATION.md` up to date
- Update changelog with each release
- Monitor GitHub Issues for installation problems

#### Troubleshooting Common Issues:

**Scoop Issues:**
```bash
# Update scoop bucket
scoop update
scoop bucket update distbuild

# Reinstall
scoop uninstall distbuild
scoop install distbuild
```

**apt Issues:**
```bash
# Update package list
sudo apt update

# Reinstall
sudo apt remove distbuild
sudo apt install distbuild
```

**Winget Issues:**
```powershell
# Update winget database
winget source update

# Reinstall
winget uninstall YourName.distbuild
winget install YourName.distbuild
```

### Phase 7: Advanced Setup (Optional)

#### Custom Domain for APT Repository:
```bash
# Update apt repository URLs in:
# - packages/apt/build-deb.sh
# - README_INSTALLATION.md
# - GitHub Actions workflow
```

#### Automated Testing:
```bash
# Add test workflow to .github/workflows/
# Test installers on multiple platforms
# Test package manager installations
```

#### Analytics and Monitoring:
```bash
# Add download tracking to installers
# Monitor GitHub release downloads
# Track package manager installation stats
```

## Quick Start Checklist

### Before First Release:
- [ ] Update all `YOUR_USERNAME` placeholders
- [ ] Create Scoop bucket repository
- [ ] Create GitHub Pages branch
- [ ] Set up GPG key for apt signing
- [ ] Test installers locally

### First Release:
- [ ] Update version in gradle.properties
- [ ] Commit and tag release
- [ ] Verify GitHub Actions success
- [ ] Test all installation methods
- [ ] Submit Winget PR

### Ongoing:
- [ ] Monitor GitHub Actions
- [ ] Update changelog with each release
- [ ] Respond to user issues
- [ ] Keep documentation updated

## Support Resources

### Documentation:
- `README_INSTALLATION.md` - User installation guide
- `CLI_OPERATIONAL_GUIDE.md` - CLI usage guide
- `distbuild-distribution-guide.txt` - Distribution strategy

### GitHub Actions:
- Monitor `.github/workflows/release.yml` for failures
- Check release artifacts in GitHub releases

### Package Managers:
- Scoop: https://github.com/lukesampson/scoop/wiki
- apt: https://wiki.debian.org/DebianRepository/Format
- Winget: https://github.com/microsoft/winget-pkgs

### Community:
- GitHub Issues for bug reports
- GitHub Discussions for questions
- Create documentation for common issues

---

**You're now ready to publish distbuild to the world!** Follow this guide step by step for a successful release.
