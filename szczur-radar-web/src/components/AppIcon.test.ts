import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import sprite from '../assets/icons/sprite.svg?raw'
import AppIcon from './AppIcon.vue'
import { ICON_NAMES } from './iconNames'
import IconSprite from './IconSprite.vue'

/** The `<symbol id>` list of the sprite. */
function spriteIds(): string[] {
  return [...sprite.matchAll(/<symbol\b[^>]*\bid="([^"]+)"/g)].map((match) => match[1])
}

describe('ICON_NAMES', () => {
  it('equals the symbols of the sprite: none missing, none extra, none repeated', () => {
    expect([...ICON_NAMES].sort()).toEqual(spriteIds().sort())
  })
})

describe('AppIcon', () => {
  it('is hidden from assistive technology and takes no focus', () => {
    const wrapper = mount(AppIcon, { props: { name: 'i-bell' } })
    expect(wrapper.element.tagName.toLowerCase()).toBe('svg')
    expect(wrapper.attributes('aria-hidden')).toBe('true')
    expect(wrapper.attributes('focusable')).toBe('false')
  })

  it.each(ICON_NAMES)('draws %s from the sprite', (name) => {
    const wrapper = mount(AppIcon, { props: { name } })
    expect(wrapper.find('use').attributes('href')).toBe(`#${name}`)
  })

  it('draws a stroked icon and a filled stance mark in their own way', () => {
    expect(mount(AppIcon, { props: { name: 'i-search' } }).classes()).toContain('stroked')
    expect(mount(AppIcon, { props: { name: 's-up' } }).classes()).toContain('mark')
  })
})

describe('IconSprite', () => {
  it('writes every symbol of the sprite into a hidden element', () => {
    const wrapper = mount(IconSprite)
    expect(wrapper.attributes('aria-hidden')).toBe('true')
    expect(wrapper.findAll('symbol').map((symbol) => symbol.attributes('id')).sort()).toEqual(spriteIds().sort())
  })
})
