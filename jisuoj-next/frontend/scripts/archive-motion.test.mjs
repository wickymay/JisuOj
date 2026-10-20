import { test } from 'node:test'
import assert from 'node:assert/strict'
import { archiveWave, clampIndex, dampSpring } from '../src/components/archive/archive-motion.ts'

test('selection is clamped for empty, single and paginated archives', () => {
  assert.equal(clampIndex(10, 0), 0)
  assert.equal(clampIndex(-1, 20), 0)
  assert.equal(clampIndex(20, 20), 19)
  assert.equal(clampIndex(4, 1), 0)
})
test('spring settles without overshoot from rest and remains stable across frame rates', () => {
  for (const dt of [1 / 30, 1 / 60, 1 / 120]) {
    let state = { value: 0, velocity: 0 }
    for (let t = 0; t < 3; t += dt) {
      state = dampSpring(state.value, state.velocity, 19, dt, 7)
      assert.ok(state.value >= 0 && state.value <= 19)
    }
    assert.ok(Math.abs(state.value - 19) < .0001)
  }
})
test('quick reversal preserves continuity and converges on the newest file', () => {
  let state = dampSpring(0, 0, 19, .05, 7)
  const before = state.value
  state = dampSpring(state.value, state.velocity, 0, .001, 7)
  assert.ok(Math.abs(state.value - before) < .1)
  for (let i = 0; i < 240; i++) state = dampSpring(state.value, state.velocity, 0, 1 / 60, 7)
  assert.ok(Math.abs(state.value) < .0001)
})
test('wave arrives later at distant slots, decays, and expires', () => {
  assert.equal(archiveWave(8, .1), 0)
  assert.equal(archiveWave(0, -1), 0)
  assert.equal(archiveWave(0, 3), 0)
  assert.ok(archiveWave(0, .15) > 0)
  assert.equal(archiveWave(-3, .4), archiveWave(3, .4))
  assert.ok(Math.abs(archiveWave(0, 2.5)) < .01)
})
