# auto_png_dota.py
import os
from osgeo import gdal
import numpy as np
import cv2
import sys
import shutil

def linearmap(low, high):
    mapp = np.linspace(0, 65535, 65536)
    for i in range(0, 65536):
        if i < low:
            mapp[i] = 0
        elif i > high:
            mapp[i] = 255
        else:
            mapp[i] = int((i - low) / (high - low + 0.01) * 255)
    return mapp

def autolevel_m3std(img, control_param):
    hei, wid = img.shape[0], img.shape[1]
    if img.ndim == 3:
        b, g, r = cv2.split(img)
        RedMean = np.mean(r)
        RedStd = np.std(r)
        maxR = RedMean + control_param * RedStd
        GreenMean = np.mean(g)
        GreenStd = np.std(g)
        maxG = GreenMean + control_param * GreenStd
        BlueMean = np.mean(b)
        BlueStd = np.std(b)
        maxB = BlueMean + control_param * BlueStd

        RedMap = linearmap(0, maxR)
        GreenMap = linearmap(0, maxG)
        BlueMap = linearmap(0, maxB)

        imDst = np.zeros((hei, wid, 3), dtype=np.uint8)
        imDst[:, :, 0] = BlueMap[b]
        imDst[:, :, 1] = GreenMap[g]
        imDst[:, :, 2] = RedMap[r]
        return imDst
    else:
        GrayMean = np.mean(img)
        GrayStd = np.std(img)
        maxGray = GrayMean + control_param * GrayStd
        GrayMap = linearmap(0, maxGray)
        return GrayMap[img].astype(np.uint8)

def gdalread(filename):
    gdal.AllRegister()
    dataset = gdal.Open(filename)
    if dataset is None:
        raise RuntimeError(f"Cannot open {filename}")
    im_width = dataset.RasterXSize
    im_height = dataset.RasterYSize
    im_bands = dataset.RasterCount
    im_data = dataset.ReadAsArray(0, 0, im_width, im_height)
    return im_width, im_height, im_bands, im_data

if __name__ == '__main__':
    if len(sys.argv) < 5:
        print("Usage: python auto_png_dota.py <input_image_dir> <input_label_dir> <output_image_dir> <output_label_dir> [control_param]")
        sys.exit(1)

    images_dir = sys.argv[1]
    labels_dir = sys.argv[2]
    out_images_dir = sys.argv[3]
    out_labels_dir = sys.argv[4]

    control_param = 3.0
    if len(sys.argv) > 5:
        try:
            control_param = float(sys.argv[5])
            print(f"[Python] Using control_param: {control_param}")
        except:
            print("[Python] Invalid control_param, using default 3.0")

    os.makedirs(out_images_dir, exist_ok=True)
    os.makedirs(out_labels_dir, exist_ok=True)

    # 复制所有 .txt 标注文件
    label_files = [f for f in os.listdir(labels_dir) if f.lower().endswith('.txt')]
    for lf in label_files:
        src = os.path.join(labels_dir, lf)
        dst = os.path.join(out_labels_dir, lf)
        shutil.copy2(src, dst)
    print(f"Copied {len(label_files)} DOTA label files.")

    # 处理所有图像
    image_files = [f for f in os.listdir(images_dir) if f.lower().endswith(('.png', '.jpg', '.jpeg', '.tif', '.tiff'))]
    for img_file in image_files:
        img_path = os.path.join(images_dir, img_file)
        print(f"Processing {img_file}")
        try:
            im_width, im_height, im_bands, im_data = gdalread(img_path)
        except Exception as e:
            print(f"Skip {img_file}: {e}")
            continue

        if im_bands == 1:
            enhanced = autolevel_m3std(im_data, control_param)
            out_img = np.stack([enhanced] * 3, axis=-1)
        elif im_bands == 3:
            im_data = np.transpose(im_data, (1, 2, 0))
            out_img = autolevel_m3std(im_data, control_param)
        else:
            print(f"Unsupported band count: {im_bands}, skip {img_file}")
            continue

        # 输出为 .png（统一格式）
        base_name = os.path.splitext(img_file)[0]
        out_path = os.path.join(out_images_dir, base_name + ".png")
        cv2.imencode('.png', out_img)[1].tofile(out_path)

    print("DOTA-style auto enhancement finished.")
