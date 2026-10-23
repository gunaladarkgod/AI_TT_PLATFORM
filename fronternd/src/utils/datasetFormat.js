export const datasetFormatLabel = row => ({ coco: 'coco', yolo: 'yolo', both: 'coco&yolo' }[row?.exportFormat] || '待识别')
export const datasetFormatColor = row => ({ coco: '#409eff', yolo: '#67c23a', both: '#9254de' }[row?.exportFormat] || '#909399')
export const datasetsForEngine = (rows, engine) => rows.map(row => {
  const compatible = engine === 'ultralytics' ? row.yoloAvailable : row.cocoAvailable
  const reasons = [...(row.reasons || [])]
  if (!compatible) reasons.push(engine === 'ultralytics' ? '需要有效的 YOLO 标注与 data.yaml' : '需要有效的 COCO 标注')
  return { ...row, qualified: row.qualified && compatible, reasons }
}).sort((a, b) => Number(b.qualified) - Number(a.qualified) || String(a.name).localeCompare(String(b.name)))
