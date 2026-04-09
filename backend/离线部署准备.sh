#!/bin/bash
# 离线部署准备脚本（Ubuntu版本）
# 用于在有网络的电脑上准备离线部署所需的文件

echo "========================================"
echo "离线部署准备脚本"
echo "========================================"
echo ""
echo "此脚本用于在有网络的电脑上准备离线部署所需的文件"
echo "将生成完整的 dist/ 文件夹"
echo ""

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
MAVEN_REPO="$HOME/.m2/repository"
OUTPUT_DIR="$PROJECT_DIR/dist"

echo "[1/4] 创建输出目录..."
mkdir -p "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR/maven-repository"
mkdir -p "$OUTPUT_DIR/project"

echo "[2/4] 下载Maven依赖..."
echo "这可能需要几分钟时间，请耐心等待..."
cd "$PROJECT_DIR"
mvn dependency:go-offline -q
if [ $? -ne 0 ]; then
    echo "错误：Maven依赖下载失败！"
    echo "请检查网络连接和Maven配置"
    exit 1
fi
echo "  ✓ Maven依赖下载完成"

echo "[3/4] 复制Maven本地仓库..."
echo "正在复制Maven仓库到输出目录..."
echo "这可能需要较长时间，请耐心等待..."
cp -r "$MAVEN_REPO"/* "$OUTPUT_DIR/maven-repository/" 2>/dev/null
if [ $? -ne 0 ]; then
    echo "警告：复制Maven仓库时出现问题"
    echo "请手动复制 $MAVEN_REPO 到 $OUTPUT_DIR/maven-repository"
else
    echo "  ✓ Maven仓库复制完成"
fi

echo "[4/4] 复制项目文件..."
echo "正在复制项目文件..."
cp -r "$PROJECT_DIR/src" "$OUTPUT_DIR/project/" 2>/dev/null
cp -r "$PROJECT_DIR/target" "$OUTPUT_DIR/project/" 2>/dev/null
cp "$PROJECT_DIR/pom.xml" "$OUTPUT_DIR/project/" 2>/dev/null
if [ $? -eq 0 ]; then
    echo "  ✓ 项目文件复制完成"
else
    echo "  ⚠ 部分项目文件复制失败，请检查"
fi

# 复制README.txt到dist目录（可选，如果需要在dist中也有README）
# cp "$PROJECT_DIR/README.txt" "$OUTPUT_DIR/" 2>/dev/null

echo ""
echo "========================================"
echo "准备完成！"
echo "========================================"
echo ""
echo "已准备的文件位置："
echo "  dist/maven-repository/  - Maven依赖仓库"
echo "  dist/project/           - 项目源代码"
echo ""
echo "下一步操作："
echo "1. 将 dist/ 文件夹复制到U盘的对应位置"
echo "2. 在离线电脑上安装JDK 17、Maven、MySQL、Redis"
echo "3. 配置Maven使用复制的本地仓库"
echo "4. 按照部署文档完成后续配置"
echo ""


