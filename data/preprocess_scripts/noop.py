# noop.py
# 用途：NOOP 增强脚本，仅复制图像和标注文件，不做任何处理
import os
import sys
import shutil
import json

def safe_copy_file(src, dst):
    """安全复制单个文件，避免 SameFileError"""
    src_abs = os.path.abspath(src)
    dst_abs = os.path.abspath(dst)
    if src_abs == dst_abs:
        return  # 跳过相同文件
    os.makedirs(os.path.dirname(dst_abs), exist_ok=True)
    shutil.copy2(src, dst)

def safe_copy_dir(src_dir, dst_dir):
    """递归安全复制目录"""
    src_dir = os.path.abspath(src_dir)
    dst_dir = os.path.abspath(dst_dir)
    if src_dir == dst_dir:
        return  # 完全相同的目录，跳过
    os.makedirs(dst_dir, exist_ok=True)
    for item in os.listdir(src_dir):
        s = os.path.join(src_dir, item)
        d = os.path.join(dst_dir, item)
        if os.path.isfile(s):
            safe_copy_file(s, d)
        elif os.path.isdir(s):
            safe_copy_dir(s, d)

if __name__ == '__main__':
    if len(sys.argv) != 5:
        print("Usage: python noop.py <input_image_dir> <input_label_dir> <output_image_dir> <output_label_dir>")
        sys.exit(1)

    input_image_dir = sys.argv[1]
    input_label_dir = sys.argv[2]
    output_image_dir = sys.argv[3]
    output_label_dir = sys.argv[4]

    print("[Python] [NOOP] 开始执行空增强：仅复制数据，不进行任何处理。")

    # 复制图像目录
    if os.path.exists(input_image_dir):
        safe_copy_dir(input_image_dir, output_image_dir)
        print(f"[Python] [NOOP] 图像已复制到: {output_image_dir}")
    else:
        print(f"[Python] [NOOP] 警告：输入图像目录不存在: {input_image_dir}")

    # # 复制标注目录（支持 COCO JSON、DOTA TXT 等任意格式）
    if os.path.exists(input_label_dir):
        safe_copy_dir(input_label_dir, output_label_dir)
        print(f"[Python] [NOOP] 标注已复制到: {output_label_dir}")
    else:
        print(f"[Python] [NOOP] 警告：输入标注目录不存在: {input_label_dir}")

    print("[Python] [NOOP] 数据复制完成。")
