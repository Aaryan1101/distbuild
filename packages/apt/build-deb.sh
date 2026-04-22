#!/bin/bash
# Builds a .deb and updates the GitHub Pages apt repo
set -euo pipefail

VERSION=${1:-1.0.0}

echo "Building distbuild Debian package v$VERSION"

# Build .deb package structure
rm -rf deb-pkg
mkdir -p deb-pkg/DEBIAN
mkdir -p deb-pkg/opt/distbuild
mkdir -p deb-pkg/usr/local/bin

# Copy control file, substitute version
sed "s/Version: .*/Version: $VERSION/" packages/apt/distbuild.control > deb-pkg/DEBIAN/control

# Copy JAR
cp cli/build/libs/distbuild.jar deb-pkg/opt/distbuild/distbuild.jar

# Write launcher
cat > deb-pkg/usr/local/bin/distbuild <<'EOF'
#!/bin/bash
exec java ${DISTBUILD_JVM_OPTS:--Xmx512m} -jar /opt/distbuild/distbuild.jar "$@"
EOF
chmod +x deb-pkg/usr/local/bin/distbuild

# Calculate package size
SIZE=$(du -s deb-pkg | cut -f1)

# Update control file with size
sed -i "s/Installed-Size: .*/Installed-Size: $SIZE/" deb-pkg/DEBIAN/control

# Build the .deb
dpkg-deb --build deb-pkg distbuild-${VERSION}.deb

echo "Built distbuild-${VERSION}.deb"

# Update GitHub Pages apt repository (requires gh-pages branch)
if [ ! -d "apt-repo" ]; then
    echo "Cloning apt repository (requires gh-pages branch)"
    git clone --branch gh-pages https://github.com/Aaryan1101/distbuild.git apt-repo
fi

cp distbuild-${VERSION}.deb apt-repo/
cd apt-repo

# Update package index
echo "Updating package index..."
dpkg-scanpackages --multiversion . > Packages
gzip -k -f Packages

# Generate Release file
echo "Generating Release file..."
apt-ftparchive release . > Release

# Sign packages (you'll need to set up GPG signing)
echo "Note: You need to sign the packages manually:"
echo "1. gpg --default-key you@example.com -abs -o Release.gpg Release"
echo "2. gpg --default-key you@example.com --clearsign -o InRelease Release"
echo "3. git add -A && git commit -m 'apt repo: distbuild v$VERSION' && git push"

git add -A
git commit -m "apt repo: distbuild v$VERSION" || echo "No changes to commit"

echo "Debian package build complete!"
echo "Package: distbuild-${VERSION}.deb"
echo "Repository: apt-repo/"
echo ""
echo "Next steps:"
echo "1. Sign the Release files with GPG"
echo "2. Push the changes: git push"
echo "3. Users can install with:"
echo "   curl -fsSL https://YOUR_USERNAME.github.io/distbuild/gpg.key | sudo gpg --dearmor -o /etc/apt/keyrings/distbuild.gpg"
echo "   echo 'deb [signed-by=/etc/apt/keyrings/distbuild.gpg] https://YOUR_USERNAME.github.io/distbuild stable main' | sudo tee /etc/apt/sources.list.d/distbuild.list"
echo "   sudo apt update && sudo apt install distbuild"
