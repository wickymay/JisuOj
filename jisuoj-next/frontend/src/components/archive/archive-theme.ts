// Canvas materials cannot inherit CSS. Keep the original daylight materials
// intact and give the night scene a neutral graphite/silver and brass palette.
const light = {
  background: 0xe9e5df, slot: 0xbdbbb4, edge: 0xf3eee3,
  record: 0xcac7be, selected: 0xe8ddc7, strip: 0xab7a3b,
  metal: 0xaaa79b, core: 0x4f5754, gold: 0xc1985b,
  emissive: 0xb57d30, glass: 0xf9f4e6,
  ground: 0xb0a28f, key: 0xfff6e8, fill: 0xdce5ea,
  label: '#3b413d', labelMuted: '#807967',
  exposure: 1.02, environment: .52, hemisphere: 1.1, keyIntensity: 2.4, fillIntensity: 1.1,
}
const dark: typeof light = {
  background: 0x151515, slot: 0x383838, edge: 0x808080,
  record: 0x4c4c4c, selected: 0x707070, strip: 0xb28d51,
  metal: 0x929292, core: 0x2d2d2d, gold: 0xcfad74,
  emissive: 0xc2954e, glass: 0x979797,
  ground: 0x303030, key: 0xeee5cf, fill: 0xc3c3c3,
  label: '#f0f0f0', labelMuted: '#ccc5b8',
  exposure: .86, environment: .38, hemisphere: .9, keyIntensity: 2, fillIntensity: .7,
}
const classic: typeof light = {
  background: 0x08111f, slot: 0x1b2d48, edge: 0x52749b,
  record: 0x1b3553, selected: 0x205580, strip: 0x1688ef,
  metal: 0x7894b3, core: 0x0c192a, gold: 0x37a0ff,
  emissive: 0x1b72b8, glass: 0x26486b,
  ground: 0x0f1c2f, key: 0xf0f6ff, fill: 0xb8d8fa,
  label: '#eaf2ff', labelMuted: '#a9bad0',
  exposure: .86, environment: .42, hemisphere: .95, keyIntensity: 2.1, fillIntensity: .8,
}

export function archivePalette(theme: string | undefined) { return theme === 'dark' ? dark : theme === 'classic' ? classic : light }
export type ArchiveMaterialColor = 'slot' | 'edge' | 'strip' | 'metal' | 'core' | 'gold' | 'glass'
