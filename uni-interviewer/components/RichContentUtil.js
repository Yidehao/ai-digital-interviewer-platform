// Regular expression variable
var graceRichTextReg;
 
// Batch replacement styles [ Set according to project requirements ]
var GRT = [
	// div style
	['div', "line-height:2em;"],
	// h1 style
	['h1', "font-size:3em; line-height:1.5em;"],
	// h2 style
	['h2', "font-size:2em; line-height:1.8em;"],
	// h3 style
	['h3', "font-size:1.6em; line-height:2em;"],
	// h4 style
	['h4', "font-size:1.2em; line-height:2em;"],
	// h5 style
	['h5', "font-size:1em; line-height:2em;"],
	// h6 style
	['h6', "font-size:0.9em; line-height:2em;"],
	// p style
	['p', "font-size:1em; line-height:2em;"],
	// b style
	['b', "font-size:1em; line-height:2em;"],
	// strong style
	['strong', "font-size:1em; line-height:2em;"],
	// code style
	['code', "font-size:1em; line-height:1.2em; background:#F6F7F8; padding:8px 2%; width:96%;"],
	// img style
	['img', "width:100%; margin:8px 0;"],
	// blockquote
	['blockquote', "font-size:1em; border-left:3px solid #D1D1D1; line-height:2em; border-radius:5px; background:#F6F7F8; padding:8px 2%;"],
	// li style
	['ul', "padding:5px 0; list-style:none; padding:0; margin:0;"],
	['li', "line-height:1.5em; padding:5px 0; list-style:none; padding:0; margin:0; margin-top:10px;"],
	// table
	['table', "width:100%; border-left:1px solid #F2F3F4; border-top:1px solid #F2F3F4;"],
	['th', "border-right:1px solid #F2F3F4; border-bottom:1px solid #F2F3F4;"],
	['td', "border-right:1px solid #F2F3F4; border-bottom:1px solid #F2F3F4; padding-left:5px;"]
];
 
 
module.exports = {
	format : function(html){
		html = html.replace(/<pre.*pre>?/gis, function(word){
			word =  word.replace(/[\n]/gi,'<br />');
			word =  word.replace(/    /gi,'<span style="padding-left:2em;"></span>');
			return word.replace(/[\t]/gi, '<span style="padding-left:2em;"></span>');
		});
		html = html.replace(/<pre/gi, '<p style="font-size:1em; margin:12px 0; line-height:1.2em; background:#F6F7F8; border-radius:5px; padding:8px 4%; width:92%;"');
		html = html.replace(/<\/pre/gi,"</p");
		for(let i = 0; i < GRT.length; i++){
			graceRichTextReg = new RegExp('<'+GRT[i][0]+'>|<'+GRT[i][0]+' (.*?)>', 'gi');
			html = html.replace(graceRichTextReg , function(word){
				// Check if DOM has style=""
				if(word.indexOf('style=') != -1){
					var regIn = new RegExp('<' + GRT[i][0] + '(.*?)style="(.*?)"(.*?)(/?)>', 'gi');
					return word.replace(regIn, '<'+ GRT[i][0] +'$1style="$2 ' + GRT[i][1] +'"$3$4>');
				}else{
					var regIn = new RegExp('<' + GRT[i][0] + '(.*?)(/?)>', 'gi');
					return word.replace(regIn, '<'+ GRT[i][0] +'$1 style="' + GRT[i][1] +'$2">');
				}
			});
		}
		return html;
	}
	
}
