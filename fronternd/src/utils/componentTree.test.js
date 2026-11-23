import test from 'node:test'
import assert from 'node:assert/strict'
import { buildComponentTree } from './componentTree.js'

const baseline = { id: 'small-object/yolo11', direction: 'small-object', name: 'YOLO11', kind: 'baseline' }
const tdsc = { id: 'small-object/tdsc', direction: 'small-object', baselineId: baseline.id, name: 'TDSC', kind: 'algorithm' }

test('registered and uploaded algorithms share their explicit baseline, preserving action metadata', () => {
  const uploaded = { ...tdsc, id: baseline.id, custom: true, localStatus: 'uploaded' }
  const rows = buildComponentTree('small-object', [baseline], [tdsc, uploaded])
  const parent = rows.find(row => row.id === baseline.id)
  assert.deepEqual(parent.children.map(row => row.name), ['TDSC', 'TDSC'])
  assert.equal(parent.children[0].kind, 'algorithm')
  assert.equal(parent.children[1].custom, true)
  const keys = [parent.treeKey, ...parent.children.map(row => row.treeKey)]
  assert.equal(new Set(keys).size, keys.length)
  assert.equal(rows.filter(row => row.name === 'YOLO11').length, 1)
  assert.equal(baseline.children, undefined)
  assert.equal(tdsc.treeKey, undefined)
})

test('missing and stale baseline associations remain visible without being assigned by name', () => {
  const algorithms = ['', '待登记', 'small-object/missing'].map((baselineId, index) => ({ ...tdsc, id: `orphan-${index}`, baselineId }))
  const rows = buildComponentTree('small-object', [baseline], algorithms)
  assert.equal(rows[0].children.length, 0)
  assert.deepEqual(rows.find(row => row.nodeType === 'group').children.map(row => row.id), algorithms.map(row => row.id))
})

test('directions stay isolated, including invalid cross-direction baseline links', () => {
  const foreign = { ...baseline, id: 'knowledge-distillation/yolo11', direction: 'knowledge-distillation' }
  const rows = buildComponentTree('small-object', [baseline, foreign], [
    tdsc,
    { ...tdsc, id: 'foreign-algorithm', direction: foreign.direction },
    { ...tdsc, id: 'cross-direction', baselineId: foreign.id },
  ])
  assert.deepEqual(rows[0].children.map(row => row.id), [tdsc.id])
  assert.deepEqual(rows.find(row => row.nodeType === 'group').children.map(row => row.id), ['cross-direction'])
  assert.ok(!rows.some(row => row.id === foreign.id))
})

test('empty directions have baseline placeholders only, without fake algorithms or expandable groups', () => {
  const rows = buildComponentTree('spatiotemporal', [], [])
  assert.equal(rows.length, 4)
  assert.ok(rows.every(row => row.placeholder && !row.children))
})
