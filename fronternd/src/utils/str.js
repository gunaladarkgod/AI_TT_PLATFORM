export function showRemark(msg,len) {
    return msg.substring(0, len) + (msg.length > len ? '...' : '')
}