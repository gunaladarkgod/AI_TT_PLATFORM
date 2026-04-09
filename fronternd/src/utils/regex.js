export function isEN(str) {
    let pattern = /^[A-Za-z0-9_]+$/
    return pattern.test(str)
}

export function isId(str) {
    let pattern = /^[a-z0-9_]+$/
    return pattern.test(str)
}
export function isId2(str) {
    let pattern = /^[A-Za-z0-9_]+$/
    return pattern.test(str)
}

export function isIp(ip) {
    var pattern = /^(\d{1,2}|1\d\d|2[0-4]\d|25[0-5])\.(\d{1,2}|1\d\d|2[0-4]\d|25[0-5])\.(\d{1,2}|1\d\d|2[0-4]\d|25[0-5])\.(\d{1,2}|1\d\d|2[0-4]\d|25[0-5])$/
    return pattern.test(ip);
}
export function isNum(str) {
    var pattern = /^([0-9]+)$/
    return pattern.test(str);
}
