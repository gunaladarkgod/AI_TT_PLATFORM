
import { debounce } from "lodash-es";
const doResize = debounce(async (el, binding) => {
	// 获取调用传递过来的数据
	const { value: { bottomOffset = 0, isUse } = {} } = binding;
	if (isUse) {
		const height = window.innerHeight - el.getBoundingClientRect().top - bottomOffset;
		el.style.height = `${height}px`;
	}
}, 0)

export default {
	// 初始化设置
	beforeMount (el, binding) {
		el.resizeListener = async () => {
			await doResize(el, binding);
		}
		el.resizeListener();
		window.addEventListener('resize', el.resizeListener);
	},
	// 销毁时设置
	beforeUnmount (el) {
		// 移除resize监听
		window.removeEventListener('resize', el.resizeListener);
	}
};