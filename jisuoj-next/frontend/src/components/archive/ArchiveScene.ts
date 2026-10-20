import * as THREE from 'three'
import { RoundedBoxGeometry } from 'three/addons/geometries/RoundedBoxGeometry.js'
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js'
import type { ProblemSummary } from '../../types/domain'
import { archiveWave, clampIndex, dampSpring } from './archive-motion'
import { archivePalette, type ArchiveMaterialColor } from './archive-theme'

type SceneOptions = { select: (index: number) => void; unavailable: () => void }
type RecordModel = { group: THREE.Group; face: THREE.Mesh; material: THREE.MeshStandardMaterial; lift: number; velocity: number }
const ARRAY_LANES = [-6, -5, -4, -3, -2, -1, 1, 2, 3, 4, 5, 6]
const ARRAY_ROW_START = -17
const ARRAY_ROW_END = 17
const ARRAY_CAPACITY = ARRAY_LANES.length * (ARRAY_ROW_END - ARRAY_ROW_START)

/** Original procedural archive hardware: no remote textures or game assets. */
export class ArchiveScene {
  private renderer: THREE.WebGLRenderer
  private scene = new THREE.Scene()
  private camera = new THREE.PerspectiveCamera(33, 1, .1, 120)
  private records = new THREE.Group()
  private models: RecordModel[] = []
  private bodyGeometry = new RoundedBoxGeometry(3.3, 2.5, .22, 2, .045)
  private geometries = new Set<THREE.BufferGeometry>()
  private materials = new Set<THREE.Material>()
  private palette = archivePalette(document.documentElement.dataset.theme)
  private themedMaterials = new Map<THREE.MeshStandardMaterial, ArchiveMaterialColor>()
  private hemisphere = new THREE.HemisphereLight(0xffffff, this.palette.ground, this.palette.hemisphere)
  private keyLight = new THREE.DirectionalLight(this.palette.key, this.palette.keyIntensity)
  private fillLight = new THREE.DirectionalLight(this.palette.fill, this.palette.fillIntensity)
  private themeObserver: MutationObserver
  private textures = new Set<THREE.Texture>()
  private environment: THREE.WebGLRenderTarget
  private slots: THREE.InstancedMesh
  private slotEdges: THREE.InstancedMesh
  private stripGeometry: THREE.BoxGeometry
  private stripMaterial: THREE.MeshStandardMaterial
  private dummy = new THREE.Object3D()
  private raycaster = new THREE.Raycaster()
  private pointer = new THREE.Vector2()
  private resizeObserver: ResizeObserver
  private frame = 0
  private disposed = false
  private active = !document.hidden
  private moving = true
  private selectedIndex = 0
  private cursor = 0
  private cursorVelocity = 0
  private clock = 0
  private previousTime = 0
  private look = { x: 0, y: 0 }
  private lookTarget = { x: 0, y: 0 }
  private waves: { index: number; time: number }[] = []
  private labelCanvas = document.createElement('canvas')
  private labelTexture: THREE.CanvasTexture
  private labelRow?: ProblemSummary
  private focusModel = new THREE.Group()

  constructor(private host: HTMLElement, private options: SceneOptions) {
    this.renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'low-power' })
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.6))
    this.renderer.setClearColor(this.palette.background, 0)
    this.renderer.outputColorSpace = THREE.SRGBColorSpace
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping
    this.renderer.toneMappingExposure = this.palette.exposure
    this.renderer.domElement.setAttribute('aria-hidden', 'true')
    this.renderer.domElement.addEventListener('webglcontextlost', this.contextLost)
    host.append(this.renderer.domElement)
    this.geometries.add(this.bodyGeometry)
    this.scene.fog = new THREE.Fog(this.palette.background, 25, 58)
    const room = new RoomEnvironment()
    const pmrem = new THREE.PMREMGenerator(this.renderer)
    this.environment = pmrem.fromScene(room, .04)
    this.scene.environment = this.environment.texture
    this.scene.environmentIntensity = this.palette.environment
    room.dispose(); pmrem.dispose()
    this.keyLight.position.set(-6, 12, 8)
    this.scene.add(this.hemisphere, this.keyLight)
    this.fillLight.position.set(8, 5, -8)
    this.scene.add(this.fillLight, this.records)
    this.stripGeometry = this.geometry(new THREE.BoxGeometry(.045, 2.18, .24))
    this.stripMaterial = this.themedMaterial('strip', new THREE.MeshStandardMaterial({ metalness: .35, roughness: .42 }))

    const slotMaterial = this.themedMaterial('slot', new THREE.MeshStandardMaterial({ metalness: .35, roughness: .43 }))
    this.slots = new THREE.InstancedMesh(this.bodyGeometry, slotMaterial, ARRAY_CAPACITY)
    this.slots.frustumCulled = false
    this.slots.instanceMatrix.setUsage(THREE.DynamicDrawUsage)
    const edgeGeometry = this.geometry(new THREE.BoxGeometry(3.08, .035, .235))
    this.slotEdges = new THREE.InstancedMesh(edgeGeometry, this.themedMaterial('edge', new THREE.MeshStandardMaterial({ roughness: .5, metalness: .45 })), ARRAY_CAPACITY)
    this.slotEdges.frustumCulled = false
    this.slotEdges.instanceMatrix.setUsage(THREE.DynamicDrawUsage)
    this.scene.add(this.slots, this.slotEdges)

    const metal = this.themedMaterial('metal', new THREE.MeshStandardMaterial({ metalness: .75, roughness: .28 }))
    const dark = this.themedMaterial('core', new THREE.MeshStandardMaterial({ metalness: .65, roughness: .38 }))
    const gold = this.themedMaterial('gold', new THREE.MeshStandardMaterial({ emissive: this.palette.emissive, emissiveIntensity: .22, metalness: .65, roughness: .28 }))
    const glass = this.themedMaterial('glass', new THREE.MeshPhysicalMaterial({ metalness: .12, roughness: .27, transmission: .35, thickness: .1, transparent: true, opacity: .48, depthWrite: false }))
    const core = new THREE.Mesh(this.geometry(new THREE.TorusGeometry(.66, .09, 10, 56)), metal)
    core.position.set(.37, .02, .22)
    const inner = new THREE.Mesh(this.geometry(new THREE.TorusGeometry(.49, .028, 8, 48)), gold)
    inner.position.set(.37, .02, .25)
    const disc = new THREE.Mesh(this.geometry(new THREE.CylinderGeometry(.37, .37, .05, 6)), dark)
    disc.rotation.x = Math.PI / 2
    disc.position.set(.37, .02, .22)
    this.focusModel.add(core, inner, disc)
    for (const [x, y, w, h] of [[0, 1.19, 3.3, .11], [0, -1.19, 3.3, .11], [-1.59, 0, .12, 2.4], [1.59, 0, .12, 2.4]]) {
      const rail = new THREE.Mesh(this.geometry(new THREE.BoxGeometry(w, h, .24)), metal)
      rail.position.set(x!, y!, .02)
      this.focusModel.add(rail)
    }
    const cover = new THREE.Mesh(this.geometry(new THREE.PlaneGeometry(3.04, 2.24)), glass)
    cover.position.z = .34
    this.focusModel.add(cover)
    for (const x of [-1.48, 1.48]) for (const y of [-1.06, 1.06]) {
      const screw = new THREE.Mesh(this.geometry(new THREE.CylinderGeometry(.045, .045, .025, 12)), dark)
      screw.rotation.x = Math.PI / 2
      screw.position.set(x, y, .37)
      this.focusModel.add(screw)
    }
    this.labelCanvas.width = 1024; this.labelCanvas.height = 256
    this.labelTexture = new THREE.CanvasTexture(this.labelCanvas)
    this.labelTexture.colorSpace = THREE.SRGBColorSpace
    this.textures.add(this.labelTexture)
    const label = new THREE.Mesh(this.geometry(new THREE.PlaneGeometry(2.88, .72)), this.material(new THREE.MeshBasicMaterial({ map: this.labelTexture, transparent: true, depthWrite: false })))
    label.position.set(0, -.65, .4)
    this.focusModel.add(label)
    this.scene.add(this.focusModel)

    const shadowCanvas = document.createElement('canvas')
    shadowCanvas.width = shadowCanvas.height = 128
    const context = shadowCanvas.getContext('2d')!
    const gradient = context.createRadialGradient(64, 64, 8, 64, 64, 64)
    gradient.addColorStop(0, 'rgba(76,65,47,.24)'); gradient.addColorStop(1, 'rgba(76,65,47,0)')
    context.fillStyle = gradient; context.fillRect(0, 0, 128, 128)
    const shadowTexture = new THREE.CanvasTexture(shadowCanvas)
    this.textures.add(shadowTexture)
    const shadow = new THREE.Mesh(this.geometry(new THREE.PlaneGeometry(27, 24)), this.material(new THREE.MeshBasicMaterial({ map: shadowTexture, transparent: true, depthWrite: false })))
    shadow.rotation.x = -Math.PI / 2; shadow.position.y = -.16
    this.scene.add(shadow)
    this.resizeObserver = new ResizeObserver(() => this.resize())
    this.resizeObserver.observe(host)
    this.themeObserver = new MutationObserver(() => this.syncTheme())
    this.themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] })
    document.addEventListener('visibilitychange', this.visibilityChanged)
    this.resize()
  }
  private geometry<T extends THREE.BufferGeometry>(value: T): T { this.geometries.add(value); return value }
  private material<T extends THREE.Material>(value: T): T { this.materials.add(value); return value }
  private themedMaterial<T extends THREE.MeshStandardMaterial>(color: ArchiveMaterialColor, value: T): T {
    value.color.set(this.palette[color])
    this.themedMaterials.set(value, color)
    return this.material(value)
  }
  private syncTheme() {
    if (this.disposed) return
    const palette = archivePalette(document.documentElement.dataset.theme)
    if (palette === this.palette) return
    this.palette = palette
    this.renderer.setClearColor(palette.background, 0)
    this.renderer.toneMappingExposure = palette.exposure
    this.scene.fog?.color.set(palette.background)
    this.scene.environmentIntensity = palette.environment
    this.hemisphere.groundColor.set(palette.ground)
    this.hemisphere.intensity = palette.hemisphere
    this.keyLight.color.set(palette.key)
    this.keyLight.intensity = palette.keyIntensity
    this.fillLight.color.set(palette.fill)
    this.fillLight.intensity = palette.fillIntensity
    this.themedMaterials.forEach((color, material) => {
      material.color.set(palette[color])
      if (color === 'gold') material.emissive.set(palette.emissive)
    })
    this.setLabel(this.labelRow)
    // Also redraw a paused scene. Changing theme must not reset selection/motion.
    this.wake()
  }

  setRows(rows: ProblemSummary[], selected: number) {
    for (const model of this.models) { model.material.dispose(); this.materials.delete(model.material) }
    this.records.clear(); this.models = []
    this.selectedIndex = clampIndex(selected, rows.length)
    this.cursor = this.selectedIndex; this.cursorVelocity = 0; this.waves = []
    rows.forEach((_problem, index) => {
      const material = this.material(new THREE.MeshStandardMaterial({ color: this.palette.record, metalness: .28, roughness: .4 }))
      const face = new THREE.Mesh(this.bodyGeometry, material)
      face.userData.index = index
      const group = new THREE.Group()
      const signal = new THREE.Mesh(this.stripGeometry, this.stripMaterial)
      signal.position.x = -1.43
      group.add(face, signal)
      this.records.add(group)
      this.models.push({ group, face, material, lift: 0, velocity: 0 })
    })
    this.focusModel.visible = rows.length > 0
    this.setLabel(rows[this.selectedIndex]); this.wake()
  }
  setSelection(index: number, row?: ProblemSummary) {
    const next = clampIndex(index, this.models.length)
    if (next !== this.selectedIndex && this.moving) {
      this.waves.push({ index: next, time: this.clock }); this.waves = this.waves.slice(-5)
    }
    this.selectedIndex = next
    this.setLabel(row); this.wake()
  }
  private setLabel(row?: ProblemSummary) {
    this.labelRow = row
    const ctx = this.labelCanvas.getContext('2d')!
    ctx.clearRect(0, 0, 1024, 256)
    if (row) {
      ctx.fillStyle = this.palette.label; ctx.font = '600 54px Consolas, monospace'
      ctx.fillText(`JISU / ${row.id}`, 14, 62)
      ctx.font = '30px "Microsoft YaHei", sans-serif'
      ctx.fillText(row.title.length > 22 ? `${row.title.slice(0, 22)}…` : row.title, 14, 120, 950)
      ctx.fillStyle = this.palette.labelMuted; ctx.font = '22px Consolas, monospace'
      ctx.fillText('PROBLEM ARCHIVE     /     ACCESS PERMITTED', 14, 182)
      for (let i = 0; i < 60; i++) ctx.fillRect(14 + i * 9, 217, i % 3 ? 3 : 5, 22)
    }
    this.labelTexture.needsUpdate = true
  }
  setMotion(enabled: boolean) { this.moving = enabled; this.waves = []; this.lookTarget = { x: 0, y: 0 }; this.wake() }
  point(clientX: number, clientY: number) {
    const rect = this.host.getBoundingClientRect()
    if (!rect.width || !rect.height) return
    this.lookTarget = { x: (clientX - rect.left) / rect.width - .5, y: (clientY - rect.top) / rect.height - .5 }; this.wake()
  }
  resetPoint() { this.lookTarget = { x: 0, y: 0 }; this.wake() }
  pick(clientX: number, clientY: number) {
    const rect = this.host.getBoundingClientRect()
    if (!rect.width || !rect.height) return
    this.pointer.set((clientX - rect.left) / rect.width * 2 - 1, -((clientY - rect.top) / rect.height) * 2 + 1)
    this.raycaster.setFromCamera(this.pointer, this.camera)
    const hit = this.raycaster.intersectObjects([...this.models.map(model => model.face), this.slots], false)[0]
    if (hit && Number.isInteger(hit.object.userData.index)) this.options.select(hit.object.userData.index as number)
  }
  private resize() {
    if (this.disposed) return
    const width = Math.max(1, this.host.clientWidth), height = Math.max(1, this.host.clientHeight)
    this.camera.aspect = width / height
    this.camera.zoom = width > 550 && height < 450 ? 1.18 : 1
    this.camera.updateProjectionMatrix()
    this.renderer.setSize(width, height); this.wake()
  }
  private visibilityChanged = () => {
    this.active = !document.hidden
    if (this.active) this.wake()
    else { cancelAnimationFrame(this.frame); this.frame = 0; this.previousTime = 0 }
  }
  private contextLost = (event: Event) => { event.preventDefault(); this.options.unavailable() }
  private wake() { if (!this.disposed && this.active && !this.frame) this.frame = requestAnimationFrame(this.render) }
  private render = (now: number) => {
    this.frame = 0
    if (this.disposed || !this.active) return
    const dt = this.previousTime ? Math.min(.05, (now - this.previousTime) / 1000) : .016
    this.previousTime = now; this.clock += dt
    const step = dampSpring(this.cursor, this.cursorVelocity, this.selectedIndex, dt, 7)
    this.cursor = this.moving ? step.value : this.selectedIndex
    this.cursorVelocity = this.moving ? step.velocity : 0
    this.waves = this.waves.filter(wave => this.clock - wave.time < 2.8)
    const ease = 1 - Math.exp(-dt * 4)
    this.look.x += ((this.moving ? this.lookTarget.x : 0) - this.look.x) * ease
    this.look.y += ((this.moving ? this.lookTarget.y : 0) - this.look.y) * ease
    const distanceScale = this.host.clientWidth < 550 ? Math.max(1, .82 / this.camera.aspect) : .88
    this.camera.position.set((10.8 + this.look.x * .75) * distanceScale, (9.5 - this.look.y * .6) * distanceScale, 16.8 * distanceScale)
    this.camera.lookAt(-.55, 2, -.8)
    this.models.forEach((model, index) => {
      const distance = index - this.cursor
      const peak = Math.exp(-distance * distance * .12) * .45
      const lift = index === this.selectedIndex ? 2.8 : 0
      const step = dampSpring(model.lift, model.velocity, lift, dt, 6)
      model.lift = this.moving ? step.value : lift; model.velocity = this.moving ? step.velocity : 0
      const ripple = this.moving ? this.waves.reduce((height, wave) => height + archiveWave(index - wave.index, this.clock - wave.time), 0) : 0
      const breathe = this.moving ? Math.sin(this.clock * .65 + index * .3) * .035 : 0
      model.group.position.set(0, 1.35 + peak + model.lift + ripple + breathe, distance * .5)
      model.material.color.set(index === this.selectedIndex ? this.palette.selected : this.palette.record)
      if (index === this.selectedIndex) this.focusModel.position.copy(model.group.position)
    })
    let instance = 0
    for (const lane of ARRAY_LANES) for (let row = ARRAY_ROW_START; row < ARRAY_ROW_END; row++) {
      const offset = row - (this.cursor % 1)
      const wave = this.moving ? this.waves.reduce((height, value) => height + archiveWave(offset + Math.abs(lane) * .8, this.clock - value.time) * .65, 0) : 0
      const shoulder = Math.exp(-offset * offset * .09) * .32 / Math.abs(lane)
      this.dummy.position.set(lane * 3.75, 1.26 + shoulder + wave, offset * .5)
      this.dummy.updateMatrix(); this.slots.setMatrixAt(instance, this.dummy.matrix)
      this.dummy.position.y += 1.22
      this.dummy.updateMatrix(); this.slotEdges.setMatrixAt(instance++, this.dummy.matrix)
    }
    this.slots.instanceMatrix.needsUpdate = this.slotEdges.instanceMatrix.needsUpdate = true
    this.renderer.render(this.scene, this.camera)
    if (this.moving || Math.abs(this.look.x) + Math.abs(this.look.y) > .002) this.wake()
    else this.previousTime = 0
  }
  dispose() {
    if (this.disposed) return
    this.disposed = true; cancelAnimationFrame(this.frame)
    this.resizeObserver.disconnect()
    this.themeObserver.disconnect()
    document.removeEventListener('visibilitychange', this.visibilityChanged)
    this.renderer.domElement.removeEventListener('webglcontextlost', this.contextLost)
    this.geometries.forEach(value => value.dispose()); this.materials.forEach(value => value.dispose()); this.textures.forEach(value => value.dispose())
    this.themedMaterials.clear()
    this.slots.dispose(); this.slotEdges.dispose(); this.environment.dispose(); this.scene.clear()
    this.renderer.dispose(); this.renderer.forceContextLoss(); this.renderer.domElement.remove()
  }
}
