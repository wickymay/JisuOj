// Pure motion helpers, shared by the scene and its regression checks.
export function dampSpring(value: number, velocity: number, target: number, dt: number, speed = 9) {
  const displacement = value - target
  const impulse = velocity + speed * displacement
  const decay = Math.exp(-speed * dt)
  return { value: target + (displacement + impulse * dt) * decay, velocity: (velocity - speed * impulse * dt) * decay }
}
export function archiveWave(distance: number, age: number) {
  if (age < 0 || age > 2.8) return 0
  const arrival = age - Math.abs(distance) * .065
  if (arrival < 0) return 0
  return Math.sin(arrival * 9) * Math.exp(-arrival * 2.5) * Math.exp(-Math.abs(distance) * .085) * .48
}
export function clampIndex(index: number, length: number) {
  return Math.max(0, Math.min(Math.max(0, length - 1), Math.round(index)))
}
