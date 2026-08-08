// Sound for sending messages
function playSendSound() {
	// // #ifdef APP-PLUS
	// var volumeValue = plus.device.getVolume() // Get current volume
	// if (volumeValue <= 0) {
	// 	return;
	// }
	// // #endif
	
	
	const innerAudioContext = uni.createInnerAudioContext();
	innerAudioContext.autoplay = true;
	innerAudioContext.src = '';  // No local sound file; set remote URL if needed
	innerAudioContext.onPlay(() => {
	});
	innerAudioContext.onStop((res) => {
		innerAudioContext.destroy() 	// Destroy instance after playback completes
	});
	innerAudioContext.onError((res) => {
	});
}

// Sound for receiving messages
function playReceiveSound() {
	// // #ifdef APP-PLUS
	// var volumeValue = plus.device.getVolume() // Get current volume
	// if (volumeValue <= 0) {
	// 	uni.vibrateLong();
	// 	return;
	// }
	// // #endif
	
	// console.log(1111)
	
	const innerAudioContext = uni.createInnerAudioContext();
	innerAudioContext.autoplay = true;
	innerAudioContext.src = '';  // No local sound file; set remote URL if needed
	innerAudioContext.onPlay(() => {
	});
	innerAudioContext.onStop((res) => {
		innerAudioContext.destroy() 	// Destroy instance after playback completes
	});
	innerAudioContext.onError((res) => {
	});
}

module.exports = {
	playSendSound, playReceiveSound
}
