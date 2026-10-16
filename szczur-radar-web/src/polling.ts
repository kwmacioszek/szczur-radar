// A timer that lives as long as the component that set it up: started on mount, cleared before unmount.
import { onBeforeUnmount, onMounted } from 'vue'

/** Calls `tick` every `ms` while the component is mounted; `tick` decides whether there is anything to do. */
export function useEvery(ms: number, tick: () => void): void {
  let timer: ReturnType<typeof setInterval> | undefined
  onMounted(() => {
    timer = setInterval(tick, ms)
  })
  onBeforeUnmount(() => {
    clearInterval(timer)
  })
}
