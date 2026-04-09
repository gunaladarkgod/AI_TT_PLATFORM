# rotate_xie_dota.py
import cv2
import numpy as np
import os
import sys

def transform_points_with_M(coords, M):
    """
    使用与 warpAffine 相同的 2x3 仿射矩阵 M 来变换标注点
    coords: [x1, y1, x2, y2, ..., x4, y4]
    返回: [x1', y1', ..., x4', y4']
    """
    pts = np.array(coords, dtype=np.float32).reshape(-1, 2)  # (N, 2)
    ones = np.ones((pts.shape[0], 1), dtype=np.float32)
    pts_h = np.hstack([pts, ones])  # (N, 3)

    # (N, 3) @ (3, 2) = (N, 2)
    rotated = pts_h @ M.T
    return rotated.reshape(-1).tolist()

def rotate_image_and_labels(image, label_lines, angle_deg):
    h, w = image.shape[:2]
    cx, cy = w / 2.0, h / 2.0

    angle_rad = np.deg2rad(angle_deg)
    cos_a = abs(np.cos(angle_rad))
    sin_a = abs(np.sin(angle_rad))

    # 计算新画布大小
    new_w = int(w * cos_a + h * sin_a)
    new_h = int(w * sin_a + h * cos_a)

    # 注意：这个 M 既包含旋转也包含平移（因为后面修改了 M[0,2], M[1,2]）
    M = cv2.getRotationMatrix2D((cx, cy), angle_deg, 1.0)
    M[0, 2] += (new_w - w) / 2
    M[1, 2] += (new_h - h) / 2

    # 旋转图像
    rotated_img = cv2.warpAffine(
        image, M, (new_w, new_h),
        flags=cv2.INTER_LINEAR,
        borderMode=cv2.BORDER_CONSTANT,
        borderValue=(0, 0, 0)
    )

    # 旋转标注：这里和图像用的是同一个 M
    new_label_lines = []
    for line in label_lines:
        parts = line.strip().split()
        if len(parts) < 9:
            continue

        coords = list(map(float, parts[:8]))  # x1 y1 x2 y2 x3 y3 x4 y4
        cls_name = parts[8]
        diff = parts[9] if len(parts) > 9 else '0'

        rotated_coords = transform_points_with_M(coords, M)

        # DOTA 通常是整数，也可以保留一位小数，这里保持你的写法，取整
        rotated_coords = [str(int(round(x))) for x in rotated_coords]
        new_line = ' '.join(rotated_coords + [cls_name, diff])
        new_label_lines.append(new_line)

    return rotated_img, new_label_lines

if __name__ == '__main__':
    if len(sys.argv) < 5:
        print("Usage: python rotate_xie_dota.py <input_image_dir> <input_label_dir> <output_image_dir> <output_label_dir> [angle]")
        sys.exit(1)

    img_dir = sys.argv[1]
    lbl_dir = sys.argv[2]
    out_img_dir = sys.argv[3]
    out_lbl_dir = sys.argv[4]

    angle = 30
    if len(sys.argv) > 5:
        try:
            angle = int(sys.argv[5])
        except:
            pass

    os.makedirs(out_img_dir, exist_ok=True)
    os.makedirs(out_lbl_dir, exist_ok=True)

    # 如果你增强后是 .png，这里建议加上 .tif / .tiff 也无所谓
    image_files = [
        f for f in os.listdir(img_dir)
        if f.lower().endswith(('.png', '.jpg', '.jpeg', '.tif', '.tiff'))
    ]
    processed = 0

    for img_name in image_files:
        base_name = os.path.splitext(img_name)[0]
        lbl_name = base_name + '.txt'
        lbl_path = os.path.join(lbl_dir, lbl_name)

        if not os.path.exists(lbl_path):
            print(f"Label not found for {img_name}, skip.")
            continue

        # 读图像
        img_path = os.path.join(img_dir, img_name)
        image = cv2.imdecode(np.fromfile(img_path, dtype=np.uint8), -1)
        if image is None:
            print(f"Failed to load {img_name}")
            continue

        # 读标注
        with open(lbl_path, 'r', encoding='utf-8') as f:
            label_lines = f.readlines()

        # 旋转
        rotated_img, new_labels = rotate_image_and_labels(image, label_lines, angle)

        # 保存
        #out_img_path = os.path.join(out_img_dir, base_name + '.png')
        #out_lbl_path = os.path.join(out_lbl_dir, lbl_name)
        suffix = f"_rotate{angle}"
        out_img_path = os.path.join(out_img_dir, base_name + suffix + '.png')
        out_lbl_path = os.path.join(out_lbl_dir, base_name + suffix + '.txt')

        cv2.imencode('.png', rotated_img)[1].tofile(out_img_path)

        with open(out_lbl_path, 'w', encoding='utf-8') as f:
            f.write('\n'.join(new_labels) + '\n')

        processed += 1

    print(f"DOTA rotation augmentation finished. Processed {processed} images.")

