import os
import cv2
import random
import numpy as np
import glob
import sys

# 设置随机种子，确保结果可复现
random.seed(42)
np.random.seed(42)

class DOTAGenerator:
    def __init__(self, 
                 object_images_dir,  # 目标图像目录（含待粘贴的目标）
                 object_labels_dir,  # DOTA格式标注目录
                 background_images_dir,  # 背景图像目录
                 output_images_dir,  # 生成图像输出目录
                 output_labels_dir,  # 生成DOTA标注输出目录
                 num_generated=3000,
                 min_objects=1, 
                 max_objects=5):
        """
        初始化DOTA格式图像生成器
        :param object_images_dir: 目标图像目录（单目标/多目标图像均可）
        :param object_labels_dir: DOTA格式标注文件目录（.txt）
        :param background_images_dir: 背景图像目录
        :param output_images_dir: 生成图像保存目录
        :param output_labels_dir: 生成DOTA标注保存目录
        :param num_generated: 生成图像总数
        :param min_objects: 单图最小目标数
        :param max_objects: 单图最大目标数
        """
        self.object_images_dir = object_images_dir
        self.object_labels_dir = object_labels_dir
        self.background_images_dir = background_images_dir
        self.output_images_dir = output_images_dir
        self.output_labels_dir = output_labels_dir
        self.num_generated = num_generated
        self.min_objects = min_objects
        self.max_objects = max_objects
        
        # 类别映射（自动从标注中提取，类别名→ID）
        self.class_map = {}
        self.class_names = []
        
        # 验证输入目录
        self._validate_input_dirs()
        
        # 加载目标（图像+DOTA标注信息）
        self.objects = self._load_dota_objects()
        
        # 加载背景图像
        self.backgrounds = self._load_backgrounds()
        
        # 验证加载结果
        self._validate_loaded_data()
        
        print(f"✅ 初始化完成：加载 {len(self.objects)} 个目标 | {len(self.backgrounds)} 张背景 | {len(self.class_map)} 个类别")

    def _validate_input_dirs(self):
        """验证输入目录是否存在"""
        for dir_path in [self.object_images_dir, self.object_labels_dir, self.background_images_dir]:
            if not os.path.exists(dir_path):
                raise ValueError(f"目录不存在：{dir_path}")

    def _load_dota_objects(self):
        """加载DOTA格式的目标（图像+标注）：提取每个目标的四边形顶点和最小外接矩形"""
        objects = []
        image_extensions = ['*.jpg', '*.jpeg', '*.png', '*.bmp', '*.tif']
        
        # 遍历所有目标图像
        for ext in image_extensions:
            for img_path in glob.glob(os.path.join(self.object_images_dir, ext)):
                img_filename = os.path.splitext(os.path.basename(img_path))[0]
                label_path = os.path.join(self.object_labels_dir, f"{img_filename}.txt")
                
                # 跳过无标注的图像
                if not os.path.exists(label_path):
                    print(f"⚠️  跳过无标注图像：{img_path}")
                    continue
                
                # 读取目标图像
                obj_img = cv2.imread(img_path)
                if obj_img is None:
                    print(f"⚠️  跳过无法读取的图像：{img_path}")
                    continue
                img_h, img_w = obj_img.shape[:2]
                
                # 读取DOTA标注文件（格式：x1 y1 x2 y2 x3 y3 x4 y4 类别名 难度）
                with open(label_path, 'r', encoding='utf-8') as f:
                    lines = [line.strip() for line in f if line.strip()]
                
                for line in lines:
                    parts = line.split()
                    # 验证DOTA格式（必须是 8坐标+1类别+1难度 = 10个字段）
                    if len(parts) != 10:
                        print(f"⚠️  标注格式错误（跳过）：{label_path} -> {line}")
                        continue
                    
                    # 解析标注字段
                    coords = list(map(float, parts[:8]))  # 8个顶点坐标（x1,y1,x2,y2,x3,y3,x4,y4）
                    class_name = parts[8]
                    difficulty = int(parts[9])  # 难度系数（0/1）
                    
                    # 转换为整数像素坐标，并构建顶点列表
                    points = [(int(coords[2*i]), int(coords[2*i+1])) for i in range(4)]
                    
                    # 计算目标的最小外接矩形（用于截取目标图像）
                    x_coords = [p[0] for p in points]
                    y_coords = [p[1] for p in points]
                    xmin = max(0, min(x_coords))
                    ymin = max(0, min(y_coords))
                    xmax = min(img_w - 1, max(x_coords))
                    ymax = min(img_h - 1, max(y_coords))
                    
                    # 验证外接矩形有效性
                    if xmax - xmin <= 5 or ymax - ymin <= 5:  # 过滤过小目标
                        print(f"⚠️  跳过过小目标：{label_path} -> {class_name}")
                        continue
                    
                    # 截取目标图像（从原始图像中裁剪出目标区域）
                    crop_img = obj_img[ymin:ymax, xmin:xmax]
                    
                    # 调整顶点坐标为「相对于裁剪后图像」的坐标（后续粘贴时偏移用）
                    adjusted_points = [(x - xmin, y - ymin) for x, y in points]
                    
                    # 构建类别映射（自动分配类别ID）
                    if class_name not in self.class_map:
                        self.class_map[class_name] = len(self.class_map)
                        self.class_names.append(class_name)
                    
                    # 保存目标信息
                    objects.append({
                        'crop_img': crop_img,  # 裁剪后的目标图像
                        'class_name': class_name,  # 类别名（DOTA格式）
                        'difficulty': difficulty,  # 难度系数
                        'adjusted_points': adjusted_points,  # 相对于裁剪图的顶点坐标
                        'crop_w': xmax - xmin,  # 裁剪图宽度
                        'crop_h': ymax - ymin   # 裁剪图高度
                    })
        
        return objects

    def _load_backgrounds(self):
        """加载背景图像（支持多种格式）"""
        backgrounds = []
        image_extensions = ['*.jpg', '*.jpeg', '*.png', '*.bmp', '*.tif']
        
        for ext in image_extensions:
            for bg_path in glob.glob(os.path.join(self.background_images_dir, ext)):
                bg_img = cv2.imread(bg_path)
                if bg_img is not None:
                    backgrounds.append(bg_img)
        
        return backgrounds

    def _validate_loaded_data(self):
        """验证加载的目标和背景是否有效"""
        if not self.objects:
            raise ValueError("未加载到任何有效目标（检查图像和标注格式）")
        if not self.backgrounds:
            raise ValueError("未加载到任何背景图像")

    def generate_images(self):
        """生成合成图像和对应的DOTA格式标注"""
        for img_idx in range(self.num_generated):
            # 随机选择背景图并复制（避免修改原始背景）
            bg_img = random.choice(self.backgrounds).copy()
            bg_h, bg_w = bg_img.shape[:2]
            
            # 随机选择当前图像的目标数量
            num_objs = random.randint(self.min_objects, self.max_objects)
            
            # 存储当前图像的DOTA标注
            dota_labels = []
            
            for _ in range(num_objs):
                # 随机选择一个目标
                obj = random.choice(self.objects)
                crop_img = obj['crop_img'].copy()
                crop_w, crop_h = obj['crop_w'], obj['crop_h']
                
                # 确保目标不会超出背景边界（计算最大可放置位置）
                max_x = bg_w - crop_w - 1
                max_y = bg_h - crop_h - 1
                if max_x <= 0 or max_y <= 0:
                    continue  # 目标过大，跳过
                
                # 随机选择目标在背景中的放置位置（左上角坐标）
                paste_x = random.randint(0, max_x)
                paste_y = random.randint(0, max_y)
                
                # 将目标粘贴到背景图（直接覆盖，如需透明可修改掩码逻辑）
                bg_img[paste_y:paste_y+crop_h, paste_x:paste_x+crop_w] = crop_img
                
                # 计算目标在背景图中的最终顶点坐标（调整坐标+粘贴偏移）
                final_points = [(paste_x + p[0], paste_y + p[1]) for p in obj['adjusted_points']]
                
                # 转换为DOTA格式的字符串（8个坐标保留3位小数）
                final_coords = []
                for (x, y) in final_points:
                    final_coords.append(f"{x:.3f}")
                    final_coords.append(f"{y:.3f}")
                coords_str = ' '.join(final_coords)
                
                # 构建DOTA标注行（坐标+类别名+难度）
                label_line = f"{coords_str} {obj['class_name']} {obj['difficulty']}"
                dota_labels.append(label_line)
            
            # 保存生成的图像
            img_save_path = os.path.join(self.output_images_dir, f"generated_{img_idx:06d}.jpg")
            cv2.imwrite(img_save_path, bg_img, [cv2.IMWRITE_JPEG_QUALITY, 95])
            
            # 保存DOTA格式标注文件（与图像同名.txt）
            label_save_path = os.path.join(self.output_labels_dir, f"generated_{img_idx:06d}.txt")
            with open(label_save_path, 'w', encoding='utf-8') as f:
                f.write('\n'.join(dota_labels))
            
            # 打印进度（每100张输出一次）
            if (img_idx + 1) % 100 == 0:
                print(f"�� 已生成 {img_idx + 1}/{self.num_generated} 张图像")
        
        print(f"\n�� 生成完成！")
        print(f"�� 生成图像：{self.output_images_dir}")
        print(f"�� 生成标注：{self.output_labels_dir}")
        print(f"�� 类别统计：{self.class_map}")

if __name__ == "__main__":
    # 解析命令行参数
    if len(sys.argv) < 5:
        print("Usage: script.py obj_img_dir obj_lbl_dir output_img_dir output_lbl_dir [bg_dir] [num_gen] [min_obj] [max_obj]")
        print("参数说明：")
        print("  obj_img_dir   - 目标图像目录（待粘贴的目标图像）")
        print("  obj_lbl_dir   - DOTA格式标注目录（与目标图像同名.txt）")
        print("  output_img_dir- 生成图像输出目录")
        print("  output_lbl_dir- 生成DOTA标注输出目录")
        print("  bg_dir        - 背景图像目录（必填额外参数）")
        print("  num_gen       - 生成图像数量（默认3000）")
        print("  min_obj       - 单图最小目标数（默认1）")
        print("  max_obj       - 单图最大目标数（默认5）")
        sys.exit(1)
    
    # 必要参数（固定顺序）
    obj_img_dir = sys.argv[1]    # 目标图像输入目录
    obj_lbl_dir = sys.argv[2]    # DOTA标注输入目录
    output_img_dir = sys.argv[3] # 生成图像输出目录
    output_lbl_dir = sys.argv[4] # 生成标注输出目录
    extra_params = sys.argv[5:]  # 额外参数
    
    # 创建输出目录（自动忽略已存在目录）
    os.makedirs(output_img_dir, exist_ok=True)
    os.makedirs(output_lbl_dir, exist_ok=True)
    
    # 解析额外参数（顺序：背景目录→生成数量→最小目标数→最大目标数）
    if len(extra_params) < 1:
        print("❌ 错误：必须提供背景图像目录作为第5个参数")
        sys.exit(1)
    bg_dir = extra_params[0]
    num_generated = int(extra_params[1]) if len(extra_params) > 1 else 3000
    min_objects = int(extra_params[2]) if len(extra_params) > 2 else 1
    max_objects = int(extra_params[3]) if len(extra_params) > 3 else 5
    
    # 验证参数有效性
    if min_objects < 1:
        print("⚠️  警告：最小目标数不能小于1，已自动修正为1")
        min_objects = 1
    if max_objects < min_objects:
        print("⚠️  警告：最大目标数不能小于最小目标数，已自动修正为最小目标数")
        max_objects = min_objects
    
    # 启动生成器
    try:
        generator = DOTAGenerator(
            object_images_dir=obj_img_dir,
            object_labels_dir=obj_lbl_dir,
            background_images_dir=bg_dir,
            output_images_dir=output_img_dir,
            output_labels_dir=output_lbl_dir,
            num_generated=num_generated,
            min_objects=min_objects,
            max_objects=max_objects
        )
        generator.generate_images()
        print("\n✅ 脚本执行成功！")
    except Exception as e:
        print(f"\n❌ 脚本执行失败：{str(e)}")
        sys.exit(1)
