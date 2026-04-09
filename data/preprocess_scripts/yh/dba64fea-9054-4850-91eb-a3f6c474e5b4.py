import os
import random
import sys
import shutil
import numpy as np
from PIL import Image
from typing import List, Tuple

def set_seed(seed: int = 42) -> None:
    """设置随机种子以保证可重复性"""
    random.seed(seed)
    np.random.seed(seed)

def load_dota_data(img_dir: str, lbl_dir: str) -> Tuple[List[np.ndarray], List[List[str]], List[str]]:
    """
    加载DOTA格式的图像和标注
    假设图像和标注文件同名，标注为txt格式
    Args:
        img_dir: 图像目录
        lbl_dir: 标注目录
    Returns:
        images: 图像数组列表 (H, W, C)
        bboxes_list: 每个图像的边界框列表，每个元素是DOTA格式的一行字符串
        img_names: 图像文件名（不含扩展名）列表
    """
    images = []
    bboxes_list = []
    img_names = []
    img_extensions = ('.png', '.jpg', '.jpeg', '.bmp', '.tif')
    
    for img_name in os.listdir(img_dir):
        img_base, ext = os.path.splitext(img_name)
        if ext.lower() not in img_extensions:
            continue
        
        # 读取图像
        img_path = os.path.join(img_dir, img_name)
        with Image.open(img_path).convert('RGB') as img:
            img_arr = np.array(img, dtype=np.float32) / 255.0
            images.append(img_arr)
        
        # 读取对应的标注文件
        lbl_path = os.path.join(lbl_dir, f"{img_base}.txt")
        bboxes = []
        if os.path.exists(lbl_path):
            with open(lbl_path, 'r', encoding='utf-8') as f:
                for line in f:
                    line = line.strip()
                    if line:
                        bboxes.append(line)  # 保留原始行，后续处理坐标
        bboxes_list.append(bboxes)
        img_names.append(img_base)
    
    print(f"成功加载 {len(images)} 张图像及其标注")
    return images, bboxes_list, img_names

def mixup_dota_samples(
    img1: np.ndarray, 
    bboxes1: List[str], 
    img2: np.ndarray, 
    bboxes2: List[str], 
    alpha: float = 1.0
) -> Tuple[np.ndarray, List[str]]:
    """
    对两个DOTA样本进行Mixup混合，处理图像和边界框
    Args:
        img1: 第一个图像 (H, W, C)
        bboxes1: 第一个图像的边界框列表（DOTA格式）
        img2: 第二个图像 (H, W, C)
        bboxes2: 第二个图像的边界框列表（DOTA格式）
        alpha: Beta分布参数
    Returns:
        mixed_img: 混合后的图像
        mixed_bboxes: 混合后的边界框列表（DOTA格式）
    """
    # 生成混合系数λ
    if alpha > 0:
        lam = np.random.beta(alpha, alpha)
    else:
        lam = 1.0
    
    # 获取图像1的尺寸（H, W）
    h1, w1 = img1.shape[0], img1.shape[1]
    h2, w2 = img2.shape[0], img2.shape[1]
    
    # 调整img2的大小以匹配img1，并调整其边界框坐标
    if (h1, w1) != (h2, w2):
        # 调整图像大小
        img2_resized = np.array(Image.fromarray((img2 * 255).astype(np.uint8)).resize(
            (w1, h1), Image.Resampling.LANCZOS
        ), dtype=np.float32) / 255.0
        
        # 调整边界框坐标：缩放
        scale_w = w1 / w2
        scale_h = h1 / h2
        bboxes2_resized = []
        for bbox_line in bboxes2:
            parts = bbox_line.split()
            if len(parts) < 9:  # DOTA格式至少有8个坐标+类别+难度
                continue
            # 解析8个坐标
            coords = list(map(float, parts[:8]))
            # 缩放坐标
            scaled_coords = []
            for i in range(0, 8, 2):
                x = coords[i] * scale_w
                y = coords[i+1] * scale_h
                scaled_coords.extend([x, y])
            # 重新组合成字符串
            scaled_line = ' '.join(map(str, scaled_coords)) + ' ' + ' '.join(parts[8:])
            bboxes2_resized.append(scaled_line)
    else:
        img2_resized = img2
        bboxes2_resized = bboxes2
    
    # 混合图像
    mixed_img = lam * img1 + (1 - lam) * img2_resized
    
    # 混合后的边界框是两个图像的边界框组合
    mixed_bboxes = bboxes1 + bboxes2_resized
    
    return mixed_img, mixed_bboxes

def save_dota_mixup_results(
    mixed_imgs: List[np.ndarray],
    mixed_bboxes: List[List[str]],
    img_out_dir: str,
    lbl_out_dir: str,
    num_samples: int
) -> None:
    """保存Mixup增强后的DOTA格式图像和标注"""
    for i in range(min(len(mixed_imgs), num_samples)):
        img = mixed_imgs[i]
        bboxes = mixed_bboxes[i]
        
        # 保存图像
        img_name = f"mixup_{i:06d}.jpg"
        img_path = os.path.join(img_out_dir, img_name)
        img_uint8 = (np.clip(img, 0.0, 1.0) * 255).astype(np.uint8)
        Image.fromarray(img_uint8).save(img_path, quality=95)
        
        # 保存标注（与图像同名的txt文件）
        lbl_name = f"mixup_{i:06d}.txt"
        lbl_path = os.path.join(lbl_out_dir, lbl_name)
        with open(lbl_path, 'w', encoding='utf-8') as f:
            for bbox_line in bboxes:
                f.write(f"{bbox_line}\n")
    
    print(f"增强图像保存至：{img_out_dir}")
    print(f"增强标注保存至：{lbl_out_dir}")
    print(f"共生成 {min(len(mixed_imgs), num_samples)} 个增强样本")

if __name__ == '__main__':
    if len(sys.argv) < 5:
        print("Usage: script.py img_in lbl_in img_out lbl_out [params...]")
        print("params顺序: alpha(默认1.0) num_samples(默认1000) seed(默认42)")
        sys.exit(1)
    
    img_in, lbl_in, img_out, lbl_out = sys.argv[1:5]
    extra = sys.argv[5:]
    
    # 创建输出目录
    os.makedirs(img_out, exist_ok=True)
    os.makedirs(lbl_out, exist_ok=True)
    
    # 解析额外参数
    alpha = float(extra[0]) if len(extra) > 0 else 1.0
    num_samples = int(extra[1]) if len(extra) > 1 else 1000
    seed = int(extra[2]) if len(extra) > 2 else 42
    
    # 设置随机种子
    set_seed(seed)
    
    # 加载DOTA数据
    images, bboxes_list, _ = load_dota_data(img_in, lbl_in)
    total_samples = len(images)
    if total_samples < 2:
        raise ValueError("原始数据集中样本数必须至少为2才能进行Mixup")
    
    # 生成Mixup样本
    mixed_imgs = []
    mixed_bboxes = []
    
    while len(mixed_imgs) < num_samples:
        # 随机选择两个不同的样本
        idx1 = random.randint(0, total_samples - 1)
        idx2 = random.randint(0, total_samples - 1)
        if idx1 == idx2:
            continue
        
        # 混合样本
        img1, bboxes1 = images[idx1], bboxes_list[idx1]
        img2, bboxes2 = images[idx2], bboxes_list[idx2]
        mixed_img, mixed_bbox = mixup_dota_samples(
            img1, bboxes1, img2, bboxes2, alpha
        )
        
        mixed_imgs.append(mixed_img)
        mixed_bboxes.append(mixed_bbox)
        
        # 进度提示
        if len(mixed_imgs) % 100 == 0:
            print(f"已生成 {len(mixed_imgs)}/{num_samples} 个增强样本")
    
    # 保存结果
    save_dota_mixup_results(mixed_imgs, mixed_bboxes, img_out, lbl_out, num_samples)
    print("✅ 脚本执行成功！")
