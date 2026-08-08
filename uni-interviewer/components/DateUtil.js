const DateUtil = {};

DateUtil.formatCompareDatetime = function(nowTimeStr, msgLocalDateTime) {
	// console.log("nowTimeStr = " + nowTimeStr);
	// console.log("msgLocalDateTime = " + msgLocalDateTime);
	// If within 1 minute, don't display, indicating active chat interaction, similar to WeChat
	var lastTimestamp = Date.parse(msgLocalDateTime)/1000;	// Previous message time
	var nowTimeTimestamp = Date.parse(nowTimeStr)/1000;		// Current time
	
	// console.log("lastTimestamp = " + lastTimestamp);
	// console.log("nowTimeTimestamp = " + nowTimeTimestamp);
	
	var timestampDiff = nowTimeTimestamp - lastTimestamp; // Seconds difference between previous timestamp and current timestamp
	// console.log("timestampDiff = " + timestampDiff);
	if ( timestampDiff < 60 ) {
		return null;
	} else {
		return this.formatWeekDatetime(nowTimeStr);
	}
}

// Within 7 days, format as Today, Yesterday, Monday, Tuesday, Wednesday, Thursday, Friday
// Over 7 days, display formatted yyyy-dd-mm hh-MM-ss
DateUtil.formatWeekDatetime = function(strDateTime) {
	function zeroize(num) {
		return (String(num).length == 1 ? '0' : '') + num;
	}
		
	// var pendingDateTime = new Date(strDateTime);
	var timestamp = Date.parse(strDateTime)/1000;
	// console.log("timestamp = " + timestamp);
	
	var curTimestamp = parseInt(new Date().getTime() / 1000); // Current timestamp
	var timestampDiff = curTimestamp - timestamp; // Seconds difference between parameter timestamp and current timestamp
	
	var curDate = new Date( curTimestamp * 1000 ); // Current time date object
	var tmDate = new Date( timestamp * 1000 );  // Date object converted from parameter timestamp
	
	var Y = tmDate.getFullYear(), m = tmDate.getMonth() + 1, d = tmDate.getDate();
	var H = tmDate.getHours(), i = tmDate.getMinutes(), s = tmDate.getSeconds();
		
	
	// console.log("pendingDateTime = " + pendingDateTime);
	// var pendingDateTimeStr = this.formatDateToStr(pendingDateTime);
	// console.log("pendingDateTimeStr = " + pendingDateTimeStr);
	if ( timestampDiff < 60 ) { // Within one minute
		
		return 'Today ' + zeroize(H) + ':' + zeroize(i);
		// return "Just now";
	} else if( timestampDiff < 3600 ) { // Within one hour
		return 'Today ' + zeroize(H) + ':' + zeroize(i);
		// return Math.floor( timestampDiff / 60 ) + " minutes ago";
	} else if ( curDate.getFullYear() == Y && curDate.getMonth()+1 == m && curDate.getDate() == d ) {
		return 'Today ' + zeroize(H) + ':' + zeroize(i);
	} else {
        var newDate = new Date( (curTimestamp - 86400) * 1000 ); // Date object converted from timestamp plus one day in parameter
        if ( newDate.getFullYear() == Y && newDate.getMonth()+1 == m && newDate.getDate() == d ) {
            return 'Yesterday ' + zeroize(H) + ':' + zeroize(i);
        } else if ( curDate.getFullYear() == Y ) {
            return  zeroize(m) + '/' + zeroize(d) + ' ' + zeroize(H) + ':' + zeroize(i);
        } else {
            return  Y + '/' + zeroize(m) + '/' + zeroize(d) + ' ' + zeroize(H) + ':' + zeroize(i);
        }
    }
}

// Split string date time to get year and time
DateUtil.getDateStr = function(strDateTime) {
    var arr = strDateTime.split(" ");
	console.log("arr[0] = " + arr[0]);
    return arr[0];
}
DateUtil.getTimeStr = function(strDateTime) {
    var arr = strDateTime.split(" ");
	console.log("arr[0] = " + arr[1]);
    return arr[1];
}

// Left pad num with 0 to len length string
DateUtil.addZero = function(num, len) {
    var l = num.toString().length;
    while(l < len) {
        num = "0" + num;
        l++;
    }
    return num;
}

// Format the passed Date as "yyyy/MM/dd HH:mm:ss"
DateUtil.formatDateToStr = function(d){
    var year = d.getFullYear();
    var month = d.getMonth() + 1;
    var day = d.getDate();
    var hours = d.getHours();
    var minutes = d.getMinutes();
    var seconds = d.getSeconds();
    // var milliSeconds = d.getMilliseconds();
    var resStr = year + "/" + this.addZero(month, 2) + "/" + this.addZero(day, 2) + " " + this.addZero(hours,2) + ":" + this.addZero(minutes,2) + ":" + this.addZero(seconds,2);
    return resStr;
}

DateUtil.MINUTE = 1000 * 60;
DateUtil.HOUR = DateUtil.MINUTE * 60;
DateUtil.DAY = DateUtil.HOUR * 24;
DateUtil.WEEK = DateUtil.DAY * 7;
DateUtil.MONTH = DateUtil.WEEK * 4;
DateUtil.YEAR = DateUtil.MONTH * 12;
// Convert time to local date
DateUtil.toLocaleDateString = function(datestr) {
  let date = new Date(datestr);
  let m = date.getMonth() + 1;
  if (m < 10) {
    m = "0" + m;
  }
  let day = date.getDate();
  if (day < 10) {
    day = "0" + day;
  }
  return date.getFullYear() + "-" + m + "-" + day;
};
 
DateUtil.toLocaleMinutString = function(datestr) {
  let date = new Date(datestr);
 
  let m = date.getMonth() + 1;
  if (m < 10) {
    m = "0" + m;
  }
  let day = date.getDate();
  if (day < 10) {
    day = "0" + day;
  }
 
  let h = date.getHours();
  if (h < 10) {
    h = "0" + h;
  }
  let MM = date.getMinutes();
  if (MM < 10) {
    MM = "0" + MM;
  }
  let SS = date.getSeconds();
  if (SS < 10) {
    SS = "0" + SS;
  }
  return `${date.getFullYear()}-${m}-${day} ${h}:${MM}:${SS}`;
};
 
// Convert time to display time, e.g., show how many minutes ago for recent times
DateUtil.toShowTime = function(datestr) {
  // datestr = datestr.replace(new RegExp(/-/gm) ,"/");
  let curdate = new Date();
  let date = new Date(datestr);
  let dt = curdate.getTime() - date.getTime();
  if (dt < DateUtil.HOUR) {
    let MINUTE = dt / DateUtil.MINUTE;
    MINUTE = Math.max(MINUTE, 1);
    MINUTE = Math.floor(MINUTE);
    return MINUTE + " minutes ago";
  }
  if (dt < DateUtil.DAY) {
    let HOUR = dt / DateUtil.HOUR;
    HOUR = Math.max(HOUR, 1);
    HOUR = Math.floor(HOUR);
    return HOUR + " hours ago";
  }
  if (dt < DateUtil.WEEK) {
    let DAY = dt / DateUtil.DAY;
    DAY = Math.max(DAY, 1);
    DAY = Math.floor(DAY);
    return DAY + " days ago";
  }
  return DateUtil.toLocaleMinutString(datestr);
};
 
DateUtil.toShowTimeFormat = function(datestr) {
  datestr = datestr.replace(new RegExp(/-/gm) ,"/");
  let curdate = new Date();
  let date = new Date(datestr);
  let dt = curdate.getTime() - date.getTime();
  if (dt < DateUtil.HOUR) {
    let MINUTE = dt / DateUtil.MINUTE;
    MINUTE = Math.max(MINUTE, 1);
    MINUTE = Math.floor(MINUTE);
    return MINUTE + " minutes ago";
  }
  if (dt < DateUtil.DAY) {
    let HOUR = dt / DateUtil.HOUR;
    HOUR = Math.max(HOUR, 1);
    HOUR = Math.floor(HOUR);
    return HOUR + " hours ago";
  }
  if (dt < DateUtil.WEEK) {
    let DAY = dt / DateUtil.DAY;
    DAY = Math.max(DAY, 1);
    DAY = Math.floor(DAY);
    return DAY + " days ago";
  }
  if (dt < DateUtil.MONTH) {
    let WEEK = dt / DateUtil.WEEK;
    WEEK = Math.max(WEEK, 1);
    WEEK = Math.floor(WEEK);
    return WEEK + " weeks ago";
  }
  if (dt < DateUtil.YEAR) {
    let MONTH = dt / DateUtil.MONTH;
    MONTH = Math.max(MONTH, 1);
    MONTH = Math.floor(MONTH);
    return MONTH + " months ago";
  }
  return DateUtil.toLocaleMinutString(datestr);
};
 
export default DateUtil;