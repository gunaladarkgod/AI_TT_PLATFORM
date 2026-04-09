package com.xgls.web.base;

import java.util.HashMap;
import java.util.regex.Pattern;

/**
 * 字典类
 */
public class CodeMap {
    // 默认分页 页码尺寸
    public final static long PAGE_SIZE_DEFAULT = 10L;
    // 默认起始页
    public final static long PAGE_NO_DEFAULT = 1L;

    // session user
    public final static String CUR_USER = "cur_user";

    // 系统基础账户,不允许删除
    public final static String SYSADMINUSER = "sysadmin";
    // 加密盐
    public final static String XGLS = "xglszm";
    // 默认密码后缀
    public final static String PWD_SUFFIX_DEFAULT = "12345!";

    // 保存在http请求的header中的参数
    public final static String X_ACCESS_TOKEN = "Authorization";

    // 正则表达式
    public static Pattern pattern = Pattern.compile("http://([^:]+):(\\d+)");
    /** 密码 */
    public final static String REGEX_PASSWORD_STRONG1 = "^(?![0-9]+$)(?![^0-9]+$)(?![a-zA-Z]+$)(?![^a-zA-Z]+$)[a-zA-Z0-9\\S]{8,30}$";
    /** 文件命名约束 */
    public final static String RE_SN = "[0-9a-zA-Z_-]+";
    /** 路径约束 */
    public final static String RE_PATH = "[\\\\/0-9a-zA-Z_-]+";
    public final static String RE_EMPTY_PATH = "[\\\\/]*";
    /** 图片 */
    public final static String RE_IMG = "^(jpg|png|jpeg)$";
    /** apps 12,23,34 */
    public final static String RE_IDS = "^\\d+(?:,\\d+)*$";
    /** 模型转换任务的名称 由训练任务创建 */
    public final static String RE_TRANS_NAME = "^train_[0-9]+_exp[0-9]+$";

    public final static String RE_EXP_NAME = "^exp[0-9]+$";

    // 用户状态
    public final static Integer USER_STATUS_LOCK = 0; // 锁定
    public final static Integer USER_STATUS_OK = 1; // 正常

    // 用户分类-涉及到权限- 注意按照
    public final static Integer USER_TYPE_SYS = 1; // 系统管理员
    public final static Integer USER_TYPE_ADMIN = 2; // 管理员 暂时不用
    public final static Integer USER_TYPE_OTHER = 3; // 普通用户

    public final static String ROLE_SYS = "sys"; // 系统管理员
    public final static String ROLE_ADMIN = "admin"; // 管理员 暂时不用
    public final static String ROLE_USER = "user"; // 普通用户

    // 导出的数据格式（按你现有实现保留 URL 编码形式）
    public final static String DATASET_FORMAT = "CVAT for images 1.1";

    public static HashMap<Integer, String> RoleMap = new HashMap<>();
    static {
        RoleMap.put(USER_TYPE_SYS, ROLE_SYS);
        RoleMap.put(USER_TYPE_ADMIN, ROLE_ADMIN);// 暂未使用
        RoleMap.put(USER_TYPE_OTHER, ROLE_USER);
    }

    // ================= 路径/目录常量 =================
    public final static String DIR_SRC = "src";              // 资源根目录
    public final static String DIR_CVAT_TASK = "task";       // CVAT 任务本地落盘根目录（与你现有一致）
    public final static String DIR_YOLO = "yolo";            // yolo配置文件
    public final static String DIR_TRAIN_TASK = "train";     // 训练任务目录
    public final static String DIR_SCRIPT = "script";        // 算法脚本
    public final static String DIR_TEMP = "temp";            // 临时目录
    public final static String DIR_MODEL_TRANS = "model_trans"; // 模型转换任务目录
    public final static String DIR_MODEL_CALIBRATE = "calibrate"; // 校准图片根目录
    public final static String DIR_MODEL_CHECK = "check";    // 校准根目录
    public final static String DIR_MODEL_VAL = "val";        // 验证根目录

    // 任务导出后的子目录（你现有定义，保持不变）
    public final static String DIR_TRAIN_IMAGES = "images";  // 任务导出后的图片目录
    public final static String DIR_TRAIN_FILE = "file";      // 训练任务配置文件
    public final static String DIR_TRAIN_LABELS = "labels";  // 标注文件目录
    public final static String DIR_TRAIN_RUN = "run";        // 训练结果目录
    public final static String DIR_TRAIN_RESULT = "result";  // 训练结果目录

    // ✅ 新增：原始数据集（按 project 汇聚）落盘根
    public final static String DIR_DATA = "data";                          // 数据根
    public final static String DIR_ORIGINAL_DATASET = "original_dataset";  // 原始数据集根
    public final static String DIR_IMAGES = "images";                      // 原始数据集 - 图片目录
    public final static String DIR_ANNOTATIONS = "annotations";            // 原始数据集 - 标注目录

    public final static String DIR_REPORT = "report";       // 报告目录
    public final static String DIR_TEMPLATE = "template";   // 报告模板目录
    public final static String ZIP_SUFF = ".zip";           // 软件压缩包后缀
    public final static String SP_ = "_";                   // 分隔符

    /** 模型转换名称 */
    public final static String ALG_PT_ONNX = "pt2onnx";
    public final static String ALG_PT_RKNN = "pt2rknn";

    /** 数据导出 */
    public final static Integer EXPROTED_YES = 1; // 已导出
    public final static Integer EXPROTED_NO = 0;  // 未导出

    /**
     * 训练任务状态
     */
    public final static Integer TRAIN_TASK_STATUS_DEFAULT = 0; // 默认状态,配置中
    public final static Integer TRAIN_TASK_STATUS_READY = 1;   // 配置完毕
    public final static Integer TRAIN_TASK_STATUS_QUEUE = 2;   // 排队中
    public final static Integer TRAIN_TASK_STATUS_RUN = 3;     // 执行中
    public final static Integer TRAIN_TASK_STATUS_FINISH = 4;  // 成功运行
    public final static Integer TRAIN_TASK_STATUS_CFG_FAIL = 5; // 配置遇到错误

    public final static Integer TRAIN_FINISH_SUCCESS = 0; // 任务正常结束
    public final static Integer TRAIN_FINISH_ERROR = 1;   // 任务异常结束

    /**
     * 模型转换任务状态
     */
    public final static Integer MODEL_TRANS_STATUS_DEFAULT = 0; // 准备好了
    public final static Integer MODEL_TRANS_STATUS_RUN = 3;     // 进行中
    public final static Integer MODEL_TRANS_STATUS_FINISH = 4;  // 结束

    /** 算法脚本类型 */
    public final static String SCRIPT_TYPE_TRAIN = "train";     // 模型训练
    public final static String SCRIPT_TYPE_TRANS = "trans";     // 模型转换
    public final static String SCRIPT_TYPE_DATA = "data";       // 数据集转换
    public final static String SCRIPT_TYPE_VAL = "val";         // 验证
    public final static String SCRIPT_TYPE_PREDICT = "predict"; // 预测

    /** 不存在的标记 */
    public final static Integer CVAT_DEL_FLG = 1; // 标志删除
    public final static Integer CVAT_EXIST_FLG = 0; // 标志存在
    public final static String CVAT_HAS_DEL = "404 Not Found: \"{\"detail\":\"Not found.\"}\""; // 不存在

    /** 数据导出任务状态 */
    public final static Integer EXPORT_STATUS_DEFAULT = 0; // 默认状态
    public final static Integer EXPORT_STATUS_SUCCESS = 1; // 成功结束
    public final static Integer EXPORT_STATUS_QUEUE = 2;   // 排队中
    public final static Integer EXPORT_STATUS_RUN = 3;     // 执行中
    public final static Integer EXPORT_STATUS_FAIL = 4;    // 失败结束

    /** 使用自上传文件ID */
    public final static Integer USE_SELF_ID = 0;
    /** 配置文件类型 */
    public final static String FILE_TYPE_WEIGHT = "weights"; // 模型权重
    public final static String FILE_TYPE_CFG = "cfg";        // 模型配置
    public final static String FILE_TYPE_HYP = "hyp";        // 超参数

    public final static Integer LABEL_IS_MERAGE = 1; // 是合并标签c
    public final static Integer LABEL_IS_ORG = 0;    // 是原始标签

    public final static Integer STATE_READY = 0;   // 可运行
    public final static Integer STATE_RUNNING = 1; // 运行中

    /** 数据集转换 */
    public final static Integer STATE_DATA_TRANS_DEFAULT = 0;  // 默认状态
    public final static Integer STATE_DATA_TRANS_RUNNING = 1;  // 转换中
}
