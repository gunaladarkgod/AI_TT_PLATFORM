import axios from "axios";
import qs from 'qs'
import { showMessage } from './status'
import { ElMessage } from 'element-plus'

import { useLoginStore } from '../stores/index'

import { useRouter } from "vue-router";

const router = useRouter()

// 启用代理的开关
let useProxyFlag = process.env.NODE_ENV == 'development';

axios.defaults.baseURL = useProxyFlag ? '/develop' : '';
//
export const baseHost = window.location.host + (useProxyFlag ? '/develop' : '');
export const logoPath = useProxyFlag ? '/imgs/logo_deep.png' : '/dist/imgs/logo_deep.png';

export const nginx_tensorboard = "http://" + baseHost.split(':')[0] + ":";
//资源根据路径 
export const basePath_SRC = "http://" + baseHost + "/src/";
export const basePath_TASK = basePath_SRC + "task/";
export const basePath_YOLO = basePath_SRC + "yolo/";
export const basePath_SCRIPT = basePath_SRC + "script/";
export const basePath_TRAIN = basePath_SRC + "train/";
export const basePath_MODEL_TRANS = basePath_SRC + "model_trans/";

export const basePath_WS_TASK = "ws://" + (useProxyFlag ? "127.0.0.1:8081" : baseHost) + "/working/train/";
export const basePath_WS_TRANS = "ws://" + (useProxyFlag ? "127.0.0.1:8081" : baseHost) + "/working/trans/";
export const basePath_FILE_UPLOAD = "http://" + baseHost + '/files/file/add';
export const basePath_SSE_EXPORT = "http://" + baseHost + '/sse/export';

axios.defaults.withCredentials = true

axios.interceptors.request.use(
    config => {
        const isFormData =
            typeof FormData !== 'undefined' && config.data instanceof FormData;
        const headers = {};
        // FormData 必须由 axios/浏览器设置 multipart boundary；手动写 multipart/form-data 会缺 boundary → 服务端 400
        if (!isFormData && config.contentType) {
            headers['content-type'] = config.contentType;
        }
        if (config.token) {
            headers['Authorization'] = config.token;
        }
        if (config.cacheType) {
            headers['Cache-Control'] = config.cacheType;
        }
        config.headers = headers;
        return config;
    },
    error => {
        return Promise.reject(error)
    }
)

axios.interceptors.response.use(
    response => {
        if (response.status == 200) {
            return normalizeApiResponse(response.data)
        } else {
            return Promise.reject(response.data)
        }
    },
    error => {
        const { response } = error
        if (response) {
            //401 token失效，需要清空缓存的token， 跳转到登录页重新登录
            if (response.status == 401) {
                useLoginStore().$patch((state) => {
                    state.isLogin = false;
                    state.uuidLogin = "";
                    state.token = "";
                });
                //跳转到登录页
                router.replace('/login')
            }

            //这里统一进行出错提示,后面不需要出错拦截了
            if (response.status == 404 && error.config.method?.toLowerCase() == 'get') {
                // get 文件请求弹框不要了
            } else {
                ElMessage.warning(showMessage(response.status))
            }
            return Promise.reject(response.data)
        } else {
            ElMessage.warning('网络连接异常,请稍后再试!')
        }
    }
)

function normalizeApiResponse(payload) {
    if (!payload || typeof payload !== 'object') {
        return payload
    }
    const normalized = { ...payload }
    if (normalized.code === 200) {
        normalized.code = 0
    }
    if (normalized.message && !normalized.msg) {
        normalized.msg = normalized.message
    }
    if (normalized.msg && !normalized.message) {
        normalized.message = normalized.msg
    }
    return normalized
}

export function request(url = '', params = {}, type = 'POST', contentType = 'application/x-www-form-urlencoded;chartset=utf-8', responseType = 'text', onUploadProgress = null) {
    let token = useLoginStore()?.token;
    return new Promise((resolve, reject) => {
        let promise;
        const method = type.toUpperCase();

        if (method === 'GET') {
            promise = axios({
                url: url,
                method: 'get',
                params: params,
                contentType: contentType,
                token: token,
                responseType: responseType,
                cacheType: 'no-cache'
            });
        } else if (method === 'POST') {
            if (contentType === 'application/x-www-form-urlencoded;chartset=utf-8') {
                if (responseType === 'blob') {
                    promise = axios({
                        method: 'post',
                        url: url,
                        data: qs.stringify(params),
                        contentType: contentType,
                        token: token,
                        responseType: responseType,
                    });
                } else {
                    promise = axios({
                        method: 'post',
                        url: url,
                        data: qs.stringify(params),
                        contentType: contentType,
                        token: token,
                    });
                }
            } else if (contentType === 'multipart/form-data;application/x-www-form-urlencoded;chartset=utf-8') {
                promise = axios({
                    method: 'post',
                    url: url,
                    data: params,
                    contentType: contentType,
                    token: token,
                    onUploadProgress: onUploadProgress
                });
            } else {
                promise = axios({
                    method: 'post',
                    url: url,
                    data: params,
                    contentType: contentType,
                    token: token
                });
            }
        } else if (method === 'DELETE') {
            // ✅ 新增：DELETE 分支（不传 data，只传 url + headers）
            promise = axios({
                method: 'delete',
                url: url,
                contentType: contentType,
                token: token
            });
        }

        if (promise) {
            promise.then(res => {
                resolve(res);
            }).catch(err => {
                reject(err);
            });
        } else {
            // 如果 method 不支持，拒绝 Promise
            reject(new Error(`不支持的请求方法: ${type}`));
        }
    });
}

 






export const apiRequest = async (api, params) => {
    try {
        let res = await api(params)
        if (res && (res.code === 0 || res.code === 200)) {
            return res.data
        }
        return []
    } catch (error) {
        console.log(error)
        return []
    }
}



export function uploadRequest(url, formData, onProgress = null) {
    const token = useLoginStore().token;
    return axios({
        method: 'post',
        url,
        data: formData,
        token: token, // ← 关键：传给 interceptors
        onUploadProgress: onProgress
    }).then(res => res.data);
}