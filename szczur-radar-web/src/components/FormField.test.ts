import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { h } from 'vue'
import FormField from './FormField.vue'

type Props = { label: string; hint?: string; error?: string | null }

/** The field with a plain input bound to the slot's `fieldProps`, as a view does. */
function mountField(props: Props) {
  return mount(FormField, {
    props,
    slots: { default: (slot: { fieldProps: Record<string, string | undefined> }) => h('input', { type: 'text', ...slot.fieldProps }) },
  })
}

describe('FormField', () => {
  it('ties the label to the field by for and id', () => {
    const wrapper = mountField({ label: 'E-mail' })
    const input = wrapper.find('input')
    expect(input.attributes('id')).toBeTruthy()
    expect(wrapper.find('label').attributes('for')).toBe(input.attributes('id'))
    expect(wrapper.find('label').text()).toBe('E-mail')
  })

  it('has no description and is not invalid without a hint and an error', () => {
    const input = mountField({ label: 'E-mail' }).find('input')
    expect(input.attributes('aria-describedby')).toBeUndefined()
    expect(input.attributes('aria-invalid')).toBeUndefined()
  })

  it('describes the field by its hint alone', () => {
    const wrapper = mountField({ label: 'Hasło', hint: 'Od 12 do 128 znaków.' })
    const describedBy = wrapper.find('input').attributes('aria-describedby')
    expect(describedBy).toBe(wrapper.find('.hint').attributes('id'))
    expect(wrapper.find('.hint').text()).toBe('Od 12 do 128 znaków.')
    expect(wrapper.find('input').attributes('aria-invalid')).toBeUndefined()
  })

  it('describes the field by its error alone and marks it invalid', () => {
    const wrapper = mountField({ label: 'Hasło', error: 'Hasło jest za krótkie.' })
    const input = wrapper.find('input')
    expect(input.attributes('aria-describedby')).toBe(wrapper.find('.error').attributes('id'))
    expect(input.attributes('aria-invalid')).toBe('true')
  })

  it('names the hint and the error together, in that order', () => {
    const wrapper = mountField({ label: 'Hasło', hint: 'Od 12 do 128 znaków.', error: 'Hasło jest za krótkie.' })
    const ids = [wrapper.find('.hint').attributes('id'), wrapper.find('.error').attributes('id')]
    expect(ids[0]).not.toBe(ids[1])
    expect(wrapper.find('input').attributes('aria-describedby')).toBe(ids.join(' '))
  })

  it('treats an empty error like none', () => {
    const wrapper = mountField({ label: 'Hasło', error: null })
    expect(wrapper.find('.error').exists()).toBe(false)
    expect(wrapper.find('input').attributes('aria-invalid')).toBeUndefined()
  })

  it('shows the error with its icon and without role alert', () => {
    const error = mountField({ label: 'Hasło', error: 'Hasło jest za krótkie.' }).find('.error')
    expect(error.element.tagName.toLowerCase()).toBe('p')
    expect(error.text()).toBe('Hasło jest za krótkie.')
    expect(error.find('use').attributes('href')).toBe('#i-circle-alert')
    expect(error.find('svg').attributes('aria-hidden')).toBe('true')
    expect(error.attributes('role')).toBeUndefined()
  })

  it('keeps a polite live region from the start and announces the error in it when it appears', async () => {
    const wrapper = mountField({ label: 'Hasło' })
    const region = wrapper.find('[aria-live="polite"]')
    expect(region.exists()).toBe(true)
    expect(region.text()).toBe('')

    await wrapper.setProps({ error: 'Hasło jest za krótkie.' })
    const after = wrapper.find('[aria-live="polite"]')
    expect(after.element).toBe(region.element)
    expect(after.text()).toBe('Hasło jest za krótkie.')
    expect(after.find('.error').exists()).toBe(true)
  })

  it('gives two fields of one page different ids', () => {
    const field = (label: string) =>
      h(
        FormField,
        { label, hint: 'Podpowiedź', error: 'Błąd' },
        { default: (slot: { fieldProps: Record<string, string | undefined> }) => h('input', { ...slot.fieldProps }) },
      )
    const wrapper = mount({ render: () => h('div', [field('Hasło'), field('Powtórz hasło')]) })
    const ids = wrapper.findAll('[id]').map((element) => element.attributes('id'))
    expect(ids).toHaveLength(6)
    expect(new Set(ids).size).toBe(6)
  })

  it('takes the layout class of its parent on the root', () => {
    const wrapper = mount(FormField, { props: { label: 'Hasło' }, attrs: { class: 'wide' }, slots: { default: '<input />' } })
    expect(wrapper.classes()).toEqual(expect.arrayContaining(['form-field', 'wide']))
  })
})
