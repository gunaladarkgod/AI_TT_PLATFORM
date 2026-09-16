export function profileFailure(error) {
  const status = error?.status || error?.response?.status
  if (status === 401 || error?.code === 2) return '登录凭据已失效，请切换账户或退出后重新登录。后端重启后，旧登录凭据可能已失效。'
  if (status === 404) return '当前后端尚未提供此接口，请重启更新后的后端。'
  if (error?.code === 3) return '当前账号没有用户权限管理权限，请重新登录确认最新等级。'
  if (error?.msg) return error.msg
  return '接口请求失败，请确认后端已更新并运行、前端请求已正确转发，然后重试。'
}
