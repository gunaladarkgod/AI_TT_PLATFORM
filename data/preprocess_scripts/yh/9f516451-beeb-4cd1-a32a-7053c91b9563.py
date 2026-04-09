import os
import sys
import random
import shutil
import numpy as np
from PIL import Image, ImageEnhance
from typing import List, Tuple, Dict
import warnings
warnings.filterwarnings('ignore')

def set_seed(seed: int = 42) -> None:
    """设置随机种子保证可复现性"""
    random.seed(seed)
    np.random.seed(seed)

def load_dota_dataset(img_dir: str, lbl_dir: str) -> Tuple[List[str], Dict[str, np.ndarray]]:
    """
    加载DOTA格式数据集（图像+标注）
    DOTA标注格式：x1 y1 x2 y2 x3 y3 x4 y4 class difficult
    （8个点坐标为四边形像素坐标，class为类别名称，difficult为0/1）
    """
    img_paths = []
    label_dict = {}  # key: 图像路径, value: 边界框列表（shape: [N, 10]，10=8坐标+class_id+difficult）

    # 简单类别映射（可根据实际数据集扩展）
    class_names = set()
    for lbl_name in os.listdir(lbl_dir):
        if lbl_name.endswith('.txt'):
            with open(os.path.join(lbl_dir, lbl_name), 'r', encoding='utf-8') as f:
                for line in f:
                    parts = line.strip().split()
                    if len(parts) == 10:
                        class_names.add(parts[8])
    class2id = {name: i for i, name in enumerate(sorted(class_names))}

    for img_name in os.listdir(img_dir):
        img_path = os.path.join(img_dir, img_name)
        if not img_name.lower().endswith(('.jpg', '.jpeg', '.png', '.bmp', '.tif')):
            continue
        
        # 对应标注文件路径（DOTA标注通常与图像同名）
        lbl_name = os.path.splitext(img_name)[0] + ".txt"
        lbl_path = os.path.join(lbl_dir, lbl_name)
        
        # 读取标注（无标注则为空列表）
        bboxes = []
        if os.path.exists(lbl_path):
            with open(lbl_path, 'r', encoding='utf-8') as f:
                for line in f:
                    line = line.strip()
                    if not line:
                        continue
                    parts = line.split()
                    if len(parts) == 10:
                        # 解析8个坐标点、类别、difficult
                        coords = list(map(float, parts[:8]))
                        class_name = parts[8]
                        difficult = int(parts[9])
                        class_id = class2id.get(class_name, -1)
                        if class_id != -1:
                            bboxes.append(coords + [class_id, difficult])
        
        img_paths.append(img_path)
        label_dict[img_path] = np.array(bboxes, dtype=np.float32) if bboxes else np.empty((0, 10), dtype=np.float32)
    
    print(f"成功加载 {len(img_paths)} 张图像，标注文件 {len(label_dict)} 个")
    return img_paths, label_dict, class2id

def random_crop(img: np.ndarray, bboxes: np.ndarray, crop_size: Tuple[int, int]) -> Tuple[np.ndarray, np.ndarray]:
    """
    随机裁剪图像和对应的DOTA格式边界框（四边形）
    Args:
        img: 输入图像 (H, W, C)
        bboxes: 边界框 (N, 10)，格式：x1 y1 x2 y2 x3 y3 x4 y4 class_id difficult
        crop_size: 裁剪后图像大小 (crop_h, crop_w)
    Returns:
        cropped_img: 裁剪后的图像
        cropped_bboxes: 裁剪后的边界框（过滤无效框）
    """
    h, w = img.shape[:2]
    crop_h, crop_w = crop_size

    # 计算裁剪区域的左上角坐标（确保裁剪区域在图像内）
    max_top = max(0, h - crop_h)
    max_left = max(0, w - crop_w)
    top = random.randint(0, max_top) if max_top > 0 else 0
    left = random.randint(0, max_left) if max_left > 0 else 0
    bottom = top + crop_h
    right = left + crop_w

    # 裁剪图像
    cropped_img = img[top:bottom, left:right]

    if len(bboxes) == 0:
        return cropped_img, np.empty((0, 10), dtype=np.float32)

    # 调整边界框坐标（减去裁剪偏移）
    cropped_bboxes = []
    for bbox in bboxes:
        coords = bbox[:8].reshape(-1, 2)  # (4, 2)
        # 减去裁剪偏移
        coords[:, 0] -= left
        coords[:, 1] -= top
        # 裁剪到有效区域
        coords[:, 0] = np.clip(coords[:, 0], 0, crop_w - 1)
        coords[:, 1] = np.clip(coords[:, 1], 0, crop_h - 1)
        
        # 判断框是否有效（至少有一个点在裁剪区域内且面积不为0）
        in_area = (coords[:, 0] < crop_w) & (coords[:, 0] >= 0) & \
                  (coords[:, 1] < crop_h) & (coords[:, 1] >= 0)
        if np.any(in_area):
            # 计算多边形面积判断有效性
            x = coords[:, 0]
            y = coords[:, 1]
            area = 0.5 * np.abs(np.dot(x, np.roll(y, 1)) - np.dot(y, np.roll(x, 1)))
            if area > 1.0:  # 面积大于1像素才保留
                cropped_bbox = np.concatenate([coords.flatten(), bbox[8:10]])
                cropped_bboxes.append(cropped_bbox)

    return cropped_img, np.array(cropped_bboxes, dtype=np.float32) if cropped_bboxes else np.empty((0, 10), dtype=np.float32)

def mosaic_augment(
    img_paths: List[str],
    label_dict: Dict[str, np.ndarray],
    output_size: Tuple[int, int],
    use_flip: bool = True,
    color_jitter: bool = True
) -> Tuple[np.ndarray, np.ndarray]:
    """
    马赛克增广核心函数：4张图拼接成1张（适配DOTA格式）
    """
    h, w = output_size
    mosaic_img = np.zeros((h, w, 3), dtype=np.uint8)
    mosaic_bboxes = []

    # 随机生成拼接中心点（在图像中心区域浮动）
    cx = random.randint(int(w * 0.25), int(w * 0.75))
    cy = random.randint(int(h * 0.25), int(h * 0.75))

    # 4个区域的裁剪大小和拼接位置
    regions = [
        ((cy, cx), (0, 0)),          # 左上区域
        ((cy, w - cx), (0, cx)),     # 右上区域
        ((h - cy, cx), (cy, 0)),     # 左下区域
        ((h - cy, w - cx), (cy, cx)) # 右下区域
    ]

    # 随机选择4张图像
    selected_imgs = random.sample(img_paths, 4)

    for img_path, (crop_size, (y0, x0)) in zip(selected_imgs, regions):
        # 读取图像
        with Image.open(img_path).convert('RGB') as pil_img:
            img = np.array(pil_img)
        
        # 获取边界框
        bboxes = label_dict[img_path]

        # 随机裁剪
        cropped_img, cropped_bboxes = random_crop(img, bboxes, crop_size)
        crop_h, crop_w = cropped_img.shape[:2]

        # 可选增强：水平翻转
        if use_flip and random.random() > 0.5:
            cropped_img = np.fliplr(cropped_img)
            if len(cropped_bboxes) > 0:
                # 翻转后x坐标：crop_w - 1 - x
                for bbox in cropped_bboxes:
                    coords = bbox[:8].reshape(-1, 2)
                    coords[:, 0] = crop_w - 1 - coords[:, 0]
                    bbox[:8] = coords.flatten()

        # 可选增强：颜色抖动
        if color_jitter and random.random() > 0.5:
            pil_crop = Image.fromarray(cropped_img)
            pil_crop = ImageEnhance.Brightness(pil_crop).enhance(random.uniform(0.5, 1.5))
            pil_crop = ImageEnhance.Contrast(pil_crop).enhance(random.uniform(0.5, 1.5))
            pil_crop = ImageEnhance.Color(pil_crop).enhance(random.uniform(0.5, 1.5))
            cropped_img = np.array(pil_crop)

        # 将裁剪块拼接到马赛克图像
        mosaic_img[y0:y0+crop_h, x0:x0+crop_w] = cropped_img

        # 调整边界框坐标到马赛克图像（添加偏移）
        if len(cropped_bboxes) > 0:
            adjusted_bboxes = []
            for bbox in cropped_bboxes:
                coords = bbox[:8].reshape(-1, 2)
                # 添加区域偏移
                coords[:, 0] += x0
                coords[:, 1] += y0
                adjusted_bbox = np.concatenate([coords.flatten(), bbox[8:10]])
                adjusted_bboxes.append(adjusted_bbox)
            mosaic_bboxes.extend(adjusted_bboxes)

    # 过滤超出马赛克图像范围的框
    valid_bboxes = []
    for bbox in mosaic_bboxes:
        coords = bbox[:8].reshape(-1, 2)
        # 检查是否有坐标超出图像范围
        if np.all((coords[:, 0] >= 0) & (coords[:, 0] < w) & 
                  (coords[:, 1] >= 0) & (coords[:, 1] < h)):
            valid_bboxes.append(bbox)

    return mosaic_img, np.array(valid_bboxes, dtype=np.float32) if valid_bboxes else np.empty((0, 10), dtype=np.float32)

def save_mosaic_results(
    mosaic_imgs: List[np.ndarray],
    mosaic_bboxes_list: List[np.ndarray],
    img_out: str,
    lbl_out: str,
    num_samples: int,
    class2id: Dict[str, int]
) -> None:
    """保存马赛克增强结果（DOTA格式）"""
    id2class = {v: k for k, v in class2id.items()}

    for i in range(min(len(mosaic_imgs), num_samples)):
        img = mosaic_imgs[i]
        bboxes = mosaic_bboxes_list[i]

        # 保存图像
        img_name = f"mosaic_{i:06d}.jpg"
        img_path = os.path.join(img_out, img_name)
        Image.fromarray(img).save(img_path, quality=95)

        # 保存标注（DOTA格式）
        label_name = f"mosaic_{i:06d}.txt"
        label_path = os.path.join(lbl_out, label_name)
        with open(label_path, 'w', encoding='utf-8') as f:
            for bbox in bboxes:
                coords = bbox[:8]
                class_id = int(bbox[8])
                difficult = int(bbox[9])
                class_name = id2class.get(class_id, "unknown")
                # 格式：x1 y1 x2 y2 x3 y3 x4 y4 class difficult
                line = f"{' '.join(map(lambda x: f'{x:.1f}', coords))} {class_name} {difficult}\n"
                f.write(line)

        # 进度提示
        if (i + 1) % 100 == 0:
            print(f"已保存 {i + 1}/{num_samples} 个马赛克样本")

    print(f"\n马赛克增强完成！")
    print(f"增强图像：{img_out}")
    print(f"增强标注：{lbl_out}")
    print(f"共生成 {min(len(mosaic_imgs), num_samples)} 个样本")

if __name__ == '__main__':
    if len(sys.argv) < 5:
        print("Usage: script.py img_in lbl_in img_out lbl_out [params...]")
        print("Params顺序: num_samples(默认500) img_h(默认640) img_w(默认640) seed(默认42) use_flip(默认True) color_jitter(默认True)")
        sys.exit(1)
    
    img_in, lbl_in, img_out, lbl_out = sys.argv[1:5]
    extra = sys.argv[5:]
    
    # 创建输出目录
    os.makedirs(img_out, exist_ok=True)
    os.makedirs(lbl_out, exist_ok=True)
    
    # 解析参数
    num_samples = int(extra[0]) if len(extra) > 0 else 500
    img_h = int(extra[1]) if len(extra) > 1 else 640
    img_w = int(extra[2]) if len(extra) > 2 else 640
    seed = int(extra[3]) if len(extra) > 3 else 42
    use_flip = extra[4].lower() == 'true' if len(extra) > 4 else True
    color_jitter = extra[5].lower() == 'true' if len(extra) > 5 else True
    
    # 初始化设置
    set_seed(seed)
    output_size = (img_h, img_w)
    
    # 加载DOTA数据集
    img_paths, label_dict, class2id = load_dota_dataset(img_in, lbl_in)
    if len(img_paths) < 4:
        raise ValueError("数据集图像数量必须≥4才能进行马赛克增广")
    
    # 生成马赛克样本
    mosaic_imgs = []
    mosaic_bboxes_list = []
    print(f"开始生成马赛克样本（输出大小：{output_size}，翻转：{use_flip}，颜色抖动：{color_jitter}）")
    
    for _ in range(num_samples):
        img, bboxes = mosaic_augment(
            img_paths, label_dict, output_size, use_flip, color_jitter
        )
        mosaic_imgs.append(img)
        mosaic_bboxes_list.append(bboxes)
    
    # 保存结果
    save_mosaic_results(mosaic_imgs, mosaic_bboxes_list, img_out, lbl_out, num_samples, class2id)
    
    print("✅ 脚本执行成功！")
