export function getNowFormatDate() { // 获取当前时间
  const date = new Date()

  const seperator1 = '-' // 年月日之间的分隔

  const month = date.getMonth() + 1 < 10 ? '0' + (date.getMonth() + 1) : date.getMonth() + 1 // 获取月,如果小于10,前面补个0

  const strDate = date.getDate() < 10 ? '0' + date.getDate() : date.getDate() // 获取日,如果小于10,前面补个0

  const currentdate = date.getFullYear() + seperator1 + month + seperator1 + strDate // 拼接一下

  return currentdate // 返回
}

export function getNowFormatTime() { // 获取当前时间
  const date = new Date()

  const seperator1 = '-' // 年月日之间的分隔

  const seperator2 = ':' // 时分秒之间的分隔

  const month = date.getMonth() + 1 < 10 ? '0' + (date.getMonth() + 1) : date.getMonth() + 1 // 获取月,如果小于10,前面补个0

  const strDate = date.getDate() < 10 ? '0' + date.getDate() : date.getDate() // 获取日,如果小于10,前面补个0

  const strHours = date.getHours() < 10 ? '0' + date.getHours() : date.getHours() // 获取小时,如果小于10,前面补个0

  const strMinutes = date.getMinutes() < 10 ? '0' + date.getMinutes() : date.getMinutes() // 获取分,如果小于10,前面补个0

  const strSeconds = date.getSeconds() < 10 ? '0' + date.getSeconds() : date.getSeconds() // 获取秒,如果小于10,前面补个0

  const currentdate = date.getFullYear() + seperator1 + month + seperator1 + strDate + ' ' + strHours + seperator2 + strMinutes + seperator2 + strSeconds // 拼接一下

  return currentdate // 返回
}

export function getNowDiffTime(date, diff, unit) {
  if (unit == 'year') {
    date.setFullYear(date.getFullYear() + diff)
  } else if (unit == 'month') {
    date.setMonth(date.getMonth() + diff)
  } else if (unit == 'day') {
    date.setDate(date.getDate() + diff)
  } else if (unit == 'hour') {
    date.setHours(date.getHours() + diff)
  } else if (unit == 'min') {
    date.setMinutes(date.getMinutes() + diff)
  } else if (unit == 'second') {
    date.setSeconds(date.getSeconds() + diff)
  }
  const y = date.getFullYear();
  const m = (date.getMonth() + 1) < 10 ? ("0" + (date.getMonth() + 1)) : (date.getMonth() + 1);
  const d = date.getDate() < 10 ? ("0" + date.getDate()) : date.getDate();
  const h = date.getHours() < 10 ? ('0' + date.getHours()) : date.getHours()
  const f = date.getMinutes() < 10 ? ('0' + date.getMinutes()) : date.getMinutes()
  const s = date.getSeconds() < 10 ? ('0' + date.getseconds()) : date.getSeconds()
  const formatdate = y + '-' + m + '-' + d + " " + h + ":" + f + ":" + s;
  return formatdate;
}

export function delay(duration) {
  setTimeout(() => {
    location.reload()
  }, duration)
}

export function formatTime(time, fmt) {
  if (!time) return ''
  else {
    const date = new Date(time)
    const o = {
      'M+': date.getMonth() + 1,
      'd+': date.getDate(),
      'H+': date.getHours(),
      'm+': date.getMinutes(),
      's+': date.getSeconds(),
      'q+': Math.floor((date.getMonth() + 3) / 3),
      S: date.getMilliseconds()
    }
    if (/(y+)/.test(fmt)) {
      fmt = fmt.replace(
        RegExp.$1,
        (date.getFullYear() + '').substr(4 - RegExp.$1.length)
      )
    }
    for (const k in o) {
      if (new RegExp('(' + k + ')').test(fmt)) {
        fmt = fmt.replace(
          RegExp.$1,
          RegExp.$1.length === 1
            ? o[k]
            : ('00' + o[k]).substr(('' + o[k]).length)
        )
      }
    }
    return fmt
  }
}
export function convertFloat(byteStr) {
  var buffer = str2ArrayBuffer(byteStr, 4);
  var dataView = new DataView(buffer, 0, 4);
  var res = dataView.getFloat32(0);
  if (res < 0.001) {
    return 0;
  }
  return res;
}

// 字符串转为ArrayBuffer对象，参数为字符串
export function str2ArrayBuffer(str, len) {
  var buf = new ArrayBuffer(len);
  var bufView = new Uint8Array(buf);
  for (var i = 0, strLen = str.length; i < strLen; i++) {
    bufView[i] = parseInt(str.substr(i * 2, 2), 16);
  }
  return buf;
}


export function timeIntervalToString(timeInterval) {
  timeInterval = parseInt(timeInterval / 1000);
  var totalMinute = parseInt(timeInterval / 60); //总分钟数
  var day = parseInt(parseInt(totalMinute / 60) / 24);
  var hour = parseInt((totalMinute - day * 24 * 60) / 60);
  var minute = totalMinute - day * 24 * 60 - hour * 60
  var retData = '';
  if (day != 0) {
    retData += day + '天';
  }
  if (hour != 0) {
    retData += hour + '小时';
  }
  if (minute != 0) {
    retData += minute + '分钟';
  }
  // if (minute == 0) {
  //   retData += '1分钟内';
  // }
  if (timeInterval < 60) {
    return timeInterval + "秒";
  }
  return retData;
}

export function getTimeDif(time1, time2) {
  var dif = new Date(time2).getTime() - new Date(time1).getTime()
  return timeIntervalToString(dif)
}


