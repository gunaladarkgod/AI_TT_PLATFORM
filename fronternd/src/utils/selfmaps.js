export const userTypeMap = new Map([
    [1, "系统管理员"],
    [2, "管理员"],
    [3, "普通用户"]
])


export const userTypeList = [
    [1, "系统管理员"],
    [2, "管理员"],
    [3, "普通用户"]
]

export const taskStatusMap = new Map([
    [0, "准备中"],
    [1, "准备完毕"],
    [2, "排队中"],
    [3, "训练中"],
    [4, "已结束"],
    [5, "配置出错"],
])
export const taskStatusList = [
    [-1, "全部状态"],
    [0, "准备中"],
    [1, "准备完毕"],
    [2, "排队中"],
    [3, "训练中"],
    [4, "已结束"],
    [5, "配置出错"],
]

export const perspectiveMap = new Map([
    ['d', "无人机"],
    ['s', "卫星"],
    ['u', "仰视"],
    ['h', "平视"],
])

export const fileTypeMap = new Map([
    ['weights', "权重文件"],
    ['cfg', "模型配置"],
    ['hyp', "超参配置"],
])


export const ScriptTypeList = [
    ['train', "模型训练"],
    ['trans', "模型转换"],
    ['data', "数据集转换"],
]
export const ScriptTypeMap = new Map([
    ['train', "模型训练"],
    ['trans', "模型转换"],
])

export const ExportStatusMap = new Map([
    [0, ''],
    [1, '导入成功'],
    [2, '排队中'],
    [3, '正在导入'],
    [4, '导入失败'],
])




