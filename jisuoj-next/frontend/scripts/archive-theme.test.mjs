import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { archivePalette } from '../src/components/archive/archive-theme.ts'

test('daylight materials remain unchanged and theme selection is reversible', () => {
  const light = archivePalette('light')
  const dark = archivePalette('dark')
  assert.equal(light.background, 0xe9e5df)
  assert.equal(light.selected, 0xe8ddc7)
  assert.equal(light.label, '#3b413d')
  assert.equal(light.exposure, 1.02)
  assert.equal(archivePalette(undefined), light)
  assert.equal(archivePalette('invalid'), light)
  assert.notDeepEqual(dark, light)
  assert.equal(archivePalette('light'), light)
  assert.equal(archivePalette('dark'), dark)
  assert.equal(dark.background, 0x151515)
  assert.ok(dark.exposure < light.exposure)
  assert.notEqual(dark.record, dark.selected)
  assert.notEqual(dark.label, light.label)
})

test('night archive text tokens meet 4.5:1 contrast on all panel surfaces', () => {
  const css = readFileSync(new URL('../src/styles/problem-archive-dark.css', import.meta.url), 'utf8')
  const tokens = Object.fromEntries([...css.matchAll(/--archive-([a-z-]+): (#[0-9a-f]{6});/g)].map(m => [m[1], m[2]]))
  function luminance(color) {
    const rgb = color.slice(1).match(/../g).map(c => parseInt(c, 16) / 255)
      .map(c => c <= .04045 ? c / 12.92 : ((c + .055) / 1.055) ** 2.4)
    return rgb[0] * .2126 + rgb[1] * .7152 + rgb[2] * .0722
  }
  function contrast(fg, bg) {
    const values = [luminance(fg), luminance(bg)].sort((a, b) => a - b)
    return (values[1] + .05) / (values[0] + .05)
  }
  for (const fg of ['ink', 'muted', 'dim', 'gold', 'success', 'danger']) {
    for (const bg of ['bg', 'surface', 'raised']) {
      assert.ok(contrast(tokens[fg], tokens[bg]) >= 4.5, `${fg} on ${bg}`)
    }
  }
  for (const bg of ['action', 'action-hover']) {
    assert.ok(contrast(tokens['on-accent'], tokens[bg]) >= 4.5)
    assert.ok(contrast(tokens['action-muted'], tokens[bg]) >= 4.5)
  }
})

test('night surfaces and 3D hardware stay neutral without green accents', () => {
  const dark = archivePalette('dark')
  const css = readFileSync(new URL('../src/styles/problem-archive-dark.css', import.meta.url), 'utf8')
  const channels = hex => hex.slice(0, 6).match(/../g).map(c => parseInt(c, 16))
  for (const key of ['background', 'slot', 'edge', 'record', 'selected', 'metal', 'core', 'glass', 'ground', 'fill']) {
    const [r, g, b] = channels(dark[key].toString(16).padStart(6, '0'))
    assert.ok(r === g && g === b, `${key} must be neutral gray`)
  }
  for (const match of css.matchAll(/#([0-9a-f]{6})(?:[0-9a-f]{2})?\b/g)) {
    const [r, g, b] = channels(match[1])
    assert.ok(g <= Math.max(r, b), `unexpected green tint: ${match[0]}`)
  }
  assert.match(css, /\.archive-table \.result-code\.accepted\s*\{\s*color: var\(--archive-success\)/)
})
