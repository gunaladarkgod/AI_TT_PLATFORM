

/**
 * @description 设置primary颜色 包括elementplus 及 /vxe ui 框架 
 * */
//颜色处理插件  
import tinycolor from 'tinycolor2'
export const setPrimaryColor = (color) => {
	if (color) {
	  document.documentElement.style.setProperty('--el-color-primary', color);
	  //对应--el-color-primary其他配色（白色混合色  黑色混合色）  用于hover  active 
	  document.documentElement.style.setProperty("--el-color-primary-light-3", tinycolor.mix( color,tinycolor("white"), 30).toHexString());
	  document.documentElement.style.setProperty("--el-color-primary-light-5", tinycolor.mix( color,tinycolor("white"), 50).toHexString());
	  document.documentElement.style.setProperty("--el-color-primary-light-7", tinycolor.mix( color,tinycolor("white"), 70).toHexString());
	  document.documentElement.style.setProperty("--el-color-primary-light-8", tinycolor.mix( color,tinycolor("white"), 80).toHexString());
	  document.documentElement.style.setProperty("--el-color-primary-light-9", tinycolor.mix( color,tinycolor("white"), 90).toHexString());
	  document.documentElement.style.setProperty("--el-color-primary-dark-2", tinycolor.mix( color,tinycolor("block"), 20).toHexString());
	

      document.documentElement.style.setProperty('--vxe-ui-font-primary-color', color)
	  document.documentElement.style.setProperty('--vxe-ui-font-primary-tinge-color', tinycolor(color).lighten(28).toString())
	  document.documentElement.style.setProperty('--vxe-ui-font-primary-lighten-color', tinycolor(color).lighten(6).toString())
	  document.documentElement.style.setProperty('--vxe-ui-font-primary-darken-color', tinycolor(color).darken(12).toString())
	  document.documentElement.style.setProperty('--vxe-ui-font-primary-disabled-color', tinycolor(color).lighten(15).toString())
    
    }
}
