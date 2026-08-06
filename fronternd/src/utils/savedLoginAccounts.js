const STORAGE_KEY = 'ai_training_platform_saved_login_accounts_v1'
const MAX_SAVED_ACCOUNTS = 8

function readAccounts() {
  try {
    const value = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]')
    return Array.isArray(value) ? value.filter((item) => item && item.username) : []
  } catch (_) {
    return []
  }
}

function writeAccounts(accounts) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(accounts.slice(0, MAX_SAVED_ACCOUNTS)))
}

export function getSavedLoginAccounts() {
  return readAccounts().sort((a, b) => Number(b.lastUsedAt || 0) - Number(a.lastUsedAt || 0))
}

export function getAutoLoginAccount() {
  return getSavedLoginAccounts().find((account) => account.autoLogin && account.pmd)
}

/**
 * 本地只保存后端登录所需的摘要 pmd，不保存用户输入的明文密码。
 * pmd 与登录令牌一样属于敏感凭据，因此仅适合受信任的个人电脑。
 */
export function saveLoginAccount({ username, pmd, rememberPassword, autoLogin }) {
  const normalizedName = String(username || '').trim()
  if (!normalizedName) return

  const accounts = readAccounts().filter((item) => item.username !== normalizedName)
  accounts.unshift({
    username: normalizedName,
    pmd: rememberPassword ? String(pmd || '') : '',
    rememberPassword: Boolean(rememberPassword),
    autoLogin: Boolean(rememberPassword && autoLogin),
    lastUsedAt: Date.now(),
  })
  writeAccounts(accounts)
}

export function removeSavedLoginAccount(username) {
  writeAccounts(readAccounts().filter((item) => item.username !== username))
}
