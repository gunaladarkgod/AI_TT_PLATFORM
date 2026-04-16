import { request } from './axios'

/**登录接口 */
export class AuthService {
    static async login(params) {
        return request('/auth/login', params, 'post');
    }
    static async logout() {
        return request('/auth/logout', {}, 'post');
    }
}

/**用户管理接口 */
export class UserService {
    static async queryList(params) {
        return request('/user/list', params, 'post');
    }
    static async add(params) {
        return request('/user/add', params, 'post');
    }
    static async update(params) {
        return request('/user/update', params, 'post');
    }
    static async delete(params) {
        return request('/user/del', params, 'post');
    }
    static async updatePwd(params) {
        return request('/user/pwd', params, 'post');
    }
    static async resetPwd(params) {
        return request('/user/reset', params, 'post');
    }
}

/**用户-项目管理接口 */
export class UserProjectService {
    //获取用户关联的项目列表
    static async getUserProject(params) {
        return request('/userProject/query', params, 'post');
    }
    //保存用户-项目关联关系
    static async savaUserProject(params) {
        return request('/userProject/save', params, 'post');
    }
}



/**标注项目管理接口 */
export class EngineProjectService {
    static async queryAll(params) {
        return request('/engineProject/all', params, 'post');
    }
    // 查询所有,不含标签信息
    static async getProjectAll(params) {
        return request('/engineProject/all2', params, 'post');
    }
    static async queryList(params) {
        return request('/engineProject/list', params, 'post');
    }
    static async add(params) {
        return request('/engineProject/add', params, 'post');
    }
    static async update(params) {
        return request('/engineProject/update', params, 'post');
    }
    static async delete(params) {
        return request('/engineProject/del', params, 'post');
    }
    static async syncOne(params) {
        return request('/engineProject/sync/one', params, 'post');
    }
    static async syncAll(params) {
        return request('/engineProject/sync/all', params, 'post');
    }
    static async queryDistinct(params) {
        return request('/engineProject/distinct', params, 'post'); 
    }
    static async pullMissing(params) {
        return request('/engineProject/pull/missing', params, 'post', 'application/json')
    }
}

/**标注项目管理接口 */
export class EngineTaskService {
    static async queryAll(params) {
        return request('/engineTask/all', params, 'post');
    }
    static async queryList(params) {
        return request('/engineTask/list', params, 'post');
    }
    static async add(params) {
        return request('/engineTask/add', params, 'post');
    }
    static async update(params) {
        return request('/engineTask/update', params, 'post');
    }
    static async delete(params) {
        return request('/engineTask/del', params, 'post');
    }
    // static async exportDataset(params) {
    //     return request('/engineTask/export/dataset', params, 'post');
    // }
    static async exportDataset(params) {
        return request('/engineTask/export/enqueue', params, 'post');
    }
    static async exportCancel(params) {
        return request('/engineTask/export/cancel', params, 'post');
    }
    static async exportTop(params) {
        return request('/engineTask/export/top', params, 'post');
    }
    static async syncOneTask(params) {
        return request('/engineTask/sync/one', params, 'post');
    }
    static async queryDistinct(params) {
        return request('/engineTask/distinct', params, 'post');
    }
    static async cleartDelFlg(params) {
        return request('/engineTask/clear/del', params, 'post');
    }
    static async startDataTrans(params) {
        return request('/engineTask/trans', params, 'post','application/json;chartset=utf-8');
    }
}
/**训练标签管理 */
export class TrainLabelService {
    static async queryAll(params) {
        return request('/trainLabel/all', params, 'post');
    }
    static async add(params) {
        return request('/trainLabel/add', params, 'post');
    }
    static async update(params) {
        return request('/trainLabel/update', params, 'post');
    }
    static async delete(params) {
        return request('/trainLabel/del', params, 'post');
    }
    static async syncAll(params) {
        return request('/trainLabel/sync/all', params, 'post');
    }
}
/**训练任务管理 */
export class TrainTaskService {
    static async queryList(params) {
        return request('/trainTask/list', params, 'post');
    }
    static async add(params) {
        return request('/trainTask/add', params, 'post', 'multipart/form-data;chartset=utf-8');
    }
    static async addMMD(params) {
        return request('/trainTask/pack', params, 'post', 'multipart/form-data;chartset=utf-8');
    }
    static async update(params) {
        return request('/trainTask/update', params, 'post', 'multipart/form-data;chartset=utf-8');
    }
    static async queryArgs(params) {
        return request('/trainTask/args/query', params, 'post');
    }
    static async queryData(params) {
        return request('/trainTask/data/query', params, 'post');
    }
    static async delete(params) {
        return request('/trainTask/del', params, 'post');
    }
    static async enqueue(params) {
        return request('/trainTask/enqueue', params, 'post');
    }
    static async runnerHealth(params) {
        return request('/api/runner/health', params || {}, 'post');
    }
    static async stop(params) {
        return request('/trainTask/stop', params, 'post');
    }
    static async topTask(params) {
        return request('/trainTask/top', params, 'post');
    }
    static async cancelTask(params) {
        return request('/trainTask/cancel', params, 'post');
    }
    static async queryUsers(params) {
        return request('/trainTask/user', params, 'post');
    }
    static async getExtQuery(params) {
        return request('/trainTask/ext/query', params, 'post');
    }
    static async clearEpoches(params) {
        return request('/trainTask/clear/epoches', params, 'post');
    }

    static async addValTask(params) {
        return request('/trainTask/val/add', params, 'post', 'application/json');
    }
    static async addPredictTask(params) {
        return request('/trainTask/predict/add', params, 'post', 'multipart/form-data;chartset=utf-8');
    }

}

/**训练文件管理 */
export class TrainYoloService {
    static async queryList(params) {
        return request('/trainYolo/list', params, 'post');
    }
    static async add(params) {
        return request('/trainYolo/add', params, 'post', "multipart/form-data;chartset=utf-8");
    }
    static async update(params) {
        return request('/trainYolo/update', params, 'post');
    }
    static async delete(params) {
        return request('/trainYolo/del', params, 'post');
    }
}
/**训练文件管理 */
export class TrainScriptService {
    static async queryList(params) {
        return request('/trainScript/list', params, 'post');
    }
    static async queryAll(params) {
        return request('/trainScript/all', params, 'post');
    }
    static async add(params) {
        return request('/trainScript/add', params, 'post');
    }
    static async update(params) {
        return request('/trainScript/update', params, 'post');
    }
    static async delete(params) {
        return request('/trainScript/del', params, 'post');
    }
}

/**模型转换管理 */
export class ModelTransService {
    static async queryList(params) {
        return request('/modelTrans/list', params, 'post');
    }
    static async add(params) {
        return request('/modelTrans/add', params, 'post', "multipart/form-data;chartset=utf-8");
    }
    static async addByTask(params) {
        return request('/modelTrans/addByTask', params, 'post', "multipart/form-data;chartset=utf-8");
    }
    static async update(params) {
        return request('/modelTrans/update', params, 'post', "multipart/form-data;chartset=utf-8");
    }
    static async delete(params) {
        return request('/modelTrans/del', params, 'post');
    }
    static async start(params) {
        return request('/modelTrans/start', params, 'post');
    }
    static async queryCalibrate(params) {
        return request('/modelTrans/calibrate', params, 'post');
    }
}
/**文件管理服务 */
export class FileService {
    static async queryFiles(params) {
        return request('/files/list', params, 'post');
    }
    static async addDir(params) {
        return request('/files/dir/add', params, 'post');
    }
    static async addFile(params) {
        return request('/files/file/add', params, 'post', "multipart/form-data;chartset=utf-8");
    }
    static async delDir(params) {
        return request('/files/dir/del', params, 'post');
    }
    static async delFile(params) {
        return request('/files/file/del', params, 'post');
    }

    static async getLog(params) {
        return request('/files/log', params, 'post');
    }
    static async getFile(url, type) {
        return request(url, null, 'get', null, type);
    }
    static async downZipFile(params) {
        return request('/files/dir/download', params, 'post','application/x-www-form-urlencoded;chartset=utf-8','blob');
    }

}



//菜单管理接口
export class MenuService {
    static async queryList(params) {
        return request('/menu/all', params, 'post');
    }
    static async add(params) {
        return request('/menu/add', params, 'post');
    }
    static async update(params) {
        return request('/menu/update', params, 'post');
    }
    static async delete(params) {
        return request('/menu/del', params, 'post');
    }
    static async roleMenu(params) {
        return request('/menu/role/save', params, 'post');
    }
    static async getRoleMenu(params) {
        return request("/menu/role/list", params, 'post')
    }
    static async getMenuPage(params) {
        return request("/menu/list", params, 'post')
    }
}




//模型训练模板管理
export class trainService {
    static async allTrain(params) {
        return request('/profile_train/all', params, 'post');
    }
    static async add(params) {
        return request('/profile_train/add', params, 'post');
    }
    static async update(params) {
        return request('/profile_train/update', params, 'post');
    }
    static async delete(params) {
        return request('/profile_train/del', params, 'post');
    }
    static async getIdTrain(params) {
        return request("/profile_train/id", params, 'post')
    }
    static async getTablePage(params) {
        return request("/profile_train/list", params, 'post')
    }
}


//模型转换模板管理
export class transService {
    static async allTrain(params) {
        return request('/profile_trans/all', params, 'post');
    }
    static async add(params) {
        return request('/profile_trans/add', params, 'post');
    }
    static async update(params) {
        return request('/profile_trans/update', params, 'post');
    }
    static async delete(params) {
        return request('/profile_trans/del', params, 'post');
    }
    static async getIdTrans(params) {
        return request("/profile_trans/id", params, 'post')
    }
    static async getTablePage(params) {
        return request("/profile_trans/list", params, 'post')
    }
}


/**任务数据集管理接口 */
export class TaskDatabaseManageService {
    static async queryList(params) {
        return request('/taskDataset/queryTaskDatasetList', params, 'post');
    }

    static async del(params) {
        return request('/taskDataset/del', params, 'post');
    }
}

export class TaskDatasetDevService {
    static async listTasks() {
        return request('/taskDatasetDev/tasks/list', {}, 'post', 'application/json');
    }

    static async createTask(payload) {
        return request('/taskDatasetDev/tasks', payload, 'post', 'application/json');
    }

    static async deleteTask(payload) {
        return request('/taskDatasetDev/tasks/delete', payload, 'post', 'application/json');
    }

    static async updateTask(payload) {
        return request('/taskDatasetDev/tasks/update', payload, 'post', 'application/json');
    }

    static async updateMapping(payload) {
        return request('/taskDatasetDev/tasks/mapping', payload, 'post', 'application/json');
    }

    static async exportTask(payload) {
        return request('/taskDatasetDev/tasks/export', payload, 'post', 'application/json');
    }

    static async clearTask(payload) {
        return request('/taskDatasetDev/tasks/clear', payload, 'post', 'application/json');
    }
}

// /**结果查询管理 */
// export class ResultQueryService {
//     static async queryList(params) {
//         return request('/resultQuery/list', params, 'post');
//     }

// }








/**公共接口 */
export class ApiService {
    static async getTensorboardUrl(params) {
        return request('/api/tensorboard/url', params, 'post');
    }
    //获取系统的相关信息,用于配置切换
    static async getSysInfo(params) {
        return request('/api/sys', params, 'post');
    }
}

export class OriginalDatasetService {
  static async list(params) {
    // 约定入参：{ page:1, size:1000, keyword:'', sensorType:'', targetType:'', sortBy:'created_time', order:'desc' }
    return request('/original-dataset', params, 'get', 'application/json')
  }

  static async listExternal() {
    return request('/original-dataset/external', {}, 'get', 'application/json')
  }

  static async validateExternal(payload) {
    return request('/original-dataset/external/validate', payload, 'post', 'application/json')
  }

  static async pickExternalDir() {
    return request('/original-dataset/external/pick-dir', {}, 'post', 'application/json')
  }

  static async importExternal(payload) {
    return request('/original-dataset/external/import', payload, 'post', 'application/json')
  }

  static async deleteExternal(payload) {
    return request('/original-dataset/external/delete', payload, 'post', 'application/json')
  }

  static async randomSample(payload) {
    return request('/original-dataset/sample-random', payload, 'post', 'application/json')
  }

  static async markSubsets(payload) {
    // 后端路由：POST /original-dataset/mark-subsets
    // payload 形如：{ target:[1,2], train:[3,4], fatherName:'任务数据集名称' }
    return request('/original-dataset/mark-subsets', payload, 'post', 'application/json')
  }

  static async preview(id, perLabel = 3) {
    // 后端路由：GET /original-dataset/{id}/preview?perLabel=3
    // 返回形如：{ items: [ { label:'ship', images:[url1,url2,url3] }, ... ] }
    return request(`/original-dataset/${id}/preview`, { perLabel }, 'get', 'application/json')
  }
}

/**模板管理接口 */
export class TemplateService {
    static async list() {
        return request('/api/template/list', {}, 'get');
    }
    static async upload(params) {
        return request('/api/template/upload', params, 'post', 'application/json;charset=UTF-8');
    }
}
// 任务数据集合并流程接口
export class TaskDatasetMergeService {
    static async getSubsetsInfo(params) {
        return request('/taskDataset/subsets-info', params, 'get');
    }

    static async uploadTemplate(formData) {
        return request('/taskDataset/upload-template', formData, 'post', 'multipart/form-data');
    }

    static async mergeTarget(params) {
        return request('/taskDataset/merge-target', params, 'post', 'application/json;charset=UTF-8');
    }

    static async mergePretrain(params) {
        return request('/taskDataset/merge-pretrain', params, 'post', 'application/json;charset=UTF-8');
    }
    // 新增：任务数据集子集预览
    static async previewSubset(taskDatasetId, subset, perLabel = 3) {
        return request(
            `/taskDataset/${taskDatasetId}/subset-preview`,
            { subset, perLabel },
            'get',
            'application/json'
        );
    }
    
}
/**
 * 中间实例数据集服务（用于创建实例数据集时选择预处理源）
 */
export class SourceInstanceDatasetService {
  /**
   * @param {{ presentOnDisk?: boolean }} [opts] presentOnDisk 为 true 时仅返回磁盘路径完整、含训练样本的中间实例数据集
   */
  static async list(opts = {}) {
    const q = opts.presentOnDisk ? '?presentOnDisk=true' : ''
    const res = await request(`/instance-mid/sourcedatasets${q}`, {}, 'get')
    return res // ✅ 返回完整 { code, message, data }
  }
}

/**
 * 实例数据集管理接口
 */
export class InstanceDatasetService {
    static async queryList() {
        const res = await request('/instance/instancedatasets', {}, 'post');
        return res.data || [];
    }


    static async getNames() {
        return request('/instance/getNames', {}, 'post');
    }

    /** MMDet 等创建训练任务：仅返回磁盘可用、含类别配置的实例数据集名称 */
    static async getTrainableNames() {
        return request('/instance/getTrainableNames', {}, 'post');
    }


    // /**
    //  * 修改：预处理请求体格式（不再需要 instanceDatasetName 和 taskDatasetId）
    //  * @param {Object} params - 请求参数
    //  * @param {number[]} params.sourceInstanceIds - 中间实例数据集 ID 列表
    //  * @param {number} [params.enhanceScriptId] - 增强脚本 ID
    //  * @param {Object} [params.enhanceParams] - 增强参数
    //  * @param {number} [params.augmentScriptId] - 增广脚本 ID
    //  * @param {Object} [params.augmentParams] - 增广参数
    //  */
    static async runPreprocess(params) {
        // 确保 Content-Type 为 JSON
        return request('/api/preprocess/run', params, 'post', 'application/json;charset=utf-8');
    }

     // ========== 新增：删除实例数据集 ==========
 static async deleteById(id) {
    //  现在可以正常调用 request
    return request(`/instance/instancedatasets/${id}`, null, 'delete', 'application/json');
  }
}

export class PreprocessScriptService {
    // 使用正确的接口路径
    static async getAugmentationScripts(params = {}) {
        // 根据后端代码，type=1 是增广脚本
        return request('/preprocess/scripts', { type: 1, ...params }, 'get');
    }
    
    static async getEnhancementScripts(params = {}) {
        // 根据后端代码，type=0 是增强脚本
        return request('/preprocess/scripts', { type: 0, ...params }, 'get');
    }
}


// 改为导入新函数
import { uploadRequest } from './axios'

export function uploadScript(data) {
  return uploadRequest('/preprocess/upload', data);
}

/**任务数据集管理接口 */
export class TaskDatasetService {
    // 获取任务数据集分页列表
    static async queryList(params) {
        return request('/instance/taskdatasets', params || {}, 'post','application/json;charset=UTF-8','json'); 
    }
    
    // 获取目标子集的小数据集列表
    static async getTargetSubsets(taskId) {
        return request(`/instance/targetSubsets?taskId=${taskId}`, {}, 'get', null, 'json');
    }

    // 获取预训练子集
    static async getPretrainDatasets(fatherName) {
        return request(`/instance/pretrainDatasets?fatherName=${fatherName}`, {}, 'get', null, 'json');
    }

    // 保存测试集划分结果
    static async saveTrainTestSplit(params) {
        return request('/instance/trainTestSplit/save', params, 'post', 'application/json;charset=UTF-8', 'json');
    }
}

/**结果查询管理 */
export class ResultQueryService {
    static async queryList(params) {
        return request('/trainResult/all', params, 'post');
    }
}