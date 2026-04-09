<template>
	<v-ace-editor
	  v-model:value="modelValue"
	  :lang="lang"
	  :theme="theme"
	  :options="mergedOptions"
	  :height="computedHeight"
	  width="100%"
	  ref="aceRef"
	  class="vue-ace-editor"
	  @update:value="handleInput"
	/>
  </template>
  
  <script setup>
  import { ref, watch, computed, onUnmounted } from "vue";
  import { VAceEditor } from "vue3-ace-editor";
  import { debounce } from 'lodash';
  import './aceConfig';
  
  const props = defineProps({
	modelValue: {
	  type: String,
	  default: ''
	},
	height: {
	  type: [Number, String],
	  default: 0
	},
	readOnly: {
	  type: Boolean,
	  default: false
	},
	theme: {
	  type: String,
	  default: 'monokai'
	},
	lang: {
	  type: String,
	  default: 'json'
	},
	options: {
	  type: Object,
	  default: () => ({})
	}
  });
  
  const emit = defineEmits(["update:modelValue","change"]);
  
  const aceRef = ref();
  const modelValue = ref(props.modelValue);
  
  const defaultOptions = {
	tabSize: 4,
	showPrintMargin: true,
	fontSize: 15,
	highlightActiveLine: true,
	enableBasicAutocompletion: true,
	enableSnippets: true,
	enableLiveAutocompletion: true,
	useWorker: true,
	readOnly: props.readOnly,
  };
  
  const mergedOptions = computed(() => ({
	...defaultOptions,
	...props.options,
	readOnly: props.readOnly,
  }));
  
  const computedHeight = computed(() => 
	typeof props.height === 'number' ? `${props.height}px` : props.height
  );
  
  watch(() => props.modelValue, (newVal) => {
	if (newVal !== modelValue.value) {
	  modelValue.value = newVal;
	}
  });
  
  watch(() => props.theme, (newTheme) => {
	const editor = aceRef.value?.editor;
	if (editor) editor.setTheme(`ace/theme/${newTheme}`);
  });
  
  watch(() => props.lang, (newLang) => {
	const session = aceRef.value?.editor?.session;
	console.log(newLang,'newLang')
	if (session) session.setMode(`ace/mode/${newLang}`);
  });
  const handleInput = debounce((value) => {
	emit('update:modelValue', value);
	emit('change', value);
  }, 300 );
  
  const checkAce = () => {
	const instance = aceRef.value?.getAceInstance?.();
	if (!instance) return false;
	const annotations = instance.session.getAnnotations();
	return annotations.some(anno => anno.type === 'error');
  };
  

  onUnmounted(() => {
	handleInput.cancel()
  });
  
  defineExpose({ checkAce});
  </script>
  
  <style lang="scss" scoped>
  .vue-ace-editor {
	width: 100%;
	height: 100%;
  }
  </style>