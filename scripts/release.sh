#!/usr/bin/env bash
#
# Daos 发布脚本：只负责 Maven 制品版本与 Git tag。
# 不触碰 schemaVersion、releaseVersion、snapshotId 与前端 @daos/* 包版本。
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
POM="$ROOT_DIR/pom.xml"

usage() {
    cat <<'EOF'
用法: scripts/release.sh <version>

示例: scripts/release.sh 1.2.0

执行内容:
  1. 校验版本格式、分支与工作区
  2. 改写根 pom 的 <revision> 并执行 mvn -B verify
  3. 提交并打 tag vX.Y.Z
  4. 把 main 推进到下一个 SNAPSHOT

脚本不会自动 push。
EOF
}

fail() {
    echo "发布中止: $*" >&2
    exit 1
}

[ $# -eq 1 ] || { usage; exit 1; }
VERSION="$1"
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "版本必须是 X.Y.Z 形式: $VERSION"

git -C "$ROOT_DIR" rev-parse --is-inside-work-tree >/dev/null 2>&1 || fail "当前目录不是 Git 仓库"
BRANCH="$(git -C "$ROOT_DIR" branch --show-current)"
[ "$BRANCH" = "main" ] || fail "只能在 main 上发布，当前分支: $BRANCH"
[ -z "$(git -C "$ROOT_DIR" status --porcelain)" ] || fail "工作区不干净，请先提交或清理"
git -C "$ROOT_DIR" rev-parse -q --verify "refs/tags/v$VERSION" >/dev/null && fail "tag v$VERSION 已存在"
command -v mvn >/dev/null 2>&1 || fail "未找到 mvn"

CURRENT="$(sed -n 's:.*<revision>\(.*\)</revision>.*:\1:p' "$POM" | head -1)"
[ -n "$CURRENT" ] || fail "根 pom 未找到 <revision>"

case "$CURRENT" in
    *-SNAPSHOT) CURRENT_BASE="${CURRENT%-SNAPSHOT}"; IS_SNAPSHOT=1 ;;
    *) CURRENT_BASE="$CURRENT"; IS_SNAPSHOT=0 ;;
esac

HIGHER="$(printf '%s\n%s\n' "$CURRENT_BASE" "$VERSION" | sort -V | tail -1)"
[ "$HIGHER" = "$VERSION" ] || fail "目标版本 $VERSION 低于当前 $CURRENT_BASE"
if [ "$VERSION" = "$CURRENT_BASE" ] && [ "$IS_SNAPSHOT" = "0" ]; then
    fail "版本 $VERSION 已发布过，请提升版本号"
fi

set_revision() {
    perl -0pi -e "s{<revision>[^<]+</revision>}{<revision>$1</revision>}" "$POM"
    grep -q "<revision>$1</revision>" "$POM" || fail "写入 revision 失败"
}

echo "==> 发布 $VERSION（当前 $CURRENT）"
set_revision "$VERSION"

echo "==> 执行 mvn -B verify"
if ! (cd "$ROOT_DIR" && mvn -B verify); then
    git -C "$ROOT_DIR" checkout -- pom.xml
    fail "构建失败，已还原 pom.xml"
fi

git -C "$ROOT_DIR" add pom.xml
git -C "$ROOT_DIR" commit -q -m "chore(release): 发布 v$VERSION"
git -C "$ROOT_DIR" tag "v$VERSION"

IFS='.' read -r MAJOR MINOR PATCH <<< "$VERSION"
NEXT="$MAJOR.$MINOR.$((PATCH + 1))-SNAPSHOT"
set_revision "$NEXT"
git -C "$ROOT_DIR" add pom.xml
git -C "$ROOT_DIR" commit -q -m "chore(release): 开启 $NEXT"

cat <<EOF

发布完成: v$VERSION
main 已推进到: $NEXT

推送:
  git push origin main
  git push origin v$VERSION
EOF
