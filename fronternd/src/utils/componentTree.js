const baselinePlaceholders = ['YOLOv5', 'YOLOv8', 'YOLO11', 'DETR']

// Match explicit baseline IDs within the same direction; never infer ownership from names.
export function buildComponentTree(direction, baselines, algorithms) {
  const rows = baselines.filter(item => item.direction === direction).map(item => ({
    ...item,
    nodeType: 'baseline',
    treeKey: `baseline:${item.id}`,
    children: [],
  }))
  const byId = new Map(rows.map(row => [row.id, row]))
  const unlinked = []
  for (const item of algorithms.filter(item => item.direction === direction)) {
    const row = {
      ...item,
      nodeType: 'algorithm',
      treeKey: `${item.custom ? 'custom' : 'algorithm'}:${item.id}`,
    }
    const parent = byId.get(item.baselineId)
    if (parent) parent.children.push(row)
    else unlinked.push(row)
  }
  for (const name of baselinePlaceholders) {
    if (rows.some(row => row.name?.toLowerCase().includes(name.toLowerCase()))) continue
    rows.push({
      id: `${direction}/placeholder-${name}`,
      treeKey: `placeholder:${direction}:${name}`,
      name,
      direction,
      nodeType: 'baseline',
      placeholder: true,
      registrationStatus: '待登记',
      localStatus: 'not-downloaded',
    })
  }
  if (unlinked.length) rows.push({
    treeKey: `unlinked:${direction}`,
    name: '未关联基线',
    nodeType: 'group',
    children: unlinked,
  })
  return rows
}
