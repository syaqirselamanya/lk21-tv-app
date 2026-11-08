#!/bin/zsh
# Skrip Automatik Buat Repo & Muat Naik ke GitHub (Tanpa Perlu Git / Xcode Tools)
# Cara guna: ./upload-to-github.sh <GITHUB_TOKEN> <NAMA_REPO>

set -e

TOKEN="$1"
REPO_NAME="${2:-lk21-tv-app}"

if [ -z "$TOKEN" ]; then
  echo "❌ Sila masukkan GitHub Personal Access Token (dengan skop 'repo' dan 'workflow')."
  echo "Contoh: ./upload-to-github.sh ghp_xxxxxxxxxxxx lk21-tv-app"
  exit 1
fi

echo "🔍 Menyemak akaun GitHub..."
USER_JSON=$(/usr/bin/curl -s -H "Authorization: token $TOKEN" -H "Accept: application/vnd.github+json" https://api.github.com/user)
USERNAME=$(echo "$USER_JSON" | grep -o '"login": *"[^"]*"' | head -n 1 | cut -d'"' -f4)

if [ -z "$USERNAME" ]; then
  echo "❌ Token tidak sah atau gagal disahkan. Sila semak semula token anda."
  exit 1
fi

echo "✅ Berjaya log masuk sebagai: $USERNAME"
echo "📦 Membuat repository '$REPO_NAME'..."

/usr/bin/curl -s -X POST \
  -H "Authorization: token $TOKEN" \
  -H "Accept: application/vnd.github+json" \
  https://api.github.com/user/repos \
  -d "{\"name\":\"$REPO_NAME\",\"description\":\"LK21 TV App (No Ads) & Cloudstream Repository\",\"private\":false,\"auto_init\":true}" > /dev/null

sleep 2

# Kemas kini repo.json dengan username dan nama repo sebenar
sed -i '' "s|USERNAME/REPO_NAME|$USERNAME/$REPO_NAME|g" repo.json

upload_file() {
  local FILE_PATH="$1"
  local REL_PATH="${FILE_PATH#./}"
  echo "⬆️  Memuat naik: $REL_PATH"

  # Semak jika fail sudah wujud (untuk dapatkan SHA)
  local EXISTING_SHA=$(/usr/bin/curl -s \
    -H "Authorization: token $TOKEN" \
    -H "Accept: application/vnd.github+json" \
    "https://api.github.com/repos/$USERNAME/$REPO_NAME/contents/$REL_PATH" | grep -o '"sha": *"[^"]*"' | head -n 1 | cut -d'"' -f4)

  local TMP_JSON=$(mktemp /tmp/gh_upload.XXXXXX)
  if [ -n "$EXISTING_SHA" ]; then
    printf '{"message":"Update %s","sha":"%s","content":"' "$REL_PATH" "$EXISTING_SHA" > "$TMP_JSON"
  else
    printf '{"message":"Add %s","content":"' "$REL_PATH" > "$TMP_JSON"
  fi

  /usr/bin/base64 -i "$FILE_PATH" | tr -d '\n\r' >> "$TMP_JSON"
  printf '"}' >> "$TMP_JSON"

  /usr/bin/curl -s -X PUT \
    -H "Authorization: token $TOKEN" \
    -H "Accept: application/vnd.github+json" \
    "https://api.github.com/repos/$USERNAME/$REPO_NAME/contents/$REL_PATH" \
    -d @"$TMP_JSON" > /dev/null

  rm -f "$TMP_JSON"
}

delete_file_if_exists() {
  local REL_PATH="$1"
  local EXISTING_SHA=$(/usr/bin/curl -s \
    -H "Authorization: token $TOKEN" \
    -H "Accept: application/vnd.github+json" \
    "https://api.github.com/repos/$USERNAME/$REPO_NAME/contents/$REL_PATH" | grep -o '"sha": *"[^"]*"' | head -n 1 | cut -d'"' -f4)

  if [ -n "$EXISTING_SHA" ]; then
    echo "🗑️  Memadam fail lapuk di GitHub: $REL_PATH"
    /usr/bin/curl -s -X DELETE \
      -H "Authorization: token $TOKEN" \
      -H "Accept: application/vnd.github+json" \
      "https://api.github.com/repos/$USERNAME/$REPO_NAME/contents/$REL_PATH" \
      -d "{\"message\":\"Remove obsolete $REL_PATH\",\"sha\":\"$EXISTING_SHA\"}" > /dev/null
  fi
}

# Padam fail XML drawable lama untuk elak konflik 'duplicate resource' dengan fail PNG baharu
delete_file_if_exists "tv-app/app/src/main/res/drawable/ic_tv_logo.xml"
delete_file_if_exists "tv-app/app/src/main/res/drawable/tv_banner.xml"

# Muat naik semua fail projek dulu, kemudian fail workflow paling akhir supaya GitHub Actions terus bina semua sekali
for f in $(find . -type f -not -path '*/.*' -not -name 'upload-to-github.sh' -not -name '*.zip'); do
  upload_file "$f"
done

upload_file "./.gitignore"
upload_file "./.github/workflows/build.yml"

echo ""
echo "🎉 SIAP! Semua fail telah dimuat naik ke: https://github.com/$USERNAME/$REPO_NAME"
echo "⚙️  GitHub Actions sedang membina APK & Cloudstream Repo di:"
echo "👉 https://github.com/$USERNAME/$REPO_NAME/actions"
echo ""
echo "📺 Selepas ~2 minit (bila Actions bertanda ✅ hijau), guna URL ini di TV anda:"
echo "1. URL APK (Downloader TV):"
echo "   https://github.com/$USERNAME/$REPO_NAME/releases/download/latest/LK21-TV-NoAds.apk"
echo ""
echo "2. URL Cloudstream Repository:"
echo "   https://raw.githubusercontent.com/$USERNAME/$REPO_NAME/builds/repo.json"
